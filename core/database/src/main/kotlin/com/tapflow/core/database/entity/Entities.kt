package com.tapflow.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectPixel
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.log.ExecutionStatus
import com.tapflow.core.model.log.PipelineStepRunLog
import com.tapflow.core.model.pipeline.PipelineErrorStrategy
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.model.pipeline.PipelineStep

@Entity(tableName = "scripts")
data class ScriptEntity(
  @PrimaryKey val id: String,
  val title: String,
  val description: String,
  val tags: List<String>,
  val globalDelay: DelayConfig,
  val variables: List<VariableDefinition>,
  val actions: List<ScriptAction>,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Entity(tableName = "pipelines")
data class PipelineEntity(
  @PrimaryKey val id: String,
  val title: String,
  val description: String,
  val tags: List<String>,
  val steps: List<PipelineStep>,
  val repeatMode: PipelineRepeatMode,
  val errorStrategy: PipelineErrorStrategy,
  val crashRecoveryAppPackage: String?,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Entity(tableName = "inspect_groups")
data class InspectGroupEntity(
  @PrimaryKey val id: String,
  val title: String,
  val tags: List<String>,
  val createdAtMs: Long,
)

@Entity(
  tableName = "inspect_items",
  foreignKeys = [
    ForeignKey(
      entity = InspectGroupEntity::class,
      parentColumns = ["id"],
      childColumns = ["groupId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["groupId"])],
)
data class InspectItemEntity(
  @PrimaryKey val id: String,
  val groupId: String,
  val fullScreenPngPath: String,
  val screenWidth: Int,
  val screenHeight: Int,
  val appName: String,
  val packageName: String,
  val pixels: List<InspectPixel>,
  val crops: List<InspectCrop>,
  val createdAtMs: Long,
)

@Entity(
  tableName = "execution_session_logs",
  indices = [Index(value = ["scriptId"]), Index(value = ["startedAtMs"])],
)
data class ExecutionSessionLogEntity(
  @PrimaryKey val id: String,
  val scriptId: String,
  val scriptTitle: String,
  val status: ExecutionStatus,
  val startedAtMs: Long,
  val finishedAtMs: Long,
  val totalStepsExecuted: Int,
  val entries: List<ActionLogEntry>,
  val errorReason: String?,
  val errorScreenshotPath: String?,
)

@Entity(
  tableName = "pipeline_session_logs",
  indices = [Index(value = ["pipelineId"]), Index(value = ["startedAtMs"])],
)
data class PipelineSessionLogEntity(
  @PrimaryKey val id: String,
  val pipelineId: String,
  val pipelineTitle: String,
  val status: ExecutionStatus,
  val startedAtMs: Long,
  val finishedAtMs: Long,
  val stepRuns: List<PipelineStepRunLog>,
  val errorReason: String?,
)
