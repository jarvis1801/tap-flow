package com.tapflow.feature.editor

import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.MathOperator
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.ConditionValidator
import com.tapflow.core.model.condition.LogicOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionTreeAndConditionTest {

  @Test
  fun postActionDelaySupport_matchesSpecification() {
    assertTrue(ScriptAction.Tap(id = "t1").supportsPostActionDelay())
    assertTrue(ScriptAction.ClipboardOp(id = "c1", operation = ClipboardActionType.PASTE).supportsPostActionDelay())
    assertFalse(ScriptAction.ClipboardOp(id = "c2", operation = ClipboardActionType.COPY).supportsPostActionDelay())
    assertFalse(ScriptAction.ClipboardOp(id = "c3", operation = ClipboardActionType.CLEAR).supportsPostActionDelay())
    assertFalse(ScriptAction.Delay(id = "d1", config = DelayConfig.Fixed(200L)).supportsPostActionDelay())
    assertFalse(ScriptAction.IfBlock(id = "i1", conditionRoot = ConditionGroup("g1")).supportsPostActionDelay())
    assertFalse(ScriptAction.LoopBlock(id = "l1", mode = LoopMode.Count()).supportsPostActionDelay())
    assertFalse(ScriptAction.Break(id = "b1").supportsPostActionDelay())
    assertFalse(ScriptAction.Continue(id = "cn1").supportsPostActionDelay())
    assertFalse(ScriptAction.EndScript(id = "e1").supportsPostActionDelay())
    assertFalse(ScriptAction.Vibrate(id = "v1").supportsPostActionDelay())
    assertFalse(ScriptAction.PlayAlertSound(id = "s1").supportsPostActionDelay())
    assertFalse(
      ScriptAction.MathOp(
        id = "m1",
        targetKey = "x",
        operator = MathOperator.ADD,
        leftOperand = "1",
        rightOperand = "2",
      ).supportsPostActionDelay(),
    )
  }

  @Test
  fun actionTree_nestedInsertMoveDeleteAndUndo_preservesBranchAndIndex() {
    val ifBlock =
      ScriptAction.IfBlock(
        id = "if1",
        conditionRoot = ConditionGroup(id = "g1"),
        thenActions =
          listOf(
            ScriptAction.Tap(id = "t1", x = NumericInput.Literal(10), y = NumericInput.Literal(10)),
            ScriptAction.Tap(id = "t2", x = NumericInput.Literal(20), y = NumericInput.Literal(20)),
          ),
      )
    val root = listOf(ifBlock)
    val thenLoc = ActionBranchLocation(parentActionId = "if1", branchKind = BranchKind.IF_THEN)

    // Insert into index 1 of Then branch
    val inserted =
      ActionTreeUtils.insertActionAt(
        actions = root,
        location = thenLoc,
        index = 1,
        newAction = ScriptAction.Delay(id = "d_mid"),
      )
    val insertedThen = (inserted.first() as ScriptAction.IfBlock).thenActions
    assertEquals(listOf("t1", "d_mid", "t2"), insertedThen.map { it.id })

    // Move d_mid up to index 0
    val moved = ActionTreeUtils.moveSibling(inserted, thenLoc, index = 1, delta = -1)
    val movedThen = (moved.first() as ScriptAction.IfBlock).thenActions
    assertEquals(listOf("d_mid", "t1", "t2"), movedThen.map { it.id })

    // Delete t1 and restore via Undo record
    val (afterDelete, undoRecord) = ActionTreeUtils.deleteActionById(moved, "t1")
    assertNotNull(undoRecord)
    assertEquals(1, undoRecord!!.index)
    assertEquals(BranchKind.IF_THEN, undoRecord.location.branchKind)
    assertEquals(listOf("d_mid", "t2"), (afterDelete.first() as ScriptAction.IfBlock).thenActions.map { it.id })

    val restored =
      ActionTreeUtils.insertActionAt(
        actions = afterDelete,
        location = undoRecord.location,
        index = undoRecord.index,
        newAction = undoRecord.action,
      )
    assertEquals(listOf("d_mid", "t1", "t2"), (restored.first() as ScriptAction.IfBlock).thenActions.map { it.id })
  }

  @Test
  fun conditionTree_enforcesMaxDepth3AndGeneratesNaturalLanguagePreview() {
    val l1 = ConditionGroup(id = "g1", operator = LogicOperator.ANY)
    val l2 = ConditionGroup(id = "g2", operator = LogicOperator.ALL)
    val l3 = ConditionGroup(id = "g3", operator = LogicOperator.ANY)

    var tree = ConditionTreeUtils.addNodeToGroup(l1, "g1", l2)
    tree = ConditionTreeUtils.addNodeToGroup(tree, "g2", l3)

    // Level 3 cannot nest a Level 4 subgroup
    assertFalse(ConditionValidator.canAddSubGroup(tree, "g3"))
    val attemptedL4 = ConditionGroup(id = "g4")
    val afterBlockedAdd = ConditionTreeUtils.addNodeToGroup(tree, "g3", attemptedL4)
    assertEquals(3, ConditionValidator.calculateMaxDepth(afterBlockedAdd))

    // Add leaf to L3
    val leaf =
      ConditionLeaf(
        id = "leaf1",
        label = "Ready",
        predicate =
          ConditionPredicate.ColorMatch(
            targets = listOf(ColorTarget(x = 100, y = 200, hexColor = "#00FF00")),
          ),
      )
    val withLeaf = ConditionTreeUtils.addNodeToGroup(tree, "g3", leaf)
    val preview = ConditionValidator.formatNaturalLanguagePreview(withLeaf)
    assertTrue(preview.startsWith("When "))
    assertTrue(preview.contains("[Ready]"))
  }

  @Test
  fun applyGlobalDelayToAllPhysicalActions_resetsNestedOverridesAndOffToGlobal() {
    val tree =
      listOf(
        ScriptAction.Tap(
          id = "root_tap",
          postDelay = PostActionDelay.Override(DelayConfig.Fixed(300L)),
        ),
        ScriptAction.IfBlock(
          id = "if1",
          conditionRoot = ConditionGroup("g1"),
          thenActions =
            listOf(
              ScriptAction.DoubleTap(id = "then_dt", postDelay = PostActionDelay.Off),
              ScriptAction.ClipboardOp(
                id = "then_paste",
                operation = ClipboardActionType.PASTE,
                postDelay = PostActionDelay.Override(DelayConfig.Fixed(120L)),
              ),
            ),
          elseActions =
            listOf(
              ScriptAction.LaunchApp(
                id = "else_launch",
                packageName = "com.jarvis.tapflow.app",
                postDelay = PostActionDelay.Off,
              ),
            ),
        ),
        ScriptAction.LoopBlock(
          id = "loop1",
          mode = LoopMode.Count(),
          bodyActions =
            listOf(
              ScriptAction.SwipeDrag(
                id = "loop_swipe",
                postDelay = PostActionDelay.Override(DelayConfig.Fixed(500L)),
              ),
              ScriptAction.Delay(id = "loop_delay", config = DelayConfig.Fixed(250L)),
            ),
        ),
      )

    val updated = ActionTreeUtils.applyGlobalDelayToAllPhysicalActions(tree)

    val rootTap = updated[0] as ScriptAction.Tap
    assertEquals(PostActionDelay.Global, rootTap.postDelay)

    val ifBlock = updated[1] as ScriptAction.IfBlock
    assertEquals(PostActionDelay.Global, (ifBlock.thenActions[0] as ScriptAction.DoubleTap).postDelay)
    assertEquals(PostActionDelay.Global, (ifBlock.thenActions[1] as ScriptAction.ClipboardOp).postDelay)
    assertEquals(PostActionDelay.Global, (ifBlock.elseActions[0] as ScriptAction.LaunchApp).postDelay)

    val loopBlock = updated[2] as ScriptAction.LoopBlock
    assertEquals(PostActionDelay.Global, (loopBlock.bodyActions[0] as ScriptAction.SwipeDrag).postDelay)
    assertEquals(DelayConfig.Fixed(250L), (loopBlock.bodyActions[1] as ScriptAction.Delay).config)
  }
}
