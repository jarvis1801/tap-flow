package com.tapflow.feature.home

import com.tapflow.core.service.shizuku.ShizukuDiagnosticState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAutomationGuideStateTest {
  @Test
  fun isAllPrerequisitesReady_requiresOnlyProAndShizukuReady() {
    val readyShizuku =
      ShizukuDiagnosticState(
        isInstalled = true,
        isServiceRunning = true,
        isPermissionGranted = true,
      )
    val unreadyShizuku =
      ShizukuDiagnosticState(
        isInstalled = true,
        isServiceRunning = false,
        isPermissionGranted = false,
      )

    assertTrue(
      ExternalAutomationGuideUiState(
        isPro = true,
        shizukuStatus = readyShizuku,
      ).isAllPrerequisitesReady,
    )
    assertFalse(
      ExternalAutomationGuideUiState(
        isPro = false,
        shizukuStatus = readyShizuku,
      ).isAllPrerequisitesReady,
    )
    assertFalse(
      ExternalAutomationGuideUiState(
        isPro = true,
        shizukuStatus = unreadyShizuku,
      ).isAllPrerequisitesReady,
    )
  }
}
