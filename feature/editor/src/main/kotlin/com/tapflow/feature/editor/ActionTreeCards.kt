package com.tapflow.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ConditionGroup

@Composable
internal fun InlineInsertDivider(
  label: String,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
    Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier =
        Modifier
          .clip(RoundedCornerShape(999.dp))
          .clickable(onClick = onClick)
          .testTag(testTag),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
  }
}

@Composable
internal fun ActionTreeItemCard(
  action: ScriptAction,
  stepLabel: String,
  location: ActionBranchLocation,
  indexInBranch: Int,
  siblingCount: Int,
  nestingDepth: Int,
  globalDelay: DelayConfig,
  revealedElseBlockIds: Set<String>,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isContainer = action is ScriptAction.IfBlock || action is ScriptAction.LoopBlock
  val isExpanded =
    when (action) {
      is ScriptAction.IfBlock -> action.isExpanded
      is ScriptAction.LoopBlock -> action.isExpanded
      else -> false
    }
  var menuExpanded by remember(action.id) { mutableStateOf(false) }

  val containerColor =
    if (isContainer) {
      if (nestingDepth > 0) {
        MaterialTheme.colorScheme.surfaceContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      }
    } else {
      MaterialTheme.colorScheme.surfaceContainerLow
    }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = containerColor,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .clickable { onIntent(ScriptEditorUiIntent.OpenEditActionSheet(action.id)) }
        .testTag("action_card_${action.id}"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          ActionIconBadge(
            icon = action.actionIcon(),
            category = action.categoryGroup(),
            boxSize = 40.dp,
            iconSize = 20.dp,
            cornerRadius = 12.dp,
          )
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
          ) {
            Text(
              text = stepLabel,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = ActionTreeUtils.actionTypeTitle(action),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = ActionTreeUtils.summarizeAction(action),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (action.supportsPostActionDelay()) {
            val postDelay = extractActionPostDelay(action)
            val isCustomOverride = postDelay is PostActionDelay.Override
            val delayPillText = formatPostDelayBadge(postDelay, globalDelay)
            Surface(
              shape = RoundedCornerShape(6.dp),
              color =
                if (isCustomOverride) {
                  MaterialTheme.colorScheme.secondaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {
              Text(
                text = delayPillText,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isCustomOverride) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isContainer) {
            IconButton(
              onClick = { onIntent(ScriptEditorUiIntent.ToggleActionExpanded(action.id)) },
              modifier = Modifier.size(48.dp).testTag("toggle_expand_${action.id}"),
            ) {
              Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Collapse branch" else "Expand branch",
              )
            }
          }

          Box {
            IconButton(
              onClick = { menuExpanded = true },
              modifier = Modifier.size(48.dp).testTag("action_menu_${action.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Action options",
              )
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false },
            ) {
              DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onIntent(ScriptEditorUiIntent.OpenEditActionSheet(action.id))
                },
              )
              DropdownMenuItem(
                text = { Text("Move Up") },
                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                enabled = indexInBranch > 0,
                onClick = {
                  menuExpanded = false
                  onIntent(ScriptEditorUiIntent.MoveActionUp(location, indexInBranch))
                },
              )
              DropdownMenuItem(
                text = { Text("Move Down") },
                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                enabled = indexInBranch < siblingCount - 1,
                onClick = {
                  menuExpanded = false
                  onIntent(ScriptEditorUiIntent.MoveActionDown(location, indexInBranch))
                },
              )
              HorizontalDivider()
              DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                colors =
                  MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                  ),
                onClick = {
                  menuExpanded = false
                  onIntent(ScriptEditorUiIntent.DeleteAction(action.id))
                },
              )
            }
          }
        }
      }

      when (action) {
        is ScriptAction.IfBlock -> {
          ConditionSummaryPill(
            conditionRoot = action.conditionRoot,
            prefix = "If",
            emptyText = "If no rules added yet — tap to set rule",
            onClick = { onIntent(ScriptEditorUiIntent.OpenEditActionSheet(action.id)) },
            testTag = "if_condition_pill_${action.id}",
          )

          if (action.isExpanded) {
            val showElseSection =
              action.id in revealedElseBlockIds || action.elseActions.isNotEmpty()

            // 1. THEN Branch ("If Yes, do these:") — always visible when expanded
            BranchSectionBlock(
              title = "If Yes, do these:",
              stepCount = action.thenActions.size,
              railColor = MaterialTheme.colorScheme.primary,
              badgeContainerColor = MaterialTheme.colorScheme.primaryContainer,
              badgeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              actions = action.thenActions,
              location = ActionBranchLocation(action.id, BranchKind.IF_THEN),
              stepPrefix = "$stepLabel.Y",
              nestingDepth = nestingDepth + 1,
              globalDelay = globalDelay,
              revealedElseBlockIds = revealedElseBlockIds,
              addStepTestTag = "add_then_step_${action.id}",
              onRemoveBranch = null,
              removeBranchTestTag = null,
              removeBranchContentDesc = null,
              onIntent = onIntent,
            )

            // 2. ELSE IF Branches — rendered in order
            action.elseIfBranches.forEachIndexed { branchIdx, branch ->
              key(branch.id) {
                val branchTitle =
                  if (branch.name.isNotBlank()) {
                    "Otherwise, if (${branch.name.trim()}):"
                  } else {
                    "Otherwise, if:"
                  }
                BranchSectionBlock(
                  title = branchTitle,
                  stepCount = branch.actions.size,
                  railColor = MaterialTheme.colorScheme.secondary,
                  badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                  badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  headerExtra = {
                    ConditionSummaryPill(
                      conditionRoot = branch.conditionRoot,
                      prefix = "When",
                      emptyText = "No rule set yet — tap to set condition",
                      onClick = {
                        onIntent(
                          ScriptEditorUiIntent.OpenEditElseIfConditionSheet(
                            ifBlockId = action.id,
                            branchId = branch.id,
                          ),
                        )
                      },
                      testTag = "edit_else_if_condition_${branch.id}",
                    )
                  },
                  actions = branch.actions,
                  location = ActionBranchLocation(action.id, BranchKind.IF_ELSE_IF, branch.id),
                  stepPrefix = "$stepLabel.E${branchIdx + 1}.",
                  nestingDepth = nestingDepth + 1,
                  globalDelay = globalDelay,
                  revealedElseBlockIds = revealedElseBlockIds,
                  addStepTestTag = "add_else_if_step_${branch.id}",
                  onRemoveBranch = {
                    onIntent(
                      ScriptEditorUiIntent.RemoveElseIfBranch(
                        ifBlockId = action.id,
                        branchId = branch.id,
                      ),
                    )
                  },
                  removeBranchTestTag = "remove_else_if_${branch.id}",
                  removeBranchContentDesc = "Remove Else If branch",
                  onIntent = onIntent,
                )
              }
            }

            // 3. ELSE Branch ("Otherwise (if none above matched):") — only when added/non-empty
            if (showElseSection) {
              BranchSectionBlock(
                title = "Otherwise (if none above matched):",
                stepCount = action.elseActions.size,
                railColor = MaterialTheme.colorScheme.tertiary,
                badgeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                badgeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                actions = action.elseActions,
                location = ActionBranchLocation(action.id, BranchKind.IF_ELSE),
                stepPrefix = "$stepLabel.O",
                nestingDepth = nestingDepth + 1,
                globalDelay = globalDelay,
                revealedElseBlockIds = revealedElseBlockIds,
                addStepTestTag = "add_else_step_${action.id}",
                onRemoveBranch = {
                  onIntent(ScriptEditorUiIntent.RemoveElseBranch(action.id))
                },
                removeBranchTestTag = "remove_else_branch_${action.id}",
                removeBranchContentDesc = "Remove Otherwise branch",
                onIntent = onIntent,
              )
            }

            // 4. Bottom compact tonal chips for adding Else If / Otherwise (Else)
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              TonalBranchChip(
                label = "+ Add \"Else If\"",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onIntent(ScriptEditorUiIntent.AddElseIfBranch(action.id)) },
                testTag = "add_else_if_chip_${action.id}",
              )
              if (!showElseSection) {
                TonalBranchChip(
                  label = "+ Add \"Otherwise (Else)\"",
                  containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                  contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                  onClick = { onIntent(ScriptEditorUiIntent.RevealElseBranch(action.id)) },
                  testTag = "add_else_chip_${action.id}",
                )
              }
            }
          }
        }

        is ScriptAction.LoopBlock -> {
          if (action.isExpanded) {
            BranchSectionBlock(
              title = "Repeat these steps:",
              stepCount = action.bodyActions.size,
              railColor = MaterialTheme.colorScheme.secondary,
              badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
              badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              actions = action.bodyActions,
              location = ActionBranchLocation(action.id, BranchKind.LOOP_BODY),
              stepPrefix = "$stepLabel.",
              nestingDepth = nestingDepth + 1,
              globalDelay = globalDelay,
              revealedElseBlockIds = revealedElseBlockIds,
              addStepTestTag = "add_loop_step_${action.id}",
              onRemoveBranch = null,
              removeBranchTestTag = null,
              removeBranchContentDesc = null,
              onIntent = onIntent,
            )
          }
        }

        else -> Unit
      }
    }
  }
}

