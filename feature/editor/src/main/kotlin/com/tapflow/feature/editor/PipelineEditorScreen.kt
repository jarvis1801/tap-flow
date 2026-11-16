package com.tapflow.feature.editor

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.pipeline.PipelineErrorStrategy
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.model.pipeline.PipelineStep
import com.tapflow.core.model.script.Script
import kotlinx.coroutines.launch

@Composable
fun PipelineEditorScreen(
  onNavigateBack: () -> Unit,
  onNavigateToPaywall: (String) -> Unit,
  viewModel: PipelineEditorViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  BackHandler {
    viewModel.onIntent(PipelineEditorUiIntent.BackClicked)
  }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        PipelineEditorUiEffect.NavigateBack -> {
          snackbarHostState.currentSnackbarData?.dismiss()
          onNavigateBack()
        }
        is PipelineEditorUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is PipelineEditorUiEffect.ShowSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(effect.message)
          }
        }
      }
    }
  }

  PipelineEditorContent(
    state = state,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
  )
}

@Composable
fun PipelineEditorContent(
  state: PipelineEditorUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (PipelineEditorUiIntent) -> Unit,
) {
  var showTagsDialog by remember { mutableStateOf(false) }
  var tagDraft by remember { mutableStateOf("") }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      PipelineEditorTopHeaderBar(
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
          if (state.steps.isEmpty()) {
            item(key = "empty_pipeline_steps") {
              EmptyPipelineStepsCard(
                onAddFirst = { onIntent(PipelineEditorUiIntent.OpenAddScriptSheet(0)) },
              )
            }
          } else {
            item(key = "add_step_head") {
              InlineStepInsertDivider(
                label = "Insert Script",
                onClick = { onIntent(PipelineEditorUiIntent.OpenAddScriptSheet(0)) },
                testTag = "add_script_step_0",
              )
            }

            itemsIndexed(state.steps, key = { _, step -> step.id }) { index, step ->
              val script = state.availableScriptsById[step.scriptId]
              PipelineStepCard(
                index = index,
                step = step,
                script = script,
                totalSteps = state.steps.size,
                onIntent = onIntent,
              )
              Spacer(modifier = Modifier.height(10.dp))
              if (index == state.steps.lastIndex) {
                FilledTonalButton(
                  onClick = { onIntent(PipelineEditorUiIntent.OpenAddScriptSheet(index + 1)) },
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .heightIn(min = 44.dp)
                      .testTag("add_script_step_${index + 1}"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Add Script")
                }
              } else {
                InlineStepInsertDivider(
                  label = "Insert Script",
                  onClick = { onIntent(PipelineEditorUiIntent.OpenAddScriptSheet(index + 1)) },
                  testTag = "add_script_step_${index + 1}",
                )
              }
            }
          }

          item(key = "pipeline_bottom_scroll_spacer") {
            Spacer(modifier = Modifier.height(88.dp))
          }
        }
      }
    }
  }

  if (state.showUnsavedDialog) {
    AlertDialog(
      onDismissRequest = { onIntent(PipelineEditorUiIntent.DismissUnsavedDialog) },
      title = { Text("Unsaved Changes") },
      text = { Text("You have unsaved edits in this pipeline. Save before leaving?") },
      confirmButton = {
        Button(onClick = { onIntent(PipelineEditorUiIntent.SaveAndExitClicked) }) {
          Text("Save & Exit")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { onIntent(PipelineEditorUiIntent.DiscardChangesClicked) }) {
            Text("Discard Changes")
          }
          TextButton(onClick = { onIntent(PipelineEditorUiIntent.DismissUnsavedDialog) }) {
            Text("Keep Editing")
          }
        }
      },
    )
  }

  if (showTagsDialog) {
    AlertDialog(
      onDismissRequest = { showTagsDialog = false },
      title = { Text("Pipeline Tags") },
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
                  onIntent(PipelineEditorUiIntent.AddTag(tagDraft))
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
              text = "No tags yet. Tags help filter pipelines in your library.",
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
                  onClick = { onIntent(PipelineEditorUiIntent.RemoveTag(tag)) },
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

  if (state.showSettingsSheet) {
    PipelineSettingsBottomSheet(
      state = state,
      onDismiss = { onIntent(PipelineEditorUiIntent.SetSettingsSheetVisible(false)) },
      onSave = { repeatMode, errorStrategy, recoveryPkg ->
        onIntent(
          PipelineEditorUiIntent.UpdateSettings(
            repeatMode = repeatMode,
            errorStrategy = errorStrategy,
            crashRecoveryAppPackage = recoveryPkg,
          ),
        )
      },
      onPickCrashApp = { onIntent(PipelineEditorUiIntent.OpenCrashAppPicker) },
      onProRequested = { onIntent(PipelineEditorUiIntent.RequestProUpgrade(it)) },
    )
  }

  if (state.addStepInsertIndex != null) {
    AddScriptPickerBottomSheet(
      scripts = state.availableScripts,
      onSelectScript = { scriptId ->
        onIntent(PipelineEditorUiIntent.InsertScriptStep(scriptId))
      },
      onDismiss = { onIntent(PipelineEditorUiIntent.DismissAddScriptSheet) },
    )
  }

  if (state.showCrashAppPicker) {
    AppPickerBottomSheet(
      apps = state.installedApps,
      isLoading = state.isLoadingApps,
      currentForegroundPackage = state.currentForegroundPackage,
      onSelect = { pkg, _ -> onIntent(PipelineEditorUiIntent.SelectCrashRecoveryApp(pkg)) },
      onDismiss = { onIntent(PipelineEditorUiIntent.DismissCrashAppPicker) },
    )
  }
}

@Composable
private fun PipelineEditorTopHeaderBar(
  state: PipelineEditorUiState,
  onIntent: (PipelineEditorUiIntent) -> Unit,
  onOpenTagsDialog: () -> Unit,
) {
  var descriptionFocused by remember { mutableStateOf(false) }

  val repeatLabel =
    when (val m = state.repeatMode) {
      PipelineRepeatMode.RunOnce -> "Run Once"
      is PipelineRepeatMode.Count -> "Loop ×${m.iterations}"
      PipelineRepeatMode.Unlimited -> "Unlimited Loop"
    }
  val strategyLabel =
    if (state.errorStrategy == PipelineErrorStrategy.SKIP_AND_CONTINUE) {
      "Skip & Continue"
    } else {
      "Abort on Error"
    }

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
              onClick = { onIntent(PipelineEditorUiIntent.BackClicked) },
              modifier = Modifier.size(48.dp).testTag("pipeline_editor_back_button"),
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
                onValueChange = { onIntent(PipelineEditorUiIntent.UpdateTitle(it)) },
                singleLine = true,
                textStyle =
                  MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag("pipeline_title_input"),
                decorationBox = { innerTextField ->
                  if (state.title.isEmpty()) {
                    Text(
                      text = "Untitled Pipeline...",
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
              onClick = { onIntent(PipelineEditorUiIntent.SavePipelineClicked) },
              enabled = !state.isSaving,
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
              modifier = Modifier.heightIn(min = 40.dp).testTag("save_pipeline_button"),
            ) {
              Text(if (state.isSaving) "Saving…" else "Save")
            }
          }

          // Row 2: Expandable Borderless Description + Policy Pill, Live Step/Run Badge, Tags
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
                onValueChange = { onIntent(PipelineEditorUiIntent.UpdateDescription(it)) },
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
                    .testTag("pipeline_description_input"),
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
                onClick = { onIntent(PipelineEditorUiIntent.SetSettingsSheetVisible(true)) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    text = "$repeatLabel • $strategyLabel",
                    maxLines = 1,
                  )
                },
                modifier = Modifier.testTag("pipeline_settings_button"),
              )

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
              ) {
                Text(
                  text = "${state.steps.size} Steps • ${state.steps.sumOf { it.repeatCount }} Runs",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
              }

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
                modifier = Modifier.testTag("pipeline_tags_button"),
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
private fun EmptyPipelineStepsCard(onAddFirst: () -> Unit) {
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
        icon = Icons.Rounded.AccountTree,
        category = ActionCategoryGroup.FLOW_CONTROL,
        boxSize = 56.dp,
        iconSize = 28.dp,
        cornerRadius = 16.dp,
      )
      Text(
        text = "No scripts in pipeline",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "Chain scripts together with per-step repeat counts and crash recovery.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(4.dp))
      FilledTonalButton(
        onClick = onAddFirst,
        modifier = Modifier.heightIn(min = 48.dp).testTag("add_script_step_0"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add Script")
      }
    }
  }
}

