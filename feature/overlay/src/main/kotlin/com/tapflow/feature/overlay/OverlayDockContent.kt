package com.tapflow.feature.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapflow.core.service.engine.RunnerExecutionSnapshot
import com.tapflow.core.service.engine.RunnerPhase
import com.tapflow.core.service.overlay.OverlayCallbacks
import com.tapflow.core.service.overlay.OverlayMode
import com.tapflow.core.service.overlay.OverlaySessionState
import com.tapflow.core.service.overlay.OverlayUiState
import com.tapflow.core.service.projection.ScreenCaptureState
import com.tapflow.core.service.theme.TapFlowTheme

@Composable
internal fun OverlayDockView(
  state: OverlayUiState,
  callbacks: OverlayCallbacks,
  modifier: Modifier = Modifier,
) {
  val dragModifier =
    Modifier.pointerInput(Unit) {
      detectDragGestures(
        onDrag = { change, dragAmount ->
          change.consume()
          callbacks.onDragDelta(dragAmount.x, dragAmount.y)
        },
        onDragEnd = { callbacks.onDragEnd() },
        onDragCancel = { callbacks.onDragEnd() },
      )
    }

  val modeAccent = resolveModeAccentColor(state)

  if (state.isBubbleCollapsed) {
    CollapsedEdgeBubble(
      state = state,
      accentColor = modeAccent,
      onExpand = callbacks::onToggleBubbleCollapse,
      modifier = modifier.then(dragModifier),
    )
    return
  }

  Row(
    modifier = modifier.padding(2.dp),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (state.isDockedOnRight && state.captureAlertMessage != null) {
      CaptureAlertSideCard(
        message = state.captureAlertMessage ?: "",
        onOpenTapFlow = callbacks::onReturnToTapFlow,
        onDismiss = callbacks::onDismissCaptureAlert,
      )
    }

    Surface(
      modifier =
        dragModifier
          .width(38.dp)
          .testTag("overlay_vertical_strip_dock"),
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(1.dp, modeAccent.copy(alpha = 0.7f)),
      shadowElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        DockHeaderBar(
          accentColor = modeAccent,
          onCollapseClick = callbacks::onToggleBubbleCollapse,
        )

        HorizontalDivider(
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
          modifier = Modifier.padding(horizontal = 4.dp),
        )

        when (state.mode) {
          OverlayMode.RUNNER_SCRIPT,
          OverlayMode.RUNNER_PIPELINE -> {
            RunnerModeControls(
              state = state,
              callbacks = callbacks,
            )
          }

          OverlayMode.GESTURE_PICKER -> {
            GesturePickerModeControls(
              state = state,
              callbacks = callbacks,
            )
          }

          OverlayMode.LIVE_RECORDER -> {
            LiveRecorderModeControls(
              state = state,
              callbacks = callbacks,
            )
          }

          OverlayMode.LIVE_INSPECTOR -> {
            LiveInspectorModeControls(
              state = state,
              callbacks = callbacks,
            )
          }

          OverlayMode.IDLE -> Unit
        }

        HorizontalDivider(
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
          modifier = Modifier.padding(horizontal = 4.dp),
        )

        DockActionIconButton(
          icon = Icons.Filled.Close,
          contentDescription =
            if (state.mode == OverlayMode.GESTURE_PICKER) "Cancel and return to TapFlow"
            else "Close overlay and return to TapFlow",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          testTag = "overlay_close_button",
          onClick = callbacks::onCloseOverlay,
        )
      }
    }

    if (!state.isDockedOnRight && state.captureAlertMessage != null) {
      CaptureAlertSideCard(
        message = state.captureAlertMessage ?: "",
        onOpenTapFlow = callbacks::onReturnToTapFlow,
        onDismiss = callbacks::onDismissCaptureAlert,
      )
    }
  }
}

