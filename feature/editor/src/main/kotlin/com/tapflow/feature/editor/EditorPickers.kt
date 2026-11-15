package com.tapflow.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect as AndroidRect
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.tapflow.core.database.R as DbR
import com.tapflow.core.database.repository.InspectGroupWithItems
import com.tapflow.core.model.common.DurationInput
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import com.tapflow.core.model.inspect.ScreenOrientation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumericInputField(
  label: String,
  value: NumericInput,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onValueChange: (NumericInput) -> Unit,
  onProRequested: (String) -> Unit,
  modifier: Modifier = Modifier,
  errorText: String? = null,
  placeholder: String = "",
  supportingHint: String? = null,
  unsetValue: Int = UNSET_COORD,
  allowNegative: Boolean = false,
) {
  val numberVars = remember(variables) { variables.filter { it.type == VariableType.NUMBER } }
  val isVar = value is NumericInput.VariableRef
  val showVarToggle = numberVars.isNotEmpty() || isVar

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    when (value) {
      is NumericInput.Literal -> {
        var text by remember(value.value) {
          mutableStateOf(if (value.value == unsetValue) "" else value.value.toString())
        }
        val activeSupport = errorText ?: supportingHint
        OutlinedTextField(
          value = text,
          onValueChange = { raw ->
            val clean =
              raw.filterIndexed { idx, c -> c.isDigit() || (allowNegative && idx == 0 && c == '-') }
            text = clean
            if (clean.isEmpty()) {
              onValueChange(NumericInput.Literal(unsetValue))
            } else {
              clean.toIntOrNull()?.let { onValueChange(NumericInput.Literal(it)) }
            }
          },
          label = { Text(label) },
          placeholder =
            if (placeholder.isNotBlank()) {
              { Text(placeholder) }
            } else {
              null
            },
          isError = errorText != null,
          supportingText =
            if (activeSupport != null) {
              { Text(activeSupport) }
            } else {
              null
            },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      is NumericInput.VariableRef -> {
        OutlinedTextField(
          value = value.variableKey,
          onValueChange = { onValueChange(NumericInput.VariableRef(it)) },
          label = { Text("$label (Variable)") },
          isError = errorText != null,
          supportingText =
            if (errorText != null) {
              { Text(errorText) }
            } else {
              null
            },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (numberVars.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            numberVars.forEach { v ->
              AssistChip(
                onClick = { onValueChange(NumericInput.VariableRef(v.key)) },
                label = { Text(v.key) },
              )
            }
          }
        }
      }
    }

    if (showVarToggle) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AssistChip(
          onClick = {
            if (isVar) {
              onValueChange(NumericInput.Literal(unsetValue))
            } else if (!isPro) {
              onProRequested(ProFeatureGate.DYNAMIC_VARIABLES.displayName)
            } else {
              val defaultKey = numberVars.firstOrNull()?.key.orEmpty()
              onValueChange(NumericInput.VariableRef(defaultKey))
            }
          },
          leadingIcon =
            if (!isVar && !isPro) {
              {
                Icon(
                  imageVector = Icons.Rounded.Lock,
                  contentDescription = "Pro feature",
                  modifier = Modifier.size(14.dp),
                )
              }
            } else {
              null
            },
          label = {
            Text(
              text = if (isVar) "Switch to Number" else "Use variable",
              style = MaterialTheme.typography.labelSmall,
            )
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationInputField(
  label: String,
  value: DurationInput,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onValueChange: (DurationInput) -> Unit,
  onProRequested: (String) -> Unit,
  modifier: Modifier = Modifier,
  suggestedHint: String? = null,
) {
  val numberVars = remember(variables) { variables.filter { it.type == VariableType.NUMBER } }
  val includeVarTab = numberVars.isNotEmpty() || value is DurationInput.VariableRef
  val selectedIdx =
    when (value) {
      is DurationInput.Fixed -> 0
      is DurationInput.RandomRange -> 1
      is DurationInput.VariableRef -> 2
    }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val options =
      if (includeVarTab) {
        listOf("Number", "Random Range", "Variable")
      } else {
        listOf("Number", "Random Range")
      }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      options.forEachIndexed { index, text ->
        val locked = index > 0 && !isPro
        SegmentedButton(
          selected = selectedIdx == index,
          onClick = {
            when (index) {
              0 -> onValueChange(DurationInput.Fixed(50L))
              1 ->
                if (!isPro) {
                  onProRequested(ProFeatureGate.RANDOM_DEVIATION_DELAY.displayName)
                } else {
                  onValueChange(DurationInput.RandomRange(40L, 120L))
                }
              2 ->
                if (!isPro) {
                  onProRequested(ProFeatureGate.DYNAMIC_VARIABLES.displayName)
                } else {
                  onValueChange(DurationInput.VariableRef(numberVars.firstOrNull()?.key.orEmpty()))
                }
            }
          },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
          icon = {
            if (locked) {
              Icon(Icons.Rounded.Lock, contentDescription = "Pro feature", modifier = Modifier.size(14.dp))
            } else {
              SegmentedButtonDefaults.Icon(active = selectedIdx == index)
            }
          },
        ) {
          Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }

    when (value) {
      is DurationInput.Fixed -> {
        var text by remember(value.ms) { mutableStateOf(value.ms.toString()) }
        OutlinedTextField(
          value = text,
          onValueChange = { raw ->
            text = raw.filter { it.isDigit() }
            text.toLongOrNull()?.let { onValueChange(DurationInput.Fixed(it.coerceAtLeast(10L))) }
          },
          label = { Text("$label (ms)") },
          supportingText =
            if (suggestedHint != null) {
              { Text(suggestedHint) }
            } else {
              null
            },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      is DurationInput.RandomRange -> {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          var minText by remember(value.minMs) { mutableStateOf(value.minMs.toString()) }
          var maxText by remember(value.maxMs) { mutableStateOf(value.maxMs.toString()) }
          OutlinedTextField(
            value = minText,
            onValueChange = { raw ->
              minText = raw.filter { it.isDigit() }
              minText.toLongOrNull()?.let { min ->
                onValueChange(DurationInput.RandomRange(min, value.maxMs.coerceAtLeast(min)))
              }
            },
            label = { Text("Min (ms)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = maxText,
            onValueChange = { raw ->
              maxText = raw.filter { it.isDigit() }
              maxText.toLongOrNull()?.let { max ->
                onValueChange(DurationInput.RandomRange(value.minMs, max.coerceAtLeast(value.minMs)))
              }
            },
            label = { Text("Max (ms)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }
      }
      is DurationInput.VariableRef -> {
        OutlinedTextField(
          value = value.variableKey,
          onValueChange = { onValueChange(DurationInput.VariableRef(it)) },
          label = { Text("$label (Variable)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (numberVars.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            numberVars.forEach { v ->
              AssistChip(
                onClick = { onValueChange(DurationInput.VariableRef(v.key)) },
                label = { Text(v.key) },
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerBottomSheet(
  apps: List<InstalledAppEntry>,
  isLoading: Boolean,
  currentForegroundPackage: String?,
  onSelect: (packageName: String, label: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered =
    remember(apps, query) {
      val q = query.trim()
      if (q.isEmpty()) {
        apps
      } else {
        apps.filter {
          it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
      }
    }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Select Application",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      if (!currentForegroundPackage.isNullOrBlank()) {
        val currentApp = apps.firstOrNull { it.packageName == currentForegroundPackage }
        FilledTonalButton(
          onClick = {
            onSelect(
              currentForegroundPackage,
              currentApp?.label ?: currentForegroundPackage,
            )
          },
          modifier = Modifier.fillMaxWidth().testTag("select_current_foreground_app_button"),
        ) {
          Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.size(6.dp))
          Text(
            text = "Current Foreground: ${currentApp?.label ?: currentForegroundPackage}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon =
          if (query.isNotEmpty()) {
            {
              IconButton(onClick = { query = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
              }
            }
          } else {
            null
          },
        placeholder = { Text("Search app name or package...") },
        shape = RoundedCornerShape(999.dp),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("app_picker_search"),
      )

      if (isLoading) {
        Box(
          modifier = Modifier.fillMaxWidth().height(200.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(filtered, key = { it.packageName }) { app ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onSelect(app.packageName, app.label) },
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                AsyncImage(
                  model = app.iconDrawable,
                  contentDescription = app.label,
                  modifier = Modifier.size(36.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectTargetPickerBottomSheet(
  inspectGroups: List<InspectGroupWithItems>,
  isCropMode: Boolean,
  onSelectPixel: (InspectItem, InspectPixel) -> Unit,
  onSelectCrop: (InspectItem, InspectCrop) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = if (isCropMode) "Import Inspect Crop" else "Import Inspect Color Pixel",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          if (isCropMode) {
            "Exports a lossless template PNG into this Script's dedicated asset storage."
          } else {
            "Auto-fills exact screen coordinates and HEX color."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      val flatItems =
        remember(inspectGroups, isCropMode) {
          inspectGroups.flatMap { g ->
            g.items.filter { item ->
              if (isCropMode) item.crops.isNotEmpty() else item.pixels.isNotEmpty()
            }.map { item -> g.group.title to item }
          }
        }

      if (flatItems.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text =
              if (isCropMode) {
                "No Inspect Crops saved yet. Open Inspect Studio or run Live Inspect first."
              } else {
                "No Inspect Pixels saved yet. Pick pixels in Inspect Studio or Live Inspect first."
              },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(flatItems, key = { it.second.id }) { (groupTitle, item) ->
            val displayAppName = item.appName.ifBlank { "Screen Capture" }
            val isPreview = LocalInspectionMode.current
            val imageExists by
              produceState<Boolean?>(
                initialValue = if (isPreview) item.fullScreenPngPath.isNotBlank() else null,
                item.fullScreenPngPath,
                isPreview,
              ) {
                if (isPreview) {
                  value = item.fullScreenPngPath.isNotBlank()
                  return@produceState
                }
                value =
                  withContext(Dispatchers.IO) {
                    val path = item.fullScreenPngPath
                    path.isNotBlank() && File(path).let { it.exists() && it.isFile }
                  }
              }

            EditorSectionCard(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerHighest,
                  border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier.size(64.dp),
                ) {
                  Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                  ) {
                    when (imageExists) {
                      true -> {
                        AsyncImage(
                          model = File(item.fullScreenPngPath),
                          contentDescription = "Screenshot for $displayAppName",
                          contentScale = ContentScale.Fit,
                          modifier = Modifier.fillMaxSize(),
                        )
                      }
                      false -> {
                        Column(
                          modifier = Modifier.padding(4.dp),
                          horizontalAlignment = Alignment.CenterHorizontally,
                          verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                          Icon(
                            imageVector = Icons.Rounded.ImageNotSupported,
                            contentDescription = "Image Deleted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                          )
                          Text(
                            text = "Image Deleted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                          )
                        }
                      }
                      null -> Unit
                    }
                  }
                }

                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    PickerAppIcon(
                      packageName = item.packageName,
                      contentDescription = null,
                    )
                    Text(
                      text = "$groupTitle • $displayAppName",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                  }
                  PickerOrientationBadge(item = item)
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              if (isCropMode) {
                item.crops.forEach { crop ->
                  val cropHasFile by
                    produceState(
                      initialValue = isPreview && item.fullScreenPngPath.isNotBlank(),
                      item.fullScreenPngPath,
                      crop.cropPngPath,
                      isPreview,
                    ) {
                      if (isPreview) return@produceState
                      value =
                        withContext(Dispatchers.IO) {
                          val legacyOk =
                            crop.cropPngPath?.takeIf { it.isNotBlank() }?.let {
                              File(it).let { f -> f.exists() && f.isFile }
                            } == true
                          val screenOk =
                            item.fullScreenPngPath.isNotBlank() &&
                              File(item.fullScreenPngPath).let { f -> f.exists() && f.isFile }
                          legacyOk || screenOk
                        }
                    }

                  Row(
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = cropHasFile) { onSelectCrop(item, crop) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    PickerCropRegionThumbnail(
                      fullScreenPngPath = item.fullScreenPngPath,
                      crop = crop,
                      modifier = Modifier.size(44.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = crop.label.ifBlank { "Crop Template" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color =
                          if (cropHasFile) {
                            MaterialTheme.colorScheme.onSurface
                          } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                          },
                      )
                      Text(
                        text = "${crop.width} × ${crop.height} px • (${crop.x}, ${crop.y})",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      if (!cropHasFile) {
                        Text(
                          text = "Image Deleted",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.error,
                        )
                      }
                    }
                  }
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
              } else {
                item.pixels.forEach { pixel ->
                  Row(
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectPixel(item, pixel) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier
                          .size(22.dp)
                          .clip(CircleShape)
                          .background(parseEditorHexColor(pixel.hexColor))
                          .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = pixel.label.ifBlank { "Pixel" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                      )
                      Text(
                        text = "${pixel.hexColor} @ (${pixel.x}, ${pixel.y})",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

private object PickerAppIconCache {
  private val cache = LruCache<String, ImageBitmap>(48)

  suspend fun load(context: Context, packageName: String): ImageBitmap? {
    if (packageName.isBlank()) return null
    cache.get(packageName)?.let { return it }
    return withContext(Dispatchers.IO) {
      runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawable.toBitmap(width = 64, height = 64, config = Bitmap.Config.ARGB_8888)
          .asImageBitmap()
          .also { cache.put(packageName, it) }
      }.getOrNull()
    }
  }
}

@Composable
private fun PickerAppIcon(
  packageName: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isPreview = LocalInspectionMode.current
  val iconBitmap by
    produceState<ImageBitmap?>(initialValue = null, packageName, isPreview) {
      value =
        if (isPreview || packageName.isBlank()) {
          null
        } else {
          PickerAppIconCache.load(context, packageName)
        }
    }

  val loaded = iconBitmap
  if (loaded != null) {
    Image(
      bitmap = loaded,
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
    )
  } else {
    Surface(
      modifier = modifier.size(24.dp),
      shape = RoundedCornerShape(6.dp),
      color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Rounded.Apps,
          contentDescription = contentDescription,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(15.dp),
        )
      }
    }
  }
}

@Composable
private fun PickerOrientationBadge(
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
      ScreenOrientation.SQUARE_UNFOLDED -> stringResource(DbR.string.orientation_unfolded)
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
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
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
        text = "$label • ${item.screenWidth} × ${item.screenHeight}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private data class PickerCropThumbState(
  val bitmap: ImageBitmap? = null,
  val isImageDeleted: Boolean = false,
)

@Composable
private fun PickerCropRegionThumbnail(
  fullScreenPngPath: String,
  crop: InspectCrop,
  modifier: Modifier = Modifier,
) {
  val isPreview = LocalInspectionMode.current
  val thumbState by
    produceState(
      initialValue = PickerCropThumbState(isImageDeleted = isPreview && fullScreenPngPath.isBlank()),
      fullScreenPngPath,
      crop.x,
      crop.y,
      crop.width,
      crop.height,
      crop.cropPngPath,
      isPreview,
    ) {
      if (isPreview) return@produceState
      value =
        withContext(Dispatchers.IO) {
          runCatching {
            val legacyPath = crop.cropPngPath?.takeIf { it.isNotBlank() }
            if (legacyPath != null) {
              val legacyFile = File(legacyPath)
              if (legacyFile.exists() && legacyFile.isFile) {
                val bmp = BitmapFactory.decodeFile(legacyFile.absolutePath)?.asImageBitmap()
                return@runCatching PickerCropThumbState(bitmap = bmp, isImageDeleted = bmp == null)
              }
            }
            if (fullScreenPngPath.isBlank()) {
              return@runCatching PickerCropThumbState(isImageDeleted = true)
            }
            val screenFile = File(fullScreenPngPath)
            if (!screenFile.exists() || !screenFile.isFile) {
              return@runCatching PickerCropThumbState(isImageDeleted = true)
            }
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(screenFile.absolutePath, boundsOpts)
            val imgW = boundsOpts.outWidth
            val imgH = boundsOpts.outHeight
            if (imgW <= 0 || imgH <= 0) {
              return@runCatching PickerCropThumbState(isImageDeleted = true)
            }
            val safeX = crop.x.coerceIn(0, imgW - 1)
            val safeY = crop.y.coerceIn(0, imgH - 1)
            val safeW = crop.width.coerceIn(1, imgW - safeX)
            val safeH = crop.height.coerceIn(1, imgH - safeY)
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(screenFile.absolutePath, false)
            try {
              val bmp =
                decoder.decodeRegion(
                  AndroidRect(safeX, safeY, safeX + safeW, safeY + safeH),
                  BitmapFactory.Options(),
                )?.asImageBitmap()
              PickerCropThumbState(bitmap = bmp, isImageDeleted = bmp == null)
            } finally {
              decoder.recycle()
            }
          }.getOrElse { PickerCropThumbState(isImageDeleted = true) }
        }
    }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier,
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      val bmp = thumbState.bitmap
      when {
        bmp != null -> {
          Image(
            bitmap = bmp,
            contentDescription = crop.label.ifBlank { "Crop region" },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(2.dp),
          )
        }
        thumbState.isImageDeleted -> {
          Icon(
            imageVector = Icons.Rounded.ImageNotSupported,
            contentDescription = "Image Deleted",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
        else -> {
          Icon(
            imageVector = Icons.Rounded.Crop,
            contentDescription = crop.label.ifBlank { "Crop region" },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

internal fun parseEditorHexColorOrNull(hex: String): Color? {
  val clean = hex.removePrefix("#").trim()
  if (clean.length != 6 && clean.length != 8) return null
  val parsed = clean.toLongOrNull(16) ?: return null
  return when (clean.length) {
    6 -> Color((0xFF000000L or parsed).toInt())
    8 -> Color(parsed.toInt())
    else -> null
  }
}

internal fun parseEditorHexColor(hex: String): Color =
  parseEditorHexColorOrNull(hex) ?: Color.Gray
