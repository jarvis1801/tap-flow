package com.tapflow.feature.editor

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.DurationInput
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionNode
import com.tapflow.core.model.condition.ConditionValidator
import com.tapflow.core.model.condition.LogicOperator

enum class BranchKind {
  ROOT,
  IF_THEN,
  IF_ELSE,
  LOOP_BODY,
}

@Immutable
data class ActionBranchLocation(
  val parentActionId: String? = null,
  val branchKind: BranchKind = BranchKind.ROOT,
)

@Immutable
data class RemovedActionRecord(
  val action: ScriptAction,
  val location: ActionBranchLocation,
  val index: Int,
)

enum class ActionCategoryGroup {
  TOUCH_GESTURES,
  FLOW_CONTROL,
  SYSTEM_HARDWARE,
  VARIABLE_OPS,
}

fun ScriptAction.supportsPostActionDelay(): Boolean =
  when (this) {
    is ScriptAction.Tap,
    is ScriptAction.DoubleTap,
    is ScriptAction.LongPress,
    is ScriptAction.MultiTouch,
    is ScriptAction.SwipeDrag,
    is ScriptAction.InputText,
    is ScriptAction.KeyEvent,
    is ScriptAction.LaunchApp,
    is ScriptAction.ForceStopApp -> true
    is ScriptAction.ClipboardOp -> operation == ClipboardActionType.PASTE
    is ScriptAction.IfBlock,
    is ScriptAction.LoopBlock,
    is ScriptAction.Delay,
    is ScriptAction.Break,
    is ScriptAction.Continue,
    is ScriptAction.EndScript,
    is ScriptAction.Vibrate,
    is ScriptAction.PlayAlertSound,
    is ScriptAction.VariableOp -> false
  }

fun ScriptAction.categoryGroup(): ActionCategoryGroup =
  when (this) {
    is ScriptAction.Tap,
    is ScriptAction.DoubleTap,
    is ScriptAction.LongPress,
    is ScriptAction.MultiTouch,
    is ScriptAction.SwipeDrag,
    is ScriptAction.InputText -> ActionCategoryGroup.TOUCH_GESTURES
    is ScriptAction.IfBlock,
    is ScriptAction.LoopBlock,
    is ScriptAction.Delay,
    is ScriptAction.Break,
    is ScriptAction.Continue,
    is ScriptAction.EndScript -> ActionCategoryGroup.FLOW_CONTROL
    is ScriptAction.KeyEvent,
    is ScriptAction.LaunchApp,
    is ScriptAction.ForceStopApp,
    is ScriptAction.ClipboardOp,
    is ScriptAction.Vibrate,
    is ScriptAction.PlayAlertSound -> ActionCategoryGroup.SYSTEM_HARDWARE
    is ScriptAction.VariableOp -> ActionCategoryGroup.VARIABLE_OPS
  }

object ActionTreeUtils {

  fun insertActionAt(
    actions: List<ScriptAction>,
    location: ActionBranchLocation,
    index: Int,
    newAction: ScriptAction,
  ): List<ScriptAction> {
    if (location.parentActionId == null || location.branchKind == BranchKind.ROOT) {
      val mutable = actions.toMutableList()
      mutable.add(index.coerceIn(0, mutable.size), newAction)
      return mutable
    }
    return actions.map { action ->
      if (action.id == location.parentActionId) {
        insertIntoContainer(action, location.branchKind, index, newAction)
      } else {
        recurseChildren(action) { branch ->
          insertActionAt(branch, location, index, newAction)
        }
      }
    }
  }

  fun replaceAction(
    actions: List<ScriptAction>,
    updatedAction: ScriptAction,
  ): List<ScriptAction> {
    return actions.map { action ->
      if (action.id == updatedAction.id) {
        updatedAction
      } else {
        recurseChildren(action) { branch -> replaceAction(branch, updatedAction) }
      }
    }
  }

