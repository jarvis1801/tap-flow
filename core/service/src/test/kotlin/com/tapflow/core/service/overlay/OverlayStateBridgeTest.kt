package com.tapflow.core.service.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStateBridgeTest {

  @Test
  fun tracksRunningStateAndSuppressionWindow() {
    var fakeElapsedMs = 10_000L
    val bridge = OverlayStateBridge(clockMs = { fakeElapsedMs })

    assertFalse(bridge.isOverlayRunning.value)
    assertFalse(bridge.shouldAutoCloseOnForeground())

    bridge.suppressAutoCloseBriefly(1500L)
    bridge.setOverlayRunning(true)
    assertTrue(bridge.isOverlayRunning.value)

    // Still inside 1500ms suppression window
    assertFalse(bridge.shouldAutoCloseOnForeground())
    fakeElapsedMs += 1500L
    assertFalse(bridge.shouldAutoCloseOnForeground())

    // Exceeds suppression timestamp -> auto-close allowed
    fakeElapsedMs += 1L
    assertTrue(bridge.shouldAutoCloseOnForeground())

    // New suppression cancelled by onStop -> immediately allows auto-close on return
    bridge.suppressAutoCloseBriefly(1500L)
    assertFalse(bridge.shouldAutoCloseOnForeground())
    bridge.clearAutoCloseSuppression()
    assertTrue(bridge.shouldAutoCloseOnForeground())

    // Overlay stopped -> no longer triggers auto-close
    bridge.setOverlayRunning(false)
    assertFalse(bridge.shouldAutoCloseOnForeground())
  }
}
