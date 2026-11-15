package com.tapflow.core.database

import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.LogicOperator
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.log.ConditionEvalTrace
import com.tapflow.core.model.log.ExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TapFlowTypeConvertersTest {
  private val converters = TapFlowTypeConverters()

  @Test
  fun nestedScriptActionTreeRoundTripsCleanly() {
    val actions =
      listOf(
        ScriptAction.IfBlock(
          id = "if-1",
          name = "Check Loot",
          conditionRoot =
            ConditionGroup(
              id = "root",
              operator = LogicOperator.ANY,
              children =
                listOf(
                  ConditionLeaf(
                    id = "leaf-1",
                    predicate =
                      ConditionPredicate.ColorMatch(
                        targets = listOf(ColorTarget(100, 200, "#00E5FF", tolerance = 10)),
                      ),
                  ),
                ),
            ),
          thenActions =
            listOf(
              ScriptAction.Tap(
                id = "tap-1",
                x = NumericInput.Literal(100),
                y = NumericInput.VariableRef("target_y"),
              ),
            ),
          elseActions =
            listOf(
              ScriptAction.LoopBlock(
                id = "loop-1",
                mode = LoopMode.Count(NumericInput.Literal(3), exportIndexVarKey = "i"),
                bodyActions = listOf(ScriptAction.Delay("delay-1", DelayConfig.Fixed(200L))),
              ),
            ),
        ),
      )

    val encoded = converters.fromScriptActionList(actions)
    val decoded = converters.toScriptActionList(encoded)
    assertEquals(actions, decoded)
  }

  @Test
  fun variablesAndLogTracesRoundTripCleanly() {
    val vars =
      listOf(
        VariableDefinition(key = "counter", type = VariableType.NUMBER, defaultValue = "0"),
        VariableDefinition(key = "enabled", type = VariableType.BOOLEAN, defaultValue = "true"),
      )
    assertEquals(vars, converters.toVariableList(converters.fromVariableList(vars)))

    val entries =
      listOf(
        ActionLogEntry(
          id = "entry-1",
          stepLabel = "#01",
          actionType = "IfBlock",
          summary = "Check Loot",
          timestampMs = 1_000L,
          durationMs = 25L,
          status = ExecutionStatus.SUCCESS,
          conditionTrace =
            ConditionEvalTrace(
              nodeId = "root",
              label = "Root",
              passed = true,
              actualSummary = "#00E5FF",
              expectedSummary = "#00E5FF",
            ),
        ),
      )
    assertEquals(entries, converters.toActionLogEntryList(converters.fromActionLogEntryList(entries)))
  }

  @Test
  fun elseIfBranchesRoundTripAndLegacyIfBlockJsonDeserializesWithoutMigrationCrash() {
    val withElseIf =
      listOf(
        ScriptAction.IfBlock(
          id = "if-ei",
          name = "Popup Chain",
          conditionRoot = ConditionGroup(id = "g-if"),
          thenActions = listOf(ScriptAction.Delay(id = "d-then")),
          elseIfBranches =
            listOf(
              ScriptAction.ElseIfBranch(
                id = "ei-1",
                name = "Close Button",
                conditionRoot = ConditionGroup(id = "g-ei1"),
                actions = listOf(ScriptAction.Delay(id = "d-ei1")),
              ),
            ),
          elseActions = listOf(ScriptAction.Delay(id = "d-else")),
        ),
      )

    val encoded = converters.fromScriptActionList(withElseIf)
    val decoded = converters.toScriptActionList(encoded)
    assertEquals(withElseIf, decoded)

    // Legacy Room row without "elseIfBranches" field must deserialize with emptyList()
    val legacyJson =
      """[{"kind":"if_block","id":"if-legacy","name":"Old","conditionRoot":{"id":"g1","name":"When","operator":"ALL","children":[],"isExpanded":true},"thenActions":[],"elseActions":[],"isExpanded":true}]"""
    val decodedLegacy = converters.toScriptActionList(legacyJson)
    assertEquals(1, decodedLegacy.size)
    val legacyIf = decodedLegacy.first() as ScriptAction.IfBlock
    assertEquals(emptyList<ScriptAction.ElseIfBranch>(), legacyIf.elseIfBranches)
  }
}
