package com.tapflow.core.model.entitlement

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.action.LongPressMode
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.SwipeTrajectoryMode
import com.tapflow.core.model.action.TextInjectionEngine
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.DurationInput
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ColorMatchMode
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionNode
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.pipeline.PipelineErrorStrategy
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.model.script.Script
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TrialStatus {
  NOT_STARTED,
  ACTIVE,
  EXPIRED,
  EXPIRED_TAMPERED,
}

@Immutable
@Serializable
sealed interface EntitlementState {
  @Immutable
  @Serializable
  @SerialName("free")
  data class Free(
    val trialStatus: TrialStatus,
    val trialDaysRemaining: Int = 0,
  ) : EntitlementState

  @Immutable
  @Serializable
  @SerialName("pro_trial")
  data class ProTrial(
    val expiresAtUtcMs: Long,
    val remainingDays: Int,
    val remainingHours: Int,
  ) : EntitlementState

  @Immutable
  @Serializable
  @SerialName("pro_lifetime")
  data class ProLifetime(
    val purchaseTokenHash: String,
    val purchasedAtUtcMs: Long,
  ) : EntitlementState

  @Immutable
  @Serializable
  @SerialName("pending_purchase")
  data object PendingPurchase : EntitlementState
}

@Serializable
enum class ProFeatureGate(val displayName: String) {
  UNLIMITED_SCRIPTS("Unlimited Scripts"),
  UNLIMITED_ACTIONS("Unlimited Actions per Script"),
  UNLIMITED_PIPELINES("Unlimited Pipelines"),
  IMAGE_MATCH("Image Template Matching"),
  MULTI_POINT_COLOR_MATCH("Multi-Point & Region Color Match"),
  INSPECT_STUDIO_CROP("Inspect Studio Image Cropping"),
  LIVE_IMAGE_INSPECTOR("Live Image Inspector (Mode 4)"),
  IF_BLOCK("Nested If Conditions"),
  ADVANCED_LOOPS("While / Until / Unlimited Loops"),
  DYNAMIC_VARIABLES("Dynamic Variables & Operations"),
  ANTI_BAN_JITTER("Anti-Ban Touch Jitter"),
  BEZIER_TRAJECTORY("Humanized Bezier Swipes"),
  MULTI_POINT_SWIPE("Multi-Point Swipe Paths"),
  MULTI_TOUCH("Multi-Touch Gestures"),
  HUMAN_TYPING("Human-like Typing"),
  RANDOM_DEVIATION_DELAY("Randomized & Deviation Delays"),
  SHIZUKU_INTEGRATION("Seamless Shizuku Integration"),
  DESKTOP_CONSOLE("Desktop Studio WebSocket Server"),
  TAPFLOW_BACKUP("Backup Export / Import (.tapflow)"),
  PIPELINE_SKIP_CONTINUE("Pipeline Skip & Continue Recovery"),
  FULL_EXECUTION_LOGS("Full Execution History & Export"),
  FORCE_STOP_APP("Rootless App Force-Stop"),
  HOLD_GESTURE_MODES("Advanced Long Press Hold Modes"),
}

object EntitlementValidator {
  const val FREE_MAX_SCRIPTS: Int = 3
  const val FREE_MAX_ACTIONS_PER_SCRIPT: Int = 15
  const val FREE_MAX_PIPELINES: Int = 1
  const val TRIAL_DURATION_MS: Long = 7L * 24L * 60L * 60L * 1000L
  const val CLOCK_ROLLBACK_TOLERANCE_MS: Long = 60_000L
  const val SKU_PRO_LIFETIME: String = "tapflow_pro_lifetime"

  fun isPro(state: EntitlementState): Boolean =
    state is EntitlementState.ProTrial || state is EntitlementState.ProLifetime

  fun canUseFeature(state: EntitlementState, gate: ProFeatureGate): Boolean = isPro(state)

  fun canCreateScript(state: EntitlementState, existingScriptCount: Int): Boolean =
    isPro(state) || existingScriptCount < FREE_MAX_SCRIPTS

  fun canCreatePipeline(state: EntitlementState, existingPipelineCount: Int): Boolean =
    isPro(state) || existingPipelineCount < FREE_MAX_PIPELINES

