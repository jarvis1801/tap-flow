package com.tapflow.core.server

import androidx.compose.runtime.Immutable
import com.tapflow.core.datastore.AppPreferences
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.script.Script
import kotlinx.serialization.Serializable

@Immutable
data class ServerUiStatus(
  val isRunning: Boolean = false,
  val port: Int = AppPreferences.DEFAULT_KTOR_PORT,
  val connectedClientCount: Int = 0,
  val adbCommand: String = "adb forward tcp:$port tcp:$port",
  val isLiveStreaming: Boolean = false,
  val lastError: String? = null,
)

@Immutable
@Serializable
data class ServerHealthResponse(
  val app: String = "TapFlow",
  val protocolVersion: Int = 1,
  val port: Int,
  val isRunning: Boolean,
  val connectedClients: Int,
)

@Immutable
@Serializable
data class HandshakeAckPayload(
  val serverName: String = "TapFlow",
  val protocolVersion: Int = 1,
  val port: Int,
  val capturePermitted: Boolean,
)

@Immutable
@Serializable
data class HeartbeatPayload(
  val alive: Boolean = true,
  val connectedClients: Int,
  val runnerPhase: String,
)

@Immutable
@Serializable
data class SyncAllScriptsPayload(
  val scripts: List<Script>,
)

@Immutable
@Serializable
data class PushScriptPayload(
  val script: Script,
)

@Immutable
@Serializable
data class DeleteScriptPayload(
  val scriptId: String,
)

@Immutable
@Serializable
data class RemoteRunScriptPayload(
  val scriptId: String,
)

@Immutable
@Serializable
data class ExecutionLogEventPayload(
  val eventType: String,
  val scriptTitle: String = "",
  val phase: String = "",
  val currentStep: Int = 0,
  val totalSteps: Int = 0,
  val message: String? = null,
  val actionLog: ActionLogEntry? = null,
)
