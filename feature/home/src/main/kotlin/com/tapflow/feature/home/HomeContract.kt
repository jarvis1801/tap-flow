package com.tapflow.feature.home

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.tapflow.core.database.backup.BackupImportPreview
import com.tapflow.core.database.backup.ImportConflictStrategy
import com.tapflow.core.database.repository.InspectGroupWithItems
import com.tapflow.core.database.repository.InspectStorageStats
import com.tapflow.core.datastore.AppPreferences
import java.util.Locale
import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.ThemePaletteOption
import com.tapflow.core.model.common.OverlaySizeOption
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import com.tapflow.core.server.ServerUiStatus
import com.tapflow.core.service.shizuku.ShizukuDiagnosticState

enum class HomeTabType(val title: String) {
  SCRIPT("Scripts"),
  PIPELINE("Pipelines"),
  INSPECT("Inspect"),
  CONSOLE("Console"),
  SEAMLESS("Seamless"),
  SETTINGS("Settings"),
}

enum class AppLocaleOption(val label: String, val languageTag: String) {
  ENGLISH("English", "en"),
  TRADITIONAL_CHINESE("繁體中文", "zh-TW"),
  SIMPLIFIED_CHINESE("简体中文", "zh-CN"),
}

@Immutable
data class ScriptCardUi(
  val script: Script,
  val requiresVision: Boolean,
  val proBlockers: List<String>,
)

@Immutable
data class PipelineCardUi(
  val pipeline: Pipeline,
  val totalRunsCount: Int,
  val missingScriptCount: Int,
  val requiresVision: Boolean,
  val proBlockers: List<String>,
)

@Immutable
sealed interface PendingMediaProjectionTarget {
  @Immutable
  data class RunScript(val scriptId: String) : PendingMediaProjectionTarget

  @Immutable
  data class RunPipeline(val pipelineId: String) : PendingMediaProjectionTarget

  @Immutable
  data object LiveRecorder : PendingMediaProjectionTarget

  @Immutable
  data object LiveInspector : PendingMediaProjectionTarget
}

@Immutable
data class DeleteScriptPrompt(
  val scriptId: String,
  val scriptTitle: String,
  val referencingPipelineTitles: List<String>,
)

@Immutable
data class DeletePipelinePrompt(
  val pipelineId: String,
  val pipelineTitle: String,
)

@Immutable
data class DeleteInspectGroupPrompt(
  val groupId: String,
  val groupTitle: String,
  val itemCount: Int,
)

@Immutable
data class ImportBackupPrompt(
  val preview: BackupImportPreview,
)

@Immutable
data class HomeUiState(
  val activeTab: HomeTabType = HomeTabType.SCRIPT,
  val searchQuery: String = "",
  val isSearchExpanded: Boolean = false,
  val entitlementState: EntitlementState = EntitlementState.Free(TrialStatus.NOT_STARTED, 7),
  val localizedProPrice: String? = null,
  val preferences: AppPreferences = AppPreferences(),
  val scripts: List<ScriptCardUi> = emptyList(),
  val totalScriptsCount: Int = 0,
  val pipelines: List<PipelineCardUi> = emptyList(),
  val totalPipelinesCount: Int = 0,
  val inspectGroups: List<InspectGroupWithItems> = emptyList(),
  val inspectStorageStats: InspectStorageStats = InspectStorageStats(),
  val inspectImageRefreshToken: Long = 0L,
  val expandedInspectGroupIds: Set<String> = emptySet(),
  val expandedInspectItemIds: Set<String> = emptySet(),
  val serverStatus: ServerUiStatus = ServerUiStatus(),
  val shizukuStatus: ShizukuDiagnosticState = ShizukuDiagnosticState(),
  val isScriptFabExpanded: Boolean = false,
  val isInspectFabExpanded: Boolean = false,
  val pendingProjectionTarget: PendingMediaProjectionTarget? = null,
  val deleteScriptPrompt: DeleteScriptPrompt? = null,
  val deletePipelinePrompt: DeletePipelinePrompt? = null,
  val deleteInspectGroupPrompt: DeleteInspectGroupPrompt? = null,
  val showClearInspectConfirmDialog: Boolean = false,
  val isClearingInspectData: Boolean = false,
  val importBackupPrompt: ImportBackupPrompt? = null,
  val showTermsDialog: Boolean = false,
  val showPrivacyDialog: Boolean = false,
  val appVersionName: String = "1.0.0",
  val appVersionCode: Long = 1L,
) {
  val isPro: Boolean
    get() =
      entitlementState is EntitlementState.ProTrial ||
        entitlementState is EntitlementState.ProLifetime

  val isLifetimePro: Boolean
    get() = entitlementState is EntitlementState.ProLifetime

  val supportsSearch: Boolean
    get() =
      activeTab == HomeTabType.SCRIPT ||
        activeTab == HomeTabType.PIPELINE ||
        activeTab == HomeTabType.INSPECT
}

