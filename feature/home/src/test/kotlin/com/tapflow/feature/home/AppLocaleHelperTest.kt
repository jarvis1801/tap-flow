package com.tapflow.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocaleHelperTest {

  @Test
  fun `empty active locales matches English default only`() {
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("", "en"))
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("  ", ""))
    assertFalse(AppLocaleHelper.isLocaleAlreadyActive("", "zh-TW"))
    assertFalse(AppLocaleHelper.isLocaleAlreadyActive("", "zh-CN"))
  }

  @Test
  fun `exact and regional English tags match English`() {
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("en", "en"))
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("en-US,zh-TW", "en"))
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("EN", "en"))
    assertFalse(AppLocaleHelper.isLocaleAlreadyActive("zh-TW", "en"))
  }

  @Test
  fun `Chinese regional and script tags stay distinct`() {
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("zh-TW", "zh-TW"))
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("zh-Hant-TW", "zh-TW"))
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("zh-Hant", "zh-TW"))
    assertFalse(AppLocaleHelper.isLocaleAlreadyActive("zh-TW", "zh-CN"))
    assertFalse(AppLocaleHelper.isLocaleAlreadyActive("zh-Hant", "zh-CN"))

    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("zh-CN", "zh-CN"))
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("zh-Hans-CN", "zh-CN"))
    assertTrue(AppLocaleHelper.isLocaleAlreadyActive("zh-Hans", "zh-CN"))
    assertFalse(AppLocaleHelper.isLocaleAlreadyActive("zh-CN", "zh-TW"))
    assertFalse(AppLocaleHelper.isLocaleAlreadyActive("zh-Hans", "zh-TW"))
  }
}
