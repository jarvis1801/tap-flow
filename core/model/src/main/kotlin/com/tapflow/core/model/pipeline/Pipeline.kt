package com.tapflow.core.model.pipeline

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface PipelineRepeatMode {
  @Immutable
  @Serializable
  @SerialName("run_once")
  data object RunOnce : PipelineRepeatMode

  @Immutable
  @Serializable
  @SerialName("count")
  data class Count(val iterations: Int = 1) : PipelineRepeatMode

  @Immutable
  @Serializable
  @SerialName("unlimited")
  data object Unlimited : PipelineRepeatMode
}

@Serializable
enum class PipelineErrorStrategy {
  ABORT,
  SKIP_AND_CONTINUE,
}

@Immutable
@Serializable
data class PipelineStep(
  val id: String,
  val scriptId: String,
  val repeatCount: Int = 1,
)

@Immutable
@Serializable
data class Pipeline(
  val id: String,
  val title: String,
  val description: String = "",
  val tags: List<String> = emptyList(),
  val steps: List<PipelineStep> = emptyList(),
  val repeatMode: PipelineRepeatMode = PipelineRepeatMode.RunOnce,
  val errorStrategy: PipelineErrorStrategy = PipelineErrorStrategy.ABORT,
  val crashRecoveryAppPackage: String? = null,
  val createdAtMs: Long = 0L,
  val updatedAtMs: Long = 0L,
)