  fun findActionById(actions: List<ScriptAction>, actionId: String): ScriptAction? {
    for (action in actions) {
      if (action.id == actionId) return action
      when (action) {
        is ScriptAction.IfBlock -> {
          findActionById(action.thenActions, actionId)?.let { return it }
          findActionById(action.elseActions, actionId)?.let { return it }
        }
        is ScriptAction.LoopBlock -> {
          findActionById(action.bodyActions, actionId)?.let { return it }
        }
        else -> Unit
      }
    }
    return null
  }

  fun deleteActionById(
    actions: List<ScriptAction>,
    actionId: String,
    currentLocation: ActionBranchLocation = ActionBranchLocation(),
  ): Pair<List<ScriptAction>, RemovedActionRecord?> {
    val directIdx = actions.indexOfFirst { it.id == actionId }
    if (directIdx >= 0) {
      val removed = actions[directIdx]
      val next = actions.filterNot { it.id == actionId }
      return next to RemovedActionRecord(removed, currentLocation, directIdx)
    }

    var foundRecord: RemovedActionRecord? = null
    val updated =
      actions.map { action ->
        when (action) {
          is ScriptAction.IfBlock -> {
            val (nextThen, thenRemoved) =
              deleteActionById(
                action.thenActions,
                actionId,
                ActionBranchLocation(action.id, BranchKind.IF_THEN),
              )
            if (thenRemoved != null) {
              foundRecord = thenRemoved
              action.copy(thenActions = nextThen)
            } else {
              val (nextElse, elseRemoved) =
                deleteActionById(
                  action.elseActions,
                  actionId,
                  ActionBranchLocation(action.id, BranchKind.IF_ELSE),
                )
              if (elseRemoved != null) {
                foundRecord = elseRemoved
                action.copy(elseActions = nextElse)
              } else {
                action
              }
            }
          }
          is ScriptAction.LoopBlock -> {
            val (nextBody, bodyRemoved) =
              deleteActionById(
                action.bodyActions,
                actionId,
                ActionBranchLocation(action.id, BranchKind.LOOP_BODY),
              )
            if (bodyRemoved != null) {
              foundRecord = bodyRemoved
              action.copy(bodyActions = nextBody)
            } else {
              action
            }
          }
          else -> action
        }
      }
    return updated to foundRecord
  }

  fun moveSibling(
    actions: List<ScriptAction>,
    location: ActionBranchLocation,
    index: Int,
    delta: Int,
  ): List<ScriptAction> {
    if (location.parentActionId == null || location.branchKind == BranchKind.ROOT) {
      return swapIndices(actions, index, index + delta)
    }
    return actions.map { action ->
      if (action.id == location.parentActionId) {
        when (action) {
          is ScriptAction.IfBlock ->
            when (location.branchKind) {
              BranchKind.IF_THEN ->
                action.copy(thenActions = swapIndices(action.thenActions, index, index + delta))
              BranchKind.IF_ELSE ->
                action.copy(elseActions = swapIndices(action.elseActions, index, index + delta))
              else -> action
            }
          is ScriptAction.LoopBlock ->
            if (location.branchKind == BranchKind.LOOP_BODY) {
              action.copy(bodyActions = swapIndices(action.bodyActions, index, index + delta))
            } else {
              action
            }
          else -> action
        }
      } else {
        recurseChildren(action) { branch -> moveSibling(branch, location, index, delta) }
      }
    }
  }

  fun toggleExpanded(actions: List<ScriptAction>, actionId: String): List<ScriptAction> {
    return actions.map { action ->
      when {
        action.id == actionId && action is ScriptAction.IfBlock ->
          action.copy(isExpanded = !action.isExpanded)
        action.id == actionId && action is ScriptAction.LoopBlock ->
          action.copy(isExpanded = !action.isExpanded)
        else -> recurseChildren(action) { branch -> toggleExpanded(branch, actionId) }
      }
    }
  }

