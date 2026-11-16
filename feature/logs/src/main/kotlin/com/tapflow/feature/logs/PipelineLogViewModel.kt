package com.tapflow.feature.logs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.database.repository.LogRepository
import com.tapflow.core.database.repository.PipelineRepository
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.log.ExecutionStatus
import com.tapflow.core.model.log.PipelineSessionLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

@HiltViewModel
class PipelineLogViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val logRepository: LogRepository,
  private val pipelineRepository: PipelineRepository,
  private val entitlementRepository: EntitlementRepository,
) : ViewModel() {

  private val argPipelineId: String = savedStateHandle["pipelineId"] ?: ""

  private val _uiState = MutableStateFlow(PipelineLogUiState(pipelineId = argPipelineId))
  val uiState: StateFlow<PipelineLogUiState> = _uiState.asStateFlow()

  private val _effects = Channel<PipelineLogUiEffect>(Channel.BUFFERED)
  val effects: Flow<PipelineLogUiEffect> = _effects.receiveAsFlow()

  init {
    viewModelScope.launch {
      if (argPipelineId.isNotBlank()) {
        pipelineRepository.getPipelineById(argPipelineId)?.let { pipeline ->
          _uiState.update { it.copy(pipelineTitle = pipeline.title) }
        }
      }
    }

    viewModelScope.launch {
      entitlementRepository.entitlementState.collectLatest { ent ->
        _uiState.update { it.copy(isPro = EntitlementValidator.isPro(ent)) }
      }
    }

    viewModelScope.launch {
      logRepository.observePipelineLogs(argPipelineId.ifBlank { null }).collectLatest { logs ->
        _uiState.update { state ->
          val nextExpanded =
            if (state.isLoading && state.expandedPipelineIds.isEmpty() && logs.isNotEmpty()) {
              setOf(logs.first().id)
            } else {
              state.expandedPipelineIds
            }
          val resolvedTitle =
            if (state.pipelineTitle == "Pipeline Execution History" && logs.isNotEmpty()) {
              logs.first().pipelineTitle
            } else {
              state.pipelineTitle
            }
          state.copy(
            isLoading = false,
            pipelineTitle = resolvedTitle,
            allLogs = logs,
            filteredLogs = applyFilter(logs, state.statusFilter, state.searchQuery),
            expandedPipelineIds = nextExpanded,
          )
        }
      }
    }
  }

  fun onIntent(intent: PipelineLogUiIntent) {
    when (intent) {
      PipelineLogUiIntent.BackClicked -> sendEffect(PipelineLogUiEffect.NavigateBack)

      is PipelineLogUiIntent.UpdateStatusFilter -> {
        _uiState.update { state ->
          state.copy(
            statusFilter = intent.filter,
            filteredLogs = applyFilter(state.allLogs, intent.filter, state.searchQuery),
          )
        }
      }

      is PipelineLogUiIntent.UpdateSearchQuery -> {
        _uiState.update { state ->
          state.copy(
            searchQuery = intent.query,
            filteredLogs = applyFilter(state.allLogs, state.statusFilter, intent.query),
          )
        }
      }

      is PipelineLogUiIntent.TogglePipelineExpanded -> {
        _uiState.update { state ->
          val next =
            if (intent.logId in state.expandedPipelineIds) {
              state.expandedPipelineIds - intent.logId
            } else {
              state.expandedPipelineIds + intent.logId
            }
          state.copy(expandedPipelineIds = next)
        }
      }

      is PipelineLogUiIntent.ToggleNestedRunExpanded -> {
        _uiState.update { state ->
          val next =
            if (intent.runKey in state.expandedNestedRunKeys) {
              state.expandedNestedRunKeys - intent.runKey
            } else {
              state.expandedNestedRunKeys + intent.runKey
            }
          state.copy(expandedNestedRunKeys = next)
        }
      }

      is PipelineLogUiIntent.OpenScreenshotViewer -> {
        _uiState.update { it.copy(fullscreenScreenshotPath = intent.path) }
      }

      PipelineLogUiIntent.DismissScreenshotViewer -> {
        _uiState.update { it.copy(fullscreenScreenshotPath = null) }
      }

      is PipelineLogUiIntent.SetClearDialogVisible -> {
        _uiState.update { it.copy(showClearDialog = intent.visible) }
      }

      PipelineLogUiIntent.ConfirmClearAll -> {
        _uiState.update { it.copy(showClearDialog = false) }
        viewModelScope.launch {
          logRepository.clearPipelineLogs(argPipelineId.ifBlank { null })
          sendEffect(PipelineLogUiEffect.ShowSnackbar("Cleared pipeline execution history"))
        }
      }

      PipelineLogUiIntent.OpenExportSheet -> {
        if (!entitlementRepository.canUseFeature(ProFeatureGate.FULL_LOGS)) {
          sendEffect(
            PipelineLogUiEffect.NavigateToPaywall(ProFeatureGate.FULL_LOGS.displayName),
          )
          return
        }
        if (_uiState.value.filteredLogs.isEmpty()) {
          sendEffect(PipelineLogUiEffect.ShowSnackbar("No matching pipeline logs to export"))
          return
        }
        _uiState.update { it.copy(showExportSheet = true) }
      }

      PipelineLogUiIntent.DismissExportSheet -> {
        _uiState.update { it.copy(showExportSheet = false) }
      }

      is PipelineLogUiIntent.RequestExport -> {
        if (!entitlementRepository.canUseFeature(ProFeatureGate.FULL_LOGS)) {
          _uiState.update { it.copy(showExportSheet = false) }
          sendEffect(
            PipelineLogUiEffect.NavigateToPaywall(ProFeatureGate.FULL_LOGS.displayName),
          )
          return
        }
        val logsToExport = _uiState.value.filteredLogs
        val safeTitle =
          _uiState.value.pipelineTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "pipeline" }
        _uiState.update { it.copy(showExportSheet = false) }
        viewModelScope.launch {
          val content =
            withContext(Dispatchers.Default) {
              when (intent.format) {
                LogExportFormat.JSON ->
                  TapFlowJson.instance.encodeToString(
                    ListSerializer(PipelineSessionLog.serializer()),
                    logsToExport,
                  )
                LogExportFormat.TEXT -> formatPipelineLogsAsText(logsToExport)
              }
            }
          sendEffect(
            PipelineLogUiEffect.LaunchExportDocument(
              suggestedFileName = "tapflow_pipeline_${safeTitle}.${intent.format.extension}",
              mimeType = intent.format.mimeType,
              content = content,
            ),
          )
        }
      }

      is PipelineLogUiIntent.NotifyExportResult -> {
        sendEffect(PipelineLogUiEffect.ShowSnackbar(intent.message))
      }

      PipelineLogUiIntent.UpgradeForFullLogs -> {
        sendEffect(PipelineLogUiEffect.NavigateToPaywall(ProFeatureGate.FULL_LOGS.displayName))
      }
    }
  }

  private fun applyFilter(
    logs: List<PipelineSessionLog>,
    statusFilter: LogStatusFilter,
    query: String,
  ): List<PipelineSessionLog> {
    val cleanQuery = query.trim()
    return logs.filter { session ->
      val statusMatches =
        when (statusFilter) {
          LogStatusFilter.ALL -> true
          LogStatusFilter.SUCCESS -> session.status == ExecutionStatus.SUCCESS
          LogStatusFilter.FAILED -> session.status == ExecutionStatus.FAILED
          LogStatusFilter.STOPPED -> session.status == ExecutionStatus.STOPPED
        }
      if (!statusMatches) return@filter false
      if (cleanQuery.isEmpty()) return@filter true

      session.pipelineTitle.contains(cleanQuery, ignoreCase = true) ||
        session.id.contains(cleanQuery, ignoreCase = true) ||
        (session.errorReason?.contains(cleanQuery, ignoreCase = true) == true) ||
        session.stepRuns.any { run ->
          run.scriptSessionLog.scriptTitle.contains(cleanQuery, ignoreCase = true) ||
            (run.scriptSessionLog.errorReason?.contains(cleanQuery, ignoreCase = true) == true)
        }
    }
  }

  private fun formatPipelineLogsAsText(logs: List<PipelineSessionLog>): String {
    return buildString {
      logs.forEach { pipeline ->
        appendLine("=== ${pipeline.pipelineTitle} [${pipeline.status.name}] ===")
        appendLine("Session: ${pipeline.id}")
        appendLine("Duration: ${pipeline.finishedAtMs - pipeline.startedAtMs}ms | Runs: ${pipeline.stepRuns.size}")
        pipeline.errorReason?.let { appendLine("Error: $it") }
        pipeline.stepRuns.forEach { run ->
          val s = run.scriptSessionLog
          appendLine(
            "  Step #${run.stepIndex + 1} [Run #${run.runNumber}] ${s.scriptTitle} -> ${s.status.name} (${s.totalStepsExecuted} steps)",
          )
          s.errorReason?.let { err -> appendLine("    Error: $err") }
        }
        appendLine()
      }
    }
  }

  private fun sendEffect(effect: PipelineLogUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }
}
