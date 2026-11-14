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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
              passed = shizuku.isInstalled,
              detail = if (shizuku.isInstalled) "Installed" else "Not installed",
            )
            DiagnosticRow(
              step = "2. Shizuku Service Running",
              passed = shizuku.isServiceRunning,
              detail =
                if (shizuku.isServiceRunning) "Binder alive (pingBinder OK)" else "Service inactive",
            )
            DiagnosticRow(
              step = "3. TapFlow Permission Granted",
              passed = shizuku.isPermissionGranted,
              detail = if (shizuku.isPermissionGranted) "Authorized" else "Permission required",
            )

            Row(
              modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (!shizuku.isInstalled) {
                OutlinedButton(
                  onClick = { onIntent(HomeUiIntent.OpenShizukuDownloadClicked) },
                  modifier =
                    Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("download_shizuku_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Get Shizuku")
                }
              }

              if (shizuku.isInstalled && !shizuku.isPermissionGranted) {
                FilledTonalButton(
                  onClick = { onIntent(HomeUiIntent.RequestShizukuPermission) },
                  modifier =
                    Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("request_shizuku_permission_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Request Permission")
                }
              }
            }
          }
        }
      }

      item(key = "shizuku_toggles_card") {
        Card(
          modifier =
            Modifier
              .fillMaxWidth()
              .alpha(if (togglesEnabled) 1f else 0.58f),
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
              subtitle = "Executes taps, swipes, and text via Shizuku shell, bypassing Accessibility touch filters.",
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
              subtitle = "Captures frames silently via shell screencap and bypasses MediaProjection prompts.",
              checked = state.preferences.shizuku.silentScreenshots,
              enabled = togglesEnabled,
              testTag = "toggle_shizuku_silent_screenshots",
              onCheckedChange = { onIntent(HomeUiIntent.ToggleShizukuSilentScreenshots(it)) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DiagnosticRow(
  step: String,
  passed: Boolean,
  detail: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
      contentDescription = null,
      tint =
        if (passed) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.error
        },
      modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = step,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
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