  fun applyGlobalDelayToAllPhysicalActions(actions: List<ScriptAction>): List<ScriptAction> {
    return actions.map { action ->
      when (action) {
        is ScriptAction.Tap -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.DoubleTap -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.LongPress -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.MultiTouch -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.SwipeDrag -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.InputText -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.KeyEvent -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.LaunchApp -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.ForceStopApp -> action.copy(postDelay = PostActionDelay.Global)
        is ScriptAction.ClipboardOp ->
          if (action.operation == ClipboardActionType.PASTE) {
            action.copy(postDelay = PostActionDelay.Global)
          } else {
            action
          }
        is ScriptAction.IfBlock ->
          action.copy(
            thenActions = applyGlobalDelayToAllPhysicalActions(action.thenActions),
            elseActions = applyGlobalDelayToAllPhysicalActions(action.elseActions),
          )
        is ScriptAction.LoopBlock ->
          action.copy(bodyActions = applyGlobalDelayToAllPhysicalActions(action.bodyActions))
        is ScriptAction.Delay,
        is ScriptAction.Break,
        is ScriptAction.Continue,
        is ScriptAction.EndScript,
        is ScriptAction.Vibrate,
        is ScriptAction.PlayAlertSound,
        is ScriptAction.VariableOp -> action
      }
    }
  }

  fun formatNumericInput(input: NumericInput): String =
    when (input) {
      is NumericInput.Literal -> input.value.toString()
      is NumericInput.VariableRef -> "{${input.variableKey}}"
    }

  fun formatDurationInput(input: DurationInput): String =
    when (input) {
      is DurationInput.Fixed -> "${input.ms}ms"
      is DurationInput.RandomRange -> "${input.minMs}~${input.maxMs}ms"
      is DurationInput.VariableRef -> "{${input.variableKey}}ms"
    }

  fun formatDelayConfig(config: DelayConfig): String =
    when (config) {
      is DelayConfig.Fixed -> formatDurationCompact(config.durationMs)
      is DelayConfig.RandomRange ->
        "${formatDurationCompact(config.minMs)} ~ ${formatDurationCompact(config.maxMs)}"
      is DelayConfig.Deviation ->
        "${formatDurationCompact(config.baseMs)} ± ${formatDurationCompact(config.deltaMs)}"
    }

  fun formatDurationCompact(totalMs: Long): String {
    val ms = totalMs.coerceAtLeast(0L)
    if (ms < 1000L) return "${ms}ms"
    val totalSec = ms / 1000L
    val remMs = ms % 1000L
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    val parts = buildList {
      if (hours > 0L) add("${hours}h")
      if (mins > 0L) add("${mins}m")
      if (secs > 0L || remMs > 0L) {
        if (remMs > 0L) add("${secs}.${remMs / 100}s") else add("${secs}s")
      }
    }
    return parts.joinToString(" ").ifBlank { "${ms}ms" }
  }

  fun actionTypeTitle(action: ScriptAction): String =
    when (action) {
      is ScriptAction.Tap -> "Tap"
      is ScriptAction.DoubleTap -> "Double Tap"
      is ScriptAction.LongPress -> "Long Press"
      is ScriptAction.MultiTouch -> "Multi-touch"
      is ScriptAction.SwipeDrag -> "Swipe & Drag"
      is ScriptAction.InputText -> "Input Text"
      is ScriptAction.IfBlock -> "If Block"
      is ScriptAction.LoopBlock -> "Loop Block"
      is ScriptAction.Delay -> "Delay"
      is ScriptAction.Break -> "Break"
      is ScriptAction.Continue -> "Continue"
      is ScriptAction.EndScript -> "End Script"
      is ScriptAction.KeyEvent -> "Key Event"
      is ScriptAction.LaunchApp -> "Launch App"
      is ScriptAction.ForceStopApp -> "Force Stop App"
      is ScriptAction.ClipboardOp -> "Clipboard"
      is ScriptAction.Vibrate -> "Vibrate"
      is ScriptAction.PlayAlertSound -> "Alert Sound"
      is ScriptAction.SetVariable -> "Set Variable"
      is ScriptAction.MathOp -> "Math Op"
      is ScriptAction.RandomOp -> "Random Value"
      is ScriptAction.StringOp -> "String Op"
      is ScriptAction.BooleanOp -> "Boolean Op"
      is ScriptAction.TypeCastOp -> "Type Cast"
    }

