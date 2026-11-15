package com.tapflow.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class InspectStorageFormatTest {

  @Test
  fun formatInspectStorageSize_formatsBytesKbMbAndGbCleanly() {
    assertEquals("0 B", formatInspectStorageSize(-5L))
    assertEquals("0 B", formatInspectStorageSize(0L))
    assertEquals("512 B", formatInspectStorageSize(512L))
    assertEquals("1.5 KB", formatInspectStorageSize(1536L))
    assertEquals("42.3 MB", formatInspectStorageSize((42.3 * 1024.0 * 1024.0).toLong()))
    assertEquals("1.25 GB", formatInspectStorageSize((1.25 * 1024.0 * 1024.0 * 1024.0).toLong()))
  }
}
