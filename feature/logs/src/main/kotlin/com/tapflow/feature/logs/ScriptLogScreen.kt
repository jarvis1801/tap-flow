package com.tapflow.feature.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.log.ConditionEvalTrace
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.ExecutionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScriptLogScreen(
  onNavigateBack: () -> Unit,
  onNavigateToPaywall: (String) -> Unit,
  viewModel: ScriptLogViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var pendingExportContent by remember { mutableStateOf<String?>(null) }

  val createDocLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
      val content = pendingExportContent
      pendingExportContent = null
      if (uri != null && content != null) {
        scope.launch {
          runCatching {
            withContext(Dispatchers.IO) {
              context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
              } ?: error("Unable to open output stream")
            }
          }.onSuccess {
            viewModel.onIntent(ScriptLogUiIntent.NotifyExportResult("Exported script logs"))
          }.onFailure { err ->
            viewModel.onIntent(
              ScriptLogUiIntent.NotifyExportResult(err.message ?: "Export failed"),
            )
          }
        }
      }
    }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        ScriptLogUiEffect.NavigateBack -> {
          snackbarHostState.currentSnackbarData?.dismiss()
          onNavigateBack()
        }
        is ScriptLogUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is ScriptLogUiEffect.LaunchExportDocument -> {
          pendingExportContent = effect.content
          createDocLauncher.launch(effect.suggestedFileName)
        }
        is ScriptLogUiEffect.ShowSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(effect.message)
          }
        }
      }
    }
  }

  ScriptLogContent(
    state = state,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptLogContent(
  state: ScriptLogUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (ScriptLogUiIntent) -> Unit,
) {
  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { onIntent(ScriptLogUiIntent.BackClicked) },
            modifier = Modifier.testTag("script_log_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Navigate back",
            )
          }
        },
        title = {
          Column {
            Text(
              text = "Script Execution History",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = state.scriptTitle,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { onIntent(ScriptLogUiIntent.OpenExportSheet) },
            modifier = Modifier.testTag("export_script_logs_button"),
          ) {
            Icon(
              imageVector = if (state.isPro) Icons.Filled.Download else Icons.Filled.Lock,
              contentDescription = "Export logs",
            )
          }
          IconButton(
            onClick = { onIntent(ScriptLogUiIntent.SetClearDialogVisible(true)) },
            enabled = state.allLogs.isNotEmpty(),
            modifier = Modifier.testTag("clear_script_logs_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteSweep,
              contentDescription = "Clear all logs",
            )
          }
        },
      )
    },
  ) { innerPadding ->
    if (state.isLoading) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    } else {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        LazyColumn(
          modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item(key = "script_log_filters") {
            LogSearchAndStatusFilter(
              searchQuery = state.searchQuery,
              statusFilter = state.statusFilter,
              onQueryChange = { onIntent(ScriptLogUiIntent.UpdateSearchQuery(it)) },
              onFilterChange = { onIntent(ScriptLogUiIntent.UpdateStatusFilter(it)) },
            )
          }

          if (state.filteredLogs.isEmpty()) {
            item(key = "script_log_empty") {
              Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                  )
                  Text(
                    text =
                      if (state.allLogs.isEmpty()) {
                        "No execution sessions recorded yet."
                      } else {
                        "No execution logs match your current filters."
                      },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = "Up to 50 sessions or 7 days of history are retained automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            items(state.filteredLogs, key = { it.id }) { session ->
              val isExpanded = session.id in state.expandedSessionIds
              ScriptSessionCard(
                session = session,
                isExpanded = isExpanded,
                onToggleExpand = { onIntent(ScriptLogUiIntent.ToggleSessionExpanded(session.id)) },
                onOpenScreenshot = { onIntent(ScriptLogUiIntent.OpenScreenshotViewer(it)) },
              )
            }
          }
        }
      }
    }
  }

  if (state.showClearDialog) {
    AlertDialog(
      onDismissRequest = { onIntent(ScriptLogUiIntent.SetClearDialogVisible(false)) },
      title = { Text("Clear Execution History?") },
      text = {
        Text("This permanently deletes all recorded sessions and error screenshots for this script.")
      },
      confirmButton = {
        Button(
          onClick = { onIntent(ScriptLogUiIntent.ConfirmClearAll) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_clear_script_logs"),
        ) {
          Text("Clear All")
        }
      },
      dismissButton = {
        TextButton(onClick = { onIntent(ScriptLogUiIntent.SetClearDialogVisible(false)) }) {
          Text("Cancel")
        }
      },
    )
  }

  if (state.showExportSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { onIntent(ScriptLogUiIntent.DismissExportSheet) },
      sheetState = sheetState,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Export Execution Logs",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        LogExportFormat.entries.forEach { format ->
          ListItem(
            headlineContent = { Text(format.label, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
              Text("Exports ${state.filteredLogs.size} filtered session(s)")
            },
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onIntent(ScriptLogUiIntent.RequestExport(format)) }
                .testTag("export_format_${format.name.lowercase()}"),
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }

  state.fullscreenScreenshotPath?.let { path ->
    FullscreenScreenshotViewerDialog(
      pngPath = path,
      onDismiss = { onIntent(ScriptLogUiIntent.DismissScreenshotViewer) },
    )
  }
}

@Composable
private fun ScriptSessionCard(
  session: ExecutionSessionLog,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onOpenScreenshot: (String) -> Unit,
) {
  val borderColor =
    if (session.status == ExecutionStatus.FAILED) {
      MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
    } else {
      MaterialTheme.colorScheme.outlineVariant
    }

  Card(
    border = BorderStroke(1.dp, borderColor),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = Modifier.fillMaxWidth().testTag("script_log_session_${session.id}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ExecutionStatusBadge(status = session.status)
            Text(
              text = session.scriptTitle,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          val elapsed = formatLogDuration(session.finishedAtMs - session.startedAtMs)
          Text(
            text =
              "${formatTimestampDateTime(session.startedAtMs)} • $elapsed • ${session.totalStepsExecuted} steps",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = onToggleExpand,
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (isExpanded) "Collapse session" else "Expand session",
          )
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        ExecutionSessionDetailBody(
          session = session,
          onOpenScreenshot = onOpenScreenshot,
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ScriptLogPreview() {
  MaterialTheme {
    val sampleSession =
      ExecutionSessionLog(
        id = "sess_1",
        scriptId = "s1",
        scriptTitle = "Daily Quest Auto",
        status = ExecutionStatus.FAILED,
        startedAtMs = 1_731_530_000_000L,
        finishedAtMs = 1_731_530_012_400L,
        totalStepsExecuted = 4,
        errorReason = "Condition mismatch in If Block [Claim Reward]",
        entries =
          listOf(
            ActionLogEntry(
              id = "e1",
              stepLabel = "#01",
              actionType = "Tap",
              summary = "(540, 960) • 50ms",
              timestampMs = 1_731_530_000_500L,
              durationMs = 62L,
              status = ExecutionStatus.SUCCESS,
            ),
            ActionLogEntry(
              id = "e2",
              stepLabel = "#02",
              actionType = "IfBlock",
              summary = "Popup Check",
              timestampMs = 1_731_530_001_200L,
              durationMs = 34L,
              status = ExecutionStatus.FAILED,
              errorMessage = "Root condition evaluated false",
              conditionTrace =
                ConditionEvalTrace(
                  nodeId = "g1",
                  label = "Root (Match ALL)",
                  passed = false,
                  actualSummary = "0/1 passed",
                  expectedSummary = "ALL true",
                  children =
                    listOf(
                      ConditionEvalTrace(
                        nodeId = "l1",
                        label = "Confirm Button Color",
                        passed = false,
                        actualSummary = "#22252A",
                        expectedSummary = "#00C853 (±15)",
                      ),
                    ),
                ),
            ),
          ),
      )

    ScriptLogContent(
      state =
        ScriptLogUiState(
          scriptId = "s1",
          scriptTitle = "Daily Quest Auto",
          isLoading = false,
          isPro = true,
          allLogs = listOf(sampleSession),
          filteredLogs = listOf(sampleSession),
          expandedSessionIds = setOf("sess_1"),
        ),
      snackbarHostState = remember { SnackbarHostState() },
      onIntent = {},
    )
  }
}
