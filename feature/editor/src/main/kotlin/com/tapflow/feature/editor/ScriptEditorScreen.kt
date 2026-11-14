package com.tapflow.feature.editor

import android.app.Activity
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.text.font.FontFamily
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
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionValidator
import com.tapflow.core.service.overlay.OverlayLaunchIntents

private sealed interface EditorTreeRow {
  val key: String
  val depth: Int

  data class BranchLabel(
    override val key: String,
    override val depth: Int,
    val title: String,
    val branchKind: BranchKind,
  ) : EditorTreeRow

  data class InsertRow(
    override val key: String,
    override val depth: Int,
    val location: ActionBranchLocation,
    val insertIndex: Int,
    val label: String,
    val isTail: Boolean,
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

@Composable
fun ScriptEditorContent(
  state: ScriptEditorUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  var showTagsDialog by remember { mutableStateOf(false) }
  var tagDraft by remember { mutableStateOf("") }
  val treeRows = remember(state.actions) { flattenActionTree(state.actions) }

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
            items(treeRows, key = { it.key }) { row ->
              DepthIndentedRow(depth = row.depth) {
                when (row) {
                  is EditorTreeRow.BranchLabel -> {
                    val containerColor =
                      when (row.branchKind) {
                        BranchKind.IF_THEN -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        BranchKind.IF_ELSE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        BranchKind.LOOP_BODY -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        BranchKind.ROOT -> MaterialTheme.colorScheme.surfaceContainerHigh
                      }
                    val contentColor =
                      when (row.branchKind) {
                        BranchKind.IF_THEN -> MaterialTheme.colorScheme.onPrimaryContainer
                        BranchKind.IF_ELSE -> MaterialTheme.colorScheme.onTertiaryContainer
                        BranchKind.LOOP_BODY -> MaterialTheme.colorScheme.onSecondaryContainer
                        BranchKind.ROOT -> MaterialTheme.colorScheme.onSurface
                      }
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = containerColor,
                      modifier = Modifier.padding(top = 4.dp),
                    ) {
                      Text(
                        text = row.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      )
                    }
                  }

                  is EditorTreeRow.InsertRow -> {
                    val onInsert = {
                      onIntent(
                        ScriptEditorUiIntent.OpenAddActionSheet(
                          location = row.location,
                          insertIndex = row.insertIndex,
                        ),
                      )
                    }
                    if (row.isTail) {
                      FilledTonalButton(
                        onClick = onInsert,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag(row.key),
                      ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(row.label)
                      }
                    } else {
                      InlineInsertDivider(
                        label = "Insert",
                        onClick = onInsert,
                        testTag = row.key,
                      )
                    }
                  }

                  is EditorTreeRow.ActionRow -> {
                    ActionTreeItemCard(
                      row = row,
                      globalDelay = state.globalDelay,
                      onIntent = onIntent,
                    )
                  }
                }
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
      onSelectCrop = { crop -> onIntent(ScriptEditorUiIntent.ImportInspectCrop(crop)) },
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
                    .padding(end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
              )
            }

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
                    text = "Global Delay: ${ActionTreeUtils.formatDelayConfig(state.globalDelay)}",
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
        text = "No actions yet",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "Build an automation flow with taps, swipes, conditional logic, loops, or app actions.",
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
        Text("Add Action")
      }
    }
  }
}

