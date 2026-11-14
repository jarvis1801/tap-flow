package com.tapflow.core.model.log

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionStatus {
  SUCCESS,
  FAILED,
  STOPPED,
}

@Immutable
@Serializable
data class ConditionEvalTrace(
  val nodeId: String,
  val label: String,
  val passed: Boolean,
  val actualSummary: String,
  val expectedSummary: String,
  val children: List<ConditionEvalTrace> = emptyList(),
)

@Immutable
@Serializable
data class ActionLogEntry(
  val id: String,
  val stepLabel: String,
  val actionType: String,
  val summary: String,
  val timestampMs: Long,
  val durationMs: Long,
  val status: ExecutionStatus,
  val errorMessage: String? = null,
  val conditionTrace: ConditionEvalTrace? = null,
)

@Immutable
@Serializable
data class ExecutionSessionLog(
  val id: String,
  val scriptId: String,
  val scriptTitle: String,
  val status: ExecutionStatus,
  val startedAtMs: Long,
  val finishedAtMs: Long,
  val totalStepsExecuted: Int,
  val entries: List<ActionLogEntry> = emptyList(),
  val errorReason: String? = null,
  val errorScreenshotPath: String? = null,
)

@Immutable
@Serializable
data class PipelineStepRunLog(
  val stepIndex: Int,
  val runNumber: Int,
  val scriptSessionLog: ExecutionSessionLog,
)

@Immutable
@Serializable
data class PipelineSessionLog(
  val id: String,
  val pipelineId: String,
  val pipelineTitle: String,
  val status: ExecutionStatus,
  val startedAtMs: Long,
  val finishedAtMs: Long,
  val stepRuns: List<PipelineStepRunLog> = emptyList(),
  val errorReason: String? = null,
)
