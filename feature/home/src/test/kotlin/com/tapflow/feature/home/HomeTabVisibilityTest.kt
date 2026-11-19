package com.tapflow.feature.home

import com.tapflow.core.model.common.FeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeTabVisibilityTest {
  @Test
  fun desktopConsoleFlag_isDisabledForV1Release() {
    assertFalse(FeatureFlags.ENABLE_DESKTOP_CONSOLE)
  }

  @Test
  fun visibleEntries_omitsConsoleTab_whenDesktopConsoleDisabled() {
    assertEquals(
      listOf(
        HomeTabType.SCRIPT,
        HomeTabType.PIPELINE,
        HomeTabType.INSPECT,
        HomeTabType.SEAMLESS,
        HomeTabType.SETTINGS,
      ),
      HomeTabType.visibleEntries(),
    )
  }

  @Test
  fun sanitizeTab_fallsBackFromConsoleToScript_whenDesktopConsoleDisabled() {
    assertEquals(HomeTabType.SCRIPT, HomeTabType.sanitizeTab(HomeTabType.CONSOLE))
    assertEquals(HomeTabType.SETTINGS, HomeTabType.sanitizeTab(HomeTabType.SETTINGS))
  }
}
