package com.tapflow.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tapflow.core.database.repository.InspectGroupWithItems
import com.tapflow.core.model.common.DurationInput
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import java.io.File

@Composable
fun NumericInputField(
  label: String,
  value: NumericInput,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onValueChange: (NumericInput) -> Unit,
  onProRequested: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val numberVars = remember(variables) { variables.filter { it.type == VariableType.NUMBER } }
  val isVar = value is NumericInput.VariableRef

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      FilterChip(
        selected = !isVar,
        onClick = {
          if (isVar) onValueChange(NumericInput.Literal(0))
        },
        label = { Text("Fixed") },
      )
      FilterChip(
        selected = isVar,
        onClick = {
          if (!isPro) {
            onProRequested(ProFeatureGate.DYNAMIC_VARIABLES.displayName)
          } else {
            val defaultKey = numberVars.firstOrNull()?.key ?: "var"
            onValueChange(NumericInput.VariableRef(defaultKey))
          }
        },
        leadingIcon =
          if (!isPro) {
            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
          } else {
            null
          },
        label = { Text("Variable") },
      )
    }

    when (value) {
      is NumericInput.Literal -> {
        var text by remember(value.value) { mutableStateOf(value.value.toString()) }
        OutlinedTextField(
          value = text,
          onValueChange = { raw ->
            val clean = raw.filterIndexed { idx, c -> c.isDigit() || (idx == 0 && c == '-') }
            text = clean
            clean.toIntOrNull()?.let { onValueChange(NumericInput.Literal(it)) }
          },
          label = { Text(label) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      is NumericInput.VariableRef -> {
        OutlinedTextField(
          value = value.variableKey,
          onValueChange = { onValueChange(NumericInput.VariableRef(it)) },
          label = { Text("$label (Number Var Key)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (numberVars.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            numberVars.forEach { v ->
              AssistChip(
                onClick = { onValueChange(NumericInput.VariableRef(v.key)) },
                label = { Text("{${v.key}}") },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun DurationInputField(
  label: String,
  value: DurationInput,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onValueChange: (DurationInput) -> Unit,
  onProRequested: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val numberVars = remember(variables) { variables.filter { it.type == VariableType.NUMBER } }
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(
        selected = value is DurationInput.Fixed,
        onClick = { onValueChange(DurationInput.Fixed(50L)) },
        label = { Text("Fixed") },
      )
      FilterChip(
        selected = value is DurationInput.RandomRange,
        onClick = {
          if (!isPro) {
            onProRequested(ProFeatureGate.RANDOM_DEVIATION_DELAY.displayName)
          } else {
            onValueChange(DurationInput.RandomRange(40L, 120L))
          }
        },
        leadingIcon =
          if (!isPro) {
            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
          } else {
            null
          },
        label = { Text("Random") },
      )
      FilterChip(
        selected = value is DurationInput.VariableRef,
        onClick = {
          if (!isPro) {
            onProRequested(ProFeatureGate.DYNAMIC_VARIABLES.displayName)
          } else {
            onValueChange(DurationInput.VariableRef(numberVars.firstOrNull()?.key ?: "dur"))
          }
        },
        leadingIcon =
          if (!isPro) {
            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
          } else {
            null
          },
        label = { Text("Var") },
      )
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
            label = { Text("Min ms") },
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
            label = { Text("Max ms") },
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
          label = { Text("$label (Number Variable Key)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
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
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Select Application",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      if (!currentForegroundPackage.isNullOrBlank()) {
        val currentApp = apps.firstOrNull { it.packageName == currentForegroundPackage }
        AssistChip(
          onClick = {
            onSelect(
              currentForegroundPackage,
              currentApp?.label ?: currentForegroundPackage,
            )
          },
          leadingIcon = { Icon(Icons.Filled.Apps, contentDescription = null) },
          label = {
            Text(
              "Select Current Foreground App (${currentApp?.label ?: currentForegroundPackage})",
            )
          },
          modifier = Modifier.testTag("select_current_foreground_app_button"),
        )
      }

      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Search app name or package...") },
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
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(filtered, key = { it.packageName }) { app ->
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onSelect(app.packageName, app.label) }
                  .padding(horizontal = 8.dp, vertical = 10.dp),
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
                )
                Text(
                  text = app.packageName,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
          }
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectTargetPickerBottomSheet(
  inspectGroups: List<InspectGroupWithItems>,
  isCropMode: Boolean,
  onSelectPixel: (InspectItem, InspectPixel) -> Unit,
  onSelectCrop: (InspectCrop) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = if (isCropMode) "Import Inspect Crop (Lossless PNG)" else "Import Inspect Color Pixel",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          if (isCropMode) {
            "Copies the template PNG into this Script's dedicated asset storage."
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
        Text(
          text =
            if (isCropMode) {
              "No Inspect Crops saved yet. Open Inspect Studio or run Live Inspect first."
            } else {
              "No Inspect Pixels saved yet. Pick pixels in Inspect Studio or Live Inspect first."
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 24.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(flatItems, key = { it.second.id }) { (groupTitle, item) ->
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "$groupTitle • ${item.appName}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                )
                if (isCropMode) {
                  item.crops.forEach { crop ->
                    Row(
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .clickable { onSelectCrop(crop) }
                          .padding(vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      AsyncImage(
                        model = File(crop.cropPngPath),
                        contentDescription = crop.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(44.dp),
                      )
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = crop.label.ifBlank { "Crop Template" },
                          style = MaterialTheme.typography.bodyMedium,
                          fontWeight = FontWeight.Medium,
                        )
                        Text(
                          text = "${crop.width} × ${crop.height} px • (${crop.x}, ${crop.y})",
                          style = MaterialTheme.typography.labelSmall,
                          fontFamily = FontFamily.Monospace,
                        )
                      }
                    }
                  }
                } else {
                  item.pixels.forEach { pixel ->
                    Row(
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .clickable { onSelectPixel(item, pixel) }
                          .padding(vertical = 6.dp),
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
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
    }
  }
}

internal fun parseEditorHexColor(hex: String): Color {
  val clean = hex.removePrefix("#").trim()
  val parsed = clean.toLongOrNull(16) ?: return Color.Gray
  return when (clean.length) {
    6 -> Color((0xFF000000L or parsed).toInt())
    8 -> Color(parsed.toInt())
    else -> Color.Gray
  }
}