@Composable
private fun CollapsedEdgeBubble(
  state: OverlayUiState,
  accentColor: Color,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onExpand,
    modifier = modifier.size(34.dp).testTag("overlay_collapsed_bubble"),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
    contentColor = accentColor,
    border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.8f)),
    shadowElevation = 6.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Filled.TouchApp,
        contentDescription = "Expand floating dock",
        tint = accentColor,
        modifier = Modifier.size(18.dp),
      )
      if (state.captureAlertMessage != null) {
        Box(
          modifier =
            Modifier
              .align(Alignment.TopEnd)
              .padding(3.dp)
              .size(7.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.tertiary),
        )
      }
    }
  }
}

@Composable
private fun DockHeaderBar(
  accentColor: Color,
  onCollapseClick: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(1.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(accentColor),
      )
      Spacer(modifier = Modifier.width(2.dp))
      Icon(
        imageVector = Icons.Filled.DragIndicator,
        contentDescription = "Drag floating dock",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(13.dp),
      )
    }

    IconButton(
      onClick = onCollapseClick,
      modifier = Modifier.size(30.dp).testTag("overlay_collapse_toggle_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.KeyboardArrowUp,
        contentDescription = "Collapse floating dock to bubble",
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
private fun RunnerModeControls(
  state: OverlayUiState,
  callbacks: OverlayCallbacks,
) {
  val snap = state.runnerSnapshot
  val isRunning = snap.phase == RunnerPhase.RUNNING

  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("overlay_runner_action_counter"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "${snap.currentTopLevelAction}/${snap.totalTopLevelActions}",
      fontSize = 9.sp,
      lineHeight = 11.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      maxLines = 1,
      textAlign = TextAlign.Center,
    )
    if (state.mode == OverlayMode.RUNNER_PIPELINE || snap.isPipeline) {
      Text(
        text = "${snap.currentPipelineStep}.${snap.currentStepIteration}",
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        textAlign = TextAlign.Center,
      )
    }
  }

  DockActionIconButton(
    icon = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
    contentDescription = if (isRunning) "Pause script" else "Play or resume script",
    tint = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.primary,
    testTag = "overlay_runner_play_pause_button",
    onClick = callbacks::onPlayPause,
  )

  DockActionIconButton(
    icon = Icons.Filled.SkipPrevious,
    contentDescription = "Previous top-level action",
    tint = MaterialTheme.colorScheme.onSurface,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    testTag = "overlay_runner_prev_button",
    onClick = callbacks::onPreviousStep,
  )

  DockActionIconButton(
    icon = Icons.Filled.SkipNext,
    contentDescription = "Step next top-level action",
    tint = MaterialTheme.colorScheme.onSurface,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    testTag = "overlay_runner_next_button",
    onClick = callbacks::onNextStep,
  )

  DockActionIconButton(
    icon = Icons.Filled.Stop,
    contentDescription = "Stop execution",
    tint = MaterialTheme.colorScheme.onErrorContainer,
    containerColor = MaterialTheme.colorScheme.errorContainer,
    testTag = "overlay_runner_stop_button",
    onClick = callbacks::onStopExecution,
  )
}

@Composable
private fun GesturePickerModeControls(
  state: OverlayUiState,
  callbacks: OverlayCallbacks,
) {
  DockActionIconButton(
    icon = if (state.isCaptureActive) Icons.Filled.Stop else Icons.Filled.TouchApp,
    contentDescription =
      if (state.isCaptureActive) "Pause pick interception" else "Resume gesture picking",
    tint =
      if (state.isCaptureActive) MaterialTheme.colorScheme.onPrimary
      else MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor =
      if (state.isCaptureActive) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.primaryContainer,
    testTag = "overlay_picker_toggle_button",
    onClick = callbacks::onToggleCaptureListening,
  )
}

@Composable
private fun LiveRecorderModeControls(
  state: OverlayUiState,
  callbacks: OverlayCallbacks,
) {
  if (state.recordedActionCount > 0) {
    Text(
      text = "${state.recordedActionCount}",
      fontSize = 10.sp,
      lineHeight = 12.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).testTag("overlay_recorder_count_badge"),
    )
  }

  when (state.sessionState) {
    OverlaySessionState.STANDBY -> {
      DockActionIconButton(
        icon = Icons.Filled.FiberManualRecord,
        contentDescription = "Start recording live gestures",
        tint = MaterialTheme.colorScheme.onError,
        containerColor = MaterialTheme.colorScheme.error,
        testTag = "overlay_recorder_start_button",
        onClick = callbacks::onStartOrResumeCapture,
      )
    }

    OverlaySessionState.ACTIVE -> {
      DockActionIconButton(
        icon = Icons.Filled.Pause,
        contentDescription = "Pause recording",
        tint = MaterialTheme.colorScheme.onTertiaryContainer,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        testTag = "overlay_recorder_pause_button",
        onClick = callbacks::onPauseCapture,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop recording and save script",
        tint = MaterialTheme.colorScheme.onError,
        containerColor = MaterialTheme.colorScheme.error,
        testTag = "overlay_recorder_stop_button",
        onClick = callbacks::onStopCapture,
      )
    }

    OverlaySessionState.PAUSED -> {
      DockActionIconButton(
        icon = Icons.Filled.PlayArrow,
        contentDescription = "Resume recording",
        tint = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.primary,
        testTag = "overlay_recorder_resume_button",
        onClick = callbacks::onStartOrResumeCapture,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop recording and save script",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        testTag = "overlay_recorder_stop_button",
        onClick = callbacks::onStopCapture,
      )
    }
  }
}

