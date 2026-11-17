package com.tapflow.core.service.bridge

import android.content.Context
import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LocalBridgeServerController {
  val isServerRunning: StateFlow<Boolean>
  val activePort: StateFlow<Int>
  val isLiveStreamingFromConsole: StateFlow<Boolean>

  suspend fun startServerFromService(requestedPort: Int? = null): Boolean

  suspend fun stopServerFromService(reason: String? = null)

  fun stopDesktopLiveStream()
}

@Singleton
class LocalBridgeStateBridge @Inject constructor() {
  private val _isBridgeRunning = MutableStateFlow(false)
  val isBridgeRunning: StateFlow<Boolean> = _isBridgeRunning.asStateFlow()

  private val _isLiveStreamingFromConsole = MutableStateFlow(false)
  val isLiveStreamingFromConsole: StateFlow<Boolean> = _isLiveStreamingFromConsole.asStateFlow()

  fun setBridgeRunning(running: Boolean) {
    _isBridgeRunning.value = running
  }

  fun setLiveStreamingFromConsole(streaming: Boolean) {
    _isLiveStreamingFromConsole.value = streaming
  }
}

object LocalBridgeControlIntents {
  fun createStartIntent(
    context: Context,
    port: Int? = null,
  ): Intent =
    Intent(context, LocalBridgeService::class.java).apply {
      action = LocalBridgeService.ACTION_START
      if (port != null) {
        putExtra(LocalBridgeService.EXTRA_PORT, port)
      }
    }

  fun createStopIntent(context: Context): Intent =
    Intent(context, LocalBridgeService::class.java).apply {
      action = LocalBridgeService.ACTION_STOP
    }
}
