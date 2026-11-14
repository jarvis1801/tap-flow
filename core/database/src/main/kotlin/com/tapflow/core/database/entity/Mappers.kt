package com.tapflow.core.database.entity

import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.PipelineSessionLog
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script

fun ScriptEntity.toDomain(): Script =
  Script(
    id = id,
    title = title,
    description = description,
    tags = tags,
    globalDelay = globalDelay,
    variables = variables,
    actions = actions,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
  )

fun Script.toEntity(): ScriptEntity =
  ScriptEntity(
    id = id,
    title = title,
    description = description,
    tags = tags,
    globalDelay = globalDelay,
    variables = variables,
    actions = actions,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
  )

fun PipelineEntity.toDomain(): Pipeline =
  Pipeline(
    id = id,
    title = title,
    description = description,
    tags = tags,
    steps = steps,
    repeatMode = repeatMode,
    errorStrategy = errorStrategy,
    crashRecoveryAppPackage = crashRecoveryAppPackage,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
  )

fun Pipeline.toEntity(): PipelineEntity =
  PipelineEntity(
    id = id,
    title = title,
    description = description,
    tags = tags,
    steps = steps,
    repeatMode = repeatMode,
    errorStrategy = errorStrategy,
    crashRecoveryAppPackage = crashRecoveryAppPackage,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
  )

fun InspectGroupEntity.toDomain(): InspectGroup =
  InspectGroup(
    id = id,
    title = title,
    tags = tags,
    createdAtMs = createdAtMs,
  )

fun InspectGroup.toEntity(): InspectGroupEntity =
  InspectGroupEntity(
    id = id,
    title = title,
    tags = tags,
    createdAtMs = createdAtMs,
  )

fun InspectItemEntity.toDomain(): InspectItem =
  InspectItem(
    id = id,
    groupId = groupId,
    fullScreenPngPath = fullScreenPngPath,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    appName = appName,
    packageName = packageName,
    pixels = pixels,
    crops = crops,
    createdAtMs = createdAtMs,
  )

fun InspectItem.toEntity(): InspectItemEntity =
  InspectItemEntity(
    id = id,
    groupId = groupId,
    fullScreenPngPath = fullScreenPngPath,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    appName = appName,
    packageName = packageName,
    pixels = pixels,
    crops = crops,
    createdAtMs = createdAtMs,
  )

fun ExecutionSessionLogEntity.toDomain(): ExecutionSessionLog =
  ExecutionSessionLog(
    id = id,
    scriptId = scriptId,
    scriptTitle = scriptTitle,
    status = status,
    startedAtMs = startedAtMs,
    finishedAtMs = finishedAtMs,
    totalStepsExecuted = totalStepsExecuted,
    entries = entries,
    errorReason = errorReason,
    errorScreenshotPath = errorScreenshotPath,
  )

fun ExecutionSessionLog.toEntity(): ExecutionSessionLogEntity =
  ExecutionSessionLogEntity(
    id = id,
    scriptId = scriptId,
    scriptTitle = scriptTitle,
    status = status,
    startedAtMs = startedAtMs,
    finishedAtMs = finishedAtMs,
    totalStepsExecuted = totalStepsExecuted,
    entries = entries,
    errorReason = errorReason,
    errorScreenshotPath = errorScreenshotPath,
  )

fun PipelineSessionLogEntity.toDomain(): PipelineSessionLog =
  PipelineSessionLog(
    id = id,
    pipelineId = pipelineId,
    pipelineTitle = pipelineTitle,
    status = status,
    startedAtMs = startedAtMs,
    finishedAtMs = finishedAtMs,
    stepRuns = stepRuns,
    errorReason = errorReason,
  )

fun PipelineSessionLog.toEntity(): PipelineSessionLogEntity =
  PipelineSessionLogEntity(
    id = id,
    pipelineId = pipelineId,
    pipelineTitle = pipelineTitle,
    status = status,
    startedAtMs = startedAtMs,
    finishedAtMs = finishedAtMs,
    stepRuns = stepRuns,
    errorReason = errorReason,
  )
