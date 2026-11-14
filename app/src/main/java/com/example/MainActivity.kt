package com.example

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.navigation.HomeTab
import com.example.navigation.Route
import com.example.ui.theme.TapFlowTheme
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.datastore.AppPreferences
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.service.accessibility.TapFlowAccessibilityService
import com.tapflow.core.service.overlay.GesturePickerBridge
import com.tapflow.core.service.overlay.OverlayLaunchIntents
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.feature.editor.PipelineEditorScreen
import com.tapflow.feature.editor.ScriptEditorScreen
import com.tapflow.feature.home.HomeScreen
import com.tapflow.feature.home.HomeTabType
import com.tapflow.feature.home.PendingMediaProjectionTarget
import com.tapflow.feature.logs.PipelineLogScreen
import com.tapflow.feature.logs.ScriptLogScreen
import com.tapflow.feature.onboarding.OnboardingScreen
import com.tapflow.feature.paywall.PaywallScreen
import com.tapflow.feature.studio.InspectStudioScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  @Inject lateinit var entitlementRepository: EntitlementRepository
  @Inject lateinit var preferencesRepository: TapFlowPreferencesRepository
  @Inject lateinit var mediaProjectionBridge: MediaProjectionBridge
  @Inject lateinit var gesturePickerBridge: GesturePickerBridge

  private var pendingProjectionTarget: PendingMediaProjectionTarget? = null
  private var pendingProjectionDeniedCallback: (() -> Unit)? = null

  private val mediaProjectionLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val target = pendingProjectionTarget
      val onDenied = pendingProjectionDeniedCallback
      pendingProjectionTarget = null
      pendingProjectionDeniedCallback = null
      val data = result.data
      if (result.resultCode == Activity.RESULT_OK && data != null && target != null) {
        val projectionIntent =
          mediaProjectionBridge.createStartIntent(
            context = this,
            resultCode = result.resultCode,
            resultData = data,
          )
        ContextCompat.startForegroundService(this, projectionIntent)
        launchOverlayForTarget(target)
        moveTaskToBack(true)
      } else {
        onDenied?.invoke()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val prefsOrNull by
        preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(initialValue = null)
      val prefs = prefsOrNull

      TapFlowTheme(
        darkMode = prefs?.darkMode ?: AppPreferences().darkMode,
        themePalette = prefs?.themePalette ?: AppPreferences().themePalette,
      ) {
        if (prefs == null) {
          Box(modifier = Modifier.fillMaxSize())
        } else {
          val initialDestination: Route =
            remember(prefs.hasCompletedPermissionOnboarding) {
              if (prefs.hasCompletedPermissionOnboarding &&
                areMandatoryPermissionsGranted(this@MainActivity)
              ) {
                Route.Home(HomeTab.SCRIPT)
              } else {
                Route.Onboarding
              }
            }
          TapFlowNavHost(
            startDestination = initialDestination,
            entitlementRepository = entitlementRepository,
            mediaProjectionBridge = mediaProjectionBridge,
            onRequestMediaProjection = ::requestMediaProjectionForTarget,
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    // Preserve active NavHost backstack & ScriptEditor ActionBottomSheet when returning from
    // Overlay Mode 2; GesturePickerBridge delivers the picked result directly to the ViewModel.
    if (gesturePickerBridge.pickedResult.value == null &&
      gesturePickerBridge.pendingRequest.value != null
    ) {
      gesturePickerBridge.cancelPendingPick()
    }
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      entitlementRepository.refreshEntitlement()
    }
  }

  private fun requestMediaProjectionForTarget(
    target: PendingMediaProjectionTarget,
    onDenied: () -> Unit,
  ) {
    val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    if (manager == null) {
      onDenied()
      return
    }
    pendingProjectionTarget = target
    pendingProjectionDeniedCallback = onDenied
    mediaProjectionLauncher.launch(mediaProjectionBridge.createWholeScreenCaptureIntent(manager))
  }

  private fun launchOverlayForTarget(target: PendingMediaProjectionTarget) {
    val overlayIntent =
      when (target) {
        is PendingMediaProjectionTarget.RunScript ->
          OverlayLaunchIntents.createRunnerScriptIntent(this, target.scriptId)
        is PendingMediaProjectionTarget.RunPipeline ->
          OverlayLaunchIntents.createRunnerPipelineIntent(this, target.pipelineId)
        PendingMediaProjectionTarget.LiveRecorder ->
          OverlayLaunchIntents.createLiveRecorderIntent(this)
        PendingMediaProjectionTarget.LiveInspector ->
          OverlayLaunchIntents.createLiveInspectorIntent(this)
      }
    ContextCompat.startForegroundService(this, overlayIntent)
  }
}

@Composable
fun TapFlowNavHost(
  startDestination: Route,
  entitlementRepository: EntitlementRepository,
  mediaProjectionBridge: MediaProjectionBridge,
  onRequestMediaProjection: (PendingMediaProjectionTarget, onDenied: () -> Unit) -> Unit,
) {
  val navController = rememberNavController()
  NavHost(
    navController = navController,
    startDestination = startDestination,
  ) {
    composable<Route.Onboarding> {
      OnboardingScreen(
        onNavigateToHome = {
          navController.navigate(Route.Home(HomeTab.SCRIPT)) {
            popUpTo<Route.Onboarding> { inclusive = true }
            launchSingleTop = true
          }
        },
      )
    }
    composable<Route.Home> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.Home>()
      HomeScreen(
        initialTab = route.initialTab.toHomeTabType(),
        mediaProjectionBridge = mediaProjectionBridge,
        onRequestMediaProjection = onRequestMediaProjection,
        onNavigateToPaywall = { sourceFeature ->
          navController.navigate(Route.Paywall(sourceFeature)) {
            launchSingleTop = true
          }
        },
        onNavigateToScriptEditor = { scriptId ->
          navController.navigate(Route.ScriptEditor(scriptId))
        },
        onNavigateToPipelineEditor = { pipelineId ->
          navController.navigate(Route.PipelineEditor(pipelineId))
        },
        onNavigateToScriptLog = { scriptId ->
          navController.navigate(Route.ScriptLog(scriptId))
        },
        onNavigateToPipelineLog = { pipelineId ->
          navController.navigate(Route.PipelineLog(pipelineId))
        },
        onNavigateToInspectStudio = { itemId ->
          navController.navigate(Route.InspectStudio(itemId))
        },
      )
    }
    composable<Route.Paywall> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.Paywall>()
      PaywallScreen(
        sourceFeature = route.sourceFeature,
        entitlementRepository = entitlementRepository,
        onNavigateBack = { navController.popBackStackSafely() },
      )
    }
    composable<Route.ScriptEditor> {
      ScriptEditorScreen(
        onNavigateBack = { navController.popBackStackSafely() },
        onNavigateToPaywall = { sourceFeature ->
          navController.navigate(Route.Paywall(sourceFeature)) {
            launchSingleTop = true
          }
        },
      )
    }
    composable<Route.PipelineEditor> {
      PipelineEditorScreen(
        onNavigateBack = { navController.popBackStackSafely() },
        onNavigateToPaywall = { sourceFeature ->
          navController.navigate(Route.Paywall(sourceFeature)) {
            launchSingleTop = true
          }
        },
      )
    }
    composable<Route.ScriptLog> {
      ScriptLogScreen(
        onNavigateBack = { navController.popBackStackSafely() },
        onNavigateToPaywall = { sourceFeature ->
          navController.navigate(Route.Paywall(sourceFeature)) {
            launchSingleTop = true
          }
        },
      )
    }
    composable<Route.PipelineLog> {
      PipelineLogScreen(
        onNavigateBack = { navController.popBackStackSafely() },
        onNavigateToPaywall = { sourceFeature ->
          navController.navigate(Route.Paywall(sourceFeature)) {
            launchSingleTop = true
          }
        },
      )
    }
    composable<Route.InspectStudio> {
      InspectStudioScreen(
        onNavigateBack = { navController.popBackStackSafely() },
        onNavigateToPaywall = { sourceFeature ->
          navController.navigate(Route.Paywall(sourceFeature)) {
            launchSingleTop = true
          }
        },
      )
    }
  }
}