  fun summarizeAction(action: ScriptAction): String =
    when (action) {
      is ScriptAction.Tap ->
        "(${formatNumericInput(action.x)}, ${formatNumericInput(action.y)}) • ${formatDurationInput(action.pressDuration)}" +
          if (action.repeatCount != NumericInput.Literal(1)) {
            " ×${formatNumericInput(action.repeatCount)}"
          } else {
            ""
          }
      is ScriptAction.DoubleTap ->
        "(${formatNumericInput(action.x)}, ${formatNumericInput(action.y)}) • ${action.tapIntervalMs}ms gap"
      is ScriptAction.LongPress ->
        "${action.mode.name} (${formatNumericInput(action.x)}, ${formatNumericInput(action.y)}) • ${formatDurationCompact(action.pressDurationMs)}"
      is ScriptAction.MultiTouch ->
        "${action.mode.name} • ${formatDurationCompact(action.durationMs)}"
      is ScriptAction.SwipeDrag ->
        "(${formatNumericInput(action.startX)},${formatNumericInput(action.startY)}) ➔ (${formatNumericInput(action.endX)},${formatNumericInput(action.endY)}) • ${action.trajectoryMode.name}"
      is ScriptAction.InputText ->
        "${action.engine.name}: \"${action.textTemplate.take(28)}\""
      is ScriptAction.IfBlock ->
        "${action.name} • ${ConditionValidator.formatNaturalLanguagePreview(action.conditionRoot)}"
      is ScriptAction.LoopBlock -> {
        val modeLabel =
          when (val m = action.mode) {
            is LoopMode.Count -> "Count ×${formatNumericInput(m.count)}"
            is LoopMode.WhileCondition -> "While (${ConditionValidator.formatNaturalLanguagePreview(m.conditionRoot)})"
            is LoopMode.UntilCondition -> "Until (${ConditionValidator.formatNaturalLanguagePreview(m.conditionRoot)})"
            LoopMode.Unlimited -> "Unlimited"
          }
        "${action.name} • $modeLabel (${action.bodyActions.size} steps)"
      }
      is ScriptAction.Delay -> "Wait ${formatDelayConfig(action.config)}"
      is ScriptAction.Break -> "Exit nearest enclosing loop"
      is ScriptAction.Continue -> "Jump to next loop iteration"
      is ScriptAction.EndScript -> "Stop script execution immediately"
      is ScriptAction.KeyEvent -> "System key: ${action.key.name}"
      is ScriptAction.LaunchApp -> action.appLabel.ifBlank { action.packageName }.ifBlank { "Select app" }
      is ScriptAction.ForceStopApp -> "am force-stop ${action.appLabel.ifBlank { action.packageName }}"
      is ScriptAction.ClipboardOp -> "${action.operation.name} ${action.targetVariableKey ?: action.valueTemplate}".trim()
      is ScriptAction.Vibrate -> "Pattern: ${action.pattern.name}"
      is ScriptAction.PlayAlertSound -> "Alert: ${action.soundUriOrType}"
      is ScriptAction.SetVariable -> "{${action.targetKey}} = \"${action.valueTemplate}\""
      is ScriptAction.MathOp -> "{${action.targetKey}} = ${action.leftOperand} ${action.operator.name} ${action.rightOperand}".trim()
      is ScriptAction.RandomOp -> "{${action.targetKey}} = random ${action.targetType.name}"
      is ScriptAction.StringOp -> "{${action.targetKey}} = ${action.operator.name}(${action.inputA})"
      is ScriptAction.BooleanOp -> "{${action.targetKey}} = ${action.operator.name}(${action.leftKey})"
      is ScriptAction.TypeCastOp -> "{${action.targetKey}} = ${action.operator.name}(${action.sourceKey})"
    }

