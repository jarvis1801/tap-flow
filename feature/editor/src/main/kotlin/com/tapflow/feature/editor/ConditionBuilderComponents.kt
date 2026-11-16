package com.tapflow.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
  validationErrors: Map<String, String> = emptyMap(),
  previewPrefix: String = "If:",
) {
  var showAddRuleCards by remember(currentGroup.id) { mutableStateOf(false) }
  val groupError = validationErrors["cond_group_${currentGroup.id}"]

  val cardContent: @Composable () -> Unit = {
    val containerColor =
      when (depth) {
        1 -> MaterialTheme.colorScheme.surfaceContainerLow
        2 -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
      }
    val borderColor =
      if (groupError != null) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.outlineVariant
      }

    Surface(
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(if (groupError != null) 1.dp else 0.75.dp, borderColor),
      color = containerColor,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (depth == 1) {
          val sentence =
            PlainLanguageFormatter.formatConditionSentence(
              root = root,
              prefix = previewPrefix,
              emptyText = "No rules added yet — tap '+ Add Rule' below",
            )
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = sentence,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (depth == 1) {
                  "When should this run?"
                } else {
                  PlainLanguageFormatter.logicOperatorShortHeader(currentGroup.operator)
                },
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
          if (depth > 1) {
            IconButton(
              onClick = { onRootChange(ConditionTreeUtils.removeNode(root, currentGroup.id)) },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete sub-group",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
          IconButton(
            onClick = {
              onRootChange(ConditionTreeUtils.toggleGroupExpanded(root, currentGroup.id))
            },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector =
                if (currentGroup.isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
              contentDescription =
                if (currentGroup.isExpanded) "Collapse rule group" else "Expand rule group",
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            LogicOperator.entries.forEach { op ->
              FilterChip(
                selected = currentGroup.operator == op,
                onClick = {
                  onRootChange(ConditionTreeUtils.updateGroupOperator(root, currentGroup.id, op))
                },
                label = { Text(PlainLanguageFormatter.logicOperatorTitle(op)) },
              )
            }
          }
          Text(
            text = PlainLanguageFormatter.logicOperatorHelper(currentGroup.operator),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (groupError != null) {
          Text(
            text = groupError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
          )
        }

        if (currentGroup.isExpanded) {
          currentGroup.children.forEach { node ->
            when (node) {
              is ConditionLeaf -> {
                ConditionLeafCardEditor(
                  leaf = node,
                  isPro = isPro,
                  variables = variables,
                  validationErrors = validationErrors,
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
                  validationErrors = validationErrors,
                  onRootChange = onRootChange,
                  onIntent = onIntent,
                )
              }
            }
          }

          FilledTonalButton(
            onClick = { showAddRuleCards = !showAddRuleCards },
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("add_rule_button_${currentGroup.id}"),
          ) {
            Icon(
              imageVector = if (showAddRuleCards) Icons.Rounded.Close else Icons.Rounded.Add,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (showAddRuleCards) "Close Rule Picker" else "Add Rule")
          }

          AnimatedVisibility(visible = showAddRuleCards) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              RuleOptionCard(
                icon = Icons.Rounded.Palette,
                title = "Check Screen Color",
                subtitle = "Check if a spot on screen is a specific color",
                locked = false,
                onClick = {
                  showAddRuleCards = false
                  val leaf =
                    ConditionLeaf(
                      id = UUID.randomUUID().toString(),
                      predicate =
                        ConditionPredicate.ColorMatch(
                          targets = listOf(ColorTarget(x = UNSET_COORD, y = UNSET_COORD, hexColor = "")),
                        ),
                    )
                  onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, leaf))
                },
              )
              RuleOptionCard(
                icon = Icons.Rounded.ImageSearch,
                title = "Find Image on Screen",
                subtitle = "Check if a button or icon appears on screen",
                locked = !isPro,
                onClick = {
                  if (!isPro) {
                    onIntent(
                      ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.ADVANCED_VISION.displayName),
                    )
                  } else {
                    showAddRuleCards = false
                    val leaf =
                      ConditionLeaf(
                        id = UUID.randomUUID().toString(),
                        predicate = ConditionPredicate.ImageMatch(templatePngPath = ""),
                      )
                    onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, leaf))
                  }
                },
              )
              RuleOptionCard(
                icon = Icons.Rounded.Apps,
                title = "Check Current App",
                subtitle = "Check which app is open",
                locked = false,
                onClick = {
                  showAddRuleCards = false
                  val leaf =
                    ConditionLeaf(
                      id = UUID.randomUUID().toString(),
                      predicate = ConditionPredicate.AppMatch(packageName = ""),
                    )
                  onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, leaf))
                },
              )
              RuleOptionCard(
                icon = Icons.Rounded.Pin,
                title = "Check Variable",
                subtitle = "Compare a saved number or text",
                locked = false,
                onClick = {
                  showAddRuleCards = false
                  val firstVar = variables.firstOrNull()
                  val leaf =
                    ConditionLeaf(
                      id = UUID.randomUUID().toString(),
                      predicate =
                        ConditionPredicate.VariableCompare(
                          variableKey = firstVar?.key.orEmpty(),
                          variableType = firstVar?.type ?: VariableType.NUMBER,
                          numberOp =
                            if (firstVar?.type == VariableType.NUMBER || firstVar == null) {
                              NumberCompareOp.GT
                            } else {
                              null
                            },
                          stringOp = if (firstVar?.type == VariableType.STRING) StringCompareOp.EQ else null,
                          booleanOp =
                            if (firstVar?.type == VariableType.BOOLEAN) {
                              BooleanCompareOp.IS_TRUE
                            } else {
                              null
                            },
                          expectedValue = "",
                        ),
                    )
                  onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, leaf))
                },
              )
            }
          }

          if (ConditionValidator.canAddSubGroup(root, currentGroup.id)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              OutlinedButton(
                onClick = {
                  val sub =
                    ConditionGroup(
                      id = UUID.randomUUID().toString(),
                      name = "Sub-Group",
                      operator = LogicOperator.ANY,
                    )
                  onRootChange(ConditionTreeUtils.addNodeToGroup(root, currentGroup.id, sub))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.AccountTree,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ Add Sub-Group (AND / OR)")
              }
              Text(
                text = "Group rules together, e.g. A AND (B OR C)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            Text(
              text = "Maximum ${ConditionValidator.MAX_DEPTH} nesting levels reached.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }

  if (depth <= 1) {
    cardContent()
  } else {
    val railColor =
      if (depth == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 8.dp).height(IntrinsicSize.Min),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier
            .width(3.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(railColor.copy(alpha = 0.75f)),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Box(modifier = Modifier.weight(1f)) {
        cardContent()
      }
    }
  }
}

@Composable
private fun RuleOptionCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  locked: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(38.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (locked) {
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = "Pro feature",
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "PRO",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
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
  validationErrors: Map<String, String>,
  onUpdate: (ConditionLeaf) -> Unit,
  onRemove: () -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  val prefix = "cond_leaf_${leaf.id}"
  val ruleTitle =
    when (leaf.predicate) {
      is ConditionPredicate.ColorMatch -> "Check Screen Color"
      is ConditionPredicate.ImageMatch -> "Find Image on Screen"
      is ConditionPredicate.AppMatch -> "Check Current App"
      is ConditionPredicate.VariableCompare -> "Check Variable"
    }
  val ruleIcon =
    when (leaf.predicate) {
      is ConditionPredicate.ColorMatch -> Icons.Rounded.Palette
      is ConditionPredicate.ImageMatch -> Icons.Rounded.ImageSearch
      is ConditionPredicate.AppMatch -> Icons.Rounded.Apps
      is ConditionPredicate.VariableCompare -> Icons.Rounded.Pin
    }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = ruleIcon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = ruleTitle,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete rule",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      when (val predicate = leaf.predicate) {
        is ConditionPredicate.ColorMatch -> {
          ColorMatchRuleBody(
            leaf = leaf,
            predicate = predicate,
            prefix = prefix,
            isPro = isPro,
            validationErrors = validationErrors,
            onUpdate = onUpdate,
            onIntent = onIntent,
          )
        }

        is ConditionPredicate.ImageMatch -> {
          ImageMatchRuleBody(
            leaf = leaf,
            predicate = predicate,
            prefix = prefix,
            validationErrors = validationErrors,
            onUpdate = onUpdate,
            onIntent = onIntent,
          )
        }

        is ConditionPredicate.AppMatch -> {
          AppMatchRuleBody(
            leaf = leaf,
            predicate = predicate,
            prefix = prefix,
            validationErrors = validationErrors,
            onUpdate = onUpdate,
            onIntent = onIntent,
          )
        }

        is ConditionPredicate.VariableCompare -> {
          VariableCompareRuleBody(
            leaf = leaf,
            predicate = predicate,
            prefix = prefix,
            variables = variables,
            validationErrors = validationErrors,
            onUpdate = onUpdate,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorMatchRuleBody(
  leaf: ConditionLeaf,
  predicate: ConditionPredicate.ColorMatch,
  prefix: String,
  isPro: Boolean,
  validationErrors: Map<String, String>,
  onUpdate: (ConditionLeaf) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  var showMultiRegion by remember(leaf.id) {
    mutableStateOf(predicate.mode != ColorMatchMode.SINGLE_POINT)
  }

  // Simple positive/negative toggle
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    listOf("Matches this color", "Does NOT match").forEachIndexed { index, label ->
      val selected = (index == 0 && !leaf.inverted) || (index == 1 && leaf.inverted)
      SegmentedButton(
        selected = selected,
        onClick = { onUpdate(leaf.copy(inverted = index == 1)) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
      ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }

  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilledTonalButton(
      onClick = {
        onIntent(
          ScriptEditorUiIntent.OpenInspectPicker(
            InspectPickerTarget.ConditionColorMatch(leaf.id),
          ),
        )
      },
      modifier = Modifier.weight(1f).heightIn(min = 48.dp),
    ) {
      Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Pick from Saved Inspect", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
      modifier = Modifier.weight(0.85f).heightIn(min = 48.dp),
    ) {
      Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Pick Spot", maxLines = 1)
    }
  }

  validationErrors["${prefix}_color"]?.let { err ->
    Text(
      text = err,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
  }

  if (predicate.mode == ColorMatchMode.REGION) {
    val reg =
      predicate.region ?: RegionBounds(UNSET_COORD, UNSET_COORD, UNSET_COORD, UNSET_COORD)
    val regErr = validationErrors["${prefix}_region"]
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      IntTextField(
        label = "Left X",
        value = reg.x1,
        min = 0,
        onChange = { onUpdate(leaf.copy(predicate = predicate.copy(region = reg.copy(x1 = it)))) },
        modifier = Modifier.weight(1f),
        isError = regErr != null && reg.x1.isUnsetCoord(),
        unsetValue = UNSET_COORD,
      )
      IntTextField(
        label = "Top Y",
        value = reg.y1,
        min = 0,
        onChange = { onUpdate(leaf.copy(predicate = predicate.copy(region = reg.copy(y1 = it)))) },
        modifier = Modifier.weight(1f),
        isError = regErr != null && reg.y1.isUnsetCoord(),
        unsetValue = UNSET_COORD,
      )
      IntTextField(
        label = "Right X",
        value = reg.x2,
        min = 0,
        onChange = { onUpdate(leaf.copy(predicate = predicate.copy(region = reg.copy(x2 = it)))) },
        modifier = Modifier.weight(1f),
        isError = regErr != null && reg.x2.isUnsetCoord(),
        unsetValue = UNSET_COORD,
      )
      IntTextField(
        label = "Bottom Y",
        value = reg.y2,
        min = 0,
        onChange = { onUpdate(leaf.copy(predicate = predicate.copy(region = reg.copy(y2 = it)))) },
        modifier = Modifier.weight(1f),
        isError = regErr != null && reg.y2.isUnsetCoord(),
        unsetValue = UNSET_COORD,
      )
    }
    if (regErr != null) {
      Text(text = regErr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
  }

  predicate.targets.forEachIndexed { idx, target ->
    val hexErr = validationErrors["${prefix}_target_${idx}_hex"]
    val xErr = validationErrors["${prefix}_target_${idx}_x"]
    val yErr = validationErrors["${prefix}_target_${idx}_y"]
    val previewColor = parseEditorHexColorOrNull(target.hexColor)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(previewColor ?: MaterialTheme.colorScheme.surfaceContainerHighest)
              .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          if (previewColor == null) {
            Text(
              text = "?",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
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
          label = { Text("Color Code (HEX)") },
          placeholder = { Text("#FF0000") },
          isError = hexErr != null,
          supportingText = hexErr?.let { err -> { Text(err) } },
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
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove color spot")
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntTextField(
          label = "X",
          value = target.x,
          min = 0,
          onChange = { nx ->
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
          },
          modifier = Modifier.weight(1f),
          errorText = xErr,
          unsetValue = UNSET_COORD,
        )
        IntTextField(
          label = "Y",
          value = target.y,
          min = 0,
          onChange = { ny ->
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
          },
          modifier = Modifier.weight(1f),
          errorText = yErr,
          unsetValue = UNSET_COORD,
        )
      }
    }
  }

  val mainTolerance = predicate.targets.firstOrNull()?.tolerance ?: 15
  val strictLabel =
    when {
      mainTolerance <= 5 -> "Strict ($mainTolerance)"
      mainTolerance <= 25 -> "Balanced ($mainTolerance, Suggested)"
      else -> "Loose ($mainTolerance)"
    }
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = "Match Strictness: $strictLabel",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
      value = mainTolerance.coerceIn(0, 80).toFloat(),
      onValueChange = { raw ->
        val tol = raw.roundToInt().coerceIn(0, 80)
        onUpdate(
          leaf.copy(
            predicate =
              predicate.copy(
                targets = predicate.targets.map { it.copy(tolerance = tol) },
              ),
          ),
        )
      },
      valueRange = 0f..80f,
    )
  }

  TextButton(
    onClick = { showMultiRegion = !showMultiRegion },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text =
        if (showMultiRegion) {
          "Hide Multi-point / Region Options"
        } else {
          "Multi-point / Region"
        },
      modifier = Modifier.weight(1f),
    )
    Icon(
      imageVector = if (showMultiRegion) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
      contentDescription = null,
    )
  }

  AnimatedVisibility(visible = showMultiRegion) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val colorModes = ColorMatchMode.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        colorModes.forEachIndexed { index, mode ->
          val proLocked = mode != ColorMatchMode.SINGLE_POINT && !isPro
          val label =
            when (mode) {
              ColorMatchMode.SINGLE_POINT -> "One Spot"
              ColorMatchMode.MULTI_POINT -> "Multi-point"
              ColorMatchMode.REGION -> "Screen Area"
            }
          SegmentedButton(
            selected = predicate.mode == mode,
            onClick = {
              if (proLocked) {
                onIntent(
                  ScriptEditorUiIntent.RequestProUpgrade(
                    ProFeatureGate.ADVANCED_VISION.displayName,
                  ),
                )
              } else {
                val region =
                  if (mode == ColorMatchMode.REGION && predicate.region == null) {
                    RegionBounds(UNSET_COORD, UNSET_COORD, UNSET_COORD, UNSET_COORD)
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

      if (predicate.mode != ColorMatchMode.SINGLE_POINT) {
        val rules = ConditionMatchRule.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          rules.forEachIndexed { index, rule ->
            val label =
              when (rule) {
                ConditionMatchRule.ALL -> "All Spots"
                ConditionMatchRule.ANY -> "Any Spot"
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
            label = "Match % Required",
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

      if (predicate.mode == ColorMatchMode.MULTI_POINT || predicate.mode == ColorMatchMode.REGION) {
        OutlinedButton(
          onClick = {
            onUpdate(
              leaf.copy(
                predicate =
                  predicate.copy(
                    targets =
                      predicate.targets +
                        ColorTarget(x = UNSET_COORD, y = UNSET_COORD, hexColor = ""),
                  ),
              ),
            )
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Another Color Spot")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageMatchRuleBody(
  leaf: ConditionLeaf,
  predicate: ConditionPredicate.ImageMatch,
  prefix: String,
  validationErrors: Map<String, String>,
  onUpdate: (ConditionLeaf) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  var showSearchArea by remember(leaf.id) { mutableStateOf(predicate.searchRoi != null) }
  val imgErr = validationErrors["${prefix}_image"]

  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    listOf("Visible on screen", "NOT visible").forEachIndexed { index, label ->
      val selected = (index == 0 && !leaf.inverted) || (index == 1 && leaf.inverted)
      SegmentedButton(
        selected = selected,
        onClick = { onUpdate(leaf.copy(inverted = index == 1)) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
      ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }

  FilledTonalButton(
    onClick = {
      onIntent(
        ScriptEditorUiIntent.OpenInspectPicker(
          InspectPickerTarget.ConditionImageMatch(leaf.id),
        ),
      )
    },
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
  ) {
    Icon(Icons.Rounded.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text("Pick Image from Inspect")
  }

  if (predicate.templatePngPath.isNotBlank()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      AsyncImage(
        model = File(predicate.templatePngPath),
        contentDescription = "Selected image preview",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(48.dp),
      )
      Text(
        text = File(predicate.templatePngPath).name,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  } else {
    Text(
      text = imgErr ?: "No image picked yet. Take a screenshot crop in Inspect first.",
      style = MaterialTheme.typography.bodySmall,
      color = if (imgErr != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  Text(
    text = "Match Accuracy: ${(predicate.similarityThreshold * 100f).roundToInt()}% (Suggested: 85%)",
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Slider(
    value = predicate.similarityThreshold,
    onValueChange = {
      onUpdate(leaf.copy(predicate = predicate.copy(similarityThreshold = it)))
    },
    valueRange = 0.50f..0.99f,
  )

  TextButton(
    onClick = { showSearchArea = !showSearchArea },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = "Search Area (Optional)",
      modifier = Modifier.weight(1f),
    )
    Icon(
      imageVector = if (showSearchArea) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
      contentDescription = null,
    )
  }

  AnimatedVisibility(visible = showSearchArea) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = {
          onIntent(
            ScriptEditorUiIntent.RequestOverlayPick(
              purpose = OverlayPickPurpose.ConditionImageRoi(leaf.id),
              targetType = GesturePickerTargetType.REGION_BOUNDS,
            ),
          )
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      ) {
        Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Pick Search Area on Screen")
      }
      predicate.searchRoi?.let { roi ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Area: (${roi.x1}, ${roi.y1}) ➔ (${roi.x2}, ${roi.y2})",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
          )
          TextButton(onClick = { onUpdate(leaf.copy(predicate = predicate.copy(searchRoi = null))) }) {
            Text("Use Full Screen")
          }
        }
      }
      val algos = ImageMatchAlgorithm.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        algos.forEachIndexed { index, algo ->
          val label =
            if (algo == ImageMatchAlgorithm.FAST_32_PIXEL) {
              "Fast Match"
            } else {
              "Detailed Match"
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
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppMatchRuleBody(
  leaf: ConditionLeaf,
  predicate: ConditionPredicate.AppMatch,
  prefix: String,
  validationErrors: Map<String, String>,
  onUpdate: (ConditionLeaf) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  val appErr = validationErrors["${prefix}_app"]

  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    listOf("Is Open", "Is NOT Open").forEachIndexed { index, label ->
      val selected = (index == 0 && !leaf.inverted) || (index == 1 && leaf.inverted)
      SegmentedButton(
        selected = selected,
        onClick = { onUpdate(leaf.copy(inverted = index == 1)) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
      ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }

  Text(
    text =
      if (predicate.packageName.isBlank()) {
        appErr ?: "No app chosen yet"
      } else {
        "${predicate.appLabel.ifBlank { predicate.packageName }} (${predicate.packageName})"
      },
    style = MaterialTheme.typography.bodyMedium,
    color =
      if (predicate.packageName.isBlank() && appErr != null) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.onSurface
      },
  )

  FilledTonalButton(
    onClick = {
      onIntent(ScriptEditorUiIntent.OpenAppPicker(AppPickerTarget.ConditionAppMatch(leaf.id)))
    },
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
  ) {
    Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(if (predicate.packageName.isBlank()) "Choose App" else "Change App")
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariableCompareRuleBody(
  leaf: ConditionLeaf,
  predicate: ConditionPredicate.VariableCompare,
  prefix: String,
  variables: List<VariableDefinition>,
  validationErrors: Map<String, String>,
  onUpdate: (ConditionLeaf) -> Unit,
) {
  val keyErr = validationErrors["${prefix}_var_key"]
  val expectedErr = validationErrors["${prefix}_var_expected"]

  if (variables.isNotEmpty()) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      variables.forEach { v ->
        AssistChip(
          onClick = {
            onUpdate(
              leaf.copy(
                predicate =
                  predicate.copy(
                    variableKey = v.key,
                    variableType = v.type,
                    stringOp = if (v.type == VariableType.STRING) StringCompareOp.EQ else null,
                    numberOp = if (v.type == VariableType.NUMBER) NumberCompareOp.EQ else null,
                    booleanOp = if (v.type == VariableType.BOOLEAN) BooleanCompareOp.IS_TRUE else null,
                  ),
              ),
            )
          },
          label = { Text(v.key) },
        )
      }
    }
  }

  OutlinedTextField(
    value = predicate.variableKey,
    onValueChange = { onUpdate(leaf.copy(predicate = predicate.copy(variableKey = it))) },
    label = { Text("Variable Name") },
    isError = keyErr != null,
    supportingText = keyErr?.let { err -> { Text(err) } },
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
            label = { Text(PlainLanguageFormatter.stringCompareLabel(op)) },
          )
        }
      }
      OutlinedTextField(
        value = predicate.expectedValue,
        onValueChange = { onUpdate(leaf.copy(predicate = predicate.copy(expectedValue = it))) },
        label = { Text("Compare With") },
        isError = expectedErr != null,
        supportingText = expectedErr?.let { err -> { Text(err) } },
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
            label = { Text(PlainLanguageFormatter.numberCompareLabel(op)) },
          )
        }
      }
      OutlinedTextField(
        value = predicate.expectedValue,
        onValueChange = { onUpdate(leaf.copy(predicate = predicate.copy(expectedValue = it))) },
        label = { Text("Number to Compare") },
        isError = expectedErr != null,
        supportingText = expectedErr?.let { err -> { Text(err) } },
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
            label = { Text(PlainLanguageFormatter.booleanCompareLabel(op)) },
          )
        }
      }
    }
  }
}
