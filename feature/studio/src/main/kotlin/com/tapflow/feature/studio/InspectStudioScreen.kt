package com.tapflow.feature.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import kotlin.math.roundToInt

@Composable
fun InspectStudioScreen(
  onNavigateBack: () -> Unit,
  onNavigateToPaywall: (String) -> Unit,
  viewModel: InspectStudioViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  BackHandler {
    viewModel.onIntent(InspectStudioUiIntent.BackClicked)
  }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        InspectStudioUiEffect.NavigateBack -> onNavigateBack()
        is InspectStudioUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is InspectStudioUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
      }
    }
  }

  InspectStudioContent(
    state = state,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
  )
}

@Composable
fun InspectStudioContent(
  state: InspectStudioUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (InspectStudioUiIntent) -> Unit,
) {
  val item = state.item
  val loadedImage = rememberCoilSoftwareImage(item?.fullScreenPngPath.orEmpty())
  var zoom by remember { mutableFloatStateOf(1f) }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = Color(0xFF0E1014),
  ) { _ ->
    when {
      state.isLoading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      item == null -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "Inspect item not found or was deleted.",
              color = Color.White,
              style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = { onIntent(InspectStudioUiIntent.BackClicked) }) {
              Text("Go Back")
            }
          }
        }
      }
      else -> {
        Column(modifier = Modifier.fillMaxSize()) {
          // Top Studio Bar
          StudioTopBar(
            groupTitle = state.groupTitle,
            itemName = item.appName.ifBlank { item.packageName }.ifBlank { "Screenshot" },
            zoom = zoom,
            markersVisible = state.markersVisible,
            mode = state.mode,
            isPro = state.isPro,
            onBack = { onIntent(InspectStudioUiIntent.BackClicked) },
            onToggleMarkers = { onIntent(InspectStudioUiIntent.ToggleMarkersVisible) },
            onSwitchMode = { onIntent(InspectStudioUiIntent.SwitchMode(it)) },
          )

          // Fullscreen Interactive Canvas
          InteractiveInspectCanvas(
            state = state,
            item = item,
            loadedImage = loadedImage,
            zoom = zoom,
            onZoomChange = { zoom = it },
            onIntent = onIntent,
            modifier = Modifier.weight(1f),
          )

          // Bottom Control Bar
          StudioBottomControlBar(
            state = state,
            item = item,
            loadedImage = loadedImage,
            onIntent = onIntent,
          )
        }
      }
    }
  }

  if (state.showUnsavedDialog) {
    AlertDialog(
      onDismissRequest = { onIntent(InspectStudioUiIntent.DismissUnsavedDialog) },
      title = { Text("Discard Adjusted Target?") },
      text = { Text("You moved the reticle or crop box without saving it.") },
      confirmButton = {
        Button(onClick = { onIntent(InspectStudioUiIntent.DiscardAndExit) }) {
          Text("Discard & Exit")
        }
      },
      dismissButton = {
        TextButton(onClick = { onIntent(InspectStudioUiIntent.DismissUnsavedDialog) }) {
          Text("Keep Editing")
        }
      },
    )
  }

  if (state.showSaveSheet) {
    StudioSaveConfirmationSheet(
      state = state,
      onDismiss = { onIntent(InspectStudioUiIntent.DismissSaveSheet) },
      onSave = { label, tags, tolerance, similarity ->
        onIntent(
          InspectStudioUiIntent.ConfirmSaveTarget(
            label = label,
            tags = tags,
            tolerance = tolerance,
            similarityThreshold = similarity,
          ),
        )
      },
    )
  }

  state.markerToDelete?.let { marker ->
    AlertDialog(
      onDismissRequest = { onIntent(InspectStudioUiIntent.DismissDeleteMarker) },
      title = { Text("Delete Saved ${if (marker.isCrop) "Crop" else "Pixel"}?") },
      text = {
        Text(
          if (marker.isCrop) {
            "Deletes \"${marker.label}\" and its lossless template PNG file from Inspect storage."
          } else {
            "Removes saved pixel target \"${marker.label}\"."
          },
        )
      },
      confirmButton = {
        Button(
          onClick = { onIntent(InspectStudioUiIntent.ConfirmDeleteMarker) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_delete_studio_marker"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { onIntent(InspectStudioUiIntent.DismissDeleteMarker) }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun StudioTopBar(
  groupTitle: String,
  itemName: String,
  zoom: Float,
  markersVisible: Boolean,
  mode: StudioMode,
  isPro: Boolean,
  onBack: () -> Unit,
  onToggleMarkers: () -> Unit,
  onSwitchMode: (StudioMode) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    tonalElevation = 4.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier
          .windowInsetsPadding(WindowInsets.statusBars)
          .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("studio_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "$groupTitle / $itemName",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Zoom ${(zoom * 100).roundToInt()}% (Pinch 100–1000%)",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = onToggleMarkers,
          modifier = Modifier.size(48.dp).testTag("studio_toggle_markers_button"),
        ) {
          Icon(
            imageVector =
              if (markersVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
            contentDescription = if (markersVisible) "Hide markers" else "Show markers",
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = mode == StudioMode.PIXEL_INSPECT,
          onClick = { onSwitchMode(StudioMode.PIXEL_INSPECT) },
          leadingIcon = {
            Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          label = { Text(StudioMode.PIXEL_INSPECT.label) },
          modifier = Modifier.weight(1f).testTag("studio_mode_pixel"),
        )
        FilterChip(
          selected = mode == StudioMode.IMAGE_CROP,
          onClick = { onSwitchMode(StudioMode.IMAGE_CROP) },
          leadingIcon = {
            Icon(
              imageVector = if (isPro) Icons.Filled.Crop else Icons.Filled.Lock,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text(StudioMode.IMAGE_CROP.label) },
          modifier = Modifier.weight(1f).testTag("studio_mode_crop"),
        )
      }
    }
  }
}

@Composable
private fun StudioBottomControlBar(
  state: InspectStudioUiState,
  item: InspectItem,
  loadedImage: StudioLoadedImage?,
  onIntent: (InspectStudioUiIntent) -> Unit,
) {
  var adjustSizeInsteadOfOrigin by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    tonalElevation = 8.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier
          .navigationBarsPadding()
          .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (state.mode == StudioMode.PIXEL_INSPECT) {
        val selectedPixel = item.pixels.firstOrNull { it.id == state.selectedPixelId }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Swatch + HEX + Coordinates
          Box(
            modifier =
              Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(parseStudioHexColor(state.cursorHex))
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${state.cursorHex} • (${state.cursorX}, ${state.cursorY})",
              style = MaterialTheme.typography.titleSmall,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                selectedPixel?.let { "Selected: ${it.label}" }
                  ?: "1px Precision Reticle • Tap marker to select",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          if (selectedPixel != null) {
            IconButton(
              onClick = {
                onIntent(
                  InspectStudioUiIntent.RequestDeleteMarker(
                    id = selectedPixel.id,
                    label = selectedPixel.label.ifBlank { selectedPixel.hexColor },
                    isCrop = false,
                  ),
                )
              },
              modifier = Modifier.size(48.dp).testTag("delete_selected_pixel_button"),
            ) {
              Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Delete selected pixel",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }

          NudgeDPad(
            onUp = { onIntent(InspectStudioUiIntent.NudgePixel(0, -1)) },
            onDown = { onIntent(InspectStudioUiIntent.NudgePixel(0, +1)) },
            onLeft = { onIntent(InspectStudioUiIntent.NudgePixel(-1, 0)) },
            onRight = { onIntent(InspectStudioUiIntent.NudgePixel(+1, 0)) },
          )
        }

        Button(
          onClick = { onIntent(InspectStudioUiIntent.OpenSaveSheet) },
          enabled = !state.isSaving,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_pixel_target_button"),
        ) {
          Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save Pixel Target")
        }
      } else {
        val selectedCrop = item.crops.firstOrNull { it.id == state.selectedCropId }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Live Cropped Thumbnail Preview
          Box(
            modifier =
              Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1E24))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val img = loadedImage?.imageBitmap ?: return@Canvas
              val sx = state.cropX.coerceIn(0, (img.width - 1).coerceAtLeast(0))
              val sy = state.cropY.coerceIn(0, (img.height - 1).coerceAtLeast(0))
              val sw = state.cropWidth.coerceIn(1, (img.width - sx).coerceAtLeast(1))
              val sh = state.cropHeight.coerceIn(1, (img.height - sy).coerceAtLeast(1))
              drawImage(
                image = img,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(sw, sh),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.None,
              )
            }
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${state.cropWidth} × ${state.cropHeight} px @ (${state.cropX}, ${state.cropY})",
              style = MaterialTheme.typography.labelLarge,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              FilterChip(
                selected = !adjustSizeInsteadOfOrigin,
                onClick = { adjustSizeInsteadOfOrigin = false },
                label = { Text("Move X/Y") },
              )
              FilterChip(
                selected = adjustSizeInsteadOfOrigin,
                onClick = { adjustSizeInsteadOfOrigin = true },
                label = { Text("Resize W/H") },
              )
            }
          }

          if (selectedCrop != null) {
            IconButton(
              onClick = {
                onIntent(
                  InspectStudioUiIntent.RequestDeleteMarker(
                    id = selectedCrop.id,
                    label = selectedCrop.label.ifBlank { "${selectedCrop.width}x${selectedCrop.height}" },
                    isCrop = true,
                  ),
                )
              },
              modifier = Modifier.size(48.dp).testTag("delete_selected_crop_button"),
            ) {
              Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Delete selected crop",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }

          NudgeDPad(
            onUp = {
              if (adjustSizeInsteadOfOrigin) {
                onIntent(InspectStudioUiIntent.ResizeCropBox(0, -1))
              } else {
                onIntent(InspectStudioUiIntent.NudgeCropOrigin(0, -1))
              }
            },
            onDown = {
              if (adjustSizeInsteadOfOrigin) {
                onIntent(InspectStudioUiIntent.ResizeCropBox(0, +1))
              } else {
                onIntent(InspectStudioUiIntent.NudgeCropOrigin(0, +1))
              }
            },
            onLeft = {
              if (adjustSizeInsteadOfOrigin) {
                onIntent(InspectStudioUiIntent.ResizeCropBox(-1, 0))
              } else {
                onIntent(InspectStudioUiIntent.NudgeCropOrigin(-1, 0))
              }
            },
            onRight = {
              if (adjustSizeInsteadOfOrigin) {
                onIntent(InspectStudioUiIntent.ResizeCropBox(+1, 0))
              } else {
                onIntent(InspectStudioUiIntent.NudgeCropOrigin(+1, 0))
              }
            },
          )
        }

        Button(
          onClick = { onIntent(InspectStudioUiIntent.OpenSaveSheet) },
          enabled = !state.isSaving,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_image_crop_button"),
        ) {
          Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save Image Crop (Lossless PNG)")
        }
      }
    }
  }
}

@Composable
private fun NudgeDPad(
  onUp: () -> Unit,
  onDown: () -> Unit,
  onLeft: () -> Unit,
  onRight: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    OutlinedButton(
      onClick = onLeft,
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
      modifier = Modifier.size(38.dp).testTag("nudge_left"),
    ) {
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Nudge left 1px")
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      OutlinedButton(
        onClick = onUp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(38.dp).testTag("nudge_up"),
      ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Nudge up 1px")
      }
      OutlinedButton(
        onClick = onDown,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(38.dp).testTag("nudge_down"),
      ) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nudge down 1px")
      }
    }
    OutlinedButton(
      onClick = onRight,
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
      modifier = Modifier.size(38.dp).testTag("nudge_right"),
    ) {
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nudge right 1px")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StudioSaveConfirmationSheet(
  state: InspectStudioUiState,
  onDismiss: () -> Unit,
  onSave: (label: String, tags: List<String>, tolerance: Int, similarity: Float) -> Unit,
) {
  val isCrop = state.mode == StudioMode.IMAGE_CROP
  var labelInput by remember {
    mutableStateOf(
      if (isCrop) {
        "Crop ${state.cropWidth}x${state.cropHeight}"
      } else {
        "Pixel ${state.cursorHex}"
      },
    )
  }
  var tags by remember(state.groupTags) { mutableStateOf(state.groupTags) }
  var tagDraft by remember { mutableStateOf("") }
  var tolerance by remember { mutableIntStateOf(15) }
  var similarity by remember { mutableFloatStateOf(0.85f) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = if (isCrop) "Save Lossless Image Crop" else "Save Pixel Target",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      OutlinedTextField(
        value = labelInput,
        onValueChange = { labelInput = it },
        label = { Text("Target Label") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("studio_save_label_input"),
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = tagDraft,
          onValueChange = { tagDraft = it },
          label = { Text("Group Tag (Optional)") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        Button(
          onClick = {
            val clean = tagDraft.trim()
            if (clean.isNotBlank() && clean !in tags) {
              tags = tags + clean
            }
            tagDraft = ""
          },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text("Add")
        }
      }

      if (tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          tags.forEach { tag ->
            InputChip(
              selected = false,
              onClick = { tags = tags - tag },
              label = { Text(tag) },
              trailingIcon = {
                Icon(Icons.Filled.Close, contentDescription = "Remove tag $tag", modifier = Modifier.size(16.dp))
              },
            )
          }
        }
      }

      if (isCrop) {
        Text(
          text = "Confidence / Similarity Threshold: ${(similarity * 100f).roundToInt()}%",
          style = MaterialTheme.typography.labelLarge,
        )
        Slider(
          value = similarity,
          onValueChange = { similarity = it },
          valueRange = 0.50f..0.99f,
        )
      } else {
        Text(
          text = "RGB Color Tolerance: ±$tolerance (0–255)",
          style = MaterialTheme.typography.labelLarge,
        )
        Slider(
          value = tolerance.toFloat(),
          onValueChange = { tolerance = it.roundToInt().coerceIn(0, 255) },
          valueRange = 0f..120f,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = { onSave(labelInput, tags, tolerance, similarity) },
          modifier = Modifier.heightIn(min = 48.dp).testTag("studio_confirm_save_button"),
        ) {
          Text("Save")
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun InspectStudioPreview() {
  MaterialTheme {
    InspectStudioContent(
      state =
        InspectStudioUiState(
          itemId = "item1",
          groupTitle = "Arena Menu",
          isLoading = false,
          isPro = true,
          mode = StudioMode.PIXEL_INSPECT,
          cursorX = 540,
          cursorY = 1280,
          cursorHex = "#00C853",
          item =
            InspectItem(
              id = "item1",
              groupId = "g1",
              fullScreenPngPath = "",
              screenWidth = 1080,
              screenHeight = 2400,
              appName = "Sample Game",
              packageName = "com.sample.game",
              pixels =
                listOf(
                  InspectPixel(
                    id = "px1",
                    label = "Battle Ready",
                    x = 540,
                    y = 1280,
                    hexColor = "#00C853",
                  ),
                ),
              crops =
                listOf(
                  InspectCrop(
                    id = "cr1",
                    label = "Go Button",
                    cropPngPath = "",
                    x = 380,
                    y = 1800,
                    width = 240,
                    height = 96,
                  ),
                ),
            ),
        ),
      snackbarHostState = remember { SnackbarHostState() },
      onIntent = {},
    )
  }
}
