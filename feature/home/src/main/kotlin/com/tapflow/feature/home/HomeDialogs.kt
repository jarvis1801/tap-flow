package com.tapflow.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapflow.core.database.backup.ImportConflictStrategy

@Composable
internal fun HomeDialogs(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
) {
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
}
