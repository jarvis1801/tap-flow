package com.tapflow.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.LoopTimeoutAction
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
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
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Condition Block") {
      OutlinedTextField(
        value = action.name,
        onValueChange = { onChange(action.copy(name = it)) },
        label = { Text("Block Label") },
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
) {
  val modeIndex =
    when (action.mode) {
      is LoopMode.Count -> 0
      is LoopMode.WhileCondition -> 1
      is LoopMode.UntilCondition -> 2
      LoopMode.Unlimited -> 3
    }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Loop Configuration") {
      OutlinedTextField(
        value = action.name,
        onValueChange = { onChange(action.copy(name = it)) },
        label = { Text("Loop Label") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("loop_block_name_input"),
      )

      val modeLabels = listOf("Count", "While", "Until", "∞")
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modeLabels.forEachIndexed { idx, label ->
          val locked = idx > 0 && !isPro
          SegmentedButton(
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
                        ?: ConditionGroup(id = UUID.randomUUID().toString(), name = "While Root")
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
                        ?: ConditionGroup(id = UUID.randomUUID().toString(), name = "Until Root")
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
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = modeLabels.size),
            icon = {
              if (locked) {
                Icon(Icons.Rounded.Lock, contentDescription = "Pro feature", modifier = Modifier.size(14.dp))
              } else {
                SegmentedButtonDefaults.Icon(active = modeIndex == idx)
              }
            },
          ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
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
                onIntent(
                  ScriptEditorUiIntent.RequestProUpgrade(
                    ProFeatureGate.DYNAMIC_VARIABLES.displayName,
                  ),
                )
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
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Runs continuously until a Break action, End Script, or Loop Safeguard triggers.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
      }
    }

    EditorSectionCard(
      title = "Loop Safeguards",
      subtitle = "Interval between cycles & timeout limits",
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
      Text(
        text = "On Timeout / Max Iterations",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val timeoutActions = LoopTimeoutAction.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        timeoutActions.forEachIndexed { index, timeoutAct ->
          val label =
            if (timeoutAct == LoopTimeoutAction.BREAK_AND_CONTINUE) {
              "Break & Continue"
            } else {
              "Stop & Alert"
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
    title = "Blocking Delay",
    subtitle = "Standalone wait step (min 50ms). Does not add a secondary post-delay.",
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
      is ScriptAction.Break -> "Exits the nearest enclosing Loop Block and continues with the next step."
      is ScriptAction.Continue -> "Skips remaining steps inside the current Loop cycle and starts the next cycle."
      is ScriptAction.EndScript -> "Immediately terminates the running script session and resets loop state."
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

@OptIn(ExperimentalMaterial3Api::class)
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
      1 -> MaterialTheme.colorScheme.outlineVariant
      2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
      else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    }
  val containerColor =
    when (depth) {
      1 -> MaterialTheme.colorScheme.surfaceContainerLow
      2 -> MaterialTheme.colorScheme.surfaceContainer
      else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

  Surface(
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, borderColor),
    color = containerColor,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (depth == 1) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = ConditionValidator.formatNaturalLanguagePreview(root),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
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
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        if (depth > 1) {
          IconButton(onClick = { onRootChange(ConditionTreeUtils.removeNode(root, currentGroup.id)) }) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = "Remove sub-group",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
        IconButton(onClick = { onRootChange(ConditionTreeUtils.toggleGroupExpanded(root, currentGroup.id)) }) {
          Icon(
            imageVector = if (currentGroup.isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = "Toggle condition group",
          )
        }
      }

      val ops = LogicOperator.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ops.forEachIndexed { index, op ->
          val label =
            when (op) {
              LogicOperator.ALL -> "Match ALL"
              LogicOperator.ANY -> "Match ANY"
              LogicOperator.NONE -> "Match NONE"
            }
          SegmentedButton(
            selected = currentGroup.operator == op,
            onClick = { onRootChange(ConditionTreeUtils.updateGroupOperator(root, currentGroup.id, op)) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = ops.size),
          ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Add Condition",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
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
              leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) },
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
                  imageVector = if (isPro) Icons.Rounded.ImageSearch else Icons.Rounded.Lock,
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
              leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) },
              label = { Text("App Match") },
            )
            AssistChip(
              onClick = {
                if (!isPro) {
                  onIntent(
                    ScriptEditorUiIntent.RequestProUpgrade(
                      ProFeatureGate.DYNAMIC_VARIABLES.displayName,
                    ),
                  )
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
                  imageVector = if (isPro) Icons.Rounded.Add else Icons.Rounded.Lock,
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
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionLeafCardEditor(
  leaf: ConditionLeaf,
  isPro: Boolean,
  variables: List<VariableDefinition>,
  onUpdate: (ConditionLeaf) -> Unit,
  onRemove: () -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SingleChoiceSegmentedButtonRow {
          listOf("IS", "NOT").forEachIndexed { index, label ->
            val selected = (index == 0 && !leaf.inverted) || (index == 1 && leaf.inverted)
            SegmentedButton(
              selected = selected,
              onClick = { onUpdate(leaf.copy(inverted = index == 1)) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
            ) {
              Text(label, maxLines = 1)
            }
          }
        }
        OutlinedTextField(
          value = leaf.label,
          onValueChange = { onUpdate(leaf.copy(label = it)) },
          placeholder = { Text("Label (optional)") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete condition",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      when (val predicate = leaf.predicate) {
        is ConditionPredicate.ColorMatch -> {
          val colorModes = ColorMatchMode.entries
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            colorModes.forEachIndexed { index, mode ->
              val proLocked = mode != ColorMatchMode.SINGLE_POINT && !isPro
              val label =
                when (mode) {
                  ColorMatchMode.SINGLE_POINT -> "Single"
                  ColorMatchMode.MULTI_POINT -> "Multi"
                  ColorMatchMode.REGION -> "Region"
                }
              SegmentedButton(
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
                shape = SegmentedButtonDefaults.itemShape(index = index, count = colorModes.size),
                icon = {
                  if (proLocked) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Pro feature", modifier = Modifier.size(14.dp))
                  } else {
                    SegmentedButtonDefaults.Icon(active = predicate.mode == mode)
                  }
                },
              ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
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
              Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Overlay", maxLines = 1)
            }
            FilledTonalButton(
              onClick = {
                onIntent(
                  ScriptEditorUiIntent.OpenInspectPicker(
                    InspectPickerTarget.ConditionColorMatch(leaf.id),
                  ),
                )
              },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Inspect", maxLines = 1)
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
            val rules = ConditionMatchRule.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              rules.forEachIndexed { index, rule ->
                val label =
                  when (rule) {
                    ConditionMatchRule.ALL -> "ALL"
                    ConditionMatchRule.ANY -> "ANY"
                    ConditionMatchRule.PERCENT_MATCH -> "% Match"
                  }
                SegmentedButton(
                  selected = predicate.rule == rule,
                  onClick = { onUpdate(leaf.copy(predicate = predicate.copy(rule = rule))) },
                  shape = SegmentedButtonDefaults.itemShape(index = index, count = rules.size),
                ) {
                  Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove target")
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
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add Color Target")
            }
          }
        }

        is ConditionPredicate.ImageMatch -> {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
              onClick = {
                onIntent(
                  ScriptEditorUiIntent.OpenInspectPicker(
                    InspectPickerTarget.ConditionImageMatch(leaf.id),
                  ),
                )
              },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.ImageSearch, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Import Crop", maxLines = 1)
            }
            FilledTonalButton(
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
              Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pick ROI", maxLines = 1)
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

          val algos = ImageMatchAlgorithm.entries
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            algos.forEachIndexed { index, algo ->
              val label =
                if (algo == ImageMatchAlgorithm.FAST_32_PIXEL) {
                  "32-Pixel Fast"
                } else {
                  "Template Match"
                }
              SegmentedButton(
                selected = predicate.algorithm == algo,
                onClick = { onUpdate(leaf.copy(predicate = predicate.copy(algorithm = algo))) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = algos.size),
              ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
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
          FilledTonalButton(
            onClick = {
              onIntent(ScriptEditorUiIntent.OpenAppPicker(AppPickerTarget.ConditionAppMatch(leaf.id)))
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
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
          val types = VariableType.entries
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, t ->
              SegmentedButton(
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
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
              ) {
                Text(t.displayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
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
