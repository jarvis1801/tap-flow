package com.tapflow.core.service.engine

import android.graphics.Bitmap
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.repository.LogRepository
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.PipelineSessionLog
import com.tapflow.core.service.vision.ZeroAllocVisionEngine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asynchronous sequential writer for runner execution logs and failure screenshots so Room DB
 * writes and PNG compression/file I/O run strictly on background dispatchers ([Dispatchers.Default]
 * / [Dispatchers.IO]) without blocking the runner's action/delay timing loop or UI.
 */
@Singleton
class AsyncLogDispatcher @Inject constructor(
  private val logRepository: LogRepository,
  private val scriptAssetManager: ScriptAssetManager,
  private val visionEngine: ZeroAllocVisionEngine,
) {
  private sealed interface LogWriteTask {
    data class ErrorScreenshot(
      val bitmap: Bitmap,
      val destFile: File,
    ) : LogWriteTask

    data class ScriptSession(
      val log: ExecutionSessionLog,
      val finishedAtMs: Long,
    ) : LogWriteTask

    data class PipelineSession(
      val log: PipelineSessionLog,
      val finishedAtMs: Long,
    ) : LogWriteTask
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val channel = Channel<LogWriteTask>(capacity = Channel.UNLIMITED)

  init {
    scope.launch {
      for (task in channel) {
        withContext(NonCancellable) {
          runCatching {
            when (task) {
              is LogWriteTask.ErrorScreenshot -> {
                try {
                  visionEngine.saveLosslessPng(task.bitmap, task.destFile)
                } finally {
                  if (!task.bitmap.isRecycled) {
                    task.bitmap.recycle()
                  }
                }
              }

              is LogWriteTask.ScriptSession -> {
                logRepository.recordScriptSession(task.log, task.finishedAtMs)
              }

              is LogWriteTask.PipelineSession -> {
                logRepository.recordPipelineSession(task.log, task.finishedAtMs)
              }
            }
          }
        }
      }
    }
  }

  /**
   * Captures the screen frame cleanly on [Dispatchers.Default] and queues PNG encoding + disk
   * write asynchronously on [Dispatchers.IO], immediately returning the destination PNG path.
   */
  suspend fun captureAndEnqueueErrorScreenshot(useShizukuFallback: Boolean): String? {
    val bitmap = visionEngine.captureCleanBitmap(useShizukuFallback) ?: return null
    val outFile =
      File(scriptAssetManager.getErrorScreenshotsDir(), "err_${UUID.randomUUID()}.png")
    val enqueued =
      channel.trySend(LogWriteTask.ErrorScreenshot(bitmap = bitmap, destFile = outFile)).isSuccess
    if (!enqueued) {
      if (!bitmap.isRecycled) {
        bitmap.recycle()
      }
      return null
    }
    return outFile.absolutePath
  }

  fun enqueueScriptSessionLog(log: ExecutionSessionLog, finishedAtMs: Long) {
    channel.trySend(LogWriteTask.ScriptSession(log = log, finishedAtMs = finishedAtMs))
  }

  fun enqueuePipelineSessionLog(log: PipelineSessionLog, finishedAtMs: Long) {
    channel.trySend(LogWriteTask.PipelineSession(log = log, finishedAtMs = finishedAtMs))
  }
}
