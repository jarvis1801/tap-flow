package com.tapflow.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.service.external.ExternalBroadcastContract
import com.tapflow.core.service.shizuku.ShizukuDiagnosticState
import kotlinx.coroutines.launch

@Composable
fun ExternalAutomationGuideScreen(
  onNavigateBack: () -> Unit,
  onNavigateToPaywall: (String?) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ExternalAutomationGuideViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LifecycleResumeEffect(Unit) {
    viewModel.onIntent(ExternalAutomationGuideUiIntent.RefreshStatus)
    onPauseOrDispose {}
  }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        ExternalAutomationGuideUiEffect.NavigateBack -> onNavigateBack()
        is ExternalAutomationGuideUiEffect.NavigateToPaywall ->
          onNavigateToPaywall(effect.sourceFeature)
        is ExternalAutomationGuideUiEffect.CopyToClipboard -> {
          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText(effect.label, effect.value))
        }
        is ExternalAutomationGuideUiEffect.ShowSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(effect.message)
          }
        }
      }
    }
  }

  ExternalAutomationGuideContent(
    state = state,
    packageName = context.packageName,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalAutomationGuideContent(
  state: ExternalAutomationGuideUiState,
  packageName: String,
  snackbarHostState: SnackbarHostState,
  onIntent: (ExternalAutomationGuideUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "External Automation Guide",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { onIntent(ExternalAutomationGuideUiIntent.BackClicked) },
            modifier = Modifier.size(48.dp).testTag("external_guide_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Navigate back",
            )
          }
        },
        actions = {
          IconButton(
            onClick = { onIntent(ExternalAutomationGuideUiIntent.RefreshStatus) },
            modifier = Modifier.size(48.dp).testTag("external_guide_refresh_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Refresh Shizuku status",
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item(key = "prereq_banner") {
          PrerequisiteBannerCard(
            state = state,
            onIntent = onIntent,
          )
        }

        item(key = "intent_generator") {
          IntentGeneratorCard(
            state = state,
            packageName = packageName,
            onIntent = onIntent,
          )
        }

        item(key = "control_actions_reference") {
          ControlActionsReferenceCard(
            packageName = packageName,
            onCopy = { label, value ->
              onIntent(ExternalAutomationGuideUiIntent.CopyField(label, value))
            },
          )
        }

        item(key = "step_by_step_guide") {
          StepByStepGuideCard(
            activeTab = state.activePlatformTab,
            packageName = packageName,
            selectedTargetTitle = state.selectedTarget?.title ?: "My Automation Script",
            isPipelineMode = state.targetMode == ExternalTargetMode.PIPELINE,
            onSelectTab = { onIntent(ExternalAutomationGuideUiIntent.SelectPlatformTab(it)) },
            onCopy = { label, value ->
              onIntent(ExternalAutomationGuideUiIntent.CopyField(label, value))
            },
          )
        }
      }
    }
  }
}

@Composable
private fun PrerequisiteBannerCard(
  state: ExternalAutomationGuideUiState,
  onIntent: (ExternalAutomationGuideUiIntent) -> Unit,
) {
  val containerColor =
    if (state.isAllPrerequisitesReady) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
      MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    }
  val borderColor =
    if (state.isAllPrerequisitesReady) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.error
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("external_guide_prereq_banner"),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text =
          if (state.isAllPrerequisitesReady) {
            "Ready for MacroDroid & Tasker Triggers"
          } else {
            "Prerequisites Required Before Sending Intents"
          },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      PrereqStatusRow(
        label = "TapFlow Pro / 7-Day Trial",
        isReady = state.isPro,
        statusText = if (state.isPro) "Active ✓" else "Pro Required",
      )
      PrereqStatusRow(
        label = "Shizuku Service & Permission",
        isReady = state.shizukuStatus.isFullyReady,
        statusText =
          when {
            state.shizukuStatus.isFullyReady -> "Ready ✓"
            !state.shizukuStatus.isInstalled -> "Not Installed"
            !state.shizukuStatus.isServiceRunning -> "Service Stopped"
            else -> "Permission Needed"
          },
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "External Broadcast Listener",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text =
              if (state.externalBroadcastEnabled) {
                "Enabled — TapFlow accepts triggers & emits completion broadcasts."
              } else {
                "Disabled — turn ON to accept MacroDroid / Tasker broadcasts."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (!state.isPro) {
          FilledTonalButton(
            onClick = { onIntent(ExternalAutomationGuideUiIntent.OpenPaywallClicked) },
            modifier = Modifier.testTag("external_guide_upgrade_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Unlock")
          }
        } else {
          Switch(
            checked = state.externalBroadcastEnabled,
            enabled = state.shizukuStatus.isFullyReady,
            onCheckedChange = {
              onIntent(ExternalAutomationGuideUiIntent.ToggleExternalBroadcast(it))
            },
            modifier = Modifier.testTag("external_guide_toggle_switch"),
          )
        }
      }
    }
  }
}

