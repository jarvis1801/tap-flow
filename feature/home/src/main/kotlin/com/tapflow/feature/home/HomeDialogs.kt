package com.tapflow.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapflow.core.database.backup.ImportConflictStrategy
import com.tapflow.core.service.permissions.AccessibilityProminentDisclosureDialog
import com.tapflow.core.service.permissions.RuntimePermissionsDisabledDialog

@Composable
internal fun HomeDialogs(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
) {
  state.runnerLaunchChoicePrompt?.let { prompt ->
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.DismissRunnerLaunchChoice) },
      title = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("Start Runner")
          if (prompt.target.title.isNotBlank()) {
            Text(
              text = prompt.target.title,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            onClick = { onIntent(HomeUiIntent.ConfirmRunnerLaunchChoice(autoStart = false)) },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 1.dp,
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("launch_mode_standby_option"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Filled.PauseCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                  )
                }
              }
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = "Open in Standby",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Opens floating controls paused — tap ▶ once inside your target app",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Surface(
            onClick = { onIntent(HomeUiIntent.ConfirmRunnerLaunchChoice(autoStart = true)) },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 1.dp,
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("launch_mode_autostart_option"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                  )
                }
              }
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = "Start Immediately",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Begins running actions right away",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable {
                  onIntent(HomeUiIntent.SetRunnerLaunchRememberChoice(!prompt.rememberChoice))
                }
                .padding(vertical = 4.dp)
                .testTag("launch_mode_remember_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Checkbox(
              checked = prompt.rememberChoice,
              onCheckedChange = { checked ->
                onIntent(HomeUiIntent.SetRunnerLaunchRememberChoice(checked))
              },
              modifier = Modifier.testTag("launch_mode_remember_checkbox"),
            )
            Text(
              text = "Remember my choice (can change in Settings)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(
          onClick = { onIntent(HomeUiIntent.DismissRunnerLaunchChoice) },
          modifier = Modifier.testTag("cancel_launch_mode_dialog_button"),
        ) {
          Text("Cancel")
        }
      },
    )
  }
  state.pendingProjectionTarget?.let {
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.DismissMediaProjectionConsentDialog) },
      title = { Text("Screen Capture Required") },
      text = {
        Text(
          "Full-screen capture is required to inspect screen pixels, evaluate Color/Image conditions, and capture lossless screenshots accurately across apps.",
        )
      },
      confirmButton = {
        Button(
          onClick = { onIntent(HomeUiIntent.ConfirmMediaProjectionConsentDialog) },
          modifier = Modifier.testTag("confirm_mediaprojection_dialog_button"),
        ) {
          Text("Continue")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { onIntent(HomeUiIntent.DismissMediaProjectionConsentDialog) },
          modifier = Modifier.testTag("cancel_mediaprojection_dialog_button"),
        ) {
          Text("Cancel")
        }
      },
    )
  }

  state.deleteScriptPrompt?.let { prompt ->
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.DismissDeleteScript) },
      title = { Text("Delete \"${prompt.scriptTitle}\"?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("This permanently deletes the script and any unreferenced template PNGs.")
          if (prompt.referencingPipelineTitles.isNotEmpty()) {
            Text(
              text =
                "Warning: Used by ${prompt.referencingPipelineTitles.size} pipeline(s): ${prompt.referencingPipelineTitles.joinToString(", ")}. Existing steps will appear as [Missing Script].",
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { onIntent(HomeUiIntent.ConfirmDeleteScript) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_delete_script_button"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { onIntent(HomeUiIntent.DismissDeleteScript) }) {
          Text("Cancel")
        }
      },
    )
  }

  state.deletePipelinePrompt?.let { prompt ->
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.DismissDeletePipeline) },
      title = { Text("Delete \"${prompt.pipelineTitle}\"?") },
      text = {
        Text("Removing this pipeline will not delete its individual scripts.")
      },
      confirmButton = {
        Button(
          onClick = { onIntent(HomeUiIntent.ConfirmDeletePipeline) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_delete_pipeline_button"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { onIntent(HomeUiIntent.DismissDeletePipeline) }) {
          Text("Cancel")
        }
      },
    )
  }

  state.deleteInspectGroupPrompt?.let { prompt ->
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.DismissDeleteInspectGroup) },
      title = { Text("Delete Inspect Group?") },
      text = {
        Text(
          "Delete \"${prompt.groupTitle}\" and all ${prompt.itemCount} captured item(s), including lossless PNG screenshots and crops on disk?",
        )
      },
      confirmButton = {
        Button(
          onClick = { onIntent(HomeUiIntent.ConfirmDeleteInspectGroup) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_delete_inspect_group_button"),
        ) {
          Text("Delete All")
        }
      },
      dismissButton = {
        TextButton(onClick = { onIntent(HomeUiIntent.DismissDeleteInspectGroup) }) {
          Text("Cancel")
        }
      },
    )
  }

  if (state.showClearInspectConfirmDialog) {
    val sizeText = formatInspectStorageSize(state.inspectStorageStats.totalBytes)
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.DismissClearAllInspectData) },
      title = { Text("Clear All Inspect Images?") },
      text = {
        Text(
          "This will permanently delete all Inspect screenshot and crop PNG files on disk (freeing $sizeText). Your Inspect groups, saved Pixel colors, and saved Scripts with Image Match templates will NOT be affected.",
        )
      },
      confirmButton = {
        Button(
          onClick = { onIntent(HomeUiIntent.ConfirmClearAllInspectData) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_clear_inspect_button"),
        ) {
          Text("Clear All")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { onIntent(HomeUiIntent.DismissClearAllInspectData) },
          modifier = Modifier.testTag("cancel_clear_inspect_button"),
        ) {
          Text("Cancel")
        }
      },
    )
  }

  state.importBackupPrompt?.let { prompt ->
    val preview = prompt.preview
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.DismissBackupImportPrompt) },
      title = { Text("Duplicate Titles Found") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text =
              "Backup contains ${preview.scriptsCount} script(s) and ${preview.pipelinesCount} pipeline(s).",
          )
          if (preview.duplicateScriptTitles.isNotEmpty()) {
            Text(
              text = "Matching Scripts: ${preview.duplicateScriptTitles.joinToString(", ")}",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
            )
          }
          if (preview.duplicatePipelineTitles.isNotEmpty()) {
            Text(
              text = "Matching Pipelines: ${preview.duplicatePipelineTitles.joinToString(", ")}",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
            )
          }
          Text(
            text = "Choose whether to keep duplicates as copies or overwrite matching titles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onIntent(HomeUiIntent.ConfirmBackupImport(ImportConflictStrategy.IMPORT_AS_COPY))
          },
          modifier = Modifier.testTag("import_as_copy_button"),
        ) {
          Text("Import as Copy")
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = {
            onIntent(HomeUiIntent.ConfirmBackupImport(ImportConflictStrategy.OVERWRITE))
          },
          modifier = Modifier.testTag("import_overwrite_button"),
        ) {
          Text("Overwrite")
        }
      },
    )
  }

  if (state.showTermsDialog) {
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.SetTermsDialogVisible(false)) },
      title = { Text("Terms & Conditions") },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            "TapFlow is licensed as proprietary offline automation software for personal productivity, UI testing, and accessibility workflows.",
          )
          Text(
            "You are solely responsible for ensuring that scripts and pipelines you create comply with the terms of service of any third-party applications or games you automate.",
          )
          Text(
            "TapFlow Pro lifetime purchases are managed via Google Play Billing and tied to your Google account.",
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { onIntent(HomeUiIntent.SetTermsDialogVisible(false)) }) {
          Text("Close")
        }
      },
    )
  }

  if (state.showPrivacyDialog) {
    AlertDialog(
      onDismissRequest = { onIntent(HomeUiIntent.SetPrivacyDialogVisible(false)) },
      title = { Text("Privacy Policy") },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            "TapFlow is strictly offline-only. All scripts, pipelines, execution logs, inspected screenshots, and preferences are stored locally in your device's private Room database and DataStore.",
          )
          Text(
            "Accessibility Service and MediaProjection frames are processed transiently in memory on-device and are never transmitted to external servers. The optional Desktop Studio WebSocket bridge listens strictly on localhost (127.0.0.1) for your local ADB session.",
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { onIntent(HomeUiIntent.SetPrivacyDialogVisible(false)) }) {
          Text("Close")
        }
      },
    )
  }

  if (state.showPermissionsDisabledDialog) {
    RuntimePermissionsDisabledDialog(
      accessibilityConnected = state.isAccessibilityConnected,
      overlayGranted = state.isOverlayPermissionGranted,
      onRequestAccessibility = { onIntent(HomeUiIntent.RequestAccessibilityFromPreFlight) },
      onRequestOverlay = { onIntent(HomeUiIntent.RequestOverlayFromPreFlight) },
      onContinue = { onIntent(HomeUiIntent.ContinuePendingOverlayAction) },
      onDismiss = { onIntent(HomeUiIntent.DismissPermissionsDisabledDialog) },
    )
  }

  if (state.showAccessibilityDisclosureDialog) {
    AccessibilityProminentDisclosureDialog(
      onAcceptAndContinue = { onIntent(HomeUiIntent.AcceptAccessibilityDisclosure) },
      onDecline = { onIntent(HomeUiIntent.DismissAccessibilityDisclosure) },
    )
  }
}