private fun NavHostController.popBackStackSafely() {
  if (previousBackStackEntry != null &&
    currentDestination?.hasRoute<Route.Home>() == false
  ) {
    popBackStack()
  }
}

private fun HomeTab.toHomeTabType(): HomeTabType =
  when (this) {
    HomeTab.SCRIPT -> HomeTabType.SCRIPT
    HomeTab.PIPELINE -> HomeTabType.PIPELINE
    HomeTab.INSPECT -> HomeTabType.INSPECT
    HomeTab.CONSOLE -> HomeTabType.CONSOLE
    HomeTab.SEAMLESS -> HomeTabType.SEAMLESS
    HomeTab.SETTINGS -> HomeTabType.SETTINGS
  }

internal fun areMandatoryPermissionsGranted(context: Context): Boolean {
  val overlayGranted = Settings.canDrawOverlays(context)
  if (!overlayGranted) return false

  val notificationsGranted =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  if (!notificationsGranted) return false

  return isAccessibilityServiceEnabled(context)
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
  val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
  val targetClass = TapFlowAccessibilityService::class.java.name
  val targetPackage = context.packageName
  val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
  val foundInList =
    enabled.any { info ->
      val svc = info.resolveInfo?.serviceInfo
      svc != null && svc.packageName == targetPackage && svc.name == targetClass
    }
  if (foundInList) return true

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
