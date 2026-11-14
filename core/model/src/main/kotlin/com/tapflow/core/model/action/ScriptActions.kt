package com.tapflow.core.model.action

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.DurationInput
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ConditionGroup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Point2D(
  val x: Int,
  val y: Int,
)

@Serializable
enum class LongPressMode {
  STANDARD,
  PRESS_DOWN_ONLY,
  RELEASE_ONLY,
  HOLD_UNTIL,
}

@Serializable
enum class MultiTouchMode {
  CUSTOM_POINTS,
  PINCH_SPREAD,
  ROTATE,
}

@Immutable
@Serializable
data class MultiTouchPoint(
  val x: Int,
  val y: Int,
  val jitterPx: Int = 0,
  val pressDurationMs: Long = 80L,
  val staggerDelayMs: Long = 0L,
)

@Serializable
enum class SwipeTrajectoryMode {
  LINEAR,
  BEZIER_CURVE,
  MULTI_POINT_PATH,
}

@Serializable
enum class TextInjectionEngine {
  ACCESSIBILITY_SET_TEXT,
  CLIPBOARD_PASTE,
  HUMAN_TYPING,
}

@Serializable
enum class PostTextActionKey {
  NONE,
  ENTER,
  SEARCH,
  SEND,
  DONE,
}

@Immutable
@Serializable
sealed interface LoopMode {
  @Immutable
  @Serializable
  @SerialName("count")
  data class Count(
    val count: NumericInput = NumericInput.Literal(1),
    val exportIndexVarKey: String? = null,
  ) : LoopMode

  @Immutable
  @Serializable
  @SerialName("while_condition")
  data class WhileCondition(val conditionRoot: ConditionGroup) : LoopMode

  @Immutable
  @Serializable
  @SerialName("until_condition")
  data class UntilCondition(val conditionRoot: ConditionGroup) : LoopMode

  @Immutable
  @Serializable
  @SerialName("unlimited")
  data object Unlimited : LoopMode
}

@Serializable
enum class LoopTimeoutAction {
  BREAK_AND_CONTINUE,
  STOP_AND_ALERT,
}

@Immutable
@Serializable
data class LoopSafeguards(
  val intervalDelay: DelayConfig = DelayConfig.Fixed(100L),
  val maxTimeoutMs: Long = 60_000L,
  val maxIterations: Int = 500,
  val onTimeout: LoopTimeoutAction = LoopTimeoutAction.BREAK_AND_CONTINUE,
)

@Serializable
enum class SystemKeyType {
  HOME,
  BACK,
  RECENTS,
  POWER_DIALOG,
  VOLUME_UP,
  VOLUME_DOWN,
  NOTIFICATIONS,
  QUICK_SETTINGS,
  TAKE_SCREENSHOT,
}

@Serializable
enum class ClipboardActionType {
  COPY,
  PASTE,
  CLEAR,
}

@Serializable
enum class VibratePattern {
  SHORT,
  LONG,
  CUSTOM,
}

@Serializable
enum class MathOperator {
  ADD,
  SUBTRACT,
  MULTIPLY,
  DIVIDE,
  MODULO,
  ROUND,
  FLOOR,
  CEIL,
  ABS,
}

@Serializable
enum class RandomTargetType {
  INT,
  FLOAT,
  BOOLEAN,
}

@Serializable
enum class StringOperator {
  CONCATENATE,
  TRIM,
  REPLACE,
  REPLACE_REGEX,
  SUBSTRING,
}

@Serializable
enum class BooleanOperator {
  TOGGLE,
  AND,
  OR,
}

@Serializable
enum class TypeCastOperator {
  STRING_TO_NUMBER,
  NUMBER_TO_STRING,
  BOOLEAN_TO_STRING,
  BOOLEAN_TO_NUMBER,
}

@Immutable
@Serializable
sealed interface ScriptAction {
  val id: String

  // -------------------------------------------------------------------------
  // Category 1: Touch & Gestures
  // -------------------------------------------------------------------------

