package com.tapflow.core.model.condition

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.common.VariableType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LogicOperator {
  ALL,
  ANY,
  NONE,
}

@Serializable
enum class ConditionMatchRule {
  ALL,
  ANY,
  PERCENT_MATCH,
}

@Serializable
enum class ColorMatchMode {
  SINGLE_POINT,
  MULTI_POINT,
  REGION,
}

@Serializable
enum class ImageMatchAlgorithm {
  FAST_32_PIXEL,
  TEMPLATE_MATCH,
}

@Immutable
@Serializable
data class ColorTarget(
  val x: Int,
  val y: Int,
  val hexColor: String,
  val tolerance: Int = 15,
)

@Immutable
@Serializable
data class RegionBounds(
  val x1: Int,
  val y1: Int,
  val x2: Int,
  val y2: Int,
)

@Serializable
enum class StringCompareOp {
  EQ,
  NE,
  CONTAINS,
  LENGTH_GT,
  LENGTH_LT,
  LENGTH_EQ,
}

@Serializable
enum class NumberCompareOp {
  GT,
  GTE,
  LT,
  LTE,
  EQ,
  NE,
}

@Serializable
enum class BooleanCompareOp {
  IS_TRUE,
  IS_FALSE,
}

@Immutable
@Serializable
sealed interface ConditionPredicate {
  @Immutable
  @Serializable
  @SerialName("color_match")
  data class ColorMatch(
    val mode: ColorMatchMode = ColorMatchMode.SINGLE_POINT,
    val targets: List<ColorTarget> = emptyList(),
    val region: RegionBounds? = null,
    val rule: ConditionMatchRule = ConditionMatchRule.ALL,
    val matchPercentThreshold: Int = 80,
    val sourceInspectItemId: String? = null,
  ) : ConditionPredicate

  @Immutable
  @Serializable
  @SerialName("image_match")
  data class ImageMatch(
    val templatePngPath: String,
    val similarityThreshold: Float = 0.85f,
    val searchRoi: RegionBounds? = null,
    val algorithm: ImageMatchAlgorithm = ImageMatchAlgorithm.FAST_32_PIXEL,
    val sourceInspectCropId: String? = null,
  ) : ConditionPredicate

  @Immutable
  @Serializable
  @SerialName("app_match")
  data class AppMatch(
    val packageName: String,
    val appLabel: String = "",
  ) : ConditionPredicate

  @Immutable
  @Serializable
  @SerialName("variable_compare")
  data class VariableCompare(
    val variableKey: String,
    val variableType: VariableType,
    val stringOp: StringCompareOp? = null,
    val numberOp: NumberCompareOp? = null,
    val booleanOp: BooleanCompareOp? = null,
    val expectedValue: String = "",
  ) : ConditionPredicate
}

@Immutable
@Serializable
sealed interface ConditionNode {
  val id: String
}

@Immutable
@Serializable
@SerialName("group")
data class ConditionGroup(
  override val id: String,
  val name: String = "Condition Group",
  val operator: LogicOperator = LogicOperator.ALL,
  val children: List<ConditionNode> = emptyList(),
  val isExpanded: Boolean = true,
) : ConditionNode

@Immutable
@Serializable
@SerialName("leaf")
data class ConditionLeaf(
  override val id: String,
  val label: String = "",
  val inverted: Boolean = false,
  val predicate: ConditionPredicate,
) : ConditionNode

object ConditionValidator {
  const val MAX_DEPTH: Int = 3

  fun calculateMaxDepth(node: ConditionNode, currentDepth: Int = 1): Int {
    return when (node) {
      is ConditionLeaf -> currentDepth
      is ConditionGroup -> {
        if (node.children.isEmpty()) {
          currentDepth
        } else {
          node.children.maxOf { child ->
            when (child) {
              is ConditionLeaf -> currentDepth
              is ConditionGroup -> calculateMaxDepth(child, currentDepth + 1)
            }
          }
        }
      }
    }
  }

  fun isValidDepth(root: ConditionGroup): Boolean = calculateMaxDepth(root) <= MAX_DEPTH

  fun findGroupDepth(
    current: ConditionGroup,
    targetGroupId: String,
    currentDepth: Int = 1,
  ): Int? {
    if (current.id == targetGroupId) return currentDepth
    for (child in current.children) {
      if (child is ConditionGroup) {
        val found = findGroupDepth(child, targetGroupId, currentDepth + 1)
        if (found != null) return found
      }
    }
    return null
  }

  fun canAddSubGroup(root: ConditionGroup, targetGroupId: String): Boolean {
    val depth = findGroupDepth(root, targetGroupId) ?: return false
    return depth < MAX_DEPTH
  }

  fun formatNaturalLanguagePreview(root: ConditionGroup): String {
    val body = formatGroupExpression(root)
    return if (body.isBlank()) "When (no conditions)" else "When $body"
  }

  private fun formatGroupExpression(group: ConditionGroup): String {
    if (group.children.isEmpty()) return "(empty)"
    val joiner =
      when (group.operator) {
        LogicOperator.ALL -> " AND "
        LogicOperator.ANY -> " OR "
        LogicOperator.NONE -> " NOR "
      }
    val parts =
      group.children.map { child ->
        when (child) {
          is ConditionLeaf -> {
            val raw = child.label.ifBlank { summarizePredicate(child.predicate) }
            if (child.inverted) "[NOT $raw]" else "[$raw]"
          }
          is ConditionGroup -> "(${formatGroupExpression(child)})"
        }
      }
    val joined = parts.joinToString(joiner)
    return if (group.operator == LogicOperator.NONE) "NONE($joined)" else joined
  }

  private fun summarizePredicate(predicate: ConditionPredicate): String {
    return when (predicate) {
      is ConditionPredicate.ColorMatch -> {
        val first = predicate.targets.firstOrNull()
        if (first != null) {
          "Color ${first.hexColor}@(${first.x},${first.y})"
        } else {
          "ColorMatch"
        }
      }
      is ConditionPredicate.ImageMatch -> "ImageMatch(${(predicate.similarityThreshold * 100).toInt()}%)"
      is ConditionPredicate.AppMatch ->
        "App=${predicate.appLabel.ifBlank { predicate.packageName }}"
      is ConditionPredicate.VariableCompare -> {
        val op = predicate.stringOp?.name ?: predicate.numberOp?.name ?: predicate.booleanOp?.name ?: "=="
        "${predicate.variableKey} $op ${predicate.expectedValue}".trim()
      }
    }
  }
}
