package com.tapflow.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

  ModalBottomSheet(
    onDismissRequest = { onIntent(ScriptEditorUiIntent.DismissActionSheet) },
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
        val categories = ActionCategoryGroup.entries
        val selectedIdx = categories.indexOf(session.selectedCategory).coerceAtLeast(0)
        ScrollableTabRow(
          selectedTabIndex = selectedIdx,
          edgePadding = 0.dp,
        ) {
          categories.forEach { category ->
            val locked = category == ActionCategoryGroup.VARIABLE_OPS && !isPro
            Tab(
              selected = session.selectedCategory == category,
              onClick = { onIntent(ScriptEditorUiIntent.SelectCatalogCategory(category)) },
              text = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  if (locked) {
                    Icon(
                      imageVector = Icons.Filled.Lock,
                      contentDescription = "Pro feature",
                      modifier = Modifier.size(14.dp),
                    )
                  }
                  Text(categoryTitle(category))
                }
              },
            )
          }
        }

        val templates = templatesForCategory(session.selectedCategory)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          templates.forEach { spec ->
            FilterChip(
              selected = spec.matcher(session.draftAction),
              onClick = {
                onIntent(
                  ScriptEditorUiIntent.SelectTemplateAction(
                    spec.factory(session.draftAction.id),
                  ),
                )
              },
              leadingIcon =
                if (spec.isPro && !isPro) {
                  {
                    Icon(
                      imageVector = Icons.Filled.Lock,
                      contentDescription = "Pro feature",
                      modifier = Modifier.size(14.dp),
                    )
                  }
                } else {
                  null
                },
              label = { Text(spec.label) },
            )
          }
        }
      }

      HorizontalDivider()

      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (val draft = session.draftAction) {
          is ScriptAction.Tap ->
            TapActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
            )
          is ScriptAction.DoubleTap ->
            DoubleTapActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
            )
          is ScriptAction.LongPress ->
            LongPressActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
            )
          is ScriptAction.MultiTouch ->
            MultiTouchActionEditor(
              action = draft,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
            )
          is ScriptAction.SwipeDrag ->
            SwipeDragActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
            )
          is ScriptAction.InputText ->
            InputTextActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
            )
          is ScriptAction.IfBlock ->
            IfBlockActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
            )
          is ScriptAction.LoopBlock ->
            LoopBlockActionEditor(
              action = draft,
              variables = variables,
              isPro = isPro,
              onChange = { onIntent(ScriptEditorUiIntent.UpdateDraftAction(it)) },
              onIntent = onIntent,
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
              onOpenPicker = {
                onIntent(ScriptEditorUiIntent.OpenAppPicker(AppPickerTarget.ActionLaunchApp))
              },
            )
          is ScriptAction.ForceStopApp ->
            AppLifecycleActionEditor(
              isForceStop = true,
              packageName = draft.packageName,
              appLabel = draft.appLabel,
              onOpenPicker = {
                onIntent(ScriptEditorUiIntent.OpenAppPicker(AppPickerTarget.ActionForceStopApp))
              },
            )
          is ScriptAction.ClipboardOp ->
            ClipboardActionEditor(
              action = draft,
              variables = variables,
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
          Text(
            text = "Post-Action Delay: 0ms (Excluded for Flow Control, Delay & Variable Ops)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider()

      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = { onIntent(ScriptEditorUiIntent.DismissActionSheet) }) {
          Text("Cancel")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = { onIntent(ScriptEditorUiIntent.CommitDraftAction) },
          modifier = Modifier.heightIn(min = 48.dp).testTag("commit_action_button"),
        ) {
          Text(if (isEditingExisting) "Save Changes" else "Insert Action")
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
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
          isPro = false,
          factory = { id ->
            ScriptAction.Tap(id = id, x = NumericInput.Literal(540), y = NumericInput.Literal(960))
          },
          matcher = { it is ScriptAction.Tap },
        ),
        ActionTemplateSpec(
          label = "Double Tap",
          isPro = false,
          factory = { id ->
            ScriptAction.DoubleTap(
              id = id,
              x = NumericInput.Literal(540),
              y = NumericInput.Literal(960),
            )
          },
          matcher = { it is ScriptAction.DoubleTap },
        ),
        ActionTemplateSpec(
          label = "Long Press",
          isPro = false,
          factory = { id ->
            ScriptAction.LongPress(
              id = id,
              x = NumericInput.Literal(540),
              y = NumericInput.Literal(960),
            )
          },
          matcher = { it is ScriptAction.LongPress },
        ),
        ActionTemplateSpec(
          label = "Multi-touch",
          isPro = true,
          factory = { id -> ScriptAction.MultiTouch(id = id, centerX = 540, centerY = 960) },
          matcher = { it is ScriptAction.MultiTouch },
        ),
        ActionTemplateSpec(
          label = "Swipe & Drag",
          isPro = false,
          factory = { id ->
            ScriptAction.SwipeDrag(
              id = id,
              startX = NumericInput.Literal(540),
              startY = NumericInput.Literal(1400),
              endX = NumericInput.Literal(540),
              endY = NumericInput.Literal(600),
            )
          },
          matcher = { it is ScriptAction.SwipeDrag },
        ),
        ActionTemplateSpec(
          label = "Input Text",
          isPro = false,
          factory = { id -> ScriptAction.InputText(id = id) },
          matcher = { it is ScriptAction.InputText },
        ),
      )

    ActionCategoryGroup.FLOW_CONTROL ->
      listOf(
        ActionTemplateSpec(
          label = "If Block",
          isPro = true,
          factory = { id ->
            ScriptAction.IfBlock(
              id = id,
              conditionRoot =
                ConditionGroup(id = UUID.randomUUID().toString(), name = "If Condition Root"),
            )
          },
          matcher = { it is ScriptAction.IfBlock },
        ),
        ActionTemplateSpec(
          label = "Loop Block",
          isPro = false,
          factory = { id -> ScriptAction.LoopBlock(id = id, mode = LoopMode.Count()) },
          matcher = { it is ScriptAction.LoopBlock },
        ),
        ActionTemplateSpec(
          label = "Delay",
          isPro = false,
          factory = { id -> ScriptAction.Delay(id = id, config = DelayConfig.Fixed(500L)) },
          matcher = { it is ScriptAction.Delay },
        ),
        ActionTemplateSpec(
          label = "Break",
          isPro = false,
          factory = { id -> ScriptAction.Break(id = id) },
          matcher = { it is ScriptAction.Break },
        ),
        ActionTemplateSpec(
          label = "Continue",
          isPro = false,
          factory = { id -> ScriptAction.Continue(id = id) },
          matcher = { it is ScriptAction.Continue },
        ),
        ActionTemplateSpec(
          label = "End Script",
          isPro = false,
          factory = { id -> ScriptAction.EndScript(id = id) },
          matcher = { it is ScriptAction.EndScript },
        ),
      )

    ActionCategoryGroup.SYSTEM_HARDWARE ->
      listOf(
        ActionTemplateSpec(
          label = "Key Event",
          isPro = false,
          factory = { id -> ScriptAction.KeyEvent(id = id, key = SystemKeyType.BACK) },
          matcher = { it is ScriptAction.KeyEvent },
        ),
        ActionTemplateSpec(
          label = "Launch App",
          isPro = false,
          factory = { id -> ScriptAction.LaunchApp(id = id, packageName = "") },
          matcher = { it is ScriptAction.LaunchApp },
        ),
        ActionTemplateSpec(
          label = "Force Stop",
          isPro = true,
          factory = { id -> ScriptAction.ForceStopApp(id = id, packageName = "") },
          matcher = { it is ScriptAction.ForceStopApp },
        ),
        ActionTemplateSpec(
          label = "Clipboard",
          isPro = false,
          factory = { id ->
            ScriptAction.ClipboardOp(id = id, operation = ClipboardActionType.PASTE)
          },
          matcher = { it is ScriptAction.ClipboardOp },
        ),
        ActionTemplateSpec(
          label = "Vibrate",
          isPro = false,
          factory = { id -> ScriptAction.Vibrate(id = id) },
          matcher = { it is ScriptAction.Vibrate },
        ),
        ActionTemplateSpec(
          label = "Alert Sound",
          isPro = false,
          factory = { id -> ScriptAction.PlayAlertSound(id = id) },
          matcher = { it is ScriptAction.PlayAlertSound },
        ),
      )

    ActionCategoryGroup.VARIABLE_OPS ->
      listOf(
        ActionTemplateSpec(
          label = "Set Variable",
          isPro = true,
          factory = { id ->
            ScriptAction.SetVariable(id = id, targetKey = "counter", valueTemplate = "1")
          },
          matcher = { it is ScriptAction.SetVariable },
        ),
        ActionTemplateSpec(
          label = "Math",
          isPro = true,
          factory = { id ->
            ScriptAction.MathOp(
              id = id,
              targetKey = "counter",
              operator = MathOperator.ADD,
              leftOperand = "{counter}",
              rightOperand = "1",
            )
          },
          matcher = { it is ScriptAction.MathOp },
        ),
        ActionTemplateSpec(
          label = "Random",
          isPro = true,
          factory = { id ->
            ScriptAction.RandomOp(
              id = id,
              targetKey = "rand_val",
              targetType = RandomTargetType.INT,
              minValue = 1.0,
              maxValue = 100.0,
            )
          },
          matcher = { it is ScriptAction.RandomOp },
        ),
        ActionTemplateSpec(
          label = "String",
          isPro = true,
          factory = { id ->
            ScriptAction.StringOp(
              id = id,
              targetKey = "text_var",
              operator = StringOperator.CONCATENATE,
              inputA = "{text_var}",
              inputB = "",
            )
          },
          matcher = { it is ScriptAction.StringOp },
        ),
        ActionTemplateSpec(
          label = "Boolean",
          isPro = true,
          factory = { id ->
            ScriptAction.BooleanOp(
              id = id,
              targetKey = "flag",
              operator = BooleanOperator.TOGGLE,
              leftKey = "flag",
            )
          },
          matcher = { it is ScriptAction.BooleanOp },
        ),
        ActionTemplateSpec(
          label = "Type Cast",
          isPro = true,
          factory = { id ->
            ScriptAction.TypeCastOp(
              id = id,
              targetKey = "cast_val",
              sourceKey = "counter",
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
