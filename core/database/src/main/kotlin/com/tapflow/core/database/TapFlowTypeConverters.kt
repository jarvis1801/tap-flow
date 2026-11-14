package com.tapflow.core.database

import androidx.room.TypeConverter
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

  @TypeConverter
  fun fromStringList(value: List<String>): String =
    json.encodeToString(ListSerializer(String.serializer()), value)

  @TypeConverter
  fun toStringList(raw: String): List<String> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(String.serializer()), raw)

  @TypeConverter
  fun fromDelayConfig(value: DelayConfig): String =
    json.encodeToString(DelayConfig.serializer(), value)

  @TypeConverter
  fun toDelayConfig(raw: String): DelayConfig =
    json.decodeFromString(DelayConfig.serializer(), raw)

  @TypeConverter
  fun fromVariableList(value: List<VariableDefinition>): String =
    json.encodeToString(ListSerializer(VariableDefinition.serializer()), value)

  @TypeConverter
  fun toVariableList(raw: String): List<VariableDefinition> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(VariableDefinition.serializer()), raw)

  @TypeConverter
  fun fromScriptActionList(value: List<ScriptAction>): String =
    json.encodeToString(ListSerializer(ScriptAction.serializer()), value)

  @TypeConverter
  fun toScriptActionList(raw: String): List<ScriptAction> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(ScriptAction.serializer()), raw)

  @TypeConverter
  fun fromConditionGroup(value: ConditionGroup): String =
    json.encodeToString(ConditionGroup.serializer(), value)

  @TypeConverter
  fun toConditionGroup(raw: String): ConditionGroup =
    json.decodeFromString(ConditionGroup.serializer(), raw)

  @TypeConverter
  fun fromPipelineStepList(value: List<PipelineStep>): String =
    json.encodeToString(ListSerializer(PipelineStep.serializer()), value)

  @TypeConverter
  fun toPipelineStepList(raw: String): List<PipelineStep> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(PipelineStep.serializer()), raw)

  @TypeConverter
  fun fromPipelineRepeatMode(value: PipelineRepeatMode): String =
    json.encodeToString(PipelineRepeatMode.serializer(), value)

  @TypeConverter
  fun toPipelineRepeatMode(raw: String): PipelineRepeatMode =
    json.decodeFromString(PipelineRepeatMode.serializer(), raw)

  @TypeConverter
  fun fromPipelineErrorStrategy(value: PipelineErrorStrategy): String = value.name

  @TypeConverter
  fun toPipelineErrorStrategy(raw: String): PipelineErrorStrategy =
    runCatching { PipelineErrorStrategy.valueOf(raw) }.getOrDefault(PipelineErrorStrategy.ABORT)

  @TypeConverter
  fun fromInspectPixelList(value: List<InspectPixel>): String =
    json.encodeToString(ListSerializer(InspectPixel.serializer()), value)

  @TypeConverter
  fun toInspectPixelList(raw: String): List<InspectPixel> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(InspectPixel.serializer()), raw)

  @TypeConverter
  fun fromInspectCropList(value: List<InspectCrop>): String =
    json.encodeToString(ListSerializer(InspectCrop.serializer()), value)

  @TypeConverter
  fun toInspectCropList(raw: String): List<InspectCrop> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(InspectCrop.serializer()), raw)

  @TypeConverter
  fun fromExecutionStatus(value: ExecutionStatus): String = value.name

  @TypeConverter
  fun toExecutionStatus(raw: String): ExecutionStatus =
    runCatching { ExecutionStatus.valueOf(raw) }.getOrDefault(ExecutionStatus.STOPPED)

  @TypeConverter
  fun fromActionLogEntryList(value: List<ActionLogEntry>): String =
    json.encodeToString(ListSerializer(ActionLogEntry.serializer()), value)

  @TypeConverter
  fun toActionLogEntryList(raw: String): List<ActionLogEntry> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(ActionLogEntry.serializer()), raw)

  @TypeConverter
  fun fromPipelineStepRunLogList(value: List<PipelineStepRunLog>): String =
    json.encodeToString(ListSerializer(PipelineStepRunLog.serializer()), value)

  @TypeConverter
  fun toPipelineStepRunLogList(raw: String): List<PipelineStepRunLog> =
    if (raw.isBlank()) emptyList()
    else json.decodeFromString(ListSerializer(PipelineStepRunLog.serializer()), raw)
}
