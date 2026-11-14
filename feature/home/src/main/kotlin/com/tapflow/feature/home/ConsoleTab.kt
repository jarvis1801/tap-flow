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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus
import com.tapflow.core.server.ServerUiStatus

@Composable
fun ConsoleTabContent(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val server = state.serverStatus
  val isServerRunning = state.isLifetimePro && server.isRunning
  var portInput by remember(server.port) { mutableStateOf(server.port.toString()) }

  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      if (!state.isLifetimePro) {
        item(key = "console_pro_banner") {
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              ),
            modifier = Modifier.fillMaxWidth().testTag("console_lifetime_pro_banner"),
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
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Desktop Studio Bridge",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Surface(
                  shape = MaterialTheme.shapes.extraSmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f),
                ) {
                  Text(
                    text = "Lifetime Pro Required • Excluded from 7-Day Trial",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
                Text(
                  text = "Upgrade to Lifetime Pro to run the local Ktor WebSocket server for Desktop Studio.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
              FilledTonalButton(
                onClick = { onIntent(HomeUiIntent.OpenPaywallClicked) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("console_upgrade_button"),
              ) {
                Text("Unlock")
              }
            }
          }
        }
      }

      item(key = "server_status_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border =
            BorderStroke(
              1.dp,
              if (isServerRunning) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.outlineVariant
              },
            ),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                  imageVector = Icons.Filled.DesktopWindows,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "Ktor Local Server",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }

              Surface(
                shape = MaterialTheme.shapes.small,
                color =
                  if (isServerRunning) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                  },
              ) {
                Text(
                  text = if (isServerRunning) "RUNNING" else "STOPPED",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (isServerRunning) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Active Port",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = server.port.toString(),
                  style = MaterialTheme.typography.titleMedium,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Connected Clients",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    when (server.connectedClientCount) {
                      0 -> "0 Clients"
                      1 -> "1 Desktop Connected"
                      else -> "${server.connectedClientCount} Desktops Connected"
                    },
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = if (isServerRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                  contentDescription = null,
                )
                Text(
                  text =
                    when {
                      !state.isLifetimePro -> "Start WebSocket Server (Lifetime Pro)"
                      isServerRunning -> "Server Enabled"
                      else -> "Start WebSocket Server"
                    },
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.Medium,
                )
              }
              Switch(
                checked = isServerRunning,
                onCheckedChange = {
                  if (!state.isLifetimePro) {
                    onIntent(HomeUiIntent.OpenPaywallClicked)
                  } else {
                    onIntent(HomeUiIntent.ToggleLocalServer)
                  }
                },
                modifier = Modifier.testTag("console_server_switch"),
              )
            }

            if (!server.lastError.isNullOrBlank()) {
              Text(
                text = server.lastError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }

      item(key = "adb_helper_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Quick Connection Helper (ADB)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Connect USB debugging and run this command on your desktop before launching TapFlow Studio:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainerHighest,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = server.adbCommand,
                  style = MaterialTheme.typography.bodyMedium,
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = { onIntent(HomeUiIntent.CopyAdbCommandClicked) },
                  modifier = Modifier.size(48.dp).testTag("copy_adb_command_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy ADB command",
                  )
                }
              }
            }
          }
        }
      }

      item(key = "port_config_card") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Server Port Configuration",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedTextField(
                value = portInput,
                onValueChange = { next -> portInput = next.filter { it.isDigit() }.take(5) },
                label = { Text("Port (1024–65535)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("console_port_input"),
              )
              Button(
                onClick = { onIntent(HomeUiIntent.UpdateServerPort(portInput)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_console_port_button"),
              ) {
                Text("Apply")
              }
            }
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ConsoleTabLifetimePreview() {
  MaterialTheme {
    ConsoleTabContent(
      state =
        HomeUiState(
          entitlementState = EntitlementState.ProLifetime("hash", 1_700_000_000_000L),
          serverStatus =
            ServerUiStatus(
              isRunning = true,
              port = 18080,
              connectedClientCount = 1,
              adbCommand = "adb forward tcp:18080 tcp:18080",
            ),
        ),
      onIntent = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ConsoleTabTrialLockedPreview() {
  MaterialTheme {
    ConsoleTabContent(
      state =
        HomeUiState(
          entitlementState = EntitlementState.ProTrial(0L, 6, 12),
          serverStatus =
            ServerUiStatus(
              isRunning = false,
              port = 18080,
              connectedClientCount = 0,
              adbCommand = "adb forward tcp:18080 tcp:18080",
            ),
        ),
      onIntent = {},
    )
  }
}
