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
      )

    routes.forEach { original ->
      val encoded = json.encodeToString(original)
      val decoded = json.decodeFromString<Route>(encoded)
      assertEquals(original, decoded)
    }
  }
}
