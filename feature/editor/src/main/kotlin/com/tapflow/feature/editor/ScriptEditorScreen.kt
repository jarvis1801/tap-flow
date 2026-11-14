package com.tapflow.feature.editor

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.service.overlay.OverlayLaunchIntents

private sealed interface EditorTreeRow {
  val key: String
  val depth: Int

  data class BranchLabel(
    override val key: String,
    override val depth: Int,
    val title: String,
  ) : EditorTreeRow

  data class InsertRow(
    override val key: String,
    override val depth: Int,
    val location: ActionBranchLocation,
    val insertIndex: Int,
    val label: String,
  ) : EditorTreeRow

  data class ActionRow(
    override val key: String,
    override val depth: Int,
    val stepLabel: String,
    val action: ScriptAction,
    val location: ActionBranchLocation,
    val indexInBranch: Int,
    val siblingCount: Int,
  ) : EditorTreeRow
}

@Composable
fun ScriptEditorScreen(
  onNavigateBack: () -> Unit,
  onNavigateToPaywall: (String) -> Unit,
  viewModel: ScriptEditorViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current

  BackHandler {
    viewModel.onIntent(ScriptEditorUiIntent.BackClicked)
  }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        ScriptEditorUiEffect.NavigateBack -> onNavigateBack()
        is ScriptEditorUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is ScriptEditorUiEffect.LaunchOverlayGesturePicker -> {
          val intent =
            OverlayLaunchIntents.createGesturePickerIntent(
              context = context,
              requestId = effect.requestId,
              targetType = effect.targetType,
            )
          ContextCompat.startForegroundService(context, intent)
          (context as? Activity)?.moveTaskToBack(true)
        }
        is ScriptEditorUiEffect.ShowUndoSnackbar -> {
          val result =
            snackbarHostState.showSnackbar(
              message = effect.message,
              actionLabel = "Undo",
              duration = SnackbarDuration.Short,
            )
          if (result == SnackbarResult.ActionPerformed) {
            viewModel.onIntent(ScriptEditorUiIntent.UndoDeleteAction)
          }
        }
        is ScriptEditorUiEffect.ShowSnackbar -> {
          snackbarHostState.showSnackbar(effect.message)
        }
      }
    }
  }

  ScriptEditorContent(
    state = state,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorContent(
  state: ScriptEditorUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  var tagDraft by remember { mutableStateOf("") }
  val treeRows = remember(state.actions) { flattenActionTree(state.actions) }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { onIntent(ScriptEditorUiIntent.BackClicked) },
            modifier = Modifier.testTag("script_editor_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Navigate back",
            )
          }
        },
        title = {
          Column {
            Text(
              text = state.title.ifBlank { "Untitled Script" },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                "Global Delay: ${ActionTreeUtils.formatDelayConfig(state.globalDelay)}" +
                  if (state.isDirty) " • Unsaved" else "",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { onIntent(ScriptEditorUiIntent.SetGlobalDelaySheetVisible(true)) },
            modifier = Modifier.testTag("global_delay_button"),
          ) {
            Icon(Icons.Filled.Timer, contentDescription = "Global post-action delay")
          }
          IconButton(
            onClick = { onIntent(ScriptEditorUiIntent.SetVariablesSheetVisible(true)) },
            modifier = Modifier.testTag("dynamic_variables_button"),
          ) {
            Icon(Icons.Filled.DataObject, contentDescription = "Dynamic script variables")
          }
          FilledTonalIconButton(
            onClick = { onIntent(ScriptEditorUiIntent.SaveScriptClicked) },
            enabled = !state.isSaving,
            modifier = Modifier.padding(end = 6.dp).testTag("save_script_button"),
          ) {
            Icon(Icons.Filled.Save, contentDescription = "Save script")
          }
        },
      )
    },
  ) { innerPadding ->
    if (state.isLoading) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    } else {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        LazyColumn(
          modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item(key = "script_metadata_card") {
            Card(
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedTextField(
                  value = state.title,
                  onValueChange = { onIntent(ScriptEditorUiIntent.UpdateTitle(it)) },
                  label = { Text("Script Title") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("script_title_input"),
                )
                OutlinedTextField(
                  value = state.description,
                  onValueChange = { onIntent(ScriptEditorUiIntent.UpdateDescription(it)) },
                  label = { Text("Description (Optional)") },
                  minLines = 1,
                  maxLines = 4,
                  modifier = Modifier.fillMaxWidth().testTag("script_description_input"),
                )
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it },
                    label = { Text("Add Tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                  Button(
                    onClick = {
                      if (tagDraft.isNotBlank()) {
                        onIntent(ScriptEditorUiIntent.AddTag(tagDraft))
                        tagDraft = ""
                      }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                  ) {
                    Text("Add")
                  }
                }
                if (state.tags.isNotEmpty()) {
                  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.tags.forEach { tag ->
                      InputChip(
                        selected = false,
                        onClick = { onIntent(ScriptEditorUiIntent.RemoveTag(tag)) },
                        label = { Text(tag) },
                        trailingIcon = {
                          Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove tag $tag",
                            modifier = Modifier.size(16.dp),
                          )
                        },
                      )
                    }
                  }
                }
              }
            }
          }

          items(treeRows, key = { it.key }) { row ->
            val indentDp = (row.depth * 18).dp
            when (row) {
              is EditorTreeRow.BranchLabel -> {
                Text(
                  text = row.title,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(start = indentDp, top = 4.dp),
                )
              }

              is EditorTreeRow.InsertRow -> {
                OutlinedButton(
                  onClick = {
                    onIntent(
                      ScriptEditorUiIntent.OpenAddActionSheet(
                        location = row.location,
                        insertIndex = row.insertIndex,
                      ),
                    )
                  },
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .padding(start = indentDp)
                      .heightIn(min = 44.dp)
                      .testTag(row.key),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(row.label)
                }
              }

              is EditorTreeRow.ActionRow -> {
                ActionTreeItemCard(
                  row = row,
                  globalDelay = state.globalDelay,
                  onIntent = onIntent,
                  modifier = Modifier.padding(start = indentDp),
                )
              }
            }
          }
        }
      }
    }
  }

  if (state.showUnsavedDialog) {
    AlertDialog(
      onDismissRequest = { onIntent(ScriptEditorUiIntent.DismissUnsavedDialog) },
      title = { Text("Unsaved Changes") },
      text = { Text("You have unsaved edits in this script. Would you like to save before leaving?") },
      confirmButton = {
        Button(
          onClick = { onIntent(ScriptEditorUiIntent.SaveAndExitClicked) },
          modifier = Modifier.testTag("unsaved_save_and_exit"),
        ) {
          Text("Save & Exit")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = { onIntent(ScriptEditorUiIntent.DiscardChangesClicked) },
            modifier = Modifier.testTag("unsaved_discard"),
          ) {
            Text("Discard Changes")
          }
          TextButton(onClick = { onIntent(ScriptEditorUiIntent.DismissUnsavedDialog) }) {
            Text("Keep Editing")
          }
        }
      },
    )
  }

  if (state.showGlobalDelaySheet) {
    GlobalDelayBottomSheet(
      initialConfig = state.globalDelay,
      isPro = state.isPro,
      onDismiss = { onIntent(ScriptEditorUiIntent.SetGlobalDelaySheetVisible(false)) },
      onSave = { onIntent(ScriptEditorUiIntent.SaveGlobalDelay(it)) },
      onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
    )
  }

  if (state.showVariablesSheet) {
    DynamicVariablesBottomSheet(
      variables = state.variables,
      onDismiss = { onIntent(ScriptEditorUiIntent.SetVariablesSheetVisible(false)) },
      onUpsert = { variable, originalKey ->
        onIntent(ScriptEditorUiIntent.UpsertVariable(variable, originalKey))
      },
      onDelete = { key -> onIntent(ScriptEditorUiIntent.DeleteVariable(key)) },
    )
  }

  state.actionEditorSession?.let { session ->
    ActionCatalogBottomSheet(
      session = session,
      globalDelay = state.globalDelay,
      variables = state.variables,
      isPro = state.isPro,
      onIntent = onIntent,
    )
  }

  if (state.appPickerTarget != null) {
    AppPickerBottomSheet(
      apps = state.installedApps,
      isLoading = state.isLoadingApps,
      currentForegroundPackage = state.currentForegroundPackage,
      onSelect = { pkg, label -> onIntent(ScriptEditorUiIntent.SelectInstalledApp(pkg, label)) },
      onDismiss = { onIntent(ScriptEditorUiIntent.DismissAppPicker) },
    )
  }

  state.inspectPickerTarget?.let { target ->
    InspectTargetPickerBottomSheet(
      inspectGroups = state.inspectGroups,
      isCropMode = target is InspectPickerTarget.ConditionImageMatch,
      onSelectPixel = { item, pixel ->
        onIntent(ScriptEditorUiIntent.ImportInspectPixel(item, pixel))
      },
      onSelectCrop = { crop -> onIntent(ScriptEditorUiIntent.ImportInspectCrop(crop)) },
      onDismiss = { onIntent(ScriptEditorUiIntent.DismissInspectPicker) },
    )
  }
}

