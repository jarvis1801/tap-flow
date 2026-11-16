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

  @Test
  fun evaluateSequentialDiagnostics_enforcesStrictCascade() {
    // 1. Not installed -> serviceRunning, permissionGranted, userServiceBound MUST be false
    val notInstalled =
      ShizukuManager.evaluateSequentialDiagnostics(
        installed = false,
        binderAlive = true,
        permissionGranted = true,
        userServiceBinderAlive = true,
        projectMediaAllowed = false,
      )
    assertFalse(notInstalled.isInstalled)
    assertFalse(notInstalled.isServiceRunning)
    assertFalse(notInstalled.isPermissionGranted)
    assertFalse(notInstalled.isUserServiceBound)
    assertFalse(notInstalled.isFullyReady)

    // 2. Installed but service not running -> permissionGranted MUST be false
    val notRunning =
      ShizukuManager.evaluateSequentialDiagnostics(
        installed = true,
        binderAlive = false,
        permissionGranted = true,
        userServiceBinderAlive = true,
        projectMediaAllowed = false,
      )
    assertTrue(notRunning.isInstalled)
    assertFalse(notRunning.isServiceRunning)
    assertFalse(notRunning.isPermissionGranted)
    assertFalse(notRunning.isUserServiceBound)
    assertFalse(notRunning.isFullyReady)

    // 3. Installed & running, permission not granted -> userServiceBound & isFullyReady MUST be false
    val awaitingPermission =
      ShizukuManager.evaluateSequentialDiagnostics(
        installed = true,
        binderAlive = true,
        permissionGranted = false,
        userServiceBinderAlive = true,
        projectMediaAllowed = false,
      )
    assertTrue(awaitingPermission.isInstalled)
    assertTrue(awaitingPermission.isServiceRunning)
    assertFalse(awaitingPermission.isPermissionGranted)
    assertFalse(awaitingPermission.isUserServiceBound)
    assertFalse(awaitingPermission.isFullyReady)

    // 4. All 3 checks pass -> isFullyReady is true
    val ready =
      ShizukuManager.evaluateSequentialDiagnostics(
        installed = true,
        binderAlive = true,
        permissionGranted = true,
        userServiceBinderAlive = true,
        projectMediaAllowed = true,
      )
    assertTrue(ready.isInstalled)
    assertTrue(ready.isServiceRunning)
    assertTrue(ready.isPermissionGranted)
    assertTrue(ready.isUserServiceBound)
    assertTrue(ready.isProjectMediaAllowed)
    assertTrue(ready.isFullyReady)
  }

  @Test
  fun shellSingleQuote_escapesApostrophesSafely() {
    assertEquals("'hello world'", ShizukuManager.shellSingleQuote("hello world"))
    assertEquals("'it'\\''s fine'", ShizukuManager.shellSingleQuote("it's fine"))
  }
}
