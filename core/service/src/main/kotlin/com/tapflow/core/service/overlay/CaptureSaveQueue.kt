package com.tapflow.core.service.overlay

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.repository.InspectRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import com.tapflow.core.model.script.Script
import com.tapflow.core.service.vision.ZeroAllocVisionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pure coroutine bounded save queue (capacity = [MAX_PENDING_SAVES]) consumed sequentially.
 * Kept separate from Android Framework types for deterministic JVM unit testing.
 */
internal class BoundedSaveQueueCore<T>(
  private val maxCapacity: Int = MAX_PENDING_SAVES,
  workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val processor: suspend (T) -> Unit,
  private val onCleanup: (T) -> Unit = {},
) {
  companion object {
    const val MAX_PENDING_SAVES = 3
  }

  private data class SequencedItem<T>(
    val seq: Long,
    val payload: T,
  )

  private data class PostFlushRequest(
    val targetSeq: Long,
    val action: suspend () -> Unit,
  )

  private val scope = CoroutineScope(SupervisorJob() + workerDispatcher)
  private val channel = Channel<SequencedItem<T>>(capacity = maxCapacity)
  private val postFlushChannel = Channel<PostFlushRequest>(capacity = Channel.UNLIMITED)
  private val queueLock = Any()
  private val submittedSeq = AtomicLong(0L)
  private val completedSeq = MutableStateFlow(0L)

  private val _pendingSaveCount = MutableStateFlow(0)
  val pendingSaveCount: StateFlow<Int> = _pendingSaveCount.asStateFlow()

  init {
    scope.launch {
      for (item in channel) {
        try {
          processor(item.payload)
        } catch (_: Throwable) {
          // Keep background save worker alive across isolated failures
        } finally {
          onCleanup(item.payload)
          synchronized(queueLock) {
            _pendingSaveCount.update { (it - 1).coerceAtLeast(0) }
            if (item.seq > completedSeq.value) {
              completedSeq.value = item.seq
            }
          }
        }
      }
    }

    scope.launch {
      for (request in postFlushChannel) {
        try {
          if (request.targetSeq > 0L) {
            completedSeq.first { it >= request.targetSeq }
          }
          withContext(NonCancellable) {
            runCatching { request.action() }
          }
        } catch (_: Throwable) {
          // Never crash post-flush drain worker
        }
      }
    }
  }

  fun isQueueFull(): Boolean = _pendingSaveCount.value >= maxCapacity

  fun tryEnqueue(payload: T): Boolean =
    synchronized(queueLock) {
      if (_pendingSaveCount.value >= maxCapacity) {
        return@synchronized false
      }
      val nextSeq = submittedSeq.get() + 1L
      val accepted = channel.trySend(SequencedItem(seq = nextSeq, payload = payload)).isSuccess
      if (accepted) {
        submittedSeq.set(nextSeq)
        _pendingSaveCount.update { (it + 1).coerceAtMost(maxCapacity) }
        true
      } else {
        false
      }
    }

  fun enqueuePostFlush(action: suspend () -> Unit): Boolean {
    val targetSeq = synchronized(queueLock) { submittedSeq.get() }
    return postFlushChannel.trySend(PostFlushRequest(targetSeq = targetSeq, action = action)).isSuccess
  }

  suspend fun awaitIdle() {
    val targetSeq = synchronized(queueLock) { submittedSeq.get() }
    if (targetSeq > 0L) {
      completedSeq.first { it >= targetSeq }
    }
  }
}

data class CaptureSaveTask(
  val itemId: String = UUID.randomUUID().toString(),
  val groupId: String,
  val groupTitle: String?,
  val groupTags: List<String>,
  val groupCreatedAtMs: Long,
  val bitmap: Bitmap,
  val foregroundPackage: String,
  val tapX: Int,
  val tapY: Int,
  val createdAtMs: Long = System.currentTimeMillis(),
  val onItemPersisted: ((InspectItem) -> Unit)? = null,
)

private data class EncodedCaptureSnapshot(
  val pngBytes: ByteArray,
  val width: Int,
  val height: Int,
  val safeX: Int,
  val safeY: Int,
  val hexColor: String,
)

/**
 * Application-scoped bounded image save queue (max 3 pending bitmaps) for Live Recorder (Mode 3)
 * and Live Inspector (Mode 4). Compresses PNG on [Dispatchers.Default] and writes disk + Room DB
 * sequentially on [Dispatchers.IO], surviving OverlayService UI dismissal to flush safely.
 */
