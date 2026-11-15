package com.tapflow.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.repository.InspectRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.model.action.LongPressMode
import com.tapflow.core.model.action.MultiTouchMode
import com.tapflow.core.model.action.MultiTouchPoint
import com.tapflow.core.model.action.Point2D
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.SwipeTrajectoryMode
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.condition.ColorMatchMode
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionNode
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.RegionBounds
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.script.Script
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.overlay.GesturePickerBridge
import com.tapflow.core.service.overlay.PickedGestureResult
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
class ScriptEditorViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val scriptRepository: ScriptRepository,
  private val inspectRepository: InspectRepository,
  private val scriptAssetManager: ScriptAssetManager,
  private val entitlementRepository: EntitlementRepository,
  private val accessibilityManagerBridge: AccessibilityManagerBridge,
  private val gesturePickerBridge: GesturePickerBridge,
  private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

  private val argScriptId: String? = savedStateHandle["scriptId"]

  private val _uiState = MutableStateFlow(ScriptEditorUiState())
  val uiState: StateFlow<ScriptEditorUiState> = _uiState.asStateFlow()

  private val _effects = Channel<ScriptEditorUiEffect>(Channel.BUFFERED)
  val effects: Flow<ScriptEditorUiEffect> = _effects.receiveAsFlow()

  init {
    viewModelScope.launch {
      val loaded = argScriptId?.let { scriptRepository.getScriptById(it) }
      if (loaded != null) {
        _uiState.update {
          it.copy(
            scriptId = loaded.id,
            isExistingScript = true,
            isLoading = false,
            isDirty = false,
            title = loaded.title,
            description = loaded.description,
            tags = loaded.tags,
            globalDelay = loaded.globalDelay.clamped(),
            variables = loaded.variables,
            actions = loaded.actions,
          )
        }
      } else {
        _uiState.update {
          it.copy(
            scriptId = argScriptId ?: UUID.randomUUID().toString(),
            isExistingScript = false,
            isLoading = false,
            isDirty = false,
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

    viewModelScope.launch {
      inspectRepository.observeGroupsWithItems().collectLatest { groups ->
        _uiState.update { it.copy(inspectGroups = groups) }
      }
    }

    viewModelScope.launch {
      gesturePickerBridge.pickedResult.collect { result ->
        if (result == null) return@collect
        val session = _uiState.value.actionEditorSession ?: return@collect
        val reqId = session.activePickerRequestId ?: return@collect
        if (result.requestId != reqId) return@collect

        gesturePickerBridge.consumeResult(reqId)
        val purpose = session.activePickPurpose ?: OverlayPickPurpose.ActionCoordinates
        val updatedDraft = applyPickedGestureToDraft(session.draftAction, purpose, result)
        _uiState.update { state ->
          val activeSession = state.actionEditorSession ?: return@update state
          val nextErrors =
            if (activeSession.showValidationErrors) {
              ActionValidator.validate(updatedDraft, activeSession.editingElseIfBranchId)
            } else {
              activeSession.validationErrors
            }
          state.copy(
            actionEditorSession =
              activeSession.copy(
                draftAction = updatedDraft,
                activePickerRequestId = null,
                activePickPurpose = null,
                validationErrors = nextErrors,
              ),
          )
        }
        sendEffect(
          ScriptEditorUiEffect.ShowSnackbar(
            "Captured (${result.startX}, ${result.startY})",
          ),
        )
      }
    }
  }

  fun onIntent(intent: ScriptEditorUiIntent) {
    when (intent) {
      is ScriptEditorUiIntent.UpdateTitle -> {
        _uiState.update { it.copy(title = intent.title, isDirty = true) }
      }
      is ScriptEditorUiIntent.UpdateDescription -> {
        _uiState.update { it.copy(description = intent.description, isDirty = true) }
      }
      is ScriptEditorUiIntent.AddTag -> {
        val clean = intent.tag.trim()
        if (clean.isBlank()) return
        _uiState.update {
          if (clean in it.tags) it else it.copy(tags = it.tags + clean, isDirty = true)
        }
      }
      is ScriptEditorUiIntent.RemoveTag -> {
        _uiState.update { it.copy(tags = it.tags - intent.tag, isDirty = true) }
      }
      ScriptEditorUiIntent.BackClicked -> {
        if (_uiState.value.isDirty) {
          _uiState.update { it.copy(showUnsavedDialog = true) }
        } else {
          sendEffect(ScriptEditorUiEffect.NavigateBack)
        }
      }
      ScriptEditorUiIntent.DismissUnsavedDialog -> {
        _uiState.update { it.copy(showUnsavedDialog = false) }
      }
      ScriptEditorUiIntent.DiscardChangesClicked -> {
        _uiState.update { it.copy(showUnsavedDialog = false) }
        sendEffect(ScriptEditorUiEffect.NavigateBack)
      }
      ScriptEditorUiIntent.SaveAndExitClicked -> {
        _uiState.update { it.copy(showUnsavedDialog = false) }
        persistScript(popOnSuccess = true)
      }
      ScriptEditorUiIntent.SaveScriptClicked -> {
        persistScript(popOnSuccess = true)
      }

      // Global Delay
      is ScriptEditorUiIntent.SetGlobalDelaySheetVisible -> {
        _uiState.update { it.copy(showGlobalDelaySheet = intent.visible) }
      }
      is ScriptEditorUiIntent.SaveGlobalDelay -> {
        val clamped = intent.config.clamped()
        if (clamped !is DelayConfig.Fixed && !entitlementRepository.isPro()) {
          sendEffect(
            ScriptEditorUiEffect.NavigateToPaywall(
              ProFeatureGate.RANDOM_DEVIATION_DELAY.displayName,
            ),
          )
          return
        }
        _uiState.update {
          it.copy(
            globalDelay = clamped,
            showGlobalDelaySheet = false,
            isDirty = true,
          )
        }
      }
      is ScriptEditorUiIntent.ApplyGlobalDelayToAllActions -> {
        val clamped = intent.config.clamped()
        if (clamped !is DelayConfig.Fixed && !entitlementRepository.isPro()) {
          sendEffect(
            ScriptEditorUiEffect.NavigateToPaywall(
              ProFeatureGate.RANDOM_DEVIATION_DELAY.displayName,
            ),
          )
          return
        }
        _uiState.update { state ->
          state.copy(
            globalDelay = clamped,
            actions = ActionTreeUtils.applyGlobalDelayToAllPhysicalActions(state.actions),
            showGlobalDelaySheet = false,
            isDirty = true,
          )
        }
        sendEffect(
          ScriptEditorUiEffect.ShowSnackbar("Applied Global Delay to all physical actions"),
        )
      }

      // Dynamic Variables
      is ScriptEditorUiIntent.SetVariablesSheetVisible -> {
        if (intent.visible && !entitlementRepository.canUseFeature(ProFeatureGate.DYNAMIC_VARIABLES)) {
          sendEffect(
            ScriptEditorUiEffect.NavigateToPaywall(ProFeatureGate.DYNAMIC_VARIABLES.displayName),
          )
          return
        }
        _uiState.update { it.copy(showVariablesSheet = intent.visible) }
      }
      is ScriptEditorUiIntent.UpsertVariable -> {
        val keyClean = intent.variable.key.trim()
        if (keyClean.isBlank()) {
          sendEffect(ScriptEditorUiEffect.ShowSnackbar("Variable key cannot be empty"))
          return
        }
        val cleanVar = intent.variable.copy(key = keyClean)
        _uiState.update { state ->
          val filtered =
            state.variables.filterNot {
              it.key == keyClean || (intent.originalKey != null && it.key == intent.originalKey)
            }
          state.copy(variables = filtered + cleanVar, isDirty = true)
        }
      }
      is ScriptEditorUiIntent.DeleteVariable -> {
        _uiState.update { state ->
          state.copy(
            variables = state.variables.filterNot { it.key == intent.key },
            isDirty = true,
          )
        }
      }

      // Action Tree Mutations
      is ScriptEditorUiIntent.ToggleActionExpanded -> {
        _uiState.update { state ->
          state.copy(actions = ActionTreeUtils.toggleExpanded(state.actions, intent.actionId))
        }
      }
      is ScriptEditorUiIntent.MoveActionUp -> {
        _uiState.update { state ->
          state.copy(
            actions = ActionTreeUtils.moveSibling(state.actions, intent.location, intent.index, -1),
            isDirty = true,
          )
        }
      }
      is ScriptEditorUiIntent.MoveActionDown -> {
        _uiState.update { state ->
          state.copy(
            actions = ActionTreeUtils.moveSibling(state.actions, intent.location, intent.index, +1),
            isDirty = true,
          )
        }
      }
      is ScriptEditorUiIntent.DeleteAction -> {
        val (nextActions, removed) =
          ActionTreeUtils.deleteActionById(_uiState.value.actions, intent.actionId)
        if (removed != null) {
          _uiState.update {
            it.copy(
              actions = nextActions,
              undoRecord = removed,
              isDirty = true,
            )
          }
          val label = ActionTreeUtils.actionTypeTitle(removed.action)
          sendEffect(ScriptEditorUiEffect.ShowUndoSnackbar("Removed $label"))
        }
      }
      ScriptEditorUiIntent.UndoDeleteAction -> {
        val record = _uiState.value.undoRecord ?: return
        _uiState.update { state ->
          state.copy(
            actions =
              ActionTreeUtils.insertActionAt(
                state.actions,
                record.location,
                record.index,
                record.action,
              ),
            undoRecord = null,
            isDirty = true,
          )
        }
      }
      is ScriptEditorUiIntent.OpenAddActionSheet -> {
        val state = _uiState.value
        if (intent.location.branchKind == BranchKind.ROOT &&
          !state.isPro &&
          state.actions.size >= EntitlementValidator.FREE_MAX_ACTIONS_PER_SCRIPT
        ) {
          sendEffect(
            ScriptEditorUiEffect.NavigateToPaywall(
              "${ProFeatureGate.UNLIMITED_ACTIONS.displayName} (Free limit: ${EntitlementValidator.FREE_MAX_ACTIONS_PER_SCRIPT})",
            ),
          )
          return
        }
        val canAddNestedIf =
          ActionTreeUtils.ifNestingDepthForLocation(state.actions, intent.location) <
            ActionTreeUtils.MAX_IF_NESTING_DEPTH
        val defaultAction =
          ScriptAction.Tap(
            id = UUID.randomUUID().toString(),
            x = NumericInput.Literal(UNSET_COORD),
            y = NumericInput.Literal(UNSET_COORD),
          )
        _uiState.update {
          it.copy(
            actionEditorSession =
              ActionEditorSession(
                insertionLocation = intent.location,
                insertIndex = intent.insertIndex,
                editingActionId = null,
                draftAction = defaultAction,
                selectedCategory = ActionCategoryGroup.TOUCH_GESTURES,
                editingElseIfBranchId = null,
                canAddNestedIfBlock = canAddNestedIf,
              ),
          )
        }
      }
      is ScriptEditorUiIntent.OpenEditActionSheet -> {
        val existing =
          ActionTreeUtils.findActionById(_uiState.value.actions, intent.actionId) ?: return
        _uiState.update {
          it.copy(
            actionEditorSession =
              ActionEditorSession(
                insertionLocation = ActionBranchLocation(),
                insertIndex = 0,
                editingActionId = existing.id,
                draftAction = existing,
                selectedCategory = existing.categoryGroup(),
                editingElseIfBranchId = null,
                canAddNestedIfBlock = true,
              ),
          )
        }
      }
      is ScriptEditorUiIntent.AddElseIfBranch -> {
        val ifBlock =
          ActionTreeUtils.findActionById(_uiState.value.actions, intent.ifBlockId)
            as? ScriptAction.IfBlock ?: return
        if (!verifyProGateForAction(ifBlock)) return
        val newBranch =
          ScriptAction.ElseIfBranch(
            id = UUID.randomUUID().toString(),
            conditionRoot =
              ConditionGroup(id = UUID.randomUUID().toString(), name = "Otherwise, if..."),
          )
        val updatedIf =
          ifBlock.copy(
            isExpanded = true,
            elseIfBranches = ifBlock.elseIfBranches + newBranch,
          )
        _uiState.update { state ->
          state.copy(
            actions = ActionTreeUtils.replaceAction(state.actions, updatedIf),
            isDirty = true,
            actionEditorSession =
              ActionEditorSession(
                insertionLocation = ActionBranchLocation(),
                insertIndex = 0,
                editingActionId = updatedIf.id,
                draftAction = updatedIf,
                selectedCategory = ActionCategoryGroup.FLOW_CONTROL,
                editingElseIfBranchId = newBranch.id,
                canAddNestedIfBlock = true,
              ),
          )
        }
      }
      is ScriptEditorUiIntent.OpenEditElseIfConditionSheet -> {
        val ifBlock =
          ActionTreeUtils.findActionById(_uiState.value.actions, intent.ifBlockId)
            as? ScriptAction.IfBlock ?: return
        if (ifBlock.elseIfBranches.none { it.id == intent.branchId }) return
        _uiState.update {
          it.copy(
            actionEditorSession =
              ActionEditorSession(
                insertionLocation = ActionBranchLocation(),
                insertIndex = 0,
                editingActionId = ifBlock.id,
                draftAction = ifBlock,
                selectedCategory = ActionCategoryGroup.FLOW_CONTROL,
                editingElseIfBranchId = intent.branchId,
                canAddNestedIfBlock = true,
              ),
          )
        }
      }
      is ScriptEditorUiIntent.RemoveElseIfBranch -> {
        val ifBlock =
          ActionTreeUtils.findActionById(_uiState.value.actions, intent.ifBlockId)
            as? ScriptAction.IfBlock ?: return
        val updatedIf =
          ifBlock.copy(
            elseIfBranches = ifBlock.elseIfBranches.filterNot { it.id == intent.branchId },
          )
        _uiState.update { state ->
          state.copy(
            actions = ActionTreeUtils.replaceAction(state.actions, updatedIf),
            isDirty = true,
          )
        }
      }
      is ScriptEditorUiIntent.RevealElseBranch -> {
        val ifBlock =
          ActionTreeUtils.findActionById(_uiState.value.actions, intent.ifBlockId)
            as? ScriptAction.IfBlock ?: return
        val updatedIf = if (ifBlock.isExpanded) ifBlock else ifBlock.copy(isExpanded = true)
        _uiState.update { state ->
          state.copy(
            actions = ActionTreeUtils.replaceAction(state.actions, updatedIf),
            revealedElseBlockIds = state.revealedElseBlockIds + intent.ifBlockId,
          )
        }
      }
      is ScriptEditorUiIntent.RemoveElseBranch -> {
        val ifBlock =
          ActionTreeUtils.findActionById(_uiState.value.actions, intent.ifBlockId)
            as? ScriptAction.IfBlock ?: return
        val updatedIf = ifBlock.copy(elseActions = emptyList())
        _uiState.update { state ->
          state.copy(
            actions = ActionTreeUtils.replaceAction(state.actions, updatedIf),
            revealedElseBlockIds = state.revealedElseBlockIds - intent.ifBlockId,
            isDirty = ifBlock.elseActions.isNotEmpty() || state.isDirty,
          )
        }
      }

      // Action Sheet Edits
      is ScriptEditorUiIntent.SelectCatalogCategory -> {
        if (intent.category == ActionCategoryGroup.VARIABLE_OPS && !entitlementRepository.isPro()) {
          sendEffect(
            ScriptEditorUiEffect.NavigateToPaywall(ProFeatureGate.DYNAMIC_VARIABLES.displayName),
          )
          return
        }
        _uiState.update { state ->
          val session = state.actionEditorSession ?: return@update state
          state.copy(actionEditorSession = session.copy(selectedCategory = intent.category))
        }
      }
      is ScriptEditorUiIntent.SelectTemplateAction -> {
        val currentSession = _uiState.value.actionEditorSession ?: return
        if (currentSession.editingActionId == null &&
          intent.action is ScriptAction.IfBlock &&
          !currentSession.canAddNestedIfBlock
        ) {
          sendEffect(
            ScriptEditorUiEffect.ShowSnackbar(
              "Maximum ${ActionTreeUtils.MAX_IF_NESTING_DEPTH} nested If levels reached.",
            ),
          )
          return
        }
        if (!verifyProGateForAction(intent.action)) return
        _uiState.update { state ->
          val session = state.actionEditorSession ?: return@update state
          val preservedId = session.draftAction.id
          val withId = reassignActionId(intent.action, preservedId)
          state.copy(
            actionEditorSession =
              session.copy(
                draftAction = withId,
                selectedCategory = withId.categoryGroup(),
                validationErrors = emptyMap(),
                showValidationErrors = false,
              ),
          )
        }
      }
      is ScriptEditorUiIntent.UpdateDraftAction -> {
        _uiState.update { state ->
          val session = state.actionEditorSession ?: return@update state
          val nextErrors =
            if (session.showValidationErrors) {
              ActionValidator.validate(intent.action, session.editingElseIfBranchId)
            } else {
              session.validationErrors
            }
          state.copy(
            actionEditorSession =
              session.copy(
                draftAction = intent.action,
                validationErrors = nextErrors,
              ),
          )
        }
      }
      ScriptEditorUiIntent.CommitDraftAction -> {
        val session = _uiState.value.actionEditorSession ?: return
        if (session.editingActionId == null &&
          session.draftAction is ScriptAction.IfBlock &&
          !session.canAddNestedIfBlock
        ) {
          sendEffect(
            ScriptEditorUiEffect.ShowSnackbar(
              "Maximum ${ActionTreeUtils.MAX_IF_NESTING_DEPTH} nested If levels reached.",
            ),
          )
          return
        }
        if (!verifyProGateForAction(session.draftAction)) return
        val errors = ActionValidator.validate(session.draftAction, session.editingElseIfBranchId)
        if (errors.isNotEmpty()) {
          _uiState.update { state ->
            val current = state.actionEditorSession ?: return@update state
            state.copy(
              actionEditorSession =
                current.copy(
                  validationErrors = errors,
                  showValidationErrors = true,
                ),
            )
          }
          sendEffect(ScriptEditorUiEffect.ShowSnackbar(errors.values.first()))
          return
        }
        val nextActions =
          if (session.editingActionId != null) {
            ActionTreeUtils.replaceAction(_uiState.value.actions, session.draftAction)
          } else {
            ActionTreeUtils.insertActionAt(
              _uiState.value.actions,
              session.insertionLocation,
              session.insertIndex,
              session.draftAction,
            )
          }
        _uiState.update {
          it.copy(
            actions = nextActions,
            actionEditorSession = null,
            isDirty = true,
          )
        }
      }
      ScriptEditorUiIntent.DismissActionSheet -> {
        gesturePickerBridge.cancelPendingPick()
        _uiState.update { it.copy(actionEditorSession = null) }
      }

      // Overlay Picker
      is ScriptEditorUiIntent.RequestOverlayPick -> {
        val session = _uiState.value.actionEditorSession ?: return
        val requestId = UUID.randomUUID().toString()
        gesturePickerBridge.beginPick(requestId, intent.targetType)
        _uiState.update {
          it.copy(
            actionEditorSession =
              session.copy(
                activePickerRequestId = requestId,
                activePickPurpose = intent.purpose,
              ),
          )
        }
        sendEffect(
          ScriptEditorUiEffect.LaunchOverlayGesturePicker(
            requestId = requestId,
            targetType = intent.targetType,
          ),
        )
      }

      // App Picker
      is ScriptEditorUiIntent.OpenAppPicker -> {
        _uiState.update {
          it.copy(
            appPickerTarget = intent.target,
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
      is ScriptEditorUiIntent.SelectInstalledApp -> {
        val target = _uiState.value.appPickerTarget ?: return
        val session = _uiState.value.actionEditorSession ?: return
        val updatedDraft =
          when (target) {
            AppPickerTarget.ActionLaunchApp ->
              (session.draftAction as? ScriptAction.LaunchApp)?.copy(
                packageName = intent.packageName,
                appLabel = intent.appLabel,
              ) ?: session.draftAction
            AppPickerTarget.ActionForceStopApp ->
              (session.draftAction as? ScriptAction.ForceStopApp)?.copy(
                packageName = intent.packageName,
                appLabel = intent.appLabel,
              ) ?: session.draftAction
            is AppPickerTarget.ConditionAppMatch -> {
              updateConditionLeafInDraft(session.draftAction, target.leafId) { leaf ->
                leaf.copy(
                  predicate =
                    ConditionPredicate.AppMatch(
                      packageName = intent.packageName,
                      appLabel = intent.appLabel,
                    ),
                )
              }
            }
          }
        _uiState.update {
          val nextErrors =
            if (session.showValidationErrors) {
              ActionValidator.validate(updatedDraft, session.editingElseIfBranchId)
            } else {
              session.validationErrors
            }
          it.copy(
            appPickerTarget = null,
            actionEditorSession =
              session.copy(
                draftAction = updatedDraft,
                validationErrors = nextErrors,
              ),
          )
        }
      }
      ScriptEditorUiIntent.DismissAppPicker -> {
        _uiState.update { it.copy(appPickerTarget = null) }
      }

      // Inspect DAO Picker
      is ScriptEditorUiIntent.OpenInspectPicker -> {
        _uiState.update { it.copy(inspectPickerTarget = intent.target) }
      }
      is ScriptEditorUiIntent.ImportInspectPixel -> {
        val target =
          _uiState.value.inspectPickerTarget as? InspectPickerTarget.ConditionColorMatch
            ?: return
        val session = _uiState.value.actionEditorSession ?: return
        val updatedDraft =
          updateConditionLeafInDraft(session.draftAction, target.leafId) { leaf ->
            val current = leaf.predicate as? ConditionPredicate.ColorMatch
              ?: ConditionPredicate.ColorMatch()
            val importedTarget =
              ColorTarget(
                x = intent.pixel.x,
                y = intent.pixel.y,
                hexColor = intent.pixel.hexColor,
                tolerance = intent.pixel.tolerance,
              )
            val nextTargets =
              if (current.mode == ColorMatchMode.SINGLE_POINT) {
                listOf(importedTarget)
              } else {
                current.targets + importedTarget
              }
            leaf.copy(
              predicate =
                current.copy(
                  targets = nextTargets,
                  sourceInspectItemId = intent.item.id,
                ),
            )
          }
        _uiState.update {
          val nextErrors =
            if (session.showValidationErrors) {
              ActionValidator.validate(updatedDraft, session.editingElseIfBranchId)
            } else {
              session.validationErrors
            }
          it.copy(
            inspectPickerTarget = null,
            actionEditorSession =
              session.copy(
                draftAction = updatedDraft,
                validationErrors = nextErrors,
              ),
          )
        }
      }
      is ScriptEditorUiIntent.ImportInspectCrop -> {
        val target =
          _uiState.value.inspectPickerTarget as? InspectPickerTarget.ConditionImageMatch
            ?: return
        val activeScriptId = _uiState.value.scriptId.ifBlank { UUID.randomUUID().toString() }
        viewModelScope.launch {
          runCatching {
            scriptAssetManager.exportCropRegionToScriptAssets(
              fullScreenPngPath = intent.item.fullScreenPngPath,
              crop = intent.crop,
              scriptId = activeScriptId,
            )
          }.onSuccess { copiedPath ->
            val session = _uiState.value.actionEditorSession ?: return@onSuccess
            val updatedDraft =
              updateConditionLeafInDraft(session.draftAction, target.leafId) { leaf ->
                val current =
                  leaf.predicate as? ConditionPredicate.ImageMatch
                    ?: ConditionPredicate.ImageMatch(templatePngPath = copiedPath)
                leaf.copy(
                  predicate =
                    current.copy(
                      templatePngPath = copiedPath,
                      similarityThreshold = intent.crop.similarityThreshold,
                      sourceInspectCropId = intent.crop.id,
                    ),
                )
              }
            _uiState.update {
              val nextErrors =
                if (session.showValidationErrors) {
                  ActionValidator.validate(updatedDraft, session.editingElseIfBranchId)
                } else {
                  session.validationErrors
                }
              it.copy(
                inspectPickerTarget = null,
                actionEditorSession =
                  session.copy(
                    draftAction = updatedDraft,
                    validationErrors = nextErrors,
                  ),
              )
            }
            sendEffect(
              ScriptEditorUiEffect.ShowSnackbar("Imported lossless crop into Script assets"),
            )
          }.onFailure { err ->
            _uiState.update { it.copy(inspectPickerTarget = null) }
            sendEffect(
              ScriptEditorUiEffect.ShowSnackbar(err.message ?: "Failed to copy crop PNG"),
            )
          }
        }
      }
      ScriptEditorUiIntent.DismissInspectPicker -> {
        _uiState.update { it.copy(inspectPickerTarget = null) }
      }

      is ScriptEditorUiIntent.RequestProUpgrade -> {
        sendEffect(ScriptEditorUiEffect.NavigateToPaywall(intent.featureName))
      }
    }
  }

  private fun persistScript(popOnSuccess: Boolean) {
    val state = _uiState.value
    if (state.isSaving) return
    val cleanTitle = state.title.trim()
    if (cleanTitle.isBlank()) {
      sendEffect(ScriptEditorUiEffect.ShowSnackbar("Script title cannot be empty"))
      return
    }
    _uiState.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val existing = if (state.isExistingScript) scriptRepository.getScriptById(state.scriptId) else null
      val toSave =
        Script(
          id = state.scriptId.ifBlank { UUID.randomUUID().toString() },
          title = cleanTitle,
          description = state.description.trim(),
          tags = state.tags,
          globalDelay = state.globalDelay.clamped(),
          variables = state.variables,
          actions = state.actions,
          createdAtMs = existing?.createdAtMs ?: now,
          updatedAtMs = now,
        )
      scriptRepository.saveScript(toSave)
      val allScripts = scriptRepository.getAllScripts()
      scriptAssetManager.cleanupUnreferencedScriptTemplates(allScripts)
      _uiState.update { it.copy(isSaving = false, isDirty = false, isExistingScript = true) }
      if (popOnSuccess) {
        sendEffect(ScriptEditorUiEffect.NavigateBack)
      }
    }
  }

  private fun verifyProGateForAction(action: ScriptAction): Boolean {
    if (entitlementRepository.isPro()) return true
    val blocker: ProFeatureGate? =
      when (action) {
        is ScriptAction.MultiTouch -> ProFeatureGate.MULTI_TOUCH
        is ScriptAction.IfBlock -> ProFeatureGate.IF_BLOCK
        is ScriptAction.ForceStopApp -> ProFeatureGate.FORCE_STOP_APP
        is ScriptAction.VariableOp -> ProFeatureGate.DYNAMIC_VARIABLES
        is ScriptAction.LongPress ->
          if (action.mode != LongPressMode.STANDARD) ProFeatureGate.HOLD_GESTURE_MODES else null
        is ScriptAction.SwipeDrag ->
          when (action.trajectoryMode) {
            SwipeTrajectoryMode.BEZIER_CURVE -> ProFeatureGate.BEZIER_TRAJECTORY
            SwipeTrajectoryMode.MULTI_POINT_PATH -> ProFeatureGate.MULTI_POINT_SWIPE
            SwipeTrajectoryMode.LINEAR -> null
          }
        else -> null
      }
    if (blocker != null) {
      sendEffect(ScriptEditorUiEffect.NavigateToPaywall(blocker.displayName))
      return false
    }
    return true
  }

  private fun applyPickedGestureToDraft(
    action: ScriptAction,
    purpose: OverlayPickPurpose,
    result: PickedGestureResult,
  ): ScriptAction {
    return when (purpose) {
      OverlayPickPurpose.ActionCoordinates -> {
        when (action) {
          is ScriptAction.Tap ->
            action.copy(
              x = NumericInput.Literal(result.startX),
              y = NumericInput.Literal(result.startY),
            )
          is ScriptAction.DoubleTap ->
            action.copy(
              x = NumericInput.Literal(result.startX),
              y = NumericInput.Literal(result.startY),
            )
          is ScriptAction.LongPress ->
            action.copy(
              x = NumericInput.Literal(result.startX),
              y = NumericInput.Literal(result.startY),
              pressDurationMs = result.durationMs.coerceAtLeast(350L),
            )
          is ScriptAction.MultiTouch ->
            if (action.mode == MultiTouchMode.CUSTOM_POINTS) {
              action.copy(
                customPoints =
                  action.customPoints +
                    MultiTouchPoint(x = result.startX, y = result.startY),
              )
            } else {
              action.copy(centerX = result.startX, centerY = result.startY)
            }
          is ScriptAction.SwipeDrag -> {
            val points =
              if (result.pathPoints.size >= 2) {
                result.pathPoints
              } else {
                listOf(Point2D(result.startX, result.startY), Point2D(result.endX, result.endY))
              }
            action.copy(
              startX = NumericInput.Literal(result.startX),
              startY = NumericInput.Literal(result.startY),
              endX = NumericInput.Literal(result.endX),
              endY = NumericInput.Literal(result.endY),
              durationMs = result.durationMs.coerceAtLeast(100L),
              multiPointPath = points,
            )
          }
          is ScriptAction.InputText ->
            action.copy(
              tapToFocus = true,
              focusX = result.startX,
              focusY = result.startY,
            )
          else -> action
        }
      }
      is OverlayPickPurpose.ConditionColorLeaf -> {
        updateConditionLeafInDraft(action, purpose.leafId) { leaf ->
          val current =
            leaf.predicate as? ConditionPredicate.ColorMatch ?: ConditionPredicate.ColorMatch()
          if (current.mode == ColorMatchMode.REGION) {
            val minX = minOf(result.startX, result.endX)
            val minY = minOf(result.startY, result.endY)
            val maxX = maxOf(result.startX, result.endX).coerceAtLeast(minX + 10)
            val maxY = maxOf(result.startY, result.endY).coerceAtLeast(minY + 10)
            leaf.copy(
              predicate = current.copy(region = RegionBounds(minX, minY, maxX, maxY)),
            )
          } else {
            val nextTarget =
              ColorTarget(
                x = result.startX,
                y = result.startY,
                hexColor = current.targets.firstOrNull()?.hexColor ?: "",
              )
            val nextList =
              if (current.mode == ColorMatchMode.SINGLE_POINT) {
                listOf(nextTarget)
              } else {
                current.targets + nextTarget
              }
            leaf.copy(predicate = current.copy(targets = nextList))
          }
        }
      }
      is OverlayPickPurpose.ConditionImageRoi -> {
        updateConditionLeafInDraft(action, purpose.leafId) { leaf ->
          val current = leaf.predicate as? ConditionPredicate.ImageMatch ?: return@updateConditionLeafInDraft leaf
          val minX = minOf(result.startX, result.endX)
          val minY = minOf(result.startY, result.endY)
          val maxX = maxOf(result.startX, result.endX).coerceAtLeast(minX + 16)
          val maxY = maxOf(result.startY, result.endY).coerceAtLeast(minY + 16)
          leaf.copy(predicate = current.copy(searchRoi = RegionBounds(minX, minY, maxX, maxY)))
        }
      }
    }
  }

  private fun updateConditionLeafInDraft(
    action: ScriptAction,
    leafId: String,
    transform: (ConditionLeaf) -> ConditionLeaf,
  ): ScriptAction {
    return when (action) {
      is ScriptAction.IfBlock ->
        action.copy(
          conditionRoot = replaceLeafRecursive(action.conditionRoot, leafId, transform),
          elseIfBranches =
            action.elseIfBranches.map { branch ->
              branch.copy(
                conditionRoot = replaceLeafRecursive(branch.conditionRoot, leafId, transform),
              )
            },
        )
      is ScriptAction.LoopBlock ->
        when (val m = action.mode) {
          is com.tapflow.core.model.action.LoopMode.WhileCondition ->
            action.copy(mode = m.copy(conditionRoot = replaceLeafRecursive(m.conditionRoot, leafId, transform)))
          is com.tapflow.core.model.action.LoopMode.UntilCondition ->
            action.copy(mode = m.copy(conditionRoot = replaceLeafRecursive(m.conditionRoot, leafId, transform)))
          else -> action
        }
      is ScriptAction.LongPress ->
        action.holdUntilCondition?.let { cond ->
          action.copy(holdUntilCondition = replaceLeafRecursive(cond, leafId, transform))
        } ?: action
      else -> action
    }
  }

  private fun replaceLeafRecursive(
    group: ConditionGroup,
    leafId: String,
    transform: (ConditionLeaf) -> ConditionLeaf,
  ): ConditionGroup {
    return group.copy(
      children =
        group.children.map { node ->
          when (node) {
            is ConditionLeaf -> if (node.id == leafId) transform(node) else node
            is ConditionGroup -> replaceLeafRecursive(node, leafId, transform)
          }
        },
    )
  }

  private fun reassignActionId(action: ScriptAction, id: String): ScriptAction =
    when (action) {
      is ScriptAction.Tap -> action.copy(id = id)
      is ScriptAction.DoubleTap -> action.copy(id = id)
      is ScriptAction.LongPress -> action.copy(id = id)
      is ScriptAction.MultiTouch -> action.copy(id = id)
      is ScriptAction.SwipeDrag -> action.copy(id = id)
      is ScriptAction.InputText -> action.copy(id = id)
      is ScriptAction.IfBlock -> action.copy(id = id)
      is ScriptAction.LoopBlock -> action.copy(id = id)
      is ScriptAction.Delay -> action.copy(id = id)
      is ScriptAction.Break -> action.copy(id = id)
      is ScriptAction.Continue -> action.copy(id = id)
      is ScriptAction.EndScript -> action.copy(id = id)
      is ScriptAction.KeyEvent -> action.copy(id = id)
      is ScriptAction.LaunchApp -> action.copy(id = id)
      is ScriptAction.ForceStopApp -> action.copy(id = id)
      is ScriptAction.ClipboardOp -> action.copy(id = id)
      is ScriptAction.Vibrate -> action.copy(id = id)
      is ScriptAction.PlayAlertSound -> action.copy(id = id)
      is ScriptAction.SetVariable -> action.copy(id = id)
      is ScriptAction.MathOp -> action.copy(id = id)
      is ScriptAction.RandomOp -> action.copy(id = id)
      is ScriptAction.StringOp -> action.copy(id = id)
      is ScriptAction.BooleanOp -> action.copy(id = id)
      is ScriptAction.TypeCastOp -> action.copy(id = id)
    }

  private fun sendEffect(effect: ScriptEditorUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }
}
