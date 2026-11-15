package com.tapflow.core.model.inspect

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class InspectGroup(
  val id: String,
  val title: String,
  val tags: List<String> = emptyList(),
  val createdAtMs: Long = 0L,
)

@Immutable
@Serializable
data class InspectPixel(
  val id: String,
  val label: String,
  val x: Int,
  val y: Int,
  val hexColor: String,
  val tolerance: Int = 15,
)

@Immutable
@Serializable
data class InspectCrop(
  val id: String,
  val label: String,
  val cropPngPath: String? = null,
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int,
  val similarityThreshold: Float = 0.85f,
)

@Immutable
@Serializable
enum class ScreenOrientation {
  PORTRAIT,
  LANDSCAPE,
  SQUARE_UNFOLDED,
}

@Immutable
@Serializable
data class InspectItem(
  val id: String,
  val groupId: String,
  val fullScreenPngPath: String,
  val screenWidth: Int,
  val screenHeight: Int,
  val appName: String,
  val packageName: String,
  val pixels: List<InspectPixel> = emptyList(),
  val crops: List<InspectCrop> = emptyList(),
  val createdAtMs: Long = 0L,
) {
  val aspectRatio: Float
    get() {
      val safeW = screenWidth.coerceAtLeast(1)
      val safeH = screenHeight.coerceAtLeast(1)
      return maxOf(safeW, safeH).toFloat() / minOf(safeW, safeH).toFloat()
    }

  val screenOrientation: ScreenOrientation
    get() =
      when {
        aspectRatio < FOLDABLE_SQUARE_ASPECT_THRESHOLD -> ScreenOrientation.SQUARE_UNFOLDED
        screenWidth > screenHeight -> ScreenOrientation.LANDSCAPE
        else -> ScreenOrientation.PORTRAIT
      }

  val orientationSubHint: String?
    get() =
      when (screenOrientation) {
        ScreenOrientation.SQUARE_UNFOLDED ->
          when {
            screenHeight > screenWidth -> "Unfolded (Portrait)"
            screenWidth > screenHeight -> "Unfolded (Landscape)"
            else -> "Unfolded (Square)"
          }
        ScreenOrientation.PORTRAIT,
        ScreenOrientation.LANDSCAPE -> null
      }

  companion object {
    const val FOLDABLE_SQUARE_ASPECT_THRESHOLD = 1.28f
  }
}
