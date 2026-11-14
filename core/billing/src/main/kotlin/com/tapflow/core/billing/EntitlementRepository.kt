package com.tapflow.core.billing

import android.app.Activity
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.entitlement.TrialStatus
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class EntitlementRepository @Inject constructor(
  private val billingManager: TapFlowBillingManager,
  private val trialGuardManager: TrialGuardManager,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val latestTrialOverride = MutableStateFlow<TrialEvaluation?>(null)

  val entitlementState: StateFlow<EntitlementState> =
    combine(
      billingManager.billingSnapshot,
      trialGuardManager.trialEvaluationFlow,
      latestTrialOverride,
    ) { billing, flowTrial, overrideTrial ->
      val effectiveTrial = overrideTrial ?: flowTrial
      resolveEntitlementState(billing, effectiveTrial)
    }.stateIn(
      scope = scope,
      started = SharingStarted.Eagerly,
      initialValue = EntitlementState.Free(TrialStatus.NOT_STARTED, 7),
    )

  val localizedProPrice: StateFlow<String?> =
    billingManager.billingSnapshot
      .map { it.formattedPrice }
      .stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null,
      )

  val billingStatusMessage: StateFlow<String?> =
    billingManager.billingSnapshot
      .map { it.statusMessage }
      .stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null,
      )

  init {
    scope.launch {
      refreshEntitlement()
    }
  }

  suspend fun refreshEntitlement() {
    latestTrialOverride.value = trialGuardManager.evaluateAndAdvanceClock()
    billingManager.syncBillingState()
  }

  suspend fun activateSevenDayTrial(): TrialEvaluation {
    val result = trialGuardManager.activateSevenDayTrial()
    latestTrialOverride.value = result
    return result
  }

  fun launchLifetimePurchase(activity: Activity) {
    billingManager.launchLifetimePurchase(activity)
  }

  suspend fun restorePurchases(): BillingRestoreResult {
    val result = billingManager.restorePurchases()
    latestTrialOverride.value = trialGuardManager.evaluateAndAdvanceClock()
    return result
  }

  fun isPro(): Boolean = EntitlementValidator.isPro(entitlementState.value)

  fun canUseFeature(gate: ProFeatureGate): Boolean =
    EntitlementValidator.canUseFeature(entitlementState.value, gate)

  fun canCreateScript(existingCount: Int): Boolean =
    EntitlementValidator.canCreateScript(entitlementState.value, existingCount)

  fun canCreatePipeline(existingCount: Int): Boolean =
    EntitlementValidator.canCreatePipeline(entitlementState.value, existingCount)

  fun detectScriptProBlockers(script: Script): Set<ProFeatureGate> {
    if (isPro()) return emptySet()
    return EntitlementValidator.detectProFeaturesInScript(script)
  }

  fun detectPipelineProBlockers(
    pipeline: Pipeline,
    scriptsById: Map<String, Script>,
  ): Set<ProFeatureGate> {
    if (isPro()) return emptySet()
    return EntitlementValidator.detectProFeaturesInPipeline(pipeline, scriptsById)
  }

  private fun resolveEntitlementState(
    billing: BillingSnapshot,
    trial: TrialEvaluation,
  ): EntitlementState {
    if (billing.isLifetimeOwned) {
      return EntitlementState.ProLifetime(
        purchaseTokenHash = billing.purchaseTokenHash.orEmpty(),
        purchasedAtUtcMs = billing.purchasedAtUtcMs,
      )
    }
    if (billing.isPendingPurchase) {
      return EntitlementState.PendingPurchase
    }
    if (trial.status == TrialStatus.ACTIVE && trial.expiresAtUtcMs != null) {
      return EntitlementState.ProTrial(
        expiresAtUtcMs = trial.expiresAtUtcMs,
        remainingDays = trial.remainingDays,
        remainingHours = trial.remainingHours,
      )
    }
    return EntitlementState.Free(
      trialStatus = trial.status,
      trialDaysRemaining = if (trial.status == TrialStatus.NOT_STARTED) 7 else 0,
    )
  }
}
