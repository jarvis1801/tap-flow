package com.jarvis.tapflow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jarvis.tapflow.navigation.HomeTab
import com.jarvis.tapflow.navigation.Route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TapFlowNavigationAndPermissionsTest {

  private val json = Json

  @Test
  fun `mandatory permissions default false in fresh Robolectric context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertFalse(areMandatoryPermissionsGranted(context))
  }

  @Test
  fun `type safe routes round-trip via kotlinx serialization`() {
    val routes: List<Route> =
      listOf(
        Route.Onboarding,
        Route.Home(HomeTab.INSPECT),
        Route.Paywall(sourceFeature = "Image Match"),
        Route.ScriptEditor(scriptId = "script-123"),
        Route.ScriptFlow(scriptId = "script-123"),
        Route.ScriptFlow(scriptId = null),
        Route.PipelineEditor(pipelineId = null),
        Route.ScriptLog(scriptId = "script-123"),
        Route.PipelineLog(pipelineId = "pipe-456"),
        Route.InspectStudio(inspectItemId = "item-789"),
        Route.ExternalAutomationGuide,
      )

    routes.forEach { original ->
      val encoded = json.encodeToString(original)
      val decoded = json.decodeFromString<Route>(encoded)
      assertEquals(original, decoded)
    }
  }

  @Test
  fun `external trigger receiver is exported for all 8 broadcast actions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val actions = com.tapflow.core.service.external.ExternalBroadcastContract.allIncomingActions(context.packageName)
    assertEquals(8, actions.size)
    actions.forEach { action ->
      val resolved = context.packageManager.queryBroadcastReceivers(android.content.Intent(action), 0)
      assertEquals(
        "Expected exported receiver for $action",
        true,
        resolved.any {
          it.activityInfo.name == "com.tapflow.core.service.external.TapFlowExternalTriggerReceiver" &&
            it.activityInfo.exported
        },
      )
    }
  }

  @Test
  fun `completion broadcast intent omits package restriction and sets dot-free extras`() {
    val pkg = "com.jarvis.tapflow"
    val intent =
      com.tapflow.core.service.external.ExternalBroadcastContract.buildFinishedBroadcastIntent(
        packageName = pkg,
        status = com.tapflow.core.service.external.ExternalBroadcastContract.STATUS_COMPLETED,
        mode = com.tapflow.core.service.external.ExternalBroadcastContract.MODE_SCRIPT,
        targetId = "scr-42",
        targetTitle = "Farm Gold",
        durationMs = 1234L,
        stepsExecuted = 7,
        error = "",
      )

    assertEquals("$pkg.ACTION_EXECUTION_FINISHED", intent.action)
    org.junit.Assert.assertNull(intent.`package`)
    org.junit.Assert.assertTrue(
      (intent.flags and android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES) != 0,
    )
    assertEquals("COMPLETED", intent.getStringExtra("status"))
    assertEquals("SCRIPT", intent.getStringExtra("mode"))
    assertEquals("scr-42", intent.getStringExtra("target_id"))
    assertEquals("Farm Gold", intent.getStringExtra("target_title"))
    assertEquals(1234L, intent.getLongExtra("duration_ms", -1L))
    assertEquals(7, intent.getIntExtra("steps_executed", -1))
    assertEquals("", intent.getStringExtra("error"))
  }

  @Test
  fun `manifest disables Android Auto Backup and Route Home defaults to SCRIPT`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val allowBackupEnabled =
      (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
    assertFalse(allowBackupEnabled)
    assertEquals(HomeTab.SCRIPT, Route.Home().initialTab)
    assertEquals(0, HomeTab.SCRIPT.ordinal)
  }

  @Test
  fun `normal launcher and history intents never route to SEAMLESS`() {
    val normalLauncherIntent =
      android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
      }
    org.junit.Assert.assertNull(extractRequestedHomeTab(normalLauncherIntent))

    val staleExtraLauncherIntent =
      android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        putExtra(
          com.tapflow.core.service.external.ExternalBroadcastContract.EXTRA_OPEN_HOME_TAB,
          com.tapflow.core.service.external.ExternalBroadcastContract.HOME_TAB_SEAMLESS,
        )
      }
    org.junit.Assert.assertNull(extractRequestedHomeTab(staleExtraLauncherIntent))

    val recentsHistoryIntent =
      android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        addFlags(android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        putExtra(
          com.tapflow.core.service.external.ExternalBroadcastContract.EXTRA_OPEN_HOME_TAB,
          com.tapflow.core.service.external.ExternalBroadcastContract.HOME_TAB_SEAMLESS,
        )
        putExtra(
          com.tapflow.core.service.external.ExternalBroadcastContract.EXTRA_FROM_EXTERNAL_NOTIFICATION,
          true,
        )
      }
    org.junit.Assert.assertNull(extractRequestedHomeTab(recentsHistoryIntent))

    val validNotificationIntent =
      android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        putExtra(
          com.tapflow.core.service.external.ExternalBroadcastContract.EXTRA_OPEN_HOME_TAB,
          com.tapflow.core.service.external.ExternalBroadcastContract.HOME_TAB_SEAMLESS,
        )
        putExtra(
          com.tapflow.core.service.external.ExternalBroadcastContract.EXTRA_FROM_EXTERNAL_NOTIFICATION,
          true,
        )
      }
    assertEquals(HomeTab.SEAMLESS, extractRequestedHomeTab(validNotificationIntent))
  }
}
