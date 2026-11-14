package com.tapflow.core.service.vision

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.condition.ColorMatchMode
import com.tapflow.core.model.condition.ConditionMatchRule
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.ImageMatchAlgorithm
import com.tapflow.core.model.condition.RegionBounds
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Immutable
data class ColorMatchResult(
  val matched: Boolean,
  val matchRatio: Float,
  val sampledHex: String? = null,
)

@Immutable
data class ImageMatchResult(
  val matched: Boolean,
  val matchX: Int = 0,
  val matchY: Int = 0,
  val similarity: Float = 0f,
)

data class TemplateSignature(
  val width: Int,
  val height: Int,
  /** Triplets: [dx, dy, rgb, dx, dy, rgb, ...] up to 32 points */
  val fastPoints: IntArray,
  /** Triplets: [dx, dy, rgb, ...] dense grid points for TEMPLATE_MATCH verification */
  val densePoints: IntArray,
)

internal object DirectBufferVisionMatcher {
  private const val MAX_FAST_POINTS = 32
  private const val REGION_GRID_SIDE = 16
  private const val MAX_DENSE_GRID_SIDE = 20

  fun parseHexRgb(hexColor: String): Int {
    val clean = hexColor.trim().removePrefix("#")
    val parsed =
      when (clean.length) {
        6 -> clean.toLongOrNull(16)?.toInt() ?: 0
        8 -> clean.substring(2).toLongOrNull(16)?.toInt() ?: 0
        else -> 0
      }
    return parsed and 0x00FFFFFF
  }

  fun formatRgbHex(rgb: Int): String = String.format("#%06X", rgb and 0x00FFFFFF)

  /**
   * Reads an RGBA_8888 pixel directly from [buffer] at `(y * rowStride) + (x * pixelStride)`
   * without allocating any intermediate Bitmap. Returns packed 24-bit `0x00RRGGBB` or `-1` if OOB.
   */
  fun readRgbAt(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    pixelStride: Int,
    rowStride: Int,
    x: Int,
    y: Int,
  ): Int {
    if (x < 0 || y < 0 || x >= width || y >= height) return -1
    val offset = (y * rowStride) + (x * pixelStride)
    if (offset < 0 || offset + 2 >= buffer.limit()) return -1
    val r = buffer.get(offset).toInt() and 0xFF
    val g = buffer.get(offset + 1).toInt() and 0xFF
    val b = buffer.get(offset + 2).toInt() and 0xFF
    return (r shl 16) or (g shl 8) or b
  }

  fun isColorWithinTolerance(rgb1: Int, rgb2: Int, tolerance: Int): Boolean {
    val tol = tolerance.coerceAtLeast(0)
    val dr = abs(((rgb1 ushr 16) and 0xFF) - ((rgb2 ushr 16) and 0xFF))
    val dg = abs(((rgb1 ushr 8) and 0xFF) - ((rgb2 ushr 8) and 0xFF))
    val db = abs((rgb1 and 0xFF) - (rgb2 and 0xFF))
    return dr <= tol && dg <= tol && db <= tol
  }

  fun colorSimilarity(rgb1: Int, rgb2: Int): Float {
    val dr = abs(((rgb1 ushr 16) and 0xFF) - ((rgb2 ushr 16) and 0xFF))
    val dg = abs(((rgb1 ushr 8) and 0xFF) - ((rgb2 ushr 8) and 0xFF))
    val db = abs((rgb1 and 0xFF) - (rgb2 and 0xFF))
    return 1f - ((dr + dg + db) / (3f * 255f))
  }