@Singleton
class CaptureSaveQueue @Inject constructor(
  @ApplicationContext private val context: Context,
  private val inspectRepository: InspectRepository,
  private val scriptRepository: ScriptRepository,
  private val scriptAssetManager: ScriptAssetManager,
  private val visionEngine: ZeroAllocVisionEngine,
) {
  companion object {
    const val MAX_PENDING_SAVES = BoundedSaveQueueCore.MAX_PENDING_SAVES
  }

  private val persistedGroupIds = ConcurrentHashMap.newKeySet<String>()
  private val resolvedGroupTitles = ConcurrentHashMap<String, String>()
  private val sessionSavedCounts = ConcurrentHashMap<String, Int>()

  private val core =
    BoundedSaveQueueCore<CaptureSaveTask>(
      maxCapacity = MAX_PENDING_SAVES,
      workerDispatcher = Dispatchers.IO,
      processor = { task -> processInspectSaveTask(task) },
      onCleanup = { task ->
        if (!task.bitmap.isRecycled) {
          task.bitmap.recycle()
        }
      },
    )

  val pendingSaveCount: StateFlow<Int> = core.pendingSaveCount

  fun isQueueFull(): Boolean = core.isQueueFull()

  /**
   * Attempts to enqueue [task] into the bounded (capacity = 3) save channel.
   * Caller keeps ownership of `task.bitmap` and MUST recycle it if this returns `false`.
   */
  fun tryEnqueue(task: CaptureSaveTask): Boolean = core.tryEnqueue(task)

  fun finalizeRecorderSession(
    actions: List<ScriptAction>,
    inspectGroupId: String?,
    inspectGroupTitle: String?,
    inspectGroupTags: List<String>,
    onFinalized: (savedScriptTitle: String?) -> Unit = {},
  ) {
    val snapshotActions = actions.toList()
    core.enqueuePostFlush {
      try {
        if (inspectGroupId != null && inspectGroupId in persistedGroupIds) {
          val resolvedTitle =
            resolvedGroupTitles[inspectGroupId]
              ?: inspectGroupTitle?.takeIf { it.isNotBlank() }
              ?: "Recorded Session"
          inspectRepository.saveGroup(
            InspectGroup(
              id = inspectGroupId,
              title = resolvedTitle,
              tags = inspectGroupTags,
              createdAtMs = System.currentTimeMillis(),
            ),
          )
        }
        val savedTitle: String? =
          if (snapshotActions.isNotEmpty()) {
            val stamp = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date())
            val title = "Recorded Script $stamp"
            scriptRepository.saveScript(
              Script(
                id = UUID.randomUUID().toString(),
                title = title,
                description = "Captured via Floating Recorder",
                tags = listOf("recorded"),
                actions = snapshotActions,
              ),
            )
            title
          } else {
            null
          }
        onFinalized(savedTitle)
      } finally {
        if (inspectGroupId != null) {
          clearTemporarySessionState(inspectGroupId)
        }
      }
    }
  }

  fun finalizeInspectGroupSession(
    groupId: String?,
    onFinalized: (savedItemCount: Int) -> Unit = {},
  ) {
    core.enqueuePostFlush {
      val count =
        if (groupId != null) {
          try {
            sessionSavedCounts[groupId] ?: 0
          } finally {
            clearTemporarySessionState(groupId)
          }
        } else {
          0
        }
      onFinalized(count)
    }
  }

  private fun clearTemporarySessionState(groupId: String) {
    persistedGroupIds.remove(groupId)
    resolvedGroupTitles.remove(groupId)
    sessionSavedCounts.remove(groupId)
  }

  private suspend fun processInspectSaveTask(task: CaptureSaveTask) {
    val encoded =
      withContext(Dispatchers.Default) {
        val bmp = task.bitmap
        if (bmp.isRecycled) return@withContext null
        try {
          val width = bmp.width
          val height = bmp.height
          val safeX = task.tapX.coerceIn(0, (width - 1).coerceAtLeast(0))
          val safeY = task.tapY.coerceIn(0, (height - 1).coerceAtLeast(0))
          val sampledPixel = bmp.getPixel(safeX, safeY)
          val hexColor = String.format(Locale.US, "#%06X", sampledPixel and 0x00FFFFFF)
          val baos = ByteArrayOutputStream(256 * 1024)
          val ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
          if (!ok) return@withContext null
          EncodedCaptureSnapshot(
            pngBytes = baos.toByteArray(),
            width = width,
            height = height,
            safeX = safeX,
            safeY = safeY,
            hexColor = hexColor,
          )
        } finally {
          if (!bmp.isRecycled) {
            bmp.recycle()
          }
        }
      } ?: return

    withContext(Dispatchers.IO) {
      val fullScreenFile = File(scriptAssetManager.getInspectScreensDir(), "scr_${task.itemId}.png")
      if (!visionEngine.writeBytesToFile(encoded.pngBytes, fullScreenFile)) {
        return@withContext
      }

      if (persistedGroupIds.add(task.groupId)) {
        val resolvedTitle =
          resolvedGroupTitles[task.groupId]
            ?: task.groupTitle?.takeIf { it.isNotBlank() }
            ?: run {
              val existingCount = inspectRepository.countGroups()
              "InspectGroup (${existingCount + 1})"
            }
        resolvedGroupTitles[task.groupId] = resolvedTitle
        inspectRepository.saveGroup(
          InspectGroup(
            id = task.groupId,
            title = resolvedTitle,
            tags = task.groupTags,
            createdAtMs = task.groupCreatedAtMs,
          ),
        )
      }

      val pixelEntry =
        InspectPixel(
          id = UUID.randomUUID().toString(),
          label = "Pixel (${encoded.safeX}, ${encoded.safeY})",
          x = encoded.safeX,
          y = encoded.safeY,
          hexColor = encoded.hexColor,
        )
      val appLabel = resolveAppLabel(task.foregroundPackage)
      val item =
        InspectItem(
          id = task.itemId,
          groupId = task.groupId,
          fullScreenPngPath = fullScreenFile.absolutePath,
          screenWidth = encoded.width,
          screenHeight = encoded.height,
          appName = appLabel,
          packageName = task.foregroundPackage,
          pixels = listOf(pixelEntry),
          crops = emptyList(),
          createdAtMs = task.createdAtMs,
        )
      inspectRepository.saveItem(item)
      sessionSavedCounts.merge(task.groupId, 1, Int::plus)
      task.onItemPersisted?.invoke(item)
    }
  }

  private fun resolveAppLabel(packageName: String): String {
    if (packageName.isBlank()) return "Screen Capture"
    return runCatching {
      val pm = context.packageManager
      val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(0L),
          )
        } else {
          @Suppress("DEPRECATION")
          pm.getApplicationInfo(packageName, 0)
        }
      pm.getApplicationLabel(info).toString().ifBlank { packageName }
    }.getOrDefault(packageName)
  }
}
