package com.tapflow.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.repository.InspectRepository
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.service.vision.ZeroAllocVisionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class InspectImageImporter @Inject constructor(
  @ApplicationContext private val context: Context,
  private val scriptAssetManager: ScriptAssetManager,
  private val visionEngine: ZeroAllocVisionEngine,
  private val inspectRepository: InspectRepository,
) {
  companion object {
    private const val IMPORTED_GROUP_ID = "inspect_group_imported"
  }

  suspend fun importImageUri(uri: Uri): Result<InspectItem> =
    withContext(Dispatchers.IO) {
      runCatching {
        val decoded =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
              decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
              decoder.isMutableRequired = false
            }
          } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
              BitmapFactory.decodeStream(stream)
            } ?: throw IllegalStateException("Unable to read selected image.")
          }

        val argbBitmap =
          if (decoded.config == Bitmap.Config.ARGB_8888) {
            decoded
          } else {
            decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
          } ?: throw IllegalStateException("Unable to convert image to lossless ARGB format.")

        val itemId = UUID.randomUUID().toString()
        val destFile = File(scriptAssetManager.getInspectScreensDir(), "import_$itemId.png")

        val saved =
          try {
            visionEngine.saveLosslessPng(argbBitmap, destFile)
          } finally {
            argbBitmap.recycle()
          }

        if (!saved) {
          throw IllegalStateException("Failed to write lossless PNG to TapFlow storage.")
        }

        val existingGroups = inspectRepository.observeGroupsWithItems().first()
        val importedGroup = existingGroups.firstOrNull { it.group.id == IMPORTED_GROUP_ID }?.group
        if (importedGroup == null) {
          inspectRepository.saveGroup(
            InspectGroup(
              id = IMPORTED_GROUP_ID,
              title = "Imported Screenshots",
              tags = listOf("Imported"),
              createdAtMs = System.currentTimeMillis(),
            ),
          )
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(destFile.absolutePath, opts)

        val item =
          InspectItem(
            id = itemId,
            groupId = IMPORTED_GROUP_ID,
            fullScreenPngPath = destFile.absolutePath,
            screenWidth = opts.outWidth.coerceAtLeast(1),
            screenHeight = opts.outHeight.coerceAtLeast(1),
            appName = "Imported Image",
            packageName = "local.import",
            pixels = emptyList(),
            crops = emptyList(),
            createdAtMs = System.currentTimeMillis(),
          )
        inspectRepository.saveItem(item)
        item
      }
    }
}
