package com.tapflow.feature.logs

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.log.PipelineSessionLog

@Immutable
data class PipelineLogUiState(
  val pipelineId: String = "",
  val pipelineTitle: String = "Pipeline Execution History",
  val isLoading: Boolean = true,
  val isPro: Boolean = false,
  val statusFilter: LogStatusFilter = LogStatusFilter.ALL,
  val searchQuery: String = "",
  val allLogs: List<PipelineSessionLog> = emptyList(),
  val filteredLogs: List<PipelineSessionLog> = emptyList(),
  val expandedPipelineIds: Set<String> = emptySet(),
  val expandedNestedRunKeys: Set<String> = emptySet(),
  val showClearDialog: Boolean = false,
  val showExportSheet: Boolean = false,
  val fullscreenScreenshotPath: String? = null,
)

sealed interface PipelineLogUiIntent {
  data object BackClicked : PipelineLogUiIntent

  data class UpdateStatusFilter(val filter: LogStatusFilter) : PipelineLogUiIntent

  data class UpdateSearchQuery(val query: String) : PipelineLogUiIntent

  data class TogglePipelineExpanded(val logId: String) : PipelineLogUiIntent

  data class ToggleNestedRunExpanded(val runKey: String) : PipelineLogUiIntent

  data class OpenScreenshotViewer(val path: String) : PipelineLogUiIntent

  data object DismissScreenshotViewer : PipelineLogUiIntent

  data class SetClearDialogVisible(val visible: Boolean) : PipelineLogUiIntent

  data object ConfirmClearAll : PipelineLogUiIntent

  data object OpenExportSheet : PipelineLogUiIntent

  data object DismissExportSheet : PipelineLogUiIntent

  data class RequestExport(val format: LogExportFormat) : PipelineLogUiIntent

  data class NotifyExportResult(val message: String) : PipelineLogUiIntent
}

sealed interface PipelineLogUiEffect {
  data object NavigateBack : PipelineLogUiEffect

  data class NavigateToPaywall(val sourceFeature: String) : PipelineLogUiEffect

  data class LaunchExportDocument(
    val suggestedFileName: String,
    val mimeType: String,
    val content: String,
  ) : PipelineLogUiEffect

  data class ShowSnackbar(val message: String) : PipelineLogUiEffect
}
