package com.tapflow.core.service.projection

import android.content.Context
import android.content.Intent
import android.media.Image
import android.media.ImageReader
import androidx.compose.runtime.Immutable
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
sealed interface ScreenCaptureState {
  data object Idle : ScreenCaptureState

  @Immutable
  data class Active(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
  ) : ScreenCaptureState

  @Immutable
  data class StoppedBySystem(
    val reason: String,
  ) : ScreenCaptureState

  @Immutable
  data class Error(
    val message: String,
  ) : ScreenCaptureState
}

@Singleton
class MediaProjectionBridge @Inject constructor() {
  private val readerLock = ReentrantLock()
  private var activeImageReader: ImageReader? = null

  private val _captureState = MutableStateFlow<ScreenCaptureState>(ScreenCaptureState.Idle)
  val captureState: StateFlow<ScreenCaptureState> = _captureState.asStateFlow()

  val isCaptureActive: Boolean
    get() = _captureState.value is ScreenCaptureState.Active

  fun createStartIntent(
    context: Context,
    resultCode: Int,
    resultData: Intent,
  ): Intent =
    Intent(context, MediaProjectionService::class.java).apply {
      action = MediaProjectionService.ACTION_START
      putExtra(MediaProjectionService.EXTRA_RESULT_CODE, resultCode)
      putExtra(MediaProjectionService.EXTRA_RESULT_DATA, resultData)
    }

  fun createStopIntent(context: Context): Intent =
    Intent(context, MediaProjectionService::class.java).apply {
      action = MediaProjectionService.ACTION_STOP
    }

  internal fun swapActiveReader(
    newReader: ImageReader,
    width: Int,
    height: Int,
    densityDpi: Int,
  ) {
    val previous =
      readerLock.withLock {
        val old = activeImageReader
        activeImageReader = newReader
        old
      }
    runCatching { previous?.close() }
    _captureState.value = ScreenCaptureState.Active(width = width, height = height, densityDpi = densityDpi)
  }

  internal fun clearReader(nextState: ScreenCaptureState = ScreenCaptureState.Idle) {
    val previous =
      readerLock.withLock {
        val old = activeImageReader
        activeImageReader = null
        old
      }
    runCatching { previous?.close() }
    _captureState.value = nextState
  }

  internal fun emitError(message: String) {
    clearReader(ScreenCaptureState.Error(message))
  }

  /**
   * Acquires the latest frame under [readerLock] and guarantees [Image.close] via `.use { }`
   * so ImageReader surfaces never leak buffers or race against orientation resize rebuilds.
   */
  fun <T> withLatestImage(block: (Image) -> T): T? {
    return readerLock.withLock {
      val reader = activeImageReader ?: return@withLock null
      val image =
        runCatching { reader.acquireLatestImage() }.getOrNull()
          ?: return@withLock null
      image.use { acquired ->
        block(acquired)
      }
    }
  }
}
