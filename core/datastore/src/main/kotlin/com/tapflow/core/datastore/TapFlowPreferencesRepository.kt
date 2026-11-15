package com.tapflow.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tapflow.core.model.common.OverlaySizeOption
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class TapFlowPreferencesRepository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
) {
  private object Keys {
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_permission_onboarding")
    val DARK_MODE = stringPreferencesKey("dark_mode_option")
    val THEME_PALETTE = stringPreferencesKey("theme_palette_option")
    val OVERLAY_SIZE = stringPreferencesKey("overlay_size_option")
    val KTOR_PORT = intPreferencesKey("ktor_server_port")

    val SHIZUKU_SHELL_INPUT = booleanPreferencesKey("shizuku_shell_input_injection")
    val SHIZUKU_FORCE_STOP = booleanPreferencesKey("shizuku_rootless_force_stop")
    val SHIZUKU_ENHANCED_KEYS = booleanPreferencesKey("shizuku_enhanced_key_events")
    val SHIZUKU_SILENT_SCREENSHOTS = booleanPreferencesKey("shizuku_silent_screenshots")
    val SHIZUKU_AUTO_GRANT_PROJECT_MEDIA = booleanPreferencesKey("shizuku_auto_grant_project_media")

    val DOCK_POS_X = intPreferencesKey("floating_dock_pos_x")
    val DOCK_POS_Y = intPreferencesKey("floating_dock_pos_y")

    val TRIAL_STARTED_AT_UTC_MS = longPreferencesKey("trial_started_at_utc_ms")
    val TRIAL_MAX_OBSERVED_CLOCK_MS = longPreferencesKey("trial_max_observed_clock_ms")
    val TRIAL_HMAC_SIGNATURE = stringPreferencesKey("trial_hmac_signature_b64")
  }

  private val safeData: Flow<Preferences> =
    dataStore.data.catch { exception ->
      if (exception is IOException) {
        emit(emptyPreferences())
      } else {
        throw exception
      }
    }

  val preferencesFlow: Flow<AppPreferences> =
    safeData.map { prefs ->
      val rawDarkMode = prefs[Keys.DARK_MODE]
      val rawPalette = prefs[Keys.THEME_PALETTE]
      val isLegacyAmoled = rawDarkMode == "AMOLED"
      val darkMode =
        if (isLegacyAmoled) {
          DarkModeOption.DARK
        } else {
          DarkModeOption.entries.firstOrNull { it.name == rawDarkMode } ?: DarkModeOption.SYSTEM
        }
      val themePalette =
        if (isLegacyAmoled) {
          ThemePaletteOption.AMOLED_TRUE_BLACK
        } else {
          ThemePaletteOption.entries.firstOrNull { it.name == rawPalette }
            ?: ThemePaletteOption.DYNAMIC_MATERIAL_YOU
        }
      val rawOverlaySize = prefs[Keys.OVERLAY_SIZE]
      val overlaySize =
        OverlaySizeOption.entries.firstOrNull { it.name == rawOverlaySize }
          ?: OverlaySizeOption.COMPACT
      AppPreferences(
        hasCompletedPermissionOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
        darkMode = darkMode,
        themePalette = themePalette,
        overlaySize = overlaySize,
        ktorPort = (prefs[Keys.KTOR_PORT] ?: AppPreferences.DEFAULT_KTOR_PORT).coerceIn(1024, 65535),
        shizuku =
          ShizukuPreferences(
            shellInputInjection = prefs[Keys.SHIZUKU_SHELL_INPUT] ?: false,
            rootlessForceStop = prefs[Keys.SHIZUKU_FORCE_STOP] ?: false,
            enhancedKeyEvents = prefs[Keys.SHIZUKU_ENHANCED_KEYS] ?: false,
            silentScreenshots = prefs[Keys.SHIZUKU_SILENT_SCREENSHOTS] ?: false,
            autoGrantProjectMedia = prefs[Keys.SHIZUKU_AUTO_GRANT_PROJECT_MEDIA] ?: false,
          ),
        dockPosition =
          FloatingDockPosition(
            x = prefs[Keys.DOCK_POS_X] ?: 0,
            y = prefs[Keys.DOCK_POS_Y] ?: 240,
          ),
      )
    }

  val trialSnapshotFlow: Flow<TrialStorageSnapshot> =
    safeData.map { prefs ->
      TrialStorageSnapshot(
        trialStartedAtUtcMs = prefs[Keys.TRIAL_STARTED_AT_UTC_MS],
        maxObservedClockMs = prefs[Keys.TRIAL_MAX_OBSERVED_CLOCK_MS] ?: 0L,
        hmacSignatureBase64 = prefs[Keys.TRIAL_HMAC_SIGNATURE],
      )
    }

  suspend fun getTrialSnapshotOnce(): TrialStorageSnapshot = trialSnapshotFlow.first()

  suspend fun getPreferencesOnce(): AppPreferences = preferencesFlow.first()

  suspend fun setPermissionOnboardingCompleted(completed: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.HAS_COMPLETED_ONBOARDING] = completed }
  }

  suspend fun setDarkMode(option: DarkModeOption) {
    dataStore.edit { prefs ->
      if (prefs[Keys.DARK_MODE] == "AMOLED" && prefs[Keys.THEME_PALETTE] == null) {
        prefs[Keys.THEME_PALETTE] = ThemePaletteOption.AMOLED_TRUE_BLACK.name
      }
      prefs[Keys.DARK_MODE] = option.name
    }
  }

  suspend fun setThemePalette(palette: ThemePaletteOption) {
    dataStore.edit { prefs ->
      prefs[Keys.THEME_PALETTE] = palette.name
      if (prefs[Keys.DARK_MODE] == "AMOLED") {
        prefs[Keys.DARK_MODE] = DarkModeOption.DARK.name
      }
    }
  }

  suspend fun setOverlaySize(option: OverlaySizeOption) {
    dataStore.edit { prefs -> prefs[Keys.OVERLAY_SIZE] = option.name }
  }

  suspend fun setKtorPort(port: Int) {
    val safePort = port.coerceIn(1024, 65535)
    dataStore.edit { prefs -> prefs[Keys.KTOR_PORT] = safePort }
  }

  suspend fun setShizukuShellInput(enabled: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.SHIZUKU_SHELL_INPUT] = enabled }
  }

  suspend fun setShizukuForceStop(enabled: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.SHIZUKU_FORCE_STOP] = enabled }
  }

  suspend fun setShizukuEnhancedKeys(enabled: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.SHIZUKU_ENHANCED_KEYS] = enabled }
  }

  suspend fun setShizukuSilentScreenshots(enabled: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.SHIZUKU_SILENT_SCREENSHOTS] = enabled }
  }

  suspend fun setShizukuAutoGrantProjectMedia(enabled: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.SHIZUKU_AUTO_GRANT_PROJECT_MEDIA] = enabled }
  }

  suspend fun saveFloatingDockPosition(x: Int, y: Int) {
    dataStore.edit { prefs ->
      prefs[Keys.DOCK_POS_X] = x
      prefs[Keys.DOCK_POS_Y] = y
    }
  }

  suspend fun saveTrialSnapshot(
    startedAtUtcMs: Long,
    maxObservedClockMs: Long,
    hmacSignatureBase64: String,
  ) {
    dataStore.edit { prefs ->
      prefs[Keys.TRIAL_STARTED_AT_UTC_MS] = startedAtUtcMs
      prefs[Keys.TRIAL_MAX_OBSERVED_CLOCK_MS] = maxObservedClockMs
      prefs[Keys.TRIAL_HMAC_SIGNATURE] = hmacSignatureBase64
    }
  }

  suspend fun updateTrialMaxObservedClock(
    maxObservedClockMs: Long,
    hmacSignatureBase64: String,
  ) {
    dataStore.edit { prefs ->
      prefs[Keys.TRIAL_MAX_OBSERVED_CLOCK_MS] = maxObservedClockMs
      prefs[Keys.TRIAL_HMAC_SIGNATURE] = hmacSignatureBase64
    }
  }
}
