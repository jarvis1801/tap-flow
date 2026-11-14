package com.tapflow.core.service.engine

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.entitlement.ProFeatureGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RunnerPhase {
  IDLE,
  RUNNING,
  PAUSED,
  COMPLETED,
  FAILED,
  STOPPED,
  PAYWALL_BLOCKED,
}

@Immutable
data class RunnerExecutionSnapshot(
  val phase: RunnerPhase = RunnerPhase.IDLE,
  val title: String = "",
  val isPipeline: Boolean = false,
  val currentTopLevelAction: Int = 0,
  val totalTopLevelActions: Int = 0,
  val currentPipelineStep: Int = 0,
  val totalPipelineSteps: Int = 0,
  val currentStepIteration: Int = 0,
  val totalStepIterations: Int = 0,
  val pipelineCycle: Int = 0,
  val statusMessage: String? = null,
  val blockedProGates: Set<ProFeatureGate> = emptySet(),
  val lastErrorScreenshotPath: String? = null,
)

sealed interface RunnerStepCommand {
  data object PreviousTopLevel : RunnerStepCommand
  data object NextSingleStep : RunnerStepCommand
}

@Singleton
class RunnerControlHub @Inject constructor() {
  private val _snapshot = MutableStateFlow(RunnerExecutionSnapshot())
  val snapshot: StateFlow<RunnerExecutionSnapshot> = _snapshot.asStateFlow()

  private val _isPaused = MutableStateFlow(false)
  val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

  @Volatile
  private var activeSessionJob: Job? = null

  @Volatile
  private var stopCallback: (() -> Unit)? = null

  private val commandChannel = Channel<RunnerStepCommand>(Channel.UNLIMITED)
  val commandReceiveChannel: ReceiveChannel<RunnerStepCommand>
    get() = commandChannel

  fun registerSession(job: Job, onStopRequested: () -> Unit) {
    activeSessionJob = job
    stopCallback = onStopRequested
    _isPaused.value = false
    drainCommands()
  }

  fun clearSession(finalSnapshot: RunnerExecutionSnapshot) {
    activeSessionJob = null
    stopCallback = null
    _isPaused.value = false
    _snapshot.value = finalSnapshot
  }

  fun updateSnapshot(transform: (RunnerExecutionSnapshot) -> RunnerExecutionSnapshot) {
    _snapshot.update(transform)
  }

  fun play() {
    _isPaused.value = false
    _snapshot.update {
      if (it.phase == RunnerPhase.PAUSED) it.copy(phase = RunnerPhase.RUNNING, statusMessage = null)
      else it
    }
  }

  fun pause(reason: String? = null) {
    if (activeSessionJob == null) return
    _isPaused.value = true
    _snapshot.update {
      it.copy(
        phase = RunnerPhase.PAUSED,
        statusMessage = reason ?: it.statusMessage,
      )
    }
  }

  fun togglePlayPause() {
    if (_isPaused.value) play() else pause()
  }

  fun requestPreviousStep() {
    if (activeSessionJob == null) return
    commandChannel.trySend(RunnerStepCommand.PreviousTopLevel)
  }

  fun requestNextStep() {
    if (activeSessionJob == null) return
    commandChannel.trySend(RunnerStepCommand.NextSingleStep)
  }

  fun stopExecution() {
    stopCallback?.invoke()
    activeSessionJob?.cancel()
    _isPaused.value = false
    drainCommands()
  }

  fun pollCommand(): RunnerStepCommand? = commandChannel.tryReceive().getOrNull()

  private fun drainCommands() {
    while (commandChannel.tryReceive().isSuccess) {
      // drained
    }
  }
}
