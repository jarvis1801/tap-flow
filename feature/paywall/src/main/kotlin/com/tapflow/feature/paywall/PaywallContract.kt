package com.tapflow.feature.paywall

import androidx.compose.runtime.Immutable
import com.tapflow.core.billing.PaywallPricingState
import com.tapflow.core.billing.TrialEvaluation
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus

@Immutable
data class PaywallComparisonRow(
  val id: String,
  val feature: String,
  val freeTier: String,
  val proTier: String,
  val lifetimeOnly: Boolean = false,
)

@Immutable
data class PaywallUiState(
  val entitlementState: EntitlementState = EntitlementState.Free(TrialStatus.NOT_STARTED, 7),
  val trialEvaluation: TrialEvaluation? = null,
  val pricingState: PaywallPricingState = PaywallPricingState(),
  val formattedPrice: String? = null,
  val statusMessage: String? = null,
  val sourceFeature: String? = null,
  val isProcessingAction: Boolean = false,
  val isPromoSectionExpanded: Boolean = false,
  val promoCodeInput: String = "",
  val isRedeemingCode: Boolean = false,
  val promoErrorMessage: String? = null,
  val promoSuccessMessage: String? = null,
) {
  val isProOwned: Boolean
    get() = entitlementState is EntitlementState.ProLifetime

  val isPendingPurchase: Boolean
    get() = entitlementState is EntitlementState.PendingPurchase

  val canStartTrial: Boolean
    get() =
      entitlementState is EntitlementState.Free &&
        entitlementState.trialStatus == TrialStatus.NOT_STARTED

  val isTrialActive: Boolean
    get() = entitlementState is EntitlementState.ProTrial

  val isTrialExpired: Boolean
    get() =
      entitlementState is EntitlementState.Free &&
        (entitlementState.trialStatus == TrialStatus.EXPIRED ||
          entitlementState.trialStatus == TrialStatus.EXPIRED_TAMPERED)

  val effectiveFormattedPrice: String?
    get() = pricingState.effectiveFormattedPrice ?: formattedPrice
}

sealed interface PaywallUiIntent {
  data class SetSourceFeature(val sourceFeature: String?) : PaywallUiIntent

  data object ActivateTrialClicked : PaywallUiIntent

  data object PurchaseLifetimeClicked : PaywallUiIntent

  data object RestorePurchaseClicked : PaywallUiIntent

  data object TogglePromoSection : PaywallUiIntent

  data class UpdatePromoCodeInput(val value: String) : PaywallUiIntent

  data object ApplyPromoCodeClicked : PaywallUiIntent

  data object DismissStatusMessage : PaywallUiIntent

  data object NavigateBackClicked : PaywallUiIntent
}

sealed interface PaywallUiEffect {
  data class LaunchPlayBillingFlow(val targetSku: String) : PaywallUiEffect

  data class ShowSnackbar(val message: String) : PaywallUiEffect

  data object NavigateBack : PaywallUiEffect
}