  fun detectProFeaturesInScript(script: Script): Set<ProFeatureGate> {
    val gates = linkedSetOf<ProFeatureGate>()
    if (script.actions.size > FREE_MAX_ACTIONS_PER_SCRIPT) {
      gates += ProFeatureGate.UNLIMITED_ACTIONS
    }
    if (script.variables.isNotEmpty()) {
      gates += ProFeatureGate.DYNAMIC_VARIABLES
    }
    if (script.globalDelay !is DelayConfig.Fixed) {
      gates += ProFeatureGate.RANDOM_DEVIATION_DELAY
    }
    script.actions.forEach { scanAction(it, gates) }
    return gates
  }

  fun detectProFeaturesInPipeline(
    pipeline: Pipeline,
    scriptsById: Map<String, Script>,
  ): Set<ProFeatureGate> {
    val gates = linkedSetOf<ProFeatureGate>()
    if (pipeline.repeatMode is PipelineRepeatMode.Unlimited) {
      gates += ProFeatureGate.ADVANCED_LOOPS
    }
    if (pipeline.errorStrategy == PipelineErrorStrategy.SKIP_AND_CONTINUE) {
      gates += ProFeatureGate.PIPELINE_SKIP_CONTINUE
    }
    pipeline.steps.forEach { step ->
      scriptsById[step.scriptId]?.let { script ->
        gates += detectProFeaturesInScript(script)
      }
    }
    return gates
  }

  private fun scanAction(action: ScriptAction, gates: MutableSet<ProFeatureGate>) {
    when (action) {
      is ScriptAction.Tap -> {
        scanNumericInput(action.x, gates)
        scanNumericInput(action.y, gates)
        scanNumericInput(action.repeatCount, gates)
        scanDurationInput(action.pressDuration, gates)
        scanDurationInput(action.repeatInterval, gates)
        if (action.jitterPx > 0) gates += ProFeatureGate.ANTI_BAN_JITTER
        scanPostDelay(action.postDelay, gates)
      }
      is ScriptAction.DoubleTap -> {
        scanNumericInput(action.x, gates)
        scanNumericInput(action.y, gates)
        if (action.jitterPx > 0) gates += ProFeatureGate.ANTI_BAN_JITTER
        scanPostDelay(action.postDelay, gates)
      }
      is ScriptAction.LongPress -> {
        scanNumericInput(action.x, gates)
        scanNumericInput(action.y, gates)
        if (action.jitterPx > 0 || action.microMovementJitter) {
          gates += ProFeatureGate.ANTI_BAN_JITTER
        }
        if (action.mode != LongPressMode.STANDARD) {
          gates += ProFeatureGate.HOLD_GESTURE_MODES
        }
        action.holdUntilCondition?.let { scanConditionNode(it, gates) }
        scanPostDelay(action.postDelay, gates)
      }
      is ScriptAction.MultiTouch -> {
        gates += ProFeatureGate.MULTI_TOUCH
        if (action.customPoints.any { it.jitterPx > 0 }) {
          gates += ProFeatureGate.ANTI_BAN_JITTER
        }
        scanPostDelay(action.postDelay, gates)
      }
      is ScriptAction.SwipeDrag -> {
        scanNumericInput(action.startX, gates)
        scanNumericInput(action.startY, gates)
        scanNumericInput(action.endX, gates)
        scanNumericInput(action.endY, gates)
        if (action.startJitterPx > 0 || action.endJitterPx > 0) {
          gates += ProFeatureGate.ANTI_BAN_JITTER
        }
        when (action.trajectoryMode) {
          SwipeTrajectoryMode.LINEAR -> Unit
          SwipeTrajectoryMode.BEZIER_CURVE -> gates += ProFeatureGate.BEZIER_TRAJECTORY
          SwipeTrajectoryMode.MULTI_POINT_PATH -> gates += ProFeatureGate.MULTI_POINT_SWIPE
        }
        scanPostDelay(action.postDelay, gates)
      }
      is ScriptAction.InputText -> {
        if (action.engine == TextInjectionEngine.HUMAN_TYPING) {
          gates += ProFeatureGate.HUMAN_TYPING
        }
        scanPostDelay(action.postDelay, gates)
      }
      is ScriptAction.IfBlock -> {
        gates += ProFeatureGate.IF_BLOCK
        scanConditionNode(action.conditionRoot, gates)
        action.thenActions.forEach { scanAction(it, gates) }
        action.elseActions.forEach { scanAction(it, gates) }
      }
      is ScriptAction.LoopBlock -> {
        when (val m = action.mode) {
          is LoopMode.Count -> {
            scanNumericInput(m.count, gates)
            if (!m.exportIndexVarKey.isNullOrBlank()) {
              gates += ProFeatureGate.DYNAMIC_VARIABLES
            }
          }
          is LoopMode.WhileCondition -> {
            gates += ProFeatureGate.ADVANCED_LOOPS
            scanConditionNode(m.conditionRoot, gates)
          }
          is LoopMode.UntilCondition -> {
            gates += ProFeatureGate.ADVANCED_LOOPS
            scanConditionNode(m.conditionRoot, gates)
          }
          LoopMode.Unlimited -> gates += ProFeatureGate.ADVANCED_LOOPS
        }
        if (action.safeguards.intervalDelay !is DelayConfig.Fixed) {
          gates += ProFeatureGate.RANDOM_DEVIATION_DELAY
        }
        action.bodyActions.forEach { scanAction(it, gates) }
      }
      is ScriptAction.Delay -> {
        if (action.config !is DelayConfig.Fixed) {
          gates += ProFeatureGate.RANDOM_DEVIATION_DELAY
        }
      }
      is ScriptAction.Break,
      is ScriptAction.Continue,
      is ScriptAction.EndScript,
      is ScriptAction.Vibrate,
      is ScriptAction.PlayAlertSound -> Unit
      is ScriptAction.KeyEvent -> scanPostDelay(action.postDelay, gates)
      is ScriptAction.LaunchApp -> scanPostDelay(action.postDelay, gates)
      is ScriptAction.ForceStopApp -> {
        gates += ProFeatureGate.FORCE_STOP_APP
        scanPostDelay(action.postDelay, gates)
      }
      is ScriptAction.ClipboardOp -> {
        if (!action.targetVariableKey.isNullOrBlank()) {
          gates += ProFeatureGate.DYNAMIC_VARIABLES
        }
        scanPostDelay(action.postDelay, gates)
      }
      is ScriptAction.VariableOp -> {
        gates += ProFeatureGate.DYNAMIC_VARIABLES
      }
    }
  }

