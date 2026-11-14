package com.tapflow.core.service.engine

import android.content.Context
import android.content.Intent
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.repository.LogRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.ExecutionStatus
import com.tapflow.core.model.log.PipelineSessionLog
import com.tapflow.core.model.log.PipelineStepRunLog
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.pipeline.PipelineErrorStrategy
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.clipboard.BackgroundClipboardManager
import com.tapflow.core.service.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Singleton
class PipelineExecutionEngine @Inject constructor(
  @ApplicationContext private val context: Context,
  private val scriptRepository: ScriptRepository,
  private val logRepository: LogRepository,
  private val entitlementRepository: EntitlementRepository,
  private val preferencesRepository: TapFlowPreferencesRepository,
  private val backgroundClipboardManager: BackgroundClipboardManager,
  private val accessibilityBridge: AccessibilityManagerBridge,
  private val shizukuManager: ShizukuManager,
  private val automationRunnerEngine: AutomationRunnerEngine,
  private val controlHub: RunnerControlHub,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  fun startPipeline(pipeline: Pipeline): Job {
    controlHub.stopExecution()
    val job =
      scope.launch {
        executePipelineSession(pipeline)
      }
    return job
  }

  private suspend fun executePipelineSession(pipeline: Pipeline) {
    val allScripts = scriptRepository.getAllScripts().associateBy { it.id }
    val blockers = entitlementRepository.detectPipelineProBlockers(pipeline, allScripts)
    if (blockers.isNotEmpty()) {
      controlHub.clearSession(
        RunnerExecutionSnapshot(
          phase = RunnerPhase.PAYWALL_BLOCKED,
          title = pipeline.title,
          isPipeline = true,
          statusMessage = "Pro features required: ${blockers.joinToString { it.displayName }}",
          blockedProGates = blockers,
        ),
      )
      return
    }

    val currentJob = coroutineContext[Job]!!
    controlHub.registerSession(currentJob) {
      scope.launch { accessibilityBridge.releaseActivePress() }
    }

    val startedAt = System.currentTimeMillis()
    val prefs = preferencesRepository.getPreferencesOnce()
    // Single non-resetting VariableRuntimeStore retained for the entire pipeline session
    val sharedVariables =
      VariableRuntimeStore(
        clipboardReader = { backgroundClipboardManager.readClipboardText() },
      )

    val stepRunLogs = mutableListOf<PipelineStepRunLog>()
    var pipelineStatus = ExecutionStatus.SUCCESS
    var pipelineErrorReason: String? = null
    var lastErrorShot: String? = null

    val maxCycles =
      when (val repeat = pipeline.repeatMode) {
        PipelineRepeatMode.RunOnce -> 1
        is PipelineRepeatMode.Count -> repeat.iterations.coerceAtLeast(1)
        PipelineRepeatMode.Unlimited -> Int.MAX_VALUE
      }

    try {
      var cycle = 1
      outer@ while (cycle <= maxCycles) {
        val totalSteps = pipeline.steps.size
        for ((stepIdx, step) in pipeline.steps.withIndex()) {
          val script = scriptRepository.getScriptById(step.scriptId)
          val repeatTotal = step.repeatCount.coerceAtLeast(1)

          if (script == null) {
            val errShot = automationRunnerEngine.captureErrorScreenshot(prefs.shizuku.silentScreenshots)
            val missingReason = "[Missing Script] Step #${stepIdx + 1} references missing script '${step.scriptId}'."
            val missingLog =
              ExecutionSessionLog(
                id = UUID.randomUUID().toString(),
                scriptId = step.scriptId,
                scriptTitle = "[Missing Script]",
                status = ExecutionStatus.FAILED,
                startedAtMs = System.currentTimeMillis(),
                finishedAtMs = System.currentTimeMillis(),
                totalStepsExecuted = 0,
                errorReason = missingReason,
                errorScreenshotPath = errShot,
              )
            stepRunLogs += PipelineStepRunLog(stepIndex = stepIdx, runNumber = 1, scriptSessionLog = missingLog)
            lastErrorShot = errShot

            if (pipeline.errorStrategy == PipelineErrorStrategy.ABORT) {
              pipelineStatus = ExecutionStatus.FAILED
              pipelineErrorReason = missingReason
              break@outer
            } else {
              performCrashRecovery(pipeline.crashRecoveryAppPackage, prefs.shizuku.rootlessForceStop)
              continue
            }
          }

          for (runNum in 1..repeatTotal) {
            controlHub.updateSnapshot {
              it.copy(
                phase = if (controlHub.isPaused.value) RunnerPhase.PAUSED else RunnerPhase.RUNNING,
                title = "${pipeline.title} • ${script.title}",
                isPipeline = true,
                currentPipelineStep = stepIdx + 1,
                totalPipelineSteps = totalSteps,
                currentStepIteration = runNum,
                totalStepIterations = repeatTotal,
                pipelineCycle = cycle,
                currentTopLevelAction = 1,
                totalTopLevelActions = script.actions.size,
              )
            }

            val stepLog =
              automationRunnerEngine.runScriptInternal(
                script = script,
                variables = sharedVariables,
                isPipelineChild = true,
                recordStandaloneLog = false,
              )

            stepRunLogs +=
              PipelineStepRunLog(
                stepIndex = stepIdx,
                runNumber = runNum,
                scriptSessionLog = stepLog,
              )

            when (stepLog.status) {
              ExecutionStatus.SUCCESS -> Unit
              ExecutionStatus.STOPPED -> {
                pipelineStatus = ExecutionStatus.STOPPED
                pipelineErrorReason = stepLog.errorReason ?: "Stopped by user"
                break@outer
              }
              ExecutionStatus.FAILED -> {
                lastErrorShot = stepLog.errorScreenshotPath
                if (pipeline.errorStrategy == PipelineErrorStrategy.ABORT) {
                  pipelineStatus = ExecutionStatus.FAILED
                  pipelineErrorReason = stepLog.errorReason ?: "Step ${stepIdx + 1} (${script.title}) failed"
                  break@outer
                } else {
                  performCrashRecovery(pipeline.crashRecoveryAppPackage, prefs.shizuku.rootlessForceStop)
                }
              }
            }
          }
        }
        cycle++
      }
    } catch (_: CancellationException) {
      pipelineStatus = ExecutionStatus.STOPPED
      pipelineErrorReason = "Stopped by user"
      accessibilityBridge.releaseActivePress()
    }

    val finishedAt = System.currentTimeMillis()
    val pipelineLog =
      PipelineSessionLog(
        id = UUID.randomUUID().toString(),
        pipelineId = pipeline.id,
        pipelineTitle = pipeline.title,
        status = pipelineStatus,
        startedAtMs = startedAt,
        finishedAtMs = finishedAt,
        stepRuns = stepRunLogs.toList(),
        errorReason = pipelineErrorReason,
      )
    logRepository.recordPipelineSession(pipelineLog, finishedAt)

    val finalPhase =
      when (pipelineStatus) {
        ExecutionStatus.SUCCESS -> RunnerPhase.COMPLETED
        ExecutionStatus.FAILED -> RunnerPhase.FAILED
        ExecutionStatus.STOPPED -> RunnerPhase.STOPPED
      }
    controlHub.clearSession(
      RunnerExecutionSnapshot(
        phase = finalPhase,
        title = pipeline.title,
        isPipeline = true,
        currentPipelineStep = pipeline.steps.size,
        totalPipelineSteps = pipeline.steps.size,
        statusMessage = pipelineErrorReason,
        lastErrorScreenshotPath = lastErrorShot,
      ),
    )
  }

  private suspend fun performCrashRecovery(
    recoveryPackageName: String?,
    useShizukuForceStop: Boolean,
  ) {
    if (recoveryPackageName.isNullOrBlank()) return
    if (useShizukuForceStop && shizukuManager.diagnosticState.value.isFullyReady) {
      shizukuManager.forceStopApp(recoveryPackageName)
      delay(400L)
    }
    val launchIntent =
      context.packageManager.getLaunchIntentForPackage(recoveryPackageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
    if (launchIntent != null) {
      runCatching { context.startActivity(launchIntent) }
      delay(1_200L)
    }
  }
}
