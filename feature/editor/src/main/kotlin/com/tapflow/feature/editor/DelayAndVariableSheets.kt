package com.tapflow.feature.editor

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.entitlement.ProFeatureGate

private enum class DelayModeTab {
  FIXED,
  RANDOM_RANGE,
  DEVIATION,
}

@Composable
fun DelayConfigEditor(
  config: DelayConfig,
  isPro: Boolean,
  onConfigChange: (DelayConfig) -> Unit,
  onProRequested: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val mode =
    when (config) {
      is DelayConfig.Fixed -> DelayModeTab.FIXED
      is DelayConfig.RandomRange -> DelayModeTab.RANDOM_RANGE
      is DelayConfig.Deviation -> DelayModeTab.DEVIATION
    }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      FilterChip(
        selected = mode == DelayModeTab.FIXED,
        onClick = {
          val baseMs =
            when (config) {
              is DelayConfig.Fixed -> config.durationMs
              is DelayConfig.RandomRange -> config.minMs
              is DelayConfig.Deviation -> config.baseMs
            }
          onConfigChange(DelayConfig.fixed(baseMs))
        },
        label = { Text("Fixed") },
      )
      FilterChip(
        selected = mode == DelayModeTab.RANDOM_RANGE,
        onClick = {
          if (!isPro) {
            onProRequested(ProFeatureGate.RANDOM_DEVIATION_DELAY.displayName)
          } else {
            val min =
              when (config) {
                is DelayConfig.Fixed -> config.durationMs
                is DelayConfig.RandomRange -> config.minMs
                is DelayConfig.Deviation -> config.baseMs
              }
            onConfigChange(DelayConfig.randomRange(min, (min + 150L).coerceAtLeast(min)))
          }
        },
        leadingIcon =
          if (!isPro) {
            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
          } else {
            null
          },
        label = { Text("Random Range") },
      )
      FilterChip(
        selected = mode == DelayModeTab.DEVIATION,
        onClick = {
          if (!isPro) {
            onProRequested(ProFeatureGate.RANDOM_DEVIATION_DELAY.displayName)
          } else {
            val base =
              when (config) {
                is DelayConfig.Fixed -> config.durationMs
                is DelayConfig.RandomRange -> config.minMs
                is DelayConfig.Deviation -> config.baseMs
              }
            onConfigChange(DelayConfig.deviation(base, 50L))
          }
        },
        leadingIcon =
          if (!isPro) {
            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
          } else {
            null
          },
        label = { Text("Deviation (±)") },
      )
    }

    when (config) {
      is DelayConfig.Fixed -> {
        HmsMsDurationRow(
          label = "Duration (min 50ms)",
          totalMs = config.durationMs,
          onDurationChange = { ms -> onConfigChange(DelayConfig.fixed(ms)) },
        )
      }
      is DelayConfig.RandomRange -> {
        HmsMsDurationRow(
          label = "Min Delay (min 50ms)",
          totalMs = config.minMs,
          onDurationChange = { min ->
            onConfigChange(DelayConfig.randomRange(min, config.maxMs.coerceAtLeast(min)))
          },
        )
        HmsMsDurationRow(
          label = "Max Delay",
          totalMs = config.maxMs,
          onDurationChange = { max ->
            onConfigChange(DelayConfig.randomRange(config.minMs, max))
          },
        )
      }
      is DelayConfig.Deviation -> {
        HmsMsDurationRow(
          label = "Base Delay (min 50ms)",
          totalMs = config.baseMs,
          onDurationChange = { base ->
            onConfigChange(DelayConfig.deviation(base, config.deltaMs))
          },
        )
        HmsMsDurationRow(
          label = "Jitter ± Delta (ms)",
          totalMs = config.deltaMs,
          minClampMs = 0L,
          onDurationChange = { delta ->
            onConfigChange(DelayConfig.deviation(config.baseMs, delta))
          },
        )
      }
    }
  }
}

