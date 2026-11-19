package com.tapflow.core.service.vision

import com.tapflow.core.model.condition.ColorMatchMode
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionMatchRule
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.ImageMatchAlgorithm
import com.tapflow.core.model.condition.RegionBounds
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectBufferVisionMatcherTest {

  private fun createPaddedRgbaBuffer(
    width: Int,
    height: Int,
    extraRowPaddingBytes: Int,
    pixelProvider: (x: Int, y: Int) -> Int,
  ): Pair<ByteBuffer, Int> {
    val pixelStride = 4
    val rowStride = (width * pixelStride) + extraRowPaddingBytes
    val buffer = ByteBuffer.allocateDirect(rowStride * height)
    for (y in 0 until height) {
      val rowStart = y * rowStride
      for (x in 0 until width) {
        val rgb = pixelProvider(x, y)
        val offset = rowStart + (x * pixelStride)
        buffer.put(offset, ((rgb ushr 16) and 0xFF).toByte())
        buffer.put(offset + 1, ((rgb ushr 8) and 0xFF).toByte())
        buffer.put(offset + 2, (rgb and 0xFF).toByte())
        buffer.put(offset + 3, 0xFF.toByte())
      }
    }
    buffer.rewind()
    return buffer to rowStride
  }

  @Test
  fun singlePointAndMultiPointColorMatch_respectsRowStridePaddingAndTolerance() {
    val width = 20
    val height = 16
    val (buffer, rowStride) =
      createPaddedRgbaBuffer(width, height, extraRowPaddingBytes = 16) { x, y ->
        when {
          x == 5 && y == 4 -> 0xFF3366
          x == 12 && y == 10 -> 0x10C040
          else -> 0x000000
        }
      }

    val singleOk =
      DirectBufferVisionMatcher.evaluateColorMatch(
        buffer = buffer,
        width = width,
        height = height,
        pixelStride = 4,
        rowStride = rowStride,
        predicate =
          ConditionPredicate.ColorMatch(
            mode = ColorMatchMode.SINGLE_POINT,
            targets = listOf(ColorTarget(x = 5, y = 4, hexColor = "#FC3564", tolerance = 5)),
          ),
      )
    assertTrue(singleOk.matched)
    assertEquals("#FF3366", singleOk.sampledHex)

    val multiAll =
      DirectBufferVisionMatcher.evaluateColorMatch(
        buffer = buffer,
        width = width,
        height = height,
        pixelStride = 4,
        rowStride = rowStride,
        predicate =
          ConditionPredicate.ColorMatch(
            mode = ColorMatchMode.MULTI_POINT,
            rule = ConditionMatchRule.ALL,
            targets =
              listOf(
                ColorTarget(x = 5, y = 4, hexColor = "#FF3366", tolerance = 0),
                ColorTarget(x = 12, y = 10, hexColor = "#10C040", tolerance = 0),
              ),
          ),
      )
    assertTrue(multiAll.matched)
    assertEquals(1f, multiAll.matchRatio, 0.001f)
  }

  @Test
  fun regionColorMatch_calculatesPercentageCorrectly() {
    val width = 10
    val height = 10
    val (buffer, rowStride) =
      createPaddedRgbaBuffer(width, height, extraRowPaddingBytes = 8) { x, _ ->
        if (x < 8) 0x22AA55 else 0x000000
      }

    val result =
      DirectBufferVisionMatcher.evaluateColorMatch(
        buffer = buffer,
        width = width,
        height = height,
        pixelStride = 4,
        rowStride = rowStride,
        predicate =
          ConditionPredicate.ColorMatch(
            mode = ColorMatchMode.REGION,
            region = RegionBounds(0, 0, 9, 9),
            rule = ConditionMatchRule.PERCENT_MATCH,
            matchPercentThreshold = 75,
            targets = listOf(ColorTarget(0, 0, "#22AA55", tolerance = 2)),
          ),
      )
    assertTrue(result.matched)
    assertEquals(0.8f, result.matchRatio, 0.01f)
  }

  @Test
  fun templateMatch_findsExactSubRegionDirectlyInPaddedBuffer() {
    val screenW = 64
    val screenH = 64
    val tplW = 12
    val tplH = 12
    val targetOriginX = 27
    val targetOriginY = 33

    val tplPixels =
      IntArray(tplW * tplH) { idx ->
        val tx = idx % tplW
        val ty = idx / tplW
        val r = (tx * 20).coerceIn(0, 255)
        val g = (ty * 20).coerceIn(0, 255)
        val b = ((tx + ty) * 10).coerceIn(0, 255)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
      }
    val signature = DirectBufferVisionMatcher.extractTemplateSignature(tplPixels, tplW, tplH)

    val (buffer, rowStride) =
      createPaddedRgbaBuffer(screenW, screenH, extraRowPaddingBytes = 32) { x, y ->
        if (x in targetOriginX until (targetOriginX + tplW) &&
          y in targetOriginY until (targetOriginY + tplH)
        ) {
          val tx = x - targetOriginX
          val ty = y - targetOriginY
          tplPixels[ty * tplW + tx] and 0x00FFFFFF
        } else {
          0x182028
        }
      }

    val fastResult =
      DirectBufferVisionMatcher.evaluateTemplateMatch(
        buffer = buffer,
        width = screenW,
        height = screenH,
        pixelStride = 4,
        rowStride = rowStride,
        signature = signature,
        similarityThreshold = 0.92f,
        searchRoi = RegionBounds(10, 10, 60, 60),
        algorithm = ImageMatchAlgorithm.FAST_32_PIXEL,
      )
    assertTrue(fastResult.matched)
    assertEquals(targetOriginX + tplW / 2, fastResult.matchX)
    assertEquals(targetOriginY + tplH / 2, fastResult.matchY)

    val denseMiss =
      DirectBufferVisionMatcher.evaluateTemplateMatch(
        buffer = buffer,
        width = screenW,
        height = screenH,
        pixelStride = 4,
        rowStride = rowStride,
        signature = signature,
        similarityThreshold = 0.92f,
        searchRoi = RegionBounds(0, 0, 20, 20),
        algorithm = ImageMatchAlgorithm.TEMPLATE_MATCH,
      )
    assertFalse(denseMiss.matched)
  }

  @Test
  fun evaluateColorMatchWithReader_samplesOnlyTargetCoordinatesDirectly() {
    val sampledCoords = mutableListOf<Pair<Int, Int>>()
    val result =
      DirectBufferVisionMatcher.evaluateColorMatchWithReader(
        width = 1080,
        height = 2400,
        predicate =
          ConditionPredicate.ColorMatch(
            mode = ColorMatchMode.MULTI_POINT,
            rule = ConditionMatchRule.ALL,
            targets =
              listOf(
                ColorTarget(x = 120, y = 340, hexColor = "#112233", tolerance = 0),
                ColorTarget(x = 880, y = 1900, hexColor = "#445566", tolerance = 0),
              ),
          ),
      ) { x, y ->
        sampledCoords += x to y
        when (x to y) {
          120 to 340 -> 0x112233
          880 to 1900 -> 0x445566
          else -> error("Unexpected coordinate read at ($x, $y)")
        }
      }

    assertTrue(result.matched)
    assertEquals(1f, result.matchRatio, 0.001f)
    assertEquals(listOf(120 to 340, 880 to 1900), sampledCoords)
  }
}
