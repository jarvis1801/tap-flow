package com.tapflow.core.service.overlay

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.tapflow.core.service.engine.RunnerExecutionSnapshot
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

enum class OverlayCapturePhase {
  IDLE,
  INTERCEPTING,
  CAPTURING_CLEAN_FRAME,
  PASSTHROUGH_DISABLED,
  REPLAYING,
  RESTORING,
}

@Immutable
data class OverlayUiState(
  val mode: OverlayMode = OverlayMode.IDLE,
  val isBubbleCollapsed: Boolean = false,
  val isDockedOnRight: Boolean = false,
  val capturePhase: OverlayCapturePhase = OverlayCapturePhase.IDLE,
  val runnerSnapshot: RunnerExecutionSnapshot = RunnerExecutionSnapshot(),
  val recordedActionCount: Int = 0,
  val inspectedItemCount: Int = 0,
  val statusHint: String? = null,
)

interface OverlayCallbacks {
  fun onPlayPause()
  fun onPreviousStep()
  fun onNextStep()
  fun onStopExecution()
  fun onToggleBubbleCollapse()
  fun onCloseOverlay()
  fun onCancelCaptureMode()
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
