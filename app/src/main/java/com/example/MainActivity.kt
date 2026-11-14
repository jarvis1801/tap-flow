package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.navigation.HomeTab
import com.example.navigation.Route
import com.example.ui.boot.Phase1BootScreen
import com.example.ui.theme.TapFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      TapFlowTheme {
        TapFlowBootNavHost()
      }
    }
  }
}

@Composable
fun TapFlowBootNavHost() {
  val navController = rememberNavController()
  NavHost(
    navController = navController,
    startDestination = Route.Onboarding,
  ) {
    composable<Route.Onboarding> {
      Phase1BootScreen(
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
      PlaceholderDestinationScreen(label = "Home (${route.initialTab.name})")
    }
    composable<Route.Paywall> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.Paywall>()
      PlaceholderDestinationScreen(label = "Paywall (${route.sourceFeature ?: "Upgrade"})")
    }
    composable<Route.ScriptEditor> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.ScriptEditor>()
      PlaceholderDestinationScreen(label = "Script Editor (${route.scriptId ?: "New"})")
    }
    composable<Route.PipelineEditor> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.PipelineEditor>()
      PlaceholderDestinationScreen(label = "Pipeline Editor (${route.pipelineId ?: "New"})")
    }
    composable<Route.ScriptLog> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.ScriptLog>()
      PlaceholderDestinationScreen(label = "Script Log (${route.scriptId})")
    }
    composable<Route.PipelineLog> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.PipelineLog>()
      PlaceholderDestinationScreen(label = "Pipeline Log (${route.pipelineId})")
    }
    composable<Route.InspectStudio> { backStackEntry ->
      val route = backStackEntry.toRoute<Route.InspectStudio>()
      PlaceholderDestinationScreen(label = "Inspect Studio (${route.inspectItemId})")
    }
  }
}

@Composable
private fun PlaceholderDestinationScreen(label: String) {
  Scaffold { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = label)
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun TapFlowBootPreview() {
  TapFlowTheme(darkTheme = true, dynamicColor = false) {
    Phase1BootScreen(onNavigateToHome = {})
  }
}
