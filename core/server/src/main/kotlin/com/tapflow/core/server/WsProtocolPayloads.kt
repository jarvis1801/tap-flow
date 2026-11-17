package com.tapflow.core.server

import androidx.compose.runtime.Immutable
import com.tapflow.core.datastore.AppPreferences
import com.tapflow.core.model.ipc.WsDeleteScriptPayload
import com.tapflow.core.model.ipc.WsExecutionLogEventPayload
import com.tapflow.core.model.ipc.WsHandshakeAckPayload
import com.tapflow.core.model.ipc.WsHeartbeatPayload
import com.tapflow.core.model.ipc.WsPushScriptPayload
import com.tapflow.core.model.ipc.WsRemoteRunScriptPayload
import com.tapflow.core.model.ipc.WsSyncAllScriptsPayload
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
  val protocolVersion: Int = 2,
  val port: Int,
  val isRunning: Boolean,
  val connectedClients: Int,
)

typealias HandshakeAckPayload = WsHandshakeAckPayload
typealias HeartbeatPayload = WsHeartbeatPayload
typealias SyncAllScriptsPayload = WsSyncAllScriptsPayload
typealias PushScriptPayload = WsPushScriptPayload
typealias DeleteScriptPayload = WsDeleteScriptPayload
typealias RemoteRunScriptPayload = WsRemoteRunScriptPayload
typealias ExecutionLogEventPayload = WsExecutionLogEventPayload
