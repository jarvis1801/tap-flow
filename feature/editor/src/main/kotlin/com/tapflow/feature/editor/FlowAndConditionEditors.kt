package com.tapflow.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.LoopTimeoutAction
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.entitlement.ProFeatureGate
import java.util.UUID

@Composable
fun IfBlockActionEditor(
  action: ScriptAction.IfBlock,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.IfBlock) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Condition Name") {
      OutlinedTextField(
        value = action.name,
        onValueChange = { onChange(action.copy(name = it)) },
        label = { Text("Step Nickname (Optional)") },
        placeholder = { Text("e.g. Close Popup If Shown") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("if_block_name_input"),
      )
    }

    ConditionGroupCardEditor(
      root = action.conditionRoot,
      currentGroup = action.conditionRoot,
      depth = 1,
      isPro = isPro,
      variables = variables,
      onRootChange = { updatedRoot -> onChange(action.copy(conditionRoot = updatedRoot)) },
      onIntent = onIntent,
      validationErrors = validationErrors,
      previewPrefix = "If:",
    )
  }
}

@Composable
fun ElseIfConditionEditor(
  branch: ScriptAction.ElseIfBranch,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.ElseIfBranch) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Else If Branch Name") {
      OutlinedTextField(
        value = branch.name,
        onValueChange = { onChange(branch.copy(name = it)) },
        label = { Text("Branch Nickname (Optional)") },
        placeholder = { Text("e.g. If Skip Button Appears") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("else_if_name_input_${branch.id}"),
      )
    }

    ConditionGroupCardEditor(
      root = branch.conditionRoot,
      currentGroup = branch.conditionRoot,
      depth = 1,
      isPro = isPro,
      variables = variables,
      onRootChange = { updatedRoot -> onChange(branch.copy(conditionRoot = updatedRoot)) },
      onIntent = onIntent,
      validationErrors = validationErrors,
      previewPrefix = "Otherwise, if:",
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopBlockActionEditor(
  action: ScriptAction.LoopBlock,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.LoopBlock) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  val modeIndex =
    when (action.mode) {
      is LoopMode.Count -> 0
      is LoopMode.WhileCondition -> 1
      is LoopMode.UntilCondition -> 2
      LoopMode.Unlimited -> 3
    }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Repeat Setup") {
      OutlinedTextField(
        value = action.name,
        onValueChange = { onChange(action.copy(name = it)) },
        label = { Text("Loop Nickname (Optional)") },
        placeholder = { Text("e.g. Collect Rewards Loop") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("loop_block_name_input"),
      )

      val modeLabels =
        listOf(
          "Repeat N Times",
          "Repeat While...",
          "Repeat Until...",
          "Repeat Forever",
        )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        modeLabels.forEachIndexed { idx, label ->
          val locked = idx > 0 && !isPro
          FilterChip(
            selected = modeIndex == idx,
            onClick = {
              when (idx) {
                0 -> onChange(action.copy(mode = LoopMode.Count()))
                1 ->
                  if (!isPro) {
                    onIntent(
                      ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.ADVANCED_LOOPS.displayName),
                    )
                  } else {
                    val existing =
                      (action.mode as? LoopMode.UntilCondition)?.conditionRoot
                        ?: ConditionGroup(id = UUID.randomUUID().toString(), name = "Repeat While")
                    onChange(action.copy(mode = LoopMode.WhileCondition(existing)))
                  }
                2 ->
                  if (!isPro) {
                    onIntent(
                      ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.ADVANCED_LOOPS.displayName),
                    )
                  } else {
                    val existing =
                      (action.mode as? LoopMode.WhileCondition)?.conditionRoot
                        ?: ConditionGroup(id = UUID.randomUUID().toString(), name = "Repeat Until")
                    onChange(action.copy(mode = LoopMode.UntilCondition(existing)))
                  }
                3 ->
                  if (!isPro) {
                    onIntent(
                      ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.ADVANCED_LOOPS.displayName),
                    )
                  } else {
                    onChange(action.copy(mode = LoopMode.Unlimited))
                  }
              }
            },
            leadingIcon =
              if (locked) {
                { Icon(Icons.Rounded.Lock, contentDescription = "Pro feature", modifier = Modifier.size(14.dp)) }
              } else {
                null
              },
            label = { Text(label) },
          )
        }
      }

      when (val mode = action.mode) {
        is LoopMode.Count -> {
          NumericInputField(
            label = "How Many Times",
            value = mode.count,
            variables = variables,
            isPro = isPro,
            onValueChange = { next ->
              val safe =
                if (next is NumericInput.Literal) {
                  if (next.value == UNSET_COORD) next else NumericInput.Literal(next.value.coerceAtLeast(1))
                } else {
                  next
                }
              onChange(action.copy(mode = mode.copy(count = safe)))
            },
            onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
            errorText = validationErrors["loop_count"],
            supportingHint = "Suggested: 1 time",
          )
          OutlinedTextField(
            value = mode.exportIndexVarKey.orEmpty(),
            onValueChange = { next ->
              if (!isPro && next.isNotEmpty()) {
                onIntent(
                  ScriptEditorUiIntent.RequestProUpgrade(
                    ProFeatureGate.DYNAMIC_VARIABLES.displayName,
                  ),
                )
              } else {
                onChange(action.copy(mode = mode.copy(exportIndexVarKey = next.ifBlank { null })))
              }
            },
            label = { Text("Save Current Loop Number (Optional, Pro)") },
            placeholder = { Text("e.g. loop_num") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }

        is LoopMode.WhileCondition -> {
          ConditionGroupCardEditor(
            root = mode.conditionRoot,
            currentGroup = mode.conditionRoot,
            depth = 1,
            isPro = isPro,
            variables = variables,
            onRootChange = { updated -> onChange(action.copy(mode = LoopMode.WhileCondition(updated))) },
            onIntent = onIntent,
            validationErrors = validationErrors,
            previewPrefix = "Keep repeating while:",
          )
        }

        is LoopMode.UntilCondition -> {
          ConditionGroupCardEditor(
            root = mode.conditionRoot,
            currentGroup = mode.conditionRoot,
            depth = 1,
            isPro = isPro,
            variables = variables,
            onRootChange = { updated -> onChange(action.copy(mode = LoopMode.UntilCondition(updated))) },
            onIntent = onIntent,
            validationErrors = validationErrors,
            previewPrefix = "Keep repeating until:",
          )
        }

        LoopMode.Unlimited -> {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Keeps repeating until an 'Exit Loop', 'Stop Script', or Safety Stop Limit triggers.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
      }
    }

    EditorSectionCard(
      title = "Safety Stop Limits",
      subtitle = "Wait between repeats & automatic safety limits",
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      DelayConfigEditor(
        config = action.safeguards.intervalDelay,
        isPro = isPro,
        onConfigChange = { cfg ->
          onChange(action.copy(safeguards = action.safeguards.copy(intervalDelay = cfg)))
        },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LongTextField(
          label = "Max Run Time (ms)",
          value = action.safeguards.maxTimeoutMs,
          min = 500L,
          onChange = { ms ->
            onChange(action.copy(safeguards = action.safeguards.copy(maxTimeoutMs = ms)))
          },
          supportingHint = "Suggested: 60000 ms (60s)",
          modifier = Modifier.weight(1f),
        )
        IntTextField(
          label = "Max Loop Runs",
          value = action.safeguards.maxIterations,
          min = 1,
          onChange = { cap ->
            onChange(action.copy(safeguards = action.safeguards.copy(maxIterations = cap)))
          },
          supportingHint = "Suggested: 500 runs",
          modifier = Modifier.weight(1f),
        )
      }
      Text(
        text = "When Limit Reached",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val timeoutActions = LoopTimeoutAction.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        timeoutActions.forEachIndexed { index, timeoutAct ->
          val label =
            if (timeoutAct == LoopTimeoutAction.BREAK_AND_CONTINUE) {
              "Stop Loop & Continue"
            } else {
              "Stop Script & Alert"
            }
          SegmentedButton(
            selected = action.safeguards.onTimeout == timeoutAct,
            onClick = {
              onChange(action.copy(safeguards = action.safeguards.copy(onTimeout = timeoutAct)))
            },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = timeoutActions.size),
          ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}

@Composable
fun DelayActionEditor(
  action: ScriptAction.Delay,
  isPro: Boolean,
  onChange: (ScriptAction.Delay) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  EditorSectionCard(
    title = "Pause / Wait",
    subtitle = "Pauses before the next step (at least 50ms).",
  ) {
    DelayConfigEditor(
      config = action.config,
      isPro = isPro,
      minDelayMs = DelayConfig.MIN_STANDALONE_DELAY_MS,
      onConfigChange = { cfg -> onChange(action.copy(config = cfg)) },
      onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
    )
  }
}

@Composable
fun SimpleFlowControlNotice(action: ScriptAction) {
  val msg =
    when (action) {
      is ScriptAction.Break -> "Stops the current loop right away and moves to the step after the loop."
      is ScriptAction.Continue -> "Skips the rest of this loop turn and starts the next repeat."
      is ScriptAction.EndScript -> "Stops the entire script right away."
      else -> ""
    }
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = msg,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onTertiaryContainer,
      modifier = Modifier.padding(14.dp),
    )
  }
}
