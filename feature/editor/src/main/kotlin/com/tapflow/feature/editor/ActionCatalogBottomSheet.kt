package com.tapflow.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.PanToolAlt
import androidx.compose.material.icons.rounded.Pinch
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.BooleanOperator
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.MathOperator
import com.tapflow.core.model.action.RandomTargetType
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.StringOperator
import com.tapflow.core.model.action.SystemKeyType
import com.tapflow.core.model.action.TypeCastOperator
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.condition.ConditionGroup
import java.util.UUID

private data class ActionTemplateSpec(
  val label: String,
  val hint: String,
  val icon: ImageVector,
  val category: ActionCategoryGroup,
  val isPro: Boolean,
  val factory: (id: String) -> ScriptAction,
  val matcher: (ScriptAction) -> Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCatalogBottomSheet(
  session: ActionEditorSession,
  globalDelay: DelayConfig,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val isEditingExisting = session.editingActionId != null
  var searchQuery by remember(session.editingActionId) { mutableStateOf("") }
  var showAllCategories by remember(session.editingActionId) { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = { onIntent(ScriptEditorUiIntent.DismissActionSheet) },
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ActionIconBadge(
          icon = session.draftAction.actionIcon(),
          category = session.draftAction.categoryGroup(),
          boxSize = 40.dp,
          iconSize = 20.dp,
          cornerRadius = 12.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              if (isEditingExisting) {
                "Edit ${ActionTreeUtils.actionTypeTitle(session.draftAction)}"
              } else {
                "Add Action"
              },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          if (!isEditingExisting) {
            Text(
              text = "Selected: ${ActionTreeUtils.actionTypeTitle(session.draftAction)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .heightIn(max = 540.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (!isEditingExisting) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search actions...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon =
              if (searchQuery.isNotEmpty()) {
                {
                  IconButton(onClick = { searchQuery = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                  }
                }
              } else {
                null
              },
            shape = RoundedCornerShape(999.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = showAllCategories,
              onClick = { showAllCategories = true },
              label = { Text("All") },
            )
            ActionCategoryGroup.entries.forEach { category ->
              val locked = category == ActionCategoryGroup.VARIABLE_OPS && !isPro
              FilterChip(
                selected = !showAllCategories && session.selectedCategory == category,
                onClick = {
                  if (category != ActionCategoryGroup.VARIABLE_OPS || isPro) {
                    showAllCategories = false
                  }
                  onIntent(ScriptEditorUiIntent.SelectCatalogCategory(category))
                },
                leadingIcon =
                  if (locked) {
                    {
                      Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Pro feature",
                        modifier = Modifier.size(14.dp),
                      )
                    }
                  } else {
                    null
                  },
                label = { Text(categoryTitle(category)) },
              )
            }
          }

          val visibleTemplates =
            remember(searchQuery, showAllCategories, session.selectedCategory) {
              val q = searchQuery.trim()
              val pool =
                if (q.isNotEmpty() || showAllCategories) {
                  ActionCategoryGroup.entries.flatMap { templatesForCategory(it) }
                } else {
                  templatesForCategory(session.selectedCategory)
                }
              if (q.isEmpty()) {
                pool
              } else {
                pool.filter {
                  it.label.contains(q, ignoreCase = true) ||
                    it.hint.contains(q, ignoreCase = true)
                }
              }
            }

          if (visibleTemplates.isEmpty()) {
            Text(
              text = "No matching actions found.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          } else {
            val rows = remember(visibleTemplates) { visibleTemplates.chunked(2) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              rows.forEach { pair ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  pair.forEach { spec ->
                    ActionCatalogTile(
                      spec = spec,
                      selected = spec.matcher(session.draftAction),
                      isPro = isPro,
                      onClick = {
                        onIntent(
                          ScriptEditorUiIntent.SelectTemplateAction(
                            spec.factory(session.draftAction.id),
                          ),
                        )
                      },
                      modifier = Modifier.weight(1f),
                    )
                  }
                  if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          Text(
            text = "Configure ${ActionTreeUtils.actionTypeTitle(session.draftAction)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        when (val draft = session.draftAction) {
          is ScriptAction.Tap ->
            TapActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
              validationErrors = session.validationErrors,
            )
          is ScriptAction.DoubleTap ->
            DoubleTapActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
              validationErrors = session.validationErrors,
            )
          is ScriptAction.LongPress ->
            LongPressActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
              validationErrors = session.validationErrors,
            )
          is ScriptAction.MultiTouch ->
            MultiTouchActionEditor(
              action = draft,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
              validationErrors = session.validationErrors,
            )
          is ScriptAction.SwipeDrag ->
            SwipeDragActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
              validationErrors = session.validationErrors,
            )
          is ScriptAction.InputText ->
            InputTextActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
              validationErrors = session.validationErrors,
            )
          is ScriptAction.IfBlock ->
            IfBlockActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
              validationErrors = session.validationErrors,
            )
          is ScriptAction.LoopBlock ->
            LoopBlockActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
              validationErrors = session.validationErrors,
            )
          is ScriptAction.Delay ->
            DelayActionEditor(
              action = draft,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
            )
          is ScriptAction.Break,
          is ScriptAction.Continue,
          is ScriptAction.EndScript -> SimpleFlowControlNotice(draft)
          is ScriptAction.KeyEvent ->
            KeyEventActionEditor(
              action = draft,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
            )
          is ScriptAction.LaunchApp ->
            AppLifecycleActionEditor(
              isForceStop = false,
              packageName = draft.packageName,
              appLabel = draft.appLabel,
              validationErrors = session.validationErrors,
              onOpenPicker = {
                onIntent(ScriptEditorUiIntent.OpenAppPicker(AppPickerTarget.ActionLaunchApp))
              },
            )
          is ScriptAction.ForceStopApp ->
            AppLifecycleActionEditor(
              isForceStop = true,
              packageName = draft.packageName,
              appLabel = draft.appLabel,
              validationErrors = session.validationErrors,
              onOpenPicker = {
                onIntent(ScriptEditorUiIntent.OpenAppPicker(AppPickerTarget.ActionForceStopApp))
              },
            )
          is ScriptAction.ClipboardOp ->
            ClipboardActionEditor(
              action = draft,
              variables = variables,
              validationErrors = session.validationErrors,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
            )
          is ScriptAction.Vibrate ->
            VibrateActionEditor(
              action = draft,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
            )
          is ScriptAction.PlayAlertSound ->
            PlayAlertSoundActionEditor(
              action = draft,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
            )
          is ScriptAction.VariableOp ->
            VariableOpActionEditor(
              action = draft,
              variables = variables,
              validationErrors = session.validationErrors,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
            )
        }

        if (session.draftAction.supportsPostActionDelay()) {
          val currentPostDelay = extractPostDelay(session.draftAction) ?: PostActionDelay.Global
          PostActionDelaySection(
            postDelay = currentPostDelay,
            globalDelay = globalDelay,
            isPro = isPro,
            onPostDelayChange = { nextDelay ->
              onIntent(
                ScriptEditorUiIntent.UpdateDraftAction(
                  withPostDelay(session.draftAction, nextDelay),
                ),
              )
            },
            onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          )
        } else {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Wait After This Step: 0ms (Flow, Wait & Variable steps run right away)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      if (session.showValidationErrors && session.validationErrors.isNotEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = session.validationErrors.values.first(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = { onIntent(ScriptEditorUiIntent.DismissActionSheet) },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text("Cancel")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = { onIntent(ScriptEditorUiIntent.CommitDraftAction) },
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("commit_action_button"),
        ) {
          Text(if (isEditingExisting) "Save Changes" else "Insert Action")
        }
      }
    }
  }
}

