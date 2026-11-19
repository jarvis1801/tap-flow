package com.tapflow.feature.home

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.ThemePaletteOption
import com.tapflow.core.model.common.FeatureFlags
import com.tapflow.core.model.common.OverlaySizeOption
import com.tapflow.core.model.common.RunnerLaunchMode
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus

@Composable
fun SettingsTabContent(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val currentLocales = remember(configuration) {
    AppCompatDelegate.getApplicationLocales().toLanguageTags()
  }

  LaunchedEffect(Unit) {
    onIntent(HomeUiIntent.RefreshInspectStorageStats)
  }

  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "pro_banner_card") {
        SettingsProBannerCard(
          state = state,
          onUpgrade = { onIntent(HomeUiIntent.OpenPaywallClicked) },
          onStartTrial = { onIntent(HomeUiIntent.ActivateSevenDayTrialClicked) },
          onRestore = { onIntent(HomeUiIntent.RestorePurchaseClicked) },
        )
      }

      item(key = "appearance_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.ColorLens,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }

            Text(
              text = "Dark Mode",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              DarkModeOption.entries.forEach { option ->
                FilterChip(
                  selected = state.preferences.darkMode == option,
                  onClick = { onIntent(HomeUiIntent.SetDarkMode(option)) },
                  label = {
                    Text(
                      when (option) {
                        DarkModeOption.SYSTEM -> "System Default"
                        DarkModeOption.LIGHT -> "Force Light"
                        DarkModeOption.DARK -> "Force Dark"
                      },
                    )
                  },
                  modifier = Modifier.testTag("dark_mode_${option.name.lowercase()}"),
                )
              }
            }

            HorizontalDivider()

            Text(
              text = "Theme Palette",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val effectivePalette =
              if (state.preferences.themePalette == ThemePaletteOption.AMOLED_TRUE_BLACK &&
                !state.isPro
              ) {
                ThemePaletteOption.DYNAMIC_MATERIAL_YOU
              } else {
                state.preferences.themePalette
              }
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              ThemePaletteOption.entries.forEach { palette ->
                val isAmoled = palette == ThemePaletteOption.AMOLED_TRUE_BLACK
                FilterChip(
                  selected = effectivePalette == palette,
                  onClick = { onIntent(HomeUiIntent.SetThemePalette(palette)) },
                  leadingIcon =
                    if (isAmoled && !state.isPro) {
                      {
                        Icon(
                          imageVector = Icons.Filled.Lock,
                          contentDescription = "TapFlow Pro required",
                          modifier = Modifier.size(16.dp),
                        )
                      }
                    } else {
                      null
                    },
                  label = {
                    Text(
                      when (palette) {
                        ThemePaletteOption.DYNAMIC_MATERIAL_YOU -> "Material You"
                        ThemePaletteOption.CYAN_PRECISION -> "Cyan Precision"
                        ThemePaletteOption.COBALT_TACTICAL -> "Cobalt Tactical"
                        ThemePaletteOption.AMBER_INDUSTRIAL -> "Amber Industrial"
                        ThemePaletteOption.AMOLED_TRUE_BLACK -> "AMOLED True Black [Pro]"
                      },
                    )
                  },
                  modifier = Modifier.testTag("palette_${palette.name.lowercase()}"),
                )
              }
            }
          }
        }
      }

      item(key = "floating_overlay_card") {
        Card(
          modifier = Modifier.fillMaxWidth().testTag("settings_floating_overlay_card"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Floating Overlay & Safety",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }

            Text(
              text = "Overlay Size",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              OverlaySizeOption.entries.forEach { sizeOption ->
                FilterChip(
                  selected = state.preferences.overlaySize == sizeOption,
                  onClick = { onIntent(HomeUiIntent.SetOverlaySize(sizeOption)) },
                  label = { Text(sizeOption.displayName) },
                  modifier = Modifier.testTag("overlay_size_${sizeOption.name.lowercase()}"),
                )
              }
            }

            val selectedSize = state.preferences.overlaySize
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              contentColor = MaterialTheme.colorScheme.onSurface,
              shape = RoundedCornerShape(12.dp),
              border =
                BorderStroke(
                  0.75.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
              modifier = Modifier.fillMaxWidth().testTag("overlay_size_preview"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Box(
                  modifier = Modifier.size(52.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    border =
                      BorderStroke(
                        0.75.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                      ),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier.size(selectedSize.bubbleSizeDp.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(selectedSize.iconSizeDp.dp),
                      )
                    }
                  }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = "${selectedSize.displayName} Floating Dock",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text =
                      "Button ${selectedSize.buttonSizeDp}dp • Icon ${selectedSize.iconSizeDp}dp • Padding ${selectedSize.paddingDp}dp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            HorizontalDivider()

            Text(
              text = "Runner Launch Behavior",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              RunnerLaunchMode.entries.forEach { mode ->
                FilterChip(
                  selected = state.preferences.runnerLaunchMode == mode,
                  onClick = { onIntent(HomeUiIntent.SetRunnerLaunchMode(mode)) },
                  label = {
                    Text(
                      when (mode) {
                        RunnerLaunchMode.ASK_EVERY_TIME -> "Ask Every Time"
                        RunnerLaunchMode.STANDBY -> "Standby"
                        RunnerLaunchMode.AUTO_START -> "Auto-Start"
                      },
                    )
                  },
                  modifier = Modifier.testTag("runner_launch_mode_${mode.name.lowercase()}"),
                )
              }
            }
            Text(
              text =
                when (state.preferences.runnerLaunchMode) {
                  RunnerLaunchMode.ASK_EVERY_TIME ->
                    "Prompts to choose Standby or Start Immediately whenever you tap ▶."
                  RunnerLaunchMode.STANDBY ->
                    "Opens floating controls paused so you can navigate to your target app first."
                  RunnerLaunchMode.AUTO_START ->
                    "Begins executing actions immediately upon opening the floating runner."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(
              text = "Safety & Debug Controls",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val overlayPrefs = state.preferences.overlay
            OverlaySafetySwitchRow(
              title = "Emergency Stop via Volume Down",
              subtitle =
                "Off by default. Pressing hardware Volume Down while running immediately pauses automation.",
              checked = overlayPrefs.volumeStopEnabled,
              testTag = "toggle_volume_emergency_stop",
              onCheckedChange = { enabled ->
                onIntent(HomeUiIntent.SetVolumeStopEnabled(enabled))
              },
            )

            OverlaySafetySwitchRow(
              title = "Keep Screen Awake While Running",
              subtitle =
                "Applies keep-screen-on strictly while scripts or pipelines are actively executing.",
              checked = overlayPrefs.keepScreenAwakeWhileRunning,
              testTag = "toggle_keep_screen_awake",
              onCheckedChange = { enabled ->
                onIntent(HomeUiIntent.SetKeepScreenAwakeWhileRunning(enabled))
              },
            )

            OverlaySafetySwitchRow(
              title = "Auto-Pause on Screen Off",
              subtitle =
                "Automatically pauses playback and overlay capture when the device screen turns off or locks.",
              checked = overlayPrefs.pauseOnScreenOff,
              testTag = "toggle_pause_on_screen_off",
              onCheckedChange = { enabled ->
                onIntent(HomeUiIntent.SetPauseOnScreenOff(enabled))
              },
            )

            OverlaySafetySwitchRow(
              title = "Show Debug Touch Markers",
              subtitle =
                "Briefly renders a 300ms hollow ring and swipe trail at executed touch coordinates.",
              checked = overlayPrefs.showDebugTouchMarker,
              testTag = "toggle_debug_touch_marker",
              onCheckedChange = { enabled ->
                onIntent(HomeUiIntent.SetShowDebugTouchMarker(enabled))
              },
            )
          }
        }
      }

      item(key = "localization_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Language",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              AppLocaleOption.entries.forEach { localeOption ->
                val isSelected = AppLocaleHelper.isLocaleOptionSelected(currentLocales, localeOption)
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    if (!isSelected) {
                      onIntent(HomeUiIntent.SelectLocale(localeOption, currentLocales))
                    }
                  },
                  label = { Text(localeOption.label) },
                  modifier = Modifier.testTag("locale_${localeOption.languageTag.lowercase()}"),
                )
              }
            }
          }
        }
      }

      item(key = "storage_cache_card") {
        val stats = state.inspectStorageStats
        val hasInspectImages = stats.imageCount > 0 || stats.totalBytes > 0L
        val imageWord = if (stats.imageCount == 1) "image" else "images"
        Card(
          modifier = Modifier.fillMaxWidth().testTag("settings_storage_cache_card"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Storage & Cache",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }

            Text(
              text =
                "Clears Inspect screenshot and crop PNGs on disk while keeping Inspect groups, saved Pixel colors, and Scripts with Image Match templates.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text =
                    "Inspect Screenshots: ${formatInspectStorageSize(stats.totalBytes)} (${stats.imageCount} $imageWord)",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.testTag("settings_inspect_storage_text"),
                )
                if (stats.groupCount > 0 || stats.itemCount > 0) {
                  val itemWord = if (stats.itemCount == 1) "item" else "items"
                  Text(
                    text = "${stats.groupCount} group(s) • ${stats.itemCount} inspect $itemWord kept in library",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              FilledTonalButton(
                onClick = { onIntent(HomeUiIntent.RequestClearAllInspectData) },
                enabled = hasInspectImages && !state.isClearingInspectData,
                colors =
                  ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                  ),
                modifier =
                  Modifier
                    .heightIn(min = 48.dp)
                    .testTag("settings_clear_inspect_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.DeleteOutline,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear All")
              }
            }
          }
        }
      }

      item(key = "backup_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Backup,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Data Backup & Transfer (.tapflow) [Lifetime Pro]",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }

            Text(
              text =
                "Exports scripts, pipelines, variables, and action logic to a portable .tapflow file. Inspect screenshots remain strictly device-local.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.isLifetimePro) {
              Text(
                text = "Requires Lifetime Pro purchase (not included in 7-day trial).",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.testTag("backup_lifetime_required_note"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              FilledTonalButton(
                onClick = { onIntent(HomeUiIntent.ExportBackupClicked) },
                modifier =
                  Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("export_tapflow_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.FileUpload,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export")
              }
              OutlinedButton(
                onClick = { onIntent(HomeUiIntent.ImportBackupClicked) },
                modifier =
                  Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("import_tapflow_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.FileDownload,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import")
              }
            }
          }
        }
      }

      item(key = "legal_support_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(modifier = Modifier.padding(vertical = 6.dp)) {
            SettingsClickableRow(
              icon = Icons.Filled.Description,
              title = stringResource(R.string.legal_terms_title),
              subtitle = stringResource(R.string.legal_terms_row_subtitle),
              testTag = "settings_tnc_row",
              onClick = { onIntent(HomeUiIntent.SetTermsDialogVisible(true)) },
            )
            HorizontalDivider()
            SettingsClickableRow(
              icon = Icons.Filled.PrivacyTip,
              title = stringResource(R.string.legal_privacy_title),
              subtitle = stringResource(R.string.legal_privacy_row_subtitle),
              testTag = "settings_privacy_row",
              onClick = { onIntent(HomeUiIntent.SetPrivacyDialogVisible(true)) },
            )
            HorizontalDivider()
            SettingsClickableRow(
              icon = Icons.Filled.Email,
              title = stringResource(R.string.legal_feedback_title),
              subtitle = stringResource(R.string.legal_feedback_row_subtitle),
              testTag = "settings_feedback_row",
              onClick = { onIntent(HomeUiIntent.SendFeedbackEmailClicked) },
            )
          }
        }
      }

      item(key = "about_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "TapFlow v${state.appVersionName} (${state.appVersionCode})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = stringResource(R.string.legal_footer_copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsProBannerCard(
  state: HomeUiState,
  onUpgrade: () -> Unit,
  onStartTrial: () -> Unit,
  onRestore: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (state.isPro) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
        contentColor =
          if (state.isPro) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onSurface
          },
      ),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = if (state.isPro) Icons.Filled.Verified else Icons.Filled.AutoAwesome,
          contentDescription = null,
          tint =
            if (state.isPro) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.primary
            },
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              when (val ent = state.entitlementState) {
                is EntitlementState.ProLifetime -> "Pro Lifetime Active"
                is EntitlementState.ProTrial ->
                  "7-Day Trial: ${ent.remainingDays}d ${ent.remainingHours}h Left"
                EntitlementState.PendingPurchase -> "Lifetime Pro Purchase Pending"
                is EntitlementState.Free -> "Free Plan"
              },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              when (val ent = state.entitlementState) {
                is EntitlementState.ProLifetime -> "All Pro modules permanently unlocked."
                is EntitlementState.ProTrial ->
                  if (FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
                    "Enjoying Pro automation & vision during your local trial. Desktop Console & Data Backup require Lifetime Pro."
                  } else {
                    "Enjoying Pro automation & vision during your local trial. .tapflow Data Backup requires Lifetime Pro."
                  }
                EntitlementState.PendingPurchase -> "Awaiting Google Play settlement."
                is EntitlementState.Free ->
                  when (ent.trialStatus) {
                    TrialStatus.NOT_STARTED ->
                      "Start a free 7-day trial or unlock Lifetime Pro once."
                    TrialStatus.EXPIRED -> "Trial expired. Upgrade once for lifetime Pro access."
                    TrialStatus.EXPIRED_TAMPERED ->
                      "Trial unavailable due to clock change. Upgrade to Lifetime Pro."
                    TrialStatus.ACTIVE -> ""
                  }
              },
            style = MaterialTheme.typography.bodySmall,
            color =
              if (state.isPro) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (state.entitlementState is EntitlementState.Free &&
          state.entitlementState.trialStatus == TrialStatus.NOT_STARTED
        ) {
          FilledTonalButton(
            onClick = onStartTrial,
            modifier =
              Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("settings_start_trial_button"),
          ) {
            Text("Start 7-Day Trial")
          }
        }

        if (state.entitlementState !is EntitlementState.ProLifetime) {
          Button(
            onClick = onUpgrade,
            modifier =
              Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("settings_upgrade_pro_button"),
          ) {
            Text("Upgrade to Pro")
          }
        }
      }

      OutlinedButton(
        onClick = onRestore,
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("settings_restore_purchase_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Restore,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Restore Google Play Purchase")
      }
    }
  }
}

@Composable
private fun SettingsClickableRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  testTag: String,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun OverlaySafetySwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun SettingsTabPreview() {
  MaterialTheme {
    SettingsTabContent(
      state = HomeUiState(),
      onIntent = {},
    )
  }
}
