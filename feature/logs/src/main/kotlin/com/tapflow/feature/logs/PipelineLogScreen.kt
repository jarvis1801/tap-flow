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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.ExecutionStatus
import com.tapflow.core.model.log.PipelineSessionLog
import com.tapflow.core.model.log.PipelineStepRunLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PipelineLogScreen(
  onNavigateBack: () -> Unit,
  onNavigateToPaywall: (String) -> Unit,
  viewModel: PipelineLogViewModel = hiltViewModel(),
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
            viewModel.onIntent(PipelineLogUiIntent.NotifyExportResult("Exported pipeline logs"))
          }.onFailure { err ->
            viewModel.onIntent(
              PipelineLogUiIntent.NotifyExportResult(err.message ?: "Export failed"),
            )
          }
        }
      }
    }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        PipelineLogUiEffect.NavigateBack -> {
          snackbarHostState.currentSnackbarData?.dismiss()
          onNavigateBack()
        }
        is PipelineLogUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is PipelineLogUiEffect.LaunchExportDocument -> {
          pendingExportContent = effect.content
          createDocLauncher.launch(effect.suggestedFileName)
        }
        is PipelineLogUiEffect.ShowSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(effect.message)
          }
        }
      }
    }
  }

  PipelineLogContent(
    state = state,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineLogContent(
  state: PipelineLogUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (PipelineLogUiIntent) -> Unit,
) {
  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { onIntent(PipelineLogUiIntent.BackClicked) },
            modifier = Modifier.testTag("pipeline_log_back_button"),
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
              text = "Pipeline Execution History",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = state.pipelineTitle,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { onIntent(PipelineLogUiIntent.OpenExportSheet) },
            modifier = Modifier.testTag("export_pipeline_logs_button"),
          ) {
            Icon(
              imageVector = if (state.isPro) Icons.Filled.Download else Icons.Filled.Lock,
              contentDescription = "Export pipeline logs",
            )
          }
          IconButton(
            onClick = { onIntent(PipelineLogUiIntent.SetClearDialogVisible(true)) },
            enabled = state.allLogs.isNotEmpty(),
            modifier = Modifier.testTag("clear_pipeline_logs_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteSweep,
              contentDescription = "Clear all pipeline logs",
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
          item(key = "pipeline_log_filters") {
            LogSearchAndStatusFilter(
              searchQuery = state.searchQuery,
              statusFilter = state.statusFilter,
              onQueryChange = { onIntent(PipelineLogUiIntent.UpdateSearchQuery(it)) },
              onFilterChange = { onIntent(PipelineLogUiIntent.UpdateStatusFilter(it)) },
            )
          }

          if (state.filteredLogs.isEmpty()) {
            item(key = "pipeline_log_empty") {
              Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                  )
                  Text(
                    text =
                      if (state.allLogs.isEmpty()) {
                        "No pipeline runs recorded yet."
                      } else {
                        "No pipeline logs match your filters."
                      },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            items(state.filteredLogs, key = { it.id }) { pipelineLog ->
              val isExpanded = pipelineLog.id in state.expandedPipelineIds
              OuterPipelineLogCard(
                pipelineLog = pipelineLog,
                isPro = state.isPro,
                isExpanded = isExpanded,
                expandedNestedKeys = state.expandedNestedRunKeys,
                onToggleOuter = { onIntent(PipelineLogUiIntent.TogglePipelineExpanded(pipelineLog.id)) },
                onToggleInnerRun = { runKey ->
                  onIntent(PipelineLogUiIntent.ToggleNestedRunExpanded(runKey))
                },
                onOpenScreenshot = { path ->
                  onIntent(PipelineLogUiIntent.OpenScreenshotViewer(path))
                },
                onUpgradeClick = { onIntent(PipelineLogUiIntent.UpgradeForFullLogs) },
              )
            }
          }
        }
      }
    }
  }

  if (state.showClearDialog) {
    AlertDialog(
      onDismissRequest = { onIntent(PipelineLogUiIntent.SetClearDialogVisible(false)) },
      title = { Text("Clear Pipeline History?") },
      text = {
        Text("This permanently removes all recorded pipeline logs and nested script error snapshots.")
      },
      confirmButton = {
        Button(
          onClick = { onIntent(PipelineLogUiIntent.ConfirmClearAll) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
          Text("Clear All")
        }
      },
      dismissButton = {
        TextButton(onClick = { onIntent(PipelineLogUiIntent.SetClearDialogVisible(false)) }) {
          Text("Cancel")
        }
      },
    )
  }

  if (state.showExportSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { onIntent(PipelineLogUiIntent.DismissExportSheet) },
      sheetState = sheetState,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Export Pipeline Logs",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        LogExportFormat.entries.forEach { format ->
          ListItem(
            headlineContent = { Text(format.label, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
              Text("Exports ${state.filteredLogs.size} pipeline session(s)")
            },
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onIntent(PipelineLogUiIntent.RequestExport(format)) },
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }

  state.fullscreenScreenshotPath?.let { path ->
    FullscreenScreenshotViewerDialog(
      pngPath = path,
      onDismiss = { onIntent(PipelineLogUiIntent.DismissScreenshotViewer) },
    )
  }
}

@Composable
private fun OuterPipelineLogCard(
  pipelineLog: PipelineSessionLog,
  isPro: Boolean,
  isExpanded: Boolean,
  expandedNestedKeys: Set<String>,
  onToggleOuter: () -> Unit,
  onToggleInnerRun: (String) -> Unit,
  onOpenScreenshot: (String) -> Unit,
  onUpgradeClick: () -> Unit,
) {
  val borderColor =
    if (pipelineLog.status == ExecutionStatus.FAILED) {
      MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
    } else {
      MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

  Card(
    border = BorderStroke(1.2.dp, borderColor),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().testTag("pipeline_log_card_${pipelineLog.id}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggleOuter() },
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
            ExecutionStatusBadge(status = pipelineLog.status)
            Text(
              text = pipelineLog.pipelineTitle,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          val elapsed = formatLogDuration(pipelineLog.finishedAtMs - pipelineLog.startedAtMs)
          Text(
            text =
              "${formatTimestampDateTime(pipelineLog.startedAtMs)} • $elapsed • ${pipelineLog.stepRuns.size} script runs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (isPro && !pipelineLog.errorReason.isNullOrBlank()) {
            Text(
              text = "Pipeline Error: ${pipelineLog.errorReason}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Medium,
            )
          }
        }

        IconButton(
          onClick = onToggleOuter,
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (isExpanded) "Collapse pipeline log" else "Expand pipeline log",
          )
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        if (!isPro) {
          LockedFullLogsDetailCard(
            detailsSummary =
              "${pipelineLog.stepRuns.size} nested script run(s)" +
                if (!pipelineLog.errorReason.isNullOrBlank()) " • Crash diagnostics recorded" else "",
            onUpgradeClick = onUpgradeClick,
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider()
            if (pipelineLog.stepRuns.isEmpty()) {
              Text(
                text = "No nested script runs executed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              pipelineLog.stepRuns.forEachIndexed { idx, run ->
                val runKey = "${pipelineLog.id}_${run.stepIndex}_${run.runNumber}_$idx"
                InnerNestedScriptRunCard(
                  run = run,
                  isExpanded = runKey in expandedNestedKeys,
                  onToggle = { onToggleInnerRun(runKey) },
                  onOpenScreenshot = onOpenScreenshot,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InnerNestedScriptRunCard(
  run: PipelineStepRunLog,
  isExpanded: Boolean,
  onToggle: () -> Unit,
  onOpenScreenshot: (String) -> Unit,
) {
  val session = run.scriptSessionLog
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Surface(
              shape = MaterialTheme.shapes.extraSmall,
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = "Run #${run.runNumber}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
            Surface(
              shape = MaterialTheme.shapes.extraSmall,
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                text = "Step #${run.stepIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
            ExecutionStatusBadge(status = session.status)
          }

          Text(
            text = session.scriptTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          val duration = formatLogDuration(session.finishedAtMs - session.startedAtMs)
          Text(
            text = "${session.totalStepsExecuted} actions • $duration",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = onToggle,
          modifier = Modifier.size(44.dp),
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = "Expand nested script run",
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
private fun PipelineLogPreview() {
  MaterialTheme {
    val run1 =
      PipelineStepRunLog(
        stepIndex = 0,
        runNumber = 1,
        scriptSessionLog =
          ExecutionSessionLog(
            id = "r1",
            scriptId = "s1",
            scriptTitle = "Login & Collect",
            status = ExecutionStatus.SUCCESS,
            startedAtMs = 1_731_530_000_000L,
            finishedAtMs = 1_731_530_004_200L,
            totalStepsExecuted = 3,
            entries =
              listOf(
                ActionLogEntry(
                  id = "e1",
                  stepLabel = "#01",
                  actionType = "Tap",
                  summary = "(540, 1200)",
                  timestampMs = 1_731_530_000_300L,
                  durationMs = 55L,
                  status = ExecutionStatus.SUCCESS,
                ),
              ),
          ),
      )
    val run2 =
      PipelineStepRunLog(
        stepIndex = 1,
        runNumber = 2,
        scriptSessionLog =
          ExecutionSessionLog(
            id = "r2",
            scriptId = "s2",
            scriptTitle = "Dungeon Farm",
            status = ExecutionStatus.FAILED,
            startedAtMs = 1_731_530_004_500L,
            finishedAtMs = 1_731_530_011_000L,
            totalStepsExecuted = 2,
            errorReason = "Timeout waiting for Boss HP bar",
          ),
      )
    val sample =
      PipelineSessionLog(
        id = "plog1",
        pipelineId = "p1",
        pipelineTitle = "Morning Routine",
        status = ExecutionStatus.FAILED,
        startedAtMs = 1_731_530_000_000L,
        finishedAtMs = 1_731_530_011_000L,
        stepRuns = listOf(run1, run2),
        errorReason = "Step #2 Run #2 failed (Skip & Continue captured snapshot)",
      )

    PipelineLogContent(
      state =
        PipelineLogUiState(
          pipelineId = "p1",
          pipelineTitle = "Morning Routine",
          isLoading = false,
          isPro = true,
          allLogs = listOf(sample),
          filteredLogs = listOf(sample),
          expandedPipelineIds = setOf("plog1"),
        ),
      snackbarHostState = remember { SnackbarHostState() },
      onIntent = {},
    )
  }
}