  private fun insertIntoContainer(
    container: ScriptAction,
    branchKind: BranchKind,
    index: Int,
    newAction: ScriptAction,
  ): ScriptAction {
    return when (container) {
      is ScriptAction.IfBlock ->
        when (branchKind) {
          BranchKind.IF_THEN -> {
            val next = container.thenActions.toMutableList()
            next.add(index.coerceIn(0, next.size), newAction)
            container.copy(thenActions = next)
          }
          BranchKind.IF_ELSE -> {
            val next = container.elseActions.toMutableList()
            next.add(index.coerceIn(0, next.size), newAction)
            container.copy(elseActions = next)
          }
          else -> container
        }
      is ScriptAction.LoopBlock ->
        if (branchKind == BranchKind.LOOP_BODY) {
          val next = container.bodyActions.toMutableList()
          next.add(index.coerceIn(0, next.size), newAction)
          container.copy(bodyActions = next)
        } else {
          container
        }
      else -> container
    }
  }

  private inline fun recurseChildren(
    action: ScriptAction,
    transform: (List<ScriptAction>) -> List<ScriptAction>,
  ): ScriptAction {
    return when (action) {
      is ScriptAction.IfBlock ->
        action.copy(
          thenActions = transform(action.thenActions),
          elseActions = transform(action.elseActions),
        )
      is ScriptAction.LoopBlock ->
        action.copy(bodyActions = transform(action.bodyActions))
      else -> action
    }
  }

  private fun swapIndices(
    list: List<ScriptAction>,
    fromIndex: Int,
    toIndex: Int,
  ): List<ScriptAction> {
    if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return list
    val mutable = list.toMutableList()
    val tmp = mutable[fromIndex]
    mutable[fromIndex] = mutable[toIndex]
    mutable[toIndex] = tmp
    return mutable
  }
}

object ConditionTreeUtils {
  fun updateGroupOperator(
    root: ConditionGroup,
    groupId: String,
    operator: LogicOperator,
  ): ConditionGroup {
    if (root.id == groupId) return root.copy(operator = operator)
    return root.copy(
      children =
        root.children.map { child ->
          if (child is ConditionGroup) updateGroupOperator(child, groupId, operator) else child
        },
    )
  }

  fun toggleGroupExpanded(root: ConditionGroup, groupId: String): ConditionGroup {
    if (root.id == groupId) return root.copy(isExpanded = !root.isExpanded)
    return root.copy(
      children =
        root.children.map { child ->
          if (child is ConditionGroup) toggleGroupExpanded(child, groupId) else child
        },
    )
  }

  fun addNodeToGroup(
    root: ConditionGroup,
    targetGroupId: String,
    node: ConditionNode,
  ): ConditionGroup {
    if (node is ConditionGroup && !ConditionValidator.canAddSubGroup(root, targetGroupId)) {
      return root
    }
    if (root.id == targetGroupId) {
      return root.copy(children = root.children + node, isExpanded = true)
    }
    return root.copy(
      children =
        root.children.map { child ->
          if (child is ConditionGroup) addNodeToGroup(child, targetGroupId, node) else child
        },
    )
  }

  fun updateLeaf(root: ConditionGroup, updatedLeaf: ConditionLeaf): ConditionGroup {
    return root.copy(
      children =
        root.children.map { child ->
          when (child) {
            is ConditionLeaf -> if (child.id == updatedLeaf.id) updatedLeaf else child
            is ConditionGroup -> updateLeaf(child, updatedLeaf)
          }
        },
    )
  }

  fun removeNode(root: ConditionGroup, nodeId: String): ConditionGroup {
    if (root.id == nodeId) return root
    return root.copy(
      children =
        root.children
          .filterNot { it.id == nodeId }
          .map { child -> if (child is ConditionGroup) removeNode(child, nodeId) else child },
    )
  }
}