  fun evaluateColorMatch(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    pixelStride: Int,
    rowStride: Int,
    predicate: ConditionPredicate.ColorMatch,
  ): ColorMatchResult {
    return when (predicate.mode) {
      ColorMatchMode.SINGLE_POINT -> {
        val target = predicate.targets.firstOrNull()
          ?: return ColorMatchResult(matched = false, matchRatio = 0f)
        val sampled = readRgbAt(buffer, width, height, pixelStride, rowStride, target.x, target.y)
        if (sampled < 0) {
          return ColorMatchResult(matched = false, matchRatio = 0f)
        }
        val expected = parseHexRgb(target.hexColor)
        val ok = isColorWithinTolerance(sampled, expected, target.tolerance)
        ColorMatchResult(
          matched = ok,
          matchRatio = if (ok) 1f else 0f,
          sampledHex = formatRgbHex(sampled),
        )
      }

      ColorMatchMode.MULTI_POINT -> {
        if (predicate.targets.isEmpty()) {
          return ColorMatchResult(matched = false, matchRatio = 0f)
        }
        var hitCount = 0
        var firstHex: String? = null
        for (target in predicate.targets) {
          val sampled = readRgbAt(buffer, width, height, pixelStride, rowStride, target.x, target.y)
          if (sampled >= 0) {
            if (firstHex == null) firstHex = formatRgbHex(sampled)
            val expected = parseHexRgb(target.hexColor)
            if (isColorWithinTolerance(sampled, expected, target.tolerance)) {
              hitCount++
            }
          }
        }
        val ratio = hitCount.toFloat() / predicate.targets.size.toFloat()
        val matched = evaluateRule(predicate.rule, hitCount, predicate.targets.size, predicate.matchPercentThreshold)
        ColorMatchResult(matched = matched, matchRatio = ratio, sampledHex = firstHex)
      }

      ColorMatchMode.REGION -> {
        val region = predicate.region
          ?: return ColorMatchResult(matched = false, matchRatio = 0f)
        val target = predicate.targets.firstOrNull()
          ?: return ColorMatchResult(matched = false, matchRatio = 0f)

        val minX = min(region.x1, region.x2).coerceIn(0, width - 1)
        val maxX = max(region.x1, region.x2).coerceIn(0, width - 1)
        val minY = min(region.y1, region.y2).coerceIn(0, height - 1)
        val maxY = max(region.y1, region.y2).coerceIn(0, height - 1)
        if (maxX < minX || maxY < minY) {
          return ColorMatchResult(matched = false, matchRatio = 0f)
        }

        val spanX = maxX - minX + 1
        val spanY = maxY - minY + 1
        val stepX = max(1, spanX / REGION_GRID_SIDE)
        val stepY = max(1, spanY / REGION_GRID_SIDE)

        val expected = parseHexRgb(target.hexColor)
        var totalSamples = 0
        var hitSamples = 0
        var firstHex: String? = null

        var y = minY
        while (y <= maxY) {
          var x = minX
          while (x <= maxX) {
            val sampled = readRgbAt(buffer, width, height, pixelStride, rowStride, x, y)
            if (sampled >= 0) {
              if (firstHex == null) firstHex = formatRgbHex(sampled)
              totalSamples++
              if (isColorWithinTolerance(sampled, expected, target.tolerance)) {
                hitSamples++
              }
            }
            x += stepX
          }
          y += stepY
        }

        if (totalSamples == 0) {
          return ColorMatchResult(matched = false, matchRatio = 0f)
        }
        val ratio = hitSamples.toFloat() / totalSamples.toFloat()
        val matched = evaluateRule(predicate.rule, hitSamples, totalSamples, predicate.matchPercentThreshold)
        ColorMatchResult(matched = matched, matchRatio = ratio, sampledHex = firstHex)
      }
    }
  }

  private fun evaluateRule(
    rule: ConditionMatchRule,
    hits: Int,
    total: Int,
    percentThreshold: Int,
  ): Boolean {
    if (total <= 0) return false
    return when (rule) {
      ConditionMatchRule.ALL -> hits == total
      ConditionMatchRule.ANY -> hits > 0
      ConditionMatchRule.PERCENT_MATCH -> ((hits * 100f) / total) >= percentThreshold.coerceIn(1, 100)
    }
  }

