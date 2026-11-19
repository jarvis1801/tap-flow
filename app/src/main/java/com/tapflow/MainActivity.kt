package com.jarvis.tapflow

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.jarvis.tapflow.navigation.HomeTab
import com.jarvis.tapflow.navigation.Route
import com.jarvis.tapflow.ui.theme.TapFlowTheme
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.datastore.AppPreferences
import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.datastore.ThemePaletteOption
import com.tapflow.core.model.common.FeatureFlags
import com.tapflow.core.service.accessibility.TapFlowAccessibilityService
import com.tapflow.core.service.external.ExternalBroadcastContract
import com.tapflow.core.service.overlay.GesturePickerBridge
import com.tapflow.core.service.overlay.OverlayLaunchIntents
import com.tapflow.core.service.overlay.OverlayStateBridge
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.feature.editor.PipelineEditorScreen
import com.tapflow.feature.editor.ScriptEditorScreen
import com.tapflow.feature.editor.ScriptFlowScreen
import com.tapflow.feature.home.ExternalAutomationGuideScreen
import com.tapflow.feature.home.HomeScreen
import com.tapflow.feature.home.HomeTabType
import com.tapflow.feature.home.PendingMediaProjectionTarget
import com.tapflow.feature.logs.PipelineLogScreen
import com.tapflow.feature.logs.ScriptLogScreen
import com.tapflow.feature.onboarding.OnboardingScreen
import com.tapflow.feature.paywall.PaywallScreen
import com.tapflow.feature.studio.InspectStudioScreen
import com.tapflow.core.service.theme.resolveIsDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  @Inject lateinit var entitlementRepository: EntitlementRepository
  @Inject lateinit var preferencesRepository: TapFlowPreferencesRepository
  @Inject lateinit var mediaProjectionBridge: MediaProjectionBridge
  @Inject lateinit var gesturePickerBridge: GesturePickerBridge
  @Inject lateinit var overlayStateBridge: OverlayStateBridge

  private companion object {
    const val PREF_WINDOW_BG_CACHE = "tapflow_window_bg_cache"
    const val KEY_LAST_WINDOW_BG_ARGB = "last_window_bg_argb"
    const val WINDOW_BG_AMOLED = AndroidColor.BLACK
    const val WINDOW_BG_DARK = 0xFF0A111A.toInt()
    const val WINDOW_BG_LIGHT = 0xFFF4F8FB.toInt()
  }

  private var pendingProjectionTarget: PendingMediaProjectionTarget? = null
  private var pendingProjectionDeniedCallback: (() -> Unit)? = null
  private var isAwaitingProjectionResult: Boolean = false
  private var deepLinkTabSeq = 0
  private val deepLinkTabRequest = MutableStateFlow<Pair<HomeTab, Int>?>(null)

  private val mediaProjectionLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val target = pendingProjectionTarget
      val onDenied = pendingProjectionDeniedCallback
      pendingProjectionTarget = null
      pendingProjectionDeniedCallback = null
      isAwaitingProjectionResult = false
      val data = result.data
      if (result.resultCode == Activity.RESULT_OK && data != null && target != null) {
        val projectionIntent =
          mediaProjectionBridge.createStartIntent(
            context = this,
            resultCode = result.resultCode,
            resultData = data,
          )
        ContextCompat.startForegroundService(this, projectionIntent)
        overlayStateBridge.suppressAutoCloseBriefly()
        launchOverlayForTarget(target)
        moveTaskToBack(true)
      } else {
        onDenied?.invoke()
      }
    }

  override fun setTheme(resId: Int) {
    super.setTheme(resId)
    applyWindowBackgroundColor(resolveInitialWindowBackgroundColor())
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val initialWindowBgArgb = resolveInitialWindowBackgroundColor()
    applyWindowBackgroundColor(initialWindowBgArgb)
    enableEdgeToEdge()
    setContent {
      val prefsOrNull by
        preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(initialValue = null)
      val entitlementState by
        entitlementRepository.entitlementState.collectAsStateWithLifecycle()
      val isPro = entitlementState.hasTrialOrLifetimeAccess
      val prefs = prefsOrNull
      val darkMode = prefs?.darkMode ?: AppPreferences().darkMode
      val themePalette = prefs?.themePalette ?: AppPreferences().themePalette
      val isDark = resolveIsDarkTheme(darkMode = darkMode, themePalette = themePalette, isPro = isPro)
      val isAmoledActive = themePalette == ThemePaletteOption.AMOLED_TRUE_BLACK && isPro

      val targetNightMode =
        if (prefs != null) {
          when {
            isAmoledActive -> AppCompatDelegate.MODE_NIGHT_YES
            darkMode == DarkModeOption.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            darkMode == DarkModeOption.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
          }
        } else {
          null
        }

      val resolvedWindowBgArgb =
        if (prefs != null) {
          when {
            isAmoledActive -> WINDOW_BG_AMOLED
            isDark -> WINDOW_BG_DARK
            else -> WINDOW_BG_LIGHT
          }
        } else {
          initialWindowBgArgb
        }

      LaunchedEffect(targetNightMode) {
        if (targetNightMode != null && AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
          AppCompatDelegate.setDefaultNightMode(targetNightMode)
        }
      }

      LaunchedEffect(prefs != null, resolvedWindowBgArgb) {
        applyWindowBackgroundColor(resolvedWindowBgArgb)
        if (prefs != null) {
          cacheWindowBackgroundColor(resolvedWindowBgArgb)
        }
      }

      DisposableEffect(isDark) {
        enableEdgeToEdge(
          statusBarStyle =
            if (isDark) {
              SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
              SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
          navigationBarStyle =
            if (isDark) {
              SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
              SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
        )
        onDispose {}
      }

      TapFlowTheme(
        darkMode = darkMode,
        themePalette = themePalette,
        isPro = isPro,
        darkTheme = isDark,
      ) {
        if (prefs == null) {
          Box(modifier = Modifier.fillMaxSize().background(Color(resolvedWindowBgArgb)))
        } else {
          val deepLinkState by deepLinkTabRequest.collectAsStateWithLifecycle()
          val initialDestination: Route =
            remember {
              if (prefs.hasCompletedPermissionOnboarding &&
                areMandatoryPermissionsGranted(this@MainActivity)
              ) {
                Route.Home(consumeRequestedHomeTab(intent) ?: HomeTab.SCRIPT)
              } else {
                // Ensure stale extras cannot linger across Onboarding -> Home transitions.
                consumeRequestedHomeTab(intent)
                Route.Onboarding
              }
            }
          TapFlowNavHost(
            startDestination = initialDestination,
            entitlementRepository = entitlementRepository,
            mediaProjectionBridge = mediaProjectionBridge,
            deepLinkTabRequest = deepLinkState,
            onConsumeDeepLinkTab = { deepLinkTabRequest.value = null },
            onRequestMediaProjection = ::requestMediaProjectionForTarget,
          )
        }
      }
    }
  }

  private fun resolveInitialWindowBackgroundColor(): Int {
    val cached =
      runCatching {
        getSharedPreferences(PREF_WINDOW_BG_CACHE, Context.MODE_PRIVATE)
          .takeIf { it.contains(KEY_LAST_WINDOW_BG_ARGB) }
          ?.getInt(KEY_LAST_WINDOW_BG_ARGB, WINDOW_BG_DARK)
      }.getOrNull()
    if (cached != null) {
      return cached
    }
    val nightMode = AppCompatDelegate.getDefaultNightMode()
    val isNight =
      when (nightMode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else ->
          runCatching {
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
              Configuration.UI_MODE_NIGHT_YES
          }.getOrDefault(false)
      }
    return if (isNight) WINDOW_BG_DARK else WINDOW_BG_LIGHT
  }

  private fun applyWindowBackgroundColor(argb: Int) {
    window?.setBackgroundDrawable(ColorDrawable(argb))
  }

  private fun cacheWindowBackgroundColor(argb: Int) {
    runCatching {
      getSharedPreferences(PREF_WINDOW_BG_CACHE, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_LAST_WINDOW_BG_ARGB, argb)
        .apply()
    }
  }

  override fun onStart() {
    super.onStart()
    autoCloseActiveOverlayIfReturningToApp()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumeRequestedHomeTab(intent)?.let { requestedTab ->
      deepLinkTabSeq += 1
      deepLinkTabRequest.value = requestedTab to deepLinkTabSeq
    }
    // Preserve active NavHost backstack & ScriptEditor ActionBottomSheet when returning from
    // Overlay Mode 2; GesturePickerBridge delivers the picked result directly to the ViewModel.
    if (gesturePickerBridge.pickedResult.value == null &&
      gesturePickerBridge.pendingRequest.value != null
    ) {
      gesturePickerBridge.cancelPendingPick()
    }
    autoCloseActiveOverlayIfReturningToApp()
  }

  private fun consumeRequestedHomeTab(intent: Intent?): HomeTab? {
    val resolved = extractRequestedHomeTab(intent)
    if (intent != null) {
      intent.removeExtra(ExternalBroadcastContract.EXTRA_OPEN_HOME_TAB)
      intent.removeExtra(ExternalBroadcastContract.EXTRA_FROM_EXTERNAL_NOTIFICATION)
    }
    return resolved
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      entitlementRepository.refreshEntitlement()
    }
  }

  override fun onStop() {
    overlayStateBridge.clearAutoCloseSuppression()
    super.onStop()
  }

  private fun autoCloseActiveOverlayIfReturningToApp() {
    if (isAwaitingProjectionResult || pendingProjectionTarget != null) return
    if (overlayStateBridge.shouldAutoCloseOnForeground()) {
      runCatching { startService(OverlayLaunchIntents.createCloseIntent(this)) }
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
    isAwaitingProjectionResult = true
    mediaProjectionLauncher.launch(mediaProjectionBridge.createWholeScreenCaptureIntent(manager))
  }

  private fun launchOverlayForTarget(target: PendingMediaProjectionTarget) {
    val overlayIntent =
      when (target) {
        is PendingMediaProjectionTarget.RunScript ->
          OverlayLaunchIntents.createRunnerScriptIntent(
            context = this,
            scriptId = target.scriptId,
            autoStart = target.autoStart,
          )
        is PendingMediaProjectionTarget.RunPipeline ->
          OverlayLaunchIntents.createRunnerPipelineIntent(
            context = this,
            pipelineId = target.pipelineId,
            autoStart = target.autoStart,
          )
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
  deepLinkTabRequest: Pair<HomeTab, Int>? = null,
  onConsumeDeepLinkTab: () -> Unit = {},
) {
  val navController = rememberNavController()
  LaunchedEffect(deepLinkTabRequest) {
    val request = deepLinkTabRequest ?: return@LaunchedEffect
    val safeTab =
      if (request.first == HomeTab.CONSOLE && !FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
        HomeTab.SCRIPT
      } else {
        request.first
      }
    if (navController.currentDestination?.hasRoute<Route.Home>() == false &&
      navController.currentDestination?.hasRoute<Route.Onboarding>() == false
    ) {
      navController.navigate(Route.Home(safeTab)) {
        popUpTo<Route.Home> { inclusive = true }
        launchSingleTop = true
      }
      onConsumeDeepLinkTab()
    }
  }
  NavHost(
    navController = navController,
    startDestination = startDestination,
  ) {
    composable<Route.Onboarding> {
      OnboardingScreen(
        onNavigateToHome = {
          if (navController.currentDestination?.hasRoute<Route.Onboarding>() == true) {
            onConsumeDeepLinkTab()
            navController.navigate(Route.Home(HomeTab.SCRIPT)) {
              popUpTo<Route.Onboarding> { inclusive = true }
              launchSingleTop = true
            }
          }
        },
      )
    }
    composable<Route.Home> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.Home>()
      HomeScreen(
        initialTab = route.initialTab.toHomeTabType(),
        mediaProjectionBridge = mediaProjectionBridge,
        deepLinkTab = deepLinkTabRequest?.first?.toHomeTabType(),
        deepLinkTabSeq = deepLinkTabRequest?.second ?: 0,
        onConsumeDeepLinkTab = onConsumeDeepLinkTab,
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
        onNavigateToExternalAutomationGuide = {
          navController.navigate(Route.ExternalAutomationGuide) {
            launchSingleTop = true
          }
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
        onNavigateToFlowDiagram = { scriptId ->
          navController.navigate(Route.ScriptFlow(scriptId)) {
            launchSingleTop = true
          }
        },
      )
    }
    composable<Route.ScriptFlow> {
      ScriptFlowScreen(
        onNavigateBack = { navController.popBackStackSafely() },
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
    composable<Route.ExternalAutomationGuide> {
      ExternalAutomationGuideScreen(
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
    HomeTab.CONSOLE ->
      if (FeatureFlags.ENABLE_DESKTOP_CONSOLE) HomeTabType.CONSOLE else HomeTabType.SCRIPT
    HomeTab.SEAMLESS -> HomeTabType.SEAMLESS
    HomeTab.SETTINGS -> HomeTabType.SETTINGS
  }

internal fun extractRequestedHomeTab(intent: Intent?): HomeTab? {
  if (intent == null) return null
  if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
  val tabExtra = intent.getStringExtra(ExternalBroadcastContract.EXTRA_OPEN_HOME_TAB)
  if (tabExtra != ExternalBroadcastContract.HOME_TAB_SEAMLESS) return null
  val isExplicitExternalNotification =
    intent.getBooleanExtra(ExternalBroadcastContract.EXTRA_FROM_EXTERNAL_NOTIFICATION, false)
  return if (isExplicitExternalNotification) HomeTab.SEAMLESS else null
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
