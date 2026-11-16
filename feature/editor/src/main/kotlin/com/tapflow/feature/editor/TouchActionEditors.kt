package com.tapflow.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.LongPressMode
import com.tapflow.core.model.action.MultiTouchMode
import com.tapflow.core.model.action.MultiTouchPoint
import com.tapflow.core.model.action.Point2D
import com.tapflow.core.model.action.PostTextActionKey
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.SwipeTrajectoryMode
import com.tapflow.core.model.action.TextInjectionEngine
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.VariableDefinition
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.service.overlay.GesturePickerTargetType
import java.util.UUID

@Composable
fun TapActionEditor(
  action: ScriptAction.Tap,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.Tap) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Where to Tap") {
      OverlayPickButton(
        label = "Pick Spot on Screen",
        onClick = {
          onIntent(
            ScriptEditorUiIntent.RequestOverlayPick(
              purpose = OverlayPickPurpose.ActionCoordinates,
              targetType = GesturePickerTargetType.POINT,
            ),
          )
        },
        testTag = "pick_tap_overlay_button",
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumericInputField(
          label = "X (px)",
          value = action.x,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(x = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          errorText = validationErrors["x"],
          placeholder = "e.g. 540",
          modifier = Modifier.weight(1f),
        )
        NumericInputField(
          label = "Y (px)",
          value = action.y,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(y = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          errorText = validationErrors["y"],
          placeholder = "e.g. 960",
          modifier = Modifier.weight(1f),
        )
      }

      IntTextField(
        label = "Random Tap Offset (Anti-Detection)",
        value = action.jitterPx,
        min = 0,
        onChange = { onChange(action.copy(jitterPx = it.coerceIn(0, 300))) },
        supportingHint = "Adds slight randomness so taps look human (0 = exact spot)",
        modifier = Modifier.fillMaxWidth(),
      )
    }

    EditorSectionCard(title = "Timing & Repeat") {
      DurationInputField(
        label = "Hold Time (ms)",
        value = action.pressDuration,
        variables = variables,
        isPro = isPro,
        onValueChange = { onChange(action.copy(pressDuration = it)) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        suggestedHint = "Suggested: 50 ms",
      )

      NumericInputField(
        label = "How Many Taps",
        value = action.repeatCount,
        variables = variables,
        isPro = isPro,
        onValueChange = { next ->
          val safe =
            if (next is NumericInput.Literal) {
              if (next.value == UNSET_COORD) next else NumericInput.Literal(next.value.coerceAtLeast(1))
            } else {
              next
            }
          onChange(action.copy(repeatCount = safe))
        },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        errorText = validationErrors["repeat_count"],
        supportingHint = "Suggested: 1 tap",
      )

      DurationInputField(
        label = "Wait Between Taps (ms)",
        value = action.repeatInterval,
        variables = variables,
        isPro = isPro,
        onValueChange = { onChange(action.copy(repeatInterval = it)) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        suggestedHint = "Suggested: 100 ms",
      )
    }
  }
}

@Composable
fun DoubleTapActionEditor(
  action: ScriptAction.DoubleTap,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.DoubleTap) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Where to Double-Tap") {
      OverlayPickButton(
        label = "Pick Spot on Screen",
        onClick = {
          onIntent(
            ScriptEditorUiIntent.RequestOverlayPick(
              purpose = OverlayPickPurpose.ActionCoordinates,
              targetType = GesturePickerTargetType.POINT,
            ),
          )
        },
        testTag = "pick_double_tap_overlay_button",
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumericInputField(
          label = "X (px)",
          value = action.x,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(x = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          errorText = validationErrors["x"],
          placeholder = "e.g. 540",
          modifier = Modifier.weight(1f),
        )
        NumericInputField(
          label = "Y (px)",
          value = action.y,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(y = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          errorText = validationErrors["y"],
          placeholder = "e.g. 960",
          modifier = Modifier.weight(1f),
        )
      }

      IntTextField(
        label = "Random Tap Offset (Anti-Detection)",
        value = action.jitterPx,
        min = 0,
        onChange = { onChange(action.copy(jitterPx = it.coerceIn(0, 300))) },
        supportingHint = "Adds slight randomness so taps look human (0 = exact spot)",
        modifier = Modifier.fillMaxWidth(),
      )
    }

    EditorSectionCard(title = "Tap Timing") {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LongTextField(
          label = "Hold Time (ms)",
          value = action.pressDurationMs,
          min = 10L,
          onChange = { onChange(action.copy(pressDurationMs = it)) },
          supportingHint = "Suggested: 50 ms",
          modifier = Modifier.weight(1f),
        )
        LongTextField(
          label = "Pause Between Taps (ms)",
          value = action.tapIntervalMs,
          min = 20L,
          onChange = { onChange(action.copy(tapIntervalMs = it)) },
          supportingHint = "Suggested: 100 ms",
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
fun LongPressActionEditor(
  action: ScriptAction.LongPress,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.LongPress) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Hold Style") {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        LongPressMode.entries.forEach { mode ->
          FilterChip(
            selected = action.mode == mode,
            onClick = {
              val nextHoldCond =
                if (mode == LongPressMode.HOLD_UNTIL && action.holdUntilCondition == null) {
                  ConditionGroup(id = UUID.randomUUID().toString(), name = "Stop Holding When")
                } else {
                  action.holdUntilCondition
                }
              onChange(action.copy(mode = mode, holdUntilCondition = nextHoldCond))
            },
            label = {
              Text(
                when (mode) {
                  LongPressMode.STANDARD -> "Press & Hold"
                  LongPressMode.PRESS_DOWN_ONLY -> "Press Down Only"
                  LongPressMode.RELEASE_ONLY -> "Release Only"
                  LongPressMode.HOLD_UNTIL -> "Hold Until Rule Passes"
                },
              )
            },
          )
        }
      }
    }

    if (action.mode != LongPressMode.RELEASE_ONLY) {
      EditorSectionCard(title = "Where & How Long to Hold") {
        OverlayPickButton(
          label = "Pick Spot on Screen",
          onClick = {
            onIntent(
              ScriptEditorUiIntent.RequestOverlayPick(
                purpose = OverlayPickPurpose.ActionCoordinates,
                targetType = GesturePickerTargetType.LONG_PRESS,
              ),
            )
          },
          testTag = "pick_long_press_overlay_button",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumericInputField(
            label = "X (px)",
            value = action.x,
            variables = variables,
            isPro = isPro,
            onValueChange = { onChange(action.copy(x = it)) },
            onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
            errorText = validationErrors["x"],
            placeholder = "e.g. 540",
            modifier = Modifier.weight(1f),
          )
          NumericInputField(
            label = "Y (px)",
            value = action.y,
            variables = variables,
            isPro = isPro,
            onValueChange = { onChange(action.copy(y = it)) },
            onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
            errorText = validationErrors["y"],
            placeholder = "e.g. 960",
            modifier = Modifier.weight(1f),
          )
        }

        IntTextField(
          label = "Random Tap Offset (Anti-Detection)",
          value = action.jitterPx,
          min = 0,
          onChange = { onChange(action.copy(jitterPx = it.coerceIn(0, 300))) },
          supportingHint = "Adds slight randomness so taps look human (0 = exact spot)",
          modifier = Modifier.fillMaxWidth(),
        )

        if (action.mode == LongPressMode.STANDARD) {
          LongTextField(
            label = "Hold Time (ms, supports > 60s)",
            value = action.pressDurationMs,
            min = 100L,
            onChange = { onChange(action.copy(pressDurationMs = it)) },
            supportingHint = "Suggested: 800 ms",
            modifier = Modifier.fillMaxWidth(),
          )
        }

        if (action.mode == LongPressMode.HOLD_UNTIL) {
          LongTextField(
            label = "Max Hold Time Limit (ms)",
            value = action.holdTimeoutMs,
            min = 500L,
            onChange = { onChange(action.copy(holdTimeoutMs = it)) },
            supportingHint = "Suggested: 10000 ms (releases automatically after timeout)",
            modifier = Modifier.fillMaxWidth(),
          )
          validationErrors["hold_condition"]?.let { err ->
            Text(
              text = err,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
          val condRoot =
            action.holdUntilCondition
              ?: ConditionGroup(id = UUID.randomUUID().toString(), name = "Stop Holding When")
          ConditionGroupCardEditor(
            root = condRoot,
            currentGroup = condRoot,
            depth = 1,
            isPro = isPro,
            variables = variables,
            onRootChange = { updated -> onChange(action.copy(holdUntilCondition = updated)) },
            onIntent = onIntent,
            validationErrors = validationErrors,
            previewPrefix = "Stop holding when:",
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Finger Tremble (Anti-Detection)",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = "Adds tiny natural finger movement while holding",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = action.microMovementJitter,
            onCheckedChange = { checked ->
              onChange(action.copy(microMovementJitter = checked))
            },
          )
        }

        if (action.microMovementJitter) {
          IntTextField(
            label = "Tremble Distance (px)",
            value = action.microJitterAmplitudePx,
            min = 1,
            onChange = { onChange(action.copy(microJitterAmplitudePx = it.coerceIn(1, 30))) },
            supportingHint = "Suggested: 3 px",
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiTouchActionEditor(
  action: ScriptAction.MultiTouch,
  onChange: (ScriptAction.MultiTouch) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Gesture Style") {
      OverlayPickButton(
        label = "Pick Spot on Screen",
        onClick = {
          onIntent(
            ScriptEditorUiIntent.RequestOverlayPick(
              purpose = OverlayPickPurpose.ActionCoordinates,
              targetType = GesturePickerTargetType.MULTI_TOUCH_ANCHOR,
            ),
          )
        },
        testTag = "pick_multitouch_overlay_button",
      )

      val modes = MultiTouchMode.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
          val label =
            when (mode) {
              MultiTouchMode.CUSTOM_POINTS -> "Multi-Finger"
              MultiTouchMode.PINCH_SPREAD -> "Pinch / Zoom"
              MultiTouchMode.ROTATE -> "Rotate"
            }
          SegmentedButton(
            selected = action.mode == mode,
            onClick = { onChange(action.copy(mode = mode)) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
          ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }

    when (action.mode) {
      MultiTouchMode.CUSTOM_POINTS -> {
        EditorSectionCard(
          title = "Finger Spots (${action.customPoints.size}/10)",
          trailing = {
            OutlinedButton(
              onClick = {
                if (action.customPoints.size < 10) {
                  onChange(
                    action.copy(
                      customPoints =
                        action.customPoints + MultiTouchPoint(x = UNSET_COORD, y = UNSET_COORD),
                    ),
                  )
                }
              },
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add Finger")
            }
          },
        ) {
          validationErrors["custom_points"]?.let { err ->
            Text(
              text = err,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
          if (action.customPoints.isEmpty()) {
            Text(
              text = "Tap 'Add Finger' to place where each finger should touch.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          action.customPoints.forEachIndexed { idx, pt ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Finger #${idx + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                  )
                  IconButton(
                    onClick = {
                      onChange(
                        action.copy(
                          customPoints = action.customPoints.filterIndexed { i, _ -> i != idx },
                        ),
                      )
                    },
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.DeleteOutline,
                      contentDescription = "Remove finger spot",
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  IntTextField(
                    label = "X",
                    value = pt.x,
                    min = 0,
                    onChange = { next ->
                      onChange(
                        action.copy(
                          customPoints =
                            action.customPoints.mapIndexed { i, p ->
                              if (i == idx) p.copy(x = next) else p
                            },
                        ),
                      )
                    },
                    errorText = validationErrors["finger_${idx}_x"],
                    unsetValue = UNSET_COORD,
                    placeholder = "X",
                    modifier = Modifier.weight(1f),
                  )
                  IntTextField(
                    label = "Y",
                    value = pt.y,
                    min = 0,
                    onChange = { next ->
                      onChange(
                        action.copy(
                          customPoints =
                            action.customPoints.mapIndexed { i, p ->
                              if (i == idx) p.copy(y = next) else p
                            },
                        ),
                      )
                    },
                    errorText = validationErrors["finger_${idx}_y"],
                    unsetValue = UNSET_COORD,
                    placeholder = "Y",
                    modifier = Modifier.weight(1f),
                  )
                  IntTextField(
                    label = "Random Offset",
                    value = pt.jitterPx,
                    min = 0,
                    onChange = { next ->
                      onChange(
                        action.copy(
                          customPoints =
                            action.customPoints.mapIndexed { i, p ->
                              if (i == idx) p.copy(jitterPx = next) else p
                            },
                        ),
                      )
                    },
                    modifier = Modifier.weight(1f),
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  LongTextField(
                    label = "Hold Time (ms)",
                    value = pt.pressDurationMs,
                    min = 10L,
                    onChange = { next ->
                      onChange(
                        action.copy(
                          customPoints =
                            action.customPoints.mapIndexed { i, p ->
                              if (i == idx) p.copy(pressDurationMs = next) else p
                            },
                        ),
                      )
                    },
                    supportingHint = "Suggested: 80 ms",
                    modifier = Modifier.weight(1f),
                  )
                  LongTextField(
                    label = "Finger Timing Offset (ms)",
                    value = pt.staggerDelayMs,
                    min = 0L,
                    onChange = { next ->
                      onChange(
                        action.copy(
                          customPoints =
                            action.customPoints.mapIndexed { i, p ->
                              if (i == idx) p.copy(staggerDelayMs = next) else p
                            },
                        ),
                      )
                    },
                    supportingHint = "0 = press together",
                    modifier = Modifier.weight(1f),
                  )
                }
              }
            }
          }
        }
      }

      MultiTouchMode.PINCH_SPREAD -> {
        EditorSectionCard(title = "Pinch / Zoom Setup") {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntTextField(
              label = "Center X",
              value = action.centerX,
              min = 0,
              onChange = { onChange(action.copy(centerX = it)) },
              errorText = validationErrors["center_x"],
              unsetValue = UNSET_COORD,
              placeholder = "e.g. 540",
              modifier = Modifier.weight(1f),
            )
            IntTextField(
              label = "Center Y",
              value = action.centerY,
              min = 0,
              onChange = { onChange(action.copy(centerY = it)) },
              errorText = validationErrors["center_y"],
              unsetValue = UNSET_COORD,
              placeholder = "e.g. 960",
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntTextField(
              label = "Start Width (px)",
              value = action.startDistancePx,
              min = 10,
              onChange = { onChange(action.copy(startDistancePx = it)) },
              supportingHint = "Suggested: 120 px",
              modifier = Modifier.weight(1f),
            )
            IntTextField(
              label = "End Width (px)",
              value = action.endDistancePx,
              min = 10,
              onChange = { onChange(action.copy(endDistancePx = it)) },
              supportingHint = "Suggested: 420 px",
              modifier = Modifier.weight(1f),
            )
          }
          LongTextField(
            label = "Swipe Time (ms)",
            value = action.durationMs,
            min = 50L,
            onChange = { onChange(action.copy(durationMs = it)) },
            supportingHint = "Suggested: 350 ms",
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      MultiTouchMode.ROTATE -> {
        EditorSectionCard(title = "Two-Finger Rotate Setup") {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntTextField(
              label = "Center X",
              value = action.centerX,
              min = 0,
              onChange = { onChange(action.copy(centerX = it)) },
              errorText = validationErrors["center_x"],
              unsetValue = UNSET_COORD,
              placeholder = "e.g. 540",
              modifier = Modifier.weight(1f),
            )
            IntTextField(
              label = "Center Y",
              value = action.centerY,
              min = 0,
              onChange = { onChange(action.copy(centerY = it)) },
              errorText = validationErrors["center_y"],
              unsetValue = UNSET_COORD,
              placeholder = "e.g. 960",
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatTextField(
              label = "Start Angle (°)",
              value = action.startAngleDeg,
              onChange = { onChange(action.copy(startAngleDeg = it)) },
              modifier = Modifier.weight(1f),
            )
            FloatTextField(
              label = "End Angle (°)",
              value = action.endAngleDeg,
              onChange = { onChange(action.copy(endAngleDeg = it)) },
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntTextField(
              label = "Finger Radius (px)",
              value = action.radiusPx,
              min = 20,
              onChange = { onChange(action.copy(radiusPx = it)) },
              supportingHint = "Suggested: 180 px",
              modifier = Modifier.weight(1f),
            )
            LongTextField(
              label = "Swipe Time (ms)",
              value = action.durationMs,
              min = 50L,
              onChange = { onChange(action.copy(durationMs = it)) },
              supportingHint = "Suggested: 350 ms",
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDragActionEditor(
  action: ScriptAction.SwipeDrag,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.SwipeDrag) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Swipe Style") {
      OverlayPickButton(
        label = "Draw Swipe on Screen",
        onClick = {
          onIntent(
            ScriptEditorUiIntent.RequestOverlayPick(
              purpose = OverlayPickPurpose.ActionCoordinates,
              targetType = GesturePickerTargetType.SWIPE_PATH,
            ),
          )
        },
        testTag = "pick_swipe_overlay_button",
      )

      val modes = SwipeTrajectoryMode.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
          val label =
            when (mode) {
              SwipeTrajectoryMode.LINEAR -> "Straight Line"
              SwipeTrajectoryMode.BEZIER_CURVE -> "Curved"
              SwipeTrajectoryMode.MULTI_POINT_PATH -> "Custom Path"
            }
          SegmentedButton(
            selected = action.trajectoryMode == mode,
            onClick = { onChange(action.copy(trajectoryMode = mode)) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            icon = { SegmentedButtonDefaults.Icon(active = action.trajectoryMode == mode) },
          ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
      if (action.trajectoryMode == SwipeTrajectoryMode.BEZIER_CURVE) {
        Text(
          text = "Swipes along a smooth arc so movement looks natural.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    EditorSectionCard(title = "Start & End Spots") {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumericInputField(
          label = "Start X",
          value = action.startX,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(startX = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          errorText = validationErrors["start_x"],
          placeholder = "e.g. 540",
          modifier = Modifier.weight(1f),
        )
        NumericInputField(
          label = "Start Y",
          value = action.startY,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(startY = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          errorText = validationErrors["start_y"],
          placeholder = "e.g. 1400",
          modifier = Modifier.weight(1f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumericInputField(
          label = "End X",
          value = action.endX,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(endX = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          errorText = validationErrors["end_x"],
          placeholder = "e.g. 540",
          modifier = Modifier.weight(1f),
        )
        NumericInputField(
          label = "End Y",
          value = action.endY,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(endY = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          errorText = validationErrors["end_y"],
          placeholder = "e.g. 600",
          modifier = Modifier.weight(1f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntTextField(
          label = "Start Random Offset (px)",
          value = action.startJitterPx,
          min = 0,
          onChange = { onChange(action.copy(startJitterPx = it.coerceIn(0, 200))) },
          supportingHint = "0 = exact spot",
          modifier = Modifier.weight(1f),
        )
        IntTextField(
          label = "End Random Offset (px)",
          value = action.endJitterPx,
          min = 0,
          onChange = { onChange(action.copy(endJitterPx = it.coerceIn(0, 200))) },
          supportingHint = "0 = exact spot",
          modifier = Modifier.weight(1f),
        )
      }
    }

    EditorSectionCard(title = "Speed & Pauses") {
      LongTextField(
        label = "Swipe Time (ms)",
        value = action.durationMs,
        min = 50L,
        onChange = { onChange(action.copy(durationMs = it)) },
        supportingHint = "Suggested: 300 ms (smaller = faster flick)",
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LongTextField(
          label = "Pause Before Dragging (ms)",
          value = action.holdBeforeDragMs,
          min = 0L,
          onChange = { onChange(action.copy(holdBeforeDragMs = it)) },
          supportingHint = "Suggested: 0 ms",
          modifier = Modifier.weight(1f),
        )
        LongTextField(
          label = "Pause Before Releasing (ms)",
          value = action.holdAfterDragMs,
          min = 0L,
          onChange = { onChange(action.copy(holdAfterDragMs = it)) },
          supportingHint = "Suggested: 0 ms",
          modifier = Modifier.weight(1f),
        )
      }

      if (action.trajectoryMode == SwipeTrajectoryMode.BEZIER_CURVE) {
        IntTextField(
          label = "Curve Amount (px)",
          value = action.bezierCurvatureOffsetPx,
          min = -500,
          onChange = { onChange(action.copy(bezierCurvatureOffsetPx = it)) },
          supportingHint = "How much the swipe bends (Suggested: 60 px)",
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (action.trajectoryMode == SwipeTrajectoryMode.MULTI_POINT_PATH) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Middle Turns / Waypoints (${action.multiPointPath.size})",
            style = MaterialTheme.typography.titleSmall,
          )
          OutlinedButton(
            onClick = {
              onChange(
                action.copy(
                  multiPointPath = action.multiPointPath + Point2D(UNSET_COORD, UNSET_COORD),
                ),
              )
            },
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Point")
          }
        }
        action.multiPointPath.forEachIndexed { idx, pt ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text("#${idx + 1}", style = MaterialTheme.typography.labelMedium)
            IntTextField(
              label = "X",
              value = pt.x,
              min = 0,
              onChange = { nx ->
                onChange(
                  action.copy(
                    multiPointPath =
                      action.multiPointPath.mapIndexed { i, p -> if (i == idx) p.copy(x = nx) else p },
                  ),
                )
              },
              errorText = validationErrors["path_${idx}_x"],
              unsetValue = UNSET_COORD,
              placeholder = "X",
              modifier = Modifier.weight(1f),
            )
            IntTextField(
              label = "Y",
              value = pt.y,
              min = 0,
              onChange = { ny ->
                onChange(
                  action.copy(
                    multiPointPath =
                      action.multiPointPath.mapIndexed { i, p -> if (i == idx) p.copy(y = ny) else p },
                  ),
                )
              },
              errorText = validationErrors["path_${idx}_y"],
              unsetValue = UNSET_COORD,
              placeholder = "Y",
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = {
                onChange(
                  action.copy(
                    multiPointPath = action.multiPointPath.filterIndexed { i, _ -> i != idx },
                  ),
                )
              },
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete point",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputTextActionEditor(
  action: ScriptAction.InputText,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.InputText) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
  validationErrors: Map<String, String> = emptyMap(),
) {
  var showAdvancedTags by remember(action.id) { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    EditorSectionCard(title = "Where to Type") {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf("Current Text Box", "Tap Position First").forEachIndexed { index, label ->
          val selected = (index == 0 && !action.tapToFocus) || (index == 1 && action.tapToFocus)
          SegmentedButton(
            selected = selected,
            onClick = { onChange(action.copy(tapToFocus = index == 1)) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
          ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }

      if (action.tapToFocus) {
        OverlayPickButton(
          label = "Pick Text Box on Screen",
          onClick = {
            onIntent(
              ScriptEditorUiIntent.RequestOverlayPick(
                purpose = OverlayPickPurpose.ActionCoordinates,
                targetType = GesturePickerTargetType.POINT,
              ),
            )
          },
          testTag = "pick_input_focus_overlay_button",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NullableIntTextField(
            label = "X",
            value = action.focusX,
            min = 0,
            onChange = { onChange(action.copy(focusX = it)) },
            errorText = validationErrors["focus_x"],
            placeholder = "e.g. 540",
            modifier = Modifier.weight(1f),
          )
          NullableIntTextField(
            label = "Y",
            value = action.focusY,
            min = 0,
            onChange = { onChange(action.copy(focusY = it)) },
            errorText = validationErrors["focus_y"],
            placeholder = "e.g. 960",
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    EditorSectionCard(title = "Text to Type") {
      val textErr = validationErrors["text_template"]
      OutlinedTextField(
        value = action.textTemplate,
        onValueChange = { onChange(action.copy(textTemplate = it)) },
        label = { Text("Text to Type") },
        placeholder = { Text("Enter the text you want typed...") },
        isError = textErr != null,
        supportingText = textErr?.let { err -> { Text(err) } },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().testTag("input_text_template"),
      )

      Text(
        text = "Quick Insert:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        listOf(
          "+ Date" to "{DATE}",
          "+ Time" to "{TIME}",
          "+ 4-Digit Random" to "{RANDOM_NUM_4}",
          "+ Clipboard" to "{CLIPBOARD}",
        ).forEach { (chipLabel, tag) ->
          AssistChip(
            onClick = { onChange(action.copy(textTemplate = action.textTemplate + tag)) },
            label = { Text(chipLabel) },
          )
        }
      }

      TextButton(
        onClick = { showAdvancedTags = !showAdvancedTags },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Advanced Tags",
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = if (showAdvancedTags) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = null,
        )
      }
      AnimatedVisibility(visible = showAdvancedTags) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          AssistChip(
            onClick = { onChange(action.copy(textTemplate = action.textTemplate + "{TIMESTAMP}")) },
            label = { Text("+ Epoch Seconds") },
          )
        }
      }

      if (variables.isNotEmpty()) {
        Text(
          text = "Insert Saved Variable:",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          variables.forEach { v ->
            AssistChip(
              onClick = { onChange(action.copy(textTemplate = action.textTemplate + "{${v.key}}")) },
              label = { Text(v.key) },
            )
          }
        }
      }
    }

    EditorSectionCard(title = "How to Type") {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextInjectionEngine.entries.forEach { engine ->
          val selected = action.engine == engine
          val (title, desc) =
            when (engine) {
              TextInjectionEngine.ACCESSIBILITY_SET_TEXT ->
                "Instant Fill" to "Fastest, for normal input boxes"
              TextInjectionEngine.CLIPBOARD_PASTE ->
                "Paste Text" to "Best for Chinese, Emoji & games"
              TextInjectionEngine.HUMAN_TYPING ->
                "Type Slowly" to "Looks like human typing"
            }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color =
              if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
              } else {
                MaterialTheme.colorScheme.surfaceContainer
              },
            border =
              BorderStroke(
                width = if (selected) 1.25.dp else 0.75.dp,
                color =
                  if (selected) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.outlineVariant
                  },
              ),
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onChange(action.copy(engine = engine)) },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              RadioButton(
                selected = selected,
                onClick = { onChange(action.copy(engine = engine)) },
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = desc,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Erase Existing Text First", modifier = Modifier.weight(1f))
        Switch(
          checked = action.clearExistingText,
          onCheckedChange = { onChange(action.copy(clearExistingText = it)) },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Hide Keyboard Afterward", modifier = Modifier.weight(1f))
        Switch(
          checked = action.autoHideKeyboard,
          onCheckedChange = { onChange(action.copy(autoHideKeyboard = it)) },
        )
      }

      Text(
        text = "Keyboard Key After Typing",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PostTextActionKey.entries.forEach { key ->
          val keyLabel =
            when (key) {
              PostTextActionKey.NONE -> "None"
              PostTextActionKey.ENTER -> "Enter"
              PostTextActionKey.SEARCH -> "Search"
              PostTextActionKey.SEND -> "Send"
              PostTextActionKey.DONE -> "Done"
            }
          FilterChip(
            selected = action.postActionKey == key,
            onClick = { onChange(action.copy(postActionKey = key)) },
            label = { Text(keyLabel) },
          )
        }
      }
    }
  }
}

@Composable
internal fun OverlayPickButton(
  label: String,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  FilledTonalButton(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(testTag),
  ) {
    Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(label)
  }
}

@Composable
internal fun IntTextField(
  label: String,
  value: Int,
  min: Int,
  onChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  errorText: String? = null,
  isError: Boolean = errorText != null,
  supportingHint: String? = null,
  unsetValue: Int? = null,
  placeholder: String = "",
) {
  var text by remember(value, unsetValue) {
    mutableStateOf(if (unsetValue != null && value == unsetValue) "" else value.toString())
  }
  val activeSupport = errorText ?: supportingHint
  OutlinedTextField(
    value = text,
    onValueChange = { raw ->
      val clean = raw.filterIndexed { idx, c -> c.isDigit() || (idx == 0 && c == '-' && min < 0) }
      text = clean
      if (clean.isEmpty() && unsetValue != null) {
        onChange(unsetValue)
      } else {
        clean.toIntOrNull()?.let { onChange(it.coerceAtLeast(min)) }
      }
    },
    label = { Text(label) },
    placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
    isError = isError,
    supportingText = activeSupport?.let { msg -> { Text(msg) } },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
  )
}

@Composable
internal fun NullableIntTextField(
  label: String,
  value: Int?,
  min: Int,
  onChange: (Int?) -> Unit,
  modifier: Modifier = Modifier,
  errorText: String? = null,
  supportingHint: String? = null,
  placeholder: String = "",
) {
  var text by remember(value) {
    mutableStateOf(if (value == null || value < min) "" else value.toString())
  }
  val activeSupport = errorText ?: supportingHint
  OutlinedTextField(
    value = text,
    onValueChange = { raw ->
      val clean = raw.filter { it.isDigit() }
      text = clean
      if (clean.isEmpty()) {
        onChange(null)
      } else {
        clean.toIntOrNull()?.let { onChange(it.coerceAtLeast(min)) }
      }
    },
    label = { Text(label) },
    placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
    isError = errorText != null,
    supportingText = activeSupport?.let { msg -> { Text(msg) } },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
  )
}

@Composable
internal fun LongTextField(
  label: String,
  value: Long,
  min: Long,
  onChange: (Long) -> Unit,
  modifier: Modifier = Modifier,
  supportingHint: String? = null,
  errorText: String? = null,
) {
  var text by remember(value) { mutableStateOf(value.toString()) }
  val activeSupport = errorText ?: supportingHint
  OutlinedTextField(
    value = text,
    onValueChange = { raw ->
      val clean = raw.filter { it.isDigit() }
      text = clean
      clean.toLongOrNull()?.let { onChange(it.coerceAtLeast(min)) }
    },
    label = { Text(label) },
    isError = errorText != null,
    supportingText = activeSupport?.let { msg -> { Text(msg) } },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
  )
}

@Composable
internal fun FloatTextField(
  label: String,
  value: Float,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  var text by remember(value) { mutableStateOf(value.toString()) }
  OutlinedTextField(
    value = text,
    onValueChange = { raw ->
      val clean = raw.filter { it.isDigit() || it == '.' || it == '-' }
      text = clean
      clean.toFloatOrNull()?.let(onChange)
    },
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = modifier,
  )
}
