package com.tapflow.feature.editor

import com.tapflow.core.model.action.BooleanOperator
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LongPressMode
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.MathOperator
import com.tapflow.core.model.action.MultiTouchMode
import com.tapflow.core.model.action.RandomTargetType
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.StringOperator
import com.tapflow.core.model.action.SwipeTrajectoryMode
import com.tapflow.core.model.action.SystemKeyType
import com.tapflow.core.model.action.TextInjectionEngine
import com.tapflow.core.model.action.TypeCastOperator
import com.tapflow.core.model.action.VibratePattern
import com.tapflow.core.model.common.NumericInput
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
import kotlin.math.abs

/** Sentinel integer representing an unentered screen coordinate in a new draft action. */
const val UNSET_COORD: Int = -1

fun Int.isUnsetCoord(): Boolean = this < 0

fun NumericInput.isUnsetTarget(): Boolean = this is NumericInput.Literal && this.value < 0

fun NumericInput.isBlankVarRef(): Boolean =
  this is NumericInput.VariableRef && this.variableKey.isBlank()

fun isValidHexColor(hex: String): Boolean {
  val clean = hex.trim().removePrefix("#")
  if (clean.length != 6 && clean.length != 8) return false
  return clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

/**
 * Validates draft [ScriptAction] instances before they are saved into the script tree.
 * Returns a map from stable field keys to user-friendly error messages.
 */
object ActionValidator {

  fun validate(
    action: ScriptAction,
    editingElseIfBranchId: String? = null,
  ): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    when (action) {
      is ScriptAction.Tap -> {
        validateNumericCoord(action.x, "x", "X", errors)
        validateNumericCoord(action.y, "y", "Y", errors)
        val rep = action.repeatCount
        if (rep.isBlankVarRef()) {
          errors["repeat_count"] = "Choose a number variable"
        } else if (rep is NumericInput.Literal && rep.value < 1) {
          errors["repeat_count"] = "Enter at least 1 tap"
        }
      }

      is ScriptAction.DoubleTap -> {
        validateNumericCoord(action.x, "x", "X", errors)
        validateNumericCoord(action.y, "y", "Y", errors)
      }

      is ScriptAction.LongPress -> {
        if (action.mode != LongPressMode.RELEASE_ONLY) {
          validateNumericCoord(action.x, "x", "X", errors)
          validateNumericCoord(action.y, "y", "Y", errors)
        }
        if (action.mode == LongPressMode.HOLD_UNTIL) {
          val cond = action.holdUntilCondition
          if (cond == null) {
            errors["hold_condition"] = "Add at least 1 stop rule"
          } else {
            validateConditionGroup(cond, isRoot = true, errors = errors)
          }
        }
      }

      is ScriptAction.MultiTouch -> {
        when (action.mode) {
          MultiTouchMode.CUSTOM_POINTS -> {
            if (action.customPoints.isEmpty()) {
              errors["custom_points"] = "Add at least 1 finger spot on screen"
            } else {
              action.customPoints.forEachIndexed { idx, pt ->
                if (pt.x.isUnsetCoord()) {
                  errors["finger_${idx}_x"] = "Enter X"
                }
                if (pt.y.isUnsetCoord()) {
                  errors["finger_${idx}_y"] = "Enter Y"
                }
              }
            }
          }
          MultiTouchMode.PINCH_SPREAD,
          MultiTouchMode.ROTATE -> {
            if (action.centerX.isUnsetCoord()) {
              errors["center_x"] = "Pick center spot or enter X"
            }
            if (action.centerY.isUnsetCoord()) {
              errors["center_y"] = "Pick center spot or enter Y"
            }
          }
        }
      }

      is ScriptAction.SwipeDrag -> {
        validateNumericCoord(action.startX, "start_x", "Start X", errors)
        validateNumericCoord(action.startY, "start_y", "Start Y", errors)
        validateNumericCoord(action.endX, "end_x", "End X", errors)
        validateNumericCoord(action.endY, "end_y", "End Y", errors)
        if (action.trajectoryMode == SwipeTrajectoryMode.MULTI_POINT_PATH) {
          action.multiPointPath.forEachIndexed { idx, pt ->
            if (pt.x.isUnsetCoord()) errors["path_${idx}_x"] = "Enter X"
            if (pt.y.isUnsetCoord()) errors["path_${idx}_y"] = "Enter Y"
          }
        }
      }

      is ScriptAction.InputText -> {
        if (action.tapToFocus) {
          val fx = action.focusX
          val fy = action.focusY
          if (fx == null || fx.isUnsetCoord()) {
            errors["focus_x"] = "Pick spot or enter X"
          }
          if (fy == null || fy.isUnsetCoord()) {
            errors["focus_y"] = "Pick spot or enter Y"
          }
        }
        if (action.textTemplate.isBlank()) {
          errors["text_template"] = "Enter text to type"
        }
      }

      is ScriptAction.IfBlock -> {
        if (editingElseIfBranchId != null) {
          val branch = action.elseIfBranches.firstOrNull { it.id == editingElseIfBranchId }
          if (branch != null) {
            validateElseIfBranch(branch, errors)
          }
        } else {
          validateConditionGroup(action.conditionRoot, isRoot = true, errors = errors)
          action.elseIfBranches.forEach { branch ->
            validateElseIfBranch(branch, errors)
          }
        }
      }

      is ScriptAction.LoopBlock -> {
        when (val m = action.mode) {
          is LoopMode.Count -> {
            val cnt = m.count
            if (cnt.isBlankVarRef()) {
              errors["loop_count"] = "Choose a number variable"
            } else if (cnt.isUnsetTarget() || (cnt is NumericInput.Literal && cnt.value < 1)) {
              errors["loop_count"] = "Enter 1 or more repeats"
            }
          }
          is LoopMode.WhileCondition ->
            validateConditionGroup(m.conditionRoot, isRoot = true, errors = errors)
          is LoopMode.UntilCondition ->
            validateConditionGroup(m.conditionRoot, isRoot = true, errors = errors)
          LoopMode.Unlimited -> Unit
        }
      }

      is ScriptAction.LaunchApp -> {
        if (action.packageName.isBlank()) {
          errors["app_package"] = "Select a target app"
        }
      }

      is ScriptAction.ForceStopApp -> {
        if (action.packageName.isBlank()) {
          errors["app_package"] = "Select a target app"
        }
      }

      is ScriptAction.ClipboardOp -> {
        if (action.operation == ClipboardActionType.COPY && action.valueTemplate.isBlank()) {
          errors["clipboard_text"] = "Enter text to copy"
        }
      }

      is ScriptAction.SetVariable -> {
        if (action.targetKey.isBlank()) errors["var_target_key"] = "Enter a variable name"
        if (action.valueTemplate.isBlank()) errors["var_value"] = "Enter a value to save"
      }

      is ScriptAction.MathOp -> {
        if (action.targetKey.isBlank()) errors["var_target_key"] = "Enter a variable name"
        if (action.leftOperand.isBlank()) errors["var_left"] = "Enter a number or {variable}"
        val isUnary =
          action.operator in
            setOf(MathOperator.ROUND, MathOperator.FLOOR, MathOperator.CEIL, MathOperator.ABS)
        if (!isUnary && action.rightOperand.isBlank()) {
          errors["var_right"] = "Enter a second number or {variable}"
        }
      }

      is ScriptAction.RandomOp -> {
        if (action.targetKey.isBlank()) errors["var_target_key"] = "Enter a variable name"
      }

      is ScriptAction.StringOp -> {
        if (action.targetKey.isBlank()) errors["var_target_key"] = "Enter a variable name"
        if (action.inputA.isBlank()) errors["var_input_a"] = "Enter text or {variable}"
        if (action.operator in
          setOf(StringOperator.CONCATENATE, StringOperator.REPLACE, StringOperator.REPLACE_REGEX) &&
          action.inputB.isBlank()
        ) {
          errors["var_input_b"] =
            if (action.operator == StringOperator.CONCATENATE) {
              "Enter text to add"
            } else {
              "Enter Find|Replace text"
            }
        }
      }

      is ScriptAction.BooleanOp -> {
        if (action.targetKey.isBlank()) errors["var_target_key"] = "Enter a variable name"
        if (action.leftKey.isBlank()) errors["var_left_key"] = "Enter a Yes/No variable name"
        if (action.operator != BooleanOperator.TOGGLE && action.rightKey.isNullOrBlank()) {
          errors["var_right_key"] = "Enter a second Yes/No variable name"
        }
      }

      is ScriptAction.TypeCastOp -> {
        if (action.targetKey.isBlank()) errors["var_target_key"] = "Enter a variable name"
        if (action.sourceKey.isBlank()) errors["var_source_key"] = "Enter the variable to convert"
      }

      is ScriptAction.Delay,
      is ScriptAction.Break,
      is ScriptAction.Continue,
      is ScriptAction.EndScript,
      is ScriptAction.KeyEvent,
      is ScriptAction.Vibrate,
      is ScriptAction.PlayAlertSound -> Unit
    }
    return errors
  }

  private fun validateNumericCoord(
    input: NumericInput,
    key: String,
    axisLabel: String,
    errors: MutableMap<String, String>,
  ) {
    when (input) {
      is NumericInput.Literal -> {
        if (input.value.isUnsetCoord()) {
          errors[key] = "Pick a spot on screen or enter $axisLabel"
        }
      }
      is NumericInput.VariableRef -> {
        if (input.variableKey.isBlank()) {
          errors[key] = "Choose a number variable for $axisLabel"
        }
      }
    }
  }

  fun validateElseIfBranch(
    branch: ScriptAction.ElseIfBranch,
    errors: MutableMap<String, String>,
  ) {
    validateConditionGroup(branch.conditionRoot, isRoot = true, errors = errors)
  }

  private fun validateConditionGroup(
    group: ConditionGroup,
    isRoot: Boolean,
    errors: MutableMap<String, String>,
  ) {
    if (group.children.isEmpty()) {
      errors["cond_group_${group.id}"] =
        if (isRoot) {
          "Add at least 1 condition rule"
        } else {
          "Add a rule to this sub-group or delete it"
        }
      return
    }
    group.children.forEach { child ->
      when (child) {
        is ConditionLeaf -> validateConditionLeaf(child, errors)
        is ConditionGroup -> validateConditionGroup(child, isRoot = false, errors = errors)
      }
    }
  }

  private fun validateConditionLeaf(
    leaf: ConditionLeaf,
    errors: MutableMap<String, String>,
  ) {
    val prefix = "cond_leaf_${leaf.id}"
    when (val predicate = leaf.predicate) {
      is ConditionPredicate.ColorMatch -> {
        if (predicate.targets.isEmpty()) {
          errors["${prefix}_color"] = "Pick a screen spot and color"
        } else {
          predicate.targets.forEachIndexed { idx, target ->
            if (target.x.isUnsetCoord()) {
              errors["${prefix}_target_${idx}_x"] = "Enter X"
            }
            if (target.y.isUnsetCoord()) {
              errors["${prefix}_target_${idx}_y"] = "Enter Y"
            }
            if (!isValidHexColor(target.hexColor)) {
              errors["${prefix}_target_${idx}_hex"] = "Pick a color or enter HEX (e.g. #FF0000)"
            }
          }
        }
        if (predicate.mode == ColorMatchMode.REGION) {
          val reg = predicate.region
          if (reg == null || reg.x1.isUnsetCoord() || reg.y1.isUnsetCoord() || reg.x2.isUnsetCoord() || reg.y2.isUnsetCoord()) {
            errors["${prefix}_region"] = "Pick or enter all 4 area edges"
          }
        }
      }

      is ConditionPredicate.ImageMatch -> {
        if (predicate.templatePngPath.isBlank()) {
          errors["${prefix}_image"] = "Pick an image from Saved Inspect first"
        }
      }

      is ConditionPredicate.AppMatch -> {
        if (predicate.packageName.isBlank()) {
          errors["${prefix}_app"] = "Choose an app to check"
        }
      }

      is ConditionPredicate.VariableCompare -> {
        if (predicate.variableKey.isBlank()) {
          errors["${prefix}_var_key"] = "Choose or enter a variable"
        }
        when (predicate.variableType) {
          VariableType.NUMBER -> {
            val raw = predicate.expectedValue.trim()
            val isVarRef = raw.startsWith("{") && raw.endsWith("}") && raw.length > 2
            if (raw.isEmpty() || (!isVarRef && raw.toDoubleOrNull() == null)) {
              errors["${prefix}_var_expected"] = "Enter a number to compare"
            }
          }
          VariableType.STRING -> {
            val raw = predicate.expectedValue
            val op = predicate.stringOp ?: StringCompareOp.EQ
            if (raw.isBlank()) {
              errors["${prefix}_var_expected"] = "Enter a value to compare"
            } else if (op in setOf(StringCompareOp.LENGTH_GT, StringCompareOp.LENGTH_LT, StringCompareOp.LENGTH_EQ) &&
              raw.trim().toIntOrNull() == null
            ) {
              errors["${prefix}_var_expected"] = "Enter a whole number for text length"
            }
          }
          VariableType.BOOLEAN -> Unit
        }
      }
    }
  }
}