@Composable
private fun ConditionSummaryPill(
  conditionRoot: ConditionGroup,
  prefix: String,
  emptyText: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val clause = PlainLanguageFormatter.formatConditionClause(conditionRoot)
  val swatchHex = PlainLanguageFormatter.extractFirstColorHex(conditionRoot)
  val swatchColor = swatchHex?.let(::parseEditorHexColorOrNull)
  val pillText =
    if (clause.isBlank()) {
      emptyText
    } else {
      "$prefix $clause"
    }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 40.dp)
        .clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (swatchColor != null) {
        Box(
          modifier =
            Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(swatchColor)
              .border(0.75.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
      }
      Text(
        text = pillText,
        style = MaterialTheme.typography.labelMedium,
        color =
          if (clause.isBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
          } else {
            MaterialTheme.colorScheme.onSurface
          },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Icon(
        imageVector = Icons.Rounded.Edit,
        contentDescription = "Edit condition",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

@Composable
private fun BranchSectionBlock(
  title: String,
  stepCount: Int,
  railColor: Color,
  badgeContainerColor: Color,
  badgeContentColor: Color,
  actions: List<ScriptAction>,
  location: ActionBranchLocation,
  stepPrefix: String,
  nestingDepth: Int,
  globalDelay: DelayConfig,
  revealedElseBlockIds: Set<String>,
  addStepTestTag: String,
  onRemoveBranch: (() -> Unit)?,
  removeBranchTestTag: String?,
  removeBranchContentDesc: String?,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  headerExtra: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier
          .width(3.dp)
          .fillMaxHeight()
          .padding(vertical = 2.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(railColor.copy(alpha = 0.75f)),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = badgeContainerColor,
          modifier = Modifier.weight(1f, fill = false),
        ) {
          Text(
            text = "$title ($stepCount)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = badgeContentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
        if (onRemoveBranch != null && removeBranchTestTag != null) {
          IconButton(
            onClick = onRemoveBranch,
            modifier = Modifier.size(48.dp).testTag(removeBranchTestTag),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = removeBranchContentDesc ?: "Remove branch",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      if (headerExtra != null) {
        headerExtra()
      }

      if (actions.isNotEmpty()) {
        val branchKey =
          "${location.parentActionId ?: "root"}_${location.branchKind.name}_${location.branchId ?: "main"}"
        InlineInsertDivider(
          label = "Insert",
          onClick = {
            onIntent(ScriptEditorUiIntent.OpenAddActionSheet(location = location, insertIndex = 0))
          },
          testTag = "insert_${branchKey}_0",
        )
        actions.forEachIndexed { idx, child ->
          key(child.id) {
            ActionTreeItemCard(
              action = child,
              stepLabel = "$stepPrefix${idx + 1}",
              location = location,
              indexInBranch = idx,
              siblingCount = actions.size,
              nestingDepth = nestingDepth,
              globalDelay = globalDelay,
              revealedElseBlockIds = revealedElseBlockIds,
              onIntent = onIntent,
            )
            if (idx < actions.lastIndex) {
              InlineInsertDivider(
                label = "Insert",
                onClick = {
                  onIntent(
                    ScriptEditorUiIntent.OpenAddActionSheet(
                      location = location,
                      insertIndex = idx + 1,
                    ),
                  )
                },
                testTag = "insert_${branchKey}_${idx + 1}",
              )
            }
          }
        }
      }

      FilledTonalButton(
        onClick = {
          onIntent(
            ScriptEditorUiIntent.OpenAddActionSheet(
              location = location,
              insertIndex = actions.size,
            ),
          )
        },
        colors =
          ButtonDefaults.filledTonalButtonColors(
            containerColor = badgeContainerColor.copy(alpha = 0.65f),
            contentColor = badgeContentColor,
          ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag(addStepTestTag),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add Step", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun TonalBranchChip(
  label: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = containerColor.copy(alpha = 0.85f),
    contentColor = contentColor,
    modifier =
      Modifier
        .minimumInteractiveComponentSize()
        .heightIn(min = 40.dp)
        .clip(RoundedCornerShape(999.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
      )
    }
  }
}

private fun extractActionPostDelay(action: ScriptAction): PostActionDelay =
  when (action) {
    is ScriptAction.Tap -> action.postDelay
    is ScriptAction.DoubleTap -> action.postDelay
    is ScriptAction.LongPress -> action.postDelay
    is ScriptAction.MultiTouch -> action.postDelay
    is ScriptAction.SwipeDrag -> action.postDelay
    is ScriptAction.InputText -> action.postDelay
    is ScriptAction.KeyEvent -> action.postDelay
    is ScriptAction.LaunchApp -> action.postDelay
    is ScriptAction.ForceStopApp -> action.postDelay
    is ScriptAction.ClipboardOp -> action.postDelay
    else -> PostActionDelay.Off
  }

private fun formatPostDelayBadge(postDelay: PostActionDelay, globalDelay: DelayConfig): String =
  when (postDelay) {
    PostActionDelay.Global -> "Wait (${ActionTreeUtils.formatDelayConfig(globalDelay)})"
    is PostActionDelay.Override -> "Custom Wait: ${ActionTreeUtils.formatDelayConfig(postDelay.config)}"
    PostActionDelay.Off -> "No Wait (0ms)"
  }
