package com.tapflow.feature.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.Point2D
import com.tapflow.core.service.overlay.GesturePickerTargetType
import com.tapflow.core.service.overlay.OverlayCapturePhase
import com.tapflow.core.service.overlay.OverlayMode
import com.tapflow.core.service.overlay.OverlayTouchPreview
import com.tapflow.core.service.overlay.OverlayUiState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun OverlayCaptureFeedbackLayer(state: OverlayUiState) {
  // Hide all visual chrome and reticles during clean screenshot capture and passthrough replay
  if (state.capturePhase == OverlayCapturePhase.CAPTURING_CLEAN_FRAME ||
    state.capturePhase == OverlayCapturePhase.PASSTHROUGH_DISABLED ||
    state.capturePhase == OverlayCapturePhase.REPLAYING
  ) {
    return
  }

  if (!state.isCaptureActive && state.captureAlertMessage == null) {
    return
  }

  val accentColor =
    when (state.mode) {
      OverlayMode.GESTURE_PICKER -> MaterialTheme.colorScheme.primary
      OverlayMode.LIVE_RECORDER -> MaterialTheme.colorScheme.error
      OverlayMode.LIVE_INSPECTOR -> MaterialTheme.colorScheme.secondary
      else -> MaterialTheme.colorScheme.primary
    }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(
          if (state.isCaptureActive) OverlayTokens.ScrimOverlay else Color.Transparent,
        )
        .testTag("overlay_capture_feedback_layer"),
  ) {
    if (state.isCaptureActive) {
      CaptureBorderAndReticleCanvas(
        mode = state.mode,
        pickerTargetType = state.pickerTargetType,
        touchPreview = state.touchPreview,
        accentColor = accentColor,
      )

      CaptureTopStatusPill(
        state = state,
        accentColor = accentColor,
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
      )

      state.touchPreview?.let { preview ->
        if (preview.isTouching) {
          TouchCoordinateReadoutBadge(
            preview = preview,
            pickerTargetType = state.pickerTargetType,
            accentColor = accentColor,
          )
        }
      }
    }

    state.captureAlertMessage?.let { alertMsg ->
      Surface(
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 64.dp, start = 20.dp, end = 20.dp)
            .testTag("overlay_capture_alert_banner"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
        shadowElevation = 6.dp,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = "Screen capture alert",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = alertMsg,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
      }
    }
  }
}

