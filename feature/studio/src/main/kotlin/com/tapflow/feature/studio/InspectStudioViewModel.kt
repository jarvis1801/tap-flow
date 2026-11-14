package com.tapflow.feature.studio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.repository.InspectRepository
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class InspectStudioViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val inspectRepository: InspectRepository,
  private val entitlementRepository: EntitlementRepository,
  private val studioBitmapProcessor: StudioBitmapProcessor,
) : ViewModel() {

  private val argItemId: String = savedStateHandle["inspectItemId"] ?: ""
  private var currentGroup: InspectGroup? = null
  private var initializedCursor = false

  private val _uiState = MutableStateFlow(InspectStudioUiState(itemId = argItemId))
  val uiState: StateFlow<InspectStudioUiState> = _uiState.asStateFlow()

  private val _effects = Channel<InspectStudioUiEffect>(Channel.BUFFERED)
  val effects: Flow<InspectStudioUiEffect> = _effects.receiveAsFlow()

  init {
    viewModelScope.launch {
      entitlementRepository.entitlementState.collectLatest { ent ->
        _uiState.update { it.copy(isPro = EntitlementValidator.isPro(ent)) }
      }
    }

    viewModelScope.launch {
      inspectRepository.observeGroupsWithItems().collectLatest { groups ->
        val owner = groups.firstOrNull { g -> g.items.any { it.id == argItemId } }
        if (owner != null) {
          currentGroup = owner.group
          _uiState.update {
            it.copy(
              groupTitle = owner.group.title,
              groupTags = owner.group.tags,
            )
          }
        }
      }
    }

    viewModelScope.launch {
      inspectRepository.observeItem(argItemId).collectLatest { item ->
        if (item == null) {
          _uiState.update { it.copy(isLoading = false, item = null) }
          return@collectLatest
        }

        val maxW = item.screenWidth.coerceAtLeast(100)
        val maxH = item.screenHeight.coerceAtLeast(100)
        val initialPixel = item.pixels.firstOrNull()
        val initialCrop = item.crops.firstOrNull()

        val initX = initialPixel?.x ?: (maxW / 2)
        val initY = initialPixel?.y ?: (maxH / 2)
        val initCropW = (initialCrop?.width ?: 128).coerceIn(16, maxW)
        val initCropH = (initialCrop?.height ?: 128).coerceIn(16, maxH)
        val initCropX = (initialCrop?.x ?: ((maxW - initCropW) / 2)).coerceIn(0, maxW - initCropW)
        val initCropY = (initialCrop?.y ?: ((maxH - initCropH) / 2)).coerceIn(0, maxH - initCropH)

        _uiState.update { state ->
          if (!initializedCursor) {
            initializedCursor = true
            state.copy(
              isLoading = false,
              item = item,
              cursorX = initX,
              cursorY = initY,
              cursorHex = initialPixel?.hexColor ?: state.cursorHex,
              selectedPixelId = initialPixel?.id,
              cropX = initCropX,
              cropY = initCropY,
              cropWidth = initCropW,
              cropHeight = initCropH,
              selectedCropId = initialCrop?.id,
            )
          } else {
            state.copy(
              isLoading = false,
              item = item,
            )
          }
        }

        if (initialPixel == null && item.fullScreenPngPath.isNotBlank()) {
          val sampledHex = studioBitmapProcessor.sampleHexColor(item.fullScreenPngPath, initX, initY)
          _uiState.update { it.copy(cursorHex = sampledHex) }
        }
      }
    }
  }

  fun onIntent(intent: InspectStudioUiIntent) {
    when (intent) {
      InspectStudioUiIntent.BackClicked -> {
        if (_uiState.value.hasUnsavedChanges) {
          _uiState.update { it.copy(showUnsavedDialog = true) }
        } else {
          sendEffect(InspectStudioUiEffect.NavigateBack)
        }
      }

      InspectStudioUiIntent.DismissUnsavedDialog -> {
        _uiState.update { it.copy(showUnsavedDialog = false) }
      }

      InspectStudioUiIntent.DiscardAndExit -> {
        _uiState.update { it.copy(showUnsavedDialog = false) }
        sendEffect(InspectStudioUiEffect.NavigateBack)
      }

      InspectStudioUiIntent.ToggleMarkersVisible -> {
        _uiState.update { it.copy(markersVisible = !it.markersVisible) }
      }

      is InspectStudioUiIntent.SwitchMode -> {
        if (intent.mode == StudioMode.IMAGE_CROP &&
          !entitlementRepository.canUseFeature(ProFeatureGate.INSPECT_STUDIO_CROP)
        ) {
          sendEffect(
            InspectStudioUiEffect.NavigateToPaywall(ProFeatureGate.INSPECT_STUDIO_CROP.displayName),
          )
          return
        }
        _uiState.update { it.copy(mode = intent.mode) }
      }

      is InspectStudioUiIntent.UpdatePixelCursor -> {
        updatePixelPosition(intent.x, intent.y, intent.hexColor)
      }

      is InspectStudioUiIntent.NudgePixel -> {
        val state = _uiState.value
        updatePixelPosition(state.cursorX + intent.dx, state.cursorY + intent.dy, null)
      }

      is InspectStudioUiIntent.UpdateCropBox -> {
        val item = _uiState.value.item ?: return
        val maxW = item.screenWidth.coerceAtLeast(16)
        val maxH = item.screenHeight.coerceAtLeast(16)
        val clampedW = intent.width.coerceIn(8, maxW)
        val clampedH = intent.height.coerceIn(8, maxH)
        val clampedX = intent.x.coerceIn(0, (maxW - clampedW).coerceAtLeast(0))
        val clampedY = intent.y.coerceIn(0, (maxH - clampedH).coerceAtLeast(0))
        _uiState.update {
          it.copy(
            cropX = clampedX,
            cropY = clampedY,
            cropWidth = clampedW,
            cropHeight = clampedH,
            selectedCropId = null,
            hasUnsavedChanges = true,
          )
        }
      }

      is InspectStudioUiIntent.NudgeCropOrigin -> {
        val state = _uiState.value
        onIntent(
          InspectStudioUiIntent.UpdateCropBox(
            x = state.cropX + intent.dx,
            y = state.cropY + intent.dy,
            width = state.cropWidth,
            height = state.cropHeight,
          ),
        )
      }

      is InspectStudioUiIntent.ResizeCropBox -> {
        val state = _uiState.value
        onIntent(
          InspectStudioUiIntent.UpdateCropBox(
            x = state.cropX,
            y = state.cropY,
            width = state.cropWidth + intent.dw,
            height = state.cropHeight + intent.dh,
          ),
        )
      }

      is InspectStudioUiIntent.SelectPixelMarker -> {
        _uiState.update {
          it.copy(
            mode = StudioMode.PIXEL_INSPECT,
            cursorX = intent.pixel.x,
            cursorY = intent.pixel.y,
            cursorHex = intent.pixel.hexColor,
            selectedPixelId = intent.pixel.id,
          )
        }
      }

      is InspectStudioUiIntent.SelectCropMarker -> {
        if (!entitlementRepository.canUseFeature(ProFeatureGate.INSPECT_STUDIO_CROP)) {
          sendEffect(
            InspectStudioUiEffect.NavigateToPaywall(ProFeatureGate.INSPECT_STUDIO_CROP.displayName),
          )
          return
        }
        _uiState.update {
          it.copy(
            mode = StudioMode.IMAGE_CROP,
            cropX = intent.crop.x,
            cropY = intent.crop.y,
            cropWidth = intent.crop.width,
            cropHeight = intent.crop.height,
            selectedCropId = intent.crop.id,
          )
        }
      }

      is InspectStudioUiIntent.RequestDeleteMarker -> {
        _uiState.update {
          it.copy(
            markerToDelete = StudioMarkerToDelete(intent.id, intent.label, intent.isCrop),
          )
        }
      }

      InspectStudioUiIntent.DismissDeleteMarker -> {
        _uiState.update { it.copy(markerToDelete = null) }
      }

      InspectStudioUiIntent.ConfirmDeleteMarker -> {
        val target = _uiState.value.markerToDelete ?: return
        val itemId = _uiState.value.item?.id ?: return
        _uiState.update { it.copy(markerToDelete = null) }
        viewModelScope.launch {
          if (target.isCrop) {
            inspectRepository.deleteCropFromItem(itemId, target.id)
            _uiState.update {
              it.copy(selectedCropId = if (it.selectedCropId == target.id) null else it.selectedCropId)
            }
          } else {
            inspectRepository.deletePixelFromItem(itemId, target.id)
            _uiState.update {
              it.copy(selectedPixelId = if (it.selectedPixelId == target.id) null else it.selectedPixelId)
            }
          }
          sendEffect(InspectStudioUiEffect.ShowSnackbar("Deleted marker \"${target.label}\""))
        }
      }

      InspectStudioUiIntent.OpenSaveSheet -> {
        if (_uiState.value.mode == StudioMode.IMAGE_CROP &&
          !entitlementRepository.canUseFeature(ProFeatureGate.INSPECT_STUDIO_CROP)
        ) {
          sendEffect(
            InspectStudioUiEffect.NavigateToPaywall(ProFeatureGate.INSPECT_STUDIO_CROP.displayName),
          )
          return
        }
        _uiState.update { it.copy(showSaveSheet = true) }
      }

      InspectStudioUiIntent.DismissSaveSheet -> {
        _uiState.update { it.copy(showSaveSheet = false) }
      }

      is InspectStudioUiIntent.ConfirmSaveTarget -> {
        saveTarget(
          label = intent.label,
          tags = intent.tags,
          tolerance = intent.tolerance,
          similarityThreshold = intent.similarityThreshold,
        )
      }

      is InspectStudioUiIntent.RequestProUpgrade -> {
        sendEffect(InspectStudioUiEffect.NavigateToPaywall(intent.featureName))
      }
    }
  }

  private fun updatePixelPosition(rawX: Int, rawY: Int, knownHex: String?) {
    val item = _uiState.value.item ?: return
    val clampedX = rawX.coerceIn(0, (item.screenWidth - 1).coerceAtLeast(0))
    val clampedY = rawY.coerceIn(0, (item.screenHeight - 1).coerceAtLeast(0))

    _uiState.update {
      it.copy(
        cursorX = clampedX,
        cursorY = clampedY,
        cursorHex = knownHex ?: it.cursorHex,
        selectedPixelId = null,
        hasUnsavedChanges = true,
      )
    }

    if (knownHex == null && item.fullScreenPngPath.isNotBlank()) {
      viewModelScope.launch {
        val hex = studioBitmapProcessor.sampleHexColor(item.fullScreenPngPath, clampedX, clampedY)
        _uiState.update { state ->
          if (state.cursorX == clampedX && state.cursorY == clampedY) {
            state.copy(cursorHex = hex)
          } else {
            state
          }
        }
      }
    }
  }

  private fun saveTarget(
    label: String,
    tags: List<String>,
    tolerance: Int,
    similarityThreshold: Float,
  ) {
    val state = _uiState.value
    val item = state.item ?: return
    if (state.isSaving) return

    _uiState.update { it.copy(isSaving = true, showSaveSheet = false) }
    viewModelScope.launch {
      runCatching {
        currentGroup?.let { group ->
          val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
          if (cleanTags != group.tags) {
            val updatedGroup = group.copy(tags = cleanTags)
            inspectRepository.saveGroup(updatedGroup)
            currentGroup = updatedGroup
          }
        }

        val updatedItem: InspectItem =
          if (state.mode == StudioMode.PIXEL_INSPECT) {
            val newPixel =
              InspectPixel(
                id = UUID.randomUUID().toString(),
                label = label.trim().ifBlank { "Pixel (${state.cursorX}, ${state.cursorY})" },
                x = state.cursorX,
                y = state.cursorY,
                hexColor = state.cursorHex,
                tolerance = tolerance.coerceIn(0, 255),
              )
            item.copy(pixels = item.pixels + newPixel)
          } else {
            val cropPath =
              studioBitmapProcessor.saveLosslessCropPng(
                fullScreenPngPath = item.fullScreenPngPath,
                x = state.cropX,
                y = state.cropY,
                width = state.cropWidth,
                height = state.cropHeight,
              )
            val newCrop =
              InspectCrop(
                id = UUID.randomUUID().toString(),
                label = label.trim().ifBlank { "Crop ${state.cropWidth}×${state.cropHeight}" },
                cropPngPath = cropPath,
                x = state.cropX,
                y = state.cropY,
                width = state.cropWidth,
                height = state.cropHeight,
                similarityThreshold = similarityThreshold.coerceIn(0.50f, 0.99f),
              )
            item.copy(crops = item.crops + newCrop)
          }
        inspectRepository.saveItem(updatedItem)
      }.onSuccess {
        _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
        val kind = if (state.mode == StudioMode.PIXEL_INSPECT) "Pixel target" else "Lossless image crop"
        sendEffect(InspectStudioUiEffect.ShowSnackbar("Saved $kind"))
      }.onFailure { err ->
        _uiState.update { it.copy(isSaving = false) }
        sendEffect(InspectStudioUiEffect.ShowSnackbar(err.message ?: "Failed to save inspect target"))
      }
    }
  }

  private fun sendEffect(effect: InspectStudioUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }
}
