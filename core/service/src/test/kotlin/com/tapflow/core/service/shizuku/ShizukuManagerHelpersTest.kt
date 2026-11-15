package com.tapflow.core.service.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuManagerHelpersTest {

  @Test
  fun isAsciiPrintable_rejectsUnicodeNewlinesAndControlChars() {
    assertTrue(ShizukuManager.isAsciiPrintable("Hello, World! 123 _-+=@#"))
    assertTrue(ShizukuManager.isAsciiPrintable(""))
    assertFalse(ShizukuManager.isAsciiPrintable("你好世界"))
    assertFalse(ShizukuManager.isAsciiPrintable("tap\nflow"))
    assertFalse(ShizukuManager.isAsciiPrintable("café"))
    assertFalse(ShizukuManager.isAsciiPrintable("emoji 🚀"))
  }

  @Test
  fun hasValidPngHeader_validatesMagicBytes() {
    val validPng =
      byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
      )
    assertTrue(ShizukuManager.hasValidPngHeader(validPng))
    assertFalse(ShizukuManager.hasValidPngHeader(byteArrayOf()))
    assertFalse(ShizukuManager.hasValidPngHeader(byteArrayOf(0x89.toByte(), 0x50, 0x4E)))
    assertFalse(ShizukuManager.hasValidPngHeader("Error: screencap failed".encodeToByteArray()))
  }

  @Test
  fun parseResumedActivityPackage_extractsTopPackage() {
    val dump =
      """
      mResumedActivity: ActivityRecord{a1b2c3d u0 com.example.old/.MainActivity t12}
      topResumedActivity=ActivityRecord{d4e5f6a u0 com.game.target/.GameActivity t45}
      """.trimIndent()
    assertEquals("com.game.target", ShizukuManager.parseResumedActivityPackage(dump))
    assertNull(ShizukuManager.parseResumedActivityPackage(""))
  }

  @Test
  fun parseAppOpsGetAllowed_detectsAllowMode() {
    assertTrue(ShizukuManager.parseAppOpsGetAllowed("PROJECT_MEDIA: allow; time=+1m ago"))
    assertFalse(ShizukuManager.parseAppOpsGetAllowed("PROJECT_MEDIA: ignore"))
  }
}
