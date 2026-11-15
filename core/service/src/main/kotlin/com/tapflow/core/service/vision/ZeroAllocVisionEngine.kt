package com.tapflow.core.service.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.shizuku.ShizukuManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ZeroAllocVisionEngine @Inject constructor(
  private val mediaProjectionBridge: MediaProjectionBridge,
  private val shizukuManager: ShizukuManager,
  private val accessibilityBridge: AccessibilityManagerBridge,
) {
  companion object {
    private const val TEMPLATE_CACHE_CAPACITY = 64
  }

  private val templateCache = LruCache<String, TemplateSignature>(TEMPLATE_CACHE_CAPACITY)

  /**
   * Evaluates ColorMatch directly from MediaProjection's `Image.planes[0].buffer` using
   * `(y * rowStride) + (x * pixelStride)` with ZERO intermediate Bitmap allocations.
   * Falls back seamlessly to Shizuku `screencap` and Android 11+ `AccessibilityService.takeScreenshot`.
   */
  suspend fun evaluateColorMatch(
    predicate: ConditionPredicate.ColorMatch,
    useShizukuFallback: Boolean = false,
  ): ColorMatchResult =
    withContext(Dispatchers.Default) {
      val directResult = evaluateColorMatchViaMediaProjection(predicate)
      if (directResult != null) return@withContext directResult

      val fallbackBitmap = captureFallbackBitmap(useShizukuFallback)
        ?: evaluateColorMatchViaMediaProjection(predicate)?.let { return@withContext it }
        ?: return@withContext ColorMatchResult(matched = false, matchRatio = 0f)

      try {
        val buffer = ByteBuffer.allocateDirect(fallbackBitmap.byteCount)
        fallbackBitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        DirectBufferVisionMatcher.evaluateColorMatch(
          buffer = buffer,
          width = fallbackBitmap.width,
          height = fallbackBitmap.height,
          pixelStride = 4,
          rowStride = fallbackBitmap.width * 4,
          predicate = predicate,
        )
      } finally {
        fallbackBitmap.recycle()
      }
    }

  /**
   * Evaluates ImageMatch directly against MediaProjection's `Image.planes[0].buffer` using
   * `(y * rowStride) + (x * pixelStride)` without allocating any screen Bitmap.
   * Falls back seamlessly to Shizuku `screencap` and Android 11+ `AccessibilityService.takeScreenshot`.
   */
  suspend fun evaluateImageMatch(
    predicate: ConditionPredicate.ImageMatch,
    useShizukuFallback: Boolean = false,
  ): ImageMatchResult =
    withContext(Dispatchers.Default) {
      val signature = loadTemplateSignature(predicate.templatePngPath)
        ?: return@withContext ImageMatchResult(matched = false)

      val directResult = evaluateImageMatchViaMediaProjection(signature, predicate)
      if (directResult != null) return@withContext directResult

      val fallbackBitmap = captureFallbackBitmap(useShizukuFallback)
        ?: evaluateImageMatchViaMediaProjection(signature, predicate)?.let { return@withContext it }
        ?: return@withContext ImageMatchResult(matched = false)

      try {
        val buffer = ByteBuffer.allocateDirect(fallbackBitmap.byteCount)
        fallbackBitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        DirectBufferVisionMatcher.evaluateTemplateMatch(
          buffer = buffer,
          width = fallbackBitmap.width,
          height = fallbackBitmap.height,
          pixelStride = 4,
          rowStride = fallbackBitmap.width * 4,
          signature = signature,
          similarityThreshold = predicate.similarityThreshold,
          searchRoi = predicate.searchRoi,
          algorithm = predicate.algorithm,
        )
      } finally {
        fallbackBitmap.recycle()
      }
    }

  /**
   * Reads the color of a single physical screen coordinate directly from `Image.planes[0].buffer`
   * for Inspect Mode pixel picking. Returns formatted `#RRGGBB` or null if unavailable.
   */
  suspend fun sampleSinglePixelHex(x: Int, y: Int): String? =
    withContext(Dispatchers.Default) {
      mediaProjectionBridge.withLatestImage { image ->
        val plane = image.planes.firstOrNull() ?: return@withLatestImage null
        val rgb =
          DirectBufferVisionMatcher.readRgbAt(
            buffer = plane.buffer,
            width = image.width,
            height = image.height,
            pixelStride = plane.pixelStride,
            rowStride = plane.rowStride,
            x = x,
            y = y,
          )
        if (rgb >= 0) DirectBufferVisionMatcher.formatRgbHex(rgb) else null
      }
    }

  /**
   * Allocates a clean cropped ARGB_8888 Bitmap strictly for Inspect target saving, lossless PNG
   * error snapshots, or Ktor Desktop Studio frame streaming. Handles `rowStride` padding cleanly,
   * and falls back cleanly from MediaProjection -> Shizuku `screencap` -> Android 11+ Accessibility.
   */
  suspend fun captureCleanBitmap(useShizukuFallback: Boolean = false): Bitmap? =
    withContext(Dispatchers.Default) {
      val mpBitmap = captureMediaProjectionBitmap()
      if (mpBitmap != null) return@withContext mpBitmap

      captureFallbackBitmap(useShizukuFallback) ?: captureMediaProjectionBitmap()
    }

  /** Writes [bitmap] as 100% lossless PNG on [Dispatchers.IO]. */
  suspend fun saveLosslessPng(bitmap: Bitmap, destFile: File): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { out ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
          out.flush()
        }
        true
      }.getOrDefault(false)
    }

  /** Crops [sourceBitmap] cleanly to clamped bounds and writes 100% lossless PNG on [Dispatchers.IO]. */
  suspend fun cropAndSaveLosslessPng(
    sourceBitmap: Bitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    destFile: File,
  ): Boolean =
    withContext(Dispatchers.IO) {
      val safeX = x.coerceIn(0, (sourceBitmap.width - 1).coerceAtLeast(0))
      val safeY = y.coerceIn(0, (sourceBitmap.height - 1).coerceAtLeast(0))
      val safeW = width.coerceIn(1, (sourceBitmap.width - safeX).coerceAtLeast(1))
      val safeH = height.coerceIn(1, (sourceBitmap.height - safeY).coerceAtLeast(1))

      val crop = Bitmap.createBitmap(sourceBitmap, safeX, safeY, safeW, safeH)
      try {
        saveLosslessPng(crop, destFile)
      } finally {
        crop.recycle()
      }
    }

  fun clearTemplateCache() {
    templateCache.evictAll()
  }

  private fun evaluateColorMatchViaMediaProjection(
    predicate: ConditionPredicate.ColorMatch,
  ): ColorMatchResult? {
    return mediaProjectionBridge.withLatestImage { image ->
      val plane = image.planes.firstOrNull() ?: return@withLatestImage null
      DirectBufferVisionMatcher.evaluateColorMatch(
        buffer = plane.buffer,
        width = image.width,
        height = image.height,
        pixelStride = plane.pixelStride,
        rowStride = plane.rowStride,
        predicate = predicate,
      )
    }
  }

  private fun evaluateImageMatchViaMediaProjection(
    signature: TemplateSignature,
    predicate: ConditionPredicate.ImageMatch,
  ): ImageMatchResult? {
    return mediaProjectionBridge.withLatestImage { image ->
      val plane = image.planes.firstOrNull() ?: return@withLatestImage null
      DirectBufferVisionMatcher.evaluateTemplateMatch(
        buffer = plane.buffer,
        width = image.width,
        height = image.height,
        pixelStride = plane.pixelStride,
        rowStride = plane.rowStride,
        signature = signature,
        similarityThreshold = predicate.similarityThreshold,
        searchRoi = predicate.searchRoi,
        algorithm = predicate.algorithm,
      )
    }
  }

  private fun captureMediaProjectionBitmap(): Bitmap? {
    return mediaProjectionBridge.withLatestImage { image ->
      val plane = image.planes.firstOrNull() ?: return@withLatestImage null
      val buffer = plane.buffer
      buffer.rewind()
      val pixelStride = plane.pixelStride.coerceAtLeast(4)
      val rowStride = plane.rowStride
      val rowPadding = (rowStride - (pixelStride * image.width)).coerceAtLeast(0)
      val paddedWidth = image.width + (rowPadding / pixelStride)

      val paddedBitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
      paddedBitmap.copyPixelsFromBuffer(buffer)
      if (paddedWidth == image.width) {
        paddedBitmap
      } else {
        val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, image.width, image.height)
        paddedBitmap.recycle()
        cropped
      }
    }
  }

  private suspend fun captureFallbackBitmap(useShizukuFallback: Boolean): Bitmap? {
    if (useShizukuFallback && shizukuManager.isReadyNow()) {
      captureShizukuBitmap()?.let { return it }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      accessibilityBridge.takeScreenshotBitmap()?.let { return it }
    }
    return null
  }

  private suspend fun loadTemplateSignature(templatePath: String): TemplateSignature? =
    withContext(Dispatchers.IO) {
      if (templatePath.isBlank()) return@withContext null
      val file = File(templatePath)
      if (!file.exists() || !file.isFile) return@withContext null

      val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
      templateCache.get(cacheKey)?.let { return@withContext it }

      val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
      val argbBitmap =
        if (decoded.config == Bitmap.Config.ARGB_8888) {
          decoded
        } else {
          val copied = decoded.copy(Bitmap.Config.ARGB_8888, false)
          decoded.recycle()
          copied
        } ?: return@withContext null

      try {
        val pixels = IntArray(argbBitmap.width * argbBitmap.height)
        argbBitmap.getPixels(pixels, 0, argbBitmap.width, 0, 0, argbBitmap.width, argbBitmap.height)
        val signature = DirectBufferVisionMatcher.extractTemplateSignature(pixels, argbBitmap.width, argbBitmap.height)
        templateCache.put(cacheKey, signature)
        signature
      } finally {
        argbBitmap.recycle()
      }
    }

  private suspend fun captureShizukuBitmap(): Bitmap? {
    val pngBytes = shizukuManager.captureSilentScreenshotPngBytes().getOrNull() ?: return null
    return withContext(Dispatchers.Default) {
      val decoded = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size) ?: return@withContext null
      if (decoded.config == Bitmap.Config.ARGB_8888) {
        decoded
      } else {
        val copied = decoded.copy(Bitmap.Config.ARGB_8888, false)
        decoded.recycle()
        copied
      }
    }
  }
}
