package com.tapflow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.repository.PipelineRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.service.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ExternalAutomationGuideViewModel @Inject constructor(
  private val scriptRepository: ScriptRepository,
  private val pipelineRepository: PipelineRepository,
  private val entitlementRepository: EntitlementRepository,
  private val shizukuManager: ShizukuManager,
) : ViewModel() {

  private data class LocalSelectionState(
    val targetMode: ExternalTargetMode = ExternalTargetMode.SCRIPT,
    val selectedScriptId: String? = null,
    val selectedPipelineId: String? = null,
    val activePlatformTab: ExternalGuidePlatformTab = ExternalGuidePlatformTab.MACRODROID,
  )

  private val _localState = MutableStateFlow(LocalSelectionState())
  private val _uiState = MutableStateFlow(ExternalAutomationGuideUiState())
  val uiState: StateFlow<ExternalAutomationGuideUiState> = _uiState.asStateFlow()

  private val _effects = Channel<ExternalAutomationGuideUiEffect>(Channel.BUFFERED)
  val effects: Flow<ExternalAutomationGuideUiEffect> = _effects.receiveAsFlow()

  init {
    viewModelScope.launch {
      combine(
        scriptRepository.observeScripts(),
        pipelineRepository.observePipelines(),
        entitlementRepository.entitlementState,
        shizukuManager.diagnosticState,
      ) { scripts, pipelines, entitlement, shizuku ->
        val scriptOptions = scripts.map { ExternalTargetOption(id = it.id, title = it.title) }
        val pipelineOptions = pipelines.map { ExternalTargetOption(id = it.id, title = it.title) }
        val isPro = EntitlementValidator.canUseFeature(entitlement, ProFeatureGate.EXTERNAL_BROADCAST)
        (scriptOptions to pipelineOptions) to (isPro to shizuku)
      }.combine(_localState) { (targets, statusPair), local ->
        val (scriptOptions, pipelineOptions) = targets
        val (isPro, shizuku) = statusPair
        val resolvedScriptId =
          local.selectedScriptId?.takeIf { id -> scriptOptions.any { it.id == id } }
            ?: scriptOptions.firstOrNull()?.id
        val resolvedPipelineId =
          local.selectedPipelineId?.takeIf { id -> pipelineOptions.any { it.id == id } }
            ?: pipelineOptions.firstOrNull()?.id

        ExternalAutomationGuideUiState(
          isPro = isPro,
          shizukuStatus = shizuku,
          targetMode = local.targetMode,
          scripts = scriptOptions,
          pipelines = pipelineOptions,
          selectedScriptId = resolvedScriptId,
          selectedPipelineId = resolvedPipelineId,
          activePlatformTab = local.activePlatformTab,
        )
      }.collect { computed ->
        _uiState.value = computed
      }
    }
  }

  fun onIntent(intent: ExternalAutomationGuideUiIntent) {
    when (intent) {
      ExternalAutomationGuideUiIntent.BackClicked -> {
        sendEffect(ExternalAutomationGuideUiEffect.NavigateBack)
      }
      ExternalAutomationGuideUiIntent.RefreshStatus -> {
        shizukuManager.refreshDiagnostics()
      }
      is ExternalAutomationGuideUiIntent.SelectTargetMode -> {
        _localState.update { it.copy(targetMode = intent.mode) }
      }
      is ExternalAutomationGuideUiIntent.SelectTarget -> {
        _localState.update { state ->
          when (state.targetMode) {
            ExternalTargetMode.SCRIPT -> state.copy(selectedScriptId = intent.id)
            ExternalTargetMode.PIPELINE -> state.copy(selectedPipelineId = intent.id)
          }
        }
      }
      is ExternalAutomationGuideUiIntent.SelectPlatformTab -> {
        _localState.update { it.copy(activePlatformTab = intent.tab) }
      }
      ExternalAutomationGuideUiIntent.OpenPaywallClicked -> {
        sendEffect(
          ExternalAutomationGuideUiEffect.NavigateToPaywall(
            ProFeatureGate.EXTERNAL_BROADCAST.displayName,
          ),
        )
      }
      is ExternalAutomationGuideUiIntent.CopyField -> {
        sendEffect(ExternalAutomationGuideUiEffect.CopyToClipboard(intent.label, intent.value))
        sendEffect(ExternalAutomationGuideUiEffect.ShowSnackbar("Copied ${intent.label}"))
      }
    }
  }

  private fun sendEffect(effect: ExternalAutomationGuideUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }
}