  private fun scanNumericInput(input: NumericInput, gates: MutableSet<ProFeatureGate>) {
    if (input is NumericInput.VariableRef) {
      gates += ProFeatureGate.DYNAMIC_VARIABLES
    }
  }

  private fun scanDurationInput(input: DurationInput, gates: MutableSet<ProFeatureGate>) {
    when (input) {
      is DurationInput.Fixed -> Unit
      is DurationInput.RandomRange -> gates += ProFeatureGate.RANDOM_DEVIATION_DELAY
      is DurationInput.VariableRef -> gates += ProFeatureGate.DYNAMIC_VARIABLES
    }
  }

  private fun scanPostDelay(postDelay: PostActionDelay, gates: MutableSet<ProFeatureGate>) {
    if (postDelay is PostActionDelay.Override && postDelay.config !is DelayConfig.Fixed) {
      gates += ProFeatureGate.RANDOM_DEVIATION_DELAY
    }
  }

  private fun scanConditionNode(node: ConditionNode, gates: MutableSet<ProFeatureGate>) {
    when (node) {
      is ConditionGroup -> node.children.forEach { scanConditionNode(it, gates) }
      is ConditionLeaf -> {
        when (val predicate = node.predicate) {
          is ConditionPredicate.ColorMatch -> {
            if (predicate.mode != ColorMatchMode.SINGLE_POINT || predicate.targets.size > 1) {
              gates += ProFeatureGate.MULTI_POINT_COLOR_MATCH
            }
          }
          is ConditionPredicate.ImageMatch -> gates += ProFeatureGate.IMAGE_MATCH
          is ConditionPredicate.AppMatch -> Unit
          is ConditionPredicate.VariableCompare -> gates += ProFeatureGate.DYNAMIC_VARIABLES
        }
      }
    }
  }
}
