package com.tapflow.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.service.shizuku.ShizukuDiagnosticState

@Composable
fun SeamlessTabContent(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  LifecycleResumeEffect(Unit) {
    onIntent(HomeUiIntent.RefreshShizukuStatus)
    onPauseOrDispose {}
  }

  val shizuku = state.shizukuStatus
  val togglesEnabled = state.isPro && shizuku.isFullyReady

  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      if (!state.isPro) {
        item(key = "shizuku_pro_banner") {
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Seamless Shizuku Suite (Pro)",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                  text = "Rootless shell injection, am force-stop, physical key events, and silent screenshots require TapFlow Pro.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
              FilledTonalButton(
                onClick = { onIntent(HomeUiIntent.OpenPaywallClicked) },
                modifier = Modifier.testTag("shizuku_upgrade_button"),
              ) {
                Text("Unlock")
              }
            }
          }
        }
      }

      item(key = "shizuku_checklist_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border =
            BorderStroke(
              1.dp,
              if (shizuku.isFullyReady) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.outlineVariant
              },
            ),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Bolt,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "Shizuku Environment Checklist",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
              IconButton(
                onClick = { onIntent(HomeUiIntent.RefreshShizukuStatus) },
                modifier = Modifier.size(48.dp).testTag("refresh_shizuku_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = "Refresh Shizuku status",
                )
              }
            }

            DiagnosticRow(
              step = "1. Shizuku App Installed",
              state =
                if (shizuku.isInstalled) {
                  DiagnosticStepVisualState.PASSED
                } else {
                  DiagnosticStepVisualState.ACTION_REQUIRED
                },
              statusBadge = if (shizuku.isInstalled) "Installed ✓" else "Not Installed",
              badgeTestTag = "shizuku_installed_badge",
              actionLabel = if (!shizuku.isInstalled) "Install Shizuku" else null,
              actionIcon = Icons.Filled.Download,
              actionTestTag = "install_shizuku_button",
              onActionClick = { onIntent(HomeUiIntent.OpenShizukuDownloadClicked) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DiagnosticRow(
              step = "2. Shizuku Service Running",
              state =
                when {
                  shizuku.isServiceRunning -> DiagnosticStepVisualState.PASSED
                  shizuku.isInstalled -> DiagnosticStepVisualState.ACTION_REQUIRED
                  else -> DiagnosticStepVisualState.WAITING
                },
              statusBadge =
                when {
                  shizuku.isServiceRunning -> "Running ✓"
                  shizuku.isInstalled -> "Not Running"
                  else -> "Install Shizuku first"
                },
              badgeTestTag = "shizuku_running_badge",
              actionLabel =
                if (shizuku.isInstalled && !shizuku.isServiceRunning) "Open Shizuku" else null,
              actionIcon = Icons.AutoMirrored.Filled.OpenInNew,
              actionTestTag = "open_shizuku_button",
              onActionClick = { onIntent(HomeUiIntent.OpenShizukuAppClicked) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DiagnosticRow(
              step = "3. TapFlow Permission Granted",
              state =
                when {
                  shizuku.isPermissionGranted -> DiagnosticStepVisualState.PASSED
                  shizuku.isServiceRunning -> DiagnosticStepVisualState.ACTION_REQUIRED
                  else -> DiagnosticStepVisualState.WAITING
                },
              statusBadge =
                when {
                  shizuku.isPermissionGranted -> "Granted ✓"
                  shizuku.isServiceRunning -> "Not Granted"
                  else -> "Start Shizuku service first"
                },
              badgeTestTag = "shizuku_permission_badge",
              actionLabel =
                if (shizuku.isServiceRunning && !shizuku.isPermissionGranted) {
                  "Request Permission"
                } else {
                  null
                },
              actionIcon = Icons.Filled.VerifiedUser,
              actionTestTag = "request_shizuku_permission_button",
              onActionClick = { onIntent(HomeUiIntent.RequestShizukuPermission) },
            )
          }
        }
      }

      item(key = "shizuku_toggles_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "System-Level Automation Toggles",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                if (togglesEnabled) {
                  "All toggles active and ready."
                } else if (!state.isPro) {
                  "Locked: requires active TapFlow Pro entitlement."
                } else {
                  "Disabled until all 3 Shizuku environment checks pass."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 8.dp),
            )

            ShizukuToggleRow(
              title = "Shell Input Injection",
              subtitle = "Executes taps, swipes, and text via Shizuku shell for enhanced system-level input compatibility.",
              checked = state.preferences.shizuku.shellInputInjection,
              enabled = togglesEnabled,
              testTag = "toggle_shizuku_shell_input",
              onCheckedChange = { onIntent(HomeUiIntent.ToggleShizukuShellInput(it)) },
            )
            HorizontalDivider()
            ShizukuToggleRow(
              title = "Rootless App Force-Stop",
              subtitle = "Enables ForceStopApp actions and pipeline crash recovery via am force-stop.",
              checked = state.preferences.shizuku.rootlessForceStop,
              enabled = togglesEnabled,
              testTag = "toggle_shizuku_force_stop",
              onCheckedChange = { onIntent(HomeUiIntent.ToggleShizukuForceStop(it)) },
            )
            HorizontalDivider()
            ShizukuToggleRow(
              title = "Enhanced Key Event Injection",
              subtitle = "Dispatches direct hardware keycodes through Shizuku shell.",
              checked = state.preferences.shizuku.enhancedKeyEvents,
              enabled = togglesEnabled,
              testTag = "toggle_shizuku_enhanced_keys",
              onCheckedChange = { onIntent(HomeUiIntent.ToggleShizukuEnhancedKeys(it)) },
            )
            HorizontalDivider()
            ShizukuToggleRow(
              title = "Silent Background Screenshots",
              subtitle = "Captures frames silently via shell screencap instead of MediaProjection prompts when active.",
              checked = state.preferences.shizuku.silentScreenshots,
              enabled = togglesEnabled,
              testTag = "toggle_shizuku_silent_screenshots",
              onCheckedChange = { onIntent(HomeUiIntent.ToggleShizukuSilentScreenshots(it)) },
            )
            HorizontalDivider()
            ShizukuToggleRow(
              title = "Auto-Grant Screen Capture (PROJECT_MEDIA)",
              subtitle = "Skips confirmation prompts via Shizuku AppOps and prevents Android 15+ screen-lock capture interruption (falls back safely if blocked by OEM ROM).",
              checked = state.preferences.shizuku.autoGrantProjectMedia,
              enabled = togglesEnabled,
              statusBadge = if (shizuku.isProjectMediaAllowed) "Allowed ✓" else null,
              testTag = "toggle_shizuku_auto_grant_project_media",
              onCheckedChange = { onIntent(HomeUiIntent.ToggleShizukuAutoGrantProjectMedia(it)) },
            )
            HorizontalDivider()
            ShizukuToggleRow(
              title = "MacroDroid / Tasker Broadcasts [Pro]",
              subtitle = "Trigger scripts/pipelines & receive completion broadcasts (Requires Shizuku & Pro/Trial).",
              checked = state.preferences.shizuku.externalBroadcastEnabled,
              enabled = togglesEnabled,
              testTag = "toggle_shizuku_external_broadcast",
              onCheckedChange = { onIntent(HomeUiIntent.ToggleExternalBroadcast(it)) },
            )
            HorizontalDivider()
            Surface(
              onClick = { onIntent(HomeUiIntent.OpenExternalAutomationGuideClicked) },
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(top = 6.dp)
                  .heightIn(min = 52.dp)
                  .testTag("open_external_automation_guide_button"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Code,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    text = "Setup Guide & Action Generator (MacroDroid / Tasker)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "Copy pre-filled Package, Action & Extra parameters for your scripts and pipelines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                  contentDescription = "Open Setup Guide",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}

private enum class DiagnosticStepVisualState {
  PASSED,
  ACTION_REQUIRED,
  WAITING,
}

@Composable
private fun DiagnosticRow(
  step: String,
  state: DiagnosticStepVisualState,
  statusBadge: String,
  badgeTestTag: String,
  actionLabel: String? = null,
  actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  actionTestTag: String? = null,
  onActionClick: (() -> Unit)? = null,
) {
  val icon =
    when (state) {
      DiagnosticStepVisualState.PASSED -> Icons.Filled.CheckCircle
      DiagnosticStepVisualState.ACTION_REQUIRED -> Icons.Filled.ErrorOutline
      DiagnosticStepVisualState.WAITING -> Icons.Filled.RadioButtonUnchecked
    }
  val iconTint =
    when (state) {
      DiagnosticStepVisualState.PASSED -> MaterialTheme.colorScheme.primary
      DiagnosticStepVisualState.ACTION_REQUIRED -> MaterialTheme.colorScheme.error
      DiagnosticStepVisualState.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
  val badgeContainer =
    when (state) {
      DiagnosticStepVisualState.PASSED -> MaterialTheme.colorScheme.primaryContainer
      DiagnosticStepVisualState.ACTION_REQUIRED -> MaterialTheme.colorScheme.errorContainer
      DiagnosticStepVisualState.WAITING -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
  val badgeContent =
    when (state) {
      DiagnosticStepVisualState.PASSED -> MaterialTheme.colorScheme.onPrimaryContainer
      DiagnosticStepVisualState.ACTION_REQUIRED -> MaterialTheme.colorScheme.onErrorContainer
      DiagnosticStepVisualState.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = step,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color =
          if (state == DiagnosticStepVisualState.WAITING) {
            MaterialTheme.colorScheme.onSurfaceVariant
          } else {
            MaterialTheme.colorScheme.onSurface
          },
        modifier = Modifier.weight(1f),
      )
      Surface(
        shape = MaterialTheme.shapes.small,
        color = badgeContainer,
        modifier = Modifier.testTag(badgeTestTag),
      ) {
        Text(
          text = statusBadge,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = badgeContent,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }

    if (actionLabel != null && onActionClick != null) {
      val buttonModifier =
        Modifier
          .fillMaxWidth()
          .padding(start = 30.dp)
          .heightIn(min = 48.dp)
          .let { mod -> if (actionTestTag != null) mod.testTag(actionTestTag) else mod }
      if (state == DiagnosticStepVisualState.ACTION_REQUIRED && actionIcon == Icons.Filled.Download) {
        OutlinedButton(
          onClick = onActionClick,
          modifier = buttonModifier,
        ) {
          Icon(
            imageVector = actionIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(actionLabel)
        }
      } else {
        FilledTonalButton(
          onClick = onActionClick,
          modifier = buttonModifier,
        ) {
          if (actionIcon != null) {
            Icon(
              imageVector = actionIcon,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(actionLabel)
        }
      }
    }
  }
}

@Composable
private fun ShizukuToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  enabled: Boolean,
  testTag: String,
  statusBadge: String? = null,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
          modifier = Modifier.weight(1f, fill = false),
        )
        if (!statusBadge.isNullOrBlank()) {
          Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.testTag("project_media_allowed_badge"),
          ) {
            Text(
              text = statusBadge,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
      )
    }
    Switch(
      checked = checked,
      enabled = enabled,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun SeamlessTabPreview() {
  MaterialTheme {
    SeamlessTabContent(
      state =
        HomeUiState(
          entitlementState = EntitlementState.ProLifetime("hash", 0L),
          shizukuStatus =
            ShizukuDiagnosticState(
              isInstalled = true,
              isServiceRunning = true,
              isPermissionGranted = true,
              isUserServiceBound = true,
            ),
        ),
      onIntent = {},
    )
  }
}
