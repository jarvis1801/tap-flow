package com.tapflow.core.server

import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.model.ipc.WsMessage
import com.tapflow.core.model.ipc.WsMessageType
import com.tapflow.core.model.script.Script

interface WsServerDelegate {
  val connectedClients: Int
  val currentRunnerPhaseName: String

  suspend fun getAllScripts(): List<Script>
  suspend fun saveScript(script: Script)
  suspend fun deleteScript(scriptId: String)
  suspend fun setClientLiveStreaming(clientId: String, enabled: Boolean): Boolean
  suspend fun startRemoteRun(scriptId: String): Result<Script>
  fun stopRemoteRun()
}

data class WsDispatchOutcome(
  val directReply: WsMessage? = null,
  val broadcastMessage: WsMessage? = null,
)

internal object WsMessageDispatcher {
  private val json = TapFlowJson.instance

  suspend fun dispatchIncoming(
    clientId: String,
    rawText: String,
    delegate: WsServerDelegate,
  ): WsDispatchOutcome {
    val message =
      runCatching { json.decodeFromString<WsMessage>(rawText) }.getOrNull()
        ?: return WsDispatchOutcome(
          directReply =
            buildEnvelope(
              type = WsMessageType.EXECUTION_LOG_EVENT,
              requestId = null,
              payload =
                json.encodeToString(
                  ExecutionLogEventPayload.serializer(),
                  ExecutionLogEventPayload(
                    eventType = "PROTOCOL_ERROR",
                    message = "Malformed WsMessage JSON envelope.",
                  ),
                ),
            ),
        )

    return when (message.type) {
      WsMessageType.HEARTBEAT -> {
        val payload =
          json.encodeToString(
            HeartbeatPayload.serializer(),
            HeartbeatPayload(
              alive = true,
              connectedClients = delegate.connectedClients,
              runnerPhase = delegate.currentRunnerPhaseName,
            ),
          )
        WsDispatchOutcome(
          directReply = buildEnvelope(WsMessageType.HEARTBEAT, message.requestId, payload),
        )
      }

      WsMessageType.SYNC_ALL_SCRIPTS_REQUEST -> {
        val scripts = delegate.getAllScripts()
        val payload =
          json.encodeToString(
            SyncAllScriptsPayload.serializer(),
            SyncAllScriptsPayload(scripts = scripts),
          )
        WsDispatchOutcome(
          directReply = buildEnvelope(WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE, message.requestId, payload),
        )
      }

      WsMessageType.PUSH_SCRIPT -> {
        val script = decodePushScript(message.payloadJson)
        if (script == null) {
          WsDispatchOutcome(
            directReply =
              errorLogEnvelope(message.requestId, "PUSH_SCRIPT failed: invalid Script payload."),
          )
        } else {
          delegate.saveScript(script)
          val updated = delegate.getAllScripts()
          val syncPayload =
            json.encodeToString(
              SyncAllScriptsPayload.serializer(),
              SyncAllScriptsPayload(scripts = updated),
            )
          WsDispatchOutcome(
            broadcastMessage = buildEnvelope(WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE, message.requestId, syncPayload),
          )
        }
      }

      WsMessageType.DELETE_SCRIPT -> {
        val scriptId = decodeDeleteScriptId(message.payloadJson)
        if (scriptId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply =
              errorLogEnvelope(message.requestId, "DELETE_SCRIPT failed: missing scriptId."),
          )
        } else {
          delegate.deleteScript(scriptId)
          val updated = delegate.getAllScripts()
          val syncPayload =
            json.encodeToString(
              SyncAllScriptsPayload.serializer(),
              SyncAllScriptsPayload(scripts = updated),
            )
          WsDispatchOutcome(
            broadcastMessage = buildEnvelope(WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE, message.requestId, syncPayload),
          )
        }
      }

      WsMessageType.START_LIVE_STREAM -> {
        val permitted = delegate.setClientLiveStreaming(clientId, true)
        val eventPayload =
          json.encodeToString(
            ExecutionLogEventPayload.serializer(),
            ExecutionLogEventPayload(
              eventType = if (permitted) "LIVE_STREAM_STARTED" else "LIVE_STREAM_PERMISSION_REQUIRED",
              message =
                if (permitted) "Live binary screen streaming started."
                else "MediaProjection or Shizuku Silent Screenshot is not active.",
            ),
          )
        WsDispatchOutcome(
          directReply = buildEnvelope(WsMessageType.EXECUTION_LOG_EVENT, message.requestId, eventPayload),
        )
      }

      WsMessageType.STOP_LIVE_STREAM -> {
        delegate.setClientLiveStreaming(clientId, false)
        val eventPayload =
          json.encodeToString(
            ExecutionLogEventPayload.serializer(),
            ExecutionLogEventPayload(
              eventType = "LIVE_STREAM_STOPPED",
              message = "Live binary screen streaming stopped.",
            ),
          )
        WsDispatchOutcome(
          directReply = buildEnvelope(WsMessageType.EXECUTION_LOG_EVENT, message.requestId, eventPayload),
        )
      }

      WsMessageType.REMOTE_RUN_SCRIPT -> {
        val scriptId = decodeRemoteRunScriptId(message.payloadJson)
        if (scriptId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply = errorLogEnvelope(message.requestId, "REMOTE_RUN_SCRIPT failed: missing scriptId."),
          )
        } else {
          val res = delegate.startRemoteRun(scriptId)
          val event =
            res.fold(
              onSuccess = { script ->
                ExecutionLogEventPayload(
                  eventType = "REMOTE_RUN_STARTED",
                  scriptTitle = script.title,
                  phase = "RUNNING",
                  totalSteps = script.actions.size,
                  message = "Started '${script.title}' remotely.",
                )
              },
              onFailure = { err ->
                ExecutionLogEventPayload(
                  eventType = "REMOTE_RUN_REJECTED",
                  message = err.message ?: "Unable to start script.",
                )
              },
            )
          WsDispatchOutcome(
            directReply =
              buildEnvelope(
                WsMessageType.EXECUTION_LOG_EVENT,
                message.requestId,
                json.encodeToString(ExecutionLogEventPayload.serializer(), event),
              ),
          )
        }
      }

