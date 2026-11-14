package com.tapflow.feature.studio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StudioTransformTest {

  @Test
  fun formatArgbToHex_formatsUpperRgbWithoutAlpha() {
    assertEquals("#00FF88", StudioBitmapProcessor.formatArgbToHex(0xFF00FF88.toInt()))
    assertEquals("#000000", StudioBitmapProcessor.formatArgbToHex(0xFF000000.toInt()))
    assertEquals("#FFFFFF", StudioBitmapProcessor.formatArgbToHex(0x80FFFFFF.toInt()))
  }

  @Test
  fun computeImageTransform_mapsCenterAndClampsBounds() {
    val tf =
      computeImageTransform(
        viewport = IntSize(1080, 2160),
        imgWidth = 1080,
        imgHeight = 2160,
        zoom = 1f,
        pan = Offset.Zero,
      )
    assertNotNull(tf)
    tf!!
    assertEquals(1f, tf.totalScale, 0.001f)
    val (cx, cy) = tf.screenToImage(540f, 1080f)
    assertEquals(540, cx)
    assertEquals(1080, cy)

    val (clampedX, clampedY) = tf.screenToImage(-100f, 9999f)
    assertEquals(0, clampedX)
    assertEquals(2159, clampedY)
  }

  @Test
  fun computeImageTransform_supportsTenXZoomRoundTrip() {
    val tf =
      computeImageTransform(
        viewport = IntSize(1080, 1920),
        imgWidth = 1080,
        imgHeight = 1920,
        zoom = 10f,
        pan = Offset.Zero,
      )
    assertNotNull(tf)
    tf!!
    assertEquals(10f, tf.totalScale, 0.001f)
    val screen = tf.imageToScreen(540f, 960f)
    val (backX, backY) = tf.screenToImage(screen.x, screen.y)
    assertEquals(540, backX)
    assertEquals(960, backY)
  }
}
