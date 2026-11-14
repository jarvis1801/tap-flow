package com.tapflow.core.service.engine

import com.tapflow.core.model.action.BooleanOperator
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.MathOperator
import com.tapflow.core.model.action.RandomTargetType
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.StringOperator
import com.tapflow.core.model.action.TypeCastOperator
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.condition.ConditionGroup
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableAndDelayEngineTest {

  @Test
  fun pipelineSeedIfAbsent_preservesExistingRuntimeValuesAcrossScripts() = runTest {
    val store = VariableRuntimeStore(clipboardReader = { "CLIP_VAL" }, random = Random(42))
    store.seedIfAbsent(
      listOf(
        VariableDefinition("counter", VariableType.NUMBER, "1"),
        VariableDefinition("user", VariableType.STRING, "alice"),
      ),
    )

    val mathRes =
      store.executeVariableOp(
        ScriptAction.MathOp(
          id = "m1",
          targetKey = "counter",
          operator = MathOperator.ADD,
          leftOperand = "counter",
          rightOperand = "4",
        ),
      )
    assertTrue(mathRes is VariableOpResult.Success)
    assertEquals("5", store.getString("counter"))

    // Re-seeding on repeated pipeline invocation MUST NOT reset mutated counter
    store.seedIfAbsent(
      listOf(
        VariableDefinition("counter", VariableType.NUMBER, "1"),
        VariableDefinition("flag", VariableType.BOOLEAN, "true"),
      ),
    )
    assertEquals("5", store.getString("counter"))
    assertTrue(store.getBoolean("flag"))
  }

  @Test
  fun interpolation_supportsUserVariablesAndSystemTags() = runTest {
    val store = VariableRuntimeStore(clipboardReader = { "ORDER-999" }, random = Random(7))
    store.setDirect("user", VariableType.STRING, "TapUser")

    val result = store.interpolate("Hi {user}, clip={CLIPBOARD}, rnd={RANDOM_NUM_4}, ts={TIMESTAMP}, d={DATE}, t={TIME}")
    assertTrue(result.startsWith("Hi TapUser, clip=ORDER-999, rnd="))
    assertFalse(result.contains("{CLIPBOARD}"))
    assertFalse(result.contains("{TIMESTAMP}"))
    assertFalse(result.contains("{DATE}"))
    assertFalse(result.contains("{TIME}"))
    assertFalse(result.contains("{RANDOM_NUM_4}"))
  }

  @Test
  fun variableOperations_coverMathRoundingStringBooleanAndTypeCast() = runTest {
    val store = VariableRuntimeStore()
    store.setDirect("num", VariableType.NUMBER, "-3.6")
    store.setDirect("b1", VariableType.BOOLEAN, "true")
    store.setDirect("b2", VariableType.BOOLEAN, "false")

    assertTrue(store.executeVariableOp(ScriptAction.MathOp("1", "absVal", MathOperator.ABS, "num")) is VariableOpResult.Success)
    assertEquals("3.6", store.getString("absVal"))

    assertTrue(store.executeVariableOp(ScriptAction.MathOp("2", "rounded", MathOperator.ROUND, "absVal")) is VariableOpResult.Success)
    assertEquals("4", store.getString("rounded"))

    assertTrue(store.executeVariableOp(ScriptAction.MathOp("3", "floored", MathOperator.FLOOR, "absVal")) is VariableOpResult.Success)
    assertEquals("3", store.getString("floored"))

    assertTrue(store.executeVariableOp(ScriptAction.MathOp("4", "ceiled", MathOperator.CEIL, "absVal")) is VariableOpResult.Success)
    assertEquals("4", store.getString("ceiled"))

    val divZero = store.executeVariableOp(ScriptAction.MathOp("5", "err", MathOperator.DIVIDE, "rounded", "0"))
    assertTrue(divZero is VariableOpResult.Failure)

    assertTrue(
      store.executeVariableOp(
        ScriptAction.StringOp("6", "text", StringOperator.CONCATENATE, "  hello ", "world  "),
      ) is VariableOpResult.Success,
    )
    assertTrue(store.executeVariableOp(ScriptAction.StringOp("7", "text", StringOperator.TRIM, "")) is VariableOpResult.Success)
    assertEquals("hello world", store.getString("text"))

    assertTrue(store.executeVariableOp(ScriptAction.BooleanOp("8", "bOut", BooleanOperator.AND, "b1", "b2")) is VariableOpResult.Success)
    assertFalse(store.getBoolean("bOut"))

    assertTrue(store.executeVariableOp(ScriptAction.BooleanOp("9", "bOut", BooleanOperator.TOGGLE, "bOut")) is VariableOpResult.Success)
    assertTrue(store.getBoolean("bOut"))

    assertTrue(store.executeVariableOp(ScriptAction.TypeCastOp("10", "bNum", "bOut", TypeCastOperator.BOOLEAN_TO_NUMBER)) is VariableOpResult.Success)
    assertEquals("1", store.getString("bNum"))

    assertTrue(store.executeVariableOp(ScriptAction.RandomOp("11", "rndInt", RandomTargetType.INT, 10.0, 20.0)) is VariableOpResult.Success)
    val rnd = store.getNumber("rndInt")!!
    assertTrue(rnd in 10.0..20.0)
  }

  @Test
  fun delayResolver_clampsMinimumAndEnforcesZeroOnExcludedCategories() {
    assertEquals(50L, DelayResolver.resolveDelayMs(DelayConfig.Fixed(10L)))

    val tapAction = ScriptAction.Tap(id = "t1", postDelay = PostActionDelay.Global)
    assertEquals(50L, DelayResolver.resolvePostActionDelayMs(tapAction, DelayConfig.Fixed(5L)))

    val offTap = ScriptAction.Tap(id = "t2", postDelay = PostActionDelay.Off)
    assertEquals(0L, DelayResolver.resolvePostActionDelayMs(offTap, DelayConfig.Fixed(250L)))

    val ifBlock = ScriptAction.IfBlock(id = "if1", conditionRoot = ConditionGroup(id = "g1"))
    assertEquals(0L, DelayResolver.resolvePostActionDelayMs(ifBlock, DelayConfig.Fixed(250L)))

    val setVar = ScriptAction.SetVariable(id = "v1", targetKey = "k", valueTemplate = "1")
    assertEquals(0L, DelayResolver.resolvePostActionDelayMs(setVar, DelayConfig.Fixed(250L)))

    val copyOp = ScriptAction.ClipboardOp(id = "c1", operation = ClipboardActionType.COPY, postDelay = PostActionDelay.Global)
    assertEquals(0L, DelayResolver.resolvePostActionDelayMs(copyOp, DelayConfig.Fixed(250L)))

    val pasteOp = ScriptAction.ClipboardOp(id = "c2", operation = ClipboardActionType.PASTE, postDelay = PostActionDelay.Global)
    assertEquals(250L, DelayResolver.resolvePostActionDelayMs(pasteOp, DelayConfig.Fixed(250L)))
  }
}
