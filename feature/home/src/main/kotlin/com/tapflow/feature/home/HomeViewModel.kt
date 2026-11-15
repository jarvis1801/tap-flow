package com.tapflow.feature.home

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.BillingRestoreResult
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.backup.ImportConflictStrategy
import com.tapflow.core.database.backup.TapFlowBackupManager
import com.tapflow.core.database.repository.InspectGroupWithItems
import com.tapflow.core.database.repository.InspectRepository
import com.tapflow.core.database.repository.PipelineRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.datastore.AppPreferences
import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.TapFlowPreferencesRepository
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
  private val mediaProjectionBridge: MediaProjectionBridge,
  private val backupManager: TapFlowBackupManager,
  private val inspectImageImporter: InspectImageImporter,
) : ViewModel() {

  companion object {
    internal const val KEY_ACTIVE_HOME_TAB = "active_home_tab"
  }

  private data class LocalTransientState(
    val activeTab: HomeTabType = HomeTabType.SCRIPT,
    val searchQuery: String = "",
    val isSearchExpanded: Boolean = false,
    val expandedInspectGroupIds: Set<String> = emptySet(),
    val expandedInspectItemIds: Set<String> = emptySet(),
    val isScriptFabExpanded: Boolean = false,
    val isInspectFabExpanded: Boolean = false,
    val pendingProjectionTarget: PendingMediaProjectionTarget? = null,
    val deleteScriptPrompt: DeleteScriptPrompt? = null,
    val deletePipelinePrompt: DeletePipelinePrompt? = null,
    val deleteInspectGroupPrompt: DeleteInspectGroupPrompt? = null,
    val importBackupPrompt: ImportBackupPrompt? = null,
    val showTermsDialog: Boolean = false,
    val showPrivacyDialog: Boolean = false,
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

        val filteredScripts =
          data.scripts
            .filter { script ->
              query.isEmpty() ||
                script.title.contains(query, ignoreCase = true) ||
                script.tags.any { it.contains(query, ignoreCase = true) }
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
              query.isEmpty() ||
                pipeline.title.contains(query, ignoreCase = true) ||
                pipeline.tags.any { it.contains(query, ignoreCase = true) }
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
          pipelines = filteredPipelines,
          totalPipelinesCount = data.pipelines.size,
          inspectGroups = filteredInspectGroups,
          expandedInspectGroupIds = local.expandedInspectGroupIds,
          expandedInspectItemIds = local.expandedInspectItemIds,
          serverStatus = system.serverStatus,
          shizukuStatus = system.shizukuStatus,
          isScriptFabExpanded = local.isScriptFabExpanded,
          isInspectFabExpanded = local.isInspectFabExpanded,
          pendingProjectionTarget = local.pendingProjectionTarget,
          deleteScriptPrompt = local.deleteScriptPrompt,
          deletePipelinePrompt = local.deletePipelinePrompt,
          deleteInspectGroupPrompt = local.deleteInspectGroupPrompt,
          importBackupPrompt = local.importBackupPrompt,
          showTermsDialog = local.showTermsDialog,
          showPrivacyDialog = local.showPrivacyDialog,
          appVersionName = local.appVersionName,
          appVersionCode = local.appVersionCode,
        )
      }.collect { computed ->
        _uiState.value = computed
      }
    }
  }

  fun onIntent(intent: HomeUiIntent) {
    when (intent) {
      is HomeUiIntent.InitializeTabIfUnset -> {
        if (!savedStateHandle.contains(KEY_ACTIVE_HOME_TAB)) {
          savedStateHandle[KEY_ACTIVE_HOME_TAB] = intent.tab.name
          _localState.update { it.copy(activeTab = intent.tab) }
          if (intent.tab == HomeTabType.SEAMLESS) {
            shizukuManager.refreshDiagnostics()
            viewModelScope.launch { shizukuManager.isProjectMediaAllowed() }
          }
        }
      }
      is HomeUiIntent.SelectTab -> {
        savedStateHandle[KEY_ACTIVE_HOME_TAB] = intent.tab.name
        _localState.update {
          it.copy(
            activeTab = intent.tab,
            isScriptFabExpanded = false,
            isInspectFabExpanded = false,
            isSearchExpanded = false,
            searchQuery = "",
          )
        }
        if (intent.tab == HomeTabType.SEAMLESS) {
          shizukuManager.refreshDiagnostics()
          viewModelScope.launch { shizukuManager.isProjectMediaAllowed() }
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
        if (!entitlementRepository.canCreateScript(_uiState.value.totalScriptsCount)) {
          sendEffect(
            HomeUiEffect.NavigateToPaywall(
              "${ProFeatureGate.UNLIMITED_SCRIPTS.displayName} (Free limit: ${EntitlementValidator.FREE_MAX_SCRIPTS})",
            ),
          )
          return
        }
        sendEffect(HomeUiEffect.NavigateToScriptEditor(null))
      }
      HomeUiIntent.AddRecordScriptClicked -> {
        _localState.update { it.copy(isScriptFabExpanded = false) }
        if (!entitlementRepository.canCreateScript(_uiState.value.totalScriptsCount)) {
          sendEffect(
            HomeUiEffect.NavigateToPaywall(
              "${ProFeatureGate.UNLIMITED_SCRIPTS.displayName} (Free limit: ${EntitlementValidator.FREE_MAX_SCRIPTS})",
            ),
          )
          return
        }
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
          launchTargetWithVisionGuard(
            target = PendingMediaProjectionTarget.RunScript(script.id),
            requiresVision = script.requiresVision(),
          )
        }
      }
      is HomeUiIntent.EditScriptClicked -> {
        sendEffect(HomeUiEffect.NavigateToScriptEditor(intent.scriptId))
      }
      is HomeUiIntent.ViewScriptLogsClicked -> {
        if (!entitlementRepository.canUseFeature(ProFeatureGate.FULL_EXECUTION_LOGS)) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.FULL_EXECUTION_LOGS.displayName))
          return
        }
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

      // Pipelines Tab
      HomeUiIntent.AddPipelineClicked -> {
        if (!entitlementRepository.canCreatePipeline(_uiState.value.totalPipelinesCount)) {
          sendEffect(
            HomeUiEffect.NavigateToPaywall(
              "${ProFeatureGate.UNLIMITED_PIPELINES.displayName} (Free limit: ${EntitlementValidator.FREE_MAX_PIPELINES})",
            ),
          )
          return
        }
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
          val requiresVision =
            pipeline.steps.any { step -> scriptsById[step.scriptId]?.requiresVision() == true }
          launchTargetWithVisionGuard(
            target = PendingMediaProjectionTarget.RunPipeline(pipeline.id),
            requiresVision = requiresVision,
          )
        }
      }
      is HomeUiIntent.EditPipelineClicked -> {
        sendEffect(HomeUiEffect.NavigateToPipelineEditor(intent.pipelineId))
      }
      is HomeUiIntent.ViewPipelineLogsClicked -> {
        if (!entitlementRepository.canUseFeature(ProFeatureGate.FULL_EXECUTION_LOGS)) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.FULL_EXECUTION_LOGS.displayName))
          return
        }
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
        if (!entitlementRepository.canUseFeature(ProFeatureGate.LIVE_IMAGE_INSPECTOR)) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.LIVE_IMAGE_INSPECTOR.displayName))
          return
        }
        launchTargetWithVisionGuard(
          target = PendingMediaProjectionTarget.LiveInspector,
          requiresVision = true,
        )
      }
      HomeUiIntent.ImportScreenshotClicked -> {
        _localState.update { it.copy(isInspectFabExpanded = false) }
        if (!entitlementRepository.canUseFeature(ProFeatureGate.INSPECT_STUDIO_CROP)) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.INSPECT_STUDIO_CROP.displayName))
          return
        }
        sendEffect(HomeUiEffect.LaunchPhotoPicker)
      }
      is HomeUiIntent.HandleImportedScreenshotUri -> {
        viewModelScope.launch {
          inspectImageImporter.importImageUri(intent.uri).fold(
            onSuccess = { item ->
              _localState.update {
                it.copy(expandedInspectGroupIds = it.expandedInspectGroupIds + item.groupId)
              }
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
        val isLifetimePro =
          entitlementRepository.entitlementState.value is EntitlementState.ProLifetime &&
            entitlementRepository.canUseFeature(ProFeatureGate.DESKTOP_CONSOLE)
        if (!isLifetimePro) {
          if (localServer.status.value.isRunning) {
            viewModelScope.launch {
              localServer.stopServer("Lifetime Pro purchase is required for Desktop Console.")
            }
          }
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.DESKTOP_CONSOLE.displayName))
          return
        }
        viewModelScope.launch {
          val started = localServer.toggleServer()
          val msg =
            if (started) {
              "Desktop Studio bridge listening on port ${localServer.status.value.port}"
            } else {
              localServer.status.value.lastError ?: "Desktop Studio bridge stopped"
            }
          sendEffect(HomeUiEffect.ShowSnackbar(msg))
        }
      }
      is HomeUiIntent.UpdateServerPort -> {
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
          sendEffect(HomeUiEffect.ShowSnackbar("Start the Shizuku service in the Shizuku app first"))
        } else {
          shizukuManager.requestPermission()
        }
      }
      HomeUiIntent.OpenShizukuDownloadClicked -> {
        sendEffect(HomeUiEffect.OpenShizukuDownloadPage)
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
        if (intent.option == DarkModeOption.AMOLED &&
          !entitlementRepository.canUseFeature(ProFeatureGate.AMOLED_THEME)
        ) {
          sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.AMOLED_THEME.displayName))
          return
        }
        viewModelScope.launch { preferencesRepository.setDarkMode(intent.option) }
      }
      is HomeUiIntent.SetThemePalette -> {
        viewModelScope.launch { preferencesRepository.setThemePalette(intent.palette) }
      }
      is HomeUiIntent.SelectLocale -> {
        sendEffect(HomeUiEffect.ApplyAppLocale(intent.locale.languageTag))
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

  private fun launchTargetWithVisionGuard(
    target: PendingMediaProjectionTarget,
    requiresVision: Boolean,
  ) {
    viewModelScope.launch {
      val prefs = preferencesRepository.getPreferencesOnce()
      val isPro = entitlementRepository.isPro()
      val shizukuReady =
        isPro &&
          (shizukuManager.diagnosticState.value.isFullyReady ||
            shizukuManager.refreshDiagnostics().isFullyReady)

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
    if (!entitlementRepository.canUseFeature(ProFeatureGate.SHIZUKU_INTEGRATION)) {
      sendEffect(HomeUiEffect.NavigateToPaywall(ProFeatureGate.SHIZUKU_INTEGRATION.displayName))
      return false
    }
    if (!shizukuManager.diagnosticState.value.isFullyReady) {
      sendEffect(
        HomeUiEffect.ShowSnackbar("Install, start, and grant Shizuku permission first."),
      )
      return false
    }
    return true
  }

  private fun sendEffect(effect: HomeUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }

  private fun readSavedActiveTab(): HomeTabType? {
    val savedName = savedStateHandle.get<String>(KEY_ACTIVE_HOME_TAB) ?: return null
    return runCatching { HomeTabType.valueOf(savedName) }.getOrNull()
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
