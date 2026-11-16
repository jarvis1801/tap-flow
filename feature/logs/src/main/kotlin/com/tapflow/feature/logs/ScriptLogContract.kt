package com.tapflow.feature.logs

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.log.ExecutionSessionLog

enum class LogStatusFilter(val label: String) {
  ALL("All"),
  SUCCESS("Success"),
  FAILED("Failed"),
  STOPPED("Stopped"),
}

enum class LogExportFormat(val label: String, val mimeType: String, val extension: String) {
  JSON("JSON (.json)", "application/json", "json"),
  TEXT("Plain Text (.txt)", "text/plain", "txt"),
}

@Immutable
data class ScriptLogUiState(
  val scriptId: String = "",
  val scriptTitle: String = "Execution History",
  val isLoading: Boolean = true,
  val isPro: Boolean = false,
  val statusFilter: LogStatusFilter = LogStatusFilter.ALL,
  val searchQuery: String = "",
  val allLogs: List<ExecutionSessionLog> = emptyList(),
  val filteredLogs: List<ExecutionSessionLog> = emptyList(),
  val expandedSessionIds: Set<String> = emptySet(),
  val showClearDialog: Boolean = false,
  val showExportSheet: Boolean = false,
  val fullscreenScreenshotPath: String? = null,
)

sealed interface ScriptLogUiIntent {
  data object BackClicked : ScriptLogUiIntent

  data class UpdateStatusFilter(val filter: LogStatusFilter) : ScriptLogUiIntent

  data class UpdateSearchQuery(val query: String) : ScriptLogUiIntent

  data class ToggleSessionExpanded(val sessionId: String) : ScriptLogUiIntent

  data class OpenScreenshotViewer(val path: String) : ScriptLogUiIntent

  data object DismissScreenshotViewer : ScriptLogUiIntent

  data class SetClearDialogVisible(val visible: Boolean) : ScriptLogUiIntent

  data object ConfirmClearAll : ScriptLogUiIntent

  data object OpenExportSheet : ScriptLogUiIntent

  data object DismissExportSheet : ScriptLogUiIntent

  data class RequestExport(val format: LogExportFormat) : ScriptLogUiIntent

  data class NotifyExportResult(val message: String) : ScriptLogUiIntent

  data object UpgradeForFullLogs : ScriptLogUiIntent
}

sealed interface ScriptLogUiEffect {
  data object NavigateBack : ScriptLogUiEffect

  data class NavigateToPaywall(val sourceFeature: String) : ScriptLogUiEffect

  data class LaunchExportDocument(
    val suggestedFileName: String,
    val mimeType: String,
    val content: String,
  ) : ScriptLogUiEffect

  data class ShowSnackbar(val message: String) : ScriptLogUiEffect
}