@Composable
private fun InlineStepInsertDivider(
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
private fun PipelineStepCard(
  index: Int,
  step: PipelineStep,
  script: Script?,
  totalSteps: Int,
  onIntent: (PipelineEditorUiIntent) -> Unit,
) {
  val isMissing = script == null
  var menuExpanded by remember(step.id) { mutableStateOf(false) }

  val containerColor =
    if (isMissing) {
      MaterialTheme.colorScheme.errorContainer
    } else {
      MaterialTheme.colorScheme.surfaceContainerLow
    }
  val cardContentColor =
    if (isMissing) {
      MaterialTheme.colorScheme.onErrorContainer
    } else {
      MaterialTheme.colorScheme.onSurface
    }
  val borderColor =
    if (isMissing) {
      MaterialTheme.colorScheme.error
    } else {
      MaterialTheme.colorScheme.outlineVariant
    }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = containerColor,
    contentColor = cardContentColor,
    border = BorderStroke(if (isMissing) 1.dp else 0.5.dp, borderColor),
    modifier = Modifier.fillMaxWidth().testTag("pipeline_step_card_${step.id}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color =
            if (isMissing) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.primaryContainer
            },
        ) {
          Text(
            text = "STEP ${(index + 1).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color =
              if (isMissing) {
                MaterialTheme.colorScheme.onError
              } else {
                MaterialTheme.colorScheme.onPrimaryContainer
              },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          if (isMissing) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = "Missing script",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Missing Script",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          } else {
            Text(
              text = script.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${script.actions.size} actions",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Step options")
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
          ) {
            DropdownMenuItem(
              text = { Text("Move Up") },
              leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
              enabled = index > 0,
              onClick = {
                menuExpanded = false
                onIntent(PipelineEditorUiIntent.MoveStepUp(index))
              },
            )
            DropdownMenuItem(
              text = { Text("Move Down") },
              leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
              enabled = index < totalSteps - 1,
              onClick = {
                menuExpanded = false
                onIntent(PipelineEditorUiIntent.MoveStepDown(index))
              },
            )
            HorizontalDivider()
            DropdownMenuItem(
              text = { Text("Remove") },
              leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
              colors =
                MenuDefaults.itemColors(
                  textColor = MaterialTheme.colorScheme.error,
                  leadingIconColor = MaterialTheme.colorScheme.error,
                ),
              onClick = {
                menuExpanded = false
                onIntent(PipelineEditorUiIntent.RemoveStep(step.id))
              },
            )
          }
        }
      }

      if (script != null && script.tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          script.tags.forEach { tag ->
            AssistChip(onClick = {}, label = { Text(tag) })
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Repeat Count",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RepeatStepperPill(
          count = step.repeatCount,
          onDelta = { delta ->
            onIntent(PipelineEditorUiIntent.UpdateStepRepeatCount(step.id, delta))
          },
        )
      }
    }
  }
}

