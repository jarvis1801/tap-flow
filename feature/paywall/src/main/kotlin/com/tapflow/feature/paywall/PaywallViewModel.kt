package com.tapflow.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.BillingRestoreResult
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.billing.PaywallPricingState
import com.tapflow.core.billing.PromoRedeemResult
import com.tapflow.core.billing.TrialEvaluation
import com.tapflow.core.billing.TrialGuardManager
import com.tapflow.core.model.common.FeatureFlags
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PaywallViewModel @Inject constructor(
  private val entitlementRepository: EntitlementRepository,
  private val trialGuardManager: TrialGuardManager,
) : ViewModel() {

  private data class PromoUiLocalState(
    val isExpanded: Boolean = false,
    val input: String = "",
    val isRedeeming: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
  )

  private val _sourceFeature = MutableStateFlow<String?>(null)
  private val _isProcessing = MutableStateFlow(false)
  private val _transientMessage = MutableStateFlow<String?>(null)
  private val _promoUi = MutableStateFlow(PromoUiLocalState())

  private val _uiState = MutableStateFlow(PaywallUiState())
  val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

  private val _effects = Channel<PaywallUiEffect>(Channel.BUFFERED)
  val effects: Flow<PaywallUiEffect> = _effects.receiveAsFlow()

  init {
    viewModelScope.launch {
      entitlementRepository.refreshEntitlement()
    }

    viewModelScope.launch {
      combine(
        entitlementRepository.entitlementState,
        trialGuardManager.trialEvaluationFlow,
        entitlementRepository.pricingState,
        entitlementRepository.billingStatusMessage,
        _sourceFeature,
        _isProcessing,
        _transientMessage,
        _promoUi,
      ) { values ->
        val entitlement = values[0] as EntitlementState
        val trialEval = values[1] as TrialEvaluation
        val pricing = values[2] as PaywallPricingState
        val billingMsg = values[3] as String?
        val source = values[4] as String?
        val processing = values[5] as Boolean
        val transient = values[6] as String?
        val promoUi = values[7] as PromoUiLocalState

        PaywallUiState(
          entitlementState = entitlement,
          trialEvaluation = trialEval,
          pricingState = pricing,
          formattedPrice = pricing.effectiveFormattedPrice,
          statusMessage = transient ?: billingMsg,
          sourceFeature = source,
          isProcessingAction = processing,
          isPromoSectionExpanded = promoUi.isExpanded,
          promoCodeInput = promoUi.input,
          isRedeemingCode = promoUi.isRedeeming,
          promoErrorMessage = promoUi.errorMessage,
          promoSuccessMessage = promoUi.successMessage,
        )
      }.collect { state ->
        _uiState.value = state
      }
    }
  }

  fun onIntent(intent: PaywallUiIntent) {
    when (intent) {
      is PaywallUiIntent.SetSourceFeature -> {
        _sourceFeature.value = intent.sourceFeature
      }
      PaywallUiIntent.ActivateTrialClicked -> {
        if (_isProcessing.value) return
        viewModelScope.launch {
          _isProcessing.value = true
          val eval = entitlementRepository.activateSevenDayTrial()
          _isProcessing.value = false
          val msg =
            when (eval.status) {
              TrialStatus.ACTIVE ->
                if (FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
                  "7-Day Pro Trial activated! Desktop Console & Data Backup require Lifetime Pro."
                } else {
                  "7-Day Pro Trial activated! .tapflow Data Backup requires Lifetime Pro."
                }
              TrialStatus.EXPIRED, TrialStatus.EXPIRED_TAMPERED ->
                "Free trial has already been used or expired on this device."
              TrialStatus.NOT_STARTED -> "Unable to activate trial."
            }
          _effects.send(PaywallUiEffect.ShowSnackbar(msg))
        }
      }
      PaywallUiIntent.PurchaseLifetimeClicked -> {
        val targetSku = _uiState.value.pricingState.activeSku
        viewModelScope.launch {
          _effects.send(PaywallUiEffect.LaunchPlayBillingFlow(targetSku))
        }
      }
      PaywallUiIntent.RestorePurchaseClicked -> {
        if (_isProcessing.value) return
        viewModelScope.launch {
          _isProcessing.value = true
          val result = entitlementRepository.restorePurchases()
          _isProcessing.value = false
          val msg =
            when (result) {
              BillingRestoreResult.RESTORED_LIFETIME ->
                "TapFlow Lifetime Pro purchase restored."
              BillingRestoreResult.PENDING_SETTLEMENT ->
                "Payment is still pending settlement in Google Play."
              BillingRestoreResult.NO_PURCHASE_FOUND ->
                "No existing Lifetime Pro purchase found on this Google account."
              BillingRestoreResult.SERVICE_UNAVAILABLE ->
                "Google Play Billing is currently unavailable. Please check your connection."
            }
          _transientMessage.value = msg
          _effects.send(PaywallUiEffect.ShowSnackbar(msg))
        }
      }
      PaywallUiIntent.TogglePromoSection -> {
        _promoUi.update { it.copy(isExpanded = !it.isExpanded) }
      }
      is PaywallUiIntent.UpdatePromoCodeInput -> {
        _promoUi.update {
          it.copy(
            input = intent.value,
            errorMessage = null,
          )
        }
      }
      PaywallUiIntent.ApplyPromoCodeClicked -> {
        if (_promoUi.value.isRedeeming) return
        val rawInput = _promoUi.value.input
        viewModelScope.launch {
          _promoUi.update {
            it.copy(
              isRedeeming = true,
              errorMessage = null,
              successMessage = null,
            )
          }
          when (val result = entitlementRepository.redeemPromoCode(rawInput)) {
            is PromoRedeemResult.Success -> {
              val daysLabel =
                if (result.discountWindowDays == 1) "1 day" else "${result.discountWindowDays} days"
              val successMsg = "Code applied! Special price unlocked for $daysLabel"
              _promoUi.update {
                it.copy(
                  isRedeeming = false,
                  input = result.code,
                  errorMessage = null,
                  successMessage = successMsg,
                )
              }
              _effects.send(PaywallUiEffect.ShowSnackbar(successMsg))
            }
            is PromoRedeemResult.Error -> {
              _promoUi.update {
                it.copy(
                  isRedeeming = false,
                  errorMessage = result.message,
                  successMessage = null,
                )
              }
            }
          }
        }
      }
      PaywallUiIntent.DismissStatusMessage -> {
        _transientMessage.value = null
        _uiState.update { it.copy(statusMessage = null) }
      }
      PaywallUiIntent.NavigateBackClicked -> {
        viewModelScope.launch {
          _effects.send(PaywallUiEffect.NavigateBack)
        }
      }
    }
  }
}
