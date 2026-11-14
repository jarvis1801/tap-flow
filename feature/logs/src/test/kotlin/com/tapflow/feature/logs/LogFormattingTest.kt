package com.tapflow.feature.logs

import org.junit.Assert.assertEquals
import org.junit.Test

class LogFormattingTest {

  @Test
  fun formatLogDuration_formatsMsSecondsAndMinutesCleanly() {
    assertEquals("0ms", formatLogDuration(-10L))
    assertEquals("450ms", formatLogDuration(450L))
    assertEquals("2.5s", formatLogDuration(2500L))
    assertEquals("2m 5s", formatLogDuration(125_000L))
  }
}