sealed interface HomeUiIntent {
  data class InitializeTabIfUnset(val tab: HomeTabType) : HomeUiIntent

  data class SelectTab(val tab: HomeTabType) : HomeUiIntent

  data class SetSearchExpanded(val expanded: Boolean) : HomeUiIntent

  data class UpdateSearchQuery(val query: String) : HomeUiIntent

  data object OpenPaywallClicked : HomeUiIntent

  // Scripts Tab
  data class ToggleScriptFab(val expanded: Boolean) : HomeUiIntent

  data object AddManualScriptClicked : HomeUiIntent

  data object AddRecordScriptClicked : HomeUiIntent

  data class PlayScriptClicked(val scriptId: String) : HomeUiIntent

  data class EditScriptClicked(val scriptId: String) : HomeUiIntent

  data class ViewScriptLogsClicked(val scriptId: String) : HomeUiIntent

  data class RequestDeleteScript(val scriptId: String) : HomeUiIntent

  data object ConfirmDeleteScript : HomeUiIntent

  data object DismissDeleteScript : HomeUiIntent

  // Pipelines Tab
  data object AddPipelineClicked : HomeUiIntent

  data class PlayPipelineClicked(val pipelineId: String) : HomeUiIntent

  data class EditPipelineClicked(val pipelineId: String) : HomeUiIntent

  data class ViewPipelineLogsClicked(val pipelineId: String) : HomeUiIntent

  data class RequestDeletePipeline(val pipelineId: String) : HomeUiIntent

  data object ConfirmDeletePipeline : HomeUiIntent

  data object DismissDeletePipeline : HomeUiIntent

  // Inspect Tab
  data class ToggleInspectGroupExpanded(val groupId: String) : HomeUiIntent

  data class ToggleInspectItemExpanded(val itemId: String) : HomeUiIntent

  data class RequestDeleteInspectGroup(val groupId: String) : HomeUiIntent

  data object ConfirmDeleteInspectGroup : HomeUiIntent

  data object DismissDeleteInspectGroup : HomeUiIntent

  data class DeleteInspectItem(val itemId: String) : HomeUiIntent

  data class DeleteInspectPixel(val itemId: String, val pixelId: String) : HomeUiIntent

  data class DeleteInspectCrop(val itemId: String, val cropId: String) : HomeUiIntent

  data class OpenInspectStudio(val itemId: String) : HomeUiIntent

  data class ToggleInspectFab(val expanded: Boolean) : HomeUiIntent

  data object LiveInspectClicked : HomeUiIntent

  data object ImportScreenshotClicked : HomeUiIntent

  data class HandleImportedScreenshotUri(val uri: Uri) : HomeUiIntent

  // Console Tab
  data object ToggleLocalServer : HomeUiIntent

  data class UpdateServerPort(val portText: String) : HomeUiIntent

  data object CopyAdbCommandClicked : HomeUiIntent

  // Seamless Tab
  data object RefreshShizukuStatus : HomeUiIntent

  data object RequestShizukuPermission : HomeUiIntent

  data object OpenShizukuDownloadClicked : HomeUiIntent

  data object OpenShizukuAppClicked : HomeUiIntent

  data class ToggleShizukuShellInput(val enabled: Boolean) : HomeUiIntent

  data class ToggleShizukuForceStop(val enabled: Boolean) : HomeUiIntent

  data class ToggleShizukuEnhancedKeys(val enabled: Boolean) : HomeUiIntent

