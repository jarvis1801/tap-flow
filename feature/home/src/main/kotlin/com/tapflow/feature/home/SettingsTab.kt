package com.tapflow.feature.home

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.ThemePaletteOption
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus

@Composable
fun SettingsTabContent(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentLocales = AppCompatDelegate.getApplicationLocales().toLanguageTags()

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
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              ThemePaletteOption.entries.forEach { palette ->
                FilterChip(
                  selected = state.preferences.themePalette == palette,
                  onClick = { onIntent(HomeUiIntent.SetThemePalette(palette)) },
                  label = {
                    Text(
                      when (palette) {
                        ThemePaletteOption.DYNAMIC_MATERIAL_YOU -> "Material You"
                        ThemePaletteOption.CYAN_PRECISION -> "Cyan Precision"
                        ThemePaletteOption.COBALT_TACTICAL -> "Cobalt Tactical"
                        ThemePaletteOption.AMBER_INDUSTRIAL -> "Amber Industrial"
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
                val isSelected =
                  if (currentLocales.isBlank()) {
                    localeOption == AppLocaleOption.ENGLISH
                  } else {
                    currentLocales.startsWith(localeOption.languageTag, ignoreCase = true)
                  }
                FilterChip(
                  selected = isSelected,
                  onClick = { onIntent(HomeUiIntent.SelectLocale(localeOption)) },
                  label = { Text(localeOption.label) },
                  modifier = Modifier.testTag("locale_${localeOption.languageTag.lowercase()}"),
                )
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
              title = "Terms & Conditions",
              subtitle = "Commercial license & automation responsibility",
              testTag = "settings_tnc_row",
              onClick = { onIntent(HomeUiIntent.SetTermsDialogVisible(true)) },
            )
            HorizontalDivider()
            SettingsClickableRow(
              icon = Icons.Filled.PrivacyTip,
              title = "Privacy Policy",
              subtitle = "100% offline storage — zero telemetry or cloud upload",
              testTag = "settings_privacy_row",
              onClick = { onIntent(HomeUiIntent.SetPrivacyDialogVisible(true)) },
            )
            HorizontalDivider()
            SettingsClickableRow(
              icon = Icons.Filled.Email,
              title = "Feedback via Email",
              subtitle = "Opens email client with system & Shizuku diagnostics",
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
                text = "Copyright © TapFlow. All Rights Reserved (Proprietary Software).",
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
                  "Enjoying Pro automation & vision during your local trial. Desktop Console & Data Backup require Lifetime Pro."
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
