package com.tapflow.core.server

import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.ipc.WsCaptureSnapshotResponse
import com.tapflow.core.model.ipc.WsDeleteInspectGroupPayload
import com.tapflow.core.model.ipc.WsDeleteInspectItemPayload
import com.tapflow.core.model.ipc.WsDeletePipelinePayload
import com.tapflow.core.model.ipc.WsDeleteScriptPayload
import com.tapflow.core.model.ipc.WsDeviceStatusPayload
import com.tapflow.core.model.ipc.WsErrorPayload
import com.tapflow.core.model.ipc.WsExecutionLogEventPayload
import com.tapflow.core.model.ipc.WsGetErrorScreenshotRequest
import com.tapflow.core.model.ipc.WsGetErrorScreenshotResponse
import com.tapflow.core.model.ipc.WsGetInspectImageRequest
import com.tapflow.core.model.ipc.WsGetInspectImageResponse
import com.tapflow.core.model.ipc.WsHeartbeatPayload
import com.tapflow.core.model.ipc.WsInspectGroupBundleDto
import com.tapflow.core.model.ipc.WsInstalledAppDto
import com.tapflow.core.model.ipc.WsMessage
import com.tapflow.core.model.ipc.WsMessageType
import com.tapflow.core.model.ipc.WsPushInspectItemPayload
import com.tapflow.core.model.ipc.WsPushPipelinePayload
import com.tapflow.core.model.ipc.WsPushScriptPayload
import com.tapflow.core.model.ipc.WsRemoteInputPayload
import com.tapflow.core.model.ipc.WsRemoteRunPipelinePayload
import com.tapflow.core.model.ipc.WsRemoteRunScriptPayload
import com.tapflow.core.model.ipc.WsSyncAllInspectPayload
import com.tapflow.core.model.ipc.WsSyncAllPipelinesPayload
import com.tapflow.core.model.ipc.WsSyncAllScriptsPayload
import com.tapflow.core.model.ipc.WsSyncInstalledAppsResponse
import com.tapflow.core.model.ipc.WsSyncLogsResponse
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script

class WsProtocolException(
  val code: String,
  override val message: String,
  val conflictUpdatedAtMs: Long? = null,
) : RuntimeException(message)

interface WsServerDelegate {
  val connectedClients: Int
  val currentRunnerPhaseName: String

  suspend fun getDeviceStatus(): WsDeviceStatusPayload

  suspend fun getAllScripts(): List<Script>

  suspend fun pushScript(script: Script, forceOverwrite: Boolean): Result<List<Script>>

  suspend fun deleteScript(scriptId: String): List<Script>

  suspend fun getAllPipelines(): List<Pipeline>

  suspend fun pushPipeline(pipeline: Pipeline, forceOverwrite: Boolean): Result<List<Pipeline>>

  suspend fun deletePipeline(pipelineId: String): List<Pipeline>

  suspend fun getAllInspectGroups(): List<WsInspectGroupBundleDto>

  suspend fun getInspectImage(itemId: String): Result<WsGetInspectImageResponse>

  suspend fun pushInspectItem(
    groupTitle: String,
    item: InspectItem,
    pngBase64: String?,
  ): Result<List<WsInspectGroupBundleDto>>

  suspend fun deleteInspectGroup(groupId: String): List<WsInspectGroupBundleDto>

  suspend fun deleteInspectItem(itemId: String): List<WsInspectGroupBundleDto>

  suspend fun captureLosslessSnapshot(): Result<WsCaptureSnapshotResponse>

  suspend fun getInstalledApps(): List<WsInstalledAppDto>

  suspend fun getAllLogs(): WsSyncLogsResponse

  suspend fun getErrorScreenshot(
    filePath: String?,
    logId: String?,
  ): Result<WsGetErrorScreenshotResponse>

  suspend fun setClientLiveStreaming(clientId: String, enabled: Boolean): Boolean

  suspend fun startRemoteRunScript(scriptId: String): Result<Script>

  suspend fun startRemoteRunPipeline(pipelineId: String): Result<Pipeline>

  fun stopRemoteRun()

