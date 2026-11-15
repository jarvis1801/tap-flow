package com.tapflow.core.service.engine

import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.log.ConditionEvalTrace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IfElseIfExecutionTest {

  private fun fakeEvalResult(groupId: String, passed: Boolean): ConditionEvalResult =
    ConditionEvalResult(
      passed = passed,
      trace =
        ConditionEvalTrace(
          nodeId = groupId,
          label = groupId,
          passed = passed,
          actualSummary = if (passed) "1/1 matched" else "0/1 matched",
          expectedSummary = "ALL",
        ),
    )

  private fun sampleIfBlock(): ScriptAction.IfBlock =
    ScriptAction.IfBlock(
      id = "if1",
      name = "Reward Check",
      conditionRoot = ConditionGroup(id = "if_cond"),
      thenActions = listOf(ScriptAction.Delay(id = "then_act")),
      elseIfBranches =
        listOf(
          ScriptAction.ElseIfBranch(
            id = "ei1",
            name = "Popup",
            conditionRoot = ConditionGroup(id = "ei1_cond"),
            actions = listOf(ScriptAction.Delay(id = "ei1_act")),
          ),
          ScriptAction.ElseIfBranch(
            id = "ei2",
            conditionRoot = ConditionGroup(id = "ei2_cond"),
            actions = listOf(ScriptAction.Delay(id = "ei2_act")),
          ),
        ),
      elseActions = listOf(ScriptAction.Delay(id = "else_act")),
    )

  @Test
  fun ifTrue_executesThenAndShortCircuitsElseIfAndElse() = runTest {
    val evaluatedIds = mutableListOf<String>()
    val result =
      evaluateIfBlockChain(sampleIfBlock()) { group ->
        evaluatedIds += group.id
        fakeEvalResult(group.id, passed = true)
      }

    assertEquals(listOf("if_cond"), evaluatedIds)
    assertEquals("THEN", result.branchLabel)
    assertEquals(listOf("then_act"), result.actionsToRun.map { it.id })
    assertTrue(result.conditionTrace.passed)
    assertEquals("Matched If", result.conditionTrace.actualSummary)
    assertEquals(listOf("If"), result.conditionTrace.children.map { it.label })
  }

  @Test
  fun ifFalseAndFirstElseIfTrue_executesFirstElseIfAndShortCircuitsSecond() = runTest {
    val evaluatedIds = mutableListOf<String>()
    val result =
      evaluateIfBlockChain(sampleIfBlock()) { group ->
        evaluatedIds += group.id
        fakeEvalResult(group.id, passed = group.id == "ei1_cond")
      }

    assertEquals(listOf("if_cond", "ei1_cond"), evaluatedIds)
    assertEquals("Else If #1", result.branchLabel)
    assertEquals(listOf("ei1_act"), result.actionsToRun.map { it.id })
    assertTrue(result.conditionTrace.passed)
    assertEquals("Matched Else If #1", result.conditionTrace.actualSummary)
    assertEquals(
      listOf("If", "Else If #1 (Popup)"),
      result.conditionTrace.children.map { it.label },
    )
  }

  @Test
  fun ifFalseFirstElseIfFalseSecondElseIfTrue_executesSecondElseIf() = runTest {
    val evaluatedIds = mutableListOf<String>()
    val result =
      evaluateIfBlockChain(sampleIfBlock()) { group ->
        evaluatedIds += group.id
        fakeEvalResult(group.id, passed = group.id == "ei2_cond")
      }

    assertEquals(listOf("if_cond", "ei1_cond", "ei2_cond"), evaluatedIds)
    assertEquals("Else If #2", result.branchLabel)
    assertEquals(listOf("ei2_act"), result.actionsToRun.map { it.id })
    assertTrue(result.conditionTrace.passed)
    assertEquals("Matched Else If #2", result.conditionTrace.actualSummary)
    assertEquals(
      listOf("If", "Else If #1 (Popup)", "Else If #2"),
      result.conditionTrace.children.map { it.label },
    )
  }

  @Test
  fun allConditionsFalse_fallsBackToElseBranch() = runTest {
    val evaluatedIds = mutableListOf<String>()
    val result =
      evaluateIfBlockChain(sampleIfBlock()) { group ->
        evaluatedIds += group.id
        fakeEvalResult(group.id, passed = false)
      }

    assertEquals(listOf("if_cond", "ei1_cond", "ei2_cond"), evaluatedIds)
    assertEquals("ELSE", result.branchLabel)
    assertEquals(listOf("else_act"), result.actionsToRun.map { it.id })
    assertFalse(result.conditionTrace.passed)
    assertEquals("No rule matched -> Otherwise (Else)", result.conditionTrace.actualSummary)
    assertEquals(
      listOf("If", "Else If #1 (Popup)", "Else If #2"),
      result.conditionTrace.children.map { it.label },
    )
  }
}