  fun extractTemplateSignature(argbPixels: IntArray, width: Int, height: Int): TemplateSignature {
    if (width <= 0 || height <= 0 || argbPixels.size < width * height) {
      return TemplateSignature(0, 0, IntArray(0), IntArray(0))
    }

    val opaqueCoords = ArrayList<Int>(min(width * height, MAX_DENSE_GRID_SIDE * MAX_DENSE_GRID_SIDE))
    val stepY = max(1, height / MAX_DENSE_GRID_SIDE)
    val stepX = max(1, width / MAX_DENSE_GRID_SIDE)

    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        val px = argbPixels[y * width + x]
        val alpha = (px ushr 24) and 0xFF
        if (alpha >= 128) {
          opaqueCoords.add(x)
          opaqueCoords.add(y)
          opaqueCoords.add(px and 0x00FFFFFF)
        }
        x += stepX
      }
      y += stepY
    }

    // Fallback if template was mostly transparent or tiny
    if (opaqueCoords.isEmpty()) {
      for (py in 0 until height step stepY) {
        for (pxIdx in 0 until width step stepX) {
          val px = argbPixels[py * width + pxIdx]
          opaqueCoords.add(pxIdx)
          opaqueCoords.add(py)
          opaqueCoords.add(px and 0x00FFFFFF)
        }
      }
    }

    val densePoints = opaqueCoords.toIntArray()
    val totalDense = densePoints.size / 3
    if (totalDense == 0) {
      return TemplateSignature(width, height, IntArray(0), IntArray(0))
    }

    val fastCount = min(MAX_FAST_POINTS, totalDense)
    val fastPoints = IntArray(fastCount * 3)
    for (i in 0 until fastCount) {
      val srcIdx = ((i.toLong() * totalDense) / fastCount).toInt().coerceIn(0, totalDense - 1) * 3
      val dstIdx = i * 3
      fastPoints[dstIdx] = densePoints[srcIdx]
      fastPoints[dstIdx + 1] = densePoints[srcIdx + 1]
      fastPoints[dstIdx + 2] = densePoints[srcIdx + 2]
    }

    return TemplateSignature(
      width = width,
      height = height,
      fastPoints = fastPoints,
      densePoints = densePoints,
    )
  }

  fun evaluateTemplateMatch(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    pixelStride: Int,
    rowStride: Int,
    signature: TemplateSignature,
    similarityThreshold: Float,
    searchRoi: RegionBounds?,
    algorithm: ImageMatchAlgorithm,
  ): ImageMatchResult {
    val tplW = signature.width
    val tplH = signature.height
    if (tplW <= 0 || tplH <= 0 || tplW > width || tplH > height || signature.fastPoints.isEmpty()) {
      return ImageMatchResult(matched = false)
    }

    val minX: Int
    val minY: Int
    val maxX: Int
    val maxY: Int
    if (searchRoi != null) {
      minX = min(searchRoi.x1, searchRoi.x2).coerceIn(0, width - tplW)
      minY = min(searchRoi.y1, searchRoi.y2).coerceIn(0, height - tplH)
      val roiRight = max(searchRoi.x1, searchRoi.x2).coerceIn(0, width)
      val roiBottom = max(searchRoi.y1, searchRoi.y2).coerceIn(0, height)
      maxX = (roiRight - tplW).coerceIn(minX, width - tplW)
      maxY = (roiBottom - tplH).coerceIn(minY, height - tplH)
    } else {
      minX = 0
      minY = 0
      maxX = width - tplW
      maxY = height - tplH
    }

    val threshold = similarityThreshold.coerceIn(0.1f, 1.0f)
    // Channel tolerance scaled from threshold so small JPEG/anti-aliasing variance passes fast check
    val channelTolerance = ((1f - threshold) * 255f).roundToInt().coerceIn(10, 64)
    val fastPoints = signature.fastPoints
    val fastCount = fastPoints.size / 3
    val maxFastMisses = ((1f - threshold) * fastCount).roundToInt().coerceAtLeast(1)

    val candidateArea = (maxX - minX + 1L) * (maxY - minY + 1L)
    val coarseStep =
      if (candidateArea > 60_000L && tplW >= 8 && tplH >= 8) {
        2
      } else {
        1
      }

    var bestScore = -1f
    var bestX = minX
    var bestY = minY

    var y = minY
    while (y <= maxY) {
      var x = minX
      while (x <= maxX) {
        val fastScore =
          scoreFastPoints(
            buffer = buffer,
            pixelStride = pixelStride,
            rowStride = rowStride,
            originX = x,
            originY = y,
            fastPoints = fastPoints,
            fastCount = fastCount,
            channelTolerance = channelTolerance,
            maxMisses = maxFastMisses,
          )
        if (fastScore >= 0f) {
          val score =
            when (algorithm) {
              ImageMatchAlgorithm.FAST_32_PIXEL -> fastScore
              ImageMatchAlgorithm.TEMPLATE_MATCH ->
                scoreDensePoints(
                  buffer = buffer,
                  pixelStride = pixelStride,
                  rowStride = rowStride,
                  originX = x,
                  originY = y,
                  densePoints = signature.densePoints,
                )
            }
          if (score > bestScore) {
            bestScore = score
            bestX = x
            bestY = y
          }
        }
        x += coarseStep
      }
      y += coarseStep
    }

    // Refine at 1px precision around coarse winner when coarseStep > 1
    if (coarseStep > 1 && bestScore >= 0f) {
      val refMinX = (bestX - coarseStep).coerceAtLeast(minX)
      val refMaxX = (bestX + coarseStep).coerceAtMost(maxX)
      val refMinY = (bestY - coarseStep).coerceAtLeast(minY)
      val refMaxY = (bestY + coarseStep).coerceAtMost(maxY)
      for (ry in refMinY..refMaxY) {
        for (rx in refMinX..refMaxX) {
          val score =
            when (algorithm) {
              ImageMatchAlgorithm.FAST_32_PIXEL ->
                scoreFastPoints(
                  buffer = buffer,
                  pixelStride = pixelStride,
                  rowStride = rowStride,
                  originX = rx,
                  originY = ry,
                  fastPoints = fastPoints,
                  fastCount = fastCount,
                  channelTolerance = channelTolerance,
                  maxMisses = fastCount,
                )
              ImageMatchAlgorithm.TEMPLATE_MATCH ->
                scoreDensePoints(
                  buffer = buffer,
                  pixelStride = pixelStride,
                  rowStride = rowStride,
                  originX = rx,
                  originY = ry,
                  densePoints = signature.densePoints,
                )
            }
          if (score > bestScore) {
            bestScore = score
            bestX = rx
            bestY = ry
          }
        }
      }
    }

    val clampedBest = bestScore.coerceIn(0f, 1f)
    return if (clampedBest >= threshold) {
      ImageMatchResult(
        matched = true,
        matchX = bestX + (tplW / 2),
        matchY = bestY + (tplH / 2),
        similarity = clampedBest,
      )
    } else {
      ImageMatchResult(
        matched = false,
        matchX = bestX + (tplW / 2),
        matchY = bestY + (tplH / 2),
        similarity = clampedBest,
      )
    }
  }

  private fun scoreFastPoints(
    buffer: ByteBuffer,
    pixelStride: Int,
    rowStride: Int,
    originX: Int,
    originY: Int,
    fastPoints: IntArray,
    fastCount: Int,
    channelTolerance: Int,
    maxMisses: Int,
  ): Float {
    var misses = 0
    var similaritySum = 0f
    val limit = buffer.limit()
    var i = 0
    while (i < fastPoints.size) {
      val px = originX + fastPoints[i]
      val py = originY + fastPoints[i + 1]
      val expectedRgb = fastPoints[i + 2]
      val offset = (py * rowStride) + (px * pixelStride)
      if (offset < 0 || offset + 2 >= limit) {
        misses++
        if (misses > maxMisses) return -1f
      } else {
        val r = buffer.get(offset).toInt() and 0xFF
        val g = buffer.get(offset + 1).toInt() and 0xFF
        val b = buffer.get(offset + 2).toInt() and 0xFF
        val er = (expectedRgb ushr 16) and 0xFF
        val eg = (expectedRgb ushr 8) and 0xFF
        val eb = expectedRgb and 0xFF
        val dr = abs(r - er)
        val dg = abs(g - eg)
        val db = abs(b - eb)
        if (dr > channelTolerance || dg > channelTolerance || db > channelTolerance) {
          misses++
          if (misses > maxMisses) return -1f
        }
        similaritySum += 1f - ((dr + dg + db) / (3f * 255f))
      }
      i += 3
    }
    return similaritySum / fastCount.toFloat()
  }

  private fun scoreDensePoints(
    buffer: ByteBuffer,
    pixelStride: Int,
    rowStride: Int,
    originX: Int,
    originY: Int,
    densePoints: IntArray,
  ): Float {
    val count = densePoints.size / 3
    if (count == 0) return 0f
    var similaritySum = 0f
    val limit = buffer.limit()
    var i = 0
    while (i < densePoints.size) {
      val px = originX + densePoints[i]
      val py = originY + densePoints[i + 1]
      val expectedRgb = densePoints[i + 2]
      val offset = (py * rowStride) + (px * pixelStride)
      if (offset >= 0 && offset + 2 < limit) {
        val r = buffer.get(offset).toInt() and 0xFF
        val g = buffer.get(offset + 1).toInt() and 0xFF
        val b = buffer.get(offset + 2).toInt() and 0xFF
        val er = (expectedRgb ushr 16) and 0xFF
        val eg = (expectedRgb ushr 8) and 0xFF
        val eb = expectedRgb and 0xFF
        val dr = abs(r - er)
        val dg = abs(g - eg)
        val db = abs(b - eb)
        similaritySum += 1f - ((dr + dg + db) / (3f * 255f))
      }
      i += 3
    }
    return similaritySum / count.toFloat()
  }
}
