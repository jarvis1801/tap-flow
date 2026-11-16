package com.tapflow.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.repository.PipelineRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.pipeline.PipelineErrorStrategy
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.model.pipeline.PipelineStep
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PipelineEditorViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val pipelineRepository: PipelineRepository,
  private val scriptRepository: ScriptRepository,
  private val entitlementRepository: EntitlementRepository,
  private val accessibilityManagerBridge: AccessibilityManagerBridge,
  private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

  private val argPipelineId: String? = savedStateHandle["pipelineId"]

  private val _uiState = MutableStateFlow(PipelineEditorUiState())
  val uiState: StateFlow<PipelineEditorUiState> = _uiState.asStateFlow()

  private val _effects = Channel<PipelineEditorUiEffect>(Channel.BUFFERED)
  val effects: Flow<PipelineEditorUiEffect> = _effects.receiveAsFlow()

  init {
    viewModelScope.launch {
      val loaded = argPipelineId?.let { pipelineRepository.getPipelineById(it) }
      if (loaded != null) {
        _uiState.update {
          it.copy(
            pipelineId = loaded.id,
            isExistingPipeline = true,
            isLoading = false,
            isDirty = false,
            title = loaded.title,
            description = loaded.description,
            tags = loaded.tags,
            steps = loaded.steps,
            repeatMode = loaded.repeatMode,
            errorStrategy = loaded.errorStrategy,
            crashRecoveryAppPackage = loaded.crashRecoveryAppPackage,
          )
        }
      } else {
        _uiState.update {
          it.copy(
            pipelineId = argPipelineId ?: UUID.randomUUID().toString(),
            isExistingPipeline = false,
            isLoading = false,
            isDirty = false,
          )
        }
      }
    }

    viewModelScope.launch {
      scriptRepository.observeScripts().collectLatest { scripts ->
        _uiState.update {
          it.copy(
            availableScripts = scripts,
            availableScriptsById = scripts.associateBy { s -> s.id },
          )
        }
      }
    }

    viewModelScope.launch {
      entitlementRepository.entitlementState.collectLatest { ent ->
        _uiState.update { it.copy(isPro = EntitlementValidator.isPro(ent)) }
      }
    }

    viewModelScope.launch {
      accessibilityManagerBridge.currentForegroundPackage.collectLatest { pkg ->
        _uiState.update { it.copy(currentForegroundPackage = pkg) }
      }
    }
  }

  fun onIntent(intent: PipelineEditorUiIntent) {
    when (intent) {
      is PipelineEditorUiIntent.UpdateTitle -> {
        _uiState.update { it.copy(title = intent.title, isDirty = true) }
      }
      is PipelineEditorUiIntent.UpdateDescription -> {
        _uiState.update { it.copy(description = intent.description, isDirty = true) }
      }
      is PipelineEditorUiIntent.AddTag -> {
        val clean = intent.tag.trim()
        if (clean.isBlank()) return
        _uiState.update {
          if (clean in it.tags) it else it.copy(tags = it.tags + clean, isDirty = true)
        }
      }
      is PipelineEditorUiIntent.RemoveTag -> {
        _uiState.update { it.copy(tags = it.tags - intent.tag, isDirty = true) }
      }

      PipelineEditorUiIntent.BackClicked -> {
        if (_uiState.value.isDirty) {
          _uiState.update { it.copy(showUnsavedDialog = true) }
        } else {
          sendEffect(PipelineEditorUiEffect.NavigateBack)
        }
      }
      PipelineEditorUiIntent.DismissUnsavedDialog -> {
        _uiState.update { it.copy(showUnsavedDialog = false) }
      }
      PipelineEditorUiIntent.DiscardChangesClicked -> {
        _uiState.update { it.copy(showUnsavedDialog = false) }
        sendEffect(PipelineEditorUiEffect.NavigateBack)
      }
      PipelineEditorUiIntent.SaveAndExitClicked -> {
        _uiState.update { it.copy(showUnsavedDialog = false) }
        persistPipeline()
      }
      PipelineEditorUiIntent.SavePipelineClicked -> {
        persistPipeline()
      }

      is PipelineEditorUiIntent.SetSettingsSheetVisible -> {
        _uiState.update { it.copy(showSettingsSheet = intent.visible) }
      }
      is PipelineEditorUiIntent.UpdateSettings -> {
        val usesProAutomation =
          intent.repeatMode is PipelineRepeatMode.Unlimited ||
            (intent.repeatMode is PipelineRepeatMode.Count && intent.repeatMode.iterations > 1) ||
            intent.errorStrategy == PipelineErrorStrategy.SKIP_AND_CONTINUE
        if (usesProAutomation &&
          !entitlementRepository.canUseFeature(ProFeatureGate.PIPELINE_AUTOMATION)
        ) {
          sendEffect(
            PipelineEditorUiEffect.NavigateToPaywall(
              ProFeatureGate.PIPELINE_AUTOMATION.displayName,
            ),
          )
          return
        }
        _uiState.update {
          it.copy(
            repeatMode = intent.repeatMode,
            errorStrategy = intent.errorStrategy,
            crashRecoveryAppPackage = intent.crashRecoveryAppPackage,
            showSettingsSheet = false,
            isDirty = true,
          )
        }
      }
      PipelineEditorUiIntent.OpenCrashAppPicker -> {
        _uiState.update {
          it.copy(
            showCrashAppPicker = true,
            isLoadingApps = it.installedApps.isEmpty(),
          )
        }
        if (_uiState.value.installedApps.isEmpty()) {
          viewModelScope.launch {
            val apps = installedAppsProvider.loadLaunchableApps()
            _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
          }
        }
      }
      is PipelineEditorUiIntent.SelectCrashRecoveryApp -> {
        _uiState.update {
          it.copy(
            crashRecoveryAppPackage = intent.packageName,
            showCrashAppPicker = false,
            isDirty = true,
          )
        }
      }
      PipelineEditorUiIntent.DismissCrashAppPicker -> {
        _uiState.update { it.copy(showCrashAppPicker = false) }
      }

      is PipelineEditorUiIntent.OpenAddScriptSheet -> {
        _uiState.update { it.copy(addStepInsertIndex = intent.insertIndex) }
      }
      PipelineEditorUiIntent.DismissAddScriptSheet -> {
        _uiState.update { it.copy(addStepInsertIndex = null) }
      }
      is PipelineEditorUiIntent.InsertScriptStep -> {
        val insertIdx = _uiState.value.addStepInsertIndex ?: _uiState.value.steps.size
        val step =
          PipelineStep(
            id = UUID.randomUUID().toString(),
            scriptId = intent.scriptId,
            repeatCount = 1,
          )
        _uiState.update { state ->
          val mutable = state.steps.toMutableList()
          mutable.add(insertIdx.coerceIn(0, mutable.size), step)
          state.copy(
            steps = mutable,
            addStepInsertIndex = null,
            isDirty = true,
          )
        }
      }
      is PipelineEditorUiIntent.UpdateStepRepeatCount -> {
        _uiState.update { state ->
          state.copy(
            steps =
              state.steps.map { step ->
                if (step.id == intent.stepId) {
                  step.copy(repeatCount = (step.repeatCount + intent.delta).coerceIn(1, 999))
                } else {
                  step
                }
              },
            isDirty = true,
          )
        }
      }
      is PipelineEditorUiIntent.MoveStepUp -> {
        _uiState.update { state ->
          val idx = intent.index
          if (idx <= 0 || idx >= state.steps.size) return@update state
          val mutable = state.steps.toMutableList()
          val tmp = mutable[idx]
          mutable[idx] = mutable[idx - 1]
          mutable[idx - 1] = tmp
          state.copy(steps = mutable, isDirty = true)
        }
      }
      is PipelineEditorUiIntent.MoveStepDown -> {
        _uiState.update { state ->
          val idx = intent.index
          if (idx < 0 || idx >= state.steps.lastIndex) return@update state
          val mutable = state.steps.toMutableList()
          val tmp = mutable[idx]
          mutable[idx] = mutable[idx + 1]
          mutable[idx + 1] = tmp
          state.copy(steps = mutable, isDirty = true)
        }
      }
      is PipelineEditorUiIntent.RemoveStep -> {
        _uiState.update { state ->
          state.copy(
            steps = state.steps.filterNot { it.id == intent.stepId },
            isDirty = true,
          )
        }
      }
      is PipelineEditorUiIntent.RequestProUpgrade -> {
        sendEffect(PipelineEditorUiEffect.NavigateToPaywall(intent.featureName))
      }
    }
  }

  private fun persistPipeline() {
    val state = _uiState.value
    if (state.isSaving) return
    val cleanTitle = state.title.trim()
    if (cleanTitle.isBlank()) {
      sendEffect(PipelineEditorUiEffect.ShowSnackbar("Pipeline title cannot be empty"))
      return
    }
    _uiState.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val existing = if (state.isExistingPipeline) pipelineRepository.getPipelineById(state.pipelineId) else null
      val toSave =
        Pipeline(
          id = state.pipelineId.ifBlank { UUID.randomUUID().toString() },
          title = cleanTitle,
          description = state.description.trim(),
          tags = state.tags,
          steps = state.steps,
          repeatMode = state.repeatMode,
          errorStrategy = state.errorStrategy,
          crashRecoveryAppPackage = state.crashRecoveryAppPackage?.ifBlank { null },
          createdAtMs = existing?.createdAtMs ?: now,
          updatedAtMs = now,
        )
      pipelineRepository.savePipeline(toSave)
      _uiState.update { it.copy(isSaving = false, isDirty = false, isExistingPipeline = true) }
      sendEffect(PipelineEditorUiEffect.NavigateBack)
    }
  }

  private fun sendEffect(effect: PipelineEditorUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }
}
