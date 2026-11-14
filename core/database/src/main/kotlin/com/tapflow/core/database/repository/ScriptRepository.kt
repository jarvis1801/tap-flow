package com.tapflow.core.database.repository

import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.dao.PipelineDao
import com.tapflow.core.database.dao.ScriptDao
import com.tapflow.core.database.entity.toDomain
import com.tapflow.core.database.entity.toEntity
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ScriptRepository @Inject constructor(
  private val scriptDao: ScriptDao,
  private val pipelineDao: PipelineDao,
  private val scriptAssetManager: ScriptAssetManager,
) {
  fun observeScripts(): Flow<List<Script>> =
    scriptDao.observeAll().map { entities -> entities.map { it.toDomain() } }

  fun observeScriptById(id: String): Flow<Script?> =
    scriptDao.observeById(id).map { it?.toDomain() }

  suspend fun getAllScripts(): List<Script> = scriptDao.getAllOnce().map { it.toDomain() }

  suspend fun getScriptById(id: String): Script? = scriptDao.getById(id)?.toDomain()

  suspend fun getScriptCount(): Int = scriptDao.count()

  suspend fun saveScript(script: Script) {
    val now = System.currentTimeMillis()
    val updated =
      script.copy(
        createdAtMs = if (script.createdAtMs <= 0L) now else script.createdAtMs,
        updatedAtMs = now,
      )
    scriptDao.upsert(updated.toEntity())
  }

  suspend fun findPipelinesReferencingScript(scriptId: String): List<Pipeline> {
    return pipelineDao.getAllOnce()
      .map { it.toDomain() }
      .filter { pipeline -> pipeline.steps.any { it.scriptId == scriptId } }
  }

  suspend fun deleteScript(scriptId: String) {
    scriptDao.deleteById(scriptId)
    val remainingScripts = scriptDao.getAllOnce().map { it.toDomain() }
    scriptAssetManager.cleanupUnreferencedScriptTemplates(remainingScripts)
  }
}
