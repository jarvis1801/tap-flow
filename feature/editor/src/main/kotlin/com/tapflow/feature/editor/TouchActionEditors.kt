package com.tapflow.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.service.overlay.GesturePickerTargetType
import java.util.UUID

@Composable
fun TapActionEditor(
  action: ScriptAction.Tap,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.Tap) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OverlayPickButton(
      label = "Pick (X, Y) via Overlay",
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
        modifier = Modifier.weight(1f),
      )
      NumericInputField(
        label = "Y (px)",
        value = action.y,
        variables = variables,
        isPro = isPro,
        onValueChange = { onChange(action.copy(y = it)) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        modifier = Modifier.weight(1f),
      )
    }

    ProIntField(
      label = "Anti-Ban Jitter Radius (px)",
      value = action.jitterPx,
      isPro = isPro,
      proGate = ProFeatureGate.ANTI_BAN_JITTER,
      onValueChange = { onChange(action.copy(jitterPx = it.coerceIn(0, 300))) },
      onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
    )

    DurationInputField(
      label = "Press Duration",
      value = action.pressDuration,
      variables = variables,
      isPro = isPro,
      onValueChange = { onChange(action.copy(pressDuration = it)) },
      onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
    )

    NumericInputField(
      label = "Repeat Count",
      value = action.repeatCount,
      variables = variables,
      isPro = isPro,
      onValueChange = { next ->
        val safe =
          if (next is NumericInput.Literal) {
            NumericInput.Literal(next.value.coerceAtLeast(1))
          } else {
            next
          }
        onChange(action.copy(repeatCount = safe))
      },
      onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
    )

    DurationInputField(
      label = "Repeat Interval",
      value = action.repeatInterval,
      variables = variables,
      isPro = isPro,
      onValueChange = { onChange(action.copy(repeatInterval = it)) },
      onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
    )
  }
}

