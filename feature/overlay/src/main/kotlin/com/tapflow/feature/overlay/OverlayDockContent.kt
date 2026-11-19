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
import androidx.compose.material.icons.filled.FiberManualRecord
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapflow.core.service.engine.RunnerExecutionSnapshot
import com.tapflow.core.service.engine.RunnerPhase
import com.tapflow.core.service.overlay.CaptureSaveQueue
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
  val sizeOption = state.overlaySize
  val buttonSize = sizeOption.buttonSizeDp.dp
  val iconSize = sizeOption.iconSizeDp.dp
  val bubbleSize = sizeOption.bubbleSizeDp.dp
  val counterSp = sizeOption.counterTextSp.sp
  val dockPadding = sizeOption.paddingDp.dp
  val dockBorder =
    BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
      bubbleSize = bubbleSize,
      iconSize = iconSize,
      border = dockBorder,
      onExpand = callbacks::onToggleBubbleCollapse,
      modifier = modifier.then(dragModifier),
    )
    return
  }

  val showSideColumn = state.captureAlertMessage != null || state.pendingSaveCount > 0

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (state.isDockedOnRight && showSideColumn) {
      DockSideStatusColumn(
        state = state,
        horizontalAlignment = Alignment.End,
        onOpenTapFlow = callbacks::onReturnToTapFlow,
        onDismissAlert = callbacks::onDismissCaptureAlert,
      )
    }

    Surface(
      modifier =
        dragModifier
          .width(buttonSize + (dockPadding * 2))
          .testTag("overlay_vertical_strip_dock"),
      shape = RoundedCornerShape(buttonSize / 2),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = dockBorder,
      shadowElevation = 0.dp,
      tonalElevation = 0.dp,
    ) {
      Column(
        modifier = Modifier.padding(dockPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dockPadding),
      ) {
        IconButton(
          onClick = callbacks::onToggleBubbleCollapse,
          modifier = Modifier.size(buttonSize).testTag("overlay_collapse_toggle_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Collapse floating dock to bubble",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
          )
        }

        when (state.mode) {
          OverlayMode.RUNNER_SCRIPT,
          OverlayMode.RUNNER_PIPELINE -> {
            RunnerModeControls(
              state = state,
              buttonSize = buttonSize,
              iconSize = iconSize,
              counterSp = counterSp,
              callbacks = callbacks,
            )
          }

          OverlayMode.GESTURE_PICKER -> {
            GesturePickerModeControls(
              state = state,
              buttonSize = buttonSize,
              iconSize = iconSize,
              callbacks = callbacks,
            )
          }

          OverlayMode.LIVE_RECORDER -> {
            LiveRecorderModeControls(
              state = state,
              buttonSize = buttonSize,
              iconSize = iconSize,
              counterSp = counterSp,
              callbacks = callbacks,
            )
          }

          OverlayMode.LIVE_INSPECTOR -> {
            LiveInspectorModeControls(
              state = state,
              buttonSize = buttonSize,
              iconSize = iconSize,
              counterSp = counterSp,
              callbacks = callbacks,
            )
          }

          OverlayMode.IDLE -> Unit
        }

        DockActionIconButton(
          icon = Icons.Filled.Close,
          contentDescription =
            if (state.mode == OverlayMode.GESTURE_PICKER) "Cancel and return to TapFlow"
            else "Close overlay",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          buttonSize = buttonSize,
          iconSize = iconSize,
          testTag = "overlay_close_button",
          onClick = callbacks::onCloseOverlay,
        )
      }
    }

    if (!state.isDockedOnRight && showSideColumn) {
      DockSideStatusColumn(
        state = state,
        horizontalAlignment = Alignment.Start,
        onOpenTapFlow = callbacks::onReturnToTapFlow,
        onDismissAlert = callbacks::onDismissCaptureAlert,
      )
    }
  }
}

@Composable
private fun CollapsedEdgeBubble(
  state: OverlayUiState,
  accentColor: Color,
  bubbleSize: Dp,
  iconSize: Dp,
  border: BorderStroke,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onExpand,
    modifier = modifier.size(bubbleSize).testTag("overlay_collapsed_bubble"),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    contentColor = accentColor,
    border = border,
    shadowElevation = 0.dp,
    tonalElevation = 0.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Filled.TouchApp,
        contentDescription = "Expand floating dock",
        tint = accentColor,
        modifier = Modifier.size(iconSize),
      )
      if (state.captureAlertMessage != null || state.pendingSaveCount > 0) {
        val indicatorColor =
          when {
            state.captureAlertMessage != null -> MaterialTheme.colorScheme.tertiary
            state.isSaveQueueBusyFlash || state.pendingSaveCount >= CaptureSaveQueue.MAX_PENDING_SAVES ->
              OverlayTokens.QueueAmberAccent
            else -> MaterialTheme.colorScheme.primary
          }
        Box(
          modifier =
            Modifier
              .align(Alignment.TopEnd)
              .padding(3.dp)
              .size(6.dp)
              .clip(CircleShape)
              .background(indicatorColor),
        )
      }
    }
  }
}

