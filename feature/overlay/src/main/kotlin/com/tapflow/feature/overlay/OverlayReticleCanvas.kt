package com.tapflow.feature.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.tapflow.core.service.engine.DebugTouchMarkerEvent
import com.tapflow.core.service.overlay.GesturePickerTargetType
import com.tapflow.core.service.overlay.OverlayCapturePhase
import com.tapflow.core.service.overlay.OverlayMode
import com.tapflow.core.service.overlay.OverlaySessionState
import com.tapflow.core.service.overlay.OverlayTouchPreview
import com.tapflow.core.service.overlay.OverlayUiState
import com.tapflow.core.service.theme.TapFlowTheme
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

  if (!state.isCaptureActive &&
    state.captureAlertMessage == null &&
    state.activeDebugMarkers.isEmpty()
  ) {
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

    if (state.activeDebugMarkers.isNotEmpty()) {
      DebugTouchMarkerLayer(
        markers = state.activeDebugMarkers,
        accentColor = MaterialTheme.colorScheme.primary,
      )
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
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
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
private fun CaptureBorderAndReticleCanvas(
  mode: OverlayMode,
  pickerTargetType: GesturePickerTargetType?,
  touchPreview: OverlayTouchPreview?,
  accentColor: Color,
) {
  val secondaryReticleColor = MaterialTheme.colorScheme.tertiary
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
      drawReticle(center = secOffset, color = secondaryReticleColor, radiusPx = 18.dp.toPx())
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

@Composable
private fun DebugTouchMarkerLayer(
  markers: List<DebugTouchMarkerEvent>,
  accentColor: Color,
) {
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .testTag("overlay_debug_touch_marker_layer"),
  ) {
    markers.forEach { marker ->
      key(marker.id) {
        DebugTouchMarkerItem(marker = marker, accentColor = accentColor)
      }
    }
  }
}

@Composable
private fun DebugTouchMarkerItem(
  marker: DebugTouchMarkerEvent,
  accentColor: Color,
) {
  val progress = remember(marker.id) { Animatable(0f) }
  LaunchedEffect(marker.id) {
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
    )
  }
  val t = progress.value
  val alpha = (1f - t).coerceIn(0f, 1f)
  if (alpha <= 0.01f) return

  Canvas(modifier = Modifier.fillMaxSize()) {
    val ringColor = accentColor.copy(alpha = 0.85f * alpha)
    val fillTint = accentColor.copy(alpha = 0.14f * alpha)
    val ringStrokeWidth = 2.5.dp.toPx()

    if (marker.isSwipe && marker.points.size >= 2) {
      val trailPath =
        Path().apply {
          val first = marker.points.first()
          moveTo(first.x.toFloat(), first.y.toFloat())
          for (i in 1 until marker.points.size) {
            val pt = marker.points[i]
            lineTo(pt.x.toFloat(), pt.y.toFloat())
          }
        }
      drawPath(
        path = trailPath,
        color = accentColor.copy(alpha = 0.55f * alpha),
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
      val startPt = marker.points.first()
      val endPt = marker.points.last()
      drawCircle(
        color = fillTint,
        radius = 12.dp.toPx(),
        center = Offset(startPt.x.toFloat(), startPt.y.toFloat()),
      )
      drawCircle(
        color = ringColor,
        radius = 12.dp.toPx(),
        center = Offset(startPt.x.toFloat(), startPt.y.toFloat()),
        style = Stroke(width = ringStrokeWidth),
      )
      val endRadius = 14.dp.toPx() + (10.dp.toPx() * t)
      drawCircle(
        color = fillTint,
        radius = endRadius,
        center = Offset(endPt.x.toFloat(), endPt.y.toFloat()),
      )
      drawCircle(
        color = ringColor,
        radius = endRadius,
        center = Offset(endPt.x.toFloat(), endPt.y.toFloat()),
        style = Stroke(width = ringStrokeWidth),
      )
    } else {
      val radius = 16.dp.toPx() + (12.dp.toPx() * t)
      marker.points.forEach { pt ->
        val center = Offset(pt.x.toFloat(), pt.y.toFloat())
        drawCircle(
          color = fillTint,
          radius = radius,
          center = center,
        )
        drawCircle(
          color = ringColor,
          radius = radius,
          center = center,
          style = Stroke(width = ringStrokeWidth),
        )
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E293B)
@Composable
private fun OverlayCaptureFeedbackSwipePreview() {
  TapFlowTheme {
    TapFlowOverlayTheme {
      OverlayCaptureFeedbackLayer(
        state =
          OverlayUiState(
            mode = OverlayMode.GESTURE_PICKER,
            sessionState = OverlaySessionState.ACTIVE,
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
}
