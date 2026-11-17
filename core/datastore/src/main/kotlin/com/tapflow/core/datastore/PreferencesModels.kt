package com.tapflow.core.datastore

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.common.OverlayPreferences
import com.tapflow.core.model.common.OverlaySizeOption
import com.tapflow.core.model.common.RunnerLaunchMode

enum class DarkModeOption {
  SYSTEM,
  LIGHT,
  DARK,
}

enum class ThemePaletteOption {
  DYNAMIC_MATERIAL_YOU,
  CYAN_PRECISION,
  COBALT_TACTICAL,
  AMBER_INDUSTRIAL,
  AMOLED_TRUE_BLACK,
}

@Immutable
data class ShizukuPreferences(
  val shellInputInjection: Boolean = false,
  val rootlessForceStop: Boolean = false,
  val enhancedKeyEvents: Boolean = false,
  val silentScreenshots: Boolean = false,
  val autoGrantProjectMedia: Boolean = false,
  val externalBroadcastEnabled: Boolean = false,
)

@Immutable
data class FloatingDockPosition(
  val x: Int = 0,
  val y: Int = 240,
)

@Immutable
data class TrialStorageSnapshot(
  val trialStartedAtUtcMs: Long? = null,
  val maxObservedClockMs: Long = 0L,
  val hmacSignatureBase64: String? = null,
)

@Immutable
data class AppPreferences(
  val hasCompletedPermissionOnboarding: Boolean = false,
  val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
  val themePalette: ThemePaletteOption = ThemePaletteOption.DYNAMIC_MATERIAL_YOU,
  val overlaySize: OverlaySizeOption = OverlaySizeOption.COMPACT,
  val runnerLaunchMode: RunnerLaunchMode = RunnerLaunchMode.ASK_EVERY_TIME,
  val overlay: OverlayPreferences = OverlayPreferences(),
  val ktorPort: Int = DEFAULT_KTOR_PORT,
  val shizuku: ShizukuPreferences = ShizukuPreferences(),
  val dockPosition: FloatingDockPosition = FloatingDockPosition(),
) {
  companion object {
    const val DEFAULT_KTOR_PORT: Int = 18080
  }
}