/** Plain-English formatting helpers for UI cards, action summaries, and condition previews. */
object PlainLanguageFormatter {

  fun logicOperatorTitle(op: LogicOperator): String =
    when (op) {
      LogicOperator.ALL -> "All True (AND)"
      LogicOperator.ANY -> "Any True (OR)"
      LogicOperator.NONE -> "None True (NOT)"
    }

  fun logicOperatorHelper(op: LogicOperator): String =
    when (op) {
      LogicOperator.ALL -> "Every rule below must pass"
      LogicOperator.ANY -> "At least 1 rule below must pass"
      LogicOperator.NONE -> "Pass only if all rules fail"
    }

  fun logicOperatorShortHeader(op: LogicOperator): String =
    when (op) {
      LogicOperator.ALL -> "Sub-Group: Match ALL of these"
      LogicOperator.ANY -> "Sub-Group: Match ANY of these"
      LogicOperator.NONE -> "Sub-Group: Match NONE of these"
    }

  fun systemKeyLabel(key: SystemKeyType): String =
    when (key) {
      SystemKeyType.HOME -> "Home"
      SystemKeyType.BACK -> "Back"
      SystemKeyType.RECENTS -> "Recent Apps"
      SystemKeyType.POWER_DIALOG -> "Power Menu"
      SystemKeyType.VOLUME_UP -> "Volume Up"
      SystemKeyType.VOLUME_DOWN -> "Volume Down"
      SystemKeyType.NOTIFICATIONS -> "Notifications"
      SystemKeyType.QUICK_SETTINGS -> "Quick Settings"
      SystemKeyType.TAKE_SCREENSHOT -> "Take Screenshot"
    }

