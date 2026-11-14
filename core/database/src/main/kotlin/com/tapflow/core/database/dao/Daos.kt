package com.tapflow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapflow.core.database.entity.ExecutionSessionLogEntity
import com.tapflow.core.database.entity.InspectGroupEntity
import com.tapflow.core.database.entity.InspectItemEntity
import com.tapflow.core.database.entity.PipelineEntity
import com.tapflow.core.database.entity.PipelineSessionLogEntity
import com.tapflow.core.database.entity.ScriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
  @Query("SELECT * FROM scripts ORDER BY updatedAtMs DESC")
  fun observeAll(): Flow<List<ScriptEntity>>

  @Query("SELECT * FROM scripts ORDER BY updatedAtMs DESC")
  suspend fun getAllOnce(): List<ScriptEntity>

  @Query("SELECT * FROM scripts WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): ScriptEntity?

  @Query("SELECT * FROM scripts WHERE id = :id LIMIT 1")
  fun observeById(id: String): Flow<ScriptEntity?>

  @Query("SELECT COUNT(*) FROM scripts")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ScriptEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<ScriptEntity>)

  @Query("DELETE FROM scripts WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface PipelineDao {
  @Query("SELECT * FROM pipelines ORDER BY updatedAtMs DESC")
  fun observeAll(): Flow<List<PipelineEntity>>

  @Query("SELECT * FROM pipelines ORDER BY updatedAtMs DESC")
  suspend fun getAllOnce(): List<PipelineEntity>

  @Query("SELECT * FROM pipelines WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): PipelineEntity?

  @Query("SELECT * FROM pipelines WHERE id = :id LIMIT 1")
  fun observeById(id: String): Flow<PipelineEntity?>

  @Query("SELECT COUNT(*) FROM pipelines")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: PipelineEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<PipelineEntity>)

  @Query("DELETE FROM pipelines WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface InspectDao {
  @Query("SELECT * FROM inspect_groups ORDER BY createdAtMs DESC")
  fun observeAllGroups(): Flow<List<InspectGroupEntity>>

  @Query("SELECT * FROM inspect_items ORDER BY createdAtMs DESC")
  fun observeAllItems(): Flow<List<InspectItemEntity>>

  @Query("SELECT * FROM inspect_items WHERE groupId = :groupId ORDER BY createdAtMs DESC")
  fun observeItemsByGroup(groupId: String): Flow<List<InspectItemEntity>>

  @Query("SELECT * FROM inspect_items WHERE groupId = :groupId")
  suspend fun getItemsByGroupOnce(groupId: String): List<InspectItemEntity>

  @Query("SELECT * FROM inspect_items WHERE id = :itemId LIMIT 1")
  suspend fun getItemById(itemId: String): InspectItemEntity?

  @Query("SELECT * FROM inspect_items WHERE id = :itemId LIMIT 1")
  fun observeItemById(itemId: String): Flow<InspectItemEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertGroup(group: InspectGroupEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertItem(item: InspectItemEntity)

  @Query("DELETE FROM inspect_groups WHERE id = :groupId")
  suspend fun deleteGroupById(groupId: String)

  @Query("DELETE FROM inspect_items WHERE id = :itemId")
  suspend fun deleteItemById(itemId: String)
}

@Dao
interface ExecutionLogDao {
  @Query("SELECT * FROM execution_session_logs WHERE scriptId = :scriptId ORDER BY startedAtMs DESC")
  fun observeByScriptId(scriptId: String): Flow<List<ExecutionSessionLogEntity>>

  @Query("SELECT * FROM execution_session_logs ORDER BY startedAtMs DESC")
  fun observeAll(): Flow<List<ExecutionSessionLogEntity>>

  @Query("SELECT * FROM execution_session_logs ORDER BY startedAtMs DESC")
  suspend fun getAllOnce(): List<ExecutionSessionLogEntity>

  @Query("SELECT * FROM execution_session_logs WHERE scriptId = :scriptId")
  suspend fun getByScriptIdOnce(scriptId: String): List<ExecutionSessionLogEntity>

  @Query("SELECT * FROM execution_session_logs WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): ExecutionSessionLogEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ExecutionSessionLogEntity)

  @Query("DELETE FROM execution_session_logs WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM execution_session_logs WHERE id IN (:ids)")
  suspend fun deleteByIds(ids: List<String>)

  @Query("DELETE FROM execution_session_logs WHERE scriptId = :scriptId")
  suspend fun deleteByScriptId(scriptId: String)

  @Query("DELETE FROM execution_session_logs")
  suspend fun deleteAll()
}

@Dao
interface PipelineLogDao {
  @Query("SELECT * FROM pipeline_session_logs WHERE pipelineId = :pipelineId ORDER BY startedAtMs DESC")
  fun observeByPipelineId(pipelineId: String): Flow<List<PipelineSessionLogEntity>>

  @Query("SELECT * FROM pipeline_session_logs ORDER BY startedAtMs DESC")
  fun observeAll(): Flow<List<PipelineSessionLogEntity>>

  @Query("SELECT * FROM pipeline_session_logs ORDER BY startedAtMs DESC")
  suspend fun getAllOnce(): List<PipelineSessionLogEntity>

  @Query("SELECT * FROM pipeline_session_logs WHERE pipelineId = :pipelineId")
  suspend fun getByPipelineIdOnce(pipelineId: String): List<PipelineSessionLogEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: PipelineSessionLogEntity)

  @Query("DELETE FROM pipeline_session_logs WHERE id IN (:ids)")
  suspend fun deleteByIds(ids: List<String>)

  @Query("DELETE FROM pipeline_session_logs WHERE pipelineId = :pipelineId")
  suspend fun deleteByPipelineId(pipelineId: String)

  @Query("DELETE FROM pipeline_session_logs")
  suspend fun deleteAll()
}