@Composable
private fun ActionCatalogTile(
  spec: ActionTemplateSpec,
  selected: Boolean,
  isPro: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val containerColor =
    if (selected) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.surfaceContainerLow
    }
  val titleColor =
    if (selected) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSurface
    }
  val subtitleColor =
    if (selected) {
      MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    }
  val border =
    if (selected) {
      BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
      BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = containerColor,
    contentColor = titleColor,
    border = border,
    modifier =
      modifier
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        ActionIconBadge(
          icon = spec.icon,
          category = spec.category,
          boxSize = 36.dp,
          iconSize = 18.dp,
          cornerRadius = 10.dp,
        )
        if (spec.isPro && !isPro) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
          ) {
            Text(
              text = "PRO",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = spec.label,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = titleColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = spec.hint,
          style = MaterialTheme.typography.bodySmall,
          color = subtitleColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun categoryTitle(category: ActionCategoryGroup): String =
  when (category) {
    ActionCategoryGroup.TOUCH_GESTURES -> "Touch"
    ActionCategoryGroup.FLOW_CONTROL -> "Flow"
    ActionCategoryGroup.SYSTEM_HARDWARE -> "System"
    ActionCategoryGroup.VARIABLE_OPS -> "Variables"
  }

private fun templatesForCategory(category: ActionCategoryGroup): List<ActionTemplateSpec> =
  when (category) {
    ActionCategoryGroup.TOUCH_GESTURES ->
      listOf(
        ActionTemplateSpec(
          label = "Tap",
          hint = "Tap one spot",
          icon = Icons.Rounded.TouchApp,
          category = ActionCategoryGroup.TOUCH_GESTURES,
          isPro = false,
          factory = { id ->
            ScriptAction.Tap(
              id = id,
              x = NumericInput.Literal(UNSET_COORD),
              y = NumericInput.Literal(UNSET_COORD),
            )
          },
          matcher = { it is ScriptAction.Tap },
        ),
        ActionTemplateSpec(
          label = "Double Tap",
          hint = "Two quick taps",
          icon = Icons.Rounded.AdsClick,
          category = ActionCategoryGroup.TOUCH_GESTURES,
          isPro = false,
          factory = { id ->
            ScriptAction.DoubleTap(
              id = id,
              x = NumericInput.Literal(UNSET_COORD),
              y = NumericInput.Literal(UNSET_COORD),
            )
          },
          matcher = { it is ScriptAction.DoubleTap },
        ),
        ActionTemplateSpec(
          label = "Long Press",
          hint = "Touch and hold",
          icon = Icons.Rounded.PanToolAlt,
          category = ActionCategoryGroup.TOUCH_GESTURES,
          isPro = false,
          factory = { id ->
            ScriptAction.LongPress(
              id = id,
              x = NumericInput.Literal(UNSET_COORD),
              y = NumericInput.Literal(UNSET_COORD),
            )
          },
          matcher = { it is ScriptAction.LongPress },
        ),
        ActionTemplateSpec(
          label = "Multi-Finger",
          hint = "Pinch, zoom, multi-tap",
          icon = Icons.Rounded.Pinch,
          category = ActionCategoryGroup.TOUCH_GESTURES,
          isPro = true,
          factory = { id ->
            ScriptAction.MultiTouch(id = id, centerX = UNSET_COORD, centerY = UNSET_COORD)
          },
          matcher = { it is ScriptAction.MultiTouch },
        ),
        ActionTemplateSpec(
          label = "Swipe & Drag",
          hint = "Straight or curved",
          icon = Icons.Rounded.Swipe,
          category = ActionCategoryGroup.TOUCH_GESTURES,
          isPro = false,
          factory = { id ->
            ScriptAction.SwipeDrag(
              id = id,
              startX = NumericInput.Literal(UNSET_COORD),
              startY = NumericInput.Literal(UNSET_COORD),
              endX = NumericInput.Literal(UNSET_COORD),
              endY = NumericInput.Literal(UNSET_COORD),
            )
          },
          matcher = { it is ScriptAction.SwipeDrag },
        ),
        ActionTemplateSpec(
          label = "Type Text",
          hint = "Type or paste text",
          icon = Icons.Rounded.Keyboard,
          category = ActionCategoryGroup.TOUCH_GESTURES,
          isPro = false,
          factory = { id -> ScriptAction.InputText(id = id) },
          matcher = { it is ScriptAction.InputText },
        ),
      )

    ActionCategoryGroup.FLOW_CONTROL ->
      listOf(
        ActionTemplateSpec(
          label = "If Block",
          hint = "Run steps if rule matches",
          icon = Icons.AutoMirrored.Rounded.AltRoute,
          category = ActionCategoryGroup.FLOW_CONTROL,
          isPro = true,
          factory = { id ->
            ScriptAction.IfBlock(
              id = id,
              conditionRoot =
                ConditionGroup(id = UUID.randomUUID().toString(), name = "When..."),
            )
          },
          matcher = { it is ScriptAction.IfBlock },
        ),
        ActionTemplateSpec(
          label = "Repeat (Loop)",
          hint = "Repeat steps",
          icon = Icons.Rounded.Repeat,
          category = ActionCategoryGroup.FLOW_CONTROL,
          isPro = false,
          factory = { id -> ScriptAction.LoopBlock(id = id, mode = LoopMode.Count()) },
          matcher = { it is ScriptAction.LoopBlock },
        ),
        ActionTemplateSpec(
          label = "Wait / Pause",
          hint = "Pause before next step",
          icon = Icons.Rounded.HourglassBottom,
          category = ActionCategoryGroup.FLOW_CONTROL,
          isPro = false,
          factory = { id -> ScriptAction.Delay(id = id, config = DelayConfig.Fixed(1000L)) },
          matcher = { it is ScriptAction.Delay },
        ),
        ActionTemplateSpec(
          label = "Exit Loop",
          hint = "Stop repeating now",
          icon = Icons.AutoMirrored.Rounded.ExitToApp,
          category = ActionCategoryGroup.FLOW_CONTROL,
          isPro = false,
          factory = { id -> ScriptAction.Break(id = id) },
          matcher = { it is ScriptAction.Break },
        ),
        ActionTemplateSpec(
          label = "Next Repeat",
          hint = "Skip to next turn",
          icon = Icons.Rounded.SkipNext,
          category = ActionCategoryGroup.FLOW_CONTROL,
          isPro = false,
          factory = { id -> ScriptAction.Continue(id = id) },
          matcher = { it is ScriptAction.Continue },
        ),
        ActionTemplateSpec(
          label = "Stop Script",
          hint = "End script run",
          icon = Icons.Rounded.OutlinedFlag,
          category = ActionCategoryGroup.FLOW_CONTROL,
          isPro = false,
          factory = { id -> ScriptAction.EndScript(id = id) },
          matcher = { it is ScriptAction.EndScript },
        ),
      )

    ActionCategoryGroup.SYSTEM_HARDWARE ->
      listOf(
        ActionTemplateSpec(
          label = "Phone Button",
          hint = "Back, Home, Recent Apps",
          icon = Icons.Rounded.Smartphone,
          category = ActionCategoryGroup.SYSTEM_HARDWARE,
          isPro = false,
          factory = { id -> ScriptAction.KeyEvent(id = id, key = SystemKeyType.BACK) },
          matcher = { it is ScriptAction.KeyEvent },
        ),
        ActionTemplateSpec(
          label = "Open App",
          hint = "Open installed app",
          icon = Icons.AutoMirrored.Rounded.OpenInNew,
          category = ActionCategoryGroup.SYSTEM_HARDWARE,
          isPro = false,
          factory = { id -> ScriptAction.LaunchApp(id = id, packageName = "") },
          matcher = { it is ScriptAction.LaunchApp },
        ),
        ActionTemplateSpec(
          label = "Force Stop App",
          hint = "Close target app",
          icon = Icons.Rounded.Cancel,
          category = ActionCategoryGroup.SYSTEM_HARDWARE,
          isPro = true,
          factory = { id -> ScriptAction.ForceStopApp(id = id, packageName = "") },
          matcher = { it is ScriptAction.ForceStopApp },
        ),
        ActionTemplateSpec(
          label = "Clipboard",
          hint = "Copy, paste, clear",
          icon = Icons.Rounded.ContentPaste,
          category = ActionCategoryGroup.SYSTEM_HARDWARE,
          isPro = false,
          factory = { id ->
            ScriptAction.ClipboardOp(id = id, operation = ClipboardActionType.PASTE)
          },
          matcher = { it is ScriptAction.ClipboardOp },
        ),
        ActionTemplateSpec(
          label = "Vibrate",
          hint = "Phone vibration",
          icon = Icons.Rounded.Vibration,
          category = ActionCategoryGroup.SYSTEM_HARDWARE,
          isPro = false,
          factory = { id -> ScriptAction.Vibrate(id = id) },
          matcher = { it is ScriptAction.Vibrate },
        ),
        ActionTemplateSpec(
          label = "Alert Sound",
          hint = "Play sound alert",
          icon = Icons.Rounded.NotificationsActive,
          category = ActionCategoryGroup.SYSTEM_HARDWARE,
          isPro = false,
          factory = { id -> ScriptAction.PlayAlertSound(id = id) },
          matcher = { it is ScriptAction.PlayAlertSound },
        ),
      )

    ActionCategoryGroup.VARIABLE_OPS ->
      listOf(
        ActionTemplateSpec(
          label = "Save Value",
          hint = "Store a value",
          icon = Icons.Rounded.DataObject,
          category = ActionCategoryGroup.VARIABLE_OPS,
          isPro = true,
          factory = { id ->
            ScriptAction.SetVariable(id = id, targetKey = "", valueTemplate = "")
          },
          matcher = { it is ScriptAction.SetVariable },
        ),
        ActionTemplateSpec(
          label = "Calculate (Math)",
          hint = "Add, subtract, divide",
          icon = Icons.Rounded.Calculate,
          category = ActionCategoryGroup.VARIABLE_OPS,
          isPro = true,
          factory = { id ->
            ScriptAction.MathOp(
              id = id,
              targetKey = "",
              operator = MathOperator.ADD,
              leftOperand = "",
              rightOperand = "",
            )
          },
          matcher = { it is ScriptAction.MathOp },
        ),
        ActionTemplateSpec(
          label = "Random Value",
          hint = "Random number or Yes/No",
          icon = Icons.Rounded.Casino,
          category = ActionCategoryGroup.VARIABLE_OPS,
          isPro = true,
          factory = { id ->
            ScriptAction.RandomOp(
              id = id,
              targetKey = "",
              targetType = RandomTargetType.INT,
              minValue = 1.0,
              maxValue = 100.0,
            )
          },
          matcher = { it is ScriptAction.RandomOp },
        ),
        ActionTemplateSpec(
          label = "Edit Text",
          hint = "Join, trim, replace text",
          icon = Icons.Rounded.TextFields,
          category = ActionCategoryGroup.VARIABLE_OPS,
          isPro = true,
          factory = { id ->
            ScriptAction.StringOp(
              id = id,
              targetKey = "",
              operator = StringOperator.CONCATENATE,
              inputA = "",
              inputB = "",
            )
          },
          matcher = { it is ScriptAction.StringOp },
        ),
        ActionTemplateSpec(
          label = "Yes / No Logic",
          hint = "Flip or combine flags",
          icon = Icons.Rounded.ToggleOn,
          category = ActionCategoryGroup.VARIABLE_OPS,
          isPro = true,
          factory = { id ->
            ScriptAction.BooleanOp(
              id = id,
              targetKey = "",
              operator = BooleanOperator.TOGGLE,
              leftKey = "",
            )
          },
          matcher = { it is ScriptAction.BooleanOp },
        ),
        ActionTemplateSpec(
          label = "Convert Value",
          hint = "Text to number & back",
          icon = Icons.Rounded.SwapHoriz,
          category = ActionCategoryGroup.VARIABLE_OPS,
          isPro = true,
          factory = { id ->
            ScriptAction.TypeCastOp(
              id = id,
              targetKey = "",
              sourceKey = "",
              operator = TypeCastOperator.NUMBER_TO_STRING,
            )
          },
          matcher = { it is ScriptAction.TypeCastOp },
        ),
      )
  }

private fun extractPostDelay(action: ScriptAction): PostActionDelay? =
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
    else -> null
  }

private fun withPostDelay(action: ScriptAction, next: PostActionDelay): ScriptAction =
  when (action) {
    is ScriptAction.Tap -> action.copy(postDelay = next)
    is ScriptAction.DoubleTap -> action.copy(postDelay = next)
    is ScriptAction.LongPress -> action.copy(postDelay = next)
    is ScriptAction.MultiTouch -> action.copy(postDelay = next)
    is ScriptAction.SwipeDrag -> action.copy(postDelay = next)
    is ScriptAction.InputText -> action.copy(postDelay = next)
    is ScriptAction.KeyEvent -> action.copy(postDelay = next)
    is ScriptAction.LaunchApp -> action.copy(postDelay = next)
    is ScriptAction.ForceStopApp -> action.copy(postDelay = next)
    is ScriptAction.ClipboardOp -> action.copy(postDelay = next)
    else -> action
  }
