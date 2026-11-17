package com.tapflow.core.database.repository

import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.dao.ExecutionLogDao
import com.tapflow.core.database.dao.PipelineLogDao
import com.tapflow.core.database.entity.toDomain
import com.tapflow.core.database.entity.toEntity
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.PipelineSessionLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

@Singleton
class LogRepository @Inject constructor(
  private val executionLogDao: ExecutionLogDao,
  private val pipelineLogDao: PipelineLogDao,
  private val scriptAssetManager: ScriptAssetManager,
) {
  companion object {
    const val MAX_SESSIONS_RETAINED: Int = 50
    const val MAX_RETENTION_DAYS: Long = 7L
    const val MAX_RETENTION_MS: Long = MAX_RETENTION_DAYS * 24L * 60L * 60L * 1000L
  }

  fun observeScriptLogs(scriptId: String? = null): Flow<List<ExecutionSessionLog>> {
    val source =
      if (scriptId.isNullOrBlank()) {
        executionLogDao.observeAll()
      } else {
        executionLogDao.observeByScriptId(scriptId)
      }
    return source.map { list -> list.map { it.toDomain() } }
  }

  fun observePipelineLogs(pipelineId: String? = null): Flow<List<PipelineSessionLog>> {
    val source =
      if (pipelineId.isNullOrBlank()) {
        pipelineLogDao.observeAll()
      } else {
        pipelineLogDao.observeByPipelineId(pipelineId)
      }
    return source.map { list -> list.map { it.toDomain() } }
  }

  suspend fun getAllScriptLogs(): List<ExecutionSessionLog> =
    executionLogDao.getAllOnce().map { it.toDomain() }

  suspend fun getAllPipelineLogs(): List<PipelineSessionLog> =
    pipelineLogDao.getAllOnce().map { it.toDomain() }

  suspend fun findErrorScreenshotPathByLogId(logId: String): String? {
    if (logId.isBlank()) return null
    executionLogDao.getById(logId)?.errorScreenshotPath?.takeIf { it.isNotBlank() }?.let { return it }
    val pipelineLogs = pipelineLogDao.getAllOnce()
    for (pipelineLog in pipelineLogs) {
      if (pipelineLog.id == logId) {
        pipelineLog.stepRuns.firstNotNullOfOrNull {
          it.scriptSessionLog.errorScreenshotPath?.takeIf(String::isNotBlank)
        }?.let { return it }
      }
      pipelineLog.stepRuns
        .firstOrNull { it.scriptSessionLog.id == logId }
        ?.scriptSessionLog
        ?.errorScreenshotPath
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    }
    return null
  }

  suspend fun recordScriptSession(
    log: ExecutionSessionLog,
    nowUtcMs: Long = System.currentTimeMillis(),
  ) {
    executionLogDao.upsert(log.toEntity())
    enforceRetentionPolicy(nowUtcMs)
  }

  suspend fun recordPipelineSession(
    log: PipelineSessionLog,
    nowUtcMs: Long = System.currentTimeMillis(),
  ) {
    pipelineLogDao.upsert(log.toEntity())
    enforceRetentionPolicy(nowUtcMs)
  }

  suspend fun enforceRetentionPolicy(nowUtcMs: Long = System.currentTimeMillis()) {
    val cutoffMs = nowUtcMs - MAX_RETENTION_MS

    val allScriptLogs = executionLogDao.getAllOnce()
    val scriptToDelete =
      allScriptLogs.filterIndexed { index, entity ->
        index >= MAX_SESSIONS_RETAINED || entity.startedAtMs < cutoffMs
      }
    if (scriptToDelete.isNotEmpty()) {
      executionLogDao.deleteByIds(scriptToDelete.map { it.id })
      scriptAssetManager.deleteFilesIfExist(scriptToDelete.mapNotNull { it.errorScreenshotPath })
    }

    val allPipelineLogs = pipelineLogDao.getAllOnce()
    val pipelineToDelete =
      allPipelineLogs.filterIndexed { index, entity ->
        index >= MAX_SESSIONS_RETAINED || entity.startedAtMs < cutoffMs
      }
    if (pipelineToDelete.isNotEmpty()) {
      pipelineLogDao.deleteByIds(pipelineToDelete.map { it.id })
      val nestedPaths =
        pipelineToDelete
          .flatMap { it.stepRuns }
          .mapNotNull { it.scriptSessionLog.errorScreenshotPath }
      scriptAssetManager.deleteFilesIfExist(nestedPaths)
    }
  }

  suspend fun clearScriptLogs(scriptId: String? = null) {
    if (scriptId.isNullOrBlank()) {
      val all = executionLogDao.getAllOnce()
      executionLogDao.deleteAll()
      scriptAssetManager.deleteFilesIfExist(all.mapNotNull { it.errorScreenshotPath })
    } else {
      val matching = executionLogDao.getByScriptIdOnce(scriptId)
      executionLogDao.deleteByScriptId(scriptId)
      scriptAssetManager.deleteFilesIfExist(matching.mapNotNull { it.errorScreenshotPath })
    }
  }

  suspend fun clearPipelineLogs(pipelineId: String? = null) {
    if (pipelineId.isNullOrBlank()) {
      val all = pipelineLogDao.getAllOnce()
      pipelineLogDao.deleteAll()
      val nestedPaths =
        all.flatMap { it.stepRuns }.mapNotNull { it.scriptSessionLog.errorScreenshotPath }
      scriptAssetManager.deleteFilesIfExist(nestedPaths)
    } else {
      val matching = pipelineLogDao.getByPipelineIdOnce(pipelineId)
      pipelineLogDao.deleteByPipelineId(pipelineId)
      val nestedPaths =
        matching.flatMap { it.stepRuns }.mapNotNull { it.scriptSessionLog.errorScreenshotPath }
      scriptAssetManager.deleteFilesIfExist(nestedPaths)
    }
  }

  fun exportLogsAsJson(logs: List<ExecutionSessionLog>): String {
    return TapFlowJson.instance.encodeToString(
      ListSerializer(ExecutionSessionLog.serializer()),
      logs,
    )
  }

  fun exportLogsAsText(logs: List<ExecutionSessionLog>): String {
    return buildString {
      logs.forEach { session ->
        appendLine("=== ${session.scriptTitle} [${session.status.name}] ===")
        appendLine("Session ID: ${session.id}")
        appendLine("Started: ${session.startedAtMs} | Duration: ${session.finishedAtMs - session.startedAtMs}ms")
        appendLine("Steps Executed: ${session.totalStepsExecuted}")
        session.errorReason?.let { appendLine("Error: $it") }
        session.entries.forEach { entry ->
          appendLine("  [${entry.stepLabel}] ${entry.actionType}: ${entry.summary} (${entry.status.name}, ${entry.durationMs}ms)")
          entry.errorMessage?.let { err -> appendLine("    -> Error: $err") }
        }
        appendLine()
      }
    }
  }
}
