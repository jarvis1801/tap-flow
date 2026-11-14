package com.tapflow.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
        PipelineEditorUiEffect.NavigateBack -> onNavigateBack()
        is PipelineEditorUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is PipelineEditorUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
      }
    }
  }

  PipelineEditorContent(
    state = state,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineEditorContent(
  state: PipelineEditorUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (PipelineEditorUiIntent) -> Unit,
) {
  var tagDraft by remember { mutableStateOf("") }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { onIntent(PipelineEditorUiIntent.BackClicked) },
            modifier = Modifier.testTag("pipeline_editor_back_button"),
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
              text = state.title.ifBlank { "Untitled Pipeline" },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            val repeatLabel =
              when (val m = state.repeatMode) {
                PipelineRepeatMode.RunOnce -> "Run Once"
                is PipelineRepeatMode.Count -> "Loop ×${m.iterations}"
                PipelineRepeatMode.Unlimited -> "Unlimited Loop"
              }
            Text(
              text = "$repeatLabel • ${state.steps.size} Steps" + if (state.isDirty) " • Unsaved" else "",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { onIntent(PipelineEditorUiIntent.SetSettingsSheetVisible(true)) },
            modifier = Modifier.testTag("pipeline_settings_button"),
          ) {
            Icon(Icons.Filled.Tune, contentDescription = "Pipeline execution settings")
          }
          FilledTonalIconButton(
            onClick = { onIntent(PipelineEditorUiIntent.SavePipelineClicked) },
            enabled = !state.isSaving,
            modifier = Modifier.padding(end = 6.dp).testTag("save_pipeline_button"),
          ) {
            Icon(Icons.Filled.Save, contentDescription = "Save pipeline")
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
          item(key = "pipeline_metadata_card") {
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
                  onValueChange = { onIntent(PipelineEditorUiIntent.UpdateTitle(it)) },
                  label = { Text("Pipeline Title") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("pipeline_title_input"),
                )
                OutlinedTextField(
                  value = state.description,
                  onValueChange = { onIntent(PipelineEditorUiIntent.UpdateDescription(it)) },
                  label = { Text("Description (Optional)") },
                  minLines = 1,
                  maxLines = 4,
                  modifier = Modifier.fillMaxWidth().testTag("pipeline_description_input"),
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
                        onIntent(PipelineEditorUiIntent.AddTag(tagDraft))
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
                        onClick = { onIntent(PipelineEditorUiIntent.RemoveTag(tag)) },
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

          item(key = "add_step_head") {
            AddScriptInsertButton(
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
            Spacer(modifier = Modifier.height(8.dp))
            AddScriptInsertButton(
              onClick = { onIntent(PipelineEditorUiIntent.OpenAddScriptSheet(index + 1)) },
              testTag = "add_script_step_${index + 1}",
            )
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
private fun AddScriptInsertButton(
  onClick: () -> Unit,
  testTag: String,
) {
  OutlinedButton(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag(testTag),
  ) {
    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text("+ Add Script")
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
  Card(
    border =
      if (isMissing) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
      } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = Modifier.fillMaxWidth().testTag("pipeline_step_card_${step.id}"),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color =
            if (isMissing) {
              MaterialTheme.colorScheme.errorContainer
            } else {
              MaterialTheme.colorScheme.primaryContainer
            },
        ) {
          Text(
            text = "STEP #${(index + 1).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color =
              if (isMissing) {
                MaterialTheme.colorScheme.onErrorContainer
              } else {
                MaterialTheme.colorScheme.onPrimaryContainer
              },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          if (isMissing) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = "Missing script",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "[Missing Script]",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
              )
            }
          } else {
            Text(
              text = script.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${script.actions.size} actions",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
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

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text("Repeat:", style = MaterialTheme.typography.labelMedium)
          IconButton(
            onClick = { onIntent(PipelineEditorUiIntent.UpdateStepRepeatCount(step.id, -1)) },
            enabled = step.repeatCount > 1,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease repeat count")
          }
          Text(
            text = "×${step.repeatCount}",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          )
          IconButton(
            onClick = { onIntent(PipelineEditorUiIntent.UpdateStepRepeatCount(step.id, +1)) },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase repeat count")
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { onIntent(PipelineEditorUiIntent.MoveStepUp(index)) },
            enabled = index > 0,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move step up")
          }
          IconButton(
            onClick = { onIntent(PipelineEditorUiIntent.MoveStepDown(index)) },
            enabled = index < totalSteps - 1,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move step down")
          }
          IconButton(
            onClick = { onIntent(PipelineEditorUiIntent.RemoveStep(step.id)) },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Remove step",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
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

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Pipeline Execution Settings",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      Text("Pipeline Repeat Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = draftRepeat is PipelineRepeatMode.RunOnce,
          onClick = { draftRepeat = PipelineRepeatMode.RunOnce },
          label = { Text("Run Once") },
        )
        FilterChip(
          selected = draftRepeat is PipelineRepeatMode.Count,
          onClick = {
            if (draftRepeat !is PipelineRepeatMode.Count) {
              draftRepeat = PipelineRepeatMode.Count(3)
            }
          },
          label = { Text("Loop Count (N)") },
        )
        FilterChip(
          selected = draftRepeat is PipelineRepeatMode.Unlimited,
          onClick = {
            if (!state.isPro) {
              onProRequested(ProFeatureGate.ADVANCED_LOOPS.displayName)
            } else {
              draftRepeat = PipelineRepeatMode.Unlimited
            }
          },
          leadingIcon =
            if (!state.isPro) {
              { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else {
              null
            },
          label = { Text("Unlimited") },
        )
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

      HorizontalDivider()

      Text("Error Handling Strategy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = draftErrorStrategy == PipelineErrorStrategy.ABORT,
          onClick = { draftErrorStrategy = PipelineErrorStrategy.ABORT },
          label = { Text("Abort Pipeline") },
        )
        FilterChip(
          selected = draftErrorStrategy == PipelineErrorStrategy.SKIP_AND_CONTINUE,
          onClick = {
            if (!state.isPro) {
              onProRequested(ProFeatureGate.PIPELINE_SKIP_CONTINUE.displayName)
            } else {
              draftErrorStrategy = PipelineErrorStrategy.SKIP_AND_CONTINUE
            }
          },
          leadingIcon =
            if (!state.isPro) {
              { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else {
              null
            },
          label = { Text("Skip & Continue") },
        )
      }

      OutlinedTextField(
        value = draftRecoveryPkg,
        onValueChange = { draftRecoveryPkg = it },
        label = { Text("Crash Recovery App Package (Optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedButton(
        onClick = onPickCrashApp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      ) {
        Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Pick Crash Recovery App")
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = {
            onSave(draftRepeat, draftErrorStrategy, draftRecoveryPkg.ifBlank { null })
          },
          modifier = Modifier.testTag("apply_pipeline_settings_button"),
        ) {
          Text("Apply")
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
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
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
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
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Search scripts by title or tag...") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("pipeline_script_search"),
      )

      if (filtered.isEmpty()) {
        Text(
          text = "No matching scripts found in library.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 24.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(filtered, key = { it.id }) { script ->
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onSelectScript(script.id) },
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = script.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
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
      Spacer(modifier = Modifier.height(14.dp))
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
