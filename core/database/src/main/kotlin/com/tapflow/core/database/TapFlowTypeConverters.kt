package com.tapflow.core.database

import androidx.room3.ColumnTypeConverter
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectPixel
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.log.ExecutionStatus
import com.tapflow.core.model.log.PipelineStepRunLog
import com.tapflow.core.model.pipeline.PipelineErrorStrategy
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.model.pipeline.PipelineStep
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class TapFlowTypeConverters {
  private val json = TapFlowJson.instance

  @ColumnTypeConverter
  fun fromStringList(value: List<String>): String =
    json.encodeToString(ListSerializer(String.serializer()), value)

  @ColumnTypeConverter
  fun toStringList(raw: String): List<String> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(String.serializer()), raw)

  @ColumnTypeConverter
  fun fromDelayConfig(value: DelayConfig): String =
    json.encodeToString(DelayConfig.serializer(), value)

  @ColumnTypeConverter
  fun toDelayConfig(raw: String): DelayConfig =
    json.decodeFromString(DelayConfig.serializer(), raw)

  @ColumnTypeConverter
  fun fromVariableList(value: List<VariableDefinition>): String =
    json.encodeToString(ListSerializer(VariableDefinition.serializer()), value)

  @ColumnTypeConverter
  fun toVariableList(raw: String): List<VariableDefinition> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(VariableDefinition.serializer()), raw)

  @ColumnTypeConverter
  fun fromScriptActionList(value: List<ScriptAction>): String =
    json.encodeToString(ListSerializer(ScriptAction.serializer()), value)

  @ColumnTypeConverter
  fun toScriptActionList(raw: String): List<ScriptAction> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(ScriptAction.serializer()), raw)

  @ColumnTypeConverter
  fun fromConditionGroup(value: ConditionGroup): String =
    json.encodeToString(ConditionGroup.serializer(), value)

  @ColumnTypeConverter
  fun toConditionGroup(raw: String): ConditionGroup =
    json.decodeFromString(ConditionGroup.serializer(), raw)

  @ColumnTypeConverter
  fun fromPipelineStepList(value: List<PipelineStep>): String =
    json.encodeToString(ListSerializer(PipelineStep.serializer()), value)

  @ColumnTypeConverter
  fun toPipelineStepList(raw: String): List<PipelineStep> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(PipelineStep.serializer()), raw)

  @ColumnTypeConverter
  fun fromPipelineRepeatMode(value: PipelineRepeatMode): String =
    json.encodeToString(PipelineRepeatMode.serializer(), value)

  @ColumnTypeConverter
  fun toPipelineRepeatMode(raw: String): PipelineRepeatMode =
    json.decodeFromString(PipelineRepeatMode.serializer(), raw)

  @ColumnTypeConverter
  fun fromPipelineErrorStrategy(value: PipelineErrorStrategy): String = value.name

  @ColumnTypeConverter
  fun toPipelineErrorStrategy(raw: String): PipelineErrorStrategy =
    runCatching { PipelineErrorStrategy.valueOf(raw) }.getOrDefault(PipelineErrorStrategy.ABORT)

  @ColumnTypeConverter
  fun fromInspectPixelList(value: List<InspectPixel>): String =
    json.encodeToString(ListSerializer(InspectPixel.serializer()), value)

  @ColumnTypeConverter
  fun toInspectPixelList(raw: String): List<InspectPixel> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(InspectPixel.serializer()), raw)

  @ColumnTypeConverter
  fun fromInspectCropList(value: List<InspectCrop>): String =
    json.encodeToString(ListSerializer(InspectCrop.serializer()), value)

  @ColumnTypeConverter
  fun toInspectCropList(raw: String): List<InspectCrop> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(InspectCrop.serializer()), raw)

  @ColumnTypeConverter
  fun fromExecutionStatus(value: ExecutionStatus): String = value.name

  @ColumnTypeConverter
  fun toExecutionStatus(raw: String): ExecutionStatus =
    runCatching { ExecutionStatus.valueOf(raw) }.getOrDefault(ExecutionStatus.STOPPED)

  @ColumnTypeConverter
  fun fromActionLogEntryList(value: List<ActionLogEntry>): String =
    json.encodeToString(ListSerializer(ActionLogEntry.serializer()), value)

  @ColumnTypeConverter
  fun toActionLogEntryList(raw: String): List<ActionLogEntry> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(ActionLogEntry.serializer()), raw)

  @ColumnTypeConverter
  fun fromPipelineStepRunLogList(value: List<PipelineStepRunLog>): String =
    json.encodeToString(ListSerializer(PipelineStepRunLog.serializer()), value)

  @ColumnTypeConverter
  fun toPipelineStepRunLogList(raw: String): List<PipelineStepRunLog> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(PipelineStepRunLog.serializer()), raw)
}
