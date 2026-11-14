package com.tapflow.core.database

import android.content.Context
import androidx.room.Room
import com.tapflow.core.database.dao.ExecutionLogDao
import com.tapflow.core.database.dao.InspectDao
import com.tapflow.core.database.dao.PipelineDao
import com.tapflow.core.database.dao.PipelineLogDao
import com.tapflow.core.database.dao.ScriptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideTapFlowDatabase(
    @ApplicationContext context: Context,
  ): TapFlowDatabase {
    return Room.databaseBuilder(
      context,
      TapFlowDatabase::class.java,
      "tapflow.db",
    ).build()
  }

  @Provides
  fun provideScriptDao(db: TapFlowDatabase): ScriptDao = db.scriptDao()

  @Provides
  fun providePipelineDao(db: TapFlowDatabase): PipelineDao = db.pipelineDao()

  @Provides
  fun provideInspectDao(db: TapFlowDatabase): InspectDao = db.inspectDao()

  @Provides
  fun provideExecutionLogDao(db: TapFlowDatabase): ExecutionLogDao = db.executionLogDao()

  @Provides
  fun providePipelineLogDao(db: TapFlowDatabase): PipelineLogDao = db.pipelineLogDao()
}
