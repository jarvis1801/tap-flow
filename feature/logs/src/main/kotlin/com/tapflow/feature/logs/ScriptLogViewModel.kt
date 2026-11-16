package com.tapflow.feature.logs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.repository.LogRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.ExecutionStatus
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

@HiltViewModel
class ScriptLogViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val logRepository: LogRepository,
  private val scriptRepository: ScriptRepository,
  private val entitlementRepository: EntitlementRepository,
) : ViewModel() {

  private val argScriptId: String = savedStateHandle["scriptId"] ?: ""

  private val _uiState = MutableStateFlow(ScriptLogUiState(scriptId = argScriptId))
  val uiState: StateFlow<ScriptLogUiState> = _uiState.asStateFlow()

  private val _effects = Channel<ScriptLogUiEffect>(Channel.BUFFERED)
  val effects: Flow<ScriptLogUiEffect> = _effects.receiveAsFlow()

  init {
    viewModelScope.launch {
      if (argScriptId.isNotBlank()) {
        scriptRepository.getScriptById(argScriptId)?.let { script ->
          _uiState.update { it.copy(scriptTitle = script.title) }
        }
      }
    }

    viewModelScope.launch {
      entitlementRepository.entitlementState.collectLatest { ent ->
        _uiState.update { it.copy(isPro = EntitlementValidator.isPro(ent)) }
      }
    }

    viewModelScope.launch {
      logRepository.observeScriptLogs(argScriptId.ifBlank { null }).collectLatest { logs ->
        _uiState.update { state ->
          val nextExpanded =
            if (state.isLoading && state.expandedSessionIds.isEmpty() && logs.isNotEmpty()) {
              setOf(logs.first().id)
            } else {
              state.expandedSessionIds
            }
          val resolvedTitle =
            if (state.scriptTitle == "Execution History" && logs.isNotEmpty()) {
              logs.first().scriptTitle
            } else {
              state.scriptTitle
            }
          state.copy(
            isLoading = false,
            scriptTitle = resolvedTitle,
            allLogs = logs,
            filteredLogs = applyFilter(logs, state.statusFilter, state.searchQuery),
            expandedSessionIds = nextExpanded,
          )
        }
      }
    }
  }

  fun onIntent(intent: ScriptLogUiIntent) {
    when (intent) {
      ScriptLogUiIntent.BackClicked -> sendEffect(ScriptLogUiEffect.NavigateBack)

      is ScriptLogUiIntent.UpdateStatusFilter -> {
        _uiState.update { state ->
          state.copy(
            statusFilter = intent.filter,
            filteredLogs = applyFilter(state.allLogs, intent.filter, state.searchQuery),
          )
        }
      }

      is ScriptLogUiIntent.UpdateSearchQuery -> {
        _uiState.update { state ->
          state.copy(
            searchQuery = intent.query,
            filteredLogs = applyFilter(state.allLogs, state.statusFilter, intent.query),
          )
        }
      }

      is ScriptLogUiIntent.ToggleSessionExpanded -> {
        _uiState.update { state ->
          val next =
            if (intent.sessionId in state.expandedSessionIds) {
              state.expandedSessionIds - intent.sessionId
            } else {
              state.expandedSessionIds + intent.sessionId
            }
          state.copy(expandedSessionIds = next)
        }
      }

      is ScriptLogUiIntent.OpenScreenshotViewer -> {
        _uiState.update { it.copy(fullscreenScreenshotPath = intent.path) }
      }

      ScriptLogUiIntent.DismissScreenshotViewer -> {
        _uiState.update { it.copy(fullscreenScreenshotPath = null) }
      }

      is ScriptLogUiIntent.SetClearDialogVisible -> {
        _uiState.update { it.copy(showClearDialog = intent.visible) }
      }

      ScriptLogUiIntent.ConfirmClearAll -> {
        _uiState.update { it.copy(showClearDialog = false) }
        viewModelScope.launch {
          logRepository.clearScriptLogs(argScriptId.ifBlank { null })
          sendEffect(ScriptLogUiEffect.ShowSnackbar("Cleared execution history"))
        }
      }

      ScriptLogUiIntent.OpenExportSheet -> {
        if (!entitlementRepository.canUseFeature(ProFeatureGate.FULL_LOGS)) {
          sendEffect(ScriptLogUiEffect.NavigateToPaywall(ProFeatureGate.FULL_LOGS.displayName))
          return
        }
        if (_uiState.value.filteredLogs.isEmpty()) {
          sendEffect(ScriptLogUiEffect.ShowSnackbar("No matching logs to export"))
          return
        }
        _uiState.update { it.copy(showExportSheet = true) }
      }

      ScriptLogUiIntent.DismissExportSheet -> {
        _uiState.update { it.copy(showExportSheet = false) }
      }

      is ScriptLogUiIntent.RequestExport -> {
        if (!entitlementRepository.canUseFeature(ProFeatureGate.FULL_LOGS)) {
          _uiState.update { it.copy(showExportSheet = false) }
          sendEffect(ScriptLogUiEffect.NavigateToPaywall(ProFeatureGate.FULL_LOGS.displayName))
          return
        }
        val logsToExport = _uiState.value.filteredLogs
        val safeTitle =
          _uiState.value.scriptTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "script" }
        _uiState.update { it.copy(showExportSheet = false) }
        viewModelScope.launch {
          val content =
            withContext(Dispatchers.Default) {
              when (intent.format) {
                LogExportFormat.JSON -> logRepository.exportLogsAsJson(logsToExport)
                LogExportFormat.TEXT -> logRepository.exportLogsAsText(logsToExport)
              }
            }
          sendEffect(
            ScriptLogUiEffect.LaunchExportDocument(
              suggestedFileName = "tapflow_log_${safeTitle}.${intent.format.extension}",
              mimeType = intent.format.mimeType,
              content = content,
            ),
          )
        }
      }

      is ScriptLogUiIntent.NotifyExportResult -> {
        sendEffect(ScriptLogUiEffect.ShowSnackbar(intent.message))
      }

      ScriptLogUiIntent.UpgradeForFullLogs -> {
        sendEffect(ScriptLogUiEffect.NavigateToPaywall(ProFeatureGate.FULL_LOGS.displayName))
      }
    }
  }

  private fun applyFilter(
    logs: List<ExecutionSessionLog>,
    statusFilter: LogStatusFilter,
    query: String,
  ): List<ExecutionSessionLog> {
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

      session.scriptTitle.contains(cleanQuery, ignoreCase = true) ||
        session.id.contains(cleanQuery, ignoreCase = true) ||
        (session.errorReason?.contains(cleanQuery, ignoreCase = true) == true) ||
        session.entries.any { entry ->
          entry.actionType.contains(cleanQuery, ignoreCase = true) ||
            entry.summary.contains(cleanQuery, ignoreCase = true) ||
            entry.stepLabel.contains(cleanQuery, ignoreCase = true) ||
            (entry.errorMessage?.contains(cleanQuery, ignoreCase = true) == true)
        }
    }
  }

  private fun sendEffect(effect: ScriptLogUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }
}
