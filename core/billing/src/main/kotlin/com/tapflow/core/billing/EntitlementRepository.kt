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
  private val promoRedeemManager: PromoRedeemManager,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val latestTrialOverride = MutableStateFlow<TrialEvaluation?>(null)
  private val latestPromoOverride = MutableStateFlow<PromoEvaluation?>(null)

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

  val promoEvaluation: StateFlow<PromoEvaluation> =
    combine(
      promoRedeemManager.promoEvaluationFlow,
      latestPromoOverride,
    ) { flowPromo, overridePromo ->
      overridePromo ?: flowPromo
    }.stateIn(
      scope = scope,
      started = SharingStarted.Eagerly,
      initialValue = PromoEvaluation(),
    )

  val pricingState: StateFlow<PaywallPricingState> =
    combine(
      billingManager.billingSnapshot,
      entitlementState,
      promoEvaluation,
    ) { billing, entitlement, promo ->
      resolvePricingState(billing, entitlement, promo)
    }.stateIn(
      scope = scope,
      started = SharingStarted.Eagerly,
      initialValue = PaywallPricingState(),
    )

  val localizedProPrice: StateFlow<String?> =
    pricingState
      .map { it.effectiveFormattedPrice }
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
    latestPromoOverride.value = promoRedeemManager.evaluateAndAdvanceClock()
    billingManager.syncBillingState()
    scope.launch {
      promoRedeemManager.refreshConfigInBackground()
    }
  }

  suspend fun activateSevenDayTrial(): TrialEvaluation {
    val result = trialGuardManager.activateSevenDayTrial()
    latestTrialOverride.value = result
    return result
  }

  suspend fun redeemPromoCode(rawCode: String): PromoRedeemResult {
    val result = promoRedeemManager.redeemCode(rawCode)
    if (result is PromoRedeemResult.Success) {
      latestPromoOverride.value = promoRedeemManager.evaluateAndAdvanceClock()
    }
    return result
  }

  fun launchLifetimePurchase(
    activity: Activity,
    targetSku: String = pricingState.value.activeSku,
  ) {
    billingManager.launchLifetimePurchase(activity = activity, targetSku = targetSku)
  }

  suspend fun restorePurchases(): BillingRestoreResult {
    val result = billingManager.restorePurchases()
    latestTrialOverride.value = trialGuardManager.evaluateAndAdvanceClock()
    latestPromoOverride.value = promoRedeemManager.evaluateAndAdvanceClock()
    return result
  }

  fun isPro(): Boolean = EntitlementValidator.isPro(entitlementState.value)

  fun isLifetimePro(): Boolean = EntitlementValidator.hasLifetimeProAccess(entitlementState.value)

  fun canUseFeature(gate: ProFeatureGate): Boolean =
    EntitlementValidator.canUseFeature(entitlementState.value, gate)

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

  internal fun resolvePricingState(
    billing: BillingSnapshot,
    entitlement: EntitlementState,
    promo: PromoEvaluation,
  ): PaywallPricingState {
    val regularOffer = billing.regularOffer
    val promoOffer = billing.promoOffer
    val isDiscountEligible =
      EntitlementValidator.isDiscountEligible(
        entitlementState = entitlement,
        hasActivePromoWindow = promo.isActive,
      )
    val discountReason =
      when {
        promo.isActive -> DiscountReason.PROMO_CODE_ACTIVE
        entitlement is EntitlementState.ProTrial -> DiscountReason.TRIAL_ACTIVE
        entitlement is EntitlementState.Free && entitlement.trialStatus == TrialStatus.NOT_STARTED ->
          DiscountReason.TRIAL_NOT_STARTED
        else -> DiscountReason.NONE
      }
    val showPromo = isDiscountEligible && promoOffer != null
    val activeSku =
      if (showPromo) {
        promo.activePromoSku?.takeIf { it.isNotBlank() && promo.isActive }
          ?: EntitlementValidator.SKU_PRO_LIFETIME_PROMO
      } else {
        EntitlementValidator.SKU_PRO_LIFETIME
      }
    val discountPercentOff =
      if (showPromo && regularOffer != null && promoOffer != null) {
        EntitlementValidator.computeDiscountPercentOff(
          regularPriceMicros = regularOffer.priceAmountMicros,
          promoPriceMicros = promoOffer.priceAmountMicros,
        )
      } else {
        null
      }

    return PaywallPricingState(
      regularProduct = regularOffer,
      promoProduct = promoOffer,
      isDiscountEligible = isDiscountEligible,
      discountReason = discountReason,
      activeSku = activeSku,
      discountPercentOff = discountPercentOff,
      activePromoCode = promo.activePromoCode.takeIf { promo.isActive },
      promoRemainingDays = if (promo.isActive) promo.remainingDays else 0,
      promoRemainingHours = if (promo.isActive) promo.remainingHours else 0,
    )
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
