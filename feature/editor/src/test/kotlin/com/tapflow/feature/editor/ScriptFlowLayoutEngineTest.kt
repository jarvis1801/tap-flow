package com.tapflow.feature.editor

import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.script.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
  fun postActionDelays_attachBadgesToPhysicalActionsOnlyAndStayCleanWhenZero() {
    val tapGlobal = ScriptAction.Tap(id = "tap_g", postDelay = PostActionDelay.Global)
    val tapOverride =
      ScriptAction.Tap(
        id = "tap_o",
        postDelay = PostActionDelay.Override(DelayConfig.RandomRange(200L, 500L)),
      )
    val tapOff = ScriptAction.Tap(id = "tap_off", postDelay = PostActionDelay.Off)
    val delayNode = ScriptAction.Delay(id = "del_1", config = DelayConfig.Fixed(300L))
    val varNode = ScriptAction.SetVariable(id = "var_1", targetKey = "count", valueTemplate = "1")

    val layoutNonZero =
      ScriptFlowLayoutEngine.computeLayout(
        actions = listOf(tapGlobal, tapOverride, tapOff, delayNode, varNode),
        globalDelay = DelayConfig.Fixed(500L),
      )
    assertNoNodeOverlaps(layoutNonZero)

    val badgesByNodeId =
      layoutNonZero.nodes.filterIsInstance<FlowNode.ActionCard>().associateBy { it.id }
    assertEquals("⏱ 500ms", badgesByNodeId.getValue("action_tap_g").postDelayBadge?.label)
    assertEquals("⏱ 200–500ms", badgesByNodeId.getValue("action_tap_o").postDelayBadge?.label)
    assertEquals("0ms (Off)", badgesByNodeId.getValue("action_tap_off").postDelayBadge?.label)
    assertTrue(badgesByNodeId.getValue("action_tap_off").postDelayBadge?.isMutedOff == true)
    assertNull(badgesByNodeId.getValue("action_del_1").postDelayBadge)
    assertNull(badgesByNodeId.getValue("action_var_1").postDelayBadge)

    // Outgoing edges carry matching badges on anchors
    val edgeBadges = layoutNonZero.edges.mapNotNull { it.delayBadge?.label }
    assertEquals(listOf("⏱ 500ms", "⏱ 200–500ms", "0ms (Off)"), edgeBadges)

    // When globalDelay == 0ms and PostActionDelay.Global / Off, zero badge clutter
    val layoutZero =
      ScriptFlowLayoutEngine.computeLayout(
        actions = listOf(tapGlobal, tapOff, delayNode),
        globalDelay = DelayConfig.Fixed(0L),
      )
    assertNoNodeOverlaps(layoutZero)
    assertTrue(layoutZero.edges.none { it.delayBadge != null })
  }

  @Test
  fun tallMultiLineDecisionCards_expandHeightAndKeepCleanLadderAndNoOverlaps() {
    val longSummaryIf =
      ScriptAction.IfBlock(
        id = "if_long",
        conditionRoot =
          ConditionGroup(
            id = "g_long",
            children =
              listOf(
                ConditionLeaf(
                  id = "l_a",
                  predicate = ConditionPredicate.ColorMatch(targets = listOf(ColorTarget(120, 340, "#FF1122"))),
                ),
                ConditionLeaf(
                  id = "l_b",
                  predicate = ConditionPredicate.ColorMatch(targets = listOf(ColorTarget(520, 940, "#00FF88"))),
                ),
                ConditionLeaf(
                  id = "l_c",
                  predicate = ConditionPredicate.AppMatch(packageName = "com.example.superlongpackagename.activity"),
                ),
              ),
          ),
        thenActions = listOf(ScriptAction.Tap(id = "t_then")),
        elseIfBranches =
          listOf(
            ScriptAction.ElseIfBranch(
              id = "ei_long",
              name = "Fallback Branch With Very Long Description",
              conditionRoot = ConditionGroup("g_ei"),
              actions = listOf(ScriptAction.Tap(id = "t_ei")),
            ),
          ),
        elseActions = listOf(ScriptAction.Tap(id = "t_else")),
      )

    val layout =
      ScriptFlowLayoutEngine.computeLayout(
        actions = listOf(longSummaryIf),
        decisionHeightsByNodeId = mapOf("if_dec_if_long" to 118f),
      )
    assertNoNodeOverlaps(layout)

    val rootDec = layout.nodes.filterIsInstance<FlowNode.DecisionCard>().first { it.id == "if_dec_if_long" }
    val eiDec = layout.nodes.filterIsInstance<FlowNode.DecisionCard>().first { it.id == "elseif_dec_ei_long" }
    assertEquals(118f, rootDec.height)
    // Dynamic stepping clears the center of the tall DecisionCard cleanly
    assertTrue(eiDec.y >= rootDec.y + (rootDec.height / 2f) + 26f)
  }

  @Test
  fun collapsedIfAndLoopBlocks_hideSubtreesWithoutMutatingScript() {
    val ifBlock =
      ScriptAction.IfBlock(
        id = "if_col",
        conditionRoot = ConditionGroup("g1"),
        thenActions = listOf(ScriptAction.Tap(id = "t1"), ScriptAction.Delay(id = "d1")),
        elseActions = listOf(ScriptAction.Tap(id = "t2")),
      )
    val loopBlock =
      ScriptAction.LoopBlock(
        id = "loop_col",
        mode = LoopMode.Count(NumericInput.Literal(3)),
        bodyActions = listOf(ScriptAction.Tap(id = "lt1"), ScriptAction.Tap(id = "lt2")),
      )
    val script = Script(id = "s1", title = "Collapsible", actions = listOf(ifBlock, loopBlock))

    val layout =
      ScriptFlowLayoutEngine.computeLayout(
        script = script,
        collapsedBlockIds = setOf("if_col", "loop_col"),
      )
    assertNoNodeOverlaps(layout)

    val ifNode = layout.nodes.filterIsInstance<FlowNode.DecisionCard>().single { it.blockId == "if_col" }
    assertTrue(ifNode.isCollapsed)
    assertEquals(3, ifNode.collapsedStepCount)
    assertFalse(layout.nodes.any { it.id == "action_t1" || it.id == "action_d1" || it.id == "action_t2" || it.id == "else_hdr_if_col" })

    val loopNode = layout.nodes.filterIsInstance<FlowNode.LoopHeaderCard>().single { it.blockId == "loop_col" }
    assertTrue(loopNode.isCollapsed)
    assertEquals(2, loopNode.collapsedStepCount)
    assertTrue(layout.containers.isEmpty())
    assertFalse(layout.edges.any { it.kind == FlowEdgeKind.LOOP_BACK })
    assertFalse(layout.nodes.any { it.id == "action_lt1" || it.id == "action_lt2" })
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
