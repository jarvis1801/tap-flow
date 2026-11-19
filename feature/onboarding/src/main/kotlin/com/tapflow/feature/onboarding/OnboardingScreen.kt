package com.tapflow.feature.onboarding

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.service.accessibility.TapFlowAccessibilityService
import com.tapflow.core.service.permissions.AccessibilityProminentDisclosureDialog
import com.tapflow.core.service.permissions.openAccessibilitySettings

@Composable
fun OnboardingScreen(
  onNavigateToHome: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: OnboardingViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var hasNavigatedToHome by remember { mutableStateOf(false) }

  fun evaluateAndDispatch() {
    val snapshot = evaluateDevicePermissions(context)
    viewModel.onIntent(
      OnboardingUiIntent.PermissionsEvaluated(
        accessibilityGranted = snapshot.accessibilityGranted,
        overlayGranted = snapshot.overlayGranted,
        notificationsGranted = snapshot.notificationsGranted,
        batteryOptimizationIgnored = snapshot.batteryOptimizationIgnored,
      ),
    )
  }

  val notificationLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
    ) {
      evaluateAndDispatch()
    }

  LifecycleResumeEffect(Unit) {
    evaluateAndDispatch()
    onPauseOrDispose {}
  }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        OnboardingUiEffect.LaunchAccessibilitySettings -> {
          openAccessibilitySettings(context)
        }
        OnboardingUiEffect.LaunchOverlaySettings -> {
          val intent =
            Intent(
              Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
              Uri.parse("package:${context.packageName}"),
            )
          runCatching { context.startActivity(intent) }
            .onFailure {
              context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
        OnboardingUiEffect.RequestNotificationPermission -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status =
              ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (status == PackageManager.PERMISSION_GRANTED) {
              evaluateAndDispatch()
            } else {
              val activity = context.findActivity()
              val shouldShowRationale =
                activity?.shouldShowRequestPermissionRationale(
                  Manifest.permission.POST_NOTIFICATIONS,
                ) == true
              if (shouldShowRationale || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                val settingsIntent =
                  Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                  }
                runCatching { context.startActivity(settingsIntent) }
                  .onFailure { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
              }
            }
          } else {
            evaluateAndDispatch()
          }
        }
        OnboardingUiEffect.LaunchBatteryOptimizationSettings -> {
          val intent =
            Intent(
              Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
              Uri.parse("package:${context.packageName}"),
            )
          runCatching { context.startActivity(intent) }
            .onFailure {
              runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
              }
            }
        }
        OnboardingUiEffect.LaunchAppDetailsSettings -> {
          val intent =
            Intent(
              Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.parse("package:${context.packageName}"),
            )
          runCatching { context.startActivity(intent) }
        }
        OnboardingUiEffect.NavigateToHome -> {
          if (!hasNavigatedToHome) {
            hasNavigatedToHome = true
            onNavigateToHome()
          }
        }
        OnboardingUiEffect.ExitApplication -> {
          context.findActivity()?.finishAffinity()
        }
      }
    }
  }

  OnboardingContent(
    state = uiState,
    onIntent = viewModel::onIntent,
    modifier = modifier,
  )
}

