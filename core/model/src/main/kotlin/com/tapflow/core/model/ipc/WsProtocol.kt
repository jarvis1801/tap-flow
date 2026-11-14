package com.tapflow.core.model.ipc

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class WsMessageType {
  HANDSHAKE_ACK,
  HEARTBEAT,
  SYNC_ALL_SCRIPTS_REQUEST,
  SYNC_ALL_SCRIPTS_RESPONSE,
  PUSH_SCRIPT,
  DELETE_SCRIPT,
  START_LIVE_STREAM,
  STOP_LIVE_STREAM,
  REMOTE_RUN_SCRIPT,
  REMOTE_STOP,
  EXECUTION_LOG_EVENT,
}

@Immutable
@Serializable
data class WsMessage(
  val type: WsMessageType,
  val requestId: String? = null,
  val timestamp: Long,
  val payloadJson: String = "{}",
)
