package com.tapflow.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.LoopTimeoutAction
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.condition.BooleanCompareOp
import com.tapflow.core.model.condition.ColorMatchMode
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionMatchRule
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.ConditionValidator
import com.tapflow.core.model.condition.ImageMatchAlgorithm
import com.tapflow.core.model.condition.LogicOperator
import com.tapflow.core.model.condition.NumberCompareOp
import com.tapflow.core.model.condition.RegionBounds
import com.tapflow.core.model.condition.StringCompareOp
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.service.overlay.GesturePickerTargetType
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun IfBlockActionEditor(
  action: ScriptAction.IfBlock,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.IfBlock) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(
      value = action.name,
      onValueChange = { onChange(action.copy(name = it)) },
      label = { Text("Block Label") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("if_block_name_input"),
    )

    ConditionGroupCardEditor(
      root = action.conditionRoot,
      currentGroup = action.conditionRoot,
      depth = 1,
      isPro = isPro,
      variables = variables,
      onRootChange = { updatedRoot -> onChange(action.copy(conditionRoot = updatedRoot)) },
      onIntent = onIntent,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoopBlockActionEditor(
  action: ScriptAction.LoopBlock,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.LoopBlock) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  val modeIndex =
    when (action.mode) {
      is LoopMode.Count -> 0
      is LoopMode.WhileCondition -> 1
      is LoopMode.UntilCondition -> 2
      LoopMode.Unlimited -> 3
    }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(
      value = action.name,
      onValueChange = { onChange(action.copy(name = it)) },
      label = { Text("Loop Label") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("loop_block_name_input"),
    )

    Text("Loop Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(
        selected = modeIndex == 0,
        onClick = { onChange(action.copy(mode = LoopMode.Count())) },
        label = { Text("Count") },
      )
      FilterChip(
        selected = modeIndex == 1,
        onClick = {
          if (!isPro) {
            onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.ADVANCED_LOOPS.displayName))
          } else {
            val existing =
              (action.mode as? LoopMode.UntilCondition)?.conditionRoot
                ?: ConditionGroup(id = UUID.randomUUID().toString(), name = "While Root")
            onChange(action.copy(mode = LoopMode.WhileCondition(existing)))
          }
        },
        leadingIcon =
          if (!isPro) {
            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
          } else {
            null
          },
        label = { Text("While") },
      )
      FilterChip(
        selected = modeIndex == 2,
        onClick = {
          if (!isPro) {
            onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.ADVANCED_LOOPS.displayName))
          } else {
            val existing =
              (action.mode as? LoopMode.WhileCondition)?.conditionRoot
                ?: ConditionGroup(id = UUID.randomUUID().toString(), name = "Until Root")
            onChange(action.copy(mode = LoopMode.UntilCondition(existing)))
          }
        },
        leadingIcon =
          if (!isPro) {
            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
          } else {
            null
          },
        label = { Text("Until") },
      )
      FilterChip(
        selected = modeIndex == 3,
        onClick = {
          if (!isPro) {
            onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.ADVANCED_LOOPS.displayName))
          } else {
            onChange(action.copy(mode = LoopMode.Unlimited))
          }
        },
        leadingIcon =
          if (!isPro) {
            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
          } else {
            null
          },
        label = { Text("Unlimited") },
      )
    }

    when (val mode = action.mode) {
      is LoopMode.Count -> {
        NumericInputField(
          label = "Iteration Count",
          value = mode.count,
          variables = variables,
          isPro = isPro,
          onValueChange = { next ->
            val safe =
              if (next is NumericInput.Literal) NumericInput.Literal(next.value.coerceAtLeast(1)) else next
            onChange(action.copy(mode = mode.copy(count = safe)))
          },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        )
        OutlinedTextField(
          value = mode.exportIndexVarKey.orEmpty(),
          onValueChange = { next ->
            if (!isPro && next.isNotEmpty()) {
              onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.DYNAMIC_VARIABLES.displayName))
            } else {
              onChange(action.copy(mode = mode.copy(exportIndexVarKey = next.ifBlank { null })))
            }
          },
          label = { Text("Export Loop Index Variable Key (Optional, Pro)") },
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
        )
      }
      LoopMode.Unlimited -> {
        Text(
          text = "Runs until a Break action, End Script, or Loop Safeguard triggers.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Card(
      colors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("Loop Safeguards", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Interval Between Cycles", style = MaterialTheme.typography.labelMedium)
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
            label = "Max Timeout (ms)",
            value = action.safeguards.maxTimeoutMs,
            min = 500L,
            onChange = { ms ->
              onChange(action.copy(safeguards = action.safeguards.copy(maxTimeoutMs = ms)))
            },
            modifier = Modifier.weight(1f),
          )
          IntTextField(
            label = "Max Iterations",
            value = action.safeguards.maxIterations,
            min = 1,
            onChange = { cap ->
              onChange(action.copy(safeguards = action.safeguards.copy(maxIterations = cap)))
            },
            modifier = Modifier.weight(1f),
          )
        }
        Text("On Timeout / Max Iterations", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          LoopTimeoutAction.entries.forEach { timeoutAct ->
            FilterChip(
              selected = action.safeguards.onTimeout == timeoutAct,
              onClick = {
                onChange(action.copy(safeguards = action.safeguards.copy(onTimeout = timeoutAct)))
              },
              label = {
                Text(
                  if (timeoutAct == LoopTimeoutAction.BREAK_AND_CONTINUE) {
                    "Break & Continue"
                  } else {
                    "Stop & Alert"
                  },
                )
              },
            )
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
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Standalone blocking wait step (min 50ms). Does not carry a secondary post-delay.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    DelayConfigEditor(
      config = action.config,
      isPro = isPro,
      onConfigChange = { cfg -> onChange(action.copy(config = cfg)) },
      onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
    )
  }
}

@Composable
fun SimpleFlowControlNotice(action: ScriptAction) {
  val msg =
    when (action) {
      is ScriptAction.Break -> "Exits the nearest enclosing Loop Block and continues with the next step."
      is ScriptAction.Continue -> "Skips remaining steps inside the current Loop cycle and starts the next cycle."
      is ScriptAction.EndScript -> "Immediately terminates the running script session and resets loop state."
      else -> ""
    }
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = msg,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.padding(14.dp),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionGroupCardEditor(
  root: ConditionGroup,
  currentGroup: ConditionGroup,
  depth: Int,
  isPro: Boolean,
  variables: List<VariableDefinition>,
  onRootChange: (ConditionGroup) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  val borderColor =
    when (depth) {
      1 -> MaterialTheme.colorScheme.outline
      2 -> MaterialTheme.colorScheme.primary
      else -> MaterialTheme.colorScheme.tertiary
    }
  val containerColor =
    when (depth) {
      1 -> MaterialTheme.colorScheme.surfaceContainerLow
      2 -> MaterialTheme.colorScheme.surfaceContainer
      else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

  Card(
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.5.dp, borderColor),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (depth == 1) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = ConditionValidator.formatNaturalLanguagePreview(root),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (depth == 1) "Root Condition Group (L1)" else "Sub-Group (Level $depth)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        if (depth > 1) {
          IconButton(onClick = { onRootChange(ConditionTreeUtils.removeNode(root, currentGroup.id)) }) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Remove sub-group",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
        IconButton(onClick = { onRootChange(ConditionTreeUtils.toggleGroupExpanded(root, currentGroup.id)) }) {
          Icon(
            imageVector = if (currentGroup.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = "Toggle condition group",
          )
        }
      }

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogicOperator.entries.forEach { op ->
          FilterChip(
            selected = currentGroup.operator == op,
            onClick = { onRootChange(ConditionTreeUtils.updateGroupOperator(root, currentGroup.id, op)) },
            label = {
              Text(
                when (op) {
                  LogicOperator.ALL -> "Match ALL"
                  LogicOperator.ANY -> "Match ANY"
                  LogicOperator.NONE -> "Match NONE"
                },
              )
            },
          )
        }
      }

      if (currentGroup.isExpanded) {
        currentGroup.children.forEach { node ->
          when (node) {
            is ConditionLeaf -> {
              ConditionLeafCardEditor(
                leaf = node,
                isPro = isPro,
                variables = variables,
                onUpdate = { updated -> onRootChange(ConditionTreeUtils.updateLeaf(root, updated)) },
                onRemove = { onRootChange(ConditionTreeUtils.removeNode(root, node.id)) },
                onIntent = onIntent,
              )
            }
            is ConditionGroup -> {
              ConditionGroupCardEditor(
                root = root,
                currentGroup = node,
                depth = (depth + 1).coerceAtMost(ConditionValidator.MAX_DEPTH),
                isPro = isPro,
                variables = variables,
                onRootChange = onRootChange,
                onIntent = onIntent,
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "+ Add Condition",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(
              onClick = {
                val leaf =
                  ConditionLeaf(
                    id = UUID.randomUUID().toString(),
                    predicate =
                      ConditionPredicate.ColorMatch(
                        targets = listOf(ColorTarget(x = 540, y = 960, hexColor = "#FFFFFF")),
                      ),
                  )
                onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, leaf))
              },
              leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) },
              label = { Text("Color Match") },
            )
            AssistChip(
              onClick = {
                if (!isPro) {
                  onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.IMAGE_MATCH.displayName))
                } else {
                  val leaf =
                    ConditionLeaf(
                      id = UUID.randomUUID().toString(),
                      predicate = ConditionPredicate.ImageMatch(templatePngPath = ""),
                    )
                  onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, leaf))
                }
              },
              leadingIcon = {
                Icon(
                  imageVector = if (isPro) Icons.Filled.ImageSearch else Icons.Filled.Lock,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              label = { Text("Image Match") },
            )
            AssistChip(
              onClick = {
                val leaf =
                  ConditionLeaf(
                    id = UUID.randomUUID().toString(),
                    predicate = ConditionPredicate.AppMatch(packageName = ""),
                  )
                onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, leaf))
              },
              leadingIcon = { Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) },
              label = { Text("App Match") },
            )
            AssistChip(
              onClick = {
                if (!isPro) {
                  onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.DYNAMIC_VARIABLES.displayName))
                } else {
                  val firstVar = variables.firstOrNull()
                  val leaf =
                    ConditionLeaf(
                      id = UUID.randomUUID().toString(),
                      predicate =
                        ConditionPredicate.VariableCompare(
                          variableKey = firstVar?.key ?: "counter",
                          variableType = firstVar?.type ?: VariableType.NUMBER,
                          numberOp = if (firstVar?.type == VariableType.NUMBER || firstVar == null) NumberCompareOp.GT else null,
                          stringOp = if (firstVar?.type == VariableType.STRING) StringCompareOp.EQ else null,
                          booleanOp = if (firstVar?.type == VariableType.BOOLEAN) BooleanCompareOp.IS_TRUE else null,
                          expectedValue = "0",
                        ),
                    )
                  onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, leaf))
                }
              },
              leadingIcon = {
                Icon(
                  imageVector = if (isPro) Icons.Filled.Add else Icons.Filled.Lock,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              label = { Text("Variable Compare") },
            )
          }

          if (ConditionValidator.canAddSubGroup(root, currentGroup.id)) {
            OutlinedButton(
              onClick = {
                val sub =
                  ConditionGroup(
                    id = UUID.randomUUID().toString(),
                    name = "Nested Group",
                    operator = LogicOperator.ANY,
                  )
                onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, sub))
              },
              modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Add Condition Group (Max Depth 3)")
            }
          } else {
            Text(
              text = "Maximum condition nesting depth (${ConditionValidator.MAX_DEPTH}) reached.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionLeafCardEditor(
  leaf: ConditionLeaf,
  isPro: Boolean,
  variables: List<VariableDefinition>,
  onUpdate: (ConditionLeaf) -> Unit,
  onRemove: () -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChip(
          selected = leaf.inverted,
          onClick = { onUpdate(leaf.copy(inverted = !leaf.inverted)) },
          label = { Text(if (leaf.inverted) "NOT" else "IS") },
        )
        OutlinedTextField(
          value = leaf.label,
          onValueChange = { onUpdate(leaf.copy(label = it)) },
          placeholder = { Text("Condition label (optional)") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Delete condition",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      when (val predicate = leaf.predicate) {
        is ConditionPredicate.ColorMatch -> {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorMatchMode.entries.forEach { mode ->
              val proLocked = mode != ColorMatchMode.SINGLE_POINT && !isPro
              FilterChip(
                selected = predicate.mode == mode,
                onClick = {
                  if (proLocked) {
                    onIntent(
                      ScriptEditorUiIntent.RequestProUpgrade(
                        ProFeatureGate.MULTI_POINT_COLOR_MATCH.displayName,
                      ),
                    )
                  } else {
                    val region =
                      if (mode == ColorMatchMode.REGION && predicate.region == null) {
                        RegionBounds(100, 100, 400, 400)
                      } else {
                        predicate.region
                      }
                    onUpdate(leaf.copy(predicate = predicate.copy(mode = mode, region = region)))
                  }
                },
                leadingIcon =
                  if (proLocked) {
                    { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
                  } else {
                    null
                  },
                label = { Text(mode.name.replace('_', ' ')) },
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                onIntent(
                  ScriptEditorUiIntent.RequestOverlayPick(
                    purpose = OverlayPickPurpose.ConditionColorLeaf(leaf.id),
                    targetType =
                      if (predicate.mode == ColorMatchMode.REGION) {
                        GesturePickerTargetType.REGION_BOUNDS
                      } else {
                        GesturePickerTargetType.POINT
                      },
                  ),
                )
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Pick via Overlay")
            }
            OutlinedButton(
              onClick = {
                onIntent(
                  ScriptEditorUiIntent.OpenInspectPicker(
                    InspectPickerTarget.ConditionColorMatch(leaf.id),
                  ),
                )
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Import Inspect")
            }
          }

          if (predicate.mode == ColorMatchMode.REGION) {
            val reg = predicate.region ?: RegionBounds(100, 100, 400, 400)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              IntTextField("X1", reg.x1, 0, {
                onUpdate(leaf.copy(predicate = predicate.copy(region = reg.copy(x1 = it))))
              }, Modifier.weight(1f))
              IntTextField("Y1", reg.y1, 0, {
                onUpdate(leaf.copy(predicate = predicate.copy(region = reg.copy(y1 = it))))
              }, Modifier.weight(1f))
              IntTextField("X2", reg.x2, 0, {
                onUpdate(leaf.copy(predicate = predicate.copy(region = reg.copy(x2 = it))))
              }, Modifier.weight(1f))
              IntTextField("Y2", reg.y2, 0, {
                onUpdate(leaf.copy(predicate = predicate.copy(region = reg.copy(y2 = it))))
              }, Modifier.weight(1f))
            }
          }

          if (predicate.mode != ColorMatchMode.SINGLE_POINT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              ConditionMatchRule.entries.forEach { rule ->
                FilterChip(
                  selected = predicate.rule == rule,
                  onClick = { onUpdate(leaf.copy(predicate = predicate.copy(rule = rule))) },
                  label = { Text(rule.name.replace('_', ' ')) },
                )
              }
            }
            if (predicate.rule == ConditionMatchRule.PERCENT_MATCH) {
              IntTextField(
                label = "Match % Threshold",
                value = predicate.matchPercentThreshold,
                min = 1,
                onChange = {
                  onUpdate(
                    leaf.copy(
                      predicate = predicate.copy(matchPercentThreshold = it.coerceIn(1, 100)),
                    ),
                  )
                },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }

          predicate.targets.forEachIndexed { idx, target ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Box(
                  modifier =
                    Modifier
                      .size(22.dp)
                      .clip(CircleShape)
                      .background(parseEditorHexColor(target.hexColor))
                      .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                OutlinedTextField(
                  value = target.hexColor,
                  onValueChange = { hex ->
                    onUpdate(
                      leaf.copy(
                        predicate =
                          predicate.copy(
                            targets =
                              predicate.targets.mapIndexed { i, t ->
                                if (i == idx) t.copy(hexColor = hex) else t
                              },
                          ),
                      ),
                    )
                  },
                  label = { Text("HEX #RRGGBB") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
                if (predicate.targets.size > 1) {
                  IconButton(
                    onClick = {
                      onUpdate(
                        leaf.copy(
                          predicate =
                            predicate.copy(
                              targets = predicate.targets.filterIndexed { i, _ -> i != idx },
                            ),
                        ),
                      )
                    },
                  ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove target")
                  }
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IntTextField("X", target.x, 0, { nx ->
                  onUpdate(
                    leaf.copy(
                      predicate =
                        predicate.copy(
                          targets =
                            predicate.targets.mapIndexed { i, t ->
                              if (i == idx) t.copy(x = nx) else t
                            },
                        ),
                    ),
                  )
                }, Modifier.weight(1f))
                IntTextField("Y", target.y, 0, { ny ->
                  onUpdate(
                    leaf.copy(
                      predicate =
                        predicate.copy(
                          targets =
                            predicate.targets.mapIndexed { i, t ->
                              if (i == idx) t.copy(y = ny) else t
                            },
                        ),
                    ),
                  )
                }, Modifier.weight(1f))
                IntTextField("Tol (0-255)", target.tolerance, 0, { tol ->
                  onUpdate(
                    leaf.copy(
                      predicate =
                        predicate.copy(
                          targets =
                            predicate.targets.mapIndexed { i, t ->
                              if (i == idx) t.copy(tolerance = tol.coerceIn(0, 255)) else t
                            },
                        ),
                    ),
                  )
                }, Modifier.weight(1f))
              }
            }
          }

          if (predicate.mode == ColorMatchMode.MULTI_POINT || predicate.mode == ColorMatchMode.REGION) {
            TextButton(
              onClick = {
                onUpdate(
                  leaf.copy(
                    predicate =
                      predicate.copy(
                        targets =
                          predicate.targets + ColorTarget(x = 540, y = 960, hexColor = "#FFFFFF"),
                      ),
                  ),
                )
              },
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add Color Target")
            }
          }
        }

        is ConditionPredicate.ImageMatch -> {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                onIntent(
                  ScriptEditorUiIntent.OpenInspectPicker(
                    InspectPickerTarget.ConditionImageMatch(leaf.id),
                  ),
                )
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Import Crop from Inspect")
            }
            OutlinedButton(
              onClick = {
                onIntent(
                  ScriptEditorUiIntent.RequestOverlayPick(
                    purpose = OverlayPickPurpose.ConditionImageRoi(leaf.id),
                    targetType = GesturePickerTargetType.REGION_BOUNDS,
                  ),
                )
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Pick ROI Overlay")
            }
          }

          if (predicate.templatePngPath.isNotBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              AsyncImage(
                model = File(predicate.templatePngPath),
                contentDescription = "Template crop PNG",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp),
              )
              Text(
                text = File(predicate.templatePngPath).name,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
              )
            }
          } else {
            Text(
              text = "No lossless PNG template imported yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }

          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageMatchAlgorithm.entries.forEach { algo ->
              FilterChip(
                selected = predicate.algorithm == algo,
                onClick = { onUpdate(leaf.copy(predicate = predicate.copy(algorithm = algo))) },
                label = {
                  Text(
                    if (algo == ImageMatchAlgorithm.FAST_32_PIXEL) {
                      "Fast 32-Pixel"
                    } else {
                      "Template Match"
                    },
                  )
                },
              )
            }
          }

          Text(
            text = "Similarity Threshold: ${(predicate.similarityThreshold * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
          )
          Slider(
            value = predicate.similarityThreshold,
            onValueChange = {
              onUpdate(leaf.copy(predicate = predicate.copy(similarityThreshold = it)))
            },
            valueRange = 0.50f..0.99f,
          )

          predicate.searchRoi?.let { roi ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Search ROI: (${roi.x1}, ${roi.y1}) ➔ (${roi.x2}, ${roi.y2})",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
              )
              TextButton(onClick = { onUpdate(leaf.copy(predicate = predicate.copy(searchRoi = null))) }) {
                Text("Clear ROI")
              }
            }
          }
        }

        is ConditionPredicate.AppMatch -> {
          Text(
            text =
              if (predicate.packageName.isBlank()) {
                "No app selected"
              } else {
                "${predicate.appLabel.ifBlank { predicate.packageName }} (${predicate.packageName})"
              },
            style = MaterialTheme.typography.bodyMedium,
          )
          OutlinedButton(
            onClick = {
              onIntent(ScriptEditorUiIntent.OpenAppPicker(AppPickerTarget.ConditionAppMatch(leaf.id)))
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Select App / Foreground App")
          }
        }

        is ConditionPredicate.VariableCompare -> {
          OutlinedTextField(
            value = predicate.variableKey,
            onValueChange = { onUpdate(leaf.copy(predicate = predicate.copy(variableKey = it))) },
            label = { Text("Variable Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VariableType.entries.forEach { t ->
              FilterChip(
                selected = predicate.variableType == t,
                onClick = {
                  onUpdate(
                    leaf.copy(
                      predicate =
                        predicate.copy(
                          variableType = t,
                          stringOp = if (t == VariableType.STRING) StringCompareOp.EQ else null,
                          numberOp = if (t == VariableType.NUMBER) NumberCompareOp.EQ else null,
                          booleanOp = if (t == VariableType.BOOLEAN) BooleanCompareOp.IS_TRUE else null,
                        ),
                    ),
                  )
                },
                label = { Text(t.displayLabel) },
              )
            }
          }
          when (predicate.variableType) {
            VariableType.STRING -> {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StringCompareOp.entries.forEach { op ->
                  FilterChip(
                    selected = predicate.stringOp == op,
                    onClick = { onUpdate(leaf.copy(predicate = predicate.copy(stringOp = op))) },
                    label = { Text(op.name) },
                  )
                }
              }
              OutlinedTextField(
                value = predicate.expectedValue,
                onValueChange = { onUpdate(leaf.copy(predicate = predicate.copy(expectedValue = it))) },
                label = { Text("Expected Value / Length") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
            }
            VariableType.NUMBER -> {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberCompareOp.entries.forEach { op ->
                  FilterChip(
                    selected = predicate.numberOp == op,
                    onClick = { onUpdate(leaf.copy(predicate = predicate.copy(numberOp = op))) },
                    label = { Text(op.name) },
                  )
                }
              }
              OutlinedTextField(
                value = predicate.expectedValue,
                onValueChange = { onUpdate(leaf.copy(predicate = predicate.copy(expectedValue = it))) },
                label = { Text("Expected Number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
            }
            VariableType.BOOLEAN -> {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BooleanCompareOp.entries.forEach { op ->
                  FilterChip(
                    selected = predicate.booleanOp == op,
                    onClick = { onUpdate(leaf.copy(predicate = predicate.copy(booleanOp = op))) },
                    label = { Text(op.name) },
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
