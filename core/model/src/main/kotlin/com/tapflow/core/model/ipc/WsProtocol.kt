package com.tapflow.core.model.ipc

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.PipelineSessionLog
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import kotlinx.serialization.Serializable

@Serializable
enum class WsMessageType {
  // Connection & Device Status
  HANDSHAKE_ACK,
  HEARTBEAT,
  DEVICE_STATUS_EVENT,

  // Scripts & Pipelines
  SYNC_ALL_SCRIPTS_REQUEST,
  SYNC_ALL_SCRIPTS_RESPONSE,
  PUSH_SCRIPT,
  DELETE_SCRIPT,
  SYNC_ALL_PIPELINES_REQUEST,
  SYNC_ALL_PIPELINES_RESPONSE,
  PUSH_PIPELINE,
  DELETE_PIPELINE,

  // Inspect Assets & Lossless Snapshot
  SYNC_ALL_INSPECT_REQUEST,
  SYNC_ALL_INSPECT_RESPONSE,
  GET_INSPECT_IMAGE_REQUEST,
  GET_INSPECT_IMAGE_RESPONSE,
  PUSH_INSPECT_ITEM,
  DELETE_INSPECT_GROUP,
  DELETE_INSPECT_ITEM,
  CAPTURE_SNAPSHOT_REQUEST,
  CAPTURE_SNAPSHOT_RESPONSE,

  // Installed Apps, Logs & Error Screenshots
  SYNC_INSTALLED_APPS_REQUEST,
  SYNC_INSTALLED_APPS_RESPONSE,
  SYNC_LOGS_REQUEST,
  SYNC_LOGS_RESPONSE,
  GET_ERROR_SCREENSHOT_REQUEST,
  GET_ERROR_SCREENSHOT_RESPONSE,

  // Live Stream, Remote Run & Input
  START_LIVE_STREAM,
  STOP_LIVE_STREAM,
  REMOTE_RUN_SCRIPT,
  REMOTE_RUN_PIPELINE,
  REMOTE_STOP,
  REMOTE_INPUT_EVENT,
  EXECUTION_LOG_EVENT,
  ERROR_RESPONSE,
}

@Immutable
@Serializable
data class WsMessage(
  val type: WsMessageType,
  val requestId: String? = null,
  val timestamp: Long,
  val payloadJson: String = "{}",
)

@Immutable
@Serializable
data class WsHandshakeAckPayload(
  val serverName: String = "TapFlow",
  val protocolVersion: Int = 2,
  val port: Int,
  val capturePermitted: Boolean,
)

@Immutable
@Serializable
data class WsHeartbeatPayload(
  val alive: Boolean = true,
  val connectedClients: Int,
  val runnerPhase: String,
)

@Immutable
@Serializable
data class WsDeviceStatusPayload(
  val deviceName: String,
  val androidVersion: String,
  val screenWidth: Int,
  val screenHeight: Int,
  val rotationDegrees: Int,
  val isAccessibilityReady: Boolean,
  val isShizukuReady: Boolean,
  val isMediaProjectionActive: Boolean,
  val isSilentScreenshotEnabled: Boolean,
  val currentForegroundPackage: String? = null,
  val isLifetimePro: Boolean,
)

@Immutable
@Serializable
data class WsSyncAllScriptsPayload(
  val scripts: List<Script>,
)

@Immutable
@Serializable
data class WsPushScriptPayload(
  val script: Script,
  val forceOverwrite: Boolean = false,
)

@Immutable
@Serializable
data class WsDeleteScriptPayload(
  val scriptId: String,
)

@Immutable
@Serializable
data class WsSyncAllPipelinesPayload(
  val pipelines: List<Pipeline>,
)

@Immutable
@Serializable
data class WsPushPipelinePayload(
  val pipeline: Pipeline,
  val forceOverwrite: Boolean = false,
)

@Immutable
@Serializable
data class WsDeletePipelinePayload(
  val pipelineId: String,
)

@Immutable
@Serializable
data class WsInspectGroupBundleDto(
  val group: InspectGroup,
  val items: List<InspectItem>,
)

@Immutable
@Serializable
data class WsSyncAllInspectPayload(
  val groups: List<WsInspectGroupBundleDto>,
)

@Immutable
@Serializable
data class WsGetInspectImageRequest(
  val itemId: String,
)

@Immutable
@Serializable
data class WsGetInspectImageResponse(
  val itemId: String,
  val width: Int,
  val height: Int,
  val pngBase64: String,
)

@Immutable
@Serializable
data class WsPushInspectItemPayload(
  val groupTitle: String = "",
  val item: InspectItem,
  val pngBase64: String? = null,
)

@Immutable
@Serializable
data class WsDeleteInspectGroupPayload(
  val groupId: String,
)

@Immutable
@Serializable
data class WsDeleteInspectItemPayload(
  val itemId: String,
)

@Immutable
@Serializable
data class WsCaptureSnapshotResponse(
  val width: Int,
  val height: Int,
  val rotationDegrees: Int,
  val appName: String,
  val packageName: String,
  val pngBase64: String,
)

@Immutable
@Serializable
data class WsInstalledAppDto(
  val packageName: String,
  val appName: String,
  val iconPngBase64: String? = null,
  val isLaunchable: Boolean = true,
)

@Immutable
@Serializable
data class WsSyncInstalledAppsResponse(
  val apps: List<WsInstalledAppDto>,
)

@Immutable
@Serializable
data class WsSyncLogsResponse(
  val scriptLogs: List<ExecutionSessionLog>,
  val pipelineLogs: List<PipelineSessionLog>,
)

@Immutable
@Serializable
data class WsGetErrorScreenshotRequest(
  val filePath: String? = null,
  val logId: String? = null,
)

@Immutable
@Serializable
data class WsGetErrorScreenshotResponse(
  val filePath: String,
  val logId: String? = null,
  val width: Int,
  val height: Int,
  val pngBase64: String,
)

@Immutable
@Serializable
data class WsRemoteRunScriptPayload(
  val scriptId: String,
)

@Immutable
@Serializable
data class WsRemoteRunPipelinePayload(
  val pipelineId: String,
)

@Serializable
enum class WsRemoteInputType {
  TAP,
  SWIPE,
  GLOBAL_BACK,
  GLOBAL_HOME,
}

@Immutable
@Serializable
data class WsRemoteInputPayload(
  val inputType: WsRemoteInputType,
  val x: Int = 0,
  val y: Int = 0,
  val endX: Int = 0,
  val endY: Int = 0,
  val durationMs: Long = 100L,
)

@Immutable
@Serializable
data class WsExecutionLogEventPayload(
  val eventType: String,
  val scriptTitle: String = "",
  val phase: String = "",
  val currentStep: Int = 0,
  val totalSteps: Int = 0,
  val message: String? = null,
  val actionLog: ActionLogEntry? = null,
  val pipelineStep: Int = 0,
  val totalPipelineSteps: Int = 0,
)

@Immutable
@Serializable
data class WsErrorPayload(
  val code: String,
  val message: String,
  val conflictUpdatedAtMs: Long? = null,
)
