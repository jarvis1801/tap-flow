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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapflow.core.service.engine.RunnerExecutionSnapshot
import com.tapflow.core.service.engine.RunnerPhase
import com.tapflow.core.service.overlay.GesturePickerTargetType
import com.tapflow.core.service.overlay.OverlayCallbacks
import com.tapflow.core.service.overlay.OverlayMode
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
    modifier = modifier.padding(4.dp),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
          .width(74.dp)
          .testTag("overlay_vertical_strip_dock"),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(1.2.dp, modeAccent.copy(alpha = 0.65f)),
      shadowElevation = 10.dp,
    ) {
      Column(
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Top toggle [▲ / ▼] + magnetic drag handle
        DockHeaderBar(
          accentColor = modeAccent,
          onCollapseClick = callbacks::onToggleBubbleCollapse,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))

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

          OverlayMode.IDLE -> {
            Text(
              text = "IDLE",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 4.dp),
            )
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))

        // Close (✕) button
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
  val bubbleLabel =
    when (state.mode) {
      OverlayMode.RUNNER_SCRIPT,
      OverlayMode.RUNNER_PIPELINE -> {
        val snap = state.runnerSnapshot
        if (snap.totalTopLevelActions > 0) "${snap.currentTopLevelAction}/${snap.totalTopLevelActions}"
        else "TF"
      }
      OverlayMode.LIVE_RECORDER -> "R${state.recordedActionCount}"
      OverlayMode.LIVE_INSPECTOR -> "I${state.inspectedItemCount}"
      OverlayMode.GESTURE_PICKER -> "PICK"
      OverlayMode.IDLE -> "TF"
    }

  Surface(
    onClick = onExpand,
    modifier = modifier.size(54.dp).testTag("overlay_collapsed_bubble"),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(2.dp, accentColor),
    shadowElevation = 10.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowDown,
          contentDescription = "Expand floating dock",
          tint = accentColor,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = bubbleLabel,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      if (state.captureAlertMessage != null) {
        Box(
          modifier =
            Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp)
              .size(10.dp)
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
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(accentColor),
      )
      Spacer(modifier = Modifier.width(3.dp))
      Icon(
        imageVector = Icons.Filled.DragIndicator,
        contentDescription = "Drag floating dock",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
    }

    IconButton(
      onClick = onCollapseClick,
      modifier = Modifier.size(48.dp).testTag("overlay_collapse_toggle_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.KeyboardArrowUp,
        contentDescription = "Collapse floating dock to bubble",
        tint = MaterialTheme.colorScheme.onSurface,
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

  val phaseBadge =
    when (snap.phase) {
      RunnerPhase.RUNNING -> "RUN"
      RunnerPhase.PAUSED -> "PAUSE"
      RunnerPhase.COMPLETED -> "DONE"
      RunnerPhase.FAILED -> "FAIL"
      RunnerPhase.STOPPED -> "STOP"
      RunnerPhase.PAYWALL_BLOCKED -> "PRO"
      RunnerPhase.IDLE -> "READY"
    }
  val badgeBg =
    when (snap.phase) {
      RunnerPhase.RUNNING -> MaterialTheme.colorScheme.primaryContainer
      RunnerPhase.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
      RunnerPhase.FAILED,
      RunnerPhase.PAYWALL_BLOCKED -> MaterialTheme.colorScheme.errorContainer
      else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
  val badgeFg =
    when (snap.phase) {
      RunnerPhase.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
      RunnerPhase.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
      RunnerPhase.FAILED,
      RunnerPhase.PAYWALL_BLOCKED -> MaterialTheme.colorScheme.onErrorContainer
      else -> MaterialTheme.colorScheme.onSurface
    }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = badgeBg,
    modifier = Modifier.fillMaxWidth().testTag("overlay_runner_phase_badge"),
  ) {
    Text(
      text = phaseBadge,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = badgeFg,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp),
    )
  }

  // Action progress [Current / Total]
  val actionCounter = "${snap.currentTopLevelAction}/${snap.totalTopLevelActions}"
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().testTag("overlay_runner_action_counter"),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = actionCounter,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
      if (state.mode == OverlayMode.RUNNER_PIPELINE || snap.isPipeline) {
        Text(
          text = "S ${snap.currentPipelineStep}/${snap.totalPipelineSteps}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "R ${snap.currentStepIteration}/${snap.totalStepIterations}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  // Play / Pause
  DockActionIconButton(
    icon = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
    contentDescription = if (isRunning) "Pause script" else "Play or resume script",
    tint = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.primary,
    testTag = "overlay_runner_play_pause_button",
    onClick = callbacks::onPlayPause,
  )

  // Previous Top-Level Step (⏮)
  DockActionIconButton(
    icon = Icons.Filled.SkipPrevious,
    contentDescription = "Previous top-level action",
    tint = MaterialTheme.colorScheme.onSurface,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    testTag = "overlay_runner_prev_button",
    onClick = callbacks::onPreviousStep,
  )

  // Next Atomic Top-Level Step (⏭)
  DockActionIconButton(
    icon = Icons.Filled.SkipNext,
    contentDescription = "Step next top-level action",
    tint = MaterialTheme.colorScheme.onSurface,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    testTag = "overlay_runner_next_button",
    onClick = callbacks::onNextStep,
  )

  // Stop (⏹)
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
  val modeShort =
    when (state.pickerTargetType) {
      GesturePickerTargetType.POINT -> "POINT"
      GesturePickerTargetType.LONG_PRESS -> "HOLD"
      GesturePickerTargetType.SWIPE_PATH -> "SWIPE"
      GesturePickerTargetType.MULTI_TOUCH_ANCHOR -> "MULTI"
      GesturePickerTargetType.REGION_BOUNDS -> "ROI"
      null -> "PICK"
    }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = modeShort,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(vertical = 4.dp),
    )
  }

  // Inspect / Stop toggle so user can pause interception to scroll target app or arm pick
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
  Text(
    text = if (state.isCaptureActive) "Armed" else "Inspect",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun LiveRecorderModeControls(
  state: OverlayUiState,
  callbacks: OverlayCallbacks,
) {
  val badgeContainer =
    if (state.isCaptureActive) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
  val badgeTitleColor =
    if (state.isCaptureActive) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.primary
  val badgeCountColor =
    if (state.isCaptureActive) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurface

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = badgeContainer,
    contentColor = badgeCountColor,
    modifier = Modifier.fillMaxWidth().testTag("overlay_recorder_count_badge"),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = if (state.isCaptureActive) "REC" else "SAVED",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = badgeTitleColor,
      )
      Text(
        text = "${state.recordedActionCount}",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = badgeCountColor,
      )
    }
  }

  // Record / Stop toggle
  DockActionIconButton(
    icon = if (state.isCaptureActive) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
    contentDescription =
      if (state.isCaptureActive) "Stop recording and save script"
      else "Start new recording",
    tint =
      if (state.isCaptureActive) MaterialTheme.colorScheme.onError
      else MaterialTheme.colorScheme.onErrorContainer,
    containerColor =
      if (state.isCaptureActive) MaterialTheme.colorScheme.error
      else MaterialTheme.colorScheme.errorContainer,
    testTag = "overlay_recorder_toggle_button",
    onClick = callbacks::onToggleCaptureListening,
  )
  Text(
    text = if (state.isCaptureActive) "Stop" else "Record",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun LiveInspectorModeControls(
  state: OverlayUiState,
  callbacks: OverlayCallbacks,
) {
  val badgeContainer =
    if (state.isCaptureActive) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
  val badgeTitleColor =
    if (state.isCaptureActive) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
  val badgeCountColor =
    if (state.isCaptureActive) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = badgeContainer,
    contentColor = badgeCountColor,
    modifier = Modifier.fillMaxWidth().testTag("overlay_inspector_count_badge"),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = if (state.isCaptureActive) "INSP" else "PAUSE",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = badgeTitleColor,
      )
      Text(
        text = "${state.inspectedItemCount}",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = badgeCountColor,
      )
    }
  }

  // Inspect / Stop toggle
  DockActionIconButton(
    icon = if (state.isCaptureActive) Icons.Filled.Stop else Icons.Filled.CenterFocusStrong,
    contentDescription =
      if (state.isCaptureActive) "Pause screen inspection"
      else "Resume screen inspection",
    tint =
      if (state.isCaptureActive) MaterialTheme.colorScheme.onSecondary
      else MaterialTheme.colorScheme.onSecondaryContainer,
    containerColor =
      if (state.isCaptureActive) MaterialTheme.colorScheme.secondary
      else MaterialTheme.colorScheme.secondaryContainer,
    testTag = "overlay_inspector_toggle_button",
    onClick = callbacks::onToggleCaptureListening,
  )
  Text(
    text = if (state.isCaptureActive) "Stop" else "Inspect",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
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
    modifier = Modifier.size(48.dp).testTag(testTag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(22.dp),
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
            isCaptureActive = true,
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