@Composable
fun HmsMsDurationRow(
  label: String,
  totalMs: Long,
  minClampMs: Long = DelayConfig.MIN_DELAY_MS,
  onDurationChange: (Long) -> Unit,
) {
  val safe = totalMs.coerceAtLeast(minClampMs)
  val hours = (safe / 3_600_000L).toInt()
  val mins = ((safe % 3_600_000L) / 60_000L).toInt()
  val secs = ((safe % 60_000L) / 1_000L).toInt()
  val ms = (safe % 1_000L).toInt()

  var hText by remember(hours, totalMs) { mutableStateOf(hours.toString()) }
  var mText by remember(mins, totalMs) { mutableStateOf(mins.toString()) }
  var sText by remember(secs, totalMs) { mutableStateOf(secs.toString()) }
  var msText by remember(ms, totalMs) { mutableStateOf(ms.toString()) }

  fun emitUpdated() {
    val hVal = (hText.toLongOrNull() ?: 0L).coerceIn(0L, 99L)
    val mVal = (mText.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
    val sVal = (sText.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
    val msVal = (msText.toLongOrNull() ?: 0L).coerceIn(0L, 999L)
    val computed = ((hVal * 3600L + mVal * 60L + sVal) * 1000L) + msVal
    onDurationChange(computed.coerceAtLeast(minClampMs))
  }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = "$label (${ActionTreeUtils.formatDurationCompact(safe)})",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      OutlinedTextField(
        value = hText,
        onValueChange = {
          hText = it.filter { ch -> ch.isDigit() }.take(2)
          emitUpdated()
        },
        label = { Text("Hr") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = mText,
        onValueChange = {
          mText = it.filter { ch -> ch.isDigit() }.take(2)
          emitUpdated()
        },
        label = { Text("Min") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = sText,
        onValueChange = {
          sText = it.filter { ch -> ch.isDigit() }.take(2)
          emitUpdated()
        },
        label = { Text("Sec") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = msText,
        onValueChange = {
          msText = it.filter { ch -> ch.isDigit() }.take(3)
          emitUpdated()
        },
        label = { Text("Ms") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1.2f),
      )
    }
  }
}

@Composable
fun PostActionDelaySection(
  postDelay: PostActionDelay,
  globalDelay: DelayConfig,
  isPro: Boolean,
  onPostDelayChange: (PostActionDelay) -> Unit,
  onProRequested: (String) -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Post-Action Delay",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChip(
          selected = postDelay is PostActionDelay.Global,
          onClick = { onPostDelayChange(PostActionDelay.Global) },
          label = { Text("Global (${ActionTreeUtils.formatDelayConfig(globalDelay)})") },
        )
        FilterChip(
          selected = postDelay is PostActionDelay.Override,
          onClick = {
            if (postDelay !is PostActionDelay.Override) {
              onPostDelayChange(PostActionDelay.Override(globalDelay.clamped()))
            }
          },
          label = { Text("Override") },
        )
        FilterChip(
          selected = postDelay is PostActionDelay.Off,
          onClick = { onPostDelayChange(PostActionDelay.Off) },
          label = { Text("Off (0ms)") },
        )
      }

      if (postDelay is PostActionDelay.Override) {
        DelayConfigEditor(
          config = postDelay.config,
          isPro = isPro,
          onConfigChange = { next -> onPostDelayChange(PostActionDelay.Override(next)) },
          onProRequested = onProRequested,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalDelayBottomSheet(
  initialConfig: DelayConfig,
  isPro: Boolean,
  onDismiss: () -> Unit,
  onSave: (DelayConfig) -> Unit,
  onProRequested: (String) -> Unit,
) {
  var draftConfig by remember(initialConfig) { mutableStateOf(initialConfig.clamped()) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Script Global Post-Action Delay",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          "Applied after every physical action set to Global delay. Minimum delay is strictly clamped to 50ms.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      DelayConfigEditor(
        config = draftConfig,
        isPro = isPro,
        onConfigChange = { draftConfig = it },
        onProRequested = onProRequested,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = { onSave(draftConfig.clamped()) },
          modifier = Modifier.testTag("save_global_delay_button"),
        ) {
          Text("Apply")
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicVariablesBottomSheet(
  variables: List<VariableDefinition>,
  onDismiss: () -> Unit,
  onUpsert: (VariableDefinition, originalKey: String?) -> Unit,
  onDelete: (String) -> Unit,
) {
  var keyInput by remember { mutableStateOf("") }
  var typeInput by remember { mutableStateOf(VariableType.STRING) }
  var defaultInput by remember { mutableStateOf("") }
  var editingKey by remember { mutableStateOf<String?>(null) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Dynamic Script Variables",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "In-memory session variables shared across loop cycles and pipeline steps. Interpolate with {key}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it.replace(" ", "_") },
            label = { Text("Variable Key (e.g. loop_count)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("variable_key_input"),
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VariableType.entries.forEach { t ->
              FilterChip(
                selected = typeInput == t,
                onClick = {
                  typeInput = t
                  if (t == VariableType.BOOLEAN && defaultInput != "true" && defaultInput != "false") {
                    defaultInput = "false"
                  }
                },
                label = { Text(t.displayLabel) },
              )
            }
          }
          OutlinedTextField(
            value = defaultInput,
            onValueChange = { defaultInput = it },
            label = { Text("Default Value") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("variable_default_input"),
          )
          Button(
            onClick = {
              if (keyInput.isNotBlank()) {
                onUpsert(
                  VariableDefinition(
                    key = keyInput.trim(),
                    type = typeInput,
                    defaultValue = defaultInput,
                  ),
                  editingKey,
                )
                keyInput = ""
                defaultInput = ""
                editingKey = null
              }
            },
            enabled = keyInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_variable_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (editingKey == null) "Add Variable" else "Update Variable")
          }
        }
      }

      if (variables.isNotEmpty()) {
        HorizontalDivider()
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(variables, key = { it.key }) { variable ->
            Card(
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "{${variable.key}} (${variable.type.displayLabel})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "Default: \"${variable.defaultValue}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                TextButton(
                  onClick = {
                    editingKey = variable.key
                    keyInput = variable.key
                    typeInput = variable.type
                    defaultInput = variable.defaultValue
                  },
                ) {
                  Text("Edit")
                }
                IconButton(
                  onClick = { onDelete(variable.key) },
                  modifier = Modifier.size(48.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete variable",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
