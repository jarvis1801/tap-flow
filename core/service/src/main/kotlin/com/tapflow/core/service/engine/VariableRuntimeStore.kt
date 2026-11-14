package com.tapflow.core.service.engine

import com.tapflow.core.model.action.BooleanOperator
import com.tapflow.core.model.action.MathOperator
import com.tapflow.core.model.action.RandomTargetType
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.StringOperator
import com.tapflow.core.model.action.TypeCastOperator
import com.tapflow.core.model.common.DurationInput
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.common.VariableType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random

data class RuntimeVariableEntry(
  val key: String,
  val type: VariableType,
  val rawValue: String,
)

sealed interface VariableOpResult {
  data class Success(val summary: String) : VariableOpResult
  data class Failure(val reason: String) : VariableOpResult
}

/**
 * Strictly in-memory runtime variable store. Within a Pipeline session, the same instance
 * is reused across iterations and repeated script invocations so variables do NOT reset
 * unless explicitly overwritten by SetVariable / VariableOp.
 */
class VariableRuntimeStore(
  private val clipboardReader: suspend () -> String? = { null },
  private val random: Random = Random.Default,
) {
  companion object {
    private val TOKEN_REGEX = Regex("\\{([A-Za-z0-9_]+)\\}")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
  }

  private val entries = ConcurrentHashMap<String, RuntimeVariableEntry>()

  /** Seeds variable defaults only if not already present in this session. */
  fun seedIfAbsent(definitions: List<VariableDefinition>) {
    for (def in definitions) {
      if (def.key.isBlank()) continue
      entries.putIfAbsent(
        def.key,
        RuntimeVariableEntry(
          key = def.key,
          type = def.type,
          rawValue = normalizeValueForType(def.defaultValue, def.type),
        ),
      )
    }
  }

  fun clear() {
    entries.clear()
  }

  fun snapshot(): Map<String, RuntimeVariableEntry> = entries.toMap()

  fun getEntry(key: String): RuntimeVariableEntry? = entries[key]

  fun getString(key: String): String = entries[key]?.rawValue ?: ""

  fun getNumber(key: String): Double? = entries[key]?.rawValue?.trim()?.toDoubleOrNull()

  fun getBoolean(key: String): Boolean {
    val raw = entries[key]?.rawValue?.trim()?.lowercase() ?: return false
    return raw == "true" || raw == "1" || raw == "yes"
  }

  fun setDirect(key: String, type: VariableType, value: String) {
    if (key.isBlank()) return
    entries[key] =
      RuntimeVariableEntry(
        key = key,
        type = type,
        rawValue = normalizeValueForType(value, type),
      )
  }

  fun resolveNumericInput(input: NumericInput): Int {
    return when (input) {
      is NumericInput.Literal -> input.value
      is NumericInput.VariableRef -> getNumber(input.variableKey)?.roundToInt() ?: 0
    }
  }

  fun resolveDurationInput(input: DurationInput, minMs: Long = 1L): Long {
    return when (input) {
      is DurationInput.Fixed -> input.ms.coerceAtLeast(minMs)
      is DurationInput.RandomRange -> {
        val safeMin = input.minMs.coerceAtLeast(minMs)
        val safeMax = input.maxMs.coerceAtLeast(safeMin)
        if (safeMin == safeMax) safeMin else random.nextLong(safeMin, safeMax + 1L)
      }
      is DurationInput.VariableRef ->
        (getNumber(input.variableKey)?.toLong() ?: minMs).coerceAtLeast(minMs)
    }
  }

  suspend fun interpolate(template: String): String {
    if (!template.contains('{')) return template
    val needsClipboard = template.contains("{CLIPBOARD}")
    val cachedClipboard = if (needsClipboard) clipboardReader().orEmpty() else ""

    return TOKEN_REGEX.replace(template) { match ->
      val token = match.groupValues[1]
      when (token) {
        "TIMESTAMP" -> System.currentTimeMillis().toString()
        "DATE" -> LocalDate.now().toString()
        "TIME" -> LocalTime.now().format(TIME_FORMATTER)
        "RANDOM_NUM_4" -> random.nextInt(1000, 10000).toString()
        "CLIPBOARD" -> cachedClipboard
        else -> entries[token]?.rawValue ?: match.value
      }
    }
  }

  suspend fun executeVariableOp(op: ScriptAction.VariableOp): VariableOpResult {
    return when (op) {
      is ScriptAction.SetVariable -> {
        if (op.targetKey.isBlank()) {
          return VariableOpResult.Failure("Target variable key is blank.")
        }
        val resolved = interpolate(op.valueTemplate)
        val existingType = entries[op.targetKey]?.type ?: inferVariableType(resolved)
        setDirect(op.targetKey, existingType, resolved)
        VariableOpResult.Success("${op.targetKey} = ${getString(op.targetKey)}")
      }

      is ScriptAction.MathOp -> executeMathOp(op)
      is ScriptAction.RandomOp -> executeRandomOp(op)
      is ScriptAction.StringOp -> executeStringOp(op)
      is ScriptAction.BooleanOp -> executeBooleanOp(op)
      is ScriptAction.TypeCastOp -> executeTypeCastOp(op)
    }
  }

  private suspend fun executeMathOp(op: ScriptAction.MathOp): VariableOpResult {
    if (op.targetKey.isBlank()) return VariableOpResult.Failure("MathOp target key is blank.")
    val left = resolveNumberOperand(op.leftOperand)
      ?: return VariableOpResult.Failure("Invalid left operand: '${op.leftOperand}'.")

    val resultValue =
      when (op.operator) {
        MathOperator.ROUND -> round(left)
        MathOperator.FLOOR -> floor(left)
        MathOperator.CEIL -> ceil(left)
        MathOperator.ABS -> abs(left)
        else -> {
          val right = resolveNumberOperand(op.rightOperand)
            ?: return VariableOpResult.Failure("Invalid right operand: '${op.rightOperand}'.")
          when (op.operator) {
            MathOperator.ADD -> left + right
            MathOperator.SUBTRACT -> left - right
            MathOperator.MULTIPLY -> left * right
            MathOperator.DIVIDE -> {
              if (right == 0.0) return VariableOpResult.Failure("Division by zero.")
              left / right
            }
            MathOperator.MODULO -> {
              if (right == 0.0) return VariableOpResult.Failure("Modulo by zero.")
              left % right
            }
            else -> left
          }
        }
      }

    val formatted = formatNumber(resultValue)
    setDirect(op.targetKey, VariableType.NUMBER, formatted)
    return VariableOpResult.Success("${op.targetKey} = $formatted (${op.operator.name})")
  }

  private fun executeRandomOp(op: ScriptAction.RandomOp): VariableOpResult {
    if (op.targetKey.isBlank()) return VariableOpResult.Failure("RandomOp target key is blank.")
    return when (op.targetType) {
      RandomTargetType.INT -> {
        val lo = minOf(op.minValue, op.maxValue).roundToInt()
        val hi = maxOf(op.minValue, op.maxValue).roundToInt()
        val value = if (lo == hi) lo else random.nextInt(lo, hi + 1)
        setDirect(op.targetKey, VariableType.NUMBER, value.toString())
        VariableOpResult.Success("${op.targetKey} = $value")
      }
      RandomTargetType.FLOAT -> {
        val lo = minOf(op.minValue, op.maxValue)
        val hi = maxOf(op.minValue, op.maxValue)
        val value = if (lo == hi) lo else lo + random.nextDouble() * (hi - lo)
        val formatted = String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        setDirect(op.targetKey, VariableType.NUMBER, formatted)
        VariableOpResult.Success("${op.targetKey} = $formatted")
      }
      RandomTargetType.BOOLEAN -> {
        val value = random.nextBoolean()
        setDirect(op.targetKey, VariableType.BOOLEAN, value.toString())
        VariableOpResult.Success("${op.targetKey} = $value")
      }
    }
  }

  private suspend fun executeStringOp(op: ScriptAction.StringOp): VariableOpResult {
    if (op.targetKey.isBlank()) return VariableOpResult.Failure("StringOp target key is blank.")
    val a = interpolate(op.inputA)
    val b = interpolate(op.inputB)
    val result =
      when (op.operator) {
        StringOperator.CONCATENATE -> a + b
        StringOperator.TRIM -> (a.ifBlank { getString(op.targetKey) }).trim()
        StringOperator.REPLACE -> applyStringReplace(op.targetKey, a, b, isRegex = false)
        StringOperator.REPLACE_REGEX ->
          runCatching { applyStringReplace(op.targetKey, a, b, isRegex = true) }
            .getOrElse { err ->
              return VariableOpResult.Failure("Invalid regex: ${err.message}")
            }
        StringOperator.SUBSTRING -> {
          val source = a.ifEmpty { getString(op.targetKey) }
          val start = op.startIndex.coerceIn(0, source.length)
          val end =
            if (op.endIndex <= 0) source.length
            else op.endIndex.coerceIn(start, source.length)
          source.substring(start, end)
        }
      }
    setDirect(op.targetKey, VariableType.STRING, result)
    return VariableOpResult.Success("${op.targetKey} = \"$result\"")
  }

  private fun applyStringReplace(
    targetKey: String,
    inputA: String,
    inputB: String,
    isRegex: Boolean,
  ): String {
    val existing = entries[targetKey]?.rawValue
    return if (existing != null && inputA.isNotEmpty() && !inputB.contains("|")) {
      if (isRegex) existing.replace(Regex(inputA), inputB) else existing.replace(inputA, inputB)
    } else if (inputB.contains("|")) {
      val parts = inputB.split("|", limit = 2)
      val find = parts[0]
      val replacement = parts.getOrElse(1) { "" }
      if (isRegex) inputA.replace(Regex(find), replacement) else inputA.replace(find, replacement)
    } else {
      if (isRegex) inputA.replace(Regex(inputB), "") else inputA.replace(inputB, "")
    }
  }

  private fun executeBooleanOp(op: ScriptAction.BooleanOp): VariableOpResult {
    if (op.targetKey.isBlank()) return VariableOpResult.Failure("BooleanOp target key is blank.")
    val left = resolveBooleanOperand(op.leftKey)
    val result =
      when (op.operator) {
        BooleanOperator.TOGGLE -> !left
        BooleanOperator.AND -> left && resolveBooleanOperand(op.rightKey.orEmpty())
        BooleanOperator.OR -> left || resolveBooleanOperand(op.rightKey.orEmpty())
      }
    setDirect(op.targetKey, VariableType.BOOLEAN, result.toString())
    return VariableOpResult.Success("${op.targetKey} = $result")
  }

  private fun executeTypeCastOp(op: ScriptAction.TypeCastOp): VariableOpResult {
    if (op.targetKey.isBlank()) return VariableOpResult.Failure("TypeCast target key is blank.")
    return when (op.operator) {
      TypeCastOperator.STRING_TO_NUMBER -> {
        val raw = getString(op.sourceKey).trim()
        val parsed = raw.toDoubleOrNull()
          ?: return VariableOpResult.Failure("Cannot convert '$raw' to Number.")
        val formatted = formatNumber(parsed)
        setDirect(op.targetKey, VariableType.NUMBER, formatted)
        VariableOpResult.Success("${op.targetKey} = $formatted")
      }
      TypeCastOperator.NUMBER_TO_STRING -> {
        val value = getString(op.sourceKey)
        setDirect(op.targetKey, VariableType.STRING, value)
        VariableOpResult.Success("${op.targetKey} = \"$value\"")
      }
      TypeCastOperator.BOOLEAN_TO_STRING -> {
        val value = if (getBoolean(op.sourceKey)) "true" else "false"
        setDirect(op.targetKey, VariableType.STRING, value)
        VariableOpResult.Success("${op.targetKey} = \"$value\"")
      }
      TypeCastOperator.BOOLEAN_TO_NUMBER -> {
        val value = if (getBoolean(op.sourceKey)) "1" else "0"
        setDirect(op.targetKey, VariableType.NUMBER, value)
        VariableOpResult.Success("${op.targetKey} = $value")
      }
    }
  }

  private suspend fun resolveNumberOperand(rawExpr: String): Double? {
    val trimmed = rawExpr.trim()
    if (trimmed.isEmpty()) return null
    entries[trimmed]?.let { entry ->
      entry.rawValue.trim().toDoubleOrNull()?.let { return it }
    }
    val interpolated = interpolate(trimmed).trim()
    return interpolated.toDoubleOrNull()
  }

  private fun resolveBooleanOperand(keyOrLiteral: String): Boolean {
    val trimmed = keyOrLiteral.trim()
    entries[trimmed]?.let { return getBoolean(trimmed) }
    val normalized = trimmed.lowercase()
    return normalized == "true" || normalized == "1" || normalized == "yes"
  }

  private fun inferVariableType(raw: String): VariableType {
    val trimmed = raw.trim()
    return when {
      trimmed.equals("true", ignoreCase = true) || trimmed.equals("false", ignoreCase = true) ->
        VariableType.BOOLEAN
      trimmed.toDoubleOrNull() != null -> VariableType.NUMBER
      else -> VariableType.STRING
    }
  }

  private fun normalizeValueForType(raw: String, type: VariableType): String {
    return when (type) {
      VariableType.STRING -> raw
      VariableType.NUMBER -> raw.trim().toDoubleOrNull()?.let(::formatNumber) ?: "0"
      VariableType.BOOLEAN -> {
        val norm = raw.trim().lowercase()
        if (norm == "true" || norm == "1" || norm == "yes") "true" else "false"
      }
    }
  }

  private fun formatNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) {
      asLong.toString()
    } else {
      value.toString()
    }
  }
}