  fun clipboardOpLabel(op: ClipboardActionType): String =
    when (op) {
      ClipboardActionType.COPY -> "Copy Text"
      ClipboardActionType.PASTE -> "Paste"
      ClipboardActionType.CLEAR -> "Clear Clipboard"
    }

  fun vibratePatternLabel(pattern: VibratePattern): String =
    when (pattern) {
      VibratePattern.SHORT -> "Short Buzz"
      VibratePattern.LONG -> "Long Buzz"
      VibratePattern.CUSTOM -> "Custom Pattern"
    }

  fun swipeStyleLabel(mode: SwipeTrajectoryMode): String =
    when (mode) {
      SwipeTrajectoryMode.LINEAR -> "Straight Line"
      SwipeTrajectoryMode.BEZIER_CURVE -> "Curved (Human-like)"
      SwipeTrajectoryMode.MULTI_POINT_PATH -> "Custom Path"
    }

  fun textEngineLabel(engine: TextInjectionEngine): String =
    when (engine) {
      TextInjectionEngine.ACCESSIBILITY_SET_TEXT -> "Instant Fill"
      TextInjectionEngine.CLIPBOARD_PASTE -> "Paste Text"
      TextInjectionEngine.HUMAN_TYPING -> "Type Slowly"
    }

  fun mathOpLabel(op: MathOperator): String =
    when (op) {
      MathOperator.ADD -> "Add (+)"
      MathOperator.SUBTRACT -> "Subtract (-)"
      MathOperator.MULTIPLY -> "Multiply (×)"
      MathOperator.DIVIDE -> "Divide (÷)"
      MathOperator.MODULO -> "Remainder (%)"
      MathOperator.ROUND -> "Round"
      MathOperator.FLOOR -> "Round Down"
      MathOperator.CEIL -> "Round Up"
      MathOperator.ABS -> "Positive Only"
    }