@Composable
private fun PrereqStatusRow(
  label: String,
  isReady: Boolean,
  statusText: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = if (isReady) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
      contentDescription = null,
      tint =
        if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = statusText,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color =
        if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntentGeneratorCard(
  state: ExternalAutomationGuideUiState,
  packageName: String,
  onIntent: (ExternalAutomationGuideUiIntent) -> Unit,
) {
  val items =
    when (state.targetMode) {
      ExternalTargetMode.SCRIPT -> state.scripts
      ExternalTargetMode.PIPELINE -> state.pipelines
    }
  val selected = state.selectedTarget
  var dropdownExpanded by remember { mutableStateOf(false) }

  val actionString =
    when (state.targetMode) {
      ExternalTargetMode.SCRIPT -> ExternalBroadcastContract.actionPlayScript(packageName)
      ExternalTargetMode.PIPELINE -> ExternalBroadcastContract.actionPlayPipeline(packageName)
    }
  val nameExtraKey =
    when (state.targetMode) {
      ExternalTargetMode.SCRIPT -> ExternalBroadcastContract.EXTRA_SCRIPT_NAME
      ExternalTargetMode.PIPELINE -> ExternalBroadcastContract.EXTRA_PIPELINE_NAME
    }
  val idExtraKey =
    when (state.targetMode) {
      ExternalTargetMode.SCRIPT -> ExternalBroadcastContract.EXTRA_SCRIPT_ID
      ExternalTargetMode.PIPELINE -> ExternalBroadcastContract.EXTRA_PIPELINE_ID
    }
  val targetTitle = selected?.title ?: ""
  val targetId = selected?.id ?: ""

  Card(
    modifier = Modifier.fillMaxWidth().testTag("external_intent_generator_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Tune,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "Interactive Intent Generator",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = state.targetMode == ExternalTargetMode.SCRIPT,
          onClick = {
            onIntent(ExternalAutomationGuideUiIntent.SelectTargetMode(ExternalTargetMode.SCRIPT))
          },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
          modifier = Modifier.testTag("generator_mode_script"),
        ) {
          Text("Script")
        }
        SegmentedButton(
          selected = state.targetMode == ExternalTargetMode.PIPELINE,
          onClick = {
            onIntent(ExternalAutomationGuideUiIntent.SelectTargetMode(ExternalTargetMode.PIPELINE))
          },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
          modifier = Modifier.testTag("generator_mode_pipeline"),
        ) {
          Text("Pipeline")
        }
      }

      if (items.isEmpty()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text =
              if (state.targetMode == ExternalTargetMode.SCRIPT) {
                "No saved scripts found. Create a script first to auto-fill its name and ID."
              } else {
                "No saved pipelines found. Create a pipeline first to auto-fill its name and ID."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        }
      } else {
        ExposedDropdownMenuBox(
          expanded = dropdownExpanded,
          onExpandedChange = { dropdownExpanded = it },
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedTextField(
            value = selected?.title.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = {
              Text(
                if (state.targetMode == ExternalTargetMode.SCRIPT) {
                  "Saved Script"
                } else {
                  "Saved Pipeline"
                },
              )
            },
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
            },
            modifier =
              Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("generator_target_dropdown"),
          )
          ExposedDropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false },
          ) {
            items.forEach { option ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = option.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                onClick = {
                  dropdownExpanded = false
                  onIntent(ExternalAutomationGuideUiIntent.SelectTarget(option.id))
                },
                modifier = Modifier.testTag("generator_target_option_${option.id}"),
              )
            }
          }
        }
      }

      CopyableCodeField(
        label = "Package",
        value = packageName,
        testTag = "copy_package_button",
        onCopy = { onIntent(ExternalAutomationGuideUiIntent.CopyField("Package", packageName)) },
      )
      CopyableCodeField(
        label = "Action",
        value = actionString,
        testTag = "copy_play_action_button",
        onCopy = { onIntent(ExternalAutomationGuideUiIntent.CopyField("Action", actionString)) },
      )
      if (targetTitle.isNotBlank()) {
        CopyableCodeField(
          label = "Extra (By Name - Case Insensitive)",
          value = "$nameExtraKey: $targetTitle",
          copyValue = targetTitle,
          testTag = "copy_target_name_value_button",
          onCopy = {
            onIntent(ExternalAutomationGuideUiIntent.CopyField(nameExtraKey, targetTitle))
          },
        )
        CopyableCodeField(
          label = "Tasker Extra Format ($nameExtraKey)",
          value = "$nameExtraKey:$targetTitle",
          testTag = "copy_tasker_extra_button",
          onCopy = {
            onIntent(
              ExternalAutomationGuideUiIntent.CopyField(
                "Tasker Extra",
                "$nameExtraKey:$targetTitle",
              ),
            )
          },
        )
        CopyableCodeField(
          label = "Extra (By Exact ID)",
          value = "$idExtraKey: $targetId",
          copyValue = targetId,
          testTag = "copy_target_id_value_button",
          onCopy = {
            onIntent(ExternalAutomationGuideUiIntent.CopyField(idExtraKey, targetId))
          },
        )
      }
    }
  }
}