@Composable
fun DoubleTapActionEditor(
  action: ScriptAction.DoubleTap,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.DoubleTap) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OverlayPickButton(
      label = "Pick (X, Y) via Overlay",
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
        modifier = Modifier.weight(1f),
      )
      NumericInputField(
        label = "Y (px)",
        value = action.y,
        variables = variables,
        isPro = isPro,
        onValueChange = { onChange(action.copy(y = it)) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        modifier = Modifier.weight(1f),
      )
    }

    ProIntField(
      label = "Anti-Ban Jitter Radius (px)",
      value = action.jitterPx,
      isPro = isPro,
      proGate = ProFeatureGate.ANTI_BAN_JITTER,
      onValueChange = { onChange(action.copy(jitterPx = it.coerceIn(0, 300))) },
      onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      LongTextField(
        label = "Press Duration (ms)",
        value = action.pressDurationMs,
        min = 10L,
        onChange = { onChange(action.copy(pressDurationMs = it)) },
        modifier = Modifier.weight(1f),
      )
      LongTextField(
        label = "Tap Interval (ms)",
        value = action.tapIntervalMs,
        min = 20L,
        onChange = { onChange(action.copy(tapIntervalMs = it)) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LongPressActionEditor(
  action: ScriptAction.LongPress,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.LongPress) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OverlayPickButton(
      label = "Pick Anchor & Hold via Overlay",
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

    Text(
      text = "Operation Mode",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      LongPressMode.entries.forEach { mode ->
        val requiresPro = mode != LongPressMode.STANDARD
        FilterChip(
          selected = action.mode == mode,
          onClick = {
            if (requiresPro && !isPro) {
              onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.HOLD_GESTURE_MODES.displayName))
            } else {
              val nextHoldCond =
                if (mode == LongPressMode.HOLD_UNTIL && action.holdUntilCondition == null) {
                  ConditionGroup(id = UUID.randomUUID().toString(), name = "Release Condition")
                } else {
                  action.holdUntilCondition
                }
              onChange(action.copy(mode = mode, holdUntilCondition = nextHoldCond))
            }
          },
          leadingIcon =
            if (requiresPro && !isPro) {
              { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else {
              null
            },
          label = {
            Text(
              when (mode) {
                LongPressMode.STANDARD -> "Standard"
                LongPressMode.PRESS_DOWN_ONLY -> "Press Down Only"
                LongPressMode.RELEASE_ONLY -> "Release Only"
                LongPressMode.HOLD_UNTIL -> "Hold Until Condition"
              },
            )
          },
        )
      }
    }

    if (action.mode != LongPressMode.RELEASE_ONLY) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumericInputField(
          label = "X (px)",
          value = action.x,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(x = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          modifier = Modifier.weight(1f),
        )
        NumericInputField(
          label = "Y (px)",
          value = action.y,
          variables = variables,
          isPro = isPro,
          onValueChange = { onChange(action.copy(y = it)) },
          onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
          modifier = Modifier.weight(1f),
        )
      }

      ProIntField(
        label = "Initial Touch Jitter (px)",
        value = action.jitterPx,
        isPro = isPro,
        proGate = ProFeatureGate.ANTI_BAN_JITTER,
        onValueChange = { onChange(action.copy(jitterPx = it.coerceIn(0, 300))) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
      )
    }

    if (action.mode == LongPressMode.STANDARD) {
      LongTextField(
        label = "Hold Duration (ms, supports >60s)",
        value = action.pressDurationMs,
        min = 100L,
        onChange = { onChange(action.copy(pressDurationMs = it)) },
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (action.mode == LongPressMode.HOLD_UNTIL) {
      LongTextField(
        label = "Hold Safety Timeout (ms)",
        value = action.holdTimeoutMs,
        min = 500L,
        onChange = { onChange(action.copy(holdTimeoutMs = it)) },
        modifier = Modifier.fillMaxWidth(),
      )
      val condRoot =
        action.holdUntilCondition
          ?: ConditionGroup(id = UUID.randomUUID().toString(), name = "Release When")
      ConditionGroupCardEditor(
        root = condRoot,
        currentGroup = condRoot,
        depth = 1,
        isPro = isPro,
        variables = variables,
        onRootChange = { updated -> onChange(action.copy(holdUntilCondition = updated)) },
        onIntent = onIntent,
      )
    }

    if (action.mode != LongPressMode.RELEASE_ONLY) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Micro-movement Jitter",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
          Text(
            text = if (isPro) "Simulates natural finger tremor during hold" else "Pro feature",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = action.microMovementJitter,
          onCheckedChange = { checked ->
            if (!isPro && checked) {
              onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.ANTI_BAN_JITTER.displayName))
            } else {
              onChange(action.copy(microMovementJitter = checked))
            }
          },
        )
      }

      if (action.microMovementJitter) {
        IntTextField(
          label = "Micro-Jitter Amplitude (px)",
          value = action.microJitterAmplitudePx,
          min = 1,
          onChange = { onChange(action.copy(microJitterAmplitudePx = it.coerceIn(1, 30))) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiTouchActionEditor(
  action: ScriptAction.MultiTouch,
  onChange: (ScriptAction.MultiTouch) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OverlayPickButton(
      label = "Pick Anchor / Point via Overlay",
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

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      MultiTouchMode.entries.forEach { mode ->
        FilterChip(
          selected = action.mode == mode,
          onClick = { onChange(action.copy(mode = mode)) },
          label = {
            Text(
              when (mode) {
                MultiTouchMode.CUSTOM_POINTS -> "Custom Points"
                MultiTouchMode.PINCH_SPREAD -> "Pinch / Spread"
                MultiTouchMode.ROTATE -> "Rotate"
              },
            )
          },
        )
      }
    }

    when (action.mode) {
      MultiTouchMode.CUSTOM_POINTS -> {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Touch Points (${action.customPoints.size}/10)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          OutlinedButton(
            onClick = {
              if (action.customPoints.size < 10) {
                onChange(
                  action.copy(
                    customPoints = action.customPoints + MultiTouchPoint(x = 540, y = 960),
                  ),
                )
              }
            },
          ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Finger")
          }
        }
        action.customPoints.forEachIndexed { idx, pt ->
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              ),
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
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Remove touch point",
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
                  modifier = Modifier.weight(1f),
                )
                IntTextField(
                  label = "Jitter",
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
                  label = "Press (ms)",
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
                  modifier = Modifier.weight(1f),
                )
                LongTextField(
                  label = "Stagger (ms)",
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
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      MultiTouchMode.PINCH_SPREAD -> {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IntTextField(
            label = "Center X",
            value = action.centerX,
            min = 0,
            onChange = { onChange(action.copy(centerX = it)) },
            modifier = Modifier.weight(1f),
          )
          IntTextField(
            label = "Center Y",
            value = action.centerY,
            min = 0,
            onChange = { onChange(action.copy(centerY = it)) },
            modifier = Modifier.weight(1f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IntTextField(
            label = "Start Dist (px)",
            value = action.startDistancePx,
            min = 10,
            onChange = { onChange(action.copy(startDistancePx = it)) },
            modifier = Modifier.weight(1f),
          )
          IntTextField(
            label = "End Dist (px)",
            value = action.endDistancePx,
            min = 10,
            onChange = { onChange(action.copy(endDistancePx = it)) },
            modifier = Modifier.weight(1f),
          )
        }
        LongTextField(
          label = "Duration (ms)",
          value = action.durationMs,
          min = 50L,
          onChange = { onChange(action.copy(durationMs = it)) },
          modifier = Modifier.fillMaxWidth(),
        )
      }

      MultiTouchMode.ROTATE -> {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IntTextField(
            label = "Center X",
            value = action.centerX,
            min = 0,
            onChange = { onChange(action.copy(centerX = it)) },
            modifier = Modifier.weight(1f),
          )
          IntTextField(
            label = "Center Y",
            value = action.centerY,
            min = 0,
            onChange = { onChange(action.copy(centerY = it)) },
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
            label = "Radius (px)",
            value = action.radiusPx,
            min = 20,
            onChange = { onChange(action.copy(radiusPx = it)) },
            modifier = Modifier.weight(1f),
          )
          LongTextField(
            label = "Duration (ms)",
            value = action.durationMs,
            min = 50L,
            onChange = { onChange(action.copy(durationMs = it)) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwipeDragActionEditor(
  action: ScriptAction.SwipeDrag,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.SwipeDrag) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OverlayPickButton(
      label = "Draw / Pick Swipe Path via Overlay",
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

    Text(
      text = "Trajectory Mode",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SwipeTrajectoryMode.entries.forEach { mode ->
        val requiresPro = mode != SwipeTrajectoryMode.LINEAR
        val proGate =
          if (mode == SwipeTrajectoryMode.BEZIER_CURVE) {
            ProFeatureGate.BEZIER_TRAJECTORY
          } else {
            ProFeatureGate.MULTI_POINT_SWIPE
          }
        FilterChip(
          selected = action.trajectoryMode == mode,
          onClick = {
            if (requiresPro && !isPro) {
              onIntent(ScriptEditorUiIntent.RequestProUpgrade(proGate.displayName))
            } else {
              onChange(action.copy(trajectoryMode = mode))
            }
          },
          leadingIcon =
            if (requiresPro && !isPro) {
              { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else {
              null
            },
          label = {
            Text(
              when (mode) {
                SwipeTrajectoryMode.LINEAR -> "Linear"
                SwipeTrajectoryMode.BEZIER_CURVE -> "Bezier Curve"
                SwipeTrajectoryMode.MULTI_POINT_PATH -> "Multi-point Path"
              },
            )
          },
        )
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      NumericInputField(
        label = "Start X",
        value = action.startX,
        variables = variables,
        isPro = isPro,
        onValueChange = { onChange(action.copy(startX = it)) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        modifier = Modifier.weight(1f),
      )
      NumericInputField(
        label = "Start Y",
        value = action.startY,
        variables = variables,
        isPro = isPro,
        onValueChange = { onChange(action.copy(startY = it)) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
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
        modifier = Modifier.weight(1f),
      )
      NumericInputField(
        label = "End Y",
        value = action.endY,
        variables = variables,
        isPro = isPro,
        onValueChange = { onChange(action.copy(endY = it)) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        modifier = Modifier.weight(1f),
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ProIntField(
        label = "Start Jitter (px)",
        value = action.startJitterPx,
        isPro = isPro,
        proGate = ProFeatureGate.ANTI_BAN_JITTER,
        onValueChange = { onChange(action.copy(startJitterPx = it.coerceIn(0, 200))) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        modifier = Modifier.weight(1f),
      )
      ProIntField(
        label = "End Jitter (px)",
        value = action.endJitterPx,
        isPro = isPro,
        proGate = ProFeatureGate.ANTI_BAN_JITTER,
        onValueChange = { onChange(action.copy(endJitterPx = it.coerceIn(0, 200))) },
        onProRequested = { onIntent(ScriptEditorUiIntent.RequestProUpgrade(it)) },
        modifier = Modifier.weight(1f),
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      LongTextField(
        label = "Traversal (ms)",
        value = action.durationMs,
        min = 50L,
        onChange = { onChange(action.copy(durationMs = it)) },
        modifier = Modifier.weight(1f),
      )
      LongTextField(
        label = "Hold Before (ms)",
        value = action.holdBeforeDragMs,
        min = 0L,
        onChange = { onChange(action.copy(holdBeforeDragMs = it)) },
        modifier = Modifier.weight(1f),
      )
      LongTextField(
        label = "Hold After (ms)",
        value = action.holdAfterDragMs,
        min = 0L,
        onChange = { onChange(action.copy(holdAfterDragMs = it)) },
        modifier = Modifier.weight(1f),
      )
    }

    if (action.trajectoryMode == SwipeTrajectoryMode.BEZIER_CURVE) {
      IntTextField(
        label = "Bezier Curvature Offset (px)",
        value = action.bezierCurvatureOffsetPx,
        min = -500,
        onChange = { onChange(action.copy(bezierCurvatureOffsetPx = it)) },
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
          text = "Path Waypoints (${action.multiPointPath.size})",
          style = MaterialTheme.typography.titleSmall,
        )
        OutlinedButton(
          onClick = {
            onChange(action.copy(multiPointPath = action.multiPointPath + Point2D(540, 960)))
          },
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
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
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Delete waypoint",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputTextActionEditor(
  action: ScriptAction.InputText,
  variables: List<VariableDefinition>,
  isPro: Boolean,
  onChange: (ScriptAction.InputText) -> Unit,
  onIntent: (ScriptEditorUiIntent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Tap to Focus Target First", fontWeight = FontWeight.Medium)
        Text(
          text = "Tap specified (X, Y) before injecting text",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = action.tapToFocus,
        onCheckedChange = { enabled ->
          onChange(
            action.copy(
              tapToFocus = enabled,
              focusX = if (enabled) (action.focusX ?: 540) else action.focusX,
              focusY = if (enabled) (action.focusY ?: 960) else action.focusY,
            ),
          )
        },
      )
    }

    if (action.tapToFocus) {
      OverlayPickButton(
        label = "Pick Text Field Coordinates via Overlay",
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
        IntTextField(
          label = "Focus X",
          value = action.focusX ?: 540,
          min = 0,
          onChange = { onChange(action.copy(focusX = it)) },
          modifier = Modifier.weight(1f),
        )
        IntTextField(
          label = "Focus Y",
          value = action.focusY ?: 960,
          min = 0,
          onChange = { onChange(action.copy(focusY = it)) },
          modifier = Modifier.weight(1f),
        )
      }
    }

    OutlinedTextField(
      value = action.textTemplate,
      onValueChange = { onChange(action.copy(textTemplate = it)) },
      label = { Text("Text Template (supports {var} & System Tags)") },
      minLines = 2,
      maxLines = 4,
      modifier = Modifier.fillMaxWidth().testTag("input_text_template"),
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      listOf("{TIMESTAMP}", "{DATE}", "{TIME}", "{RANDOM_NUM_4}", "{CLIPBOARD}").forEach { tag ->
        AssistChip(
          onClick = { onChange(action.copy(textTemplate = action.textTemplate + tag)) },
          label = { Text(tag) },
        )
      }
      variables.forEach { v ->
        AssistChip(
          onClick = { onChange(action.copy(textTemplate = action.textTemplate + "{${v.key}}")) },
          label = { Text("{${v.key}}") },
        )
      }
    }

    Text(
      text = "Injection Engine",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextInjectionEngine.entries.forEach { engine ->
        val requiresPro = engine == TextInjectionEngine.HUMAN_TYPING
        FilterChip(
          selected = action.engine == engine,
          onClick = {
            if (requiresPro && !isPro) {
              onIntent(ScriptEditorUiIntent.RequestProUpgrade(ProFeatureGate.HUMAN_TYPING.displayName))
            } else {
              onChange(action.copy(engine = engine))
            }
          },
          leadingIcon =
            if (requiresPro && !isPro) {
              { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else {
              null
            },
          label = {
            Text(
              when (engine) {
                TextInjectionEngine.ACCESSIBILITY_SET_TEXT -> "Accessibility SetText"
                TextInjectionEngine.CLIPBOARD_PASTE -> "Clipboard Paste"
                TextInjectionEngine.HUMAN_TYPING -> "Human-like Typing"
              },
            )
          },
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text("Clear Existing Text Before Input")
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
      Text("Auto Hide Keyboard After Input")
      Switch(
        checked = action.autoHideKeyboard,
        onCheckedChange = { onChange(action.copy(autoHideKeyboard = it)) },
      )
    }

    Text(
      text = "Post-Text IME Action Key",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      PostTextActionKey.entries.forEach { key ->
        FilterChip(
          selected = action.postActionKey == key,
          onClick = { onChange(action.copy(postActionKey = key)) },
          label = { Text(key.name) },
        )
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
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(testTag),
  ) {
    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(label)
  }
}

@Composable
internal fun ProIntField(
  label: String,
  value: Int,
  isPro: Boolean,
  proGate: ProFeatureGate,
  onValueChange: (Int) -> Unit,
  onProRequested: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var text by remember(value) { mutableStateOf(value.toString()) }
  OutlinedTextField(
    value = text,
    onValueChange = { raw ->
      if (!isPro) {
        onProRequested(proGate.displayName)
        return@OutlinedTextField
      }
      val clean = raw.filter { it.isDigit() }
      text = clean
      clean.toIntOrNull()?.let(onValueChange)
    },
    label = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!isPro) {
          Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        Text(label)
      }
    },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier.fillMaxWidth(),
  )
}

@Composable
internal fun IntTextField(
  label: String,
  value: Int,
  min: Int,
  onChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var text by remember(value) { mutableStateOf(value.toString()) }
  OutlinedTextField(
    value = text,
    onValueChange = { raw ->
      val clean = raw.filterIndexed { idx, c -> c.isDigit() || (idx == 0 && c == '-' && min < 0) }
      text = clean
      clean.toIntOrNull()?.let { onChange(it.coerceAtLeast(min)) }
    },
    label = { Text(label) },
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
) {
  var text by remember(value) { mutableStateOf(value.toString()) }
  OutlinedTextField(
    value = text,
    onValueChange = { raw ->
      val clean = raw.filter { it.isDigit() }
      text = clean
      clean.toLongOrNull()?.let { onChange(it.coerceAtLeast(min)) }
    },
    label = { Text(label) },
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