@Composable
private fun RepeatStepperPill(
  count: Int,
  onDelta: (Int) -> Unit,
) {
  var countText by remember(count) { mutableStateOf(count.toString()) }

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
      FilledTonalIconButton(
        onClick = { onDelta(-1) },
        enabled = count > 1,
        shape = CircleShape,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(Icons.Rounded.Remove, contentDescription = "Decrease repeat count")
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp),
      ) {
        BasicTextField(
          value = countText,
          onValueChange = { raw ->
            val clean = raw.filter { it.isDigit() }.take(3)
            countText = clean
            val parsed = clean.toIntOrNull()
            if (parsed != null && parsed >= 1 && parsed != count) {
              onDelta(parsed - count)
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          textStyle =
            MaterialTheme.typography.titleSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
            ),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          modifier = Modifier.widthIn(min = 24.dp, max = 44.dp),
        )
        Text(
          text = "×",
          style = MaterialTheme.typography.titleSmall,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      FilledTonalIconButton(
        onClick = { onDelta(+1) },
        shape = CircleShape,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Increase repeat count")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PipelineSettingsBottomSheet(
  state: PipelineEditorUiState,
  onDismiss: () -> Unit,
  onSave: (PipelineRepeatMode, PipelineErrorStrategy, String?) -> Unit,
  onPickCrashApp: () -> Unit,
  onProRequested: (String) -> Unit,
) {
  var draftRepeat by remember(state.repeatMode) { mutableStateOf(state.repeatMode) }
  var draftErrorStrategy by remember(state.errorStrategy) { mutableStateOf(state.errorStrategy) }
  var draftRecoveryPkg by
    remember(state.crashRecoveryAppPackage) { mutableStateOf(state.crashRecoveryAppPackage.orEmpty()) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val repeatIdx =
    when (draftRepeat) {
      PipelineRepeatMode.RunOnce -> 0
      is PipelineRepeatMode.Count -> 1
      PipelineRepeatMode.Unlimited -> 2
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Pipeline Execution Settings",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      EditorSectionCard(title = "Pipeline Repeat Mode") {
        val repeatOptions = listOf("Run Once", "Loop N", "Unlimited")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          repeatOptions.forEachIndexed { idx, label ->
            val locked = idx > 0 && !state.isPro
            SegmentedButton(
              selected = repeatIdx == idx,
              onClick = {
                when (idx) {
                  0 -> draftRepeat = PipelineRepeatMode.RunOnce
                  1 ->
                    if (!state.isPro) {
                      onProRequested(ProFeatureGate.PIPELINE_AUTOMATION.displayName)
                    } else if (draftRepeat !is PipelineRepeatMode.Count) {
                      draftRepeat = PipelineRepeatMode.Count(3)
                    }
                  2 ->
                    if (!state.isPro) {
                      onProRequested(ProFeatureGate.PIPELINE_AUTOMATION.displayName)
                    } else {
                      draftRepeat = PipelineRepeatMode.Unlimited
                    }
                }
              },
              shape = SegmentedButtonDefaults.itemShape(index = idx, count = repeatOptions.size),
              icon = {
                if (locked) {
                  Icon(Icons.Rounded.Lock, contentDescription = "Pro feature", modifier = Modifier.size(14.dp))
                } else {
                  SegmentedButtonDefaults.Icon(active = repeatIdx == idx)
                }
              },
            ) {
              Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }

        if (draftRepeat is PipelineRepeatMode.Count) {
          val countMode = draftRepeat as PipelineRepeatMode.Count
          var countText by remember(countMode.iterations) { mutableStateOf(countMode.iterations.toString()) }
          OutlinedTextField(
            value = countText,
            onValueChange = { raw ->
              countText = raw.filter { it.isDigit() }
              countText.toIntOrNull()?.let { n ->
                draftRepeat = PipelineRepeatMode.Count(n.coerceAtLeast(1))
              }
            },
            label = { Text("Pipeline Iterations") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      EditorSectionCard(title = "Error Handling & Crash Recovery") {
        val strategies = PipelineErrorStrategy.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          strategies.forEachIndexed { idx, strategy ->
            val locked = strategy == PipelineErrorStrategy.SKIP_AND_CONTINUE && !state.isPro
            val label =
              if (strategy == PipelineErrorStrategy.ABORT) "Abort" else "Skip & Continue"
            SegmentedButton(
              selected = draftErrorStrategy == strategy,
              onClick = {
                if (locked) {
                  onProRequested(ProFeatureGate.PIPELINE_AUTOMATION.displayName)
                } else {
                  draftErrorStrategy = strategy
                }
              },
              shape = SegmentedButtonDefaults.itemShape(index = idx, count = strategies.size),
              icon = {
                if (locked) {
                  Icon(Icons.Rounded.Lock, contentDescription = "Pro feature", modifier = Modifier.size(14.dp))
                } else {
                  SegmentedButtonDefaults.Icon(active = draftErrorStrategy == strategy)
                }
              },
            ) {
              Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }

        OutlinedTextField(
          value = draftRecoveryPkg,
          onValueChange = { draftRecoveryPkg = it },
          label = { Text("Crash Recovery App Package (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalButton(
          onClick = onPickCrashApp,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
          Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pick Crash Recovery App")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
          Text("Cancel")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = {
            onSave(draftRepeat, draftErrorStrategy, draftRecoveryPkg.ifBlank { null })
          },
          modifier = Modifier.heightIn(min = 48.dp).testTag("apply_pipeline_settings_button"),
        ) {
          Text("Apply")
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScriptPickerBottomSheet(
  scripts: List<Script>,
  onSelectScript: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered =
    remember(scripts, query) {
      val q = query.trim()
      if (q.isEmpty()) {
        scripts
      } else {
        scripts.filter {
          it.title.contains(q, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(q, ignoreCase = true) }
        }
      }
    }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Add Script to Pipeline",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon =
          if (query.isNotEmpty()) {
            {
              IconButton(onClick = { query = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
              }
            }
          } else {
            null
          },
        placeholder = { Text("Search scripts by title or tag...") },
        shape = RoundedCornerShape(999.dp),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("pipeline_script_search"),
      )

      if (filtered.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "No matching scripts found in library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(filtered, key = { it.id }) { script ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onSelectScript(script.id) },
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = script.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text =
                    "${script.actions.size} actions" +
                      if (script.tags.isNotEmpty()) " • ${script.tags.joinToString(", ")}" else "",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PipelineEditorPreview() {
  MaterialTheme {
    val s1 = Script(id = "s1", title = "Login & Collect", tags = listOf("Daily"))
    val s2 = Script(id = "s2", title = "Dungeon Farm", tags = listOf("Auto"))
    PipelineEditorContent(
      state =
        PipelineEditorUiState(
          pipelineId = "p1",
          isLoading = false,
          isPro = true,
          title = "Morning Routine",
          description = "Runs daily login then farms dungeon 3x.",
          tags = listOf("Chain", "AFK"),
          availableScripts = listOf(s1, s2),
          availableScriptsById = mapOf("s1" to s1, "s2" to s2),
          steps =
            listOf(
              PipelineStep(id = "st1", scriptId = "s1", repeatCount = 1),
              PipelineStep(id = "st2", scriptId = "s2", repeatCount = 3),
              PipelineStep(id = "st3", scriptId = "deleted_id", repeatCount = 1),
            ),
        ),
      snackbarHostState = remember { SnackbarHostState() },
      onIntent = {},
    )
  }
}
