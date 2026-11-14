package com.tapflow.core.model.common

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface DelayConfig {
  fun clamped(): DelayConfig

  @Immutable
  @Serializable
  @SerialName("fixed")
  data class Fixed(val durationMs: Long = 100L) : DelayConfig {
    override fun clamped(): Fixed = copy(durationMs = durationMs.coerceAtLeast(MIN_DELAY_MS))
  }

  @Immutable
  @Serializable
  @SerialName("random_range")
  data class RandomRange(val minMs: Long = 100L, val maxMs: Long = 300L) : DelayConfig {
    override fun clamped(): RandomRange {
      val safeMin = minMs.coerceAtLeast(MIN_DELAY_MS)
      val safeMax = maxMs.coerceAtLeast(safeMin)
      return RandomRange(minMs = safeMin, maxMs = safeMax)
    }
  }

  @Immutable
  @Serializable
  @SerialName("deviation")
  data class Deviation(val baseMs: Long = 200L, val deltaMs: Long = 50L) : DelayConfig {
    override fun clamped(): Deviation {
      val safeBase = baseMs.coerceAtLeast(MIN_DELAY_MS)
      val safeDelta = deltaMs.coerceAtLeast(0L)
      return Deviation(baseMs = safeBase, deltaMs = safeDelta)
    }
  }

  companion object {
    const val MIN_DELAY_MS: Long = 50L

    fun fixed(durationMs: Long): Fixed = Fixed(durationMs.coerceAtLeast(MIN_DELAY_MS))

    fun randomRange(minMs: Long, maxMs: Long): RandomRange =
      RandomRange(minMs, maxMs).clamped()

    fun deviation(baseMs: Long, deltaMs: Long): Deviation =
      Deviation(baseMs, deltaMs).clamped()
  }
}

@Immutable
@Serializable
sealed interface PostActionDelay {
  @Immutable
  @Serializable
  @SerialName("global")
  data object Global : PostActionDelay

  @Immutable
  @Serializable
  @SerialName("override")
  data class Override(val config: DelayConfig) : PostActionDelay

  @Immutable
  @Serializable
  @SerialName("off")
  data object Off : PostActionDelay
}

@Immutable
@Serializable
sealed interface NumericInput {
  @Immutable
  @Serializable
  @SerialName("literal")
  data class Literal(val value: Int) : NumericInput

  @Immutable
  @Serializable
  @SerialName("variable_ref")
  data class VariableRef(val variableKey: String) : NumericInput
}

@Immutable
@Serializable
sealed interface DurationInput {
  @Immutable
  @Serializable
  @SerialName("fixed")
  data class Fixed(val ms: Long) : DurationInput

  @Immutable
  @Serializable
  @SerialName("random_range")
  data class RandomRange(val minMs: Long, val maxMs: Long) : DurationInput

  @Immutable
  @Serializable
  @SerialName("variable_ref")
  data class VariableRef(val variableKey: String) : DurationInput
}

@Serializable
enum class VariableType(val displayLabel: String) {
  STRING("Text"),
  NUMBER("Number"),
  BOOLEAN("Yes / No"),
}

@Immutable
@Serializable
data class VariableDefinition(
  val key: String,
  val type: VariableType,
  val defaultValue: String = "",
)