@Composable
private fun RunnerModeControls(
  state: OverlayUiState,
  buttonSize: Dp,
  iconSize: Dp,
  counterSp: TextUnit,
  callbacks: OverlayCallbacks,
) {
  val snap = state.runnerSnapshot

  Column(
    modifier = Modifier.fillMaxWidth().testTag("overlay_runner_action_counter"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "${snap.currentTopLevelAction}/${snap.totalTopLevelActions}",
      fontSize = counterSp,
      lineHeight = (counterSp.value + 2f).sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      maxLines = 1,
      textAlign = TextAlign.Center,
    )
    if (state.mode == OverlayMode.RUNNER_PIPELINE || snap.isPipeline) {
      Text(
        text = "${snap.currentPipelineStep}.${snap.currentStepIteration}",
        fontSize = (counterSp.value - 1f).coerceAtLeast(7f).sp,
        lineHeight = (counterSp.value + 1f).sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        textAlign = TextAlign.Center,
      )
    }
  }

  when (snap.phase) {
    RunnerPhase.RUNNING -> {
      DockActionIconButton(
        icon = Icons.Filled.Pause,
        contentDescription = "Pause script",
        tint = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.primary,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_play_pause_button",
        onClick = callbacks::onPlayPause,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop execution",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_stop_button",
        onClick = callbacks::onStopExecution,
      )
    }

    RunnerPhase.PAUSED -> {
      DockActionIconButton(
        icon = Icons.Filled.PlayArrow,
        contentDescription = "Resume script",
        tint = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.primary,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_play_pause_button",
        onClick = callbacks::onPlayPause,
      )

      DockActionIconButton(
        icon = Icons.Filled.SkipPrevious,
        contentDescription = "Previous top-level action",
        tint = MaterialTheme.colorScheme.onSurface,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_prev_button",
        onClick = callbacks::onPreviousStep,
      )

      DockActionIconButton(
        icon = Icons.Filled.SkipNext,
        contentDescription = "Step next top-level action",
        tint = MaterialTheme.colorScheme.onSurface,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_next_button",
        onClick = callbacks::onNextStep,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop execution",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_stop_button",
        onClick = callbacks::onStopExecution,
      )
    }

    RunnerPhase.IDLE,
    RunnerPhase.STOPPED,
    RunnerPhase.COMPLETED,
    RunnerPhase.FAILED,
    RunnerPhase.PAYWALL_BLOCKED -> {
      DockActionIconButton(
        icon = Icons.Filled.PlayArrow,
        contentDescription = "Play script",
        tint = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.primary,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_play_pause_button",
        onClick = callbacks::onPlayPause,
      )

      DockActionIconButton(
        icon = Icons.Filled.SkipPrevious,
        contentDescription = "Previous top-level action",
        tint = MaterialTheme.colorScheme.onSurface,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_prev_button",
        onClick = callbacks::onPreviousStep,
      )

      DockActionIconButton(
        icon = Icons.Filled.SkipNext,
        contentDescription = "Step next top-level action",
        tint = MaterialTheme.colorScheme.onSurface,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_runner_next_button",
        onClick = callbacks::onNextStep,
      )
    }
  }
}

@Composable
private fun GesturePickerModeControls(
  state: OverlayUiState,
  buttonSize: Dp,
  iconSize: Dp,
  callbacks: OverlayCallbacks,
) {
  DockActionIconButton(
    icon = if (state.isCaptureActive) Icons.Filled.Pause else Icons.Filled.CenterFocusStrong,
    contentDescription =
      if (state.isCaptureActive) "Pause pick interception" else "Resume gesture picking",
    tint =
      if (state.isCaptureActive) MaterialTheme.colorScheme.onPrimary
      else MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor =
      if (state.isCaptureActive) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.primaryContainer,
    buttonSize = buttonSize,
    iconSize = iconSize,
    testTag = "overlay_picker_toggle_button",
    onClick = callbacks::onToggleCaptureListening,
  )
}

@Composable
private fun LiveRecorderModeControls(
  state: OverlayUiState,
  buttonSize: Dp,
  iconSize: Dp,
  counterSp: TextUnit,
  callbacks: OverlayCallbacks,
) {
  if (state.sessionState != OverlaySessionState.STANDBY && state.recordedActionCount > 0) {
    Text(
      text = "${state.recordedActionCount}",
      fontSize = counterSp,
      lineHeight = (counterSp.value + 2f).sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.error,
      maxLines = 1,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().testTag("overlay_recorder_count_badge"),
    )
  }

  when (state.sessionState) {
    OverlaySessionState.STANDBY -> {
      DockActionIconButton(
        icon = Icons.Filled.FiberManualRecord,
        contentDescription = "Start recording live gestures",
        tint = MaterialTheme.colorScheme.onError,
        containerColor = MaterialTheme.colorScheme.error,
        buttonSize = buttonSize,
        iconSize = iconSize,
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
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_recorder_pause_button",
        onClick = callbacks::onPauseCapture,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop recording and save script",
        tint = MaterialTheme.colorScheme.onError,
        containerColor = MaterialTheme.colorScheme.error,
        buttonSize = buttonSize,
        iconSize = iconSize,
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
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_recorder_resume_button",
        onClick = callbacks::onStartOrResumeCapture,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop recording and save script",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_recorder_stop_button",
        onClick = callbacks::onStopCapture,
      )
    }
  }
}