      WsMessageType.REMOTE_STOP -> {
        delegate.stopRemoteRun()
        val payload =
          json.encodeToString(
            ExecutionLogEventPayload.serializer(),
            ExecutionLogEventPayload(
              eventType = "REMOTE_STOP_ACK",
              phase = "STOPPED",
              message = "Remote stop signal executed.",
            ),
          )
        WsDispatchOutcome(
          broadcastMessage = buildEnvelope(WsMessageType.EXECUTION_LOG_EVENT, message.requestId, payload),
        )
      }

      WsMessageType.HANDSHAKE_ACK,
      WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE,
      WsMessageType.EXECUTION_LOG_EVENT -> WsDispatchOutcome()
    }
  }

  fun buildEnvelope(
    type: WsMessageType,
    requestId: String?,
    payload: String,
    timestampMs: Long = System.currentTimeMillis(),
  ): WsMessage =
    WsMessage(
      type = type,
      requestId = requestId,
      timestamp = timestampMs,
      payloadJson = payload,
    )

  fun encodeEnvelope(message: WsMessage): String = json.encodeToString(WsMessage.serializer(), message)

  private fun errorLogEnvelope(requestId: String?, message: String): WsMessage {
    val payload =
      json.encodeToString(
        ExecutionLogEventPayload.serializer(),
        ExecutionLogEventPayload(eventType = "ERROR", message = message),
      )
    return buildEnvelope(WsMessageType.EXECUTION_LOG_EVENT, requestId, payload)
  }

  private fun decodePushScript(payloadJson: String): Script? {
    runCatching { return json.decodeFromString(PushScriptPayload.serializer(), payloadJson).script }
    runCatching { return json.decodeFromString(Script.serializer(), payloadJson) }
    return null
  }

  private fun decodeDeleteScriptId(payloadJson: String): String? {
    runCatching { return json.decodeFromString(DeleteScriptPayload.serializer(), payloadJson).scriptId }
    return payloadJson.trim().trim('"').takeIf { it.isNotBlank() && !it.startsWith("{") }
  }

  private fun decodeRemoteRunScriptId(payloadJson: String): String? {
    runCatching { return json.decodeFromString(RemoteRunScriptPayload.serializer(), payloadJson).scriptId }
    return payloadJson.trim().trim('"').takeIf { it.isNotBlank() && !it.startsWith("{") }
  }
}
