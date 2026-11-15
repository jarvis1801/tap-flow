package com.tapflow.feature.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.service.overlay.OverlayLaunchIntents
import kotlinx.coroutines.launch

@Composable
fun ScriptEditorScreen(
  onNavigateBack: () -> Unit,
  onNavigateToPaywall: (String) -> Unit,
  onNavigateToFlowDiagram: (String) -> Unit = {},
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
        ScriptEditorUiEffect.NavigateBack -> {
          snackbarHostState.currentSnackbarData?.dismiss()
          onNavigateBack()
        }
        is ScriptEditorUiEffect.NavigateToFlowDiagram -> onNavigateToFlowDiagram(effect.scriptId)
        is ScriptEditorUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is ScriptEditorUiEffect.LaunchOverlayGesturePicker -> {
          val intent =
            OverlayLaunchIntents.createGesturePickerIntent(
              context = context,
              requestId = effect.requestId,
              targetType = effect.targetType,
            )
          ContextCompat.startForegroundService(context, intent)
          context.findActivity()?.moveTaskToBack(true)
        }
        is ScriptEditorUiEffect.ShowUndoSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
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
        }
        is ScriptEditorUiEffect.ShowSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(effect.message)
          }
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

@Composable
fun ScriptEditorContent(
  state: ScriptEditorUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  var showTagsDialog by remember { mutableStateOf(false) }
  var tagDraft by remember { mutableStateOf("") }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      ScriptEditorTopHeaderBar(
        state = state,
        onIntent = onIntent,
        onOpenTagsDialog = { showTagsDialog = true },
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
        modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
        contentAlignment = Alignment.TopCenter,
      ) {
        LazyColumn(
          modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (state.actions.isEmpty()) {
            item(key = "empty_actions_state") {
              EmptyActionsCard(
                onAddFirst = {
                  onIntent(
                    ScriptEditorUiIntent.OpenAddActionSheet(
                      location = ActionBranchLocation(),
                      insertIndex = 0,
                    ),
                  )
                },
              )
            }
          } else {
            item(key = "insert_root_head") {
              InlineInsertDivider(
                label = "Insert",
                onClick = {
                  onIntent(
                    ScriptEditorUiIntent.OpenAddActionSheet(
                      location = ActionBranchLocation(),
                      insertIndex = 0,
                    ),
                  )
                },
                testTag = "insert_root_ROOT_0",
              )
            }

            itemsIndexed(state.actions, key = { _, action -> "root_action_${action.id}" }) { index, action ->
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTreeItemCard(
                  action = action,
                  stepLabel = "#${(index + 1).toString().padStart(2, '0')}",
                  location = ActionBranchLocation(),
                  indexInBranch = index,
                  siblingCount = state.actions.size,
                  nestingDepth = 0,
                  globalDelay = state.globalDelay,
                  revealedElseBlockIds = state.revealedElseBlockIds,
                  onIntent = onIntent,
                )

                if (index < state.actions.lastIndex) {
                  InlineInsertDivider(
                    label = "Insert",
                    onClick = {
                      onIntent(
                        ScriptEditorUiIntent.OpenAddActionSheet(
                          location = ActionBranchLocation(),
                          insertIndex = index + 1,
                        ),
                      )
                    },
                    testTag = "insert_root_ROOT_${index + 1}",
                  )
                }
              }
            }

            item(key = "insert_root_tail") {
              FilledTonalButton(
                onClick = {
                  onIntent(
                    ScriptEditorUiIntent.OpenAddActionSheet(
                      location = ActionBranchLocation(),
                      insertIndex = state.actions.size,
                    ),
                  )
                },
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("insert_root_ROOT_${state.actions.size}"),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Action")
              }
            }
          }

          item(key = "bottom_scroll_spacer") {
            Spacer(modifier = Modifier.height(88.dp))
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

  if (showTagsDialog) {
    AlertDialog(
      onDismissRequest = { showTagsDialog = false },
      title = { Text("Script Tags") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = tagDraft,
              onValueChange = { tagDraft = it },
              label = { Text("New Tag") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
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
          if (state.tags.isEmpty()) {
            Text(
              text = "No tags yet. Tags help filter scripts in your library.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              state.tags.forEach { tag ->
                InputChip(
                  selected = false,
                  onClick = { onIntent(ScriptEditorUiIntent.RemoveTag(tag)) },
                  label = { Text(tag) },
                  trailingIcon = {
                    Icon(
                      imageVector = Icons.Rounded.Close,
                      contentDescription = "Remove tag $tag",
                      modifier = Modifier.size(16.dp),
                    )
                  },
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showTagsDialog = false }) {
          Text("Done")
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
      onApplyToAll = { onIntent(ScriptEditorUiIntent.ApplyGlobalDelayToAllActions(it)) },
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
      onSelectCrop = { item, crop -> onIntent(ScriptEditorUiIntent.ImportInspectCrop(item, crop)) },
      onDismiss = { onIntent(ScriptEditorUiIntent.DismissInspectPicker) },
    )
  }
}

@Composable
private fun ScriptEditorTopHeaderBar(
  state: ScriptEditorUiState,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  onOpenTagsDialog: () -> Unit,
) {
  var descriptionFocused by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
      ) {
        Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
          // Row 1: Back Navigation, Borderless Editable Title, Save Button
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = { onIntent(ScriptEditorUiIntent.BackClicked) },
              modifier = Modifier.size(48.dp).testTag("script_editor_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate back",
              )
            }

            Box(
              modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              BasicTextField(
                value = state.title,
                onValueChange = { onIntent(ScriptEditorUiIntent.UpdateTitle(it)) },
                singleLine = true,
                textStyle =
                  MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag("script_title_input"),
                decorationBox = { innerTextField ->
                  if (state.title.isEmpty()) {
                    Text(
                      text = "Untitled Script...",
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  innerTextField()
                },
              )
            }

            if (state.isDirty) {
              Box(
                modifier =
                  Modifier
                    .padding(end = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
              )
            }

            OutlinedButton(
              onClick = { onIntent(ScriptEditorUiIntent.OpenFlowDiagramClicked) },
              enabled = !state.isLoading,
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
              modifier = Modifier.heightIn(min = 40.dp).testTag("open_flow_diagram_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(stringResource(R.string.flow_button_label))
            }

            Spacer(modifier = Modifier.width(6.dp))

            FilledTonalButton(
              onClick = { onIntent(ScriptEditorUiIntent.SaveScriptClicked) },
              enabled = !state.isSaving,
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
              modifier = Modifier.heightIn(min = 40.dp).testTag("save_script_button"),
            ) {
              Text(if (state.isSaving) "Saving…" else "Save")
            }
          }

          // Row 2: Expandable Borderless Description + Text-Labeled Quick Configuration Pills
          Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier = Modifier.fillMaxWidth().animateContentSize(),
              contentAlignment = Alignment.CenterStart,
            ) {
              BasicTextField(
                value = state.description,
                onValueChange = { onIntent(ScriptEditorUiIntent.UpdateDescription(it)) },
                singleLine = !descriptionFocused,
                maxLines = if (descriptionFocused) 3 else 1,
                textStyle =
                  MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .onFocusChanged { descriptionFocused = it.isFocused }
                    .testTag("script_description_input"),
                decorationBox = { innerTextField ->
                  if (state.description.isEmpty()) {
                    Text(
                      text = "Add description...",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  innerTextField()
                },
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AssistChip(
                onClick = { onIntent(ScriptEditorUiIntent.SetGlobalDelaySheetVisible(true)) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    text = "Default Wait: ${ActionTreeUtils.formatDelayConfig(state.globalDelay)}",
                    maxLines = 1,
                  )
                },
                modifier = Modifier.testTag("global_delay_button"),
              )

              AssistChip(
                onClick = { onIntent(ScriptEditorUiIntent.SetVariablesSheetVisible(true)) },
                leadingIcon = {
                  Icon(
                    imageVector = if (!state.isPro) Icons.Rounded.Lock else Icons.Rounded.DataObject,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    text = "Variables (${state.variables.size})",
                    maxLines = 1,
                  )
                },
                modifier = Modifier.testTag("dynamic_variables_button"),
              )

              AssistChip(
                onClick = onOpenTagsDialog,
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    text = if (state.tags.isEmpty()) "Tags" else "Tags (${state.tags.size})",
                    maxLines = 1,
                  )
                },
                modifier = Modifier.testTag("script_tags_button"),
              )
            }
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
  }
}

@Composable
private fun EmptyActionsCard(onAddFirst: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      ActionIconBadge(
        icon = Icons.Rounded.TouchApp,
        category = ActionCategoryGroup.TOUCH_GESTURES,
        boxSize = 56.dp,
        iconSize = 28.dp,
        cornerRadius = 16.dp,
      )
      Text(
        text = "No steps yet",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "Build an automation flow with taps, swipes, smart checks, loops, or app actions.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(4.dp))
      FilledTonalButton(
        onClick = onAddFirst,
        modifier = Modifier.heightIn(min = 48.dp).testTag("insert_root_ROOT_0"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add First Step")
      }
    }
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

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