  fun mathOpSymbol(op: MathOperator): String =
    when (op) {
      MathOperator.ADD -> "+"
      MathOperator.SUBTRACT -> "-"
      MathOperator.MULTIPLY -> "×"
      MathOperator.DIVIDE -> "÷"
      MathOperator.MODULO -> "%"
      MathOperator.ROUND -> "Round"
      MathOperator.FLOOR -> "Round Down"
      MathOperator.CEIL -> "Round Up"
      MathOperator.ABS -> "Positive"
    }

  fun randomTargetLabel(type: RandomTargetType): String =
    when (type) {
      RandomTargetType.INT -> "Whole Number"
      RandomTargetType.FLOAT -> "Decimal"
      RandomTargetType.BOOLEAN -> "Yes / No"
    }

  fun stringOpLabel(op: StringOperator): String =
    when (op) {
      StringOperator.CONCATENATE -> "Join Text"
      StringOperator.TRIM -> "Remove Extra Spaces"
      StringOperator.REPLACE -> "Replace Text"
      StringOperator.REPLACE_REGEX -> "Pattern Replace"
      StringOperator.SUBSTRING -> "Cut Part of Text"
    }

  fun booleanOpLabel(op: BooleanOperator): String =
    when (op) {
      BooleanOperator.TOGGLE -> "Flip (Yes ↔ No)"
      BooleanOperator.AND -> "Both Yes (AND)"
      BooleanOperator.OR -> "Either Yes (OR)"
    }