@Composable
private fun ActionTreeItemCard(
  row: EditorTreeRow.ActionRow,
  globalDelay: DelayConfig,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val action = row.action
  val isContainer = action is ScriptAction.IfBlock || action is ScriptAction.LoopBlock
  val isExpanded =
    when (action) {
      is ScriptAction.IfBlock -> action.isExpanded
      is ScriptAction.LoopBlock -> action.isExpanded
      else -> false
    }
  val borderColor =
    when (action.categoryGroup()) {
      ActionCategoryGroup.TOUCH_GESTURES -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
      ActionCategoryGroup.FLOW_CONTROL -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
      ActionCategoryGroup.SYSTEM_HARDWARE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
      ActionCategoryGroup.VARIABLE_OPS -> MaterialTheme.colorScheme.outline
    }

  Card(
    border = BorderStroke(1.dp, borderColor),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = modifier.fillMaxWidth().testTag("action_card_${action.id}"),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = row.stepLabel,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = ActionTreeUtils.actionTypeTitle(action),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = ActionTreeUtils.summarizeAction(action),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (isContainer) {
          IconButton(
            onClick = { onIntent(ScriptEditorUiIntent.ToggleActionExpanded(action.id)) },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
              contentDescription = if (isExpanded) "Collapse branch" else "Expand branch",
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val delayBadgeText = formatPostDelayBadge(action, globalDelay)
        AssistChip(
          onClick = { onIntent(ScriptEditorUiIntent.OpenEditActionSheet(action.id)) },
          label = { Text(delayBadgeText, style = MaterialTheme.typography.labelSmall) },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = {
              onIntent(ScriptEditorUiIntent.MoveActionUp(row.location, row.indexInBranch))
            },
            enabled = row.indexInBranch > 0,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
          }
          IconButton(
            onClick = {
              onIntent(ScriptEditorUiIntent.MoveActionDown(row.location, row.indexInBranch))
            },
            enabled = row.indexInBranch < row.siblingCount - 1,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
          }
          IconButton(
            onClick = { onIntent(ScriptEditorUiIntent.OpenEditActionSheet(action.id)) },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit action")
          }
          IconButton(
            onClick = { onIntent(ScriptEditorUiIntent.DeleteAction(action.id)) },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Delete action",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

private fun formatPostDelayBadge(action: ScriptAction, globalDelay: DelayConfig): String {
  if (!action.supportsPostActionDelay()) return "Post Delay: 0ms"
  val postDelay =
    when (action) {
      is ScriptAction.Tap -> action.postDelay
      is ScriptAction.DoubleTap -> action.postDelay
      is ScriptAction.LongPress -> action.postDelay
      is ScriptAction.MultiTouch -> action.postDelay
      is ScriptAction.SwipeDrag -> action.postDelay
      is ScriptAction.InputText -> action.postDelay
      is ScriptAction.KeyEvent -> action.postDelay
      is ScriptAction.LaunchApp -> action.postDelay
      is ScriptAction.ForceStopApp -> action.postDelay
      is ScriptAction.ClipboardOp -> action.postDelay
      else -> PostActionDelay.Off
    }
  return when (postDelay) {
    PostActionDelay.Global -> "Post: Global (${ActionTreeUtils.formatDelayConfig(globalDelay)})"
    is PostActionDelay.Override -> "Post: ${ActionTreeUtils.formatDelayConfig(postDelay.config)}"
    PostActionDelay.Off -> "Post: Off (0ms)"
  }
}

private fun flattenActionTree(actions: List<ScriptAction>): List<EditorTreeRow> {
  val rows = mutableListOf<EditorTreeRow>()
  appendBranchRows(
    rows = rows,
    branchActions = actions,
    location = ActionBranchLocation(),
    depth = 0,
    prefix = "#",
    addLabel = "+ Add Action",
  )
  return rows
}

private fun appendBranchRows(
  rows: MutableList<EditorTreeRow>,
  branchActions: List<ScriptAction>,
  location: ActionBranchLocation,
  depth: Int,
  prefix: String,
  addLabel: String,
) {
  val branchKey = "${location.parentActionId ?: "root"}_${location.branchKind.name}"
  rows +=
    EditorTreeRow.InsertRow(
      key = "insert_${branchKey}_0",
      depth = depth,
      location = location,
      insertIndex = 0,
      label = addLabel,
    )

  branchActions.forEachIndexed { index, action ->
    val stepNum =
      if (depth == 0) {
        "#${(index + 1).toString().padStart(2, '0')}"
      } else {
        "$prefix${index + 1}"
      }
    rows +=
      EditorTreeRow.ActionRow(
        key = "action_${action.id}",
        depth = depth,
        stepLabel = stepNum,
        action = action,
        location = location,
        indexInBranch = index,
        siblingCount = branchActions.size,
      )

    when (action) {
      is ScriptAction.IfBlock -> {
        if (action.isExpanded) {
          rows +=
            EditorTreeRow.BranchLabel(
              key = "branch_then_${action.id}",
              depth = depth + 1,
              title = "Then Branch (${action.thenActions.size})",
            )
          appendBranchRows(
            rows = rows,
            branchActions = action.thenActions,
            location = ActionBranchLocation(action.id, BranchKind.IF_THEN),
            depth = depth + 1,
            prefix = "$stepNum.T",
            addLabel = "+ Add Then Action",
          )
          rows +=
            EditorTreeRow.BranchLabel(
              key = "branch_else_${action.id}",
              depth = depth + 1,
              title = "Else Branch (${action.elseActions.size})",
            )
          appendBranchRows(
            rows = rows,
            branchActions = action.elseActions,
            location = ActionBranchLocation(action.id, BranchKind.IF_ELSE),
            depth = depth + 1,
            prefix = "$stepNum.E",
            addLabel = "+ Add Else Action",
          )
        }
      }

      is ScriptAction.LoopBlock -> {
        if (action.isExpanded) {
          rows +=
            EditorTreeRow.BranchLabel(
              key = "branch_loop_${action.id}",
              depth = depth + 1,
              title = "Loop Body (${action.bodyActions.size})",
            )
          appendBranchRows(
            rows = rows,
            branchActions = action.bodyActions,
            location = ActionBranchLocation(action.id, BranchKind.LOOP_BODY),
            depth = depth + 1,
            prefix = "$stepNum.",
            addLabel = "+ Add Loop Action",
          )
        }
      }

      else -> Unit
    }

    rows +=
      EditorTreeRow.InsertRow(
        key = "insert_${branchKey}_${index + 1}",
        depth = depth,
        location = location,
        insertIndex = index + 1,
        label = addLabel,
      )
  }
}

@Preview(showBackground = true)
@Composable
private fun ScriptEditorPreview() {
  MaterialTheme {
    ScriptEditorContent(
      state =
        ScriptEditorUiState(
          scriptId = "demo",
          isLoading = false,
          isPro = true,
          title = "Daily Quest Auto",
          description = "Collects daily rewards and closes popups.",
          tags = listOf("RPG", "Farming"),
          actions =
            listOf(
              ScriptAction.Tap(
                id = "a1",
                x = NumericInput.Literal(540),
                y = NumericInput.Literal(1280),
              ),
              ScriptAction.IfBlock(
                id = "a2",
                name = "Popup Check",
                conditionRoot = ConditionGroup(id = "g1"),
                thenActions =
                  listOf(
                    ScriptAction.ClipboardOp(id = "a3", operation = ClipboardActionType.PASTE),
                  ),
              ),
              ScriptAction.LoopBlock(
                id = "a4",
                mode = LoopMode.Count(NumericInput.Literal(3)),
              ),
            ),
        ),
      snackbarHostState = remember { SnackbarHostState() },
      onIntent = {},
    )
  }
}
