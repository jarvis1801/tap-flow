package com.tapflow.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.BooleanOperator
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.MathOperator
import com.tapflow.core.model.action.RandomTargetType
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.StringOperator
import com.tapflow.core.model.action.SystemKeyType
import com.tapflow.core.model.action.TypeCastOperator
import com.tapflow.core.model.action.VibratePattern
import com.tapflow.core.model.common.VariableDefinition

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyEventActionEditor(
  action: ScriptAction.KeyEvent,
  onChange: (ScriptAction.KeyEvent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("System Key Action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      SystemKeyType.entries.forEach { keyType ->
        FilterChip(
          selected = action.key == keyType,
          onClick = { onChange(action.copy(key = keyType)) },
          label = { Text(keyType.name.replace('_', ' ')) },
        )
      }
    }
  }
}

@Composable
fun AppLifecycleActionEditor(
  isForceStop: Boolean,
  packageName: String,
  appLabel: String,
  onOpenPicker: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = if (isForceStop) "Rootless Force Stop (via Shizuku `am force-stop`)" else "Launch Application",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text =
        if (packageName.isBlank()) {
          "No target app selected"
        } else {
          "${appLabel.ifBlank { packageName }} ($packageName)"
        },
      style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(
      onClick = onOpenPicker,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_app_picker_button"),
    ) {
      Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Choose Installed App")
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipboardActionEditor(
  action: ScriptAction.ClipboardOp,
  variables: List<VariableDefinition>,
  onChange: (ScriptAction.ClipboardOp) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Clipboard Operation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ClipboardActionType.entries.forEach { type ->
        FilterChip(
          selected = action.operation == type,
          onClick = { onChange(action.copy(operation = type)) },
          label = { Text(type.name) },
        )
      }
    }

    when (action.operation) {
      ClipboardActionType.COPY -> {
        OutlinedTextField(
          value = action.valueTemplate,
          onValueChange = { onChange(action.copy(valueTemplate = it)) },
          label = { Text("Text Template to Copy (supports {var})") },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = action.targetVariableKey.orEmpty(),
          onValueChange = { onChange(action.copy(targetVariableKey = it.ifBlank { null })) },
          label = { Text("Or Read Current Clipboard Into Variable Key (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      ClipboardActionType.PASTE -> {
        OutlinedTextField(
          value = action.valueTemplate,
          onValueChange = { onChange(action.copy(valueTemplate = it)) },
          label = { Text("Optional Override Text Template Before Paste") },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      ClipboardActionType.CLEAR -> {
        Text(
          text = "Clears the primary system clipboard.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (variables.isNotEmpty()) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        variables.forEach { v ->
          AssistChip(
            onClick = {
              onChange(
                action.copy(
                  valueTemplate = action.valueTemplate + "{${v.key}}",
                  targetVariableKey = action.targetVariableKey ?: v.key,
                ),
              )
            },
            label = { Text("{${v.key}}") },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VibrateActionEditor(
  action: ScriptAction.Vibrate,
  onChange: (ScriptAction.Vibrate) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Vibrate Pattern", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      VibratePattern.entries.forEach { pattern ->
        FilterChip(
          selected = action.pattern == pattern,
          onClick = {
            val nextCustom =
              if (pattern == VibratePattern.CUSTOM && action.customPatternMs.isEmpty()) {
                listOf(100L, 80L, 150L)
              } else {
                action.customPatternMs
              }
            onChange(action.copy(pattern = pattern, customPatternMs = nextCustom))
          },
          label = { Text(pattern.name) },
        )
      }
    }

    if (action.pattern == VibratePattern.CUSTOM) {
      action.customPatternMs.forEachIndexed { idx, dur ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          LongTextField(
            label = "Step #${idx + 1} (ms)",
            value = dur,
            min = 10L,
            onChange = { next ->
              onChange(
                action.copy(
                  customPatternMs =
                    action.customPatternMs.mapIndexed { i, v -> if (i == idx) next else v },
                ),
              )
            },
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = {
              onChange(
                action.copy(
                  customPatternMs = action.customPatternMs.filterIndexed { i, _ -> i != idx },
                ),
              )
            },
          ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove vibration segment")
          }
        }
      }
      OutlinedButton(
        onClick = { onChange(action.copy(customPatternMs = action.customPatternMs + 100L)) },
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add Vibration Pulse (ms)")
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayAlertSoundActionEditor(
  action: ScriptAction.PlayAlertSound,
  onChange: (ScriptAction.PlayAlertSound) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Alert Sound Type", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("default", "notification", "alarm", "ringtone").forEach { type ->
        FilterChip(
          selected = action.soundUriOrType == type,
          onClick = { onChange(action.copy(soundUriOrType = type)) },
          label = { Text(type.replaceFirstChar { it.uppercase() }) },
        )
      }
    }
    OutlinedTextField(
      value = action.soundUriOrType,
      onValueChange = { onChange(action.copy(soundUriOrType = it)) },
      label = { Text("Sound Type or Custom URI") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VariableOpActionEditor(
  action: ScriptAction.VariableOp,
  variables: List<VariableDefinition>,
  onChange: (ScriptAction.VariableOp) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (variables.isNotEmpty()) {
      Text("Quick Variable Keys", style = MaterialTheme.typography.labelMedium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        variables.forEach { v ->
          AssistChip(
            onClick = {
              onChange(
                when (action) {
                  is ScriptAction.SetVariable -> action.copy(targetKey = v.key)
                  is ScriptAction.MathOp -> action.copy(targetKey = v.key)
                  is ScriptAction.RandomOp -> action.copy(targetKey = v.key)
                  is ScriptAction.StringOp -> action.copy(targetKey = v.key)
                  is ScriptAction.BooleanOp -> action.copy(targetKey = v.key)
                  is ScriptAction.TypeCastOp -> action.copy(targetKey = v.key)
                },
              )
            },
            label = { Text("${v.key} (${v.type.displayLabel})") },
          )
        }
      }
    }

    when (action) {
      is ScriptAction.SetVariable -> {
        OutlinedTextField(
          value = action.targetKey,
          onValueChange = { onChange(action.copy(targetKey = it)) },
          label = { Text("Target Variable Key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = action.valueTemplate,
          onValueChange = { onChange(action.copy(valueTemplate = it)) },
          label = { Text("Value Template (supports {var}, {CLIPBOARD}, etc.)") },
          modifier = Modifier.fillMaxWidth(),
        )
      }

      is ScriptAction.MathOp -> {
        val isUnary =
          action.operator in
            setOf(MathOperator.ROUND, MathOperator.FLOOR, MathOperator.CEIL, MathOperator.ABS)
        OutlinedTextField(
          value = action.targetKey,
          onValueChange = { onChange(action.copy(targetKey = it)) },
          label = { Text("Result Variable Key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          MathOperator.entries.forEach { op ->
            FilterChip(
              selected = action.operator == op,
              onClick = { onChange(action.copy(operator = op)) },
              label = { Text(op.name) },
            )
          }
        }
        OutlinedTextField(
          value = action.leftOperand,
          onValueChange = { onChange(action.copy(leftOperand = it)) },
          label = { Text("Left Operand (number or {var})") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (!isUnary) {
          OutlinedTextField(
            value = action.rightOperand,
            onValueChange = { onChange(action.copy(rightOperand = it)) },
            label = { Text("Right Operand (number or {var})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      is ScriptAction.RandomOp -> {
        OutlinedTextField(
          value = action.targetKey,
          onValueChange = { onChange(action.copy(targetKey = it)) },
          label = { Text("Target Variable Key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          RandomTargetType.entries.forEach { type ->
            FilterChip(
              selected = action.targetType == type,
              onClick = { onChange(action.copy(targetType = type)) },
              label = { Text(type.name) },
            )
          }
        }
        if (action.targetType != RandomTargetType.BOOLEAN) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DoubleField(
              label = "Min",
              value = action.minValue,
              onChange = { onChange(action.copy(minValue = it)) },
              modifier = Modifier.weight(1f),
            )
            DoubleField(
              label = "Max",
              value = action.maxValue,
              onChange = { onChange(action.copy(maxValue = it)) },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      is ScriptAction.StringOp -> {
        OutlinedTextField(
          value = action.targetKey,
          onValueChange = { onChange(action.copy(targetKey = it)) },
          label = { Text("Target Variable Key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          StringOperator.entries.forEach { op ->
            FilterChip(
              selected = action.operator == op,
              onClick = { onChange(action.copy(operator = op)) },
              label = { Text(op.name) },
            )
          }
        }
        OutlinedTextField(
          value = action.inputA,
          onValueChange = { onChange(action.copy(inputA = it)) },
          label = { Text("Input A (text or {var})") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (action.operator == StringOperator.CONCATENATE ||
          action.operator == StringOperator.REPLACE ||
          action.operator == StringOperator.REPLACE_REGEX
        ) {
          OutlinedTextField(
            value = action.inputB,
            onValueChange = { onChange(action.copy(inputB = it)) },
            label = {
              Text(
                if (action.operator == StringOperator.CONCATENATE) {
                  "Append Input B"
                } else {
                  "Find Pattern|Replacement"
                },
              )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (action.operator == StringOperator.SUBSTRING) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntTextField(
              label = "Start Index",
              value = action.startIndex,
              min = 0,
              onChange = { onChange(action.copy(startIndex = it)) },
              modifier = Modifier.weight(1f),
            )
            IntTextField(
              label = "End Index",
              value = action.endIndex,
              min = 0,
              onChange = { onChange(action.copy(endIndex = it)) },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      is ScriptAction.BooleanOp -> {
        OutlinedTextField(
          value = action.targetKey,
          onValueChange = { onChange(action.copy(targetKey = it)) },
          label = { Text("Target Boolean Variable Key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          BooleanOperator.entries.forEach { op ->
            FilterChip(
              selected = action.operator == op,
              onClick = { onChange(action.copy(operator = op)) },
              label = { Text(op.name) },
            )
          }
        }
        OutlinedTextField(
          value = action.leftKey,
          onValueChange = { onChange(action.copy(leftKey = it)) },
          label = { Text("Left Variable Key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (action.operator != BooleanOperator.TOGGLE) {
          OutlinedTextField(
            value = action.rightKey.orEmpty(),
            onValueChange = { onChange(action.copy(rightKey = it.ifBlank { null })) },
            label = { Text("Right Variable Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      is ScriptAction.TypeCastOp -> {
        OutlinedTextField(
          value = action.targetKey,
          onValueChange = { onChange(action.copy(targetKey = it)) },
          label = { Text("Target Variable Key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = action.sourceKey,
          onValueChange = { onChange(action.copy(sourceKey = it)) },
          label = { Text("Source Variable Key") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          TypeCastOperator.entries.forEach { op ->
            FilterChip(
              selected = action.operator == op,
              onClick = { onChange(action.copy(operator = op)) },
              label = { Text(op.name.replace('_', ' ')) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DoubleField(
  label: String,
  value: Double,
  onChange: (Double) -> Unit,
  modifier: Modifier = Modifier,
) {
  FloatTextField(
    label = label,
    value = value.toFloat(),
    onChange = { onChange(it.toDouble()) },
    modifier = modifier,
  )
}