  fun typeCastLabel(op: TypeCastOperator): String =
    when (op) {
      TypeCastOperator.STRING_TO_NUMBER -> "Text ➔ Number"
      TypeCastOperator.NUMBER_TO_STRING -> "Number ➔ Text"
      TypeCastOperator.BOOLEAN_TO_STRING -> "Yes/No ➔ Text"
      TypeCastOperator.BOOLEAN_TO_NUMBER -> "Yes/No ➔ 1 or 0"
    }

  fun stringCompareLabel(op: StringCompareOp): String =
    when (op) {
      StringCompareOp.EQ -> "is"
      StringCompareOp.NE -> "is not"
      StringCompareOp.CONTAINS -> "contains"
      StringCompareOp.LENGTH_GT -> "length is longer than"
      StringCompareOp.LENGTH_LT -> "length is shorter than"
      StringCompareOp.LENGTH_EQ -> "length equals"
    }

  fun numberCompareLabel(op: NumberCompareOp): String =
    when (op) {
      NumberCompareOp.GT -> "is greater than"
      NumberCompareOp.GTE -> "is at least"
      NumberCompareOp.LT -> "is less than"
      NumberCompareOp.LTE -> "is at most"
      NumberCompareOp.EQ -> "equals"
      NumberCompareOp.NE -> "does not equal"
    }

  fun booleanCompareLabel(op: BooleanCompareOp): String =
    when (op) {
      BooleanCompareOp.IS_TRUE -> "is Yes (True)"
      BooleanCompareOp.IS_FALSE -> "is No (False)"
    }

  fun friendlyColorName(hex: String): String {
    if (!isValidHexColor(hex)) return "selected color"
    val clean = hex.trim().removePrefix("#")
    val rgbHex = if (clean.length == 8) clean.substring(2) else clean
    val rgb = rgbHex.toIntOrNull(16) ?: return "#${rgbHex.uppercase()}"
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return when {
      r >= 242 && g >= 242 && b >= 242 -> "White"
      r <= 18 && g <= 18 && b <= 18 -> "Black"
      abs(r - g) <= 14 && abs(g - b) <= 14 ->
        when {
          r > 180 -> "Light Gray"
          r > 95 -> "Gray"
          else -> "Dark Gray"
        }
      r >= 200 && g <= 65 && b <= 65 -> "Red"
      r <= 65 && g >= 180 && b <= 80 -> "Green"
      r <= 70 && g <= 120 && b >= 200 -> "Blue"
      r >= 210 && g >= 200 && b <= 80 -> "Yellow"
      r >= 210 && g in 95..185 && b <= 65 -> "Orange"
      r <= 70 && g >= 190 && b >= 190 -> "Cyan"
      r >= 165 && g <= 90 && b >= 165 -> "Purple"
      else -> "#${rgbHex.uppercase()}"
    }
  }

