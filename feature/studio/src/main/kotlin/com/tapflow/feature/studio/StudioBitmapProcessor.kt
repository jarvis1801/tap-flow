package com.tapflow.feature.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tapflow.core.database.asset.ScriptAssetManager
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class StudioBitmapProcessor @Inject constructor(
  private val scriptAssetManager: ScriptAssetManager,
) {
  suspend fun sampleHexColor(
    fullScreenPngPath: String,
    x: Int,
    y: Int,
  ): String =
    withContext(Dispatchers.IO) {
      val file = File(fullScreenPngPath)
      if (!file.exists() || !file.isFile) return@withContext "#FFFFFF"
      val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext "#FFFFFF"
      try {
        val safeX = x.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
        val safeY = y.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
        val pixel = bitmap.getPixel(safeX, safeY)
        formatArgbToHex(pixel)
      } finally {
        bitmap.recycle()
      }
    }

  suspend fun saveLosslessCropPng(
    fullScreenPngPath: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ): String =
    withContext(Dispatchers.IO) {
      val sourceFile = File(fullScreenPngPath)
      require(sourceFile.exists() && sourceFile.isFile) {
        "Full-screen screenshot PNG not found at $fullScreenPngPath"
      }
      val sourceBitmap =
        BitmapFactory.decodeFile(sourceFile.absolutePath)
          ?: error("Failed to decode lossless PNG screenshot")
      try {
        val safeX = x.coerceIn(0, (sourceBitmap.width - 1).coerceAtLeast(0))
        val safeY = y.coerceIn(0, (sourceBitmap.height - 1).coerceAtLeast(0))
        val safeWidth = width.coerceIn(4, (sourceBitmap.width - safeX).coerceAtLeast(1))
        val safeHeight = height.coerceIn(4, (sourceBitmap.height - safeY).coerceAtLeast(1))
        val cropped = Bitmap.createBitmap(sourceBitmap, safeX, safeY, safeWidth, safeHeight)
        try {
          val outFile =
            File(
              scriptAssetManager.getInspectCropsDir(),
              "crop_${UUID.randomUUID()}.png",
            )
          FileOutputStream(outFile).use { fos ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
          }
          outFile.absolutePath
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
    }

  companion object {
    fun formatArgbToHex(argb: Int): String = String.format(Locale.US, "#%06X", 0xFFFFFF and argb)
  }
}
