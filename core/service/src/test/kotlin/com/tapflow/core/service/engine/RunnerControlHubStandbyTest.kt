package com.tapflow.core.service.engine

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunnerControlHubStandbyTest {

  @Test
  fun standbyDelegatesPlayPrevNextUntilSessionTakesOver() {
    val hub = RunnerControlHub()
    var played = 0
    var prevStepped = 0
    var nextStepped = 0

    val standbySnapshot =
      RunnerExecutionSnapshot(
        phase = RunnerPhase.IDLE,
        title = "Farm Loop",
        isPipeline = false,
        currentTopLevelAction = 0,
        totalTopLevelActions = 5,
      )

    hub.registerStandby(
      snapshot = standbySnapshot,
      onPlay = { played++ },
      onPreviousStep = { prevStepped++ },
      onNextStep = { nextStepped++ },
    )

    assertEquals(RunnerPhase.IDLE, hub.snapshot.value.phase)
    assertEquals(0, hub.snapshot.value.currentTopLevelAction)
    assertEquals(5, hub.snapshot.value.totalTopLevelActions)

    hub.play()
    assertEquals(1, played)

    hub.togglePlayPause()
    assertEquals(2, played)

    hub.requestPreviousStep()
    assertEquals(1, prevStepped)

    hub.requestNextStep()
    assertEquals(1, nextStepped)

    // Once an active session registers, standby callbacks must no longer fire
    val sessionJob = Job()
    hub.registerSession(job = sessionJob, startPaused = true, onStopRequested = {})
    assertTrue(hub.isPaused.value)

    hub.requestNextStep()
    assertEquals(1, nextStepped)
    assertEquals(RunnerStepCommand.NextSingleStep, hub.pollCommand())

    hub.clearToIdle(isPipeline = true)
    assertFalse(hub.isPaused.value)
    assertEquals(RunnerPhase.IDLE, hub.snapshot.value.phase)
    assertTrue(hub.snapshot.value.isPipeline)
    assertNull(hub.pollCommand())
  }
}