  data class ToggleShizukuSilentScreenshots(val enabled: Boolean) : HomeUiIntent

  data class ToggleShizukuAutoGrantProjectMedia(val enabled: Boolean) : HomeUiIntent

  data class ToggleExternalBroadcast(val enabled: Boolean) : HomeUiIntent

  data object OpenExternalAutomationGuideClicked : HomeUiIntent

  // Settings Tab
  data object ActivateSevenDayTrialClicked : HomeUiIntent

  data object RestorePurchaseClicked : HomeUiIntent

  data class SetDarkMode(val option: DarkModeOption) : HomeUiIntent

  data class SetThemePalette(val palette: ThemePaletteOption) : HomeUiIntent

  data class SetOverlaySize(val option: OverlaySizeOption) : HomeUiIntent

  data class SelectLocale(
    val locale: AppLocaleOption,
    val currentLanguageTags: String = "",
  ) : HomeUiIntent

  data object ExportBackupClicked : HomeUiIntent

  data class HandleBackupExportUri(val uri: Uri) : HomeUiIntent

  data object ImportBackupClicked : HomeUiIntent

  data class HandleBackupImportUri(val uri: Uri) : HomeUiIntent

  data class ConfirmBackupImport(val strategy: ImportConflictStrategy) : HomeUiIntent

  data object DismissBackupImportPrompt : HomeUiIntent

  data object RefreshInspectStorageStats : HomeUiIntent

  data object RequestClearAllInspectData : HomeUiIntent

  data object ConfirmClearAllInspectData : HomeUiIntent

  data object DismissClearAllInspectData : HomeUiIntent

  data class SetTermsDialogVisible(val visible: Boolean) : HomeUiIntent

  data class SetPrivacyDialogVisible(val visible: Boolean) : HomeUiIntent

  data object SendFeedbackEmailClicked : HomeUiIntent

  data class SetAppBuildMetadata(val versionName: String, val versionCode: Long) : HomeUiIntent

  // MediaProjection consent lifecycle
  data object ConfirmMediaProjectionConsentDialog : HomeUiIntent

  data object DismissMediaProjectionConsentDialog : HomeUiIntent

  data object MediaProjectionDenied : HomeUiIntent
}

sealed interface HomeUiEffect {
  data class NavigateToPaywall(val sourceFeature: String?) : HomeUiEffect

  data class NavigateToScriptEditor(val scriptId: String?) : HomeUiEffect

  data class NavigateToPipelineEditor(val pipelineId: String?) : HomeUiEffect

  data class NavigateToScriptLog(val scriptId: String) : HomeUiEffect

  data class NavigateToPipelineLog(val pipelineId: String) : HomeUiEffect

  data class NavigateToInspectStudio(val inspectItemId: String) : HomeUiEffect

  data object NavigateToExternalAutomationGuide : HomeUiEffect

  data class RequestMediaProjectionPermission(val target: PendingMediaProjectionTarget) : HomeUiEffect

  data class LaunchOverlayAndMinimize(val target: PendingMediaProjectionTarget) : HomeUiEffect

  data object LaunchPhotoPicker : HomeUiEffect

  data class LaunchBackupExportPicker(val suggestedFileName: String) : HomeUiEffect

  data object LaunchBackupImportPicker : HomeUiEffect

  data class CopyToClipboard(val label: String, val text: String) : HomeUiEffect

  data class ApplyAppLocale(val languageTag: String) : HomeUiEffect

  data object OpenShizukuDownloadPage : HomeUiEffect

  data object OpenShizukuApp : HomeUiEffect

  data class LaunchFeedbackEmail(val subject: String, val body: String) : HomeUiEffect

  data class ShowSnackbar(val message: String) : HomeUiEffect
}

internal fun formatInspectStorageSize(bytes: Long): String {
  val safeBytes = bytes.coerceAtLeast(0L)
  return when {
    safeBytes < 1024L -> "$safeBytes B"
    safeBytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safeBytes / 1024.0)
    safeBytes < 1024L * 1024L * 1024L ->
      String.format(Locale.US, "%.1f MB", safeBytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.2f GB", safeBytes / (1024.0 * 1024.0 * 1024.0))
  }
}
