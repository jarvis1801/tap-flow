package com.tapflow.feature.studio

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel

enum class StudioMode(val label: String) {
  PIXEL_INSPECT("Pixel Inspect"),
  IMAGE_CROP("Image Crop"),
}

@Immutable
data class StudioMarkerToDelete(
  val id: String,
  val label: String,
  val isCrop: Boolean,
)

@Immutable
sealed interface StudioMarkerInfoPopup {
  @Immutable
  data class Pixel(val pixel: InspectPixel) : StudioMarkerInfoPopup

  @Immutable
  data class Crop(val crop: InspectCrop) : StudioMarkerInfoPopup
}

@Immutable
data class InspectStudioUiState(
  val itemId: String = "",
  val groupTitle: String = "Inspect",
  val groupTags: List<String> = emptyList(),
  val item: InspectItem? = null,
  val isLoading: Boolean = true,
  val isSourceImageMissing: Boolean = false,
  val isPro: Boolean = false,
  val mode: StudioMode = StudioMode.PIXEL_INSPECT,
  val markersVisible: Boolean = true,
  val cursorX: Int = 540,
  val cursorY: Int = 960,
  val cursorHex: String = "#FFFFFF",
  val cropX: Int = 440,
  val cropY: Int = 860,
  val cropWidth: Int = 128,
  val cropHeight: Int = 128,
  val selectedPixelId: String? = null,
  val selectedCropId: String? = null,
  val hasUnsavedChanges: Boolean = false,
  val showUnsavedDialog: Boolean = false,
  val showSaveSheet: Boolean = false,
  val inspectedMarkerPopup: StudioMarkerInfoPopup? = null,
  val markerToDelete: StudioMarkerToDelete? = null,
  val isSaving: Boolean = false,
)

sealed interface InspectStudioUiIntent {
  data object BackClicked : InspectStudioUiIntent

  data object DismissUnsavedDialog : InspectStudioUiIntent

  data object DiscardAndExit : InspectStudioUiIntent

  data object ToggleMarkersVisible : InspectStudioUiIntent

  data class SwitchMode(val mode: StudioMode) : InspectStudioUiIntent

  data class UpdatePixelCursor(val x: Int, val y: Int, val hexColor: String? = null) :
    InspectStudioUiIntent

  data class NudgePixel(val dx: Int, val dy: Int) : InspectStudioUiIntent

  data class UpdateCropBox(val x: Int, val y: Int, val width: Int, val height: Int) :
    InspectStudioUiIntent

  data class NudgeCropOrigin(val dx: Int, val dy: Int) : InspectStudioUiIntent

  data class ResizeCropBox(val dw: Int, val dh: Int) : InspectStudioUiIntent

  data class SelectPixelMarker(val pixel: InspectPixel) : InspectStudioUiIntent

  data class SelectCropMarker(val crop: InspectCrop) : InspectStudioUiIntent

  data class ShowMarkerInfo(val popup: StudioMarkerInfoPopup) : InspectStudioUiIntent

  data object DismissMarkerInfo : InspectStudioUiIntent

  data class RequestDeleteMarker(val id: String, val label: String, val isCrop: Boolean) :
    InspectStudioUiIntent

  data object ConfirmDeleteMarker : InspectStudioUiIntent

  data object DismissDeleteMarker : InspectStudioUiIntent

  data object OpenSaveSheet : InspectStudioUiIntent

  data object DismissSaveSheet : InspectStudioUiIntent

  data class ConfirmSaveTarget(
    val label: String,
    val tags: List<String>,
    val tolerance: Int,
    val similarityThreshold: Float,
  ) : InspectStudioUiIntent

  data class RequestProUpgrade(val featureName: String) : InspectStudioUiIntent
}

sealed interface InspectStudioUiEffect {
  data object NavigateBack : InspectStudioUiEffect

  data class NavigateToPaywall(val sourceFeature: String) : InspectStudioUiEffect

  data class ShowSnackbar(val message: String) : InspectStudioUiEffect
}
