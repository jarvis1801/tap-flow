package com.tapflow.feature.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tapflow.core.database.R as DbR
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import com.tapflow.core.model.inspect.ScreenOrientation
import java.io.File
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
        InspectStudioUiEffect.NavigateBack -> {
          snackbarHostState.currentSnackbarData?.dismiss()
          onNavigateBack()
        }
        is InspectStudioUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is InspectStudioUiEffect.ShowSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(effect.message)
          }
        }
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
    contentColor = Color.White,
  ) { _ ->
    when {
      state.isLoading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      }
      item == null -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
          ) {
            Text(
              text = "Inspect item not found or was deleted.",
              color = Color.White,
              style = MaterialTheme.typography.titleMedium,
              textAlign = TextAlign.Center,
            )
            Button(
              onClick = { onIntent(InspectStudioUiIntent.BackClicked) },
              modifier = Modifier.heightIn(min = 48.dp).testTag("studio_not_found_back_button"),
            ) {
              Text("Go Back")
            }
          }
        }
      }
      state.isSourceImageMissing -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp).testTag("studio_missing_image_state"),
          ) {
            Icon(
              imageVector = Icons.Rounded.ImageNotSupported,
              contentDescription = "Original image was deleted",
              tint = Color.White.copy(alpha = 0.78f),
              modifier = Modifier.size(48.dp),
            )
            Text(
              text = "Original image was deleted",
              color = Color.White,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
            )
            Text(
              text = "The screenshot PNG for this inspect item was cleared from device storage. Saved pixel targets remain available in Inspect and Script pickers.",
              color = Color.White.copy(alpha = 0.72f),
              style = MaterialTheme.typography.bodySmall,
              textAlign = TextAlign.Center,
            )
            Button(
              onClick = { onIntent(InspectStudioUiIntent.BackClicked) },
              modifier = Modifier.heightIn(min = 48.dp).testTag("studio_missing_image_back_button"),
            ) {
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
            item = item,
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

  state.inspectedMarkerPopup?.let { popup ->
    MarkerInfoPopupDialog(
      popup = popup,
      loadedImage = loadedImage,
      onDelete = { id, label, isCrop ->
        onIntent(InspectStudioUiIntent.RequestDeleteMarker(id = id, label = label, isCrop = isCrop))
      },
      onDismiss = { onIntent(InspectStudioUiIntent.DismissMarkerInfo) },
    )
  }

  state.markerToDelete?.let { marker ->
    AlertDialog(
      onDismissRequest = { onIntent(InspectStudioUiIntent.DismissDeleteMarker) },
      title = { Text("Delete Saved ${if (marker.isCrop) "Crop" else "Pixel"}?") },
      text = {
        Text(
          if (marker.isCrop) {
            "Removes saved crop region \"${marker.label}\" from this screenshot."
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
  item: InspectItem,
  zoom: Float,
  markersVisible: Boolean,
  mode: StudioMode,
  isPro: Boolean,
  onBack: () -> Unit,
  onToggleMarkers: () -> Unit,
  onSwitchMode: (StudioMode) -> Unit,
) {
  val itemName = item.appName.ifBlank { item.packageName }.ifBlank { "Screenshot" }
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
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
          colors =
            IconButtonDefaults.iconButtonColors(
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          modifier = Modifier.size(48.dp).testTag("studio_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Navigate back",
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "$groupTitle / $itemName",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            StudioOrientationBadge(
              item = item,
              modifier = Modifier.testTag("studio_orientation_badge"),
            )
            Text(
              text = "${item.screenWidth} × ${item.screenHeight} • ${(zoom * 100).roundToInt()}%",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.align(Alignment.CenterVertically),
            )
          }
        }

        IconButton(
          onClick = onToggleMarkers,
          colors =
            IconButtonDefaults.iconButtonColors(
              containerColor =
                if (markersVisible) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHighest
                },
              contentColor =
                if (markersVisible) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
          modifier = Modifier.size(48.dp).testTag("studio_toggle_markers_button"),
        ) {
          Icon(
            imageVector =
              if (markersVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            contentDescription = if (markersVisible) "Hide markers" else "Show markers",
            tint =
              if (markersVisible) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val pixelSelected = mode == StudioMode.PIXEL_INSPECT
        FilterChip(
          selected = pixelSelected,
          onClick = { onSwitchMode(StudioMode.PIXEL_INSPECT) },
          colors =
            FilterChipDefaults.filterChipColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              labelColor = MaterialTheme.colorScheme.onSurface,
              iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
              selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          leadingIcon = {
            Icon(
              imageVector = Icons.Filled.Palette,
              contentDescription = null,
              tint =
                if (pixelSelected) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text(StudioMode.PIXEL_INSPECT.label) },
          modifier = Modifier.weight(1f).testTag("studio_mode_pixel"),
        )
        val cropSelected = mode == StudioMode.IMAGE_CROP
        FilterChip(
          selected = cropSelected,
          onClick = { onSwitchMode(StudioMode.IMAGE_CROP) },
          colors =
            FilterChipDefaults.filterChipColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              labelColor = MaterialTheme.colorScheme.onSurface,
              iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
              selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          leadingIcon = {
            Icon(
              imageVector = if (isPro) Icons.Filled.Crop else Icons.Filled.Lock,
              contentDescription = null,
              tint =
                if (cropSelected) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
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
private fun StudioOrientationBadge(
  item: InspectItem,
  modifier: Modifier = Modifier,
) {
  val orientation = item.screenOrientation
  val icon =
    when (orientation) {
      ScreenOrientation.PORTRAIT -> Icons.Rounded.StayCurrentPortrait
      ScreenOrientation.LANDSCAPE -> Icons.Rounded.StayCurrentLandscape
      ScreenOrientation.SQUARE_UNFOLDED -> Icons.Rounded.TabletAndroid
    }
  val label =
    when (orientation) {
      ScreenOrientation.PORTRAIT -> stringResource(DbR.string.orientation_portrait)
      ScreenOrientation.LANDSCAPE -> stringResource(DbR.string.orientation_landscape)
      ScreenOrientation.SQUARE_UNFOLDED ->
        when {
          item.screenHeight > item.screenWidth ->
            stringResource(DbR.string.orientation_unfolded_portrait)
          item.screenWidth > item.screenHeight ->
            stringResource(DbR.string.orientation_unfolded_landscape)
          else -> stringResource(DbR.string.orientation_unfolded_square)
        }
    }
  val containerColor =
    when (orientation) {
      ScreenOrientation.SQUARE_UNFOLDED -> MaterialTheme.colorScheme.tertiaryContainer
      ScreenOrientation.LANDSCAPE -> MaterialTheme.colorScheme.secondaryContainer
      ScreenOrientation.PORTRAIT -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
  val contentColor =
    when (orientation) {
      ScreenOrientation.SQUARE_UNFOLDED -> MaterialTheme.colorScheme.onTertiaryContainer
      ScreenOrientation.LANDSCAPE -> MaterialTheme.colorScheme.onSecondaryContainer
      ScreenOrientation.PORTRAIT -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = containerColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
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
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
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
              color = MaterialTheme.colorScheme.onSurface,
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
              colors =
                IconButtonDefaults.iconButtonColors(
                  contentColor = MaterialTheme.colorScheme.error,
                ),
              modifier = Modifier.size(48.dp).testTag("delete_selected_pixel_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.DeleteOutline,
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
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_pixel_target_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
          )
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
              val scale = min(size.width / sw.toFloat(), size.height / sh.toFloat())
              val dstW = (sw * scale).roundToInt().coerceAtLeast(1)
              val dstH = (sh * scale).roundToInt().coerceAtLeast(1)
              val dstX = ((size.width - dstW) / 2f).roundToInt()
              val dstY = ((size.height - dstH) / 2f).roundToInt()
              drawImage(
                image = img,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(sw, sh),
                dstOffset = IntOffset(dstX, dstY),
                dstSize = IntSize(dstW, dstH),
                filterQuality = FilterQuality.None,
              )
            }
          }

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "${state.cropWidth} × ${state.cropHeight} px @ (${state.cropX}, ${state.cropY})",
              style = MaterialTheme.typography.labelLarge,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              val moveSelected = !adjustSizeInsteadOfOrigin
              FilterChip(
                selected = moveSelected,
                onClick = { adjustSizeInsteadOfOrigin = false },
                colors =
                  FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  ),
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.OpenWith,
                    contentDescription = null,
                    tint =
                      if (moveSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      },
                    modifier = Modifier.size(14.dp),
                  )
                },
                label = { Text("Move X/Y", maxLines = 1) },
                modifier = Modifier.testTag("crop_mode_move"),
              )
              val resizeSelected = adjustSizeInsteadOfOrigin
              FilterChip(
                selected = resizeSelected,
                onClick = { adjustSizeInsteadOfOrigin = true },
                colors =
                  FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  ),
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.AspectRatio,
                    contentDescription = null,
                    tint =
                      if (resizeSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      },
                    modifier = Modifier.size(14.dp),
                  )
                },
                label = { Text("Resize W/H", maxLines = 1) },
                modifier = Modifier.testTag("crop_mode_resize"),
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
              colors =
                IconButtonDefaults.iconButtonColors(
                  contentColor = MaterialTheme.colorScheme.error,
                ),
              modifier = Modifier.size(48.dp).testTag("delete_selected_crop_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.DeleteOutline,
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
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_image_crop_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save Crop Region")
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
  val buttonColors =
    ButtonDefaults.outlinedButtonColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
      contentColor = MaterialTheme.colorScheme.onSurface,
    )
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    OutlinedButton(
      onClick = onLeft,
      colors = buttonColors,
      border = border,
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
      modifier = Modifier.size(38.dp).testTag("nudge_left"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        contentDescription = "Nudge left 1px",
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      OutlinedButton(
        onClick = onUp,
        colors = buttonColors,
        border = border,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(38.dp).testTag("nudge_up"),
      ) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowUp,
          contentDescription = "Nudge up 1px",
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
      OutlinedButton(
        onClick = onDown,
        colors = buttonColors,
        border = border,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(38.dp).testTag("nudge_down"),
      ) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowDown,
          contentDescription = "Nudge down 1px",
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
    OutlinedButton(
      onClick = onRight,
      colors = buttonColors,
      border = border,
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
      modifier = Modifier.size(38.dp).testTag("nudge_right"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = "Nudge right 1px",
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = if (isCrop) "Save Crop Region" else "Save Pixel Target",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
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
              colors =
                InputChipDefaults.inputChipColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                  labelColor = MaterialTheme.colorScheme.onSurface,
                  trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              label = { Text(tag) },
              trailingIcon = {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "Remove tag $tag",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
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

@Composable
private fun MarkerInfoPopupDialog(
  popup: StudioMarkerInfoPopup,
  loadedImage: StudioLoadedImage?,
  onDelete: (id: String, label: String, isCrop: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.testTag("studio_marker_info_dialog"),
    title = {
      Text(
        text =
          when (popup) {
            is StudioMarkerInfoPopup.Pixel -> popup.pixel.label.ifBlank { "Saved Pixel Marker" }
            is StudioMarkerInfoPopup.Crop -> popup.crop.label.ifBlank { "Saved Crop Marker" }
          },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    },
    text = {
      when (popup) {
        is StudioMarkerInfoPopup.Pixel -> {
          val px = popup.pixel
          val rgbTriple = parseRgbBreakdown(px.hexColor)
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(parseStudioHexColor(px.hexColor))
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
              )
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = px.hexColor.uppercase(Locale.US),
                  style = MaterialTheme.typography.titleMedium,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "RGB (${rgbTriple.first}, ${rgbTriple.second}, ${rgbTriple.third})",
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            MarkerInfoDetailRow(label = "Coordinates", value = "(${px.x}, ${px.y})")
            MarkerInfoDetailRow(label = "Color Tolerance", value = "±${px.tolerance}")
          }
        }

        is StudioMarkerInfoPopup.Crop -> {
          val crop = popup.crop
          val legacyCropFile = remember(crop.cropPngPath) {
            crop.cropPngPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
          }
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(112.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color(0xFF14181F))
                  .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
              contentAlignment = Alignment.Center,
            ) {
              if (loadedImage != null) {
                Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                  val img = loadedImage.imageBitmap
                  val sx = crop.x.coerceIn(0, (img.width - 1).coerceAtLeast(0))
                  val sy = crop.y.coerceIn(0, (img.height - 1).coerceAtLeast(0))
                  val sw = crop.width.coerceIn(1, (img.width - sx).coerceAtLeast(1))
                  val sh = crop.height.coerceIn(1, (img.height - sy).coerceAtLeast(1))
                  val scale = min(size.width / sw.toFloat(), size.height / sh.toFloat())
                  val dstW = (sw * scale).roundToInt().coerceAtLeast(1)
                  val dstH = (sh * scale).roundToInt().coerceAtLeast(1)
                  val dstX = ((size.width - dstW) / 2f).roundToInt()
                  val dstY = ((size.height - dstH) / 2f).roundToInt()
                  drawImage(
                    image = img,
                    srcOffset = IntOffset(sx, sy),
                    srcSize = IntSize(sw, sh),
                    dstOffset = IntOffset(dstX, dstY),
                    dstSize = IntSize(dstW, dstH),
                    filterQuality = FilterQuality.None,
                  )
                }
              } else if (legacyCropFile != null) {
                AsyncImage(
                  model = legacyCropFile,
                  contentDescription = crop.label.ifBlank { "Saved crop thumbnail" },
                  modifier = Modifier.fillMaxSize().padding(6.dp),
                )
              } else {
                Text(
                  text = "${crop.width} × ${crop.height} px",
                  style = MaterialTheme.typography.labelLarge,
                  fontFamily = FontFamily.Monospace,
                  color = Color.White.copy(alpha = 0.7f),
                )
              }
            }

            MarkerInfoDetailRow(label = "Origin (X, Y)", value = "(${crop.x}, ${crop.y})")
            MarkerInfoDetailRow(label = "Size (W × H)", value = "${crop.width} × ${crop.height} px")
            MarkerInfoDetailRow(
              label = "Similarity Threshold",
              value = "${(crop.similarityThreshold * 100f).roundToInt()}%",
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("studio_marker_info_close")) {
        Text("Close")
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          when (popup) {
            is StudioMarkerInfoPopup.Pixel ->
              onDelete(popup.pixel.id, popup.pixel.label.ifBlank { popup.pixel.hexColor }, false)
            is StudioMarkerInfoPopup.Crop ->
              onDelete(popup.crop.id, popup.crop.label.ifBlank { "${popup.crop.width}x${popup.crop.height}" }, true)
          }
        },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.testTag("studio_marker_info_delete"),
      ) {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Delete")
      }
    },
  )
}

@Composable
private fun MarkerInfoDetailRow(
  label: String,
  value: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

private fun parseRgbBreakdown(hex: String): Triple<Int, Int, Int> {
  val clean = hex.removePrefix("#").trim()
  val parsed = clean.toLongOrNull(16) ?: return Triple(0, 0, 0)
  val rgb = parsed.toInt()
  return Triple((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
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
