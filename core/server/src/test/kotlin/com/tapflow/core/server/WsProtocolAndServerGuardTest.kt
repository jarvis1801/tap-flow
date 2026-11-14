package com.tapflow.core.server

import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.ipc.WsMessage
import com.tapflow.core.model.ipc.WsMessageType
import com.tapflow.core.model.script.Script
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WsProtocolAndServerGuardTest {

  private class FakeWsDelegate : WsServerDelegate {
    val savedScripts = linkedMapOf<String, Script>()
    var liveStreamClient: String? = null
    var liveStreamPermitted: Boolean = true
    var remoteStartedId: String? = null
    var remoteStopped: Boolean = false

    override val connectedClients: Int = 2
    override val currentRunnerPhaseName: String = "IDLE"

    override suspend fun getAllScripts(): List<Script> = savedScripts.values.toList()

    override suspend fun saveScript(script: Script) {
      savedScripts[script.id] = script
    }

    override suspend fun deleteScript(scriptId: String) {
      savedScripts.remove(scriptId)
    }

    override suspend fun setClientLiveStreaming(clientId: String, enabled: Boolean): Boolean {
      liveStreamClient = if (enabled) clientId else null
      return liveStreamPermitted
    }

    override suspend fun startRemoteRun(scriptId: String): Result<Script> {
      remoteStartedId = scriptId
      val found = savedScripts[scriptId]
      return if (found != null) Result.success(found)
      else Result.failure(IllegalArgumentException("Not found"))
    }

    override fun stopRemoteRun() {
      remoteStopped = true
    }
  }

  @Test
  fun heartbeatAndSyncAndPushDeleteAndRemoteRun_workEndToEnd() = runTest {
    val json = TapFlowJson.instance
    val delegate = FakeWsDelegate()
    val sampleScript =
      Script(
        id = "scr-1",
        title = "Farm Loop",
        actions = listOf(ScriptAction.Tap(id = "tap-1")),
      )

    // 1. HEARTBEAT
    val hbReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(type = WsMessageType.HEARTBEAT, requestId = "r1", timestamp = 100L),
      )
    val hbReply = WsMessageDispatcher.dispatchIncoming("c1", hbReq, delegate).directReply!!
    assertEquals(WsMessageType.HEARTBEAT, hbReply.type)
    assertEquals("r1", hbReply.requestId)
    val hbPayload = json.decodeFromString(HeartbeatPayload.serializer(), hbReply.payloadJson)
    assertTrue(hbPayload.alive)
    assertEquals(2, hbPayload.connectedClients)

    // 2. PUSH_SCRIPT -> broadcasts SYNC_ALL_SCRIPTS_RESPONSE
    val pushReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.PUSH_SCRIPT,
          requestId = "r2",
          timestamp = 200L,
          payloadJson = json.encodeToString(PushScriptPayload.serializer(), PushScriptPayload(sampleScript)),
        ),
      )
    val pushBroadcast = WsMessageDispatcher.dispatchIncoming("c1", pushReq, delegate).broadcastMessage!!
    assertEquals(WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE, pushBroadcast.type)
    val synced = json.decodeFromString(SyncAllScriptsPayload.serializer(), pushBroadcast.payloadJson)
    assertEquals(1, synced.scripts.size)
    assertEquals("Farm Loop", synced.scripts.first().title)

    // 3. REMOTE_RUN_SCRIPT & REMOTE_STOP
    val runReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.REMOTE_RUN_SCRIPT,
          requestId = "r3",
          timestamp = 300L,
          payloadJson = json.encodeToString(RemoteRunScriptPayload.serializer(), RemoteRunScriptPayload("scr-1")),
        ),
      )
    val runReply = WsMessageDispatcher.dispatchIncoming("c1", runReq, delegate).directReply!!
    assertEquals(WsMessageType.EXECUTION_LOG_EVENT, runReply.type)
    assertEquals("scr-1", delegate.remoteStartedId)

    val stopReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(type = WsMessageType.REMOTE_STOP, requestId = "r4", timestamp = 400L),
      )
    val stopBroadcast = WsMessageDispatcher.dispatchIncoming("c1", stopReq, delegate).broadcastMessage!!
    assertEquals(WsMessageType.EXECUTION_LOG_EVENT, stopBroadcast.type)
    assertTrue(delegate.remoteStopped)

    // 4. START_LIVE_STREAM / STOP_LIVE_STREAM
    val streamReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(type = WsMessageType.START_LIVE_STREAM, requestId = "r5", timestamp = 500L),
      )
    val streamReply = WsMessageDispatcher.dispatchIncoming("c1", streamReq, delegate).directReply!!
    assertNotNull(streamReply)
    assertEquals("c1", delegate.liveStreamClient)

    // 5. DELETE_SCRIPT
    val delReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.DELETE_SCRIPT,
          requestId = "r6",
          timestamp = 600L,
          payloadJson = json.encodeToString(DeleteScriptPayload.serializer(), DeleteScriptPayload("scr-1")),
        ),
      )
    val delBroadcast = WsMessageDispatcher.dispatchIncoming("c1", delReq, delegate).broadcastMessage!!
    val postDelete = json.decodeFromString(SyncAllScriptsPayload.serializer(), delBroadcast.payloadJson)
    assertEquals(0, postDelete.scripts.size)
  }

  @Test
  fun serverUiStatus_formatsAdbCommandCorrectly() {
    val status = ServerUiStatus(isRunning = true, port = 19090, connectedClientCount = 1)
    assertEquals("adb forward tcp:19090 tcp:19090", status.adbCommand)
  }
}
