package com.tapflow.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
  private val preferencesRepository: TapFlowPreferencesRepository,
  private val accessibilityManagerBridge: AccessibilityManagerBridge,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OnboardingUiState())
  val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

  private val _effects = Channel<OnboardingUiEffect>(Channel.BUFFERED)
  val effects: Flow<OnboardingUiEffect> = _effects.receiveAsFlow()

  init {
    viewModelScope.launch {
      accessibilityManagerBridge.isAccessibilityConnected.collect { connected ->
        if (connected) {
          _uiState.update { current ->
            val updated = current.copy(accessibilityGranted = true)
            if (updated.allMandatoryGranted) {
              viewModelScope.launch {
                preferencesRepository.setPermissionOnboardingCompleted(true)
              }
            }
            updated
          }
        }
      }
    }
  }

  fun onIntent(intent: OnboardingUiIntent) {
    when (intent) {
      is OnboardingUiIntent.PermissionsEvaluated -> {
        val next =
          _uiState.value.copy(
            accessibilityGranted =
              intent.accessibilityGranted || accessibilityManagerBridge.isAccessibilityConnected.value,
            overlayGranted = intent.overlayGranted,
            notificationsGranted = intent.notificationsGranted,
            batteryOptimizationIgnored = intent.batteryOptimizationIgnored,
          )
        _uiState.value = next
        if (next.allMandatoryGranted) {
          viewModelScope.launch {
            preferencesRepository.setPermissionOnboardingCompleted(true)
          }
        }
      }
      OnboardingUiIntent.OpenAccessibilitySettings -> {
        _uiState.update { it.copy(showAccessibilityDisclosure = true) }
      }
      OnboardingUiIntent.AcceptAccessibilityDisclosure -> {
        _uiState.update { it.copy(showAccessibilityDisclosure = false) }
        sendEffect(OnboardingUiEffect.LaunchAccessibilitySettings)
      }
      OnboardingUiIntent.DismissAccessibilityDisclosure -> {
        _uiState.update { it.copy(showAccessibilityDisclosure = false) }
      }
      OnboardingUiIntent.OpenOverlaySettings -> {
        sendEffect(OnboardingUiEffect.LaunchOverlaySettings)
      }
      OnboardingUiIntent.RequestNotifications -> {
        sendEffect(OnboardingUiEffect.RequestNotificationPermission)
      }
      OnboardingUiIntent.OpenBatteryOptimization -> {
        sendEffect(OnboardingUiEffect.LaunchBatteryOptimizationSettings)
      }
      OnboardingUiIntent.OpenAppInfoForRestrictedSettings -> {
        _uiState.update { it.copy(restrictedGuideExpanded = true) }
        sendEffect(OnboardingUiEffect.LaunchAppDetailsSettings)
      }
      OnboardingUiIntent.ToggleRestrictedGuide -> {
        _uiState.update { it.copy(restrictedGuideExpanded = !it.restrictedGuideExpanded) }
      }
      OnboardingUiIntent.ContinueClicked -> {
        if (!_uiState.value.allMandatoryGranted || _uiState.value.isContinuing) return
        _uiState.update { it.copy(isContinuing = true) }
        viewModelScope.launch {
          preferencesRepository.setPermissionOnboardingCompleted(true)
          _effects.send(OnboardingUiEffect.NavigateToHome)
        }
      }
      OnboardingUiIntent.ExitClicked -> {
        sendEffect(OnboardingUiEffect.ExitApplication)
      }
    }
  }

  private fun sendEffect(effect: OnboardingUiEffect) {
    viewModelScope.launch {
      _effects.send(effect)
    }
  }
}