@Composable
fun OnboardingContent(
  state: OnboardingUiState,
  onIntent: (OnboardingUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (state.showAccessibilityDisclosure) {
    AccessibilityProminentDisclosureDialog(
      onAcceptAndContinue = { onIntent(OnboardingUiIntent.AcceptAccessibilityDisclosure) },
      onDecline = { onIntent(OnboardingUiIntent.DismissAccessibilityDisclosure) },
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { onIntent(OnboardingUiIntent.ExitClicked) },
            modifier =
              Modifier
                .heightIn(min = 48.dp)
                .testTag("onboarding_exit_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Exit App")
          }

          var continueDispatched by remember { mutableStateOf(false) }
          Button(
            onClick = {
              if (state.allMandatoryGranted && !state.isContinuing && !continueDispatched) {
                continueDispatched = true
                onIntent(OnboardingUiIntent.ContinueClicked)
              }
            },
            enabled = state.allMandatoryGranted && !state.isContinuing && !continueDispatched,
            modifier =
              Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("onboarding_continue_button"),
          ) {
            Text(
              text =
                if (state.allMandatoryGranted) {
                  "Get Started"
                } else {
                  "Grant Required (${state.mandatoryGrantedCount}/3)"
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .statusBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LazyColumn(
        modifier =
          Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item(key = "header") {
          OnboardingHeaderCard(state = state)
        }

        item(key = "accessibility") {
          PermissionChecklistCard(
            title = "Accessibility Service",
            description =
              "Enables simulated touch input, swipes, global system actions, and foreground app detection.",
            icon = Icons.Filled.AccessibilityNew,
            isMandatory = true,
            isGranted = state.accessibilityGranted,
            buttonTag = "grant_accessibility_button",
            onGrantClick = { onIntent(OnboardingUiIntent.OpenAccessibilitySettings) },
          )
        }

        item(key = "overlay") {
          PermissionChecklistCard(
            title = "Floating Overlay Window",
            description =
              "Allows the floating control dock, gesture picker, and inspection loupe to display over other apps.",
            icon = Icons.Filled.Layers,
            isMandatory = true,
            isGranted = state.overlayGranted,
            buttonTag = "grant_overlay_button",
            onGrantClick = { onIntent(OnboardingUiIntent.OpenOverlaySettings) },
          )
        }

        item(key = "notifications") {
          PermissionChecklistCard(
            title = "Post Notifications",
            description =
              "Keeps foreground automation services alive and displays active runner controls in the status bar.",
            icon = Icons.Filled.NotificationsActive,
            isMandatory = true,
            isGranted = state.notificationsGranted,
            buttonTag = "grant_notifications_button",
            onGrantClick = { onIntent(OnboardingUiIntent.RequestNotifications) },
          )
        }

        item(key = "battery") {
          PermissionChecklistCard(
            title = "Ignore Battery Optimization",
            description =
              "Recommended so Android does not suspend background scripts during extended automation runs.",
            icon = Icons.Filled.BatteryChargingFull,
            isMandatory = false,
            isGranted = state.batteryOptimizationIgnored,
            buttonTag = "grant_battery_button",
            onGrantClick = { onIntent(OnboardingUiIntent.OpenBatteryOptimization) },
          )
        }

        item(key = "restricted_settings") {
          RestrictedSettingsHelperCard(
            expanded = state.restrictedGuideExpanded,
            onToggleGuide = { onIntent(OnboardingUiIntent.ToggleRestrictedGuide) },
            onOpenAppInfo = { onIntent(OnboardingUiIntent.OpenAppInfoForRestrictedSettings) },
          )
        }
      }
    }
  }
}

@Composable
private fun OnboardingHeaderCard(state: OnboardingUiState) {
  Card(
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(10.dp).size(24.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "${state.mandatoryGrantedCount} of 3 mandatory permissions granted",
            style = MaterialTheme.typography.labelLarge,
            color =
              if (state.allMandatoryGranted) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }

      Text(
        text =
          "TapFlow requires the following system permissions to perform automated gestures and display floating controls.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      LinearProgressIndicator(
        progress = { state.mandatoryGrantedCount / 3f },
        modifier = Modifier.fillMaxWidth().height(6.dp),
      )
    }
  }
}

@Composable
private fun PermissionChecklistCard(
  title: String,
  description: String,
  icon: ImageVector,
  isMandatory: Boolean,
  isGranted: Boolean,
  buttonTag: String,
  onGrantClick: () -> Unit,
) {
  val borderColor =
    if (isGranted) {
      MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
      MaterialTheme.colorScheme.outlineVariant
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    border = BorderStroke(1.dp, borderColor),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isGranted) {
            MaterialTheme.colorScheme.surfaceContainerHigh
          } else {
            MaterialTheme.colorScheme.surfaceContainer
          },
      ),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint =
            if (isGranted) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Surface(
          shape = MaterialTheme.shapes.small,
          color =
            if (isMandatory) {
              MaterialTheme.colorScheme.secondaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
          Text(
            text = if (isMandatory) "Required" else "Recommended",
            style = MaterialTheme.typography.labelSmall,
            color =
              if (isMandatory) {
                MaterialTheme.colorScheme.onSecondaryContainer
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (isGranted) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Permission granted",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Granted",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        } else {
          FilledTonalButton(
            onClick = onGrantClick,
            modifier =
              Modifier
                .heightIn(min = 48.dp)
                .testTag(buttonTag),
          ) {
            Text("Grant Permission")
          }
        }
      }
    }
  }
}

@Composable
private fun RestrictedSettingsHelperCard(
  expanded: Boolean,
  onToggleGuide: () -> Unit,
  onOpenAppInfo: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Filled.SettingsApplications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
          )
          Text(
            text = "Blocked by \"Restricted Setting\"?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
        TextButton(
          onClick = onToggleGuide,
          colors =
            ButtonDefaults.textButtonColors(
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
          modifier = Modifier.testTag("toggle_restricted_guide_button"),
        ) {
          Text(if (expanded) "Hide" else "Guide")
          Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
          )
        }
      }

      Text(
        text =
          "On Android 13–16+, sideloaded builds may lock Accessibility or Overlay toggles until restricted settings are allowed in App Info.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.88f),
      )

      AnimatedVisibility(visible = expanded) {
        Column(
          modifier = Modifier.padding(top = 4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "1. Tap Open App Info below.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
          Text(
            text = "2. Tap the top-right ⋮ overflow menu.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
          Text(
            text = "3. Choose \"Allow restricted settings\", then return and enable Accessibility/Overlay.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
      }

      FilledTonalButton(
        onClick = onOpenAppInfo,
        colors =
          ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("open_app_info_restricted_button"),
      ) {
        Text("Open App Info")
      }
    }
  }
}

private data class EvaluatedPermissionSnapshot(
  val accessibilityGranted: Boolean,
  val overlayGranted: Boolean,
  val notificationsGranted: Boolean,
  val batteryOptimizationIgnored: Boolean,
)

private fun evaluateDevicePermissions(context: Context): EvaluatedPermissionSnapshot {
  val accessibilityGranted = isAccessibilityServiceEnabled(context)
  val overlayGranted = Settings.canDrawOverlays(context)
  val notificationsGranted =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
  val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

  return EvaluatedPermissionSnapshot(
    accessibilityGranted = accessibilityGranted,
    overlayGranted = overlayGranted,
    notificationsGranted = notificationsGranted,
    batteryOptimizationIgnored = batteryIgnored,
  )
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
  val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
  val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
  val targetClass = TapFlowAccessibilityService::class.java.name
  val targetPackage = context.packageName
  val foundInEnabledList =
    enabled.any { info ->
      val svc = info.resolveInfo?.serviceInfo
      svc != null && svc.packageName == targetPackage && svc.name == targetClass
    }
  if (foundInEnabledList) return true

  val enabledServices =
    Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
  val expectedFlat = ComponentName(targetPackage, targetClass).flattenToString()
  val expectedShort = ComponentName(targetPackage, targetClass).flattenToShortString()
  return enabledServices.split(':').any { entry ->
    entry.equals(expectedFlat, ignoreCase = true) ||
      entry.equals(expectedShort, ignoreCase = true)
  }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPartialPreview() {
  MaterialTheme {
    OnboardingContent(
      state =
        OnboardingUiState(
          accessibilityGranted = true,
          overlayGranted = false,
          notificationsGranted = true,
          batteryOptimizationIgnored = false,
          restrictedGuideExpanded = true,
        ),
      onIntent = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingReadyPreview() {
  MaterialTheme {
    OnboardingContent(
      state =
        OnboardingUiState(
          accessibilityGranted = true,
          overlayGranted = true,
          notificationsGranted = true,
          batteryOptimizationIgnored = true,
        ),
      onIntent = {},
    )
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
