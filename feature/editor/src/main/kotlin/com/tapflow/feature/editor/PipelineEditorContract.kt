package com.tapflow.feature.editor

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.pipeline.PipelineErrorStrategy
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.model.pipeline.PipelineStep
import com.tapflow.core.model.script.Script

@Immutable
data class PipelineEditorUiState(
  val pipelineId: String = "",
  val isExistingPipeline: Boolean = false,
  val isLoading: Boolean = true,
  val isSaving: Boolean = false,
  val isDirty: Boolean = false,
  val isPro: Boolean = false,
  val title: String = "New Pipeline",
  val description: String = "",
  val tags: List<String> = emptyList(),
  val suggestedTags: List<String> = emptyList(),
  val steps: List<PipelineStep> = emptyList(),
  val repeatMode: PipelineRepeatMode = PipelineRepeatMode.RunOnce,
  val errorStrategy: PipelineErrorStrategy = PipelineErrorStrategy.ABORT,
  val crashRecoveryAppPackage: String? = null,
  val availableScriptsById: Map<String, Script> = emptyMap(),
  val availableScripts: List<Script> = emptyList(),
  val showUnsavedDialog: Boolean = false,
  val showSettingsSheet: Boolean = false,
  val addStepInsertIndex: Int? = null,
  val showCrashAppPicker: Boolean = false,
  val installedApps: List<InstalledAppEntry> = emptyList(),
  val isLoadingApps: Boolean = false,
  val currentForegroundPackage: String? = null,
)

sealed interface PipelineEditorUiIntent {
  data class UpdateTitle(val title: String) : PipelineEditorUiIntent

  data class UpdateDescription(val description: String) : PipelineEditorUiIntent

  data class AddTag(val tag: String) : PipelineEditorUiIntent

  data class RemoveTag(val tag: String) : PipelineEditorUiIntent

  // Navigation & Save
  data object BackClicked : PipelineEditorUiIntent

  data object DismissUnsavedDialog : PipelineEditorUiIntent

  data object DiscardChangesClicked : PipelineEditorUiIntent

  data object SaveAndExitClicked : PipelineEditorUiIntent

  data object SavePipelineClicked : PipelineEditorUiIntent

  // Pipeline Settings Sheet
  data class SetSettingsSheetVisible(val visible: Boolean) : PipelineEditorUiIntent

  data class UpdateSettings(
    val repeatMode: PipelineRepeatMode,
    val errorStrategy: PipelineErrorStrategy,
    val crashRecoveryAppPackage: String?,
  ) : PipelineEditorUiIntent

  data object OpenCrashAppPicker : PipelineEditorUiIntent

  data class SelectCrashRecoveryApp(val packageName: String) : PipelineEditorUiIntent

  data object DismissCrashAppPicker : PipelineEditorUiIntent

  // Steps Management
  data class OpenAddScriptSheet(val insertIndex: Int) : PipelineEditorUiIntent

  data object DismissAddScriptSheet : PipelineEditorUiIntent

  data class InsertScriptStep(val scriptId: String) : PipelineEditorUiIntent

  data class UpdateStepRepeatCount(val stepId: String, val delta: Int) : PipelineEditorUiIntent

  data class MoveStepUp(val index: Int) : PipelineEditorUiIntent

  data class MoveStepDown(val index: Int) : PipelineEditorUiIntent

  data class RemoveStep(val stepId: String) : PipelineEditorUiIntent

  data class RequestProUpgrade(val featureName: String) : PipelineEditorUiIntent
}

sealed interface PipelineEditorUiEffect {
  data object NavigateBack : PipelineEditorUiEffect

  data class NavigateToPaywall(val sourceFeature: String) : PipelineEditorUiEffect

  data class ShowSnackbar(val message: String) : PipelineEditorUiEffect
}
