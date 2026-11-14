package com.tapflow.feature.onboarding

import androidx.compose.runtime.Immutable

@Immutable
data class OnboardingUiState(
  val accessibilityGranted: Boolean = false,
  val overlayGranted: Boolean = false,
  val notificationsGranted: Boolean = false,
  val batteryOptimizationIgnored: Boolean = false,
  val restrictedGuideExpanded: Boolean = false,
  val isContinuing: Boolean = false,
) {
  val mandatoryGrantedCount: Int
    get() =
      (if (accessibilityGranted) 1 else 0) +
        (if (overlayGranted) 1 else 0) +
        (if (notificationsGranted) 1 else 0)

  val allMandatoryGranted: Boolean
    get() = accessibilityGranted && overlayGranted && notificationsGranted
}

sealed interface OnboardingUiIntent {
  data class PermissionsEvaluated(
    val accessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
  ) : OnboardingUiIntent

  data object OpenAccessibilitySettings : OnboardingUiIntent

  data object OpenOverlaySettings : OnboardingUiIntent

  data object RequestNotifications : OnboardingUiIntent

  data object OpenBatteryOptimization : OnboardingUiIntent

  data object OpenAppInfoForRestrictedSettings : OnboardingUiIntent

  data object ToggleRestrictedGuide : OnboardingUiIntent

  data object ContinueClicked : OnboardingUiIntent

  data object ExitClicked : OnboardingUiIntent
}

sealed interface OnboardingUiEffect {
  data object LaunchAccessibilitySettings : OnboardingUiEffect

  data object LaunchOverlaySettings : OnboardingUiEffect

  data object RequestNotificationPermission : OnboardingUiEffect

  data object LaunchBatteryOptimizationSettings : OnboardingUiEffect

  data object LaunchAppDetailsSettings : OnboardingUiEffect

  data object NavigateToHome : OnboardingUiEffect

  data object ExitApplication : OnboardingUiEffect
}
