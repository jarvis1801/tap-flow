package com.tapflow.feature.editor

import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.script.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptFlowLayoutEngineTest {

  private fun assertNoNodeOverlaps(layout: FlowLayoutResult) {
    val nodes = layout.nodes
    for (i in nodes.indices) {
      for (j in i + 1 until nodes.size) {
        val a = nodes[i]
        val b = nodes[j]
        val overlapX = a.x < b.x + b.width && a.x + a.width > b.x
        val overlapY = a.y < b.y + b.height && a.y + a.height > b.y
        assertFalse(
          "Nodes ${a.id} (${a.x},${a.y},${a.width}x${a.height}) and ${b.id} (${b.x},${b.y},${b.width}x${b.height}) overlapped!",
          overlapX && overlapY,
        )
      }
    }
  }

  @Test
  fun emptyScript_producesStartPlaceholderEndChain() {
    val layout = ScriptFlowLayoutEngine.computeLayout(emptyList())
    assertEquals(3, layout.nodes.size)
    assertTrue(layout.nodes[0] is FlowNode.StartAnchor)
    val placeholder = layout.nodes[1] as FlowNode.EmptyPlaceholder
    assertTrue(placeholder.isScriptEmpty)
    assertTrue(layout.nodes[2] is FlowNode.EndAnchor)
    assertEquals(2, layout.edges.size)
    assertNoNodeOverlaps(layout)
  }

  @Test
  fun ifElseIfElseLadder_hasZeroOverlapAndTwoExitsPerDecision() {
    val nestedIf =
      ScriptAction.IfBlock(
        id = "nested_if",
        conditionRoot = ConditionGroup("ng1"),
        thenActions = listOf(ScriptAction.Tap(id = "n_tap", x = NumericInput.Literal(10), y = NumericInput.Literal(20))),
        elseActions = listOf(ScriptAction.Delay(id = "n_del", config = DelayConfig.Fixed(500L))),
      )
    val rootIf =
      ScriptAction.IfBlock(
        id = "root_if",
        conditionRoot =
          ConditionGroup(
            id = "g1",
            children =
              listOf(
                ConditionLeaf(
                  id = "l1",
                  predicate = ConditionPredicate.ColorMatch(targets = listOf(ColorTarget(100, 200, "#FF0000"))),
                ),
              ),
          ),
        thenActions = listOf(nestedIf),
        elseIfBranches =
          listOf(
            ScriptAction.ElseIfBranch(
              id = "ei_1",
              name = "Skip",
              conditionRoot = ConditionGroup("g2"),
              actions = listOf(ScriptAction.Tap(id = "ei_tap", x = NumericInput.Literal(300), y = NumericInput.Literal(400))),
            ),
          ),
        elseActions =
          listOf(
            ScriptAction.Delay(id = "else_del", config = DelayConfig.Fixed(200L)),
          ),
      )

    val layout = ScriptFlowLayoutEngine.computeLayout(listOf(rootIf))
    assertNoNodeOverlaps(layout)

    // Verify cascading stepped ladder: ElseIf decision Y > root If decision Y, and Else header Y > ElseIf decision Y
    val rootDec = layout.nodes.filterIsInstance<FlowNode.DecisionCard>().first { it.id == "if_dec_root_if" }
    val eiDec = layout.nodes.filterIsInstance<FlowNode.DecisionCard>().first { it.id == "elseif_dec_ei_1" }
    val elseHdr = layout.nodes.filterIsInstance<FlowNode.ElseHeaderPill>().first { it.id == "else_hdr_root_if" }

    assertTrue(eiDec.x >= rootDec.x + rootDec.width + ScriptFlowLayoutEngine.COL_GAP)
    assertTrue(eiDec.y > rootDec.y)
    assertTrue(elseHdr.x >= eiDec.x + eiDec.width + ScriptFlowLayoutEngine.COL_GAP)
    assertTrue(elseHdr.y > eiDec.y)

    // Verify 2 exits (Yes down, No right) for root_if decision
    val yesFromRoot =
      layout.edges.firstOrNull {
        it.kind == FlowEdgeKind.BRANCH_YES &&
          it.points.first().y == rootDec.y + rootDec.height
      }
    val noFromRoot =
      layout.edges.firstOrNull {
        it.kind == FlowEdgeKind.BRANCH_NO &&
          it.points.first().x == rootDec.x + rootDec.width
      }
    assertNotNull(yesFromRoot)
    assertNotNull(noFromRoot)
  }

  @Test
  fun loopBlock_reservesRightGutterOutsideAllInnerNodes() {
    val loop =
      ScriptAction.LoopBlock(
        id = "loop1",
        mode = LoopMode.Count(NumericInput.Literal(5)),
        bodyActions =
          listOf(
            ScriptAction.IfBlock(
              id = "inner_if",
              conditionRoot = ConditionGroup("ig1"),
              thenActions = listOf(ScriptAction.Tap(id = "t1")),
              elseActions = listOf(ScriptAction.Delay(id = "d1")),
            ),
          ),
      )
    val layout = ScriptFlowLayoutEngine.computeLayout(listOf(loop))
    assertNoNodeOverlaps(layout)

    val container = layout.containers.single()
    val loopBack = layout.edges.single { it.kind == FlowEdgeKind.LOOP_BACK }
    assertTrue(container.gutterX < container.x + container.width)

    // Every node inside the loop body must be strictly left of the loop-back gutter X
    val innerNodes = layout.nodes.filter { it.id != "start" && it.id != "end" && it.id != "loop_hdr_loop1" }
    assertTrue(innerNodes.isNotEmpty())
    innerNodes.forEach { node ->
      assertTrue(
        "Node ${node.id} right edge ${node.x + node.width} intruded into gutter ${container.gutterX}",
        node.x + node.width < container.gutterX,
      )
    }
    assertTrue(loopBack.points.any { it.x == container.gutterX })
  }

  @Test
  fun earlyTerminations_doNotEmitDownwardContinuationWires() {
    val ifWithBreakAndContinue =
      ScriptAction.IfBlock(
        id = "if_term",
        conditionRoot = ConditionGroup("g1"),
        thenActions = listOf(ScriptAction.Break(id = "brk_1")),
        elseIfBranches =
          listOf(
            ScriptAction.ElseIfBranch(
              id = "ei_cont",
              conditionRoot = ConditionGroup("g2"),
              actions = listOf(ScriptAction.Continue(id = "cont_1")),
            ),
          ),
        elseActions = listOf(ScriptAction.Tap(id = "tap_ok")),
      )
    val layout = ScriptFlowLayoutEngine.computeLayout(listOf(ifWithBreakAndContinue))
    assertNoNodeOverlaps(layout)

    val breakPill = layout.nodes.filterIsInstance<FlowNode.EarlyTerminationPill>().single { it.terminationType == FlowTerminationType.BREAK }
    val contPill = layout.nodes.filterIsInstance<FlowNode.EarlyTerminationPill>().single { it.terminationType == FlowTerminationType.CONTINUE }

    // Neither Break nor Continue should have an outgoing edge starting at its bottom center
    assertFalse(
      layout.edges.any { edge ->
        val start = edge.points.first()
        start.y == breakPill.y + breakPill.height
      },
    )
    assertFalse(
      layout.edges.any { edge ->
        val start = edge.points.first()
        start.y == contPill.y + contPill.height
      },
    )

    // Top-level EndScript stops sequence before EndAnchor
    val endScriptLayout = ScriptFlowLayoutEngine.computeLayout(listOf(ScriptAction.EndScript(id = "end_1")))
    assertNoNodeOverlaps(endScriptLayout)
    assertFalse(endScriptLayout.nodes.any { it is FlowNode.EndAnchor })
  }

  @Test
  fun previewBridge_publishesLiveDraftWithoutSaving() {
    val bridge = ScriptFlowPreviewBridge()
    val draft = Script(id = "unsaved-1", title = "Unsaved Draft", actions = listOf(ScriptAction.Tap(id = "t1")))
    bridge.publishDraft(draft)
    assertEquals(draft, bridge.draftScript.value)
    bridge.clear()
    assertEquals(null, bridge.draftScript.value)
  }
}
