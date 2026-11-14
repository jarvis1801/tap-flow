package com.tapflow.feature.home

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.service.overlay.OverlayLaunchIntents
import com.tapflow.core.service.projection.MediaProjectionBridge

@Composable
fun HomeScreen(
  initialTab: HomeTabType,
  mediaProjectionBridge: MediaProjectionBridge,
  onNavigateToPaywall: (String?) -> Unit,
  onNavigateToScriptEditor: (String?) -> Unit,
  onNavigateToPipelineEditor: (String?) -> Unit,
  onNavigateToScriptLog: (String) -> Unit,
  onNavigateToPipelineLog: (String) -> Unit,
  onNavigateToInspectStudio: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var pendingConsentTarget by remember { mutableStateOf<PendingMediaProjectionTarget?>(null) }

  LaunchedEffect(initialTab) {
    viewModel.onIntent(HomeUiIntent.SelectTab(initialTab))
  }

  LaunchedEffect(context) {
    runCatching {
      val pInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
          )
        } else {
          @Suppress("DEPRECATION")
          context.packageManager.getPackageInfo(context.packageName, 0)
        }
      val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else 1L
      viewModel.onIntent(
        HomeUiIntent.SetAppBuildMetadata(
          versionName = pInfo.versionName ?: "1.0.0",
          versionCode = versionCode,
        ),
      )
    }
  }

  val mediaProjectionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
      val target = pendingConsentTarget
      pendingConsentTarget = null
      val data = result.data
      if (result.resultCode == Activity.RESULT_OK && data != null && target != null) {
        val projIntent = mediaProjectionBridge.createStartIntent(context, result.resultCode, data)
        ContextCompat.startForegroundService(context, projIntent)
        launchOverlayForTarget(context, target)
        (context as? Activity)?.moveTaskToBack(true)
      } else {
        viewModel.onIntent(HomeUiIntent.MediaProjectionDenied)
      }
    }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
      if (uri != null) {
        viewModel.onIntent(HomeUiIntent.HandleImportedScreenshotUri(uri))
      }
    }

  val backupExportLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
      if (uri != null) {
        viewModel.onIntent(HomeUiIntent.HandleBackupExportUri(uri))
      }
    }

  val backupImportLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      if (uri != null) {
        viewModel.onIntent(HomeUiIntent.HandleBackupImportUri(uri))
      }
    }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        is HomeUiEffect.NavigateToPaywall -> onNavigateToPaywall(effect.sourceFeature)
        is HomeUiEffect.NavigateToScriptEditor -> onNavigateToScriptEditor(effect.scriptId)
        is HomeUiEffect.NavigateToPipelineEditor -> onNavigateToPipelineEditor(effect.pipelineId)
        is HomeUiEffect.NavigateToScriptLog -> onNavigateToScriptLog(effect.scriptId)
        is HomeUiEffect.NavigateToPipelineLog -> onNavigateToPipelineLog(effect.pipelineId)
        is HomeUiEffect.NavigateToInspectStudio -> onNavigateToInspectStudio(effect.inspectItemId)
        is HomeUiEffect.RequestMediaProjectionPermission -> {
          pendingConsentTarget = effect.target
          val mgr =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
          mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
        }
        is HomeUiEffect.LaunchOverlayAndMinimize -> {
          launchOverlayForTarget(context, effect.target)
          (context as? Activity)?.moveTaskToBack(true)
        }
        HomeUiEffect.LaunchPhotoPicker -> {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
          )
        }
        is HomeUiEffect.LaunchBackupExportPicker -> {
          backupExportLauncher.launch(effect.suggestedFileName)
        }
        HomeUiEffect.LaunchBackupImportPicker -> {
          backupImportLauncher.launch(arrayOf("*/*"))
        }
        is HomeUiEffect.CopyToClipboard -> {
          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText(effect.label, effect.text))
        }
        is HomeUiEffect.ApplyAppLocale -> {
          val locales = LocaleListCompat.forLanguageTags(effect.languageTag)
          AppCompatDelegate.setApplicationLocales(locales)
        }
        HomeUiEffect.OpenShizukuDownloadPage -> {
          val intent =
            Intent(
              Intent.ACTION_VIEW,
              Uri.parse("https://shizuku.rikka.app/download/"),
            )
          runCatching { context.startActivity(intent) }
        }
        is HomeUiEffect.LaunchFeedbackEmail -> {
          val intent =
            Intent(Intent.ACTION_SENDTO).apply {
              data = Uri.parse("mailto:support@tapflow.app")
              putExtra(Intent.EXTRA_SUBJECT, effect.subject)
              putExtra(Intent.EXTRA_TEXT, effect.body)
            }
          runCatching { context.startActivity(intent) }
            .onFailure {
              val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cm.setPrimaryClip(ClipData.newPlainText("TapFlow Diagnostics", effect.body))
            }
        }
        is HomeUiEffect.ShowSnackbar -> {
          snackbarHostState.showSnackbar(effect.message)
        }
      }
    }
  }

  HomeContent(
    state = uiState,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
  state: HomeUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (HomeUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  HomeDialogs(state = state, onIntent = onIntent)

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 640.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            if (state.isSearchExpanded && state.supportsSearch) {
              OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onIntent(HomeUiIntent.UpdateSearchQuery(it)) },
                placeholder = {
                  Text(
                    when (state.activeTab) {
                      HomeTabType.SCRIPT -> "Search title or tag..."
                      HomeTabType.PIPELINE -> "Search pipeline or tag..."
                      HomeTabType.INSPECT -> "Search group, tag, or app..."
                      else -> "Search..."
                    },
                  )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("home_search_field"),
              )
            } else {
              Text(
                text = state.activeTab.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
          actions = {
            if (state.supportsSearch) {
              IconButton(
                onClick = { onIntent(HomeUiIntent.SetSearchExpanded(!state.isSearchExpanded)) },
                modifier = Modifier.size(48.dp).testTag("home_search_toggle_button"),
              ) {
                Icon(
                  imageVector =
                    if (state.isSearchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                  contentDescription =
                    if (state.isSearchExpanded) "Close search" else "Search",
                )
              }
            }

            EntitlementPill(
              entitlementState = state.entitlementState,
              onClick = { onIntent(HomeUiIntent.OpenPaywallClicked) },
              modifier = Modifier.padding(end = 12.dp),
            )
          },
        )
      },
      bottomBar = {
        if (!useNavigationRail) {
          NavigationBar {
            HomeTabType.entries.forEach { tab ->
              NavigationBarItem(
                selected = state.activeTab == tab,
                onClick = { onIntent(HomeUiIntent.SelectTab(tab)) },
                icon = {
                  Icon(
                    imageVector = tabIcon(tab),
                    contentDescription = tab.title,
                  )
                },
                label = {
                  Text(
                    text = tab.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag("bottom_nav_${tab.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            HomeTabType.entries.forEach { tab ->
              NavigationRailItem(
                selected = state.activeTab == tab,
                onClick = { onIntent(HomeUiIntent.SelectTab(tab)) },
                icon = {
                  Icon(
                    imageVector = tabIcon(tab),
                    contentDescription = tab.title,
                  )
                },
                label = { Text(tab.title, maxLines = 1) },
                modifier = Modifier.testTag("rail_nav_${tab.name.lowercase()}"),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          when (state.activeTab) {
            HomeTabType.SCRIPT -> ScriptsTabContent(state = state, onIntent = onIntent)
            HomeTabType.PIPELINE -> PipelinesTabContent(state = state, onIntent = onIntent)
            HomeTabType.INSPECT -> InspectTabContent(state = state, onIntent = onIntent)
            HomeTabType.CONSOLE -> ConsoleTabContent(state = state, onIntent = onIntent)
            HomeTabType.SEAMLESS -> SeamlessTabContent(state = state, onIntent = onIntent)
            HomeTabType.SETTINGS -> SettingsTabContent(state = state, onIntent = onIntent)
          }
        }
      }
    }
  }
}

@Composable
private fun EntitlementPill(
  entitlementState: EntitlementState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (label, containerColor, contentColor) =
    when (entitlementState) {
      is EntitlementState.ProLifetime ->
        Triple(
          "PRO",
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
        )
      is EntitlementState.ProTrial ->
        Triple(
          "Trial ${entitlementState.remainingDays}d",
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
        )
      EntitlementState.PendingPurchase ->
        Triple(
          "Pending",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
      is EntitlementState.Free ->
        Triple(
          "Upgrade",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }

  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.large,
    color = containerColor,
    modifier = modifier.testTag("entitlement_pill_button"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector =
          if (entitlementState is EntitlementState.ProLifetime) {
            Icons.Filled.Verified
          } else {
            Icons.Filled.WorkspacePremium
          },
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
    }
  }
}

private fun tabIcon(tab: HomeTabType): ImageVector =
  when (tab) {
    HomeTabType.SCRIPT -> Icons.Filled.TouchApp
    HomeTabType.PIPELINE -> Icons.Filled.AccountTree
    HomeTabType.INSPECT -> Icons.Filled.CenterFocusStrong
    HomeTabType.CONSOLE -> Icons.Filled.Terminal
    HomeTabType.SEAMLESS -> Icons.Filled.Bolt
    HomeTabType.SETTINGS -> Icons.Filled.Settings
  }

private fun launchOverlayForTarget(
  context: Context,
  target: PendingMediaProjectionTarget,
) {
  val overlayIntent =
    when (target) {
      is PendingMediaProjectionTarget.RunScript ->
        OverlayLaunchIntents.createRunnerScriptIntent(context, target.scriptId)
      is PendingMediaProjectionTarget.RunPipeline ->
        OverlayLaunchIntents.createRunnerPipelineIntent(context, target.pipelineId)
      PendingMediaProjectionTarget.LiveRecorder ->
        OverlayLaunchIntents.createLiveRecorderIntent(context)
      PendingMediaProjectionTarget.LiveInspector ->
        OverlayLaunchIntents.createLiveInspectorIntent(context)
    }
  ContextCompat.startForegroundService(context, overlayIntent)
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
  MaterialTheme {
    HomeContent(
      state = HomeUiState(),
      snackbarHostState = SnackbarHostState(),
      onIntent = {},
    )
  }
}
