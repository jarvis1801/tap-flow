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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class ScriptRepository @Inject constructor(
  private val scriptDao: ScriptDao,
  private val pipelineDao: PipelineDao,
  private val scriptAssetManager: ScriptAssetManager,
) {
  fun observeScripts(): Flow<List<Script>> =
    scriptDao.observeAll().map { entities -> entities.map { it.toDomain() } }.flowOn(Dispatchers.IO)

  fun observeScriptById(id: String): Flow<Script?> =
    scriptDao.observeById(id).map { it?.toDomain() }.flowOn(Dispatchers.IO)

  suspend fun getAllScripts(): List<Script> =
    withContext(Dispatchers.IO) { scriptDao.getAllOnce().map { it.toDomain() } }

  suspend fun getScriptById(id: String): Script? =
    withContext(Dispatchers.IO) { scriptDao.getById(id)?.toDomain() }

  suspend fun getScriptCount(): Int =
    withContext(Dispatchers.IO) { scriptDao.count() }

  suspend fun saveScript(script: Script) =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      val updated =
        script.copy(
          createdAtMs = if (script.createdAtMs <= 0L) now else script.createdAtMs,
          updatedAtMs = now,
        )
      scriptDao.upsert(updated.toEntity())
    }

  suspend fun findPipelinesReferencingScript(scriptId: String): List<Pipeline> =
    withContext(Dispatchers.IO) {
      pipelineDao.getAllOnce()
        .map { it.toDomain() }
        .filter { pipeline -> pipeline.steps.any { it.scriptId == scriptId } }
    }

  suspend fun deleteScript(scriptId: String) =
    withContext(Dispatchers.IO) {
      scriptDao.deleteById(scriptId)
      val remainingScripts = scriptDao.getAllOnce().map { it.toDomain() }
      scriptAssetManager.cleanupUnreferencedScriptTemplates(remainingScripts)
    }
}
