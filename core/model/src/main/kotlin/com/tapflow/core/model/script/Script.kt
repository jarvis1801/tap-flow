package com.tapflow.core.model.script

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionNode
import com.tapflow.core.model.condition.ConditionPredicate
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Script(
  val id: String,
  val title: String,
  val description: String = "",
  val tags: List<String> = emptyList(),
  val globalDelay: DelayConfig = DelayConfig.Fixed(0L),
  val variables: List<VariableDefinition> = emptyList(),
  val actions: List<ScriptAction> = emptyList(),
  val createdAtMs: Long = 0L,
  val updatedAtMs: Long = 0L,
) {
  fun requiresVision(): Boolean = actions.any(::actionRequiresVision)

  fun totalActionCount(): Int = actions.sumOf(::countActionTree)

  private fun countActionTree(action: ScriptAction): Int {
    return 1 +
      when (action) {
        is ScriptAction.IfBlock ->
          action.thenActions.sumOf(::countActionTree) +
            action.elseIfBranches.sumOf { branch -> branch.actions.sumOf(::countActionTree) } +
            action.elseActions.sumOf(::countActionTree)
        is ScriptAction.LoopBlock -> action.bodyActions.sumOf(::countActionTree)
        else -> 0
      }
  }

  private fun actionRequiresVision(action: ScriptAction): Boolean {
    return when (action) {
      is ScriptAction.IfBlock ->
        conditionRequiresVision(action.conditionRoot) ||
          action.thenActions.any(::actionRequiresVision) ||
          action.elseIfBranches.any { branch ->
            conditionRequiresVision(branch.conditionRoot) ||
              branch.actions.any(::actionRequiresVision)
          } ||
          action.elseActions.any(::actionRequiresVision)
      is ScriptAction.LoopBlock -> {
        val loopCond =
          when (val m = action.mode) {
            is LoopMode.WhileCondition -> conditionRequiresVision(m.conditionRoot)
            is LoopMode.UntilCondition -> conditionRequiresVision(m.conditionRoot)
            else -> false
          }
        loopCond || action.bodyActions.any(::actionRequiresVision)
      }
      is ScriptAction.LongPress ->
        action.holdUntilCondition?.let(::conditionRequiresVision) == true
      else -> false
    }
  }

  private fun conditionRequiresVision(node: ConditionNode): Boolean {
    return when (node) {
      is ConditionGroup -> node.children.any(::conditionRequiresVision)
      is ConditionLeaf ->
        node.predicate is ConditionPredicate.ColorMatch ||
          node.predicate is ConditionPredicate.ImageMatch
    }
  }
}