@Composable
private fun LiveInspectorModeControls(
  state: OverlayUiState,
  callbacks: OverlayCallbacks,
) {
  if (state.inspectedItemCount > 0) {
    Text(
      text = "${state.inspectedItemCount}",
      fontSize = 10.sp,
      lineHeight = 12.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.secondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).testTag("overlay_inspector_count_badge"),
    )
  }

  when (state.sessionState) {
    OverlaySessionState.STANDBY -> {
      DockActionIconButton(
        icon = Icons.Filled.CenterFocusStrong,
        contentDescription = "Start screen inspection",
        tint = MaterialTheme.colorScheme.onSecondary,
        containerColor = MaterialTheme.colorScheme.secondary,
        testTag = "overlay_inspector_start_button",
        onClick = callbacks::onStartOrResumeCapture,
      )
    }

    OverlaySessionState.ACTIVE -> {
      DockActionIconButton(
        icon = Icons.Filled.Pause,
        contentDescription = "Pause screen inspection",
        tint = MaterialTheme.colorScheme.onTertiaryContainer,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        testTag = "overlay_inspector_pause_button",
        onClick = callbacks::onPauseCapture,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop and finalize inspect group",
        tint = MaterialTheme.colorScheme.onSecondary,
        containerColor = MaterialTheme.colorScheme.secondary,
        testTag = "overlay_inspector_stop_button",
        onClick = callbacks::onStopCapture,
      )
    }

    OverlaySessionState.PAUSED -> {
      DockActionIconButton(
        icon = Icons.Filled.PlayArrow,
        contentDescription = "Resume screen inspection",
        tint = MaterialTheme.colorScheme.onSecondary,
        containerColor = MaterialTheme.colorScheme.secondary,
        testTag = "overlay_inspector_resume_button",
        onClick = callbacks::onStartOrResumeCapture,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop and finalize inspect group",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        testTag = "overlay_inspector_stop_button",
        onClick = callbacks::onStopCapture,
      )
    }
  }
}

