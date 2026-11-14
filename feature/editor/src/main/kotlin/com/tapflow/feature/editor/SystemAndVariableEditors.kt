package com.tapflow.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun KeyEventActionEditor(
  action: ScriptAction.KeyEvent,
  onChange: (ScriptAction.KeyEvent) -> Unit,
) {
  EditorSectionCard(title = "System Key Action") {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
  EditorSectionCard(
    title = if (isForceStop) "Rootless Force Stop" else "Launch Application",
    subtitle = if (isForceStop) "Uses Shizuku `am force-stop`" else "Launches target app launcher intent",
  ) {
    Text(
      text =
        if (packageName.isBlank()) {
          "No target app selected"
        } else {
          "${appLabel.ifBlank { packageName }} ($packageName)"
        },
      style = MaterialTheme.typography.bodyMedium,
    )
    FilledTonalButton(
      onClick = onOpenPicker,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_app_picker_button"),
    ) {
      Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Choose Installed App")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardActionEditor(
  action: ScriptAction.ClipboardOp,
  variables: List<VariableDefinition>,
  onChange: (ScriptAction.ClipboardOp) -> Unit,
) {
  EditorSectionCard(title = "Clipboard Operation") {
    val ops = ClipboardActionType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      ops.forEachIndexed { index, type ->
        val label =
          when (type) {
            ClipboardActionType.COPY -> "Copy"
            ClipboardActionType.PASTE -> "Paste"
            ClipboardActionType.CLEAR -> "Clear"
          }
        SegmentedButton(
          selected = action.operation == type,
          onClick = { onChange(action.copy(operation = type)) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = ops.size),
        ) {
          Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrateActionEditor(
  action: ScriptAction.Vibrate,
  onChange: (ScriptAction.Vibrate) -> Unit,
) {
  EditorSectionCard(title = "Vibrate Pattern") {
    val patterns = VibratePattern.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      patterns.forEachIndexed { index, pattern ->
        val label =
          when (pattern) {
            VibratePattern.SHORT -> "Short"
            VibratePattern.LONG -> "Long"
            VibratePattern.CUSTOM -> "Custom"
          }
        SegmentedButton(
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
          shape = SegmentedButtonDefaults.itemShape(index = index, count = patterns.size),
        ) {
          Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove vibration segment")
          }
        }
      }
      OutlinedButton(
        onClick = { onChange(action.copy(customPatternMs = action.customPatternMs + 100L)) },
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add Vibration Pulse (ms)")
      }
    }
  }
}

@Composable
fun PlayAlertSoundActionEditor(
  action: ScriptAction.PlayAlertSound,
  onChange: (ScriptAction.PlayAlertSound) -> Unit,
) {
  EditorSectionCard(title = "Alert Sound Type") {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariableOpActionEditor(
  action: ScriptAction.VariableOp,
  variables: List<VariableDefinition>,
  onChange: (ScriptAction.VariableOp) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (variables.isNotEmpty()) {
      EditorSectionCard(title = "Quick Variable Keys") {
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
    }

    EditorSectionCard(title = ActionTreeUtils.actionTypeTitle(action)) {
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
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
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
          val types = RandomTargetType.entries
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
              SegmentedButton(
                selected = action.targetType == type,
                onClick = { onChange(action.copy(targetType = type)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
              ) {
                Text(type.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
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
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
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
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
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
