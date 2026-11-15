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
  EditorSectionCard(title = "Phone Button") {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      SystemKeyType.entries.forEach { keyType ->
        FilterChip(
          selected = action.key == keyType,
          onClick = { onChange(action.copy(key = keyType)) },
          label = { Text(PlainLanguageFormatter.systemKeyLabel(keyType)) },
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
  validationErrors: Map<String, String> = emptyMap(),
  onOpenPicker: () -> Unit,
) {
  val appError = validationErrors["app_package"]
  EditorSectionCard(
    title = if (isForceStop) "Close & Restart App (Force Stop)" else "Open App",
    subtitle =
      if (isForceStop) {
        "Stops the app completely (requires Shizuku permission)"
      } else {
        "Opens the selected app on your phone"
      },
  ) {
    Text(
      text =
        if (packageName.isBlank()) {
          appError ?: "No app selected yet"
        } else {
          "${appLabel.ifBlank { packageName }} ($packageName)"
        },
      style = MaterialTheme.typography.bodyMedium,
      color =
        if (packageName.isBlank() && appError != null) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurface
        },
    )
    FilledTonalButton(
      onClick = onOpenPicker,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_app_picker_button"),
    ) {
      Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text(if (packageName.isBlank()) "Choose App" else "Change App")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardActionEditor(
  action: ScriptAction.ClipboardOp,
  variables: List<VariableDefinition>,
  validationErrors: Map<String, String> = emptyMap(),
  onChange: (ScriptAction.ClipboardOp) -> Unit,
) {
  val textError = validationErrors["clipboard_text"]
  EditorSectionCard(title = "Clipboard Action") {
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
          label = { Text("Text to Copy") },
          placeholder = { Text("Enter text to copy to clipboard...") },
          isError = textError != null,
          supportingText =
            if (textError != null) {
              { Text(textError) }
            } else {
              null
            },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = action.targetVariableKey.orEmpty(),
          onValueChange = { onChange(action.copy(targetVariableKey = it.ifBlank { null })) },
          label = { Text("Also Save Copy Into Variable (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      ClipboardActionType.PASTE -> {
        OutlinedTextField(
          value = action.valueTemplate,
          onValueChange = { onChange(action.copy(valueTemplate = it)) },
          label = { Text("Custom Text to Paste Instead (Optional)") },
          placeholder = { Text("Leave blank to paste whatever is on clipboard") },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      ClipboardActionType.CLEAR -> {
        Text(
          text = "Erases whatever text is currently copied on your phone.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (variables.isNotEmpty() && action.operation != ClipboardActionType.CLEAR) {
      Text(
        text = "Tap a variable to insert it:",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
            label = { Text(v.key) },
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
  EditorSectionCard(title = "Vibration Style") {
    val patterns = VibratePattern.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      patterns.forEachIndexed { index, pattern ->
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
          Text(
            text = PlainLanguageFormatter.vibratePatternLabel(pattern),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
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
            label = "Pulse #${idx + 1} (ms)",
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
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove vibration pulse")
          }
        }
      }
      OutlinedButton(
        onClick = { onChange(action.copy(customPatternMs = action.customPatternMs + 100L)) },
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add Vibration Pulse")
      }
    }
  }
}

@Composable
fun PlayAlertSoundActionEditor(
  action: ScriptAction.PlayAlertSound,
  onChange: (ScriptAction.PlayAlertSound) -> Unit,
) {
  EditorSectionCard(
    title = "Alert Sound",
    subtitle = "Choose a phone sound or paste a custom sound link",
  ) {
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
      label = { Text("Sound Name or Link") },
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
  validationErrors: Map<String, String> = emptyMap(),
  onChange: (ScriptAction.VariableOp) -> Unit,
) {
  val targetKeyError = validationErrors["var_target_key"]
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (variables.isNotEmpty()) {
      EditorSectionCard(title = "Tap a Saved Variable to Fill") {
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
            label = { Text("Save Result In (Variable Name)") },
            isError = targetKeyError != null,
            supportingText = targetKeyError?.let { err -> { Text(err) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          val valueErr = validationErrors["var_value"]
          OutlinedTextField(
            value = action.valueTemplate,
            onValueChange = { onChange(action.copy(valueTemplate = it)) },
            label = { Text("Value to Store") },
            placeholder = { Text("Enter text, number, or {variable}") },
            isError = valueErr != null,
            supportingText = valueErr?.let { err -> { Text(err) } },
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
            label = { Text("Save Result In (Variable Name)") },
            isError = targetKeyError != null,
            supportingText = targetKeyError?.let { err -> { Text(err) } },
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
                label = { Text(PlainLanguageFormatter.mathOpLabel(op)) },
              )
            }
          }
          val leftErr = validationErrors["var_left"]
          OutlinedTextField(
            value = action.leftOperand,
            onValueChange = { onChange(action.copy(leftOperand = it)) },
            label = { Text("First Number or {variable}") },
            isError = leftErr != null,
            supportingText = leftErr?.let { err -> { Text(err) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (!isUnary) {
            val rightErr = validationErrors["var_right"]
            OutlinedTextField(
              value = action.rightOperand,
              onValueChange = { onChange(action.copy(rightOperand = it)) },
              label = { Text("Second Number or {variable}") },
              isError = rightErr != null,
              supportingText = rightErr?.let { err -> { Text(err) } },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        is ScriptAction.RandomOp -> {
          OutlinedTextField(
            value = action.targetKey,
            onValueChange = { onChange(action.copy(targetKey = it)) },
            label = { Text("Save Result In (Variable Name)") },
            isError = targetKeyError != null,
            supportingText = targetKeyError?.let { err -> { Text(err) } },
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
                Text(
                  text = PlainLanguageFormatter.randomTargetLabel(type),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
          if (action.targetType != RandomTargetType.BOOLEAN) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              DoubleField(
                label = "Lowest",
                value = action.minValue,
                onChange = { onChange(action.copy(minValue = it)) },
                modifier = Modifier.weight(1f),
              )
              DoubleField(
                label = "Highest",
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
            label = { Text("Save Result In (Variable Name)") },
            isError = targetKeyError != null,
            supportingText = targetKeyError?.let { err -> { Text(err) } },
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
                label = { Text(PlainLanguageFormatter.stringOpLabel(op)) },
              )
            }
          }
          val inputAErr = validationErrors["var_input_a"]
          OutlinedTextField(
            value = action.inputA,
            onValueChange = { onChange(action.copy(inputA = it)) },
            label = { Text("Text or {variable}") },
            isError = inputAErr != null,
            supportingText = inputAErr?.let { err -> { Text(err) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (action.operator == StringOperator.CONCATENATE ||
            action.operator == StringOperator.REPLACE ||
            action.operator == StringOperator.REPLACE_REGEX
          ) {
            val inputBErr = validationErrors["var_input_b"]
            OutlinedTextField(
              value = action.inputB,
              onValueChange = { onChange(action.copy(inputB = it)) },
              label = {
                Text(
                  if (action.operator == StringOperator.CONCATENATE) {
                    "Text to Join Afterward"
                  } else {
                    "Find|Replace (e.g. old|new)"
                  },
                )
              },
              isError = inputBErr != null,
              supportingText = inputBErr?.let { err -> { Text(err) } },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          if (action.operator == StringOperator.SUBSTRING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IntTextField(
                label = "Start Position",
                value = action.startIndex,
                min = 0,
                onChange = { onChange(action.copy(startIndex = it)) },
                modifier = Modifier.weight(1f),
              )
              IntTextField(
                label = "End Position",
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
            label = { Text("Save Result In (Yes/No Variable)") },
            isError = targetKeyError != null,
            supportingText = targetKeyError?.let { err -> { Text(err) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BooleanOperator.entries.forEach { op ->
              FilterChip(
                selected = action.operator == op,
                onClick = { onChange(action.copy(operator = op)) },
                label = { Text(PlainLanguageFormatter.booleanOpLabel(op)) },
              )
            }
          }
          val leftKeyErr = validationErrors["var_left_key"]
          OutlinedTextField(
            value = action.leftKey,
            onValueChange = { onChange(action.copy(leftKey = it)) },
            label = { Text("Yes/No Variable Name") },
            isError = leftKeyErr != null,
            supportingText = leftKeyErr?.let { err -> { Text(err) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (action.operator != BooleanOperator.TOGGLE) {
            val rightKeyErr = validationErrors["var_right_key"]
            OutlinedTextField(
              value = action.rightKey.orEmpty(),
              onValueChange = { onChange(action.copy(rightKey = it.ifBlank { null })) },
              label = { Text("Second Yes/No Variable Name") },
              isError = rightKeyErr != null,
              supportingText = rightKeyErr?.let { err -> { Text(err) } },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        is ScriptAction.TypeCastOp -> {
          OutlinedTextField(
            value = action.targetKey,
            onValueChange = { onChange(action.copy(targetKey = it)) },
            label = { Text("Save Result In (Variable Name)") },
            isError = targetKeyError != null,
            supportingText = targetKeyError?.let { err -> { Text(err) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          val sourceErr = validationErrors["var_source_key"]
          OutlinedTextField(
            value = action.sourceKey,
            onValueChange = { onChange(action.copy(sourceKey = it)) },
            label = { Text("Variable to Convert") },
            isError = sourceErr != null,
            supportingText = sourceErr?.let { err -> { Text(err) } },
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
                label = { Text(PlainLanguageFormatter.typeCastLabel(op)) },
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
