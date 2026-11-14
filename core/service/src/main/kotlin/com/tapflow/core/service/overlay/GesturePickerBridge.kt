package com.tapflow.core.service.overlay

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.action.Point2D
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GesturePickerTargetType {
  POINT,
  LONG_PRESS,
  SWIPE_PATH,
  MULTI_TOUCH_ANCHOR,
  REGION_BOUNDS,
}

@Immutable
data class GesturePickerRequest(
  val requestId: String,
  val targetType: GesturePickerTargetType,
)

@Immutable
data class PickedGestureResult(
  val requestId: String,
  val targetType: GesturePickerTargetType,
  val startX: Int,
  val startY: Int,
  val endX: Int,
  val endY: Int,
  val pathPoints: List<Point2D>,
  val durationMs: Long,
)

@Singleton
class GesturePickerBridge @Inject constructor() {
  private val _pendingRequest = MutableStateFlow<GesturePickerRequest?>(null)
  val pendingRequest: StateFlow<GesturePickerRequest?> = _pendingRequest.asStateFlow()

  private val _pickedResult = MutableStateFlow<PickedGestureResult?>(null)
  val pickedResult: StateFlow<PickedGestureResult?> = _pickedResult.asStateFlow()

  fun beginPick(requestId: String, targetType: GesturePickerTargetType) {
    _pickedResult.value = null
    _pendingRequest.value = GesturePickerRequest(requestId = requestId, targetType = targetType)
  }

  fun publishResult(
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    pathPoints: List<Point2D>,
    durationMs: Long,
  ) {
    val req = _pendingRequest.value ?: return
    _pickedResult.value =
      PickedGestureResult(
        requestId = req.requestId,
        targetType = req.targetType,
        startX = startX,
        startY = startY,
        endX = endX,
        endY = endY,
        pathPoints = pathPoints,
        durationMs = durationMs,
      )
    _pendingRequest.value = null
  }

  fun cancelPendingPick() {
    _pendingRequest.value = null
  }

  fun consumeResult(requestId: String? = null): PickedGestureResult? {
    val current = _pickedResult.value ?: return null
    if (requestId != null && current.requestId != requestId) return null
    _pickedResult.value = null
    return current
  }
}