@Composable
private fun DepthIndentedRow(
  depth: Int,
  content: @Composable () -> Unit,
) {
  if (depth <= 0) {
    content()
    return
  }
  val railColor =
    when (depth) {
      1 -> MaterialTheme.colorScheme.primary
      2 -> MaterialTheme.colorScheme.tertiary
      else -> MaterialTheme.colorScheme.secondary
    }
  Row(
    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (depth > 1) {
      Spacer(modifier = Modifier.width(((depth - 1) * 14).dp))
    }
    Box(
      modifier =
        Modifier
          .width(3.dp)
          .fillMaxHeight()
          .padding(vertical = 2.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(railColor.copy(alpha = 0.7f)),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Box(modifier = Modifier.weight(1f)) {
      content()
    }
  }
}

@Composable
private fun InlineInsertDivider(
  label: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
    Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier =
        Modifier
          .clip(RoundedCornerShape(999.dp))
          .clickable(onClick = onClick)
          .testTag(testTag),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
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
  var menuExpanded by remember(action.id) { mutableStateOf(false) }

  val containerColor =
    if (isContainer) {
      MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
      MaterialTheme.colorScheme.surfaceContainerLow
    }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = containerColor,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .clickable { onIntent(ScriptEditorUiIntent.OpenEditActionSheet(action.id)) }
        .testTag("action_card_${action.id}"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          ActionIconBadge(
            icon = action.actionIcon(),
            category = action.categoryGroup(),
            boxSize = 40.dp,
            iconSize = 20.dp,
            cornerRadius = 12.dp,
          )
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
          ) {
            Text(
              text = row.stepLabel,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = ActionTreeUtils.actionTypeTitle(action),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = ActionTreeUtils.summarizeAction(action),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (action.supportsPostActionDelay()) {
            val postDelay = extractActionPostDelay(action)
            val isCustomOverride = postDelay is PostActionDelay.Override
            val delayPillText = formatPostDelayBadge(postDelay, globalDelay)
            Surface(
              shape = RoundedCornerShape(6.dp),
              color =
                if (isCustomOverride) {
                  MaterialTheme.colorScheme.secondaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {
              Text(
                text = delayPillText,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isCustomOverride) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isContainer) {
            IconButton(
              onClick = { onIntent(ScriptEditorUiIntent.ToggleActionExpanded(action.id)) },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Collapse branch" else "Expand branch",
              )
            }
          }

          Box {
            IconButton(
              onClick = { menuExpanded = true },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Action options",
              )
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false },
            ) {
              DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onIntent(ScriptEditorUiIntent.OpenEditActionSheet(action.id))
                },
              )
              DropdownMenuItem(
                text = { Text("Move Up") },
                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                enabled = row.indexInBranch > 0,
                onClick = {
                  menuExpanded = false
                  onIntent(ScriptEditorUiIntent.MoveActionUp(row.location, row.indexInBranch))
                },
              )
              DropdownMenuItem(
                text = { Text("Move Down") },
                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                enabled = row.indexInBranch < row.siblingCount - 1,
                onClick = {
                  menuExpanded = false
                  onIntent(ScriptEditorUiIntent.MoveActionDown(row.location, row.indexInBranch))
                },
              )
              HorizontalDivider()
              DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                colors =
                  MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                  ),
                onClick = {
                  menuExpanded = false
                  onIntent(ScriptEditorUiIntent.DeleteAction(action.id))
                },
              )
            }
          }
        }
      }

      if (action is ScriptAction.IfBlock) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = ConditionValidator.formatNaturalLanguagePreview(action.conditionRoot),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
    }
  }
}

private fun extractActionPostDelay(action: ScriptAction): PostActionDelay =
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

private fun formatPostDelayBadge(postDelay: PostActionDelay, globalDelay: DelayConfig): String =
  when (postDelay) {
    PostActionDelay.Global -> "Global (${ActionTreeUtils.formatDelayConfig(globalDelay)})"
    is PostActionDelay.Override -> "Custom: ${ActionTreeUtils.formatDelayConfig(postDelay.config)}"
    PostActionDelay.Off -> "Off (0ms)"
  }

private fun flattenActionTree(actions: List<ScriptAction>): List<EditorTreeRow> {
  val rows = mutableListOf<EditorTreeRow>()
  appendBranchRows(
    rows = rows,
    branchActions = actions,
    location = ActionBranchLocation(),
    depth = 0,
    prefix = "#",
    addLabel = "Add Action",
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
      isTail = branchActions.isEmpty(),
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
              branchKind = BranchKind.IF_THEN,
            )
          appendBranchRows(
            rows = rows,
            branchActions = action.thenActions,
            location = ActionBranchLocation(action.id, BranchKind.IF_THEN),
            depth = depth + 1,
            prefix = "$stepNum.T",
            addLabel = "Add Then Action",
          )
          rows +=
            EditorTreeRow.BranchLabel(
              key = "branch_else_${action.id}",
              depth = depth + 1,
              title = "Else Branch (${action.elseActions.size})",
              branchKind = BranchKind.IF_ELSE,
            )
          appendBranchRows(
            rows = rows,
            branchActions = action.elseActions,
            location = ActionBranchLocation(action.id, BranchKind.IF_ELSE),
            depth = depth + 1,
            prefix = "$stepNum.E",
            addLabel = "Add Else Action",
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
              branchKind = BranchKind.LOOP_BODY,
            )
          appendBranchRows(
            rows = rows,
            branchActions = action.bodyActions,
            location = ActionBranchLocation(action.id, BranchKind.LOOP_BODY),
            depth = depth + 1,
            prefix = "$stepNum.",
            addLabel = "Add Loop Action",
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
        isTail = index == branchActions.lastIndex,
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
