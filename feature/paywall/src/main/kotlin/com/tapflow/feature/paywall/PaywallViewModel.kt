package com.tapflow.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.billing.BillingRestoreResult
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.billing.TrialGuardManager
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

  private val _sourceFeature = MutableStateFlow<String?>(null)
  private val _isProcessing = MutableStateFlow(false)
  private val _transientMessage = MutableStateFlow<String?>(null)

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
        entitlementRepository.localizedProPrice,
        entitlementRepository.billingStatusMessage,
        _sourceFeature,
        _isProcessing,
        _transientMessage,
      ) { values ->
        val entitlement = values[0] as com.tapflow.core.model.entitlement.EntitlementState
        val trialEval = values[1] as com.tapflow.core.billing.TrialEvaluation
        val price = values[2] as String?
        val billingMsg = values[3] as String?
        val source = values[4] as String?
        val processing = values[5] as Boolean
        val transient = values[6] as String?

        PaywallUiState(
          entitlementState = entitlement,
          trialEvaluation = trialEval,
          formattedPrice = price,
          statusMessage = transient ?: billingMsg,
          sourceFeature = source,
          isProcessingAction = processing,
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
                "7-Day Pro Trial activated! Desktop Console & Data Backup require Lifetime Pro."
              TrialStatus.EXPIRED, TrialStatus.EXPIRED_TAMPERED ->
                "Free trial has already been used or expired on this device."
              TrialStatus.NOT_STARTED -> "Unable to activate trial."
            }
          _effects.send(PaywallUiEffect.ShowSnackbar(msg))
        }
      }
      PaywallUiIntent.PurchaseLifetimeClicked -> {
        viewModelScope.launch {
          _effects.send(PaywallUiEffect.LaunchPlayBillingFlow)
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
