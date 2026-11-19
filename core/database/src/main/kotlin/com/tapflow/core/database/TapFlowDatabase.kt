package com.tapflow.core.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.tapflow.core.database.dao.ExecutionLogDao
import com.tapflow.core.database.dao.InspectDao
import com.tapflow.core.database.dao.PipelineDao
import com.tapflow.core.database.dao.PipelineLogDao
import com.tapflow.core.database.dao.ScriptDao
import com.tapflow.core.database.entity.ExecutionSessionLogEntity
import com.tapflow.core.database.entity.InspectGroupEntity
import com.tapflow.core.database.entity.InspectItemEntity
import com.tapflow.core.database.entity.PipelineEntity
import com.tapflow.core.database.entity.PipelineSessionLogEntity
import com.tapflow.core.database.entity.ScriptEntity

@Database(
  entities = [
    ScriptEntity::class,
    PipelineEntity::class,
    InspectGroupEntity::class,
    InspectItemEntity::class,
    ExecutionSessionLogEntity::class,
    PipelineSessionLogEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
@ColumnTypeConverters(TapFlowTypeConverters::class)
abstract class TapFlowDatabase : RoomDatabase() {
  abstract fun scriptDao(): ScriptDao
  abstract fun pipelineDao(): PipelineDao
  abstract fun inspectDao(): InspectDao
  abstract fun executionLogDao(): ExecutionLogDao
  abstract fun pipelineLogDao(): PipelineLogDao
}