@Composable
private fun CaptureTopStatusPill(
  state: OverlayUiState,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val modeLabel =
    when (state.mode) {
      OverlayMode.GESTURE_PICKER ->
        when (state.pickerTargetType) {
          GesturePickerTargetType.POINT -> "PICK POINT"
          GesturePickerTargetType.LONG_PRESS -> "PICK LONG PRESS"
          GesturePickerTargetType.SWIPE_PATH -> "DRAW SWIPE PATH"
          GesturePickerTargetType.MULTI_TOUCH_ANCHOR -> "PICK MULTI-TOUCH"
          GesturePickerTargetType.REGION_BOUNDS -> "DRAG REGION BOUNDS"
          null -> "GESTURE PICKER"
        }

      OverlayMode.LIVE_RECORDER -> "REC • ${state.recordedActionCount} STEPS"
      OverlayMode.LIVE_INSPECTOR -> "LIVE INSPECT • ${state.inspectedItemCount} CAPTURES"
      else -> "ACTIVE"
    }

  val icon =
    when (state.mode) {
      OverlayMode.LIVE_RECORDER -> Icons.Filled.FiberManualRecord
      OverlayMode.LIVE_INSPECTOR -> Icons.Filled.CenterFocusStrong
      else -> Icons.Filled.TouchApp
    }

  Surface(
    modifier = modifier.testTag("overlay_capture_top_pill"),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.75f)),
    shadowElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(8.dp)
            .background(accentColor, CircleShape),
      )
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accentColor,
        modifier = Modifier.size(15.dp),
      )
      Column {
        Text(
          text = modeLabel,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        state.statusHint?.let { hint ->
          Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun CaptureBorderAndReticleCanvas(
  mode: OverlayMode,
  pickerTargetType: GesturePickerTargetType?,
  touchPreview: OverlayTouchPreview?,
  accentColor: Color,
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val frameStroke = 2.dp.toPx()
    drawRect(
      color = accentColor.copy(alpha = 0.45f),
      style =
        Stroke(
          width = frameStroke,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f),
        ),
    )

    val preview = touchPreview ?: return@Canvas
    if (!preview.isTouching) return@Canvas

    val start = Offset(preview.startX, preview.startY)
    val current = Offset(preview.currentX, preview.currentY)

    val isRegion =
      mode == OverlayMode.GESTURE_PICKER &&
        pickerTargetType == GesturePickerTargetType.REGION_BOUNDS

    if (isRegion) {
      val left = min(start.x, current.x)
      val top = min(start.y, current.y)
      val w = abs(current.x - start.x).coerceAtLeast(8f)
      val h = abs(current.y - start.y).coerceAtLeast(8f)
      drawRect(
        color = accentColor.copy(alpha = 0.16f),
        topLeft = Offset(left, top),
        size = Size(w, h),
      )
      drawRect(
        color = accentColor,
        topLeft = Offset(left, top),
        size = Size(w, h),
        style = Stroke(width = 2.5.dp.toPx()),
      )
      drawReticle(center = start, color = accentColor, radiusPx = 10.dp.toPx())
      drawReticle(center = current, color = accentColor, radiusPx = 12.dp.toPx())
      return@Canvas
    }

    if (preview.isDrag && preview.pathPoints.size >= 2) {
      val path =
        Path().apply {
          val first = preview.pathPoints.first()
          moveTo(first.x.toFloat(), first.y.toFloat())
          for (i in 1 until preview.pathPoints.size) {
            val pt = preview.pathPoints[i]
            lineTo(pt.x.toFloat(), pt.y.toFloat())
          }
          lineTo(current.x, current.y)
        }
      drawPath(
        path = path,
        color = accentColor.copy(alpha = 0.35f),
        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
      drawPath(
        path = path,
        color = accentColor,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
      drawCircle(
        color = accentColor,
        radius = 8.dp.toPx(),
        center = start,
      )
      drawReticle(center = current, color = accentColor, radiusPx = 18.dp.toPx())
    } else {
      val baseRadius =
        if (preview.isLongPress || pickerTargetType == GesturePickerTargetType.LONG_PRESS) {
          26.dp.toPx() + ((preview.durationMs.coerceAtMost(3000L) / 3000f) * 18.dp.toPx())
        } else {
          20.dp.toPx()
        }
      drawReticle(center = current, color = accentColor, radiusPx = baseRadius)
    }

    // Secondary finger reticles for multi-touch feedback
    preview.secondaryPoints.forEach { sec ->
      val secOffset = Offset(sec.x.toFloat(), sec.y.toFloat())
      drawLine(
        color = accentColor.copy(alpha = 0.6f),
        start = current,
        end = secOffset,
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
      )
      drawReticle(center = secOffset, color = OverlayTokens.AmberAlert, radiusPx = 18.dp.toPx())
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReticle(
  center: Offset,
  color: Color,
  radiusPx: Float,
) {
  drawCircle(
    color = color.copy(alpha = 0.22f),
    radius = radiusPx,
    center = center,
  )
  drawCircle(
    color = color,
    radius = radiusPx,
    center = center,
    style = Stroke(width = 2.2.dp.toPx()),
  )
  drawCircle(
    color = color,
    radius = 4.dp.toPx(),
    center = center,
  )
  val arm = radiusPx * 0.65f
  drawLine(
    color = color,
    start = Offset(center.x - arm, center.y),
    end = Offset(center.x + arm, center.y),
    strokeWidth = 1.5.dp.toPx(),
  )
  drawLine(
    color = color,
    start = Offset(center.x, center.y - arm),
    end = Offset(center.x, center.y + arm),
    strokeWidth = 1.5.dp.toPx(),
  )
}

@Composable
private fun TouchCoordinateReadoutBadge(
  preview: OverlayTouchPreview,
  pickerTargetType: GesturePickerTargetType?,
  accentColor: Color,
) {
  val density = LocalDensity.current
  val offsetYPx = with(density) { 56.dp.toPx() }
  val badgeX = (preview.currentX - with(density) { 64.dp.toPx() }).roundToInt().coerceAtLeast(12)
  val badgeY =
    if (preview.currentY > offsetYPx * 2) {
      (preview.currentY - offsetYPx).roundToInt()
    } else {
      (preview.currentY + offsetYPx).roundToInt()
    }

  val label =
    when {
      pickerTargetType == GesturePickerTargetType.REGION_BOUNDS -> {
        val w = abs(preview.currentX - preview.startX).roundToInt()
        val h = abs(preview.currentY - preview.startY).roundToInt()
        "ROI ${w}x${h} px"
      }
      preview.secondaryPoints.isNotEmpty() ->
        "(${preview.currentX.roundToInt()}, ${preview.currentY.roundToInt()}) • ${1 + preview.secondaryPoints.size} fingers"
      preview.isDrag -> {
        val dist =
          hypot(
            (preview.currentX - preview.startX).toDouble(),
            (preview.currentY - preview.startY).toDouble(),
          ).roundToInt()
        "(${preview.startX.roundToInt()},${preview.startY.roundToInt()}) → (${preview.currentX.roundToInt()},${preview.currentY.roundToInt()}) • ${dist}px"
      }
      preview.isLongPress || pickerTargetType == GesturePickerTargetType.LONG_PRESS ->
        "(${preview.currentX.roundToInt()}, ${preview.currentY.roundToInt()}) • ${preview.durationMs}ms"
      else ->
        "(${preview.currentX.roundToInt()}, ${preview.currentY.roundToInt()})"
    }

  Surface(
    modifier = Modifier.offset { IntOffset(badgeX, badgeY) },
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    border = BorderStroke(1.dp, accentColor),
    shadowElevation = 4.dp,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E293B)
@Composable
private fun OverlayCaptureFeedbackSwipePreview() {
  TapFlowOverlayTheme {
    OverlayCaptureFeedbackLayer(
      state =
        OverlayUiState(
          mode = OverlayMode.GESTURE_PICKER,
          isCaptureActive = true,
          capturePhase = OverlayCapturePhase.INTERCEPTING,
          pickerTargetType = GesturePickerTargetType.SWIPE_PATH,
          statusHint = "Touch or drag on target location",
          touchPreview =
            OverlayTouchPreview(
              isTouching = true,
              startX = 180f,
              startY = 380f,
              currentX = 520f,
              currentY = 640f,
              pathPoints = listOf(Point2D(180, 380), Point2D(340, 480), Point2D(520, 640)),
              durationMs = 420L,
              isDrag = true,
            ),
        ),
    )
  }
}
