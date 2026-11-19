package com.tapflow.core.service.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.shizuku.ShizukuManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
  private val runTemplateSessionDepth = AtomicInteger(0)
  private val runTemplateCache = ConcurrentHashMap<String, TemplateSignature>()

  /**
   * Begins an execution-scoped in-memory template cache session so repeated loop evaluations
   * reuse decoded [ImageMatch] template signatures without touching disk on every check.
   */
  fun beginExecutionTemplateCache() {
    runTemplateSessionDepth.incrementAndGet()
  }

  fun endExecutionTemplateCache() {
    val remaining = runTemplateSessionDepth.updateAndGet { (it - 1).coerceAtLeast(0) }
    if (remaining == 0) {
      runTemplateCache.clear()
    }
  }

  suspend fun <T> withExecutionTemplateCache(block: suspend () -> T): T {
    beginExecutionTemplateCache()
    return try {
      block()
    } finally {
      endExecutionTemplateCache()
    }
  }

  /**
   * Evaluates ColorMatch directly from MediaProjection's `Image.planes[0].buffer` using
   * `(y * rowStride) + (x * pixelStride)` with ZERO intermediate Bitmap allocations.
   * Falls back seamlessly to Shizuku `screencap` and Android 11+ `AccessibilityService.takeScreenshot`,
   * sampling target pixel(s) directly in memory without PNG compression or disk writes.
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
        DirectBufferVisionMatcher.evaluateColorMatchWithReader(
          width = fallbackBitmap.width,
          height = fallbackBitmap.height,
          predicate = predicate,
        ) { x, y ->
          if (x in 0 until fallbackBitmap.width && y in 0 until fallbackBitmap.height) {
            fallbackBitmap.getPixel(x, y) and 0x00FFFFFF
          } else {
            -1
          }
        }
      } finally {
        if (!fallbackBitmap.isRecycled) {
          fallbackBitmap.recycle()
        }
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
        if (!fallbackBitmap.isRecycled) {
          fallbackBitmap.recycle()
        }
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
   * Allocates a clean standalone ARGB_8888 Bitmap strictly for Inspect target saving, lossless PNG
   * error snapshots, or Ktor Desktop Studio frame streaming. Handles `rowStride` padding cleanly,
   * and falls back cleanly from MediaProjection -> Shizuku `screencap` -> Android 11+ Accessibility.
   */
  suspend fun captureCleanBitmap(useShizukuFallback: Boolean = false): Bitmap? =
    withContext(Dispatchers.Default) {
      val mpBitmap = captureMediaProjectionBitmap()
      if (mpBitmap != null) return@withContext mpBitmap

      captureFallbackBitmap(useShizukuFallback) ?: captureMediaProjectionBitmap()
    }

  /** Encodes [bitmap] as 100% lossless PNG bytes strictly on [Dispatchers.Default]. */
  suspend fun encodeLosslessPngBytes(bitmap: Bitmap): ByteArray? =
    withContext(Dispatchers.Default) {
      if (bitmap.isRecycled) return@withContext null
      runCatching {
        val baos = ByteArrayOutputStream(256 * 1024)
        val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        if (ok) baos.toByteArray() else null
      }.getOrNull()
    }

  /** Writes encoded PNG bytes to [destFile] strictly on [Dispatchers.IO]. */
  suspend fun writeBytesToFile(bytes: ByteArray, destFile: File): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { out ->
          out.write(bytes)
          out.flush()
        }
        true
      }.getOrDefault(false)
    }

  /** Compresses [bitmap] as 100% lossless PNG on [Dispatchers.Default] and writes to [destFile] on [Dispatchers.IO]. */
  suspend fun saveLosslessPng(bitmap: Bitmap, destFile: File): Boolean {
    val pngBytes = encodeLosslessPngBytes(bitmap) ?: return false
    return writeBytesToFile(pngBytes, destFile)
  }

  /** Crops [sourceBitmap] cleanly to clamped bounds and writes 100% lossless PNG on [Dispatchers.Default] / [Dispatchers.IO]. */
  suspend fun cropAndSaveLosslessPng(
    sourceBitmap: Bitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    destFile: File,
  ): Boolean {
    val crop =
      withContext(Dispatchers.Default) {
        if (sourceBitmap.isRecycled) return@withContext null
        val safeX = x.coerceIn(0, (sourceBitmap.width - 1).coerceAtLeast(0))
        val safeY = y.coerceIn(0, (sourceBitmap.height - 1).coerceAtLeast(0))
        val safeW = width.coerceIn(1, (sourceBitmap.width - safeX).coerceAtLeast(1))
        val safeH = height.coerceIn(1, (sourceBitmap.height - safeY).coerceAtLeast(1))
        Bitmap.createBitmap(sourceBitmap, safeX, safeY, safeW, safeH)
      } ?: return false
    return try {
      saveLosslessPng(crop, destFile)
    } finally {
      if (!crop.isRecycled) {
        crop.recycle()
      }
    }
  }

  fun clearTemplateCache() {
    templateCache.evictAll()
    runTemplateCache.clear()
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

  internal suspend fun loadTemplateSignature(templatePath: String): TemplateSignature? {
    if (templatePath.isBlank()) return null
    val sessionActive = runTemplateSessionDepth.get() > 0
    if (sessionActive) {
      runTemplateCache[templatePath]?.let { return it }
    }

    val loaded =
      withContext(Dispatchers.IO) {
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
          withContext(Dispatchers.Default) {
            val pixels = IntArray(argbBitmap.width * argbBitmap.height)
            argbBitmap.getPixels(pixels, 0, argbBitmap.width, 0, 0, argbBitmap.width, argbBitmap.height)
            val signature =
              DirectBufferVisionMatcher.extractTemplateSignature(pixels, argbBitmap.width, argbBitmap.height)
            templateCache.put(cacheKey, signature)
            signature
          }
        } finally {
          if (!argbBitmap.isRecycled) {
            argbBitmap.recycle()
          }
        }
      }

    if (loaded != null && runTemplateSessionDepth.get() > 0) {
      runTemplateCache[templatePath] = loaded
    }
    return loaded
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
