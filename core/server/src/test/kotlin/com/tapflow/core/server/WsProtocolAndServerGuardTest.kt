package com.tapflow.core.server

import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.ipc.WsCaptureSnapshotResponse
import com.tapflow.core.model.ipc.WsDeleteInspectGroupPayload
import com.tapflow.core.model.ipc.WsDeleteInspectItemPayload
import com.tapflow.core.model.ipc.WsDeletePipelinePayload
import com.tapflow.core.model.ipc.WsDeleteScriptPayload
import com.tapflow.core.model.ipc.WsDeviceStatusPayload
import com.tapflow.core.model.ipc.WsErrorPayload
import com.tapflow.core.model.ipc.WsGetErrorScreenshotRequest
import com.tapflow.core.model.ipc.WsGetErrorScreenshotResponse
import com.tapflow.core.model.ipc.WsGetInspectImageRequest
import com.tapflow.core.model.ipc.WsGetInspectImageResponse
import com.tapflow.core.model.ipc.WsInspectGroupBundleDto
import com.tapflow.core.model.ipc.WsInstalledAppDto
import com.tapflow.core.model.ipc.WsMessage
import com.tapflow.core.model.ipc.WsMessageType
import com.tapflow.core.model.ipc.WsPushInspectItemPayload
import com.tapflow.core.model.ipc.WsPushPipelinePayload
import com.tapflow.core.model.ipc.WsPushScriptPayload
import com.tapflow.core.model.ipc.WsRemoteInputPayload
import com.tapflow.core.model.ipc.WsRemoteInputType
import com.tapflow.core.model.ipc.WsRemoteRunPipelinePayload
import com.tapflow.core.model.ipc.WsRemoteRunScriptPayload
import com.tapflow.core.model.ipc.WsSyncAllInspectPayload
import com.tapflow.core.model.ipc.WsSyncAllPipelinesPayload
import com.tapflow.core.model.ipc.WsSyncAllScriptsPayload
import com.tapflow.core.model.ipc.WsSyncInstalledAppsResponse
import com.tapflow.core.model.ipc.WsSyncLogsResponse
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.ExecutionStatus
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.pipeline.PipelineStep
import com.tapflow.core.model.script.Script
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WsProtocolAndServerGuardTest {

  private class FakeWsDelegate : WsServerDelegate {
    val savedScripts = linkedMapOf<String, Script>()
    val savedPipelines = linkedMapOf<String, Pipeline>()
    val savedInspectGroups = linkedMapOf<String, InspectGroup>()
    val savedInspectItems = linkedMapOf<String, InspectItem>()
    val executedInputs = mutableListOf<WsRemoteInputPayload>()
    var liveStreamClient: String? = null
    var liveStreamPermitted: Boolean = true
    var remoteStartedId: String? = null
    var remotePipelineId: String? = null
    var remoteRunBlocked: Boolean = false
    var remoteStopped: Boolean = false

    override val connectedClients: Int = 2
    override val currentRunnerPhaseName: String = "IDLE"

    override suspend fun getDeviceStatus(): WsDeviceStatusPayload =
      WsDeviceStatusPayload(
        deviceName = "Pixel 9",
        androidVersion = "15 (API 35)",
        screenWidth = 1080,
        screenHeight = 2400,
        rotationDegrees = 0,
        isAccessibilityReady = true,
        isShizukuReady = true,
        isMediaProjectionActive = true,
        isSilentScreenshotEnabled = false,
        currentForegroundPackage = "com.game.test",
        isLifetimePro = true,
      )

    override suspend fun getAllScripts(): List<Script> = savedScripts.values.toList()

    override suspend fun pushScript(script: Script, forceOverwrite: Boolean): Result<List<Script>> {
      val existing = savedScripts[script.id]
      if (!forceOverwrite && existing != null && existing.updatedAtMs > script.updatedAtMs) {
        return Result.failure(
          WsProtocolException(
            code = "VERSION_CONFLICT",
            message = "Version conflict",
            conflictUpdatedAtMs = existing.updatedAtMs,
          ),
        )
      }
      savedScripts[script.id] = script
      return Result.success(savedScripts.values.toList())
    }

    override suspend fun deleteScript(scriptId: String): List<Script> {
      savedScripts.remove(scriptId)
      return savedScripts.values.toList()
    }

    override suspend fun getAllPipelines(): List<Pipeline> = savedPipelines.values.toList()

    override suspend fun pushPipeline(
      pipeline: Pipeline,
      forceOverwrite: Boolean,
    ): Result<List<Pipeline>> {
      savedPipelines[pipeline.id] = pipeline
      return Result.success(savedPipelines.values.toList())
    }

    override suspend fun deletePipeline(pipelineId: String): List<Pipeline> {
      savedPipelines.remove(pipelineId)
      return savedPipelines.values.toList()
    }

    override suspend fun getAllInspectGroups(): List<WsInspectGroupBundleDto> {
      val itemsByGroup = savedInspectItems.values.groupBy { it.groupId }
      return savedInspectGroups.values.map { group ->
        WsInspectGroupBundleDto(group = group, items = itemsByGroup[group.id].orEmpty())
      }
    }

    override suspend fun getInspectImage(itemId: String): Result<WsGetInspectImageResponse> {
      val item = savedInspectItems[itemId]
        ?: return Result.failure(WsProtocolException("INSPECT_IMAGE_NOT_FOUND", "Not found"))
      return Result.success(
        WsGetInspectImageResponse(
          itemId = item.id,
          width = item.screenWidth,
          height = item.screenHeight,
          pngBase64 = "iVBORw0KGgo=",
        ),
      )
    }

    override suspend fun pushInspectItem(
      groupTitle: String,
      item: InspectItem,
      pngBase64: String?,
    ): Result<List<WsInspectGroupBundleDto>> {
      savedInspectGroups.putIfAbsent(
        item.groupId,
        InspectGroup(id = item.groupId, title = groupTitle.ifBlank { "Studio" }),
      )
      savedInspectItems[item.id] = item
      return Result.success(getAllInspectGroups())
    }

    override suspend fun deleteInspectGroup(groupId: String): List<WsInspectGroupBundleDto> {
      savedInspectGroups.remove(groupId)
      savedInspectItems.entries.removeAll { it.value.groupId == groupId }
      return getAllInspectGroups()
    }

    override suspend fun deleteInspectItem(itemId: String): List<WsInspectGroupBundleDto> {
      savedInspectItems.remove(itemId)
      return getAllInspectGroups()
    }

    override suspend fun captureLosslessSnapshot(): Result<WsCaptureSnapshotResponse> =
      Result.success(
        WsCaptureSnapshotResponse(
          width = 1080,
          height = 2400,
          rotationDegrees = 90,
          appName = "Test Game",
          packageName = "com.game.test",
          pngBase64 = "iVBORw0KGgo=",
        ),
      )

    override suspend fun getInstalledApps(): List<WsInstalledAppDto> =
      listOf(
        WsInstalledAppDto(
          packageName = "com.game.test",
          appName = "Test Game",
          iconPngBase64 = "iVBORw0KGgo=",
          isLaunchable = true,
        ),
      )

    override suspend fun getAllLogs(): WsSyncLogsResponse =
      WsSyncLogsResponse(
        scriptLogs =
          listOf(
            ExecutionSessionLog(
              id = "log-1",
              scriptId = "scr-1",
              scriptTitle = "Farm Loop",
              status = ExecutionStatus.SUCCESS,
              startedAtMs = 100L,
              finishedAtMs = 250L,
              totalStepsExecuted = 2,
              errorScreenshotPath = "/data/user/0/tapflow/files/err.png",
            ),
          ),
        pipelineLogs = emptyList(),
      )

    override suspend fun getErrorScreenshot(
      filePath: String?,
      logId: String?,
    ): Result<WsGetErrorScreenshotResponse> =
      Result.success(
        WsGetErrorScreenshotResponse(
          filePath = filePath ?: "/data/user/0/tapflow/files/err.png",
          logId = logId,
          width = 1080,
          height = 2400,
          pngBase64 = "iVBORw0KGgo=",
        ),
      )

    override suspend fun setClientLiveStreaming(clientId: String, enabled: Boolean): Boolean {
      liveStreamClient = if (enabled) clientId else null
      return liveStreamPermitted
    }

    override suspend fun startRemoteRunScript(scriptId: String): Result<Script> {
      if (remoteRunBlocked) {
        return Result.failure(
          WsProtocolException("PERMISSIONS_OR_VISION_NOT_READY", "Missing Accessibility"),
        )
      }
      remoteStartedId = scriptId
      val found = savedScripts[scriptId]
      return if (found != null) Result.success(found)
      else Result.failure(WsProtocolException("TARGET_NOT_FOUND", "Not found"))
    }

    override suspend fun startRemoteRunPipeline(pipelineId: String): Result<Pipeline> {
      if (remoteRunBlocked) {
        return Result.failure(
          WsProtocolException("PERMISSIONS_OR_VISION_NOT_READY", "Missing Vision"),
        )
      }
      remotePipelineId = pipelineId
      val found = savedPipelines[pipelineId]
      return if (found != null) Result.success(found)
      else Result.failure(WsProtocolException("TARGET_NOT_FOUND", "Not found"))
    }

    override fun stopRemoteRun() {
      remoteStopped = true
    }

    override suspend fun executeRemoteInput(input: WsRemoteInputPayload): Result<Unit> {
      executedInputs += input
      return Result.success(Unit)
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
        updatedAtMs = 500L,
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

    // 2b. Optimistic VERSION_CONFLICT when pushing stale script without forceOverwrite
    val stalePush =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.PUSH_SCRIPT,
          requestId = "r2b",
          timestamp = 205L,
          payloadJson =
            json.encodeToString(
              WsPushScriptPayload.serializer(),
              WsPushScriptPayload(sampleScript.copy(title = "Stale", updatedAtMs = 400L), forceOverwrite = false),
            ),
        ),
      )
    val conflictReply = WsMessageDispatcher.dispatchIncoming("c1", stalePush, delegate).directReply!!
    assertEquals(WsMessageType.ERROR_RESPONSE, conflictReply.type)
    val conflictErr = json.decodeFromString(WsErrorPayload.serializer(), conflictReply.payloadJson)
    assertEquals("VERSION_CONFLICT", conflictErr.code)
    assertEquals(500L, conflictErr.conflictUpdatedAtMs)

    // 2c. Force overwrite bypasses conflict
    val forcePush =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.PUSH_SCRIPT,
          requestId = "r2c",
          timestamp = 210L,
          payloadJson =
            json.encodeToString(
              WsPushScriptPayload.serializer(),
              WsPushScriptPayload(sampleScript.copy(title = "Forced", updatedAtMs = 400L), forceOverwrite = true),
            ),
        ),
      )
    val forcedBroadcast = WsMessageDispatcher.dispatchIncoming("c1", forcePush, delegate).broadcastMessage!!
    assertEquals(WsMessageType.SYNC_ALL_SCRIPTS_RESPONSE, forcedBroadcast.type)

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
  fun extendedDesktopStudioProtocolMessages_dispatchCleanly() = runTest {
    val json = TapFlowJson.instance
    val delegate = FakeWsDelegate()

    // Pipelines ([Missing Script]-safe)
    val pipeline =
      Pipeline(
        id = "pipe-1",
        title = "Daily Routine",
        steps = listOf(PipelineStep(id = "st-1", scriptId = "missing-script-id")),
      )
    val pushPipeReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.PUSH_PIPELINE,
          requestId = "p1",
          timestamp = 1L,
          payloadJson = json.encodeToString(WsPushPipelinePayload.serializer(), WsPushPipelinePayload(pipeline)),
        ),
      )
    val pipeBroadcast = WsMessageDispatcher.dispatchIncoming("c1", pushPipeReq, delegate).broadcastMessage!!
    assertEquals(WsMessageType.SYNC_ALL_PIPELINES_RESPONSE, pipeBroadcast.type)
    val pipesSynced = json.decodeFromString(WsSyncAllPipelinesPayload.serializer(), pipeBroadcast.payloadJson)
    assertEquals(1, pipesSynced.pipelines.size)

    // Blocked remote run returns ERROR_RESPONSE(PERMISSIONS_OR_VISION_NOT_READY)
    delegate.remoteRunBlocked = true
    val runPipeReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.REMOTE_RUN_PIPELINE,
          requestId = "p2",
          timestamp = 2L,
          payloadJson =
            json.encodeToString(WsRemoteRunPipelinePayload.serializer(), WsRemoteRunPipelinePayload("pipe-1")),
        ),
      )
    val runPipeReply = WsMessageDispatcher.dispatchIncoming("c1", runPipeReq, delegate).directReply!!
    assertEquals(WsMessageType.ERROR_RESPONSE, runPipeReply.type)
    val err = json.decodeFromString(WsErrorPayload.serializer(), runPipeReply.payloadJson)
    assertEquals("PERMISSIONS_OR_VISION_NOT_READY", err.code)

    // Delete Pipeline
    val delPipeReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.DELETE_PIPELINE,
          requestId = "p3",
          timestamp = 3L,
          payloadJson = json.encodeToString(WsDeletePipelinePayload.serializer(), WsDeletePipelinePayload("pipe-1")),
        ),
      )
    val delPipeBroadcast = WsMessageDispatcher.dispatchIncoming("c1", delPipeReq, delegate).broadcastMessage!!
    assertEquals(WsMessageType.SYNC_ALL_PIPELINES_RESPONSE, delPipeBroadcast.type)

    // Inspect push / sync / get image / delete item / delete group
    val item =
      InspectItem(
        id = "item-1",
        groupId = "grp-1",
        fullScreenPngPath = "/tmp/item1.png",
        screenWidth = 1080,
        screenHeight = 2400,
        appName = "Game",
        packageName = "com.game.test",
      )
    val pushInspectReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.PUSH_INSPECT_ITEM,
          requestId = "i1",
          timestamp = 4L,
          payloadJson =
            json.encodeToString(
              WsPushInspectItemPayload.serializer(),
              WsPushInspectItemPayload(groupTitle = "Lobby", item = item, pngBase64 = "iVBORw0KGgo="),
            ),
        ),
      )
    val inspectBroadcast = WsMessageDispatcher.dispatchIncoming("c1", pushInspectReq, delegate).broadcastMessage!!
    assertEquals(WsMessageType.SYNC_ALL_INSPECT_RESPONSE, inspectBroadcast.type)
    val inspectGroups = json.decodeFromString(WsSyncAllInspectPayload.serializer(), inspectBroadcast.payloadJson)
    assertEquals("Lobby", inspectGroups.groups.first().group.title)

    val getImageReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.GET_INSPECT_IMAGE_REQUEST,
          requestId = "i2",
          timestamp = 5L,
          payloadJson = json.encodeToString(WsGetInspectImageRequest.serializer(), WsGetInspectImageRequest("item-1")),
        ),
      )
    val getImageReply = WsMessageDispatcher.dispatchIncoming("c1", getImageReq, delegate).directReply!!
    assertEquals(WsMessageType.GET_INSPECT_IMAGE_RESPONSE, getImageReply.type)

    val delItemReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.DELETE_INSPECT_ITEM,
          requestId = "i3",
          timestamp = 6L,
          payloadJson =
            json.encodeToString(WsDeleteInspectItemPayload.serializer(), WsDeleteInspectItemPayload("item-1")),
        ),
      )
    assertEquals(
      WsMessageType.SYNC_ALL_INSPECT_RESPONSE,
      WsMessageDispatcher.dispatchIncoming("c1", delItemReq, delegate).broadcastMessage!!.type,
    )

    val delGroupReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.DELETE_INSPECT_GROUP,
          requestId = "i4",
          timestamp = 7L,
          payloadJson =
            json.encodeToString(WsDeleteInspectGroupPayload.serializer(), WsDeleteInspectGroupPayload("grp-1")),
        ),
      )
    assertEquals(
      WsMessageType.SYNC_ALL_INSPECT_RESPONSE,
      WsMessageDispatcher.dispatchIncoming("c1", delGroupReq, delegate).broadcastMessage!!.type,
    )

    // Snapshot, Installed Apps, Logs & Error Screenshot
    val snapReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(type = WsMessageType.CAPTURE_SNAPSHOT_REQUEST, requestId = "s1", timestamp = 8L),
      )
    val snapReply = WsMessageDispatcher.dispatchIncoming("c1", snapReq, delegate).directReply!!
    assertEquals(WsMessageType.CAPTURE_SNAPSHOT_RESPONSE, snapReply.type)
    val snapPayload = json.decodeFromString(WsCaptureSnapshotResponse.serializer(), snapReply.payloadJson)
    assertEquals(90, snapPayload.rotationDegrees)

    val appsReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(type = WsMessageType.SYNC_INSTALLED_APPS_REQUEST, requestId = "a1", timestamp = 9L),
      )
    val appsReply = WsMessageDispatcher.dispatchIncoming("c1", appsReq, delegate).directReply!!
    assertEquals(WsMessageType.SYNC_INSTALLED_APPS_RESPONSE, appsReply.type)
    val appsPayload = json.decodeFromString(WsSyncInstalledAppsResponse.serializer(), appsReply.payloadJson)
    assertEquals("com.game.test", appsPayload.apps.first().packageName)

    val logsReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(type = WsMessageType.SYNC_LOGS_REQUEST, requestId = "l1", timestamp = 10L),
      )
    val logsReply = WsMessageDispatcher.dispatchIncoming("c1", logsReq, delegate).directReply!!
    assertEquals(WsMessageType.SYNC_LOGS_RESPONSE, logsReply.type)

    val errShotReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.GET_ERROR_SCREENSHOT_REQUEST,
          requestId = "l2",
          timestamp = 11L,
          payloadJson =
            json.encodeToString(WsGetErrorScreenshotRequest.serializer(), WsGetErrorScreenshotRequest(logId = "log-1")),
        ),
      )
    val errShotReply = WsMessageDispatcher.dispatchIncoming("c1", errShotReq, delegate).directReply!!
    assertEquals(WsMessageType.GET_ERROR_SCREENSHOT_RESPONSE, errShotReply.type)

    // Remote input
    val inputReq =
      WsMessageDispatcher.encodeEnvelope(
        WsMessage(
          type = WsMessageType.REMOTE_INPUT_EVENT,
          requestId = "in1",
          timestamp = 12L,
          payloadJson =
            json.encodeToString(
              WsRemoteInputPayload.serializer(),
              WsRemoteInputPayload(inputType = WsRemoteInputType.TAP, x = 320, y = 640),
            ),
        ),
      )
    val inputOutcome = WsMessageDispatcher.dispatchIncoming("c1", inputReq, delegate)
    assertEquals(WsMessageType.EXECUTION_LOG_EVENT, inputOutcome.directReply!!.type)
    assertEquals(WsRemoteInputType.TAP, delegate.executedInputs.single().inputType)
  }

  @Test
  fun liveStreamBinaryFrame_writesBigEndian12ByteHeader() {
    val jpegPayload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())
    val frame =
      TapFlowLocalServer.encodeLiveStreamBinaryFrame(
        width = 540,
        height = 960,
        rotationDegrees = 270,
        jpegBytes = jpegPayload,
      )
    assertEquals(12 + jpegPayload.size, frame.size)
    val buffer = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
    assertEquals(540, buffer.getInt())
    assertEquals(960, buffer.getInt())
    assertEquals(270, buffer.getInt())
    val remaining = ByteArray(jpegPayload.size)
    buffer.get(remaining)
    assertArrayEquals(jpegPayload, remaining)
  }

  @Test
  fun serverUiStatus_formatsAdbCommandCorrectly() {
    val status = ServerUiStatus(isRunning = true, port = 19090, connectedClientCount = 1)
    assertEquals("adb forward tcp:19090 tcp:19090", status.adbCommand)
  }
}
