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
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.service.bridge.LocalBridgeControlIntents
import com.tapflow.core.service.overlay.OverlayLaunchIntents
import com.tapflow.core.service.permissions.openAccessibilitySettings
import com.tapflow.core.service.permissions.openOverlayPermissionSettings
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.shizuku.ShizukuManager
import kotlinx.coroutines.launch

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
  onNavigateToExternalAutomationGuide: () -> Unit = {},
  deepLinkTab: HomeTabType? = null,
  deepLinkTabSeq: Int = 0,
  onRequestMediaProjection: ((PendingMediaProjectionTarget, onDenied: () -> Unit) -> Unit)? = null,
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var pendingConsentTarget by remember { mutableStateOf<PendingMediaProjectionTarget?>(null) }

  LaunchedEffect(initialTab) {
    viewModel.onIntent(HomeUiIntent.InitializeTabIfUnset(initialTab))
  }

  LifecycleResumeEffect(Unit) {
    viewModel.onIntent(
      HomeUiIntent.SyncOverlayPermission(Settings.canDrawOverlays(context)),
    )
    onPauseOrDispose {}
  }

  LaunchedEffect(deepLinkTab, deepLinkTabSeq) {
    if (deepLinkTab != null && deepLinkTabSeq > 0) {
      viewModel.onIntent(HomeUiIntent.SelectTab(deepLinkTab))
    }
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
        viewModel.suppressOverlayAutoCloseBriefly()
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
        HomeUiEffect.NavigateToExternalAutomationGuide -> onNavigateToExternalAutomationGuide()
        is HomeUiEffect.RequestMediaProjectionPermission -> {
          val activityHandler = onRequestMediaProjection
          if (activityHandler != null) {
            activityHandler(effect.target) {
              viewModel.onIntent(HomeUiIntent.MediaProjectionDenied)
            }
          } else {
            pendingConsentTarget = effect.target
            val mgr =
              context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(
              mediaProjectionBridge.createWholeScreenCaptureIntent(mgr),
            )
          }
        }
        is HomeUiEffect.LaunchOverlayAndMinimize -> {
          viewModel.suppressOverlayAutoCloseBriefly()
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
          val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
          if (!currentTags.equals(effect.languageTag, ignoreCase = true) &&
            !AppLocaleHelper.isLocaleAlreadyActive(currentTags, effect.languageTag)
          ) {
            val locales = LocaleListCompat.forLanguageTags(effect.languageTag)
            AppCompatDelegate.setApplicationLocales(locales)
          }
        }
        HomeUiEffect.OpenShizukuDownloadPage -> {
          val pkg = ShizukuManager.SHIZUKU_PACKAGE_NAME
          val candidates =
            listOf(
              Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")),
              Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")),
              Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest")),
            )
          candidates.firstOrNull { candidate ->
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(candidate) }.isSuccess
          }
        }
        HomeUiEffect.OpenShizukuApp -> {
          val pkg = ShizukuManager.SHIZUKU_PACKAGE_NAME
          val launchIntent =
            context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          val started =
            launchIntent != null && runCatching { context.startActivity(launchIntent) }.isSuccess
          if (!started) {
            val appInfoIntent =
              Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", pkg, null),
              ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(appInfoIntent) }
          }
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
        is HomeUiEffect.StartLocalBridgeService -> {
          ContextCompat.startForegroundService(
            context,
            LocalBridgeControlIntents.createStartIntent(context, effect.port),
          )
        }
        HomeUiEffect.StopLocalBridgeService -> {
          runCatching {
            context.startService(LocalBridgeControlIntents.createStopIntent(context))
          }
        }
        HomeUiEffect.LaunchAccessibilitySettings -> {
          openAccessibilitySettings(context)
        }
        HomeUiEffect.LaunchOverlaySettings -> {
          openOverlayPermissionSettings(context)
        }
        is HomeUiEffect.ShowSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(effect.message)
          }
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
        val searchActive = state.isSearchExpanded && state.supportsSearch
        TopAppBar(
          title = {
            if (searchActive) {
              HomeTopBarSearchField(
                query = state.searchQuery,
                placeholder =
                  when (state.activeTab) {
                    HomeTabType.SCRIPT -> "Search title or tag..."
                    HomeTabType.PIPELINE -> "Search pipeline or tag..."
                    HomeTabType.INSPECT -> "Search group, tag, or app..."
                    else -> "Search..."
                  },
                onQueryChange = { onIntent(HomeUiIntent.UpdateSearchQuery(it)) },
                onCollapseAndClear = { onIntent(HomeUiIntent.SetSearchExpanded(false)) },
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
            if (!searchActive) {
              if (state.supportsSearch) {
                IconButton(
                  onClick = { onIntent(HomeUiIntent.SetSearchExpanded(true)) },
                  modifier = Modifier.size(48.dp).testTag("home_search_toggle_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                  )
                }
              }

              if (state.activeTab == HomeTabType.INSPECT) {
                var showInspectMenu by remember { mutableStateOf(false) }
                val hasInspectImages =
                  state.inspectStorageStats.imageCount > 0 ||
                    state.inspectStorageStats.totalBytes > 0L
                Box {
                  IconButton(
                    onClick = { showInspectMenu = true },
                    modifier = Modifier.size(48.dp).testTag("inspect_overflow_menu_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.MoreVert,
                      contentDescription = "Inspect options",
                    )
                  }
                  DropdownMenu(
                    expanded = showInspectMenu,
                    onDismissRequest = { showInspectMenu = false },
                  ) {
                    DropdownMenuItem(
                      text = { Text("Clear All Inspect Images") },
                      leadingIcon = {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.error,
                        )
                      },
                      enabled = hasInspectImages && !state.isClearingInspectData,
                      onClick = {
                        showInspectMenu = false
                        onIntent(HomeUiIntent.RequestClearAllInspectData)
                      },
                      modifier = Modifier.testTag("inspect_menu_clear_all"),
                    )
                  }
                }
              }

              EntitlementPill(
                entitlementState = state.entitlementState,
                onClick = { onIntent(HomeUiIntent.OpenPaywallClicked) },
                modifier = Modifier.padding(end = 12.dp),
              )
            }
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
private fun HomeTopBarSearchField(
  query: String,
  placeholder: String,
  onQueryChange: (String) -> Unit,
  onCollapseAndClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  val collapseAndClear: () -> Unit = {
    keyboardController?.hide()
    focusManager.clearFocus()
    onCollapseAndClear()
  }

  BackHandler(enabled = true) {
    collapseAndClear()
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboardController?.show()
  }

  DisposableEffect(Unit) {
    onDispose {
      keyboardController?.hide()
    }
  }

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier = modifier.fillMaxWidth().padding(end = 12.dp).heightIn(min = 48.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = collapseAndClear,
        modifier = Modifier.size(48.dp).testTag("home_search_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Collapse search",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart,
      ) {
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions =
            KeyboardActions(
              onSearch = {
                focusManager.clearFocus()
                keyboardController?.hide()
              },
            ),
          textStyle =
            MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurface,
            ),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          modifier =
            Modifier
              .fillMaxWidth()
              .focusRequester(focusRequester)
              .testTag("home_search_field"),
          decorationBox = { innerTextField ->
            if (query.isEmpty()) {
              Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            innerTextField()
          },
        )
      }

      IconButton(
        onClick = {
          if (query.isNotEmpty()) {
            onQueryChange("")
            focusRequester.requestFocus()
            keyboardController?.show()
          } else {
            collapseAndClear()
          }
        },
        modifier = Modifier.size(48.dp).testTag("home_search_clear_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = if (query.isNotEmpty()) "Clear search" else "Close search",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        OverlayLaunchIntents.createRunnerScriptIntent(
          context = context,
          scriptId = target.scriptId,
          autoStart = target.autoStart,
        )
      is PendingMediaProjectionTarget.RunPipeline ->
        OverlayLaunchIntents.createRunnerPipelineIntent(
          context = context,
          pipelineId = target.pipelineId,
          autoStart = target.autoStart,
        )
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
