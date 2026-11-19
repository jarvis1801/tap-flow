package com.tapflow.feature.home

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.BillingRestoreResult
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.backup.ImportConflictStrategy
import com.tapflow.core.database.backup.TapFlowBackupManager
import com.tapflow.core.database.repository.InspectGroupWithItems
import com.tapflow.core.database.repository.InspectRepository
import com.tapflow.core.database.repository.InspectStorageStats
import com.tapflow.core.database.repository.PipelineRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.datastore.AppPreferences
import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.datastore.ThemePaletteOption
import com.tapflow.core.model.common.FeatureFlags
import com.tapflow.core.model.common.RunnerLaunchMode
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.entitlement.TrialStatus
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.ScreenOrientation
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.model.script.Script
import com.tapflow.core.server.ServerUiStatus
import com.tapflow.core.server.TapFlowLocalServer
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.overlay.OverlayStateBridge
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.projection.ScreenCaptureState
import com.tapflow.core.service.shizuku.ShizukuDiagnosticState
import com.tapflow.core.service.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
class HomeViewModel @Inject constructor(
  private val savedStateHandle: SavedStateHandle,
  private val scriptRepository: ScriptRepository,
  private val pipelineRepository: PipelineRepository,
  private val inspectRepository: InspectRepository,
  private val preferencesRepository: TapFlowPreferencesRepository,
  private val entitlementRepository: EntitlementRepository,
  private val localServer: TapFlowLocalServer,
  private val shizukuManager: ShizukuManager,
  private val accessibilityManagerBridge: AccessibilityManagerBridge,
  private val mediaProjectionBridge: MediaProjectionBridge,
  private val overlayStateBridge: OverlayStateBridge,
  private val backupManager: TapFlowBackupManager,
  private val scriptAssetManager: ScriptAssetManager,
  private val inspectImageImporter: InspectImageImporter,
) : ViewModel() {

  companion object {
    internal const val KEY_ACTIVE_HOME_TAB = "active_home_tab"
  }

  private data class LocalTransientState(
    val activeTab: HomeTabType = HomeTabType.SCRIPT,
    val searchQuery: String = "",
    val isSearchExpanded: Boolean = false,
    val selectedScriptTags: Set<String> = emptySet(),
    val selectedPipelineTags: Set<String> = emptySet(),
    val inspectStorageStats: InspectStorageStats = InspectStorageStats(),
    val inspectImageRefreshToken: Long = 0L,
    val expandedInspectGroupIds: Set<String> = emptySet(),
    val expandedInspectItemIds: Set<String> = emptySet(),
    val isScriptFabExpanded: Boolean = false,
    val isInspectFabExpanded: Boolean = false,
    val runnerLaunchChoicePrompt: RunnerLaunchChoicePrompt? = null,
    val pendingProjectionTarget: PendingMediaProjectionTarget? = null,
    val deleteScriptPrompt: DeleteScriptPrompt? = null,
    val deletePipelinePrompt: DeletePipelinePrompt? = null,
    val deleteInspectGroupPrompt: DeleteInspectGroupPrompt? = null,
    val showClearInspectConfirmDialog: Boolean = false,
    val isClearingInspectData: Boolean = false,
    val importBackupPrompt: ImportBackupPrompt? = null,
    val showTermsDialog: Boolean = false,
    val showPrivacyDialog: Boolean = false,
    val isAccessibilityConnected: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val showPermissionsDisabledDialog: Boolean = false,
    val showAccessibilityDisclosureDialog: Boolean = false,
    val pendingHomeOverlayAction: PendingHomeOverlayAction? = null,
    val appVersionName: String = "1.0.0",
    val appVersionCode: Long = 1L,
  )

  private data class DataLayerSnapshot(
    val scripts: List<Script>,
    val pipelines: List<Pipeline>,
    val inspectGroups: List<InspectGroupWithItems>,
    val entitlementState: EntitlementState,
    val localizedProPrice: String?,
  )

  private data class SystemLayerSnapshot(
    val preferences: AppPreferences,
    val serverStatus: ServerUiStatus,
    val shizukuStatus: ShizukuDiagnosticState,
  )

  private val _localState =
    MutableStateFlow(
      LocalTransientState(
        activeTab = readSavedActiveTab() ?: HomeTabType.SCRIPT,
      ),
    )
  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private val _effects = Channel<HomeUiEffect>(Channel.BUFFERED)
  val effects: Flow<HomeUiEffect> = _effects.receiveAsFlow()

  init {
    val dataFlow =
      combine(
        scriptRepository.observeScripts(),
        pipelineRepository.observePipelines(),
        inspectRepository.observeGroupsWithItems(),
        entitlementRepository.entitlementState,
        entitlementRepository.localizedProPrice,
      ) { scripts, pipelines, inspectGroups, entitlement, price ->
        DataLayerSnapshot(
          scripts = scripts,
          pipelines = pipelines,
          inspectGroups = inspectGroups,
          entitlementState = entitlement,
          localizedProPrice = price,
        )
      }

    viewModelScope.launch {
      accessibilityManagerBridge.isAccessibilityConnected.collect { connected ->
        _localState.update { it.copy(isAccessibilityConnected = connected) }
      }
    }

    viewModelScope.launch {
      accessibilityManagerBridge.isAccessibilityConnected.collect { connected ->
        _localState.update { it.copy(isAccessibilityConnected = connected) }
      }
    }

    viewModelScope.launch {
      inspectRepository.observeGroupsWithItems().collect {
        refreshInspectStorageStats()
      }
    }

    val systemFlow =
      combine(
        preferencesRepository.preferencesFlow,
        localServer.status,
        shizukuManager.diagnosticState,
      ) { prefs, serverStatus, shizukuStatus ->
        SystemLayerSnapshot(
          preferences = prefs,
          serverStatus = serverStatus,
          shizukuStatus = shizukuStatus,
        )
      }

    viewModelScope.launch {
      combine(dataFlow, systemFlow, _localState) { data, system, local ->
        val query = local.searchQuery.trim()
        val scriptsById = data.scripts.associateBy { it.id }

        val availableScriptTags =
          HomeLibraryFilter.extractAvailableTags(data.scripts.map { it.tags })
        val prunedScriptTags =
          HomeLibraryFilter.pruneSelectedTags(local.selectedScriptTags, availableScriptTags)

        val availablePipelineTags =
          HomeLibraryFilter.extractAvailableTags(data.pipelines.map { it.tags })
        val prunedPipelineTags =
          HomeLibraryFilter.pruneSelectedTags(local.selectedPipelineTags, availablePipelineTags)

        val filteredScripts =
          data.scripts
            .filter { script ->
              HomeLibraryFilter.matchesSearchAndTags(
                title = script.title,
                tags = script.tags,
                query = query,
                selectedTags = prunedScriptTags,
              )
            }
            .map { script ->
              ScriptCardUi(
                script = script,
                requiresVision = script.requiresVision(),
                proBlockers =
                  if (EntitlementValidator.isPro(data.entitlementState)) {
                    emptyList()
                  } else {
                    EntitlementValidator.detectProFeaturesInScript(script).map { it.displayName }
                  },
              )
            }

        val filteredPipelines =
          data.pipelines
            .filter { pipeline ->
              HomeLibraryFilter.matchesSearchAndTags(
                title = pipeline.title,
                tags = pipeline.tags,
                query = query,
                selectedTags = prunedPipelineTags,
              )
            }
            .map { pipeline ->
              val missingCount = pipeline.steps.count { it.scriptId !in scriptsById }
              val stepRuns = pipeline.steps.sumOf { it.repeatCount.coerceAtLeast(1) }
              val totalRuns =
                when (val repeat = pipeline.repeatMode) {
                  PipelineRepeatMode.RunOnce -> stepRuns
                  is PipelineRepeatMode.Count -> stepRuns * repeat.iterations.coerceAtLeast(1)
                  PipelineRepeatMode.Unlimited -> stepRuns
                }
              val needsVision =
                pipeline.steps.any { step -> scriptsById[step.scriptId]?.requiresVision() == true }
              PipelineCardUi(
                pipeline = pipeline,
                totalRunsCount = totalRuns,
                missingScriptCount = missingCount,
                requiresVision = needsVision,
                proBlockers =
                  if (EntitlementValidator.isPro(data.entitlementState)) {
                    emptyList()
                  } else {
                    EntitlementValidator.detectProFeaturesInPipeline(pipeline, scriptsById)
                      .map { it.displayName }
                  },
              )
            }

        val filteredInspectGroups =
          if (query.isEmpty()) {
            data.inspectGroups
          } else {
            data.inspectGroups.mapNotNull { entry ->
              val groupMatches =
                entry.group.title.contains(query, ignoreCase = true) ||
                  entry.group.tags.any { it.contains(query, ignoreCase = true) }
              if (groupMatches) {
                entry
              } else {
                val matchingItems =
                  entry.items.filter { item ->
                    item.appName.contains(query, ignoreCase = true) ||
                      item.packageName.contains(query, ignoreCase = true) ||
                      matchesInspectOrientationQuery(item, query)
                  }
                if (matchingItems.isNotEmpty()) entry.copy(items = matchingItems) else null
              }
            }
          }

        HomeUiState(
          activeTab = local.activeTab,
          searchQuery = local.searchQuery,
          isSearchExpanded = local.isSearchExpanded,
          entitlementState = data.entitlementState,
          localizedProPrice = data.localizedProPrice,
          preferences = system.preferences,
          scripts = filteredScripts,
          totalScriptsCount = data.scripts.size,
          availableScriptTags = availableScriptTags,
          selectedScriptTags = prunedScriptTags,
          pipelines = filteredPipelines,
          totalPipelinesCount = data.pipelines.size,
          availablePipelineTags = availablePipelineTags,
          selectedPipelineTags = prunedPipelineTags,
          inspectGroups = filteredInspectGroups,
          inspectStorageStats = local.inspectStorageStats,
          inspectImageRefreshToken = local.inspectImageRefreshToken,
          expandedInspectGroupIds = local.expandedInspectGroupIds,
          expandedInspectItemIds = local.expandedInspectItemIds,
          serverStatus = system.serverStatus,
          shizukuStatus = system.shizukuStatus,
          isScriptFabExpanded = local.isScriptFabExpanded,
          isInspectFabExpanded = local.isInspectFabExpanded,
          runnerLaunchChoicePrompt = local.runnerLaunchChoicePrompt,
          pendingProjectionTarget = local.pendingProjectionTarget,
          deleteScriptPrompt = local.deleteScriptPrompt,
          deletePipelinePrompt = local.deletePipelinePrompt,
          deleteInspectGroupPrompt = local.deleteInspectGroupPrompt,
          showClearInspectConfirmDialog = local.showClearInspectConfirmDialog,
          isClearingInspectData = local.isClearingInspectData,
          importBackupPrompt = local.importBackupPrompt,
          showTermsDialog = local.showTermsDialog,
          showPrivacyDialog = local.showPrivacyDialog,
          isAccessibilityConnected = local.isAccessibilityConnected,
          isOverlayPermissionGranted = local.isOverlayPermissionGranted,
          showPermissionsDisabledDialog = local.showPermissionsDisabledDialog,
          showAccessibilityDisclosureDialog = local.showAccessibilityDisclosureDialog,
          pendingHomeOverlayAction = local.pendingHomeOverlayAction,
          appVersionName = local.appVersionName,
          appVersionCode = local.appVersionCode,
        )
      }.collect { computed ->
        _uiState.value = computed
      }
    }

    viewModelScope.launch {
      scriptRepository.observeScripts().collect { scripts ->
        val available = HomeLibraryFilter.extractAvailableTags(scripts.map { it.tags })
        _localState.update { state ->
          val pruned = HomeLibraryFilter.pruneSelectedTags(state.selectedScriptTags, available)
          if (pruned == state.selectedScriptTags) state else state.copy(selectedScriptTags = pruned)
        }
      }
    }

    viewModelScope.launch {
      pipelineRepository.observePipelines().collect { pipelines ->
        val available = HomeLibraryFilter.extractAvailableTags(pipelines.map { it.tags })
        _localState.update { state ->
          val pruned = HomeLibraryFilter.pruneSelectedTags(state.selectedPipelineTags, available)
          if (pruned == state.selectedPipelineTags) {
            state
          } else {
            state.copy(selectedPipelineTags = pruned)
          }
        }
      }
    }
  }

  fun onIntent(intent: HomeUiIntent) {
    when (intent) {
      is HomeUiIntent.InitializeTabIfUnset -> {
        if (!savedStateHandle.contains(KEY_ACTIVE_HOME_TAB)) {
          val safeTab = HomeTabType.sanitizeTab(intent.tab)
          savedStateHandle[KEY_ACTIVE_HOME_TAB] = safeTab.name
          _localState.update { it.copy(activeTab = safeTab) }
          if (safeTab == HomeTabType.SEAMLESS) {
            shizukuManager.refreshDiagnostics()
            viewModelScope.launch { shizukuManager.isProjectMediaAllowed() }
          } else if (safeTab == HomeTabType.SETTINGS || safeTab == HomeTabType.INSPECT) {
            refreshInspectStorageStats()
          }
        }
      }
      is HomeUiIntent.SelectTab -> {
        val safeTab = HomeTabType.sanitizeTab(intent.tab)
        savedStateHandle[KEY_ACTIVE_HOME_TAB] = safeTab.name
        _localState.update {
          it.copy(
            activeTab = safeTab,
            isScriptFabExpanded = false,
            isInspectFabExpanded = false,
            isSearchExpanded = false,
            searchQuery = "",
          )
        }
        if (safeTab == HomeTabType.SEAMLESS) {
          shizukuManager.refreshDiagnostics()
          viewModelScope.launch { shizukuManager.isProjectMediaAllowed() }
        } else if (safeTab == HomeTabType.SETTINGS || safeTab == HomeTabType.INSPECT) {
          refreshInspectStorageStats()
        }
      }
      is HomeUiIntent.SetSearchExpanded -> {
        _localState.update {
          it.copy(
            isSearchExpanded = intent.expanded,
            searchQuery = if (intent.expanded) it.searchQuery else "",
          )
        }
      }
      is HomeUiIntent.UpdateSearchQuery -> {
        _localState.update { it.copy(searchQuery = intent.query) }
      }
      HomeUiIntent.OpenPaywallClicked -> {
        sendEffect(HomeUiEffect.NavigateToPaywall(null))
      }

      // Scripts Tab
      is HomeUiIntent.ToggleScriptFab -> {
        _localState.update { it.copy(isScriptFabExpanded = intent.expanded) }
      }
      HomeUiIntent.AddManualScriptClicked -> {
        _localState.update { it.copy(isScriptFabExpanded = false) }
        sendEffect(HomeUiEffect.NavigateToScriptEditor(null))
      }
      HomeUiIntent.AddRecordScriptClicked -> {
        _localState.update { it.copy(isScriptFabExpanded = false) }
        if (!ensurePermissionsReady(PendingHomeOverlayAction.LiveRecorder)) return
        launchTargetWithVisionGuard(
          target = PendingMediaProjectionTarget.LiveRecorder,
          requiresVision = true,
        )
      }
      is HomeUiIntent.PlayScriptClicked -> {
        viewModelScope.launch {
          val script = scriptRepository.getScriptById(intent.scriptId) ?: return@launch
          val blockers = entitlementRepository.detectScriptProBlockers(script)
          if (blockers.isNotEmpty()) {
            sendEffect(
              HomeUiEffect.NavigateToPaywall(
                blockers.joinToString(", ") { it.displayName },
              ),
            )
            return@launch
          }
          if (!ensurePermissionsReady(PendingHomeOverlayAction.PlayScript(script.id))) {
            return@launch
          }
          val requiresVision = script.requiresVision()
          when (preferencesRepository.getPreferencesOnce().runnerLaunchMode) {
            RunnerLaunchMode.STANDBY -> {
              launchTargetWithVisionGuard(
                target = PendingMediaProjectionTarget.RunScript(script.id, autoStart = false),
                requiresVision = requiresVision,
              )
            }
            RunnerLaunchMode.AUTO_START -> {
              launchTargetWithVisionGuard(
                target = PendingMediaProjectionTarget.RunScript(script.id, autoStart = true),
                requiresVision = requiresVision,
              )
            }
            RunnerLaunchMode.ASK_EVERY_TIME -> {
              _localState.update {
                it.copy(
                  runnerLaunchChoicePrompt =
                    RunnerLaunchChoicePrompt(
                      target =
                        RunnerLaunchTarget.ScriptTarget(
                          scriptId = script.id,
                          title = script.title,
                          requiresVision = requiresVision,
                        ),
                    ),
                )
              }
            }
          }
        }
      }
      is HomeUiIntent.EditScriptClicked -> {
        sendEffect(HomeUiEffect.NavigateToScriptEditor(intent.scriptId))
      }
      is HomeUiIntent.ViewScriptLogsClicked -> {
        sendEffect(HomeUiEffect.NavigateToScriptLog(intent.scriptId))
      }
      is HomeUiIntent.RequestDeleteScript -> {
        viewModelScope.launch {
          val script = scriptRepository.getScriptById(intent.scriptId) ?: return@launch
          val referencing = scriptRepository.findPipelinesReferencingScript(script.id)
          _localState.update {
            it.copy(
              deleteScriptPrompt =
                DeleteScriptPrompt(
                  scriptId = script.id,
                  scriptTitle = script.title,
                  referencingPipelineTitles = referencing.map { p -> p.title },
                ),
            )
          }
        }
      }
      HomeUiIntent.ConfirmDeleteScript -> {
        val prompt = _localState.value.deleteScriptPrompt ?: return
        _localState.update { it.copy(deleteScriptPrompt = null) }
        viewModelScope.launch {
          scriptRepository.deleteScript(prompt.scriptId)
          sendEffect(HomeUiEffect.ShowSnackbar("Deleted \"${prompt.scriptTitle}\""))
        }
      }
      HomeUiIntent.DismissDeleteScript -> {
        _localState.update { it.copy(deleteScriptPrompt = null) }
      }
      is HomeUiIntent.DuplicateScript -> {
        viewModelScope.launch {
          val source = scriptRepository.getScriptById(intent.scriptId) ?: return@launch
          val cloned =
            ScriptCloningUtils.cloneScriptForDuplicate(
              source = source,
              assetManager = scriptAssetManager,
            )
          scriptRepository.saveScript(cloned)
          sendEffect(HomeUiEffect.ShowSnackbar("Duplicated \"${source.title.ifBlank { "Untitled Script" }}\""))
        }
      }
      HomeUiIntent.LoadStarterTemplatesClicked -> {
        viewModelScope.launch {
          val templates = StarterScriptTemplates.createAll()
          templates.forEach { script ->
            scriptRepository.saveScript(script)
          }
          sendEffect(HomeUiEffect.ShowSnackbar("Loaded 2 starter template scripts"))
        }
      }
      is HomeUiIntent.DuplicateScript -> {
        viewModelScope.launch {
          val source = scriptRepository.getScriptById(intent.scriptId) ?: return@launch
          val cloned =
            ScriptCloningUtils.cloneScriptForDuplicate(
              source = source,
              assetManager = scriptAssetManager,
            )
          scriptRepository.saveScript(cloned)
          sendEffect(HomeUiEffect.ShowSnackbar("Duplicated \"${source.title.ifBlank { "Untitled Script" }}\""))
        }
      }
      HomeUiIntent.LoadStarterTemplatesClicked -> {
        viewModelScope.launch {
          val templates = StarterScriptTemplates.createAll()
          templates.forEach { script ->
            scriptRepository.saveScript(script)
          }
          sendEffect(HomeUiEffect.ShowSnackbar("Loaded 2 starter template scripts"))
        }
      }
      is HomeUiIntent.ToggleScriptTagFilter -> {
        val available = _uiState.value.availableScriptTags
        _localState.update { state ->
          state.copy(
            selectedScriptTags =
              HomeLibraryFilter.toggleTagSelection(
                selectedTags = state.selectedScriptTags,
                rawTag = intent.tag,
                availableTags = available,
              ),
          )
        }
      }
      HomeUiIntent.ClearScriptTagFilters -> {
        _localState.update { it.copy(selectedScriptTags = emptySet()) }
      }

      // Pipelines Tab
      HomeUiIntent.AddPipelineClicked -> {
        sendEffect(HomeUiEffect.NavigateToPipelineEditor(null))
      }
      is HomeUiIntent.PlayPipelineClicked -> {
        viewModelScope.launch {
          val pipeline = pipelineRepository.getPipelineById(intent.pipelineId) ?: return@launch
          val scriptsById = scriptRepository.getAllScripts().associateBy { it.id }
          val blockers = entitlementRepository.detectPipelineProBlockers(pipeline, scriptsById)
          if (blockers.isNotEmpty()) {
            sendEffect(
              HomeUiEffect.NavigateToPaywall(
                blockers.joinToString(", ") { it.displayName },
              ),
            )
            return@launch
          }
          if (!ensurePermissionsReady(PendingHomeOverlayAction.PlayPipeline(pipeline.id))) {
            return@launch
          }
          val requiresVision =
            pipeline.steps.any { step -> scriptsById[step.scriptId]?.requiresVision() == true }
          when (preferencesRepository.getPreferencesOnce().runnerLaunchMode) {
            RunnerLaunchMode.STANDBY -> {
              launchTargetWithVisionGuard(
                target = PendingMediaProjectionTarget.RunPipeline(pipeline.id, autoStart = false),
                requiresVision = requiresVision,
              )
            }
            RunnerLaunchMode.AUTO_START -> {
              launchTargetWithVisionGuard(
                target = PendingMediaProjectionTarget.RunPipeline(pipeline.id, autoStart = true),
                requiresVision = requiresVision,
              )
            }
            RunnerLaunchMode.ASK_EVERY_TIME -> {
              _localState.update {
                it.copy(
                  runnerLaunchChoicePrompt =
                    RunnerLaunchChoicePrompt(
                      target =
                        RunnerLaunchTarget.PipelineTarget(
                          pipelineId = pipeline.id,
                          title = pipeline.title,
                          requiresVision = requiresVision,
                        ),
                    ),
                )
              }
            }
          }
        }
      }
      is HomeUiIntent.EditPipelineClicked -> {
        sendEffect(HomeUiEffect.NavigateToPipelineEditor(intent.pipelineId))
      }
      is HomeUiIntent.ViewPipelineLogsClicked -> {
        sendEffect(HomeUiEffect.NavigateToPipelineLog(intent.pipelineId))
      }
      is HomeUiIntent.RequestDeletePipeline -> {
        viewModelScope.launch {
          val pipeline = pipelineRepository.getPipelineById(intent.pipelineId) ?: return@launch
          _localState.update {
            it.copy(
              deletePipelinePrompt =
                DeletePipelinePrompt(
                  pipelineId = pipeline.id,
                  pipelineTitle = pipeline.title,
                ),
            )
          }
        }
      }
      is HomeUiIntent.DuplicatePipeline -> {
        viewModelScope.launch {
          val source = pipelineRepository.getPipelineById(intent.pipelineId) ?: return@launch
          val cloned = ScriptCloningUtils.clonePipelineForDuplicate(source = source)
          pipelineRepository.savePipeline(cloned)
          sendEffect(HomeUiEffect.ShowSnackbar("Duplicated \"${source.title.ifBlank { "Untitled Pipeline" }}\""))
        }
      }
      HomeUiIntent.ConfirmDeletePipeline -> {
        val prompt = _localState.value.deletePipelinePrompt ?: return
        _localState.update { it.copy(deletePipelinePrompt = null) }
        viewModelScope.launch {
          pipelineRepository.deletePipeline(prompt.pipelineId)
          sendEffect(HomeUiEffect.ShowSnackbar("Deleted \"${prompt.pipelineTitle}\""))
        }
      }
      HomeUiIntent.DismissDeletePipeline -> {
        _localState.update { it.copy(deletePipelinePrompt = null) }
      }
      is HomeUiIntent.TogglePipelineTagFilter -> {
        val available = _uiState.value.availablePipelineTags
        _localState.update { state ->
          state.copy(
            selectedPipelineTags =
              HomeLibraryFilter.toggleTagSelection(
                selectedTags = state.selectedPipelineTags,
                rawTag = intent.tag,
                availableTags = available,
              ),
          )
        }
      }
      HomeUiIntent.ClearPipelineTagFilters -> {
        _localState.update { it.copy(selectedPipelineTags = emptySet()) }
      }

      // Inspect Tab
      is HomeUiIntent.ToggleInspectGroupExpanded -> {
        _localState.update { state ->
          val next =
            if (intent.groupId in state.expandedInspectGroupIds) {
              state.expandedInspectGroupIds - intent.groupId
            } else {
              state.expandedInspectGroupIds + intent.groupId
            }
          state.copy(expandedInspectGroupIds = next)
        }
      }
      is HomeUiIntent.ToggleInspectItemExpanded -> {
        _localState.update { state ->
          val next =
            if (intent.itemId in state.expandedInspectItemIds) {
              state.expandedInspectItemIds - intent.itemId
            } else {
              state.expandedInspectItemIds + intent.itemId
            }
          state.copy(expandedInspectItemIds = next)
        }
      }
      is HomeUiIntent.RequestDeleteInspectGroup -> {
        val entry =
          _uiState.value.inspectGroups.firstOrNull { it.group.id == intent.groupId } ?: return
        _localState.update {
          it.copy(
            deleteInspectGroupPrompt =
              DeleteInspectGroupPrompt(
                groupId = entry.group.id,
                groupTitle = entry.group.title,
                itemCount = entry.items.size,
              ),
          )
        }
      }
      HomeUiIntent.ConfirmDeleteInspectGroup -> {
        val prompt = _localState.value.deleteInspectGroupPrompt ?: return
        _localState.update { it.copy(deleteInspectGroupPrompt = null) }
        viewModelScope.launch {
          inspectRepository.deleteGroup(prompt.groupId)
          refreshInspectStorageStats()
          sendEffect(
            HomeUiEffect.ShowSnackbar("Removed group \"${prompt.groupTitle}\" and PNG assets"),
          )
        }
      }
      HomeUiIntent.DismissDeleteInspectGroup -> {
        _localState.update { it.copy(deleteInspectGroupPrompt = null) }
      }
      is HomeUiIntent.DeleteInspectItem -> {
        viewModelScope.launch {
          inspectRepository.deleteItem(intent.itemId)
          refreshInspectStorageStats()
          sendEffect(HomeUiEffect.ShowSnackbar("Inspect screenshot deleted"))
        }
      }
      is HomeUiIntent.DeleteInspectPixel -> {
        viewModelScope.launch {
          inspectRepository.deletePixelFromItem(intent.itemId, intent.pixelId)
        }
      }
      is HomeUiIntent.DeleteInspectCrop -> {
        viewModelScope.launch {
          inspectRepository.deleteCropFromItem(intent.itemId, intent.cropId)
          refreshInspectStorageStats()
        }
      }
      is HomeUiIntent.OpenInspectStudio -> {
        sendEffect(HomeUiEffect.NavigateToInspectStudio(intent.itemId))
      }
      is HomeUiIntent.ToggleInspectFab -> {
        _localState.update { it.copy(isInspectFabExpanded = intent.expanded) }
      }
      HomeUiIntent.LiveInspectClicked -> {
        _localState.update { it.copy(isInspectFabExpanded = false) }
        if (!entitlementRepository.canUseFeature(ProFeatureGate.ADVANCED_VISION)) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.ADVANCED_VISION.displayName))
          return
        }
        if (!ensurePermissionsReady(PendingHomeOverlayAction.LiveInspector)) return
        launchTargetWithVisionGuard(
          target = PendingMediaProjectionTarget.LiveInspector,
          requiresVision = true,
        )
      }
      HomeUiIntent.ImportScreenshotClicked -> {
        _localState.update { it.copy(isInspectFabExpanded = false) }
        sendEffect(HomeUiEffect.LaunchPhotoPicker)
      }
      is HomeUiIntent.HandleImportedScreenshotUri -> {
        viewModelScope.launch {
          inspectImageImporter.importImageUri(intent.uri).fold(
            onSuccess = { item ->
              _localState.update {
                it.copy(expandedInspectGroupIds = it.expandedInspectGroupIds + item.groupId)
              }
              refreshInspectStorageStats()
              sendEffect(HomeUiEffect.ShowSnackbar("Imported lossless PNG into Inspect Studio"))
            },
            onFailure = { err ->
              sendEffect(
                HomeUiEffect.ShowSnackbar(
                  err.message ?: "Failed to import screenshot",
                ),
              )
            },
          )
        }
      }

      // Console Tab
      HomeUiIntent.ToggleLocalServer -> {
        if (!FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
          if (localServer.status.value.isRunning) {
            sendEffect(HomeUiEffect.StopLocalBridgeService)
            viewModelScope.launch { localServer.stopServer("Desktop Console is disabled in this release.") }
          }
          return
        }
        val isLifetimePro =
          entitlementRepository.entitlementState.value is EntitlementState.ProLifetime &&
            entitlementRepository.canUseFeature(ProFeatureGate.DESKTOP_CONSOLE)
        if (!isLifetimePro) {
          if (localServer.status.value.isRunning) {
            sendEffect(HomeUiEffect.StopLocalBridgeService)
            viewModelScope.launch {
              localServer.stopServer("Lifetime Pro purchase is required for Desktop Console.")
            }
          }
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.DESKTOP_CONSOLE.displayName))
          return
        }
        val status = localServer.status.value
        if (status.isRunning) {
          sendEffect(HomeUiEffect.StopLocalBridgeService)
          sendEffect(HomeUiEffect.ShowSnackbar("Desktop Studio bridge stopped"))
        } else {
          sendEffect(HomeUiEffect.StartLocalBridgeService(status.port))
          sendEffect(
            HomeUiEffect.ShowSnackbar("Desktop Studio bridge starting on port ${status.port}"),
          )
        }
      }
      is HomeUiIntent.UpdateServerPort -> {
        if (!FeatureFlags.ENABLE_DESKTOP_CONSOLE) return
        val parsed = intent.portText.toIntOrNull()
        if (parsed == null || parsed !in 1024..65535) {
          sendEffect(HomeUiEffect.ShowSnackbar("Enter a valid port between 1024 and 65535"))
          return
        }
        viewModelScope.launch {
          localServer.updatePort(parsed)
          sendEffect(HomeUiEffect.ShowSnackbar("Desktop Studio port updated to $parsed"))
        }
      }
      HomeUiIntent.CopyAdbCommandClicked -> {
        if (!FeatureFlags.ENABLE_DESKTOP_CONSOLE) return
        val cmd = _uiState.value.serverStatus.adbCommand
        sendEffect(HomeUiEffect.CopyToClipboard("ADB Forward Command", cmd))
        sendEffect(HomeUiEffect.ShowSnackbar("Copied: $cmd"))
      }

      // Seamless Tab
      HomeUiIntent.RefreshShizukuStatus -> {
        shizukuManager.refreshDiagnostics()
        viewModelScope.launch { shizukuManager.isProjectMediaAllowed() }
      }
      HomeUiIntent.RequestShizukuPermission -> {
        val diag = shizukuManager.refreshDiagnostics()
        if (!diag.isInstalled) {
          sendEffect(HomeUiEffect.OpenShizukuDownloadPage)
        } else if (!diag.isServiceRunning) {
          sendEffect(HomeUiEffect.OpenShizukuApp)
        } else if (!diag.isPermissionGranted) {
          shizukuManager.requestPermission()
        }
      }
      HomeUiIntent.OpenShizukuDownloadClicked -> {
        sendEffect(HomeUiEffect.OpenShizukuDownloadPage)
      }
      HomeUiIntent.OpenShizukuAppClicked -> {
        val diag = shizukuManager.refreshDiagnostics()
        if (diag.isInstalled) {
          sendEffect(HomeUiEffect.OpenShizukuApp)
        } else {
          sendEffect(HomeUiEffect.OpenShizukuDownloadPage)
        }
      }
      is HomeUiIntent.ToggleShizukuShellInput -> {
        if (!requireShizukuProAndReady()) return
        viewModelScope.launch { preferencesRepository.setShizukuShellInput(intent.enabled) }
      }
      is HomeUiIntent.ToggleShizukuForceStop -> {
        if (!requireShizukuProAndReady()) return
        viewModelScope.launch { preferencesRepository.setShizukuForceStop(intent.enabled) }
      }
      is HomeUiIntent.ToggleShizukuEnhancedKeys -> {
        if (!requireShizukuProAndReady()) return
        viewModelScope.launch { preferencesRepository.setShizukuEnhancedKeys(intent.enabled) }
      }
      is HomeUiIntent.ToggleShizukuSilentScreenshots -> {
        if (!requireShizukuProAndReady()) return
        viewModelScope.launch { preferencesRepository.setShizukuSilentScreenshots(intent.enabled) }
      }
      is HomeUiIntent.ToggleShizukuAutoGrantProjectMedia -> {
        if (!requireShizukuProAndReady()) return
        viewModelScope.launch {
          if (intent.enabled) {
            shizukuManager.grantProjectMediaAppOp().fold(
              onSuccess = { allowed ->
                if (allowed) {
                  preferencesRepository.setShizukuAutoGrantProjectMedia(true)
                  sendEffect(
                    HomeUiEffect.ShowSnackbar("PROJECT_MEDIA AppOp allowed via Shizuku."),
                  )
                } else {
                  preferencesRepository.setShizukuAutoGrantProjectMedia(false)
                  sendEffect(
                    HomeUiEffect.ShowSnackbar(
                      "OEM ROM ignored PROJECT_MEDIA AppOp grant; standard prompt will be used.",
                    ),
                  )
                }
              },
              onFailure = { err ->
                preferencesRepository.setShizukuAutoGrantProjectMedia(false)
                sendEffect(
                  HomeUiEffect.ShowSnackbar(
                    err.message ?: "Failed to grant PROJECT_MEDIA via Shizuku.",
                  ),
                )
              },
            )
          } else {
            shizukuManager.revokeProjectMediaAppOp().onFailure { err ->
              sendEffect(
                HomeUiEffect.ShowSnackbar(
                  err.message ?: "Unable to revoke PROJECT_MEDIA via Shizuku.",
                ),
              )
            }
            preferencesRepository.setShizukuAutoGrantProjectMedia(false)
          }
        }
      }
      is HomeUiIntent.ToggleExternalBroadcast -> {
        if (!entitlementRepository.canUseFeature(ProFeatureGate.EXTERNAL_BROADCAST)) {
          sendEffect(
            HomeUiEffect.NavigateToPaywall(ProFeatureGate.EXTERNAL_BROADCAST.displayName),
          )
          return
        }
        if (!shizukuManager.isReadyNow()) {
          sendEffect(
            HomeUiEffect.ShowSnackbar("Install, start, and grant Shizuku permission first."),
          )
          return
        }
        viewModelScope.launch { preferencesRepository.setExternalBroadcastEnabled(intent.enabled) }
      }
      HomeUiIntent.OpenExternalAutomationGuideClicked -> {
        sendEffect(HomeUiEffect.NavigateToExternalAutomationGuide)
      }

      // Settings Tab
      HomeUiIntent.ActivateSevenDayTrialClicked -> {
        viewModelScope.launch {
          val eval = entitlementRepository.activateSevenDayTrial()
          val msg =
            when (eval.status) {
              TrialStatus.ACTIVE -> "7-Day Pro Trial activated!"
              TrialStatus.EXPIRED, TrialStatus.EXPIRED_TAMPERED ->
                "Free trial already used or expired on this device."
              TrialStatus.NOT_STARTED -> "Unable to activate trial."
            }
          sendEffect(HomeUiEffect.ShowSnackbar(msg))
        }
      }
      HomeUiIntent.RestorePurchaseClicked -> {
        viewModelScope.launch {
          val res = entitlementRepository.restorePurchases()
          val msg =
            when (res) {
              BillingRestoreResult.RESTORED_LIFETIME -> "Lifetime Pro restored"
              BillingRestoreResult.PENDING_SETTLEMENT -> "Payment pending settlement"
              BillingRestoreResult.NO_PURCHASE_FOUND -> "No previous Lifetime Pro purchase found"
              BillingRestoreResult.SERVICE_UNAVAILABLE -> "Google Play Billing unavailable"
            }
          sendEffect(HomeUiEffect.ShowSnackbar(msg))
        }
      }
      is HomeUiIntent.SetDarkMode -> {
        viewModelScope.launch { preferencesRepository.setDarkMode(intent.option) }
      }
      is HomeUiIntent.SetThemePalette -> {
        if (intent.palette == ThemePaletteOption.AMOLED_TRUE_BLACK &&
          !entitlementRepository.canUseFeature(ProFeatureGate.AMOLED_THEME)
        ) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.AMOLED_THEME.displayName))
          return
        }
        viewModelScope.launch { preferencesRepository.setThemePalette(intent.palette) }
      }
      is HomeUiIntent.SetOverlaySize -> {
        viewModelScope.launch { preferencesRepository.setOverlaySize(intent.option) }
      }
      is HomeUiIntent.SetVolumeStopEnabled -> {
        viewModelScope.launch { preferencesRepository.setVolumeStopEnabled(intent.enabled) }
      }
      is HomeUiIntent.SetKeepScreenAwakeWhileRunning -> {
        viewModelScope.launch { preferencesRepository.setKeepScreenAwakeWhileRunning(intent.enabled) }
      }
      is HomeUiIntent.SetPauseOnScreenOff -> {
        viewModelScope.launch { preferencesRepository.setPauseOnScreenOff(intent.enabled) }
      }
      is HomeUiIntent.SetShowDebugTouchMarker -> {
        viewModelScope.launch { preferencesRepository.setShowDebugTouchMarker(intent.enabled) }
      }
      is HomeUiIntent.SetVolumeStopEnabled -> {
        viewModelScope.launch { preferencesRepository.setVolumeStopEnabled(intent.enabled) }
      }
      is HomeUiIntent.SetKeepScreenAwakeWhileRunning -> {
        viewModelScope.launch { preferencesRepository.setKeepScreenAwakeWhileRunning(intent.enabled) }
      }
      is HomeUiIntent.SetPauseOnScreenOff -> {
        viewModelScope.launch { preferencesRepository.setPauseOnScreenOff(intent.enabled) }
      }
      is HomeUiIntent.SetShowDebugTouchMarker -> {
        viewModelScope.launch { preferencesRepository.setShowDebugTouchMarker(intent.enabled) }
      }
      is HomeUiIntent.SetRunnerLaunchMode -> {
        viewModelScope.launch { preferencesRepository.setRunnerLaunchMode(intent.mode) }
      }
      is HomeUiIntent.SetRunnerLaunchRememberChoice -> {
        _localState.update { state ->
          val current = state.runnerLaunchChoicePrompt ?: return@update state
          state.copy(runnerLaunchChoicePrompt = current.copy(rememberChoice = intent.remember))
        }
      }
      is HomeUiIntent.ConfirmRunnerLaunchChoice -> {
        val prompt = _localState.value.runnerLaunchChoicePrompt ?: return
        _localState.update { it.copy(runnerLaunchChoicePrompt = null) }
        viewModelScope.launch {
          if (prompt.rememberChoice) {
            val mode =
              if (intent.autoStart) RunnerLaunchMode.AUTO_START else RunnerLaunchMode.STANDBY
            preferencesRepository.setRunnerLaunchMode(mode)
          }
          when (val target = prompt.target) {
            is RunnerLaunchTarget.ScriptTarget -> {
              launchTargetWithVisionGuard(
                target =
                  PendingMediaProjectionTarget.RunScript(
                    scriptId = target.scriptId,
                    autoStart = intent.autoStart,
                  ),
                requiresVision = target.requiresVision,
              )
            }
            is RunnerLaunchTarget.PipelineTarget -> {
              launchTargetWithVisionGuard(
                target =
                  PendingMediaProjectionTarget.RunPipeline(
                    pipelineId = target.pipelineId,
                    autoStart = intent.autoStart,
                  ),
                requiresVision = target.requiresVision,
              )
            }
          }
        }
      }
      HomeUiIntent.DismissRunnerLaunchChoice -> {
        _localState.update { it.copy(runnerLaunchChoicePrompt = null) }
      }
      is HomeUiIntent.SelectLocale -> {
        if (!AppLocaleHelper.isLocaleAlreadyActive(
            intent.currentLanguageTags,
            intent.locale.languageTag,
          )
        ) {
          sendEffect(HomeUiEffect.ApplyAppLocale(intent.locale.languageTag))
        }
      }
      HomeUiIntent.ExportBackupClicked -> {
        val isLifetimePro =
          entitlementRepository.entitlementState.value is EntitlementState.ProLifetime &&
            entitlementRepository.canUseFeature(ProFeatureGate.DATA_BACKUP)
        if (!isLifetimePro) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.DATA_BACKUP.displayName))
          return
        }
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        sendEffect(HomeUiEffect.LaunchBackupExportPicker("tapflow_backup_$dateStamp.tapflow"))
      }
      is HomeUiIntent.HandleBackupExportUri -> {
        viewModelScope.launch {
          backupManager.exportToUri(intent.uri).fold(
            onSuccess = { summary ->
              sendEffect(
                HomeUiEffect.ShowSnackbar(
                  "Exported ${summary.scriptsExported} scripts & ${summary.pipelinesExported} pipelines (.tapflow)",
                ),
              )
            },
            onFailure = { err ->
              sendEffect(
                HomeUiEffect.ShowSnackbar(err.message ?: "Backup export failed"),
              )
            },
          )
        }
      }
      HomeUiIntent.ImportBackupClicked -> {
        val isLifetimePro =
          entitlementRepository.entitlementState.value is EntitlementState.ProLifetime &&
            entitlementRepository.canUseFeature(ProFeatureGate.DATA_BACKUP)
        if (!isLifetimePro) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.DATA_BACKUP.displayName))
          return
        }
        sendEffect(HomeUiEffect.LaunchBackupImportPicker)
      }
      is HomeUiIntent.HandleBackupImportUri -> {
        viewModelScope.launch {
          backupManager.inspectBackupUri(intent.uri).fold(
            onSuccess = { preview ->
              val hasConflicts =
                preview.duplicateScriptTitles.isNotEmpty() ||
                  preview.duplicatePipelineTitles.isNotEmpty()
              if (hasConflicts) {
                _localState.update {
                  it.copy(importBackupPrompt = ImportBackupPrompt(preview))
                }
              } else {
                val result = backupManager.commitImport(preview, ImportConflictStrategy.IMPORT_AS_COPY)
                sendEffect(
                  HomeUiEffect.ShowSnackbar(
                    "Imported ${result.scriptsImported} scripts & ${result.pipelinesImported} pipelines",
                  ),
                )
              }
            },
            onFailure = { err ->
              sendEffect(
                HomeUiEffect.ShowSnackbar(err.message ?: "Invalid .tapflow backup file"),
              )
            },
          )
        }
      }
      is HomeUiIntent.ConfirmBackupImport -> {
        val prompt = _localState.value.importBackupPrompt ?: return
        _localState.update { it.copy(importBackupPrompt = null) }
        viewModelScope.launch {
          val result = backupManager.commitImport(prompt.preview, intent.strategy)
          sendEffect(
            HomeUiEffect.ShowSnackbar(
              "Imported ${result.scriptsImported} scripts & ${result.pipelinesImported} pipelines",
            ),
          )
        }
      }
      HomeUiIntent.DismissBackupImportPrompt -> {
        _localState.update { it.copy(importBackupPrompt = null) }
      }
      HomeUiIntent.RefreshInspectStorageStats -> {
        refreshInspectStorageStats()
      }
      HomeUiIntent.RequestClearAllInspectData -> {
        viewModelScope.launch {
          val stats = inspectRepository.getInspectStorageStats()
          _localState.update {
            it.copy(
              inspectStorageStats = stats,
              showClearInspectConfirmDialog = true,
            )
          }
        }
      }
      HomeUiIntent.ConfirmClearAllInspectData -> {
        if (_localState.value.isClearingInspectData) return
        _localState.update {
          it.copy(
            showClearInspectConfirmDialog = false,
            isClearingInspectData = true,
          )
        }
        viewModelScope.launch {
          val result = inspectRepository.clearAllInspectImagesOnly()
          val refreshed = inspectRepository.getInspectStorageStats()
          _localState.update {
            it.copy(
              inspectStorageStats = refreshed,
              inspectImageRefreshToken = it.inspectImageRefreshToken + 1L,
              isClearingInspectData = false,
            )
          }
          result.fold(
            onSuccess = {
              sendEffect(HomeUiEffect.ShowSnackbar("Cleared all inspect images"))
            },
            onFailure = { err ->
              sendEffect(
                HomeUiEffect.ShowSnackbar(
                  err.message ?: "Failed to clear inspect images",
                ),
              )
            },
          )
        }
      }
      HomeUiIntent.DismissClearAllInspectData -> {
        _localState.update { it.copy(showClearInspectConfirmDialog = false) }
      }
      is HomeUiIntent.SetTermsDialogVisible -> {
        _localState.update { it.copy(showTermsDialog = intent.visible) }
      }
      is HomeUiIntent.SetPrivacyDialogVisible -> {
        _localState.update { it.copy(showPrivacyDialog = intent.visible) }
      }
      HomeUiIntent.SendFeedbackEmailClicked -> {
        val state = _uiState.value
        val subject = "TapFlow Feedback (v${state.appVersionName})"
        val body =
          buildString {
            appendLine("Describe your issue or suggestion below:")
            appendLine()
            appendLine()
            appendLine("--- System Diagnostics ---")
            appendLine("App Version: ${state.appVersionName} (${state.appVersionCode})")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Entitlement: ${state.entitlementState::class.simpleName}")
            appendLine(
              "Shizuku Ready: ${state.shizukuStatus.isFullyReady} (Installed=${state.shizukuStatus.isInstalled}, Running=${state.shizukuStatus.isServiceRunning}, Permitted=${state.shizukuStatus.isPermissionGranted})",
            )
          }
        sendEffect(HomeUiEffect.LaunchFeedbackEmail(subject, body))
      }
      is HomeUiIntent.SetAppBuildMetadata -> {
        _localState.update {
          it.copy(
            appVersionName = intent.versionName,
            appVersionCode = intent.versionCode,
          )
        }
      }

      // Runtime Permission Pre-Flight & Accessibility Prominent Disclosure
      is HomeUiIntent.SyncOverlayPermission -> {
        _localState.update {
          it.copy(
            isOverlayPermissionGranted = intent.granted,
            isAccessibilityConnected = accessibilityManagerBridge.isAccessibilityConnected.value,
          )
        }
      }
      HomeUiIntent.RequestAccessibilityFromPreFlight -> {
        _localState.update { it.copy(showAccessibilityDisclosureDialog = true) }
      }
      HomeUiIntent.AcceptAccessibilityDisclosure -> {
        _localState.update { it.copy(showAccessibilityDisclosureDialog = false) }
        sendEffect(HomeUiEffect.LaunchAccessibilitySettings)
      }
      HomeUiIntent.DismissAccessibilityDisclosure -> {
        _localState.update { it.copy(showAccessibilityDisclosureDialog = false) }
      }
      HomeUiIntent.RequestOverlayFromPreFlight -> {
        sendEffect(HomeUiEffect.LaunchOverlaySettings)
      }
      HomeUiIntent.ContinuePendingOverlayAction -> {
        val accessibilityOk = accessibilityManagerBridge.isAccessibilityConnected.value
        val overlayOk = _localState.value.isOverlayPermissionGranted
        _localState.update { it.copy(isAccessibilityConnected = accessibilityOk) }
        if (accessibilityOk && overlayOk) {
          val pending = _localState.value.pendingHomeOverlayAction
          _localState.update {
            it.copy(
              showPermissionsDisabledDialog = false,
              pendingHomeOverlayAction = null,
            )
          }
          when (pending) {
            is PendingHomeOverlayAction.PlayScript -> onIntent(HomeUiIntent.PlayScriptClicked(pending.scriptId))
            is PendingHomeOverlayAction.PlayPipeline -> onIntent(HomeUiIntent.PlayPipelineClicked(pending.pipelineId))
            PendingHomeOverlayAction.LiveRecorder -> onIntent(HomeUiIntent.AddRecordScriptClicked)
            PendingHomeOverlayAction.LiveInspector -> onIntent(HomeUiIntent.LiveInspectClicked)
            null -> Unit
          }
        }
      }
      HomeUiIntent.DismissPermissionsDisabledDialog -> {
        _localState.update {
          it.copy(
            showPermissionsDisabledDialog = false,
            pendingHomeOverlayAction = null,
          )
        }
      }

      // MediaProjection consent flow
      HomeUiIntent.ConfirmMediaProjectionConsentDialog -> {
        val target = _localState.value.pendingProjectionTarget ?: return
        _localState.update { it.copy(pendingProjectionTarget = null) }
        sendEffect(HomeUiEffect.RequestMediaProjectionPermission(target))
      }
      HomeUiIntent.DismissMediaProjectionConsentDialog -> {
        _localState.update { it.copy(pendingProjectionTarget = null) }
      }
      HomeUiIntent.MediaProjectionDenied -> {
        sendEffect(
          HomeUiEffect.ShowSnackbar("Screen capture permission is required."),
        )
      }
    }
  }

  private fun ensurePermissionsReady(action: PendingHomeOverlayAction): Boolean {
    val accessibilityOk = accessibilityManagerBridge.isAccessibilityConnected.value
    val overlayOk = _localState.value.isOverlayPermissionGranted
    _localState.update { it.copy(isAccessibilityConnected = accessibilityOk) }
    if (!accessibilityOk || !overlayOk) {
      _localState.update {
        it.copy(
          pendingHomeOverlayAction = action,
          showPermissionsDisabledDialog = true,
        )
      }
      return false
    }
    return true
  }

  fun suppressOverlayAutoCloseBriefly() {
    overlayStateBridge.suppressAutoCloseBriefly()
  }

  private fun launchTargetWithVisionGuard(
    target: PendingMediaProjectionTarget,
    requiresVision: Boolean,
  ) {
    val fallbackAction =
      when (target) {
        is PendingMediaProjectionTarget.RunScript -> PendingHomeOverlayAction.PlayScript(target.scriptId)
        is PendingMediaProjectionTarget.RunPipeline -> PendingHomeOverlayAction.PlayPipeline(target.pipelineId)
        PendingMediaProjectionTarget.LiveRecorder -> PendingHomeOverlayAction.LiveRecorder
        PendingMediaProjectionTarget.LiveInspector -> PendingHomeOverlayAction.LiveInspector
      }
    if (!ensurePermissionsReady(fallbackAction)) return
    viewModelScope.launch {
      val prefs = preferencesRepository.getPreferencesOnce()
      val shizukuUnlocked = entitlementRepository.canUseFeature(ProFeatureGate.SHIZUKU_SUITE)
      val shizukuReady = shizukuUnlocked && shizukuManager.isReadyNow()

      // 1) If Shizuku Silent Screenshots is enabled & ready -> skip MediaProjection completely.
      val shizukuSilentReady = shizukuReady && prefs.shizuku.silentScreenshots
      // 2) If MediaProjectionService already holds an active projection -> launch Overlay directly.
      val hasActiveProjection =
        mediaProjectionBridge.captureState.value is ScreenCaptureState.Active

      if (!requiresVision || shizukuSilentReady || hasActiveProjection) {
        sendEffect(HomeUiEffect.LaunchOverlayAndMinimize(target))
        return@launch
      }

      // 3) If Shizuku Auto-Grant PROJECT_MEDIA is enabled & Shizuku is ready ->
      //    re-verify/re-grant PROJECT_MEDIA AppOp, skip TapFlow's in-app confirmation dialog,
      //    and immediately launch MediaProjection ActivityResultLauncher.
      if (shizukuReady && prefs.shizuku.autoGrantProjectMedia) {
        if (!shizukuManager.isProjectMediaAllowed()) {
          shizukuManager.grantProjectMediaAppOp()
        }
        sendEffect(HomeUiEffect.RequestMediaProjectionPermission(target))
        return@launch
      }

      // 4) Normal non-Shizuku mode -> show TapFlow's in-app "Screen Capture Required" dialog.
      _localState.update { it.copy(pendingProjectionTarget = target) }
    }
  }

  private fun requireShizukuProAndReady(): Boolean {
    if (!entitlementRepository.canUseFeature(ProFeatureGate.SHIZUKU_SUITE)) {
      sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.SHIZUKU_SUITE.displayName))
      return false
    }
    if (!shizukuManager.isReadyNow()) {
      sendEffect(
        HomeUiEffect.ShowSnackbar("Install, start, and grant Shizuku permission first."),
      )
      return false
    }
    return true
  }

  private fun refreshInspectStorageStats() {
    viewModelScope.launch {
      val stats = inspectRepository.getInspectStorageStats()
      _localState.update { it.copy(inspectStorageStats = stats) }
    }
  }

  private fun sendEffect(effect: HomeUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }

  private fun readSavedActiveTab(): HomeTabType? {
    val savedName = savedStateHandle.get<String>(KEY_ACTIVE_HOME_TAB) ?: return null
    val parsed = runCatching { HomeTabType.valueOf(savedName) }.getOrNull() ?: return null
    val safeTab = HomeTabType.sanitizeTab(parsed)
    if (safeTab != parsed) {
      savedStateHandle[KEY_ACTIVE_HOME_TAB] = safeTab.name
    }
    return safeTab
  }

  private fun matchesInspectOrientationQuery(item: InspectItem, query: String): Boolean {
    val compactQuery = query.replace(" ", "").replace("×", "x")
    val dimsCompact = "${item.screenWidth}x${item.screenHeight}"
    if (dimsCompact.contains(compactQuery, ignoreCase = true)) return true

    val keywords =
      when (item.screenOrientation) {
        ScreenOrientation.PORTRAIT -> listOf("portrait", "直向", "竖向")
        ScreenOrientation.LANDSCAPE -> listOf("landscape", "橫向", "横向")
        ScreenOrientation.SQUARE_UNFOLDED ->
          listOf("unfolded", "fold", "foldable", "square", "展開", "展開螢幕", "展开", "展开屏幕")
      }
    if (keywords.any { it.contains(query, ignoreCase = true) || query.contains(it, ignoreCase = true) }) {
      return true
    }
    return item.orientationSubHint?.contains(query, ignoreCase = true) == true
  }
}
