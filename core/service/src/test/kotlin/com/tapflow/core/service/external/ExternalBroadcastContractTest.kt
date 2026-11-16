package com.tapflow.core.service.external

import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalBroadcastContractTest {

  @Test
  fun actionBuilders_useDynamicPackagePrefix() {
    val pkg = "com.jarvis.tapflow"
    assertEquals("$pkg.ACTION_PLAY_SCRIPT", ExternalBroadcastContract.actionPlayScript(pkg))
    assertEquals("$pkg.ACTION_PLAY_PIPELINE", ExternalBroadcastContract.actionPlayPipeline(pkg))
    assertEquals("$pkg.ACTION_RESUME", ExternalBroadcastContract.actionResume(pkg))
    assertEquals("$pkg.ACTION_PAUSE", ExternalBroadcastContract.actionPause(pkg))
    assertEquals("$pkg.ACTION_STOP", ExternalBroadcastContract.actionStop(pkg))
    assertEquals("$pkg.ACTION_CLOSE", ExternalBroadcastContract.actionClose(pkg))
    assertEquals("$pkg.ACTION_NEXT_ACTION", ExternalBroadcastContract.actionNextAction(pkg))
    assertEquals("$pkg.ACTION_PREV_ACTION", ExternalBroadcastContract.actionPrevAction(pkg))
    assertEquals(
      "$pkg.ACTION_EXECUTION_FINISHED",
      ExternalBroadcastContract.actionExecutionFinished(pkg),
    )
    assertEquals(8, ExternalBroadcastContract.allIncomingActions(pkg).size)
  }

  @Test
  fun outgoingExtrasKeys_areDotFreeForTasker() {
    val keys =
      listOf(
        ExternalBroadcastContract.EXTRA_STATUS,
        ExternalBroadcastContract.EXTRA_MODE,
        ExternalBroadcastContract.EXTRA_TARGET_ID,
        ExternalBroadcastContract.EXTRA_TARGET_TITLE,
        ExternalBroadcastContract.EXTRA_DURATION_MS,
        ExternalBroadcastContract.EXTRA_STEPS_EXECUTED,
        ExternalBroadcastContract.EXTRA_ERROR,
        ExternalBroadcastContract.EXTRA_SCRIPT_ID,
        ExternalBroadcastContract.EXTRA_SCRIPT_NAME,
        ExternalBroadcastContract.EXTRA_PIPELINE_ID,
        ExternalBroadcastContract.EXTRA_PIPELINE_NAME,
      )
    assertTrue(keys.all { !it.contains('.') })
  }

  @Test
  fun resolveModeForAction_mapsScriptPipelineAndControls() {
    val pkg = "com.jarvis.tapflow"
    assertEquals(
      ExternalBroadcastContract.MODE_SCRIPT,
      TapFlowExternalTriggerReceiver.resolveModeForAction(
        pkg,
        ExternalBroadcastContract.actionPlayScript(pkg),
      ),
    )
    assertEquals(
      ExternalBroadcastContract.MODE_PIPELINE,
      TapFlowExternalTriggerReceiver.resolveModeForAction(
        pkg,
        ExternalBroadcastContract.actionPlayPipeline(pkg),
      ),
    )
    assertEquals(
      ExternalBroadcastContract.MODE_CONTROL,
      TapFlowExternalTriggerReceiver.resolveModeForAction(
        pkg,
        ExternalBroadcastContract.actionStop(pkg),
      ),
    )
  }

  @Test
  fun findScriptAndPipelineByName_matchesCaseInsensitively() {
    val s1 = Script(id = "s1", title = "Daily Check-In")
    val s2 = Script(id = "s2", title = "Collect Mail")
    assertEquals(s1, TapFlowExternalTriggerReceiver.findScriptByName(listOf(s1, s2), "  daily check-in "))
    assertNull(TapFlowExternalTriggerReceiver.findScriptByName(listOf(s1, s2), "missing"))

    val p1 = Pipeline(id = "p1", title = "Morning Routine")
    assertEquals(p1, TapFlowExternalTriggerReceiver.findPipelineByName(listOf(p1), "MORNING ROUTINE"))
    assertNull(TapFlowExternalTriggerReceiver.findPipelineByName(listOf(p1), ""))
  }
}
