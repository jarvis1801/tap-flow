package com.tapflow.core.service.engine

import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.condition.BooleanCompareOp
import com.tapflow.core.model.condition.ColorMatchMode
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionNode
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.LogicOperator
import com.tapflow.core.model.condition.NumberCompareOp
import com.tapflow.core.model.condition.StringCompareOp
import com.tapflow.core.model.log.ConditionEvalTrace
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.vision.ZeroAllocVisionEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class ConditionEvalResult(
  val passed: Boolean,
  val trace: ConditionEvalTrace,
)

@Singleton
class ConditionEvaluator @Inject constructor(
  private val visionEngine: ZeroAllocVisionEngine,
  private val accessibilityBridge: AccessibilityManagerBridge,
) {
  suspend fun evaluate(
    root: ConditionGroup,
    variables: VariableRuntimeStore,
    useShizukuScreenshotFallback: Boolean = false,
  ): ConditionEvalResult {
    return evaluateNode(root, variables, useShizukuScreenshotFallback)
  }

  private suspend fun evaluateNode(
    node: ConditionNode,
    variables: VariableRuntimeStore,
    useShizukuFallback: Boolean,
  ): ConditionEvalResult {
    return when (node) {
      is ConditionGroup -> evaluateGroup(node, variables, useShizukuFallback)
      is ConditionLeaf -> evaluateLeaf(node, variables, useShizukuFallback)
    }
  }

  private suspend fun evaluateGroup(
    group: ConditionGroup,
    variables: VariableRuntimeStore,
    useShizukuFallback: Boolean,
  ): ConditionEvalResult {
    if (group.children.isEmpty()) {
      val emptyPasses = group.operator != LogicOperator.ANY
      return ConditionEvalResult(
        passed = emptyPasses,
        trace =
          ConditionEvalTrace(
            nodeId = group.id,
            label = group.name,
            passed = emptyPasses,
            actualSummary = "0 conditions",
            expectedSummary = "${group.operator.name} (empty)",
            children = emptyList(),
          ),
      )
    }

    val childTraces = ArrayList<ConditionEvalTrace>(group.children.size)
    var passCount = 0
    var outcome: Boolean

    when (group.operator) {
      LogicOperator.ALL -> {
        outcome = true
        for (child in group.children) {
          val res = evaluateNode(child, variables, useShizukuFallback)
          childTraces += res.trace
          if (res.passed) {
            passCount++
          } else {
            outcome = false
            break
          }
        }
      }

      LogicOperator.ANY -> {
        outcome = false
        for (child in group.children) {
          val res = evaluateNode(child, variables, useShizukuFallback)
          childTraces += res.trace
          if (res.passed) {
            passCount++
            outcome = true
            break
          }
        }
      }

      LogicOperator.NONE -> {
        outcome = true
        for (child in group.children) {
          val res = evaluateNode(child, variables, useShizukuFallback)
          childTraces += res.trace
          if (res.passed) {
            passCount++
            outcome = false
            break
          }
        }
      }
    }

    return ConditionEvalResult(
      passed = outcome,
      trace =
        ConditionEvalTrace(
          nodeId = group.id,
          label = group.name,
          passed = outcome,
          actualSummary = "$passCount/${childTraces.size} evaluated matched",
          expectedSummary = "Operator=${group.operator.name}",
          children = childTraces,
        ),
    )
  }

  private suspend fun evaluateLeaf(
    leaf: ConditionLeaf,
    variables: VariableRuntimeStore,
    useShizukuFallback: Boolean,
  ): ConditionEvalResult {
    val rawOutcome: Triple<Boolean, String, String> =
      when (val predicate = leaf.predicate) {
        is ConditionPredicate.ColorMatch -> {
          val result = visionEngine.evaluateColorMatch(predicate, useShizukuFallback)
          val actual =
            when (predicate.mode) {
              ColorMatchMode.SINGLE_POINT -> result.sampledHex ?: "Unavailable"
              ColorMatchMode.MULTI_POINT,
              ColorMatchMode.REGION ->
                "${(result.matchRatio * 100f).roundToInt()}% (${result.sampledHex ?: "n/a"})"
            }
          val expected =
            predicate.targets.firstOrNull()?.let { t ->
              "${t.hexColor}@(${t.x},${t.y}) ±${t.tolerance}"
            } ?: "ColorMatch(${predicate.mode.name})"
          Triple(result.matched, actual, expected)
        }

        is ConditionPredicate.ImageMatch -> {
          val result = visionEngine.evaluateImageMatch(predicate, useShizukuFallback)
          val actual =
            if (result.matched) {
              "${(result.similarity * 100f).roundToInt()}% at (${result.matchX},${result.matchY})"
            } else {
              "${(result.similarity * 100f).roundToInt()}%"
            }
          val expected =
            ">= ${(predicate.similarityThreshold * 100f).roundToInt()}% (${predicate.algorithm.name})"
          Triple(result.matched, actual, expected)
        }

        is ConditionPredicate.AppMatch -> {
          val currentPkg = accessibilityBridge.resolveCurrentForegroundPackage()
          val matched = !currentPkg.isNullOrBlank() && currentPkg == predicate.packageName
          Triple(
            matched,
            currentPkg ?: "unknown",
            predicate.appLabel.ifBlank { predicate.packageName },
          )
        }

        is ConditionPredicate.VariableCompare -> evaluateVariableCompare(predicate, variables)
      }

    val rawPassed = rawOutcome.first
    val finalPassed = if (leaf.inverted) !rawPassed else rawPassed
    val leafLabel = leaf.label.ifBlank { leaf.predicate::class.simpleName ?: "Condition" }
    val expectedText =
      if (leaf.inverted) "NOT (${rawOutcome.third})" else rawOutcome.third

    return ConditionEvalResult(
      passed = finalPassed,
      trace =
        ConditionEvalTrace(
          nodeId = leaf.id,
          label = leafLabel,
          passed = finalPassed,
          actualSummary = rawOutcome.second,
          expectedSummary = expectedText,
        ),
    )
  }

  private suspend fun evaluateVariableCompare(
    predicate: ConditionPredicate.VariableCompare,
    variables: VariableRuntimeStore,
  ): Triple<Boolean, String, String> {
    val actualRaw = variables.getString(predicate.variableKey)
    val expectedInterpolated = variables.interpolate(predicate.expectedValue)

    return when (predicate.variableType) {
      VariableType.STRING -> {
        val op = predicate.stringOp ?: StringCompareOp.EQ
        val passed =
          when (op) {
            StringCompareOp.EQ -> actualRaw == expectedInterpolated
            StringCompareOp.NE -> actualRaw != expectedInterpolated
            StringCompareOp.CONTAINS -> actualRaw.contains(expectedInterpolated)
            StringCompareOp.LENGTH_GT ->
              actualRaw.length > (expectedInterpolated.toIntOrNull() ?: Int.MAX_VALUE)
            StringCompareOp.LENGTH_LT ->
              actualRaw.length < (expectedInterpolated.toIntOrNull() ?: Int.MIN_VALUE)
            StringCompareOp.LENGTH_EQ ->
              actualRaw.length == (expectedInterpolated.toIntOrNull() ?: -1)
          }
        Triple(passed, "\"$actualRaw\"", "${op.name} \"$expectedInterpolated\"")
      }

      VariableType.NUMBER -> {
        val op = predicate.numberOp ?: NumberCompareOp.EQ
        val actualNum = variables.getNumber(predicate.variableKey)
        val expectedNum = expectedInterpolated.trim().toDoubleOrNull()
        val passed =
          if (actualNum != null && expectedNum != null) {
            when (op) {
              NumberCompareOp.GT -> actualNum > expectedNum
              NumberCompareOp.GTE -> actualNum >= expectedNum
              NumberCompareOp.LT -> actualNum < expectedNum
              NumberCompareOp.LTE -> actualNum <= expectedNum
              NumberCompareOp.EQ -> actualNum == expectedNum
              NumberCompareOp.NE -> actualNum != expectedNum
            }
          } else {
            false
          }
        Triple(passed, actualRaw.ifBlank { "NaN" }, "${op.name} $expectedInterpolated")
      }

      VariableType.BOOLEAN -> {
        val op = predicate.booleanOp ?: BooleanCompareOp.IS_TRUE
        val actualBool = variables.getBoolean(predicate.variableKey)
        val passed =
          when (op) {
            BooleanCompareOp.IS_TRUE -> actualBool
            BooleanCompareOp.IS_FALSE -> !actualBool
          }
        Triple(passed, actualBool.toString(), op.name)
      }
    }
  }
}