@Composable
private fun ControlActionsReferenceCard(
  packageName: String,
  onCopy: (label: String, value: String) -> Unit,
) {
  val controls =
    remember(packageName) {
      listOf(
        Triple(
          "Resume Execution",
          ExternalBroadcastContract.actionResume(packageName),
          "Resumes a paused script or pipeline session.",
        ),
        Triple(
          "Pause Execution",
          ExternalBroadcastContract.actionPause(packageName),
          "Pauses the currently running script or pipeline.",
        ),
        Triple(
          "Stop Execution",
          ExternalBroadcastContract.actionStop(packageName),
          "Stops execution, resets runner to standby, and emits STOPPED callback.",
        ),
        Triple(
          "Close Overlay",
          ExternalBroadcastContract.actionClose(packageName),
          "Stops runner, removes floating overlay, and releases MediaProjection.",
        ),
        Triple(
          "Next Action Step",
          ExternalBroadcastContract.actionNextAction(packageName),
          "Steps forward 1 atomic top-level action.",
        ),
        Triple(
          "Previous Action Step",
          ExternalBroadcastContract.actionPrevAction(packageName),
          "Steps back 1 top-level action pointer.",
        ),
      )
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("control_actions_reference_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Playback & Overlay Control Actions",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Send these Broadcast Actions with Package \"$packageName\" (no extra parameters required).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))

      controls.forEachIndexed { index, (title, action, desc) ->
        if (index > 0) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = action,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = desc,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = { onCopy(title, action) },
            modifier =
              Modifier
                .size(48.dp)
                .testTag("copy_control_${title.lowercase().replace(' ', '_')}"),
          ) {
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = "Copy $title action",
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepByStepGuideCard(
  activeTab: ExternalGuidePlatformTab,
  packageName: String,
  selectedTargetTitle: String,
  isPipelineMode: Boolean,
  onSelectTab: (ExternalGuidePlatformTab) -> Unit,
  onCopy: (label: String, value: String) -> Unit,
) {
  val playAction =
    if (isPipelineMode) {
      ExternalBroadcastContract.actionPlayPipeline(packageName)
    } else {
      ExternalBroadcastContract.actionPlayScript(packageName)
    }
  val extraKey =
    if (isPipelineMode) {
      ExternalBroadcastContract.EXTRA_PIPELINE_NAME
    } else {
      ExternalBroadcastContract.EXTRA_SCRIPT_NAME
    }
  val finishedAction = ExternalBroadcastContract.actionExecutionFinished(packageName)

  Card(
    modifier = Modifier.fillMaxWidth().testTag("step_by_step_guide_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Step-by-Step Setup Guide",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      PrimaryTabRow(selectedTabIndex = activeTab.ordinal) {
        ExternalGuidePlatformTab.entries.forEach { tab ->
          Tab(
            selected = activeTab == tab,
            onClick = { onSelectTab(tab) },
            text = { Text(tab.label) },
            modifier = Modifier.testTag("guide_tab_${tab.name.lowercase()}"),
          )
        }
      }

      when (activeTab) {
        ExternalGuidePlatformTab.MACRODROID -> {
          Text(
            text = "1. Triggering TapFlow (Send Intent)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          GuideStepList(
            steps =
              listOf(
                "Add Action ➔ Applications ➔ Send Intent.",
                "Target: Broadcast.",
                "Action: $playAction",
                "Package: $packageName",
                "Extra 1: Parameter = $extraKey | Type = String | Value = $selectedTargetTitle",
              ),
          )
          HorizontalDivider()
          Text(
            text = "2. Waiting for Completion (Intent Received)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          CopyableCodeField(
            label = "Completion Broadcast Action",
            value = finishedAction,
            testTag = "copy_finished_action_macrodroid",
            onCopy = { onCopy("Completion Action", finishedAction) },
          )
          GuideStepList(
            steps =
              listOf(
                "Add Trigger ➔ Connectivity ➔ Intent Received.",
                "Action: $finishedAction (leave Package unrestricted).",
                "Extract Extras into MacroDroid String/Integer variables using the exact dot-free keys below.",
              ),
          )
        }

        ExternalGuidePlatformTab.TASKER -> {
          Text(
            text = "1. Triggering TapFlow (Send Intent)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          GuideStepList(
            steps =
              listOf(
                "Add Action ➔ System ➔ Send Intent.",
                "Action: $playAction",
                "Cat: None",
                "Extra: $extraKey:$selectedTargetTitle",
                "Package: $packageName",
                "Target: Broadcast Receiver",
              ),
          )
          HorizontalDivider()
          Text(
            text = "2. Waiting for Completion (Intent Received)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          CopyableCodeField(
            label = "Completion Broadcast Action",
            value = finishedAction,
            testTag = "copy_finished_action_tasker",
            onCopy = { onCopy("Completion Action", finishedAction) },
          )
          GuideStepList(
            steps =
              listOf(
                "Create Profile ➔ Event ➔ System ➔ Intent Received.",
                "Action: $finishedAction",
                "Tasker automatically exposes all dot-free extras as local variables: %status, %mode, %target_id, %target_title, %duration_ms, %steps_executed, and %error.",
              ),
          )
        }
      }

      HorizontalDivider()
      Text(
        text = "Completion Extras Contract (ACTION_EXECUTION_FINISHED)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      ExtrasReferenceTable()
    }
  }
}

@Composable
private fun GuideStepList(steps: List<String>) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    steps.forEachIndexed { index, text ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "${index + 1}.",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = text,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ExtrasReferenceTable() {
  val rows =
    remember {
      listOf(
        "status (%status)" to "COMPLETED | FAILED | STOPPED | REJECTED",
        "mode (%mode)" to "SCRIPT | PIPELINE | CONTROL",
        "target_id (%target_id)" to "Script or Pipeline UUID (String)",
        "target_title (%target_title)" to "Script or Pipeline title (String)",
        "duration_ms (%duration_ms)" to "Total run duration in ms (Long)",
        "steps_executed (%steps_executed)" to "Total steps completed (Int)",
        "error (%error)" to "\"\" on success, or SHIZUKU_NOT_READY, PRO_REQUIRED, EXTERNAL_TRIGGER_DISABLED, PERMISSIONS_MISSING, TARGET_NOT_FOUND, or failure reason",
      )
    }

  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      rows.forEachIndexed { index, (key, value) ->
        if (index > 0) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun CopyableCodeField(
  label: String,
  value: String,
  testTag: String,
  onCopy: () -> Unit,
  copyValue: String = value,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = value,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      IconButton(
        onClick = onCopy,
        modifier = Modifier.size(48.dp).testTag(testTag),
      ) {
        Icon(
          imageVector = Icons.Filled.ContentCopy,
          contentDescription = "Copy $label ($copyValue)",
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ExternalAutomationGuidePreview() {
  MaterialTheme {
    ExternalAutomationGuideContent(
      state =
        ExternalAutomationGuideUiState(
          isPro = true,
          shizukuStatus =
            ShizukuDiagnosticState(
              isInstalled = true,
              isServiceRunning = true,
              isPermissionGranted = true,
              isUserServiceBound = true,
            ),
          externalBroadcastEnabled = true,
          scripts =
            listOf(
              ExternalTargetOption(id = "scr-101", title = "Daily Check-In"),
              ExternalTargetOption(id = "scr-102", title = "Auto Collect Rewards"),
            ),
          pipelines =
            listOf(
              ExternalTargetOption(id = "pip-201", title = "Morning Routine Pipeline"),
            ),
          selectedScriptId = "scr-101",
          selectedPipelineId = "pip-201",
        ),
      packageName = "com.jarvis.tapflow",
      snackbarHostState = SnackbarHostState(),
      onIntent = {},
    )
  }
}
