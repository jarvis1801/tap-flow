package com.tapflow.core.service.overlay

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.tapflow.core.model.action.Point2D
import com.tapflow.core.service.engine.RunnerExecutionSnapshot
import com.tapflow.core.service.projection.ScreenCaptureState
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

enum class OverlayMode {
  IDLE,
  RUNNER_SCRIPT,
  RUNNER_PIPELINE,
  GESTURE_PICKER,
  LIVE_RECORDER,
  LIVE_INSPECTOR,
}

enum class OverlaySessionState {
  STANDBY,
  ACTIVE,
  PAUSED,
}

enum class OverlayCapturePhase {
  IDLE,
  INTERCEPTING,
  CAPTURING_CLEAN_FRAME,
  PASSTHROUGH_DISABLED,
  REPLAYING,
  RESTORING,
}

@Immutable
data class OverlayTouchPreview(
  val isTouching: Boolean = false,
  val startX: Float = 0f,
  val startY: Float = 0f,
  val currentX: Float = 0f,
  val currentY: Float = 0f,
  val pathPoints: List<Point2D> = emptyList(),
  val secondaryPoints: List<Point2D> = emptyList(),
  val durationMs: Long = 0L,
  val isLongPress: Boolean = false,
  val isDrag: Boolean = false,
)

@Immutable
data class OverlayUiState(
  val mode: OverlayMode = OverlayMode.IDLE,
  val sessionState: OverlaySessionState = OverlaySessionState.STANDBY,
  val isBubbleCollapsed: Boolean = false,
  val isDockedOnRight: Boolean = false,
  val isCaptureActive: Boolean = sessionState == OverlaySessionState.ACTIVE,
  val capturePhase: OverlayCapturePhase = OverlayCapturePhase.IDLE,
  val pickerTargetType: GesturePickerTargetType? = null,
  val touchPreview: OverlayTouchPreview? = null,
  val runnerSnapshot: RunnerExecutionSnapshot = RunnerExecutionSnapshot(),
  val captureState: ScreenCaptureState = ScreenCaptureState.Idle,
  val captureAlertMessage: String? = null,
  val recordedActionCount: Int = 0,
  val inspectedItemCount: Int = 0,
  val lastSavedScriptTitle: String? = null,
  val statusHint: String? = null,
)

interface OverlayCallbacks {
  fun onPlayPause()
  fun onPreviousStep()
  fun onNextStep()
  fun onStopExecution()
  fun onToggleCaptureListening()
  fun onStartOrResumeCapture()
  fun onPauseCapture()
  fun onStopCapture()
  fun onToggleBubbleCollapse()
  fun onCloseOverlay()
  fun onCancelCaptureMode()
  fun onDismissCaptureAlert()
  fun onReturnToTapFlow()
  fun onDragDelta(dx: Float, dy: Float)
  fun onDragEnd()
}

interface OverlayContentProvider {
  @Composable
  fun FloatingDockContent(
    state: OverlayUiState,
    callbacks: OverlayCallbacks,
  )

  @Composable
  fun CaptureLayerFeedbackContent(state: OverlayUiState)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OverlayOptionalModule {
  @BindsOptionalOf
  abstract fun optionalOverlayContentProvider(): OverlayContentProvider
}

object OverlayLaunchIntents {
  fun createRunnerScriptIntent(context: Context, scriptId: String): Intent =
    Intent(context, OverlayService::class.java).apply {
      action = OverlayService.ACTION_START_SCRIPT
      putExtra(OverlayService.EXTRA_TARGET_ID, scriptId)
    }

  fun createRunnerPipelineIntent(context: Context, pipelineId: String): Intent =
    Intent(context, OverlayService::class.java).apply {
      action = OverlayService.ACTION_START_PIPELINE
      putExtra(OverlayService.EXTRA_TARGET_ID, pipelineId)
    }

  fun createGesturePickerIntent(
    context: Context,
    requestId: String,
    targetType: GesturePickerTargetType,
  ): Intent =
    Intent(context, OverlayService::class.java).apply {
      action = OverlayService.ACTION_START_PICKER
      putExtra(OverlayService.EXTRA_PICKER_REQUEST_ID, requestId)
      putExtra(OverlayService.EXTRA_PICKER_TYPE, targetType.name)
    }

  fun createLiveRecorderIntent(context: Context): Intent =
    Intent(context, OverlayService::class.java).apply {
      action = OverlayService.ACTION_START_RECORDER
    }

  fun createLiveInspectorIntent(context: Context): Intent =
    Intent(context, OverlayService::class.java).apply {
      action = OverlayService.ACTION_START_INSPECTOR
    }

  fun createCloseIntent(context: Context): Intent =
    Intent(context, OverlayService::class.java).apply {
      action = OverlayService.ACTION_CLOSE
    }
}
