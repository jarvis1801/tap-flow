package com.tapflow.feature.home

import androidx.compose.runtime.Immutable
import com.tapflow.core.service.shizuku.ShizukuDiagnosticState

enum class ExternalTargetMode {
  SCRIPT,
  PIPELINE,
}

enum class ExternalGuidePlatformTab(val label: String) {
  MACRODROID("MacroDroid"),
  TASKER("Tasker"),
}

@Immutable
data class ExternalTargetOption(
  val id: String,
  val title: String,
)

@Immutable
data class ExternalAutomationGuideUiState(
  val isPro: Boolean = false,
  val shizukuStatus: ShizukuDiagnosticState = ShizukuDiagnosticState(),
  val externalBroadcastEnabled: Boolean = false,
  val targetMode: ExternalTargetMode = ExternalTargetMode.SCRIPT,
  val scripts: List<ExternalTargetOption> = emptyList(),
  val pipelines: List<ExternalTargetOption> = emptyList(),
  val selectedScriptId: String? = null,
  val selectedPipelineId: String? = null,
  val activePlatformTab: ExternalGuidePlatformTab = ExternalGuidePlatformTab.MACRODROID,
) {
  val selectedTarget: ExternalTargetOption?
    get() =
      when (targetMode) {
        ExternalTargetMode.SCRIPT ->
          scripts.firstOrNull { it.id == selectedScriptId } ?: scripts.firstOrNull()
        ExternalTargetMode.PIPELINE ->
          pipelines.firstOrNull { it.id == selectedPipelineId } ?: pipelines.firstOrNull()
      }

  val isAllPrerequisitesReady: Boolean
    get() = isPro && shizukuStatus.isFullyReady && externalBroadcastEnabled
}

sealed interface ExternalAutomationGuideUiIntent {
  data object BackClicked : ExternalAutomationGuideUiIntent

  data object RefreshStatus : ExternalAutomationGuideUiIntent

  data class SelectTargetMode(val mode: ExternalTargetMode) : ExternalAutomationGuideUiIntent

  data class SelectTarget(val id: String) : ExternalAutomationGuideUiIntent

  data class SelectPlatformTab(val tab: ExternalGuidePlatformTab) : ExternalAutomationGuideUiIntent

  data class ToggleExternalBroadcast(val enabled: Boolean) : ExternalAutomationGuideUiIntent

  data object OpenPaywallClicked : ExternalAutomationGuideUiIntent

  data class CopyField(val label: String, val value: String) : ExternalAutomationGuideUiIntent
}

sealed interface ExternalAutomationGuideUiEffect {
  data object NavigateBack : ExternalAutomationGuideUiEffect

  data class NavigateToPaywall(val sourceFeature: String) : ExternalAutomationGuideUiEffect

  data class CopyToClipboard(val label: String, val value: String) : ExternalAutomationGuideUiEffect

  data class ShowSnackbar(val message: String) : ExternalAutomationGuideUiEffect
}
