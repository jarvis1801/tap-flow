package com.tapflow.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LongPressMode
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.LoopTimeoutAction
import com.tapflow.core.model.action.MultiTouchMode
import com.tapflow.core.model.action.PostTextActionKey
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ColorMatchMode
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionMatchRule
import com.tapflow.core.model.condition.ConditionNode
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.LogicOperator

@Composable
internal fun FlowNodeDetailDialog(
  node: FlowNode,
  scriptTitle: String,
  actionCount: Int,
  globalDelay: DelayConfig,
  onDismiss: () -> Unit,
) {
  val dialogTitle =
    when (node) {
      is FlowNode.StartAnchor -> stringResource(R.string.flow_start_label)
      is FlowNode.EndAnchor -> stringResource(R.string.flow_end_label)
      is FlowNode.EmptyPlaceholder -> stringResource(R.string.flow_no_steps_yet)
      is FlowNode.ActionCard -> node.title
      is FlowNode.DecisionCard -> node.title
      is FlowNode.ElseHeaderPill -> stringResource(R.string.flow_else_header)
      is FlowNode.LoopHeaderCard -> node.title
      is FlowNode.EarlyTerminationPill ->
        when (node.terminationType) {
          FlowTerminationType.BREAK -> stringResource(R.string.flow_break_loop)
          FlowTerminationType.CONTINUE -> stringResource(R.string.flow_next_cycle)
          FlowTerminationType.END_SCRIPT -> stringResource(R.string.flow_end_script)
        }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("flow_node_detail_dialog"),
    icon = {
      when (node) {
        is FlowNode.ActionCard ->
          ActionIconBadge(
            icon = node.action.actionIcon(),
            category = node.action.categoryGroup(),
            boxSize = 36.dp,
            iconSize = 20.dp,
            cornerRadius = 10.dp,
          )
        is FlowNode.DecisionCard ->
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.AltRoute,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        is FlowNode.LoopHeaderCard ->
          Icon(
            imageVector = Icons.Rounded.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
        is FlowNode.StartAnchor ->
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        is FlowNode.EndAnchor ->
          Icon(
            imageVector = Icons.Rounded.Stop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        else ->
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
      }
    },
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = dialogTitle,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = stringResource(R.string.flow_read_only_badge),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    text = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (node) {
          is FlowNode.StartAnchor -> {
            DetailSectionHeader(stringResource(R.string.flow_detail_parameters_title))
            DetailKeyValueRow("Script", scriptTitle.ifBlank { "Untitled Script" })
            DetailKeyValueRow("Total Steps", "$actionCount")
            DetailKeyValueRow(
              "Global Delay",
              ActionTreeUtils.formatDelayConfig(globalDelay),
            )
          }

          is FlowNode.EndAnchor -> {
            DetailSectionHeader(stringResource(R.string.flow_detail_parameters_title))
            DetailKeyValueRow("Step", "Normal script completion")
            DetailKeyValueRow("Total Steps", "$actionCount")
          }

          is FlowNode.EmptyPlaceholder -> {
            Text(
              text =
                if (node.isScriptEmpty) {
                  "This script currently has no actions."
                } else {
                  "No actions have been added to this branch yet."
                },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          is FlowNode.DecisionCard -> {
            DetailSectionHeader(stringResource(R.string.flow_detail_conditions_title))
            ConditionGroupReadOnlyTree(group = node.conditionRoot, depth = 0)
            HorizontalDivider()
            DetailSectionHeader(stringResource(R.string.flow_detail_parameters_title))
            DetailKeyValueRow(
              "Branch Type",
              if (node.isElseIf) "If not, check (Else If)" else "Primary If Rule",
            )
            DetailKeyValueRow("Steps Inside Branch", "${node.branchActionCount}")
            if (!node.isElseIf) {
              DetailKeyValueRow("Else-If Branches", "${node.elseIfCount}")
              DetailKeyValueRow("If-None-Match Steps", "${node.elseActionCount}")
            }
            DetailSectionHeader(stringResource(R.string.flow_detail_post_delay_title))
            DetailKeyValueRow("Timing", "None (Flow Control)")
          }

          is FlowNode.ElseHeaderPill -> {
            DetailSectionHeader(stringResource(R.string.flow_detail_parameters_title))
            DetailKeyValueRow("Parent Rule", node.parentIfName.ifBlank { "If Rule" })
            DetailKeyValueRow("Runs When", "None of the preceding If / Else-If rules matched")
            DetailKeyValueRow("Steps Inside Branch", "${node.branchActionCount}")
          }

          is FlowNode.LoopHeaderCard -> {
            val loop = node.loopAction
            when (val m = loop.mode) {
              is LoopMode.WhileCondition -> {
                DetailSectionHeader(stringResource(R.string.flow_detail_conditions_title))
                ConditionGroupReadOnlyTree(group = m.conditionRoot, depth = 0)
                HorizontalDivider()
              }
              is LoopMode.UntilCondition -> {
                DetailSectionHeader(stringResource(R.string.flow_detail_conditions_title))
                ConditionGroupReadOnlyTree(group = m.conditionRoot, depth = 0)
                HorizontalDivider()
              }
              else -> Unit
            }
            DetailSectionHeader(stringResource(R.string.flow_detail_parameters_title))
            LoopDetailsSection(loop = loop)
            DetailSectionHeader(stringResource(R.string.flow_detail_post_delay_title))
            DetailKeyValueRow("Timing", "None (Flow Control)")
          }

          is FlowNode.EarlyTerminationPill -> {
            DetailSectionHeader(stringResource(R.string.flow_detail_parameters_title))
            DetailKeyValueRow("Action", ActionTreeUtils.actionTypeTitle(node.action))
            DetailKeyValueRow("Behavior", ActionTreeUtils.summarizeAction(node.action))
            DetailSectionHeader(stringResource(R.string.flow_detail_post_delay_title))
            DetailKeyValueRow("Timing", "None (Flow Control)")
          }

          is FlowNode.ActionCard -> {
            ActionFullParametersSection(action = node.action, globalDelay = globalDelay)
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("flow_detail_close_button"),
      ) {
        Text(stringResource(R.string.flow_detail_dialog_close))
      }
    },
  )
}

@Composable
private fun DetailSectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
private fun DetailKeyValueRow(
  label: String,
  value: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(0.4f),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(0.6f),
    )
  }
}

@Composable
private fun LoopDetailsSection(loop: ScriptAction.LoopBlock) {
  val modeLabel =
    when (val m = loop.mode) {
      is LoopMode.Count -> "Repeat ${ActionTreeUtils.formatNumericInput(m.count)}×"
      is LoopMode.WhileCondition -> "Repeat While condition matches"
      is LoopMode.UntilCondition -> "Repeat Until condition matches"
      LoopMode.Unlimited -> "Repeat Forever"
    }
  DetailKeyValueRow("Name", loop.name.ifBlank { "Loop" })
  DetailKeyValueRow("Mode", modeLabel)
  val countMode = loop.mode as? LoopMode.Count
  if (countMode?.exportIndexVarKey != null) {
    DetailKeyValueRow("Index Variable", "{${countMode.exportIndexVarKey}}")
  }
  DetailKeyValueRow("Body Steps", "${loop.bodyActions.size} (${ActionTreeUtils.countAllActions(loop.bodyActions)} total)")
  DetailKeyValueRow(
    "Cycle Interval",
    ActionTreeUtils.formatDelayConfig(loop.safeguards.intervalDelay),
  )
  DetailKeyValueRow("Max Iterations", "${loop.safeguards.maxIterations}")
  DetailKeyValueRow(
    "Max Timeout",
    ActionTreeUtils.formatDurationCompact(loop.safeguards.maxTimeoutMs),
  )
  DetailKeyValueRow(
    "On Timeout",
    when (loop.safeguards.onTimeout) {
      LoopTimeoutAction.BREAK_AND_CONTINUE -> "Exit loop & continue"
      LoopTimeoutAction.STOP_AND_ALERT -> "Stop script & alert"
    },
  )
}

@Composable
private fun ActionFullParametersSection(
  action: ScriptAction,
  globalDelay: DelayConfig,
) {
  if (action is ScriptAction.LongPress &&
    action.mode == LongPressMode.HOLD_UNTIL &&
    action.holdUntilCondition != null
  ) {
    DetailSectionHeader(stringResource(R.string.flow_detail_conditions_title))
    ConditionGroupReadOnlyTree(group = action.holdUntilCondition!!, depth = 0)
    HorizontalDivider()
  }

  DetailSectionHeader(stringResource(R.string.flow_detail_parameters_title))
  when (action) {
    is ScriptAction.Tap -> {
      DetailKeyValueRow(
        "Coordinates",
        "(${ActionTreeUtils.formatNumericInput(action.x)}, ${ActionTreeUtils.formatNumericInput(action.y)})",
      )
      DetailKeyValueRow("Random Jitter", "${action.jitterPx} px")
      DetailKeyValueRow("Hold Duration", ActionTreeUtils.formatDurationInput(action.pressDuration))
      DetailKeyValueRow("Repeat Count", ActionTreeUtils.formatNumericInput(action.repeatCount))
      if (action.repeatCount != com.tapflow.core.model.common.NumericInput.Literal(1)) {
        DetailKeyValueRow(
          "Repeat Interval",
          ActionTreeUtils.formatDurationInput(action.repeatInterval),
        )
      }
    }

    is ScriptAction.DoubleTap -> {
      DetailKeyValueRow(
        "Coordinates",
        "(${ActionTreeUtils.formatNumericInput(action.x)}, ${ActionTreeUtils.formatNumericInput(action.y)})",
      )
      DetailKeyValueRow("Random Jitter", "${action.jitterPx} px")
      DetailKeyValueRow("Press Duration", "${action.pressDurationMs}ms")
      DetailKeyValueRow("Gap Between Taps", "${action.tapIntervalMs}ms")
    }

    is ScriptAction.LongPress -> {
      DetailKeyValueRow("Mode", action.mode.name)
      DetailKeyValueRow(
        "Coordinates",
        "(${ActionTreeUtils.formatNumericInput(action.x)}, ${ActionTreeUtils.formatNumericInput(action.y)})",
      )
      DetailKeyValueRow("Random Jitter", "${action.jitterPx} px")
      DetailKeyValueRow(
        "Hold Duration",
        ActionTreeUtils.formatDurationCompact(action.pressDurationMs),
      )
      if (action.mode == LongPressMode.HOLD_UNTIL) {
        DetailKeyValueRow(
          "Hold Timeout",
          ActionTreeUtils.formatDurationCompact(action.holdTimeoutMs),
        )
      }
      if (action.microMovementJitter) {
        DetailKeyValueRow("Micro-Movement", "${action.microJitterAmplitudePx} px")
      }
    }

    is ScriptAction.MultiTouch -> {
      DetailKeyValueRow(
        "Mode",
        when (action.mode) {
          MultiTouchMode.CUSTOM_POINTS -> "Custom Multi-Finger (${action.customPoints.size} points)"
          MultiTouchMode.PINCH_SPREAD -> "Pinch / Spread"
          MultiTouchMode.ROTATE -> "Rotate"
        },
      )
      DetailKeyValueRow("Duration", ActionTreeUtils.formatDurationCompact(action.durationMs))
      if (action.mode == MultiTouchMode.CUSTOM_POINTS) {
        action.customPoints.forEachIndexed { idx, pt ->
          DetailKeyValueRow(
            "Finger #${idx + 1}",
            "(${pt.x}, ${pt.y}) • hold ${pt.pressDurationMs}ms • stagger ${pt.staggerDelayMs}ms",
          )
        }
      } else {
        DetailKeyValueRow("Center", "(${action.centerX}, ${action.centerY})")
        if (action.mode == MultiTouchMode.PINCH_SPREAD) {
          DetailKeyValueRow(
            "Distance",
            "${action.startDistancePx} px ➔ ${action.endDistancePx} px",
          )
        } else {
          DetailKeyValueRow(
            "Angle / Radius",
            "${action.startAngleDeg}° ➔ ${action.endAngleDeg}° (${action.radiusPx} px)",
          )
        }
      }
    }

    is ScriptAction.SwipeDrag -> {
      DetailKeyValueRow(
        "Start",
        "(${ActionTreeUtils.formatNumericInput(action.startX)}, ${ActionTreeUtils.formatNumericInput(action.startY)}) ±${action.startJitterPx}px",
      )
      DetailKeyValueRow(
        "End",
        "(${ActionTreeUtils.formatNumericInput(action.endX)}, ${ActionTreeUtils.formatNumericInput(action.endY)}) ±${action.endJitterPx}px",
      )
      DetailKeyValueRow("Duration", ActionTreeUtils.formatDurationCompact(action.durationMs))
      DetailKeyValueRow(
        "Trajectory",
        PlainLanguageFormatter.swipeStyleLabel(action.trajectoryMode),
      )
      if (action.holdBeforeDragMs > 0L || action.holdAfterDragMs > 0L) {
        DetailKeyValueRow(
          "Hold Before / After",
          "${action.holdBeforeDragMs}ms / ${action.holdAfterDragMs}ms",
        )
      }
      if (action.multiPointPath.isNotEmpty()) {
        DetailKeyValueRow(
          "Path Waypoints",
          action.multiPointPath.joinToString(" ➔ ") { "(${it.x},${it.y})" },
        )
      }
    }

    is ScriptAction.InputText -> {
      DetailKeyValueRow("Engine", PlainLanguageFormatter.textEngineLabel(action.engine))
      if (action.tapToFocus) {
        DetailKeyValueRow(
          "Tap to Focus",
          if (action.focusX != null && action.focusY != null) {
            "(${action.focusX}, ${action.focusY})"
          } else {
            "Enabled"
          },
        )
      }
      DetailKeyValueRow("Clear Existing", if (action.clearExistingText) "Yes" else "No")
      if (action.postActionKey != PostTextActionKey.NONE) {
        DetailKeyValueRow("Post-Input Key", action.postActionKey.name)
      }
      DetailKeyValueRow("Auto-Hide Keyboard", if (action.autoHideKeyboard) "Yes" else "No")
      Text(
        text = "Text Template:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = action.textTemplate.ifEmpty { "(empty)" },
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(10.dp),
        )
      }
    }

    is ScriptAction.Delay -> {
      DetailKeyValueRow("Delay Config", ActionTreeUtils.formatDelayConfig(action.config))
    }

    is ScriptAction.KeyEvent -> {
      DetailKeyValueRow("System Key", PlainLanguageFormatter.systemKeyLabel(action.key))
    }

    is ScriptAction.LaunchApp -> {
      DetailKeyValueRow("App Name", action.appLabel.ifBlank { "Unknown" })
      DetailKeyValueRow("Package", action.packageName.ifBlank { "(not selected)" })
    }

    is ScriptAction.ForceStopApp -> {
      DetailKeyValueRow("App Name", action.appLabel.ifBlank { "Unknown" })
      DetailKeyValueRow("Package", action.packageName.ifBlank { "(not selected)" })
    }

    is ScriptAction.ClipboardOp -> {
      DetailKeyValueRow("Operation", PlainLanguageFormatter.clipboardOpLabel(action.operation))
      if (action.targetVariableKey != null) {
        DetailKeyValueRow("Target Variable", "{${action.targetVariableKey}}")
      }
      if (action.valueTemplate.isNotEmpty() || action.operation == ClipboardActionType.COPY) {
        DetailKeyValueRow("Text Template", action.valueTemplate.ifEmpty { "(empty)" })
      }
    }

    is ScriptAction.Vibrate -> {
      DetailKeyValueRow("Pattern", PlainLanguageFormatter.vibratePatternLabel(action.pattern))
      if (action.customPatternMs.isNotEmpty()) {
        DetailKeyValueRow("Timings (ms)", action.customPatternMs.joinToString(", "))
      }
    }

    is ScriptAction.PlayAlertSound -> {
      DetailKeyValueRow("Sound", action.soundUriOrType)
    }

    is ScriptAction.SetVariable -> {
      DetailKeyValueRow("Target Variable", "{${action.targetKey}}")
      DetailKeyValueRow("Value Template", action.valueTemplate)
    }

    is ScriptAction.MathOp -> {
      DetailKeyValueRow("Target Variable", "{${action.targetKey}}")
      DetailKeyValueRow("Operator", PlainLanguageFormatter.mathOpLabel(action.operator))
      DetailKeyValueRow("Left Operand", action.leftOperand)
      if (action.rightOperand.isNotBlank()) {
        DetailKeyValueRow("Right Operand", action.rightOperand)
      }
    }

    is ScriptAction.RandomOp -> {
      DetailKeyValueRow("Target Variable", "{${action.targetKey}}")
      DetailKeyValueRow("Type", PlainLanguageFormatter.randomTargetLabel(action.targetType))
      DetailKeyValueRow("Range", "${action.minValue} .. ${action.maxValue}")
    }

    is ScriptAction.StringOp -> {
      DetailKeyValueRow("Target Variable", "{${action.targetKey}}")
      DetailKeyValueRow("Operation", PlainLanguageFormatter.stringOpLabel(action.operator))
      DetailKeyValueRow("Input A", action.inputA)
      if (action.inputB.isNotBlank()) {
        DetailKeyValueRow("Input B", action.inputB)
      }
    }

    is ScriptAction.BooleanOp -> {
      DetailKeyValueRow("Target Variable", "{${action.targetKey}}")
      DetailKeyValueRow("Operation", PlainLanguageFormatter.booleanOpLabel(action.operator))
      DetailKeyValueRow("Left Key", "{${action.leftKey}}")
      if (!action.rightKey.isNullOrBlank()) {
        DetailKeyValueRow("Right Key", "{${action.rightKey}}")
      }
    }

    is ScriptAction.TypeCastOp -> {
      DetailKeyValueRow("Target Variable", "{${action.targetKey}}")
      DetailKeyValueRow("Conversion", PlainLanguageFormatter.typeCastLabel(action.operator))
      DetailKeyValueRow("Source Key", "{${action.sourceKey}}")
    }

    is ScriptAction.IfBlock,
    is ScriptAction.LoopBlock,
    is ScriptAction.Break,
    is ScriptAction.Continue,
    is ScriptAction.EndScript -> Unit
  }

  HorizontalDivider()
  DetailSectionHeader(stringResource(R.string.flow_detail_post_delay_title))
  val postDelay = ScriptFlowLayoutEngine.actionPostDelayConfig(action)
  when (postDelay) {
    null -> {
      DetailKeyValueRow("Mode", "None")
      DetailKeyValueRow("Resolved Delay", "0ms (Does not apply post-action delay)")
    }
    PostActionDelay.Global -> {
      DetailKeyValueRow("Mode", "Global")
      DetailKeyValueRow(
        "Resolved Delay",
        ActionTreeUtils.formatDelayConfig(globalDelay),
      )
    }
    is PostActionDelay.Override -> {
      DetailKeyValueRow("Mode", "Override")
      DetailKeyValueRow(
        "Resolved Delay",
        ActionTreeUtils.formatDelayConfig(postDelay.config),
      )
    }
    PostActionDelay.Off -> {
      DetailKeyValueRow("Mode", "Off")
      DetailKeyValueRow(
        "Resolved Delay",
        if (ScriptFlowLayoutEngine.isEffectivelyZero(globalDelay)) {
          "0ms (Off)"
        } else {
          "0ms (Bypasses ${ActionTreeUtils.formatDelayConfig(globalDelay)} Global Delay)"
        },
      )
    }
  }
}

@Composable
private fun ConditionGroupReadOnlyTree(
  group: ConditionGroup,
  depth: Int,
) {
  val opHeader =
    when (group.operator) {
      LogicOperator.ALL -> "Match ALL (AND)"
      LogicOperator.ANY -> "Match ANY (OR)"
      LogicOperator.NONE -> "Match NONE (NOT)"
    }
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().padding(start = (depth * 10).dp),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = if (group.name.isNotBlank() && group.name != "Condition Group") "${group.name} • $opHeader" else opHeader,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
      )
      if (group.children.isEmpty()) {
        Text(
          text = "No rules configured in this group.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        group.children.forEach { child: ConditionNode ->
          when (child) {
            is ConditionGroup -> ConditionGroupReadOnlyTree(group = child, depth = depth + 1)
            is ConditionLeaf -> ConditionLeafReadOnlyRow(leaf = child)
          }
        }
      }
    }
  }
}

