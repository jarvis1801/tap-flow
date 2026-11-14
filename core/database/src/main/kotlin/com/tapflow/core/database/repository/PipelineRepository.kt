package com.tapflow.core.database.repository

import com.tapflow.core.database.dao.PipelineDao
import com.tapflow.core.database.entity.toDomain
import com.tapflow.core.database.entity.toEntity
import com.tapflow.core.model.pipeline.Pipeline
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PipelineRepository @Inject constructor(
  private val pipelineDao: PipelineDao,
) {
  fun observePipelines(): Flow<List<Pipeline>> =
    pipelineDao.observeAll().map { entities -> entities.map { it.toDomain() } }

  fun observePipelineById(id: String): Flow<Pipeline?> =
    pipelineDao.observeById(id).map { it?.toDomain() }

  suspend fun getAllPipelines(): List<Pipeline> = pipelineDao.getAllOnce().map { it.toDomain() }

  suspend fun getPipelineById(id: String): Pipeline? = pipelineDao.getById(id)?.toDomain()

  suspend fun getPipelineCount(): Int = pipelineDao.count()

  suspend fun savePipeline(pipeline: Pipeline) {
    val now = System.currentTimeMillis()
    val updated =
      pipeline.copy(
        createdAtMs = if (pipeline.createdAtMs <= 0L) now else pipeline.createdAtMs,
        updatedAtMs = now,
      )
    pipelineDao.upsert(updated.toEntity())
  }

  suspend fun deletePipeline(pipelineId: String) {
    pipelineDao.deleteById(pipelineId)
  }
}