  fun extractFirstColorHex(group: ConditionGroup): String? {
    for (node in group.children) {
      when (node) {
        is ConditionLeaf -> {
          val p = node.predicate
          if (p is ConditionPredicate.ColorMatch) {
            val valid = p.targets.firstOrNull { isValidHexColor(it.hexColor) }
            if (valid != null) return valid.hexColor
          }
        }
        is ConditionGroup -> {
          extractFirstColorHex(node)?.let { return it }
        }
      }
    }
    return null
  }

  fun formatConditionClause(group: ConditionGroup): String {
    if (group.children.isEmpty()) return ""
    val joiner =
      when (group.operator) {
        LogicOperator.ALL -> " AND "
        LogicOperator.ANY -> " OR "
        LogicOperator.NONE -> " OR "
      }
    val parts =
      group.children.mapNotNull { child ->
        when (child) {
          is ConditionLeaf -> formatLeafClause(child)
          is ConditionGroup -> {
            val sub = formatConditionClause(child)
            if (sub.isBlank()) null else "($sub)"
          }
        }
      }
    if (parts.isEmpty()) return ""
    val joined = parts.joinToString(joiner)
    return if (group.operator == LogicOperator.NONE) {
      "NONE of ($joined)"
    } else {
      joined
    }
  }

  fun formatLeafClause(leaf: ConditionLeaf): String {
    if (leaf.label.isNotBlank()) {
      return if (leaf.inverted) "NOT ${leaf.label.trim()}" else leaf.label.trim()
    }
    return when (val p = leaf.predicate) {
      is ConditionPredicate.ColorMatch -> {
        val t = p.targets.firstOrNull()
        val colorWord = t?.let { friendlyColorName(it.hexColor) } ?: "selected color"
        when (p.mode) {
          ColorMatchMode.SINGLE_POINT -> {
            val posText =
              if (t != null && !t.x.isUnsetCoord() && !t.y.isUnsetCoord()) {
                "at (${t.x}, ${t.y})"
              } else {
                "(pick spot)"
              }
            val verb = if (leaf.inverted) "is NOT" else "is"
            "screen color $posText $verb $colorWord"
          }
          ColorMatchMode.MULTI_POINT -> {
            val count = p.targets.size
            val verb = if (leaf.inverted) "do NOT match" else "match"
            "$count screen spots $verb"
          }
          ColorMatchMode.REGION -> {
            val reg = p.region
            val areaText =
              if (reg != null && !reg.x1.isUnsetCoord()) {
                "(${reg.x1},${reg.y1})–(${reg.x2},${reg.y2})"
              } else {
                "selected area"
              }
            val verb = if (leaf.inverted) "does NOT match" else "matches"
            "Area $areaText $verb $colorWord"
          }
        }
      }

      is ConditionPredicate.ImageMatch -> {
        val fileName =
          p.templatePngPath
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "Image" }
        val stateWord = if (leaf.inverted) "is NOT visible" else "is visible"
        "'$fileName' $stateWord"
      }

      is ConditionPredicate.AppMatch -> {
        val appName = p.appLabel.ifBlank { p.packageName }.ifBlank { "selected app" }
        val verb = if (leaf.inverted) "is NOT open" else "is open"
        "app $appName $verb"
      }

      is ConditionPredicate.VariableCompare -> {
        val key = p.variableKey.ifBlank { "variable" }
        val expr =
          when (p.variableType) {
            VariableType.STRING -> {
              val opText = stringCompareLabel(p.stringOp ?: StringCompareOp.EQ)
              val expected = if (p.expectedValue.isBlank()) "..." else "\"${p.expectedValue}\""
              "$key $opText $expected"
            }
            VariableType.NUMBER -> {
              val opText = numberCompareLabel(p.numberOp ?: NumberCompareOp.EQ)
              val expected = p.expectedValue.ifBlank { "..." }
              "$key $opText $expected"
            }
            VariableType.BOOLEAN -> {
              val opText = booleanCompareLabel(p.booleanOp ?: BooleanCompareOp.IS_TRUE)
              "$key $opText"
            }
          }
        if (leaf.inverted) "NOT ($expr)" else expr
      }
    }
  }

  fun formatConditionSentence(
    root: ConditionGroup,
    prefix: String = "If:",
    emptyText: String = "No rules added yet — tap '+ Add Rule' below",
  ): String {
    val clause = formatConditionClause(root)
    return if (clause.isBlank()) emptyText else "$prefix $clause"
  }
}