@Composable
private fun ConditionLeafReadOnlyRow(leaf: ConditionLeaf) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      val headerPrefix = if (leaf.inverted) "NOT " else ""
      when (val p = leaf.predicate) {
        is ConditionPredicate.ColorMatch -> {
          val modeLabel =
            when (p.mode) {
              ColorMatchMode.SINGLE_POINT -> "Color Match (Single Point)"
              ColorMatchMode.MULTI_POINT -> "Color Match (${p.targets.size} Points, ${p.rule.name})"
              ColorMatchMode.REGION -> "Color Region (${p.rule.name})"
            }
          Text(
            text = "$headerPrefix$modeLabel",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          if (p.region != null) {
            Text(
              text = "Area: (${p.region!!.x1}, ${p.region!!.y1}) – (${p.region!!.x2}, ${p.region!!.y2})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (p.rule == ConditionMatchRule.PERCENT_MATCH) {
            Text(
              text = "Threshold: ${p.matchPercentThreshold}%",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          p.targets.forEach { target ->
            val swatchColor = parseHexColorOrNull(target.hexColor)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (swatchColor != null) {
                Box(
                  modifier =
                    Modifier
                      .size(16.dp)
                      .clip(CircleShape)
                      .background(swatchColor)
                      .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
              }
              Text(
                text = "(${target.x}, ${target.y}) • ${target.hexColor.uppercase()} (${PlainLanguageFormatter.friendlyColorName(target.hexColor)}) • tol ±${target.tolerance}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }

        is ConditionPredicate.ImageMatch -> {
          val fileName =
            p.templatePngPath.substringAfterLast('/').substringAfterLast('\\').ifBlank { "Template Image" }
          Text(
            text = "${headerPrefix}Image Match: $fileName",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Algorithm: ${p.algorithm.name} • Similarity ≥ ${(p.similarityThreshold * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (p.searchRoi != null) {
            val roi = p.searchRoi!!
            Text(
              text = "Search ROI: (${roi.x1}, ${roi.y1}) – (${roi.x2}, ${roi.y2})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        is ConditionPredicate.AppMatch -> {
          val appLabel = p.appLabel.ifBlank { p.packageName }.ifBlank { "(not selected)" }
          Text(
            text = "${headerPrefix}App Match: $appLabel",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
          )
          if (p.packageName.isNotBlank()) {
            Text(
              text = p.packageName,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        is ConditionPredicate.VariableCompare -> {
          Text(
            text = "${headerPrefix}${PlainLanguageFormatter.formatLeafClause(leaf)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }
}

private fun parseHexColorOrNull(hex: String): Color? {
  val clean = hex.trim().removePrefix("#")
  if (clean.length != 6 && clean.length != 8) return null
  val raw = clean.toLongOrNull(16) ?: return null
  val argb = if (clean.length == 6) (0xFF000000L or raw) else raw
  return Color(argb)
}