  @Immutable
  @Serializable
  @SerialName("tap")
  data class Tap(
    override val id: String,
    val x: NumericInput = NumericInput.Literal(0),
    val y: NumericInput = NumericInput.Literal(0),
    val jitterPx: Int = 0,
    val pressDuration: DurationInput = DurationInput.Fixed(50L),
    val repeatCount: NumericInput = NumericInput.Literal(1),
    val repeatInterval: DurationInput = DurationInput.Fixed(100L),
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("double_tap")
  data class DoubleTap(
    override val id: String,
    val x: NumericInput = NumericInput.Literal(0),
    val y: NumericInput = NumericInput.Literal(0),
    val jitterPx: Int = 0,
    val pressDurationMs: Long = 50L,
    val tapIntervalMs: Long = 100L,
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("long_press")
  data class LongPress(
    override val id: String,
    val x: NumericInput = NumericInput.Literal(0),
    val y: NumericInput = NumericInput.Literal(0),
    val jitterPx: Int = 0,
    val pressDurationMs: Long = 800L,
    val microMovementJitter: Boolean = false,
    val microJitterAmplitudePx: Int = 3,
    val mode: LongPressMode = LongPressMode.STANDARD,
    val holdUntilCondition: ConditionGroup? = null,
    val holdTimeoutMs: Long = 30_000L,
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("multi_touch")
  data class MultiTouch(
    override val id: String,
    val mode: MultiTouchMode = MultiTouchMode.CUSTOM_POINTS,
    val customPoints: List<MultiTouchPoint> = emptyList(),
    val centerX: Int = 0,
    val centerY: Int = 0,
    val startDistancePx: Int = 100,
    val endDistancePx: Int = 300,
    val startAngleDeg: Float = 0f,
    val endAngleDeg: Float = 90f,
    val radiusPx: Int = 150,
    val durationMs: Long = 400L,
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("swipe_drag")
  data class SwipeDrag(
    override val id: String,
    val startX: NumericInput = NumericInput.Literal(0),
    val startY: NumericInput = NumericInput.Literal(0),
    val endX: NumericInput = NumericInput.Literal(0),
    val endY: NumericInput = NumericInput.Literal(0),
    val startJitterPx: Int = 0,
    val endJitterPx: Int = 0,
    val durationMs: Long = 300L,
    val holdBeforeDragMs: Long = 0L,
    val holdAfterDragMs: Long = 0L,
    val trajectoryMode: SwipeTrajectoryMode = SwipeTrajectoryMode.LINEAR,
    val bezierCurvatureOffsetPx: Int = 40,
    val multiPointPath: List<Point2D> = emptyList(),
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("input_text")
  data class InputText(
    override val id: String,
    val tapToFocus: Boolean = false,
    val focusX: Int? = null,
    val focusY: Int? = null,
    val textTemplate: String = "",
    val engine: TextInjectionEngine = TextInjectionEngine.ACCESSIBILITY_SET_TEXT,
    val clearExistingText: Boolean = false,
    val postActionKey: PostTextActionKey = PostTextActionKey.NONE,
    val autoHideKeyboard: Boolean = false,
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  // -------------------------------------------------------------------------
  // Category 2: Flow Control (No Action-Level Post Delay)
  // -------------------------------------------------------------------------

  @Immutable
  @Serializable
  @SerialName("if_block")
  data class IfBlock(
    override val id: String,
    val name: String = "If Condition",
    val conditionRoot: ConditionGroup,
    val thenActions: List<ScriptAction> = emptyList(),
    val elseActions: List<ScriptAction> = emptyList(),
    val isExpanded: Boolean = true,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("loop_block")
  data class LoopBlock(
    override val id: String,
    val name: String = "Loop",
    val mode: LoopMode = LoopMode.Count(),
    val safeguards: LoopSafeguards = LoopSafeguards(),
    val bodyActions: List<ScriptAction> = emptyList(),
    val isExpanded: Boolean = true,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("delay")
  data class Delay(
    override val id: String,
    val config: DelayConfig = DelayConfig.Fixed(500L),
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("break")
  data class Break(override val id: String) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("continue")
  data class Continue(override val id: String) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("end_script")
  data class EndScript(override val id: String) : ScriptAction

  // -------------------------------------------------------------------------
  // Category 3: System & Hardware
  // -------------------------------------------------------------------------

  @Immutable
  @Serializable
  @SerialName("key_event")
  data class KeyEvent(
    override val id: String,
    val key: SystemKeyType = SystemKeyType.BACK,
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("launch_app")
  data class LaunchApp(
    override val id: String,
    val packageName: String,
    val appLabel: String = "",
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("force_stop_app")
  data class ForceStopApp(
    override val id: String,
    val packageName: String,
    val appLabel: String = "",
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("clipboard_op")
  data class ClipboardOp(
    override val id: String,
    val operation: ClipboardActionType = ClipboardActionType.PASTE,
    val valueTemplate: String = "",
    val targetVariableKey: String? = null,
    val postDelay: PostActionDelay = PostActionDelay.Global,
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("vibrate")
  data class Vibrate(
    override val id: String,
    val pattern: VibratePattern = VibratePattern.SHORT,
    val customPatternMs: List<Long> = emptyList(),
  ) : ScriptAction

  @Immutable
  @Serializable
  @SerialName("play_alert_sound")
  data class PlayAlertSound(
    override val id: String,
    val soundUriOrType: String = "default",
  ) : ScriptAction

  // -------------------------------------------------------------------------
  // Category 4: Variable Operations (Strictly 0ms Post Delay)
  // -------------------------------------------------------------------------

  @Immutable
  @Serializable
  sealed interface VariableOp : ScriptAction

  @Immutable
  @Serializable
  @SerialName("set_variable")
  data class SetVariable(
    override val id: String,
    val targetKey: String,
    val valueTemplate: String,
  ) : VariableOp

  @Immutable
  @Serializable
  @SerialName("math_op")
  data class MathOp(
    override val id: String,
    val targetKey: String,
    val operator: MathOperator,
    val leftOperand: String,
    val rightOperand: String = "",
  ) : VariableOp

  @Immutable
  @Serializable
  @SerialName("random_op")
  data class RandomOp(
    override val id: String,
    val targetKey: String,
    val targetType: RandomTargetType,
    val minValue: Double = 0.0,
    val maxValue: Double = 100.0,
  ) : VariableOp

  @Immutable
  @Serializable
  @SerialName("string_op")
  data class StringOp(
    override val id: String,
    val targetKey: String,
    val operator: StringOperator,
    val inputA: String,
    val inputB: String = "",
    val startIndex: Int = 0,
    val endIndex: Int = 0,
  ) : VariableOp

  @Immutable
  @Serializable
  @SerialName("boolean_op")
  data class BooleanOp(
    override val id: String,
    val targetKey: String,
    val operator: BooleanOperator,
    val leftKey: String,
    val rightKey: String? = null,
  ) : VariableOp

  @Immutable
  @Serializable
  @SerialName("type_cast_op")
  data class TypeCastOp(
    override val id: String,
    val targetKey: String,
    val sourceKey: String,
    val operator: TypeCastOperator,
  ) : VariableOp
}
