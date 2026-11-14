package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.feature.home.HomeScreen
import com.tapflow.feature.home.HomeTabType
import com.tapflow.feature.onboarding.OnboardingScreen
import com.tapflow.feature.paywall.PaywallScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  @Inject lateinit var entitlementRepository: EntitlementRepository
  @Inject lateinit var preferencesRepository: TapFlowPreferencesRepository
  @Inject lateinit var mediaProjectionBridge: MediaProjectionBridge

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
              if (prefs.hasCompletedPermissionOnboarding) {
                Route.Home(HomeTab.SCRIPT)
              } else {
                Route.Onboarding
              }
            }
          TapFlowNavHost(
            startDestination = initialDestination,
            entitlementRepository = entitlementRepository,
            mediaProjectionBridge = mediaProjectionBridge,
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      entitlementRepository.refreshEntitlement()
    }
  }
}

@Composable
fun TapFlowNavHost(
  startDestination: Route,
  entitlementRepository: EntitlementRepository,
  mediaProjectionBridge: MediaProjectionBridge,
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
        onNavigateBack = { navController.popBackStack() },
      )
    }
    composable<Route.ScriptEditor> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.ScriptEditor>()
      UpcomingPhaseDestinationScreen(
        title = "Script Editor",
        detail = "Editing script: ${route.scriptId ?: "New Script"} (Phase 7)",
        onBack = { navController.popBackStack() },
      )
    }
    composable<Route.PipelineEditor> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.PipelineEditor>()
      UpcomingPhaseDestinationScreen(
        title = "Pipeline Editor",
        detail = "Editing pipeline: ${route.pipelineId ?: "New Pipeline"} (Phase 8)",
        onBack = { navController.popBackStack() },
      )
    }
    composable<Route.ScriptLog> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.ScriptLog>()
      UpcomingPhaseDestinationScreen(
        title = "Script Execution History",
        detail = "Script ID: ${route.scriptId} (Phase 8)",
        onBack = { navController.popBackStack() },
      )
    }
    composable<Route.PipelineLog> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.PipelineLog>()
      UpcomingPhaseDestinationScreen(
        title = "Pipeline Execution History",
        detail = "Pipeline ID: ${route.pipelineId} (Phase 8)",
        onBack = { navController.popBackStack() },
      )
    }
    composable<Route.InspectStudio> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.InspectStudio>()
      UpcomingPhaseDestinationScreen(
        title = "Inspect Studio",
        detail = "Inspect Item ID: ${route.inspectItemId} (Phase 8)",
        onBack = { navController.popBackStack() },
      )
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpcomingPhaseDestinationScreen(
  title: String,
  detail: String,
  onBack: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Navigate back",
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = detail)
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun TapFlowUpcomingPreview() {
  TapFlowTheme(dynamicColor = false) {
    UpcomingPhaseDestinationScreen(
      title = "Script Editor",
      detail = "Preview",
      onBack = {},
    )
  }
}
