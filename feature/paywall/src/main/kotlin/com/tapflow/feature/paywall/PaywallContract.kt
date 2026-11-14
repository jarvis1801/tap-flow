package com.tapflow.feature.paywall

import androidx.compose.runtime.Immutable
import com.tapflow.core.billing.TrialEvaluation
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus

@Immutable
data class PaywallComparisonRow(
  val id: String,
  val feature: String,
  val freeTier: String,
  val proTier: String,
)

@Immutable
data class PaywallUiState(
  val entitlementState: EntitlementState = EntitlementState.Free(TrialStatus.NOT_STARTED, 7),
  val trialEvaluation: TrialEvaluation? = null,
  val formattedPrice: String? = null,
  val statusMessage: String? = null,
  val sourceFeature: String? = null,
  val isProcessingAction: Boolean = false,
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
}

sealed interface PaywallUiIntent {
  data class SetSourceFeature(val sourceFeature: String?) : PaywallUiIntent

  data object ActivateTrialClicked : PaywallUiIntent

  data object PurchaseLifetimeClicked : PaywallUiIntent

  data object RestorePurchaseClicked : PaywallUiIntent

  data object DismissStatusMessage : PaywallUiIntent

  data object NavigateBackClicked : PaywallUiIntent
}

sealed interface PaywallUiEffect {
  data object LaunchPlayBillingFlow : PaywallUiEffect

  data class ShowSnackbar(val message: String) : PaywallUiEffect

  data object NavigateBack : PaywallUiEffect
}