@Composable
private fun LiveInspectorModeControls(
  state: OverlayUiState,
  buttonSize: Dp,
  iconSize: Dp,
  counterSp: TextUnit,
  callbacks: OverlayCallbacks,
) {
  if (state.sessionState != OverlaySessionState.STANDBY && state.inspectedItemCount > 0) {
    Text(
      text = "${state.inspectedItemCount}",
      fontSize = counterSp,
      lineHeight = (counterSp.value + 2f).sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.secondary,
      maxLines = 1,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().testTag("overlay_inspector_count_badge"),
    )
  }

  when (state.sessionState) {
    OverlaySessionState.STANDBY -> {
      DockActionIconButton(
        icon = Icons.Filled.CenterFocusStrong,
        contentDescription = "Start screen inspection",
        tint = MaterialTheme.colorScheme.onSecondary,
        containerColor = MaterialTheme.colorScheme.secondary,
        buttonSize = buttonSize,
        iconSize = iconSize,
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
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_inspector_pause_button",
        onClick = callbacks::onPauseCapture,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop and finalize inspect group",
        tint = MaterialTheme.colorScheme.onSecondary,
        containerColor = MaterialTheme.colorScheme.secondary,
        buttonSize = buttonSize,
        iconSize = iconSize,
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
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_inspector_resume_button",
        onClick = callbacks::onStartOrResumeCapture,
      )

      DockActionIconButton(
        icon = Icons.Filled.Stop,
        contentDescription = "Stop and finalize inspect group",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        buttonSize = buttonSize,
        iconSize = iconSize,
        testTag = "overlay_inspector_stop_button",
        onClick = callbacks::onStopCapture,
      )
    }
  }
}

@Composable
private fun DockSideStatusColumn(
  state: OverlayUiState,
  horizontalAlignment: Alignment.Horizontal,
  onOpenTapFlow: () -> Unit,
  onDismissAlert: () -> Unit,
) {
  Column(
    horizontalAlignment = horizontalAlignment,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (state.pendingSaveCount > 0) {
      SaveQueueStatusBadge(
        pendingCount = state.pendingSaveCount,
        isBusyFlash = state.isSaveQueueBusyFlash,
      )
    }
    state.captureAlertMessage?.let { msg ->
      CaptureAlertSideCard(
        message = msg,
        onOpenTapFlow = onOpenTapFlow,
        onDismiss = onDismissAlert,
      )
    }
  }
}

@Composable
private fun SaveQueueStatusBadge(
  pendingCount: Int,
  isBusyFlash: Boolean,
) {
  val isFull = pendingCount >= CaptureSaveQueue.MAX_PENDING_SAVES
  val useAmber = isFull || isBusyFlash
  val bgColor =
    if (useAmber) OverlayTokens.QueueAmberSurface
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
  val fgColor =
    if (useAmber) OverlayTokens.QueueAmberContent
    else MaterialTheme.colorScheme.onSurface
  val borderColor =
    if (useAmber) OverlayTokens.QueueAmberAccent
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

  Surface(
    modifier = Modifier.testTag("overlay_save_queue_badge"),
    shape = RoundedCornerShape(10.dp),
    color = bgColor,
    contentColor = fgColor,
    border = BorderStroke(width = if (isBusyFlash) 1.5.dp else 0.75.dp, color = borderColor),
    shadowElevation = 0.dp,
    tonalElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (useAmber) OverlayTokens.QueueAmberAccent else MaterialTheme.colorScheme.primary),
      )
      Text(
        text = "Saving $pendingCount/${CaptureSaveQueue.MAX_PENDING_SAVES}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (useAmber) FontWeight.Bold else FontWeight.SemiBold,
        color = fgColor,
        maxLines = 1,
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
      border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
      shadowElevation = 0.dp,
      tonalElevation = 0.dp,
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
  buttonSize: Dp,
  iconSize: Dp,
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
    modifier = Modifier.size(buttonSize).testTag(testTag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(iconSize),
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
            sessionState = OverlaySessionState.ACTIVE,
            isCaptureActive = true,
            inspectedItemCount = 4,
            pendingSaveCount = CaptureSaveQueue.MAX_PENDING_SAVES,
            isSaveQueueBusyFlash = true,
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
