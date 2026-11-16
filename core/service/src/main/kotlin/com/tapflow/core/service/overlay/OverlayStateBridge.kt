package com.tapflow.core.service.overlay

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class OverlayStateBridge internal constructor(
  private val clockMs: () -> Long,
) {
  @Inject
  constructor() : this(clockMs = { SystemClock.elapsedRealtime() })

  private val _isOverlayRunning = MutableStateFlow(false)
  val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

  @Volatile
  private var suppressUntilElapsedMs: Long = 0L

  fun setOverlayRunning(running: Boolean) {
    _isOverlayRunning.value = running
  }

  fun suppressAutoCloseBriefly(durationMs: Long = DEFAULT_SUPPRESSION_DURATION_MS) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    suppressUntilElapsedMs = clockMs() + safeDuration
  }

  fun clearAutoCloseSuppression() {
    suppressUntilElapsedMs = 0L
  }

  fun shouldAutoCloseOnForeground(): Boolean =
    _isOverlayRunning.value && clockMs() > suppressUntilElapsedMs

  companion object {
    const val DEFAULT_SUPPRESSION_DURATION_MS = 1500L
  }
}