  suspend fun executeRemoteInput(input: WsRemoteInputPayload): Result<Unit>
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
            errorEnvelope(
              requestId = null,
              code = "MALFORMED_ENVELOPE",
              message = "Malformed WsMessage JSON envelope.",
            ),
        )

    return when (message.type) {
      WsMessageType.HEARTBEAT -> {
        val payload =
          json.encodeToString(
            WsHeartbeatPayload.serializer(),
            WsHeartbeatPayload(
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
            WsSyncAllScriptsPayload.serializer(),
            WsSyncAllScriptsPayload(scripts = scripts),
          )
        WsDispatchOutcome(
          directReply =
            buildEnvelope(WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE, message.requestId, payload),
        )
      }

      WsMessageType.PUSH_SCRIPT -> {
        val pushPayload = decodePushScript(message.payloadJson)
        if (pushPayload == null) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "PUSH_SCRIPT failed: invalid Script payload.",
              ),
          )
        } else {
          delegate.pushScript(pushPayload.script, pushPayload.forceOverwrite).fold(
            onSuccess = { updated ->
              val syncPayload =
                json.encodeToString(
                  WsSyncAllScriptsPayload.serializer(),
                  WsSyncAllScriptsPayload(scripts = updated),
                )
              WsDispatchOutcome(
                broadcastMessage =
                  buildEnvelope(
                    WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE,
                    message.requestId,
                    syncPayload,
                  ),
              )
            },
            onFailure = { err ->
              WsDispatchOutcome(
                directReply = errorEnvelopeFromThrowable(message.requestId, err, "PUSH_SCRIPT_FAILED"),
              )
            },
          )
        }
      }

      WsMessageType.DELETE_SCRIPT -> {
        val scriptId = decodeIdField(message.payloadJson) {
          json.decodeFromString(WsDeleteScriptPayload.serializer(), it).scriptId
        }
        if (scriptId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "DELETE_SCRIPT failed: missing scriptId.",
              ),
          )
        } else {
          val updated = delegate.deleteScript(scriptId)
          val syncPayload =
            json.encodeToString(
              WsSyncAllScriptsPayload.serializer(),
              WsSyncAllScriptsPayload(scripts = updated),
            )
          WsDispatchOutcome(
            broadcastMessage =
              buildEnvelope(
                WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE,
                message.requestId,
                syncPayload,
              ),
          )
        }
      }

      WsMessageType.SYNC_ALL_PIPELINES_REQUEST -> {
        val pipelines = delegate.getAllPipelines()
        val payload =
          json.encodeToString(
            WsSyncAllPipelinesPayload.serializer(),
            WsSyncAllPipelinesPayload(pipelines = pipelines),
          )
        WsDispatchOutcome(
          directReply =
            buildEnvelope(WsMessageType.SYNC_ALL_PIPELINES_RESPONSE, message.requestId, payload),
        )
      }

      WsMessageType.PUSH_PIPELINE -> {
        val pushPayload = decodePushPipeline(message.payloadJson)
        if (pushPayload == null) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "PUSH_PIPELINE failed: invalid Pipeline payload.",
              ),
          )
        } else {
          delegate.pushPipeline(pushPayload.pipeline, pushPayload.forceOverwrite).fold(
            onSuccess = { updated ->
              val syncPayload =
                json.encodeToString(
                  WsSyncAllPipelinesPayload.serializer(),
                  WsSyncAllPipelinesPayload(pipelines = updated),
                )
              WsDispatchOutcome(
                broadcastMessage =
                  buildEnvelope(
                    WsMessageType.SYNC_ALL_PIPELINES_RESPONSE,
                    message.requestId,
                    syncPayload,
                  ),
              )
            },
            onFailure = { err ->
              WsDispatchOutcome(
                directReply =
                  errorEnvelopeFromThrowable(message.requestId, err, "PUSH_PIPELINE_FAILED"),
              )
            },
          )
        }
      }

      WsMessageType.DELETE_PIPELINE -> {
        val pipelineId = decodeIdField(message.payloadJson) {
          json.decodeFromString(WsDeletePipelinePayload.serializer(), it).pipelineId
        }
        if (pipelineId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "DELETE_PIPELINE failed: missing pipelineId.",
              ),
          )
        } else {
          val updated = delegate.deletePipeline(pipelineId)
          val syncPayload =
            json.encodeToString(
              WsSyncAllPipelinesPayload.serializer(),
              WsSyncAllPipelinesPayload(pipelines = updated),
            )
          WsDispatchOutcome(
            broadcastMessage =
              buildEnvelope(
                WsMessageType.SYNC_ALL_PIPELINES_RESPONSE,
                message.requestId,
                syncPayload,
              ),
          )
        }
      }

      WsMessageType.SYNC_ALL_INSPECT_REQUEST -> {
        val groups = delegate.getAllInspectGroups()
        val payload =
          json.encodeToString(
            WsSyncAllInspectPayload.serializer(),
            WsSyncAllInspectPayload(groups = groups),
          )
        WsDispatchOutcome(
          directReply =
            buildEnvelope(WsMessageType.SYNC_ALL_INSPECT_RESPONSE, message.requestId, payload),
        )
      }

      WsMessageType.GET_INSPECT_IMAGE_REQUEST -> {
        val itemId = decodeIdField(message.payloadJson) {
          json.decodeFromString(WsGetInspectImageRequest.serializer(), it).itemId
        }
        if (itemId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "GET_INSPECT_IMAGE_REQUEST failed: missing itemId.",
              ),
          )
        } else {
          delegate.getInspectImage(itemId).fold(
            onSuccess = { response ->
              WsDispatchOutcome(
                directReply =
                  buildEnvelope(
                    WsMessageType.GET_INSPECT_IMAGE_RESPONSE,
                    message.requestId,
                    json.encodeToString(WsGetInspectImageResponse.serializer(), response),
                  ),
              )
            },
            onFailure = { err ->
              WsDispatchOutcome(
                directReply =
                  errorEnvelopeFromThrowable(message.requestId, err, "INSPECT_IMAGE_NOT_FOUND"),
              )
            },
          )
        }
      }

      WsMessageType.PUSH_INSPECT_ITEM -> {
        val pushPayload =
          runCatching {
            json.decodeFromString(WsPushInspectItemPayload.serializer(), message.payloadJson)
          }.getOrNull()
        if (pushPayload == null) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "PUSH_INSPECT_ITEM failed: invalid InspectItem payload.",
              ),
          )
        } else {
          delegate
            .pushInspectItem(
              groupTitle = pushPayload.groupTitle,
              item = pushPayload.item,
              pngBase64 = pushPayload.pngBase64,
            )
            .fold(
              onSuccess = { updatedGroups ->
                val syncPayload =
                  json.encodeToString(
                    WsSyncAllInspectPayload.serializer(),
                    WsSyncAllInspectPayload(groups = updatedGroups),
                  )
                WsDispatchOutcome(
                  broadcastMessage =
                    buildEnvelope(
                      WsMessageType.SYNC_ALL_INSPECT_RESPONSE,
                      message.requestId,
                      syncPayload,
                    ),
                )
              },
              onFailure = { err ->
                WsDispatchOutcome(
                  directReply =
                    errorEnvelopeFromThrowable(
                      message.requestId,
                      err,
                      "PUSH_INSPECT_ITEM_FAILED",
                    ),
                )
              },
            )
        }
      }

      WsMessageType.DELETE_INSPECT_GROUP -> {
        val groupId = decodeIdField(message.payloadJson) {
          json.decodeFromString(WsDeleteInspectGroupPayload.serializer(), it).groupId
        }
        if (groupId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "DELETE_INSPECT_GROUP failed: missing groupId.",
              ),
          )
        } else {
          val updated = delegate.deleteInspectGroup(groupId)
          val syncPayload =
            json.encodeToString(
              WsSyncAllInspectPayload.serializer(),
              WsSyncAllInspectPayload(groups = updated),
            )
          WsDispatchOutcome(
            broadcastMessage =
              buildEnvelope(
                WsMessageType.SYNC_ALL_INSPECT_RESPONSE,
                message.requestId,
                syncPayload,
              ),
          )
        }
      }

      WsMessageType.DELETE_INSPECT_ITEM -> {
        val itemId = decodeIdField(message.payloadJson) {
          json.decodeFromString(WsDeleteInspectItemPayload.serializer(), it).itemId
        }
        if (itemId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "DELETE_INSPECT_ITEM failed: missing itemId.",
              ),
          )
        } else {
          val updated = delegate.deleteInspectItem(itemId)
          val syncPayload =
            json.encodeToString(
              WsSyncAllInspectPayload.serializer(),
              WsSyncAllInspectPayload(groups = updated),
            )
          WsDispatchOutcome(
            broadcastMessage =
              buildEnvelope(
                WsMessageType.SYNC_ALL_INSPECT_RESPONSE,
                message.requestId,
                syncPayload,
              ),
          )
        }
      }

      WsMessageType.CAPTURE_SNAPSHOT_REQUEST -> {
        delegate.captureLosslessSnapshot().fold(
          onSuccess = { snapshot ->
            WsDispatchOutcome(
              directReply =
                buildEnvelope(
                  WsMessageType.CAPTURE_SNAPSHOT_RESPONSE,
                  message.requestId,
                  json.encodeToString(WsCaptureSnapshotResponse.serializer(), snapshot),
                ),
            )
          },
          onFailure = { err ->
            WsDispatchOutcome(
              directReply =
                errorEnvelopeFromThrowable(message.requestId, err, "CAPTURE_UNAVAILABLE"),
            )
          },
        )
      }

      WsMessageType.SYNC_INSTALLED_APPS_REQUEST -> {
        val apps = delegate.getInstalledApps()
        val payload =
          json.encodeToString(
            WsSyncInstalledAppsResponse.serializer(),
            WsSyncInstalledAppsResponse(apps = apps),
          )
        WsDispatchOutcome(
          directReply =
            buildEnvelope(WsMessageType.SYNC_INSTALLED_APPS_RESPONSE, message.requestId, payload),
        )
      }

      WsMessageType.SYNC_LOGS_REQUEST -> {
        val logs = delegate.getAllLogs()
        val payload = json.encodeToString(WsSyncLogsResponse.serializer(), logs)
        WsDispatchOutcome(
          directReply =
            buildEnvelope(WsMessageType.SYNC_LOGS_RESPONSE, message.requestId, payload),
        )
      }

      WsMessageType.GET_ERROR_SCREENSHOT_REQUEST -> {
        val request =
          runCatching {
            json.decodeFromString(WsGetErrorScreenshotRequest.serializer(), message.payloadJson)
          }.getOrNull()
        if (request == null || (request.filePath.isNullOrBlank() && request.logId.isNullOrBlank())) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "GET_ERROR_SCREENSHOT_REQUEST requires filePath or logId.",
              ),
          )
        } else {
          delegate.getErrorScreenshot(request.filePath, request.logId).fold(
            onSuccess = { response ->
              WsDispatchOutcome(
                directReply =
                  buildEnvelope(
                    WsMessageType.GET_ERROR_SCREENSHOT_RESPONSE,
                    message.requestId,
                    json.encodeToString(WsGetErrorScreenshotResponse.serializer(), response),
                  ),
              )
            },
            onFailure = { err ->
              WsDispatchOutcome(
                directReply =
                  errorEnvelopeFromThrowable(message.requestId, err, "SCREENSHOT_NOT_FOUND"),
              )
            },
          )
        }
      }

      WsMessageType.START_LIVE_STREAM -> {
        val permitted = delegate.setClientLiveStreaming(clientId, true)
        if (permitted) {
          val eventPayload =
            json.encodeToString(
              WsExecutionLogEventPayload.serializer(),
              WsExecutionLogEventPayload(
                eventType = "LIVE_STREAM_STARTED",
                message = "Live binary screen streaming started.",
              ),
            )
          WsDispatchOutcome(
            directReply =
              buildEnvelope(WsMessageType.EXECUTION_LOG_EVENT, message.requestId, eventPayload),
          )
        } else {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "CAPTURE_UNAVAILABLE",
                message = "MediaProjection, Shizuku Silent Screenshot, or Accessibility (Android 11+) is required to stream screen.",
              ),
          )
        }
      }

      WsMessageType.STOP_LIVE_STREAM -> {
        delegate.setClientLiveStreaming(clientId, false)
        val eventPayload =
          json.encodeToString(
            WsExecutionLogEventPayload.serializer(),
            WsExecutionLogEventPayload(
              eventType = "LIVE_STREAM_STOPPED",
              message = "Live binary screen streaming stopped.",
            ),
          )
        WsDispatchOutcome(
          directReply =
            buildEnvelope(WsMessageType.EXECUTION_LOG_EVENT, message.requestId, eventPayload),
        )
      }

      WsMessageType.REMOTE_RUN_SCRIPT -> {
        val scriptId = decodeIdField(message.payloadJson) {
          json.decodeFromString(WsRemoteRunScriptPayload.serializer(), it).scriptId
        }
        if (scriptId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "REMOTE_RUN_SCRIPT failed: missing scriptId.",
              ),
          )
        } else {
          delegate.startRemoteRunScript(scriptId).fold(
            onSuccess = { script ->
              val event =
                WsExecutionLogEventPayload(
                  eventType = "REMOTE_RUN_STARTED",
                  scriptTitle = script.title,
                  phase = "RUNNING",
                  totalSteps = script.actions.size,
                  message = "Started '${script.title}' remotely.",
                )
              WsDispatchOutcome(
                directReply =
                  buildEnvelope(
                    WsMessageType.EXECUTION_LOG_EVENT,
                    message.requestId,
                    json.encodeToString(WsExecutionLogEventPayload.serializer(), event),
                  ),
              )
            },
            onFailure = { err ->
              WsDispatchOutcome(
                directReply =
                  errorEnvelopeFromThrowable(
                    message.requestId,
                    err,
                    "PERMISSIONS_OR_VISION_NOT_READY",
                  ),
              )
            },
          )
        }
      }

      WsMessageType.REMOTE_RUN_PIPELINE -> {
        val pipelineId = decodeIdField(message.payloadJson) {
          json.decodeFromString(WsRemoteRunPipelinePayload.serializer(), it).pipelineId
        }
        if (pipelineId.isNullOrBlank()) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "REMOTE_RUN_PIPELINE failed: missing pipelineId.",
              ),
          )
        } else {
          delegate.startRemoteRunPipeline(pipelineId).fold(
            onSuccess = { pipeline ->
              val event =
                WsExecutionLogEventPayload(
                  eventType = "REMOTE_RUN_STARTED",
                  scriptTitle = pipeline.title,
                  phase = "RUNNING",
                  totalPipelineSteps = pipeline.steps.size,
                  message = "Started pipeline '${pipeline.title}' remotely.",
                )
              WsDispatchOutcome(
                directReply =
                  buildEnvelope(
                    WsMessageType.EXECUTION_LOG_EVENT,
                    message.requestId,
                    json.encodeToString(WsExecutionLogEventPayload.serializer(), event),
                  ),
              )
            },
            onFailure = { err ->
              WsDispatchOutcome(
                directReply =
                  errorEnvelopeFromThrowable(
                    message.requestId,
                    err,
                    "PERMISSIONS_OR_VISION_NOT_READY",
                  ),
              )
            },
          )
        }
      }

      WsMessageType.REMOTE_STOP -> {
        delegate.stopRemoteRun()
        val payload =
          json.encodeToString(
            WsExecutionLogEventPayload.serializer(),
            WsExecutionLogEventPayload(
              eventType = "REMOTE_STOP_ACK",
              phase = "STOPPED",
              message = "Remote stop signal executed.",
            ),
          )
        WsDispatchOutcome(
          broadcastMessage =
            buildEnvelope(WsMessageType.EXECUTION_LOG_EVENT, message.requestId, payload),
        )
      }

      WsMessageType.REMOTE_INPUT_EVENT -> {
        val inputPayload =
          runCatching {
            json.decodeFromString(WsRemoteInputPayload.serializer(), message.payloadJson)
          }.getOrNull()
        if (inputPayload == null) {
          WsDispatchOutcome(
            directReply =
              errorEnvelope(
                requestId = message.requestId,
                code = "INVALID_PAYLOAD",
                message = "REMOTE_INPUT_EVENT failed: invalid input payload.",
              ),
          )
        } else {
          delegate.executeRemoteInput(inputPayload).fold(
            onSuccess = {
              if (message.requestId != null) {
                val ack =
                  json.encodeToString(
                    WsExecutionLogEventPayload.serializer(),
                    WsExecutionLogEventPayload(
                      eventType = "REMOTE_INPUT_ACK",
                      message = "Executed ${inputPayload.inputType.name}",
                    ),
                  )
                WsDispatchOutcome(
                  directReply = buildEnvelope(WsMessageType.EXECUTION_LOG_EVENT, message.requestId, ack),
                )
              } else {
                WsDispatchOutcome()
              }
            },
            onFailure = { err ->
              WsDispatchOutcome(
                directReply = errorEnvelopeFromThrowable(message.requestId, err, "REMOTE_INPUT_FAILED"),
              )
            },
          )
        }
      }

      WsMessageType.HANDSHAKE_ACK,
      WsMessageType.DEVICE_STATUS_EVENT,
      WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE,
      WsMessageType.SYNC_ALL_PIPELINES_RESPONSE,
      WsMessageType.SYNC_ALL_INSPECT_RESPONSE,
      WsMessageType.GET_INSPECT_IMAGE_RESPONSE,
      WsMessageType.CAPTURE_SNAPSHOT_RESPONSE,
      WsMessageType.SYNC_INSTALLED_APPS_RESPONSE,
      WsMessageType.SYNC_LOGS_RESPONSE,
      WsMessageType.GET_ERROR_SCREENSHOT_RESPONSE,
      WsMessageType.EXECUTION_LOG_EVENT,
      WsMessageType.ERROR_RESPONSE -> WsDispatchOutcome()
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

  private fun errorEnvelopeFromThrowable(
    requestId: String?,
    err: Throwable,
    fallbackCode: String,
  ): WsMessage {
    val protocolErr = err as? WsProtocolException
    return errorEnvelope(
      requestId = requestId,
      code = protocolErr?.code ?: fallbackCode,
      message = protocolErr?.message ?: err.message ?: "Request failed.",
      conflictUpdatedAtMs = protocolErr?.conflictUpdatedAtMs,
    )
  }

  private fun errorEnvelope(
    requestId: String?,
    code: String,
    message: String,
    conflictUpdatedAtMs: Long? = null,
  ): WsMessage {
    val payload =
      json.encodeToString(
        WsErrorPayload.serializer(),
        WsErrorPayload(
          code = code,
          message = message,
          conflictUpdatedAtMs = conflictUpdatedAtMs,
        ),
      )
    return buildEnvelope(WsMessageType.ERROR_RESPONSE, requestId, payload)
  }

  private fun decodePushScript(payloadJson: String): WsPushScriptPayload? {
    runCatching { return json.decodeFromString(WsPushScriptPayload.serializer(), payloadJson) }
    runCatching {
      val script = json.decodeFromString(Script.serializer(), payloadJson)
      return WsPushScriptPayload(script = script, forceOverwrite = false)
    }
    return null
  }

  private fun decodePushPipeline(payloadJson: String): WsPushPipelinePayload? {
    runCatching { return json.decodeFromString(WsPushPipelinePayload.serializer(), payloadJson) }
    runCatching {
      val pipeline = json.decodeFromString(Pipeline.serializer(), payloadJson)
      return WsPushPipelinePayload(pipeline = pipeline, forceOverwrite = false)
    }
    return null
  }

  private inline fun decodeIdField(
    payloadJson: String,
    crossinline structuredDecoder: (String) -> String,
  ): String? {
    runCatching { return structuredDecoder(payloadJson) }
    return payloadJson.trim().trim('"').takeIf { it.isNotBlank() && !it.startsWith("{") }
  }
}
