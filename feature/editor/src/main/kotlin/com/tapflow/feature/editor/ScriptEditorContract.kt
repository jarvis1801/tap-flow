package com.tapflow.feature.editor

import androidx.compose.runtime.Immutable
import com.tapflow.core.database.repository.InspectGroupWithItems
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import com.tapflow.core.service.overlay.GesturePickerTargetType

@Immutable
sealed interface OverlayPickPurpose {
  data object ActionCoordinates : OverlayPickPurpose

  data class ConditionColorLeaf(val leafId: String) : OverlayPickPurpose

  data class ConditionImageRoi(val leafId: String) : OverlayPickPurpose
}

@Immutable
sealed interface AppPickerTarget {
  data object ActionLaunchApp : AppPickerTarget

  data object ActionForceStopApp : AppPickerTarget

  data class ConditionAppMatch(val leafId: String) : AppPickerTarget
}

@Immutable
sealed interface InspectPickerTarget {
  data class ConditionColorMatch(val leafId: String) : InspectPickerTarget

  data class ConditionImageMatch(val leafId: String) : InspectPickerTarget
}

@Immutable
data class ActionEditorSession(
  val insertionLocation: ActionBranchLocation,
  val insertIndex: Int,
  val editingActionId: String?,
  val draftAction: ScriptAction,
  val selectedCategory: ActionCategoryGroup,
  val activePickerRequestId: String? = null,
  val activePickPurpose: OverlayPickPurpose? = null,
)

@Immutable
data class ScriptEditorUiState(
  val scriptId: String = "",
  val isExistingScript: Boolean = false,
  val isLoading: Boolean = true,
  val isSaving: Boolean = false,
  val isDirty: Boolean = false,
  val isPro: Boolean = false,
  val title: String = "New Script",
  val description: String = "",
  val tags: List<String> = emptyList(),
  val globalDelay: DelayConfig = DelayConfig.Fixed(150L),
  val variables: List<VariableDefinition> = emptyList(),
  val actions: List<ScriptAction> = emptyList(),
  val undoRecord: RemovedActionRecord? = null,
  val showUnsavedDialog: Boolean = false,
  val showGlobalDelaySheet: Boolean = false,
  val showVariablesSheet: Boolean = false,
  val actionEditorSession: ActionEditorSession? = null,
  val appPickerTarget: AppPickerTarget? = null,
  val installedApps: List<InstalledAppEntry> = emptyList(),
  val isLoadingApps: Boolean = false,
  val currentForegroundPackage: String? = null,
  val inspectPickerTarget: InspectPickerTarget? = null,
  val inspectGroups: List<InspectGroupWithItems> = emptyList(),
)

sealed interface ScriptEditorUiIntent {
  data class UpdateTitle(val title: String) : ScriptEditorUiIntent

  data class UpdateDescription(val description: String) : ScriptEditorUiIntent

  data class AddTag(val tag: String) : ScriptEditorUiIntent

  data class RemoveTag(val tag: String) : ScriptEditorUiIntent

  // Navigation & Save
  data object BackClicked : ScriptEditorUiIntent

  data object DismissUnsavedDialog : ScriptEditorUiIntent

  data object DiscardChangesClicked : ScriptEditorUiIntent

  data object SaveAndExitClicked : ScriptEditorUiIntent

  data object SaveScriptClicked : ScriptEditorUiIntent

  // Global Delay Sheet
  data class SetGlobalDelaySheetVisible(val visible: Boolean) : ScriptEditorUiIntent

  data class SaveGlobalDelay(val config: DelayConfig) : ScriptEditorUiIntent

  // Dynamic Variables Sheet
  data class SetVariablesSheetVisible(val visible: Boolean) : ScriptEditorUiIntent

  data class UpsertVariable(val variable: VariableDefinition, val originalKey: String?) :
    ScriptEditorUiIntent

  data class DeleteVariable(val key: String) : ScriptEditorUiIntent

  // Action Tree Operations
  data class ToggleActionExpanded(val actionId: String) : ScriptEditorUiIntent

  data class MoveActionUp(val location: ActionBranchLocation, val index: Int) : ScriptEditorUiIntent

  data class MoveActionDown(val location: ActionBranchLocation, val index: Int) : ScriptEditorUiIntent

  data class DeleteAction(val actionId: String) : ScriptEditorUiIntent

  data object UndoDeleteAction : ScriptEditorUiIntent

  data class OpenAddActionSheet(
    val location: ActionBranchLocation,
    val insertIndex: Int,
  ) : ScriptEditorUiIntent

  data class OpenEditActionSheet(val actionId: String) : ScriptEditorUiIntent

  // Action Editor BottomSheet
  data class SelectCatalogCategory(val category: ActionCategoryGroup) : ScriptEditorUiIntent

  data class SelectTemplateAction(val action: ScriptAction) : ScriptEditorUiIntent

  data class UpdateDraftAction(val action: ScriptAction) : ScriptEditorUiIntent

  data object CommitDraftAction : ScriptEditorUiIntent

  data object DismissActionSheet : ScriptEditorUiIntent

  // Overlay Picker (Mode 2)
  data class RequestOverlayPick(
    val purpose: OverlayPickPurpose,
    val targetType: GesturePickerTargetType,
  ) : ScriptEditorUiIntent

  // App Picker
  data class OpenAppPicker(val target: AppPickerTarget) : ScriptEditorUiIntent

  data class SelectInstalledApp(val packageName: String, val appLabel: String) :
    ScriptEditorUiIntent

  data object DismissAppPicker : ScriptEditorUiIntent

  // Inspect DAO Picker
  data class OpenInspectPicker(val target: InspectPickerTarget) : ScriptEditorUiIntent

  data class ImportInspectPixel(val item: InspectItem, val pixel: InspectPixel) :
    ScriptEditorUiIntent

  data class ImportInspectCrop(val crop: InspectCrop) : ScriptEditorUiIntent

  data object DismissInspectPicker : ScriptEditorUiIntent

  // Paywall Upsell
  data class RequestProUpgrade(val featureName: String) : ScriptEditorUiIntent
}

sealed interface ScriptEditorUiEffect {
  data object NavigateBack : ScriptEditorUiEffect

  data class NavigateToPaywall(val sourceFeature: String) : ScriptEditorUiEffect

  data class LaunchOverlayGesturePicker(
    val requestId: String,
    val targetType: GesturePickerTargetType,
  ) : ScriptEditorUiEffect

  data class ShowUndoSnackbar(val message: String) : ScriptEditorUiEffect

  data class ShowSnackbar(val message: String) : ScriptEditorUiEffect
}
