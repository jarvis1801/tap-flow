package com.tapflow.feature.editor

import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.MathOperator
import com.tapflow.core.model.action.MultiTouchMode
import com.tapflow.core.model.action.MultiTouchPoint
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.TextInjectionEngine
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.ConditionValidator
import com.tapflow.core.model.condition.LogicOperator
import com.tapflow.core.model.condition.NumberCompareOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    val inserted =
      ActionTreeUtils.insertActionAt(
        actions = root,
        location = thenLoc,
        index = 1,
        newAction = ScriptAction.Delay(id = "d_mid"),
      )
    val insertedThen = (inserted.first() as ScriptAction.IfBlock).thenActions
    assertEquals(listOf("t1", "d_mid", "t2"), insertedThen.map { it.id })

    val moved = ActionTreeUtils.moveSibling(inserted, thenLoc, index = 1, delta = -1)
    val movedThen = (moved.first() as ScriptAction.IfBlock).thenActions
    assertEquals(listOf("d_mid", "t1", "t2"), movedThen.map { it.id })

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

    assertFalse(ConditionValidator.canAddSubGroup(tree, "g3"))
    val attemptedL4 = ConditionGroup(id = "g4")
    val afterBlockedAdd = ConditionTreeUtils.addNodeToGroup(tree, "g3", attemptedL4)
    assertEquals(3, ConditionValidator.calculateMaxDepth(afterBlockedAdd))

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

  @Test
  fun actionValidator_rejectsEmptyTargets_whileAllowingValidZeroCoordinates() {
    val invalidTap =
      ScriptAction.Tap(
        id = "tap_empty",
        x = NumericInput.Literal(UNSET_COORD),
        y = NumericInput.Literal(UNSET_COORD),
      )
    val tapErrors = ActionValidator.validate(invalidTap)
    assertTrue(tapErrors.containsKey("x"))
    assertTrue(tapErrors.containsKey("y"))

    val zeroTap =
      ScriptAction.Tap(
        id = "tap_origin",
        x = NumericInput.Literal(0),
        y = NumericInput.Literal(0),
      )
    assertTrue(ActionValidator.validate(zeroTap).isEmpty())

    val invalidInput =
      ScriptAction.InputText(
        id = "in_empty",
        tapToFocus = true,
        focusX = null,
        focusY = null,
        textTemplate = "   ",
      )
    val inputErrors = ActionValidator.validate(invalidInput)
    assertTrue(inputErrors.containsKey("focus_x"))
    assertTrue(inputErrors.containsKey("focus_y"))
    assertTrue(inputErrors.containsKey("text_template"))

    val invalidMultiTouch =
      ScriptAction.MultiTouch(
        id = "mt_empty",
        mode = MultiTouchMode.CUSTOM_POINTS,
        customPoints = listOf(MultiTouchPoint(x = UNSET_COORD, y = 200)),
      )
    val mtErrors = ActionValidator.validate(invalidMultiTouch)
    assertTrue(mtErrors.containsKey("finger_0_x"))
    assertFalse(mtErrors.containsKey("finger_0_y"))

    assertTrue(ActionValidator.validate(ScriptAction.LaunchApp(id = "l1", packageName = "")).containsKey("app_package"))
    assertTrue(ActionValidator.validate(ScriptAction.ForceStopApp(id = "f1", packageName = "")).containsKey("app_package"))
    assertTrue(
      ActionValidator.validate(
        ScriptAction.ClipboardOp(id = "cb1", operation = ClipboardActionType.COPY, valueTemplate = ""),
      ).containsKey("clipboard_text"),
    )

    val emptyIf = ScriptAction.IfBlock(id = "if_empty", conditionRoot = ConditionGroup(id = "root"))
    assertTrue(ActionValidator.validate(emptyIf).containsKey("cond_group_root"))

    val incompleteVarIf =
      ScriptAction.IfBlock(
        id = "if_var",
        conditionRoot =
          ConditionGroup(
            id = "root",
            children =
              listOf(
                ConditionLeaf(
                  id = "lf1",
                  predicate =
                    ConditionPredicate.VariableCompare(
                      variableKey = "",
                      variableType = VariableType.NUMBER,
                      numberOp = NumberCompareOp.GT,
                      expectedValue = "",
                    ),
                ),
              ),
          ),
      )
    val leafErrors = ActionValidator.validate(incompleteVarIf)
    assertTrue(leafErrors.containsKey("cond_leaf_lf1_var_key"))
    assertTrue(leafErrors.containsKey("cond_leaf_lf1_var_expected"))
  }

  @Test
  fun plainLanguageFormatter_generatesFriendlySentencesAndSummariesWithoutJargon() {
    val whiteLeaf =
      ConditionLeaf(
        id = "c1",
        predicate =
          ConditionPredicate.ColorMatch(
            targets = listOf(ColorTarget(x = 540, y = 960, hexColor = "#FFFFFF")),
          ),
      )
    val nestedGroup =
      ConditionGroup(
        id = "sub",
        operator = LogicOperator.ANY,
        children =
          listOf(
            ConditionLeaf(
              id = "c2",
              predicate = ConditionPredicate.AppMatch(packageName = "com.game", appLabel = "Game"),
            ),
            ConditionLeaf(
              id = "c3",
              predicate =
                ConditionPredicate.VariableCompare(
                  variableKey = "coins",
                  variableType = VariableType.NUMBER,
                  numberOp = NumberCompareOp.GTE,
                  expectedValue = "100",
                ),
            ),
          ),
      )
    val root = ConditionGroup(id = "root", operator = LogicOperator.ALL, children = listOf(whiteLeaf, nestedGroup))

    val sentence = PlainLanguageFormatter.formatConditionSentence(root)
    assertEquals(
      "Run 'Then' steps when: screen color at (540, 960) is White AND (app Game is open OR coins is at least 100)",
      sentence,
    )
    assertEquals("#FFFFFF", PlainLanguageFormatter.extractFirstColorHex(root))
    assertNull(PlainLanguageFormatter.extractFirstColorHex(ConditionGroup(id = "empty")))

    val ifAction = ScriptAction.IfBlock(id = "if1", conditionRoot = root)
    val ifSummary = ActionTreeUtils.summarizeAction(ifAction)
    assertFalse(ifSummary.contains("When ["))

    val inputSummary =
      ActionTreeUtils.summarizeAction(
        ScriptAction.InputText(
          id = "in1",
          textTemplate = "hello",
          engine = TextInjectionEngine.CLIPBOARD_PASTE,
        ),
      )
    assertTrue(inputSummary.contains("Paste Text"))
    assertFalse(inputSummary.contains("CLIPBOARD_PASTE"))
  }

  @Test
  fun elseIfBranch_supportsInsertMoveDeleteUndoAndGlobalDelay() {
    val ifBlock =
      ScriptAction.IfBlock(
        id = "if1",
        conditionRoot = ConditionGroup(id = "g1"),
        elseIfBranches =
          listOf(
            ScriptAction.ElseIfBranch(
              id = "ei1",
              conditionRoot = ConditionGroup(id = "gei1"),
              actions =
                listOf(
                  ScriptAction.Tap(id = "t1", x = NumericInput.Literal(10), y = NumericInput.Literal(10)),
                  ScriptAction.Tap(id = "t2", x = NumericInput.Literal(20), y = NumericInput.Literal(20), postDelay = PostActionDelay.Off),
                ),
            ),
          ),
      )
    val eiLoc = ActionBranchLocation(parentActionId = "if1", branchKind = BranchKind.IF_ELSE_IF, branchId = "ei1")

    val inserted =
      ActionTreeUtils.insertActionAt(
        actions = listOf(ifBlock),
        location = eiLoc,
        index = 1,
        newAction = ScriptAction.Delay(id = "d_mid"),
      )
    val insertedBranch = (inserted.first() as ScriptAction.IfBlock).elseIfBranches.first()
    assertEquals(listOf("t1", "d_mid", "t2"), insertedBranch.actions.map { it.id })

    val moved = ActionTreeUtils.moveSibling(inserted, eiLoc, index = 1, delta = -1)
    assertEquals(
      listOf("d_mid", "t1", "t2"),
      (moved.first() as ScriptAction.IfBlock).elseIfBranches.first().actions.map { it.id },
    )

    val (afterDelete, undoRecord) = ActionTreeUtils.deleteActionById(moved, "t1")
    assertNotNull(undoRecord)
    assertEquals(BranchKind.IF_ELSE_IF, undoRecord!!.location.branchKind)
    assertEquals("ei1", undoRecord.location.branchId)
    assertEquals(1, undoRecord.index)

    val restored =
      ActionTreeUtils.insertActionAt(
        actions = afterDelete,
        location = undoRecord.location,
        index = undoRecord.index,
        newAction = undoRecord.action,
      )
    assertEquals(
      listOf("d_mid", "t1", "t2"),
      (restored.first() as ScriptAction.IfBlock).elseIfBranches.first().actions.map { it.id },
    )

    val withGlobal = ActionTreeUtils.applyGlobalDelayToAllPhysicalActions(restored)
    val t2Updated = (withGlobal.first() as ScriptAction.IfBlock).elseIfBranches.first().actions[2] as ScriptAction.Tap
    assertEquals(PostActionDelay.Global, t2Updated.postDelay)
  }

  @Test
  fun ifNestingDepth_allowsSequentialIfEverywhereAndCapsOnlyAboveDepth3() {
    val level3 = ScriptAction.IfBlock(id = "if3", conditionRoot = ConditionGroup("g3"))
    val level2 =
      ScriptAction.IfBlock(
        id = "if2",
        conditionRoot = ConditionGroup("g2"),
        elseIfBranches =
          listOf(
            ScriptAction.ElseIfBranch(
              id = "ei2",
              conditionRoot = ConditionGroup("gei2"),
              actions = listOf(level3),
            ),
          ),
      )
    val level1 =
      ScriptAction.IfBlock(
        id = "if1",
        conditionRoot = ConditionGroup("g1"),
        thenActions = listOf(level2),
      )
    val tree = listOf(level1)

    // Sequential If at Root right after level1 -> depth 0 (< 3)
    assertEquals(0, ActionTreeUtils.ifNestingDepthForLocation(tree, ActionBranchLocation()))
    // Sequential If inside level1 Then (right after level2) -> depth 1 (< 3)
    assertEquals(1, ActionTreeUtils.ifNestingDepthForLocation(tree, ActionBranchLocation("if1", BranchKind.IF_THEN)))
    // Inside level2 ElseIf (right after level3) -> depth 2 (< 3)
    assertEquals(2, ActionTreeUtils.ifNestingDepthForLocation(tree, ActionBranchLocation("if2", BranchKind.IF_ELSE_IF, "ei2")))
    // Inside level3 Then -> depth 3 (== MAX_IF_NESTING_DEPTH -> blocked for adding 4th nested If)
    assertEquals(3, ActionTreeUtils.ifNestingDepthForLocation(tree, ActionBranchLocation("if3", BranchKind.IF_THEN)))
  }

  @Test
  fun actionValidator_validatesElseIfBranchCondition() {
    val ifBlock =
      ScriptAction.IfBlock(
        id = "if1",
        conditionRoot = ConditionGroup(id = "root_empty"),
        elseIfBranches =
          listOf(
            ScriptAction.ElseIfBranch(
              id = "ei1",
              conditionRoot = ConditionGroup(id = "ei1_empty"),
            ),
          ),
      )
    val eiErrors = ActionValidator.validate(ifBlock, editingElseIfBranchId = "ei1")
    assertTrue(eiErrors.containsKey("cond_group_ei1_empty"))
    assertFalse(eiErrors.containsKey("cond_group_root_empty"))

    val fullErrors = ActionValidator.validate(ifBlock)
    assertTrue(fullErrors.containsKey("cond_group_root_empty"))
    assertTrue(fullErrors.containsKey("cond_group_ei1_empty"))
  }
}
