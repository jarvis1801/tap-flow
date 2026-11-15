package com.tapflow.core.database.asset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionNode
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.script.Script
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class InspectDiskUsage(
  val imageCount: Int = 0,
  val totalBytes: Long = 0L,
)

@Singleton
class ScriptAssetManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val rootAssetsDir: File
    get() = File(context.filesDir, "tapflow_assets").apply { mkdirs() }

  fun getInspectScreensDir(): File = File(rootAssetsDir, "inspect_screens").apply { mkdirs() }

  fun getInspectCropsDir(): File = File(rootAssetsDir, "inspect_crops").apply { mkdirs() }

  fun getScriptTemplatesDir(): File = File(rootAssetsDir, "script_templates").apply { mkdirs() }

  fun getErrorScreenshotsDir(): File = File(rootAssetsDir, "error_screenshots").apply { mkdirs() }

  suspend fun copyCropToScriptAssets(
    sourceCropPngPath: String,
    scriptId: String,
  ): String =
    withContext(Dispatchers.IO) {
      val sourceFile = File(sourceCropPngPath)
      require(sourceFile.exists() && sourceFile.isFile) {
        "Inspect crop PNG not found at $sourceCropPngPath"
      }
      val safeScriptPrefix = scriptId.replace(Regex("[^A-Za-z0-9_-]"), "_")
      val destFile =
        File(
          getScriptTemplatesDir(),
          "${safeScriptPrefix}_${UUID.randomUUID()}.png",
        )
      sourceFile.copyTo(destFile, overwrite = true)
      destFile.absolutePath
    }

  suspend fun exportCropRegionToScriptAssets(
    fullScreenPngPath: String,
    crop: InspectCrop,
    scriptId: String,
  ): String =
    withContext(Dispatchers.IO) {
      val safeScriptPrefix = scriptId.replace(Regex("[^A-Za-z0-9_-]"), "_")
      val destFile =
        File(
          getScriptTemplatesDir(),
          "${safeScriptPrefix}_${UUID.randomUUID()}.png",
        )
      val legacyCropFile =
        crop.cropPngPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf {
          it.exists() && it.isFile
        }
      if (legacyCropFile != null) {
        legacyCropFile.copyTo(destFile, overwrite = true)
        return@withContext destFile.absolutePath
      }

      val screenFile = File(fullScreenPngPath)
      require(screenFile.exists() && screenFile.isFile) {
        "Inspect screenshot PNG not found at $fullScreenPngPath"
      }
      val sourceBitmap =
        BitmapFactory.decodeFile(screenFile.absolutePath)
          ?: error("Failed to decode lossless PNG screenshot")
      try {
        val safeX = crop.x.coerceIn(0, (sourceBitmap.width - 1).coerceAtLeast(0))
        val safeY = crop.y.coerceIn(0, (sourceBitmap.height - 1).coerceAtLeast(0))
        val safeWidth = crop.width.coerceIn(1, (sourceBitmap.width - safeX).coerceAtLeast(1))
        val safeHeight = crop.height.coerceIn(1, (sourceBitmap.height - safeY).coerceAtLeast(1))
        val cropped = Bitmap.createBitmap(sourceBitmap, safeX, safeY, safeWidth, safeHeight)
        try {
          FileOutputStream(destFile).use { fos ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
          }
        } finally {
          if (!cropped.isRecycled) {
            cropped.recycle()
          }
        }
      } finally {
        if (!sourceBitmap.isRecycled) {
          sourceBitmap.recycle()
        }
      }
      destFile.absolutePath
    }

  suspend fun deleteFileIfExists(path: String?) {
    if (path.isNullOrBlank()) return
    withContext(Dispatchers.IO) {
      val file = File(path)
      if (file.exists() && file.isFile) {
        file.delete()
      }
    }
  }

  suspend fun deleteFilesIfExist(paths: Iterable<String?>) {
    withContext(Dispatchers.IO) {
      paths.forEach { path ->
        if (!path.isNullOrBlank()) {
          val file = File(path)
          if (file.exists() && file.isFile) {
            file.delete()
          }
        }
      }
    }
  }

  fun collectReferencedScriptTemplatePaths(script: Script): Set<String> {
    val paths = linkedSetOf<String>()
    script.actions.forEach { collectFromAction(it, paths) }
    return paths
  }

  suspend fun getInspectDiskUsage(): InspectDiskUsage =
    withContext(Dispatchers.IO) {
      var count = 0
      var bytes = 0L
      listOf(getInspectScreensDir(), getInspectCropsDir()).forEach { dir ->
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
          count++
          bytes += file.length()
        }
      }
      InspectDiskUsage(imageCount = count, totalBytes = bytes)
    }

  suspend fun getInspectDiskUsageBytes(): Long = getInspectDiskUsage().totalBytes

  suspend fun clearInspectDirectories(): Int =
    withContext(Dispatchers.IO) {
      var deletedCount = 0
      listOf(getInspectScreensDir(), getInspectCropsDir()).forEach { dir ->
        deletedCount += deleteChildrenRecursively(dir)
      }
      deletedCount
    }

  private fun deleteChildrenRecursively(dir: File): Int {
    val children = dir.listFiles() ?: return 0
    var deletedCount = 0
    children.forEach { child ->
      if (child.isDirectory) {
        deletedCount += deleteChildrenRecursively(child)
        child.delete()
      } else if (child.isFile && child.delete()) {
        deletedCount++
      }
    }
    return deletedCount
  }

  suspend fun cleanupUnreferencedScriptTemplates(allScripts: List<Script>) {
    withContext(Dispatchers.IO) {
      val activePaths =
        allScripts.flatMapTo(HashSet()) { collectReferencedScriptTemplatePaths(it) }
      val templatesDir = getScriptTemplatesDir()
      templatesDir.listFiles()?.forEach { file ->
        if (file.isFile && file.absolutePath !in activePaths) {
          file.delete()
        }
      }
    }
  }

  private fun collectFromAction(action: ScriptAction, out: MutableSet<String>) {
    when (action) {
      is ScriptAction.IfBlock -> {
        collectFromCondition(action.conditionRoot, out)
        action.thenActions.forEach { collectFromAction(it, out) }
        action.elseIfBranches.forEach { branch ->
          collectFromCondition(branch.conditionRoot, out)
          branch.actions.forEach { collectFromAction(it, out) }
        }
        action.elseActions.forEach { collectFromAction(it, out) }
      }
      is ScriptAction.LoopBlock -> {
        when (val m = action.mode) {
          is LoopMode.WhileCondition -> collectFromCondition(m.conditionRoot, out)
          is LoopMode.UntilCondition -> collectFromCondition(m.conditionRoot, out)
          else -> Unit
        }
        action.bodyActions.forEach { collectFromAction(it, out) }
      }
      is ScriptAction.LongPress -> {
        action.holdUntilCondition?.let { collectFromCondition(it, out) }
      }
      else -> Unit
    }
  }

  private fun collectFromCondition(node: ConditionNode, out: MutableSet<String>) {
    when (node) {
      is ConditionGroup -> node.children.forEach { collectFromCondition(it, out) }
      is ConditionLeaf -> {
        val predicate = node.predicate
        if (predicate is ConditionPredicate.ImageMatch && predicate.templatePngPath.isNotBlank()) {
          out += predicate.templatePngPath
        }
      }
    }
  }
}
