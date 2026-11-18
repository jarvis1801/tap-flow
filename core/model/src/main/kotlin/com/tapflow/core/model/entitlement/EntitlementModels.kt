package com.tapflow.core.model.entitlement

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
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

  val hasTrialOrLifetimeAccess: Boolean
    get() = this is ProTrial || this is ProLifetime

  val hasLifetimeProAccess: Boolean
    get() = this is ProLifetime
}

@Serializable
enum class ProFeatureGate(val displayName: String) {
  EXTERNAL_BROADCAST("MacroDroid & Tasker Integration"),
  SHIZUKU_SUITE("Seamless (Shizuku) Suite"),
  ADVANCED_VISION("Advanced Image Vision & Inspector"),
  PIPELINE_AUTOMATION("Pipeline Looping & Crash Recovery"),
  FULL_LOGS("Full Execution Logs & Export"),
  AMOLED_THEME("AMOLED Pure Black Theme"),
  DESKTOP_CONSOLE("Desktop Studio ADB Bridge"),
  DATA_BACKUP("Data Backup & Transfer (.tapflow)"),
  ;

  val requiresLifetimePro: Boolean
    get() = this == DESKTOP_CONSOLE || this == DATA_BACKUP
}

object EntitlementValidator {
  const val TRIAL_DURATION_MS: Long = 7L * 24L * 60L * 60L * 1000L
  const val CLOCK_ROLLBACK_TOLERANCE_MS: Long = 60_000L
  const val SKU_PRO_LIFETIME: String = "tapflow_pro_lifetime"
  const val PURCHASE_OPTION_LIFETIME: String = "buy-lifetime"
  const val SKU_PRO_LIFETIME_PROMO: String = "tapflow_pro_lifetime_promo"
  const val PURCHASE_OPTION_PROMO: String = "buy-promo"
  val LIFETIME_PRO_SKUS: Set<String> = setOf(SKU_PRO_LIFETIME, SKU_PRO_LIFETIME_PROMO)

  fun isLifetimeProSku(productId: String): Boolean =
    productId == SKU_PRO_LIFETIME || productId == SKU_PRO_LIFETIME_PROMO

  fun expectedPurchaseOptionId(productId: String): String? =
    when (productId) {
      SKU_PRO_LIFETIME -> PURCHASE_OPTION_LIFETIME
      SKU_PRO_LIFETIME_PROMO -> PURCHASE_OPTION_PROMO
      else -> null
    }

  fun isDiscountEligible(
    entitlementState: EntitlementState,
    hasActivePromoWindow: Boolean,
  ): Boolean =
    hasActivePromoWindow ||
      entitlementState is EntitlementState.ProTrial ||
      (entitlementState is EntitlementState.Free &&
        entitlementState.trialStatus == TrialStatus.NOT_STARTED)

  fun computeDiscountPercentOff(
    regularPriceMicros: Long,
    promoPriceMicros: Long,
  ): Int? {
    if (regularPriceMicros <= 0L || promoPriceMicros <= 0L || promoPriceMicros >= regularPriceMicros) {
      return null
    }
    val pct = (((regularPriceMicros - promoPriceMicros).toDouble() / regularPriceMicros.toDouble()) * 100.0)
    return kotlin.math.round(pct).toInt().coerceIn(1, 99)
  }

  fun hasTrialOrLifetimeAccess(state: EntitlementState): Boolean =
    state.hasTrialOrLifetimeAccess

  fun hasLifetimeProAccess(state: EntitlementState): Boolean =
    state.hasLifetimeProAccess

  fun isPro(state: EntitlementState): Boolean = hasTrialOrLifetimeAccess(state)

  fun canUseFeature(state: EntitlementState, gate: ProFeatureGate): Boolean =
    if (gate.requiresLifetimePro) {
      hasLifetimeProAccess(state)
    } else {
      hasTrialOrLifetimeAccess(state)
    }

  fun detectProFeaturesInScript(script: Script): Set<ProFeatureGate> {
    val gates = linkedSetOf<ProFeatureGate>()
    script.actions.forEach { scanAction(it, gates) }
    return gates
  }

  fun detectProFeaturesInPipeline(
    pipeline: Pipeline,
    scriptsById: Map<String, Script>,
  ): Set<ProFeatureGate> {
    val gates = linkedSetOf<ProFeatureGate>()
    val usesMultiOrInfiniteLoop =
      pipeline.repeatMode is PipelineRepeatMode.Unlimited ||
        (pipeline.repeatMode is PipelineRepeatMode.Count && pipeline.repeatMode.iterations > 1)
    if (usesMultiOrInfiniteLoop || pipeline.errorStrategy == PipelineErrorStrategy.SKIP_AND_CONTINUE) {
      gates += ProFeatureGate.PIPELINE_AUTOMATION
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
      is ScriptAction.LongPress -> {
        action.holdUntilCondition?.let { scanConditionNode(it, gates) }
      }
      is ScriptAction.IfBlock -> {
        scanConditionNode(action.conditionRoot, gates)
        action.thenActions.forEach { scanAction(it, gates) }
        action.elseIfBranches.forEach { branch ->
          scanConditionNode(branch.conditionRoot, gates)
          branch.actions.forEach { scanAction(it, gates) }
        }
        action.elseActions.forEach { scanAction(it, gates) }
      }
      is ScriptAction.LoopBlock -> {
        when (val m = action.mode) {
          is LoopMode.Count,
          LoopMode.Unlimited -> Unit
          is LoopMode.WhileCondition -> scanConditionNode(m.conditionRoot, gates)
          is LoopMode.UntilCondition -> scanConditionNode(m.conditionRoot, gates)
        }
        action.bodyActions.forEach { scanAction(it, gates) }
      }
      is ScriptAction.Tap,
      is ScriptAction.DoubleTap,
      is ScriptAction.MultiTouch,
      is ScriptAction.SwipeDrag,
      is ScriptAction.InputText,
      is ScriptAction.Delay,
      is ScriptAction.Break,
      is ScriptAction.Continue,
      is ScriptAction.EndScript,
      is ScriptAction.Vibrate,
      is ScriptAction.PlayAlertSound,
      is ScriptAction.KeyEvent,
      is ScriptAction.LaunchApp,
      is ScriptAction.ForceStopApp,
      is ScriptAction.ClipboardOp,
      is ScriptAction.VariableOp -> Unit
    }
  }

  private fun scanConditionNode(node: ConditionNode, gates: MutableSet<ProFeatureGate>) {
    when (node) {
      is ConditionGroup -> node.children.forEach { scanConditionNode(it, gates) }
      is ConditionLeaf -> {
        when (val predicate = node.predicate) {
          is ConditionPredicate.ColorMatch -> {
            if (predicate.mode != ColorMatchMode.SINGLE_POINT || predicate.targets.size > 1) {
              gates += ProFeatureGate.ADVANCED_VISION
            }
          }
          is ConditionPredicate.ImageMatch -> gates += ProFeatureGate.ADVANCED_VISION
          is ConditionPredicate.AppMatch,
          is ConditionPredicate.VariableCompare -> Unit
        }
      }
    }
  }
}