@Composable
private fun CaptureAlertSideCard(
  message: String,
  onOpenTapFlow: () -> Unit,
  onDismiss: () -> Unit,
) {
  AnimatedVisibility(visible = message.isNotBlank()) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
      shadowElevation = 8.dp,
      modifier = Modifier.widthIn(max = 220.dp).testTag("overlay_stopped_by_system_card"),
    ) {
      Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "Capture Interrupted",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
        Text(
          text = message,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.height(36.dp),
          ) {
            Text("Dismiss", style = MaterialTheme.typography.labelSmall)
          }
          TextButton(
            onClick = onOpenTapFlow,
            modifier = Modifier.height(36.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.OpenInNew,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("TapFlow", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }
  }
}

@Composable
private fun DockActionIconButton(
  icon: ImageVector,
  contentDescription: String,
  tint: Color,
  containerColor: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  FilledTonalIconButton(
    onClick = onClick,
    colors =
      IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = containerColor,
        contentColor = tint,
      ),
    modifier = Modifier.size(32.dp).testTag(testTag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun resolveModeAccentColor(state: OverlayUiState): Color =
  when {
    state.captureAlertMessage != null -> MaterialTheme.colorScheme.tertiary
    state.mode == OverlayMode.LIVE_RECORDER -> MaterialTheme.colorScheme.error
    state.mode == OverlayMode.LIVE_INSPECTOR -> MaterialTheme.colorScheme.secondary
    state.mode == OverlayMode.GESTURE_PICKER -> MaterialTheme.colorScheme.primary
    state.runnerSnapshot.phase == RunnerPhase.RUNNING -> MaterialTheme.colorScheme.primary
    state.runnerSnapshot.phase == RunnerPhase.FAILED ||
      state.runnerSnapshot.phase == RunnerPhase.PAYWALL_BLOCKED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
  }

private val PreviewNoOpCallbacks =
  object : OverlayCallbacks {
    override fun onPlayPause() = Unit
    override fun onPreviousStep() = Unit
    override fun onNextStep() = Unit
    override fun onStopExecution() = Unit
    override fun onToggleCaptureListening() = Unit
    override fun onStartOrResumeCapture() = Unit
    override fun onPauseCapture() = Unit
    override fun onStopCapture() = Unit
    override fun onToggleBubbleCollapse() = Unit
    override fun onCloseOverlay() = Unit
    override fun onCancelCaptureMode() = Unit
    override fun onDismissCaptureAlert() = Unit
    override fun onReturnToTapFlow() = Unit
    override fun onDragDelta(dx: Float, dy: Float) = Unit
    override fun onDragEnd() = Unit
  }

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun OverlayDockPipelineRunnerPreview() {
  TapFlowTheme {
    TapFlowOverlayTheme {
      OverlayDockView(
        state =
          OverlayUiState(
            mode = OverlayMode.RUNNER_PIPELINE,
            runnerSnapshot =
              RunnerExecutionSnapshot(
                phase = RunnerPhase.RUNNING,
                title = "Farm Loop • Collect",
                isPipeline = true,
                currentTopLevelAction = 3,
                totalTopLevelActions = 9,
                currentPipelineStep = 2,
                totalPipelineSteps = 4,
                currentStepIteration = 2,
                totalStepIterations = 5,
              ),
            captureState = ScreenCaptureState.StoppedBySystem("Screen capture stopped by system."),
            captureAlertMessage = "Screen capture stopped by system.",
          ),
        callbacks = PreviewNoOpCallbacks,
      )
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun OverlayDockRecorderPreview() {
  TapFlowTheme {
    TapFlowOverlayTheme {
      OverlayDockView(
        state =
          OverlayUiState(
            mode = OverlayMode.LIVE_RECORDER,
            sessionState = OverlaySessionState.ACTIVE,
            isCaptureActive = true,
            recordedActionCount = 7,
          ),
        callbacks = PreviewNoOpCallbacks,
      )
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun OverlayDockInspectorPreview() {
  TapFlowTheme {
    TapFlowOverlayTheme {
      OverlayDockView(
        state =
          OverlayUiState(
            mode = OverlayMode.LIVE_INSPECTOR,
            sessionState = OverlaySessionState.PAUSED,
            isCaptureActive = false,
            inspectedItemCount = 4,
          ),
        callbacks = PreviewNoOpCallbacks,
      )
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun OverlayDockCollapsedPreview() {
  TapFlowTheme {
    TapFlowOverlayTheme {
      OverlayDockView(
        state =
          OverlayUiState(
            mode = OverlayMode.RUNNER_SCRIPT,
            isBubbleCollapsed = true,
            runnerSnapshot =
              RunnerExecutionSnapshot(
                phase = RunnerPhase.RUNNING,
                currentTopLevelAction = 4,
                totalTopLevelActions = 12,
              ),
          ),
        callbacks = PreviewNoOpCallbacks,
      )
    }
  }
}
