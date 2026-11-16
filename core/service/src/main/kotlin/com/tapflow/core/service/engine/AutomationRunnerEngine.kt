package com.tapflow.core.service.engine

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent as AndroidKeyEvent
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.repository.LogRepository
import com.tapflow.core.datastore.ShizukuPreferences
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.LongPressMode
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.LoopTimeoutAction
import com.tapflow.core.model.action.MultiTouchMode
import com.tapflow.core.model.action.PostTextActionKey
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.SwipeTrajectoryMode
import com.tapflow.core.model.action.SystemKeyType
import com.tapflow.core.model.action.TextInjectionEngine
import com.tapflow.core.model.action.VibratePattern
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.VariableType
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.log.ConditionEvalTrace
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.ExecutionStatus
import com.tapflow.core.model.script.Script
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.clipboard.BackgroundClipboardManager
import com.tapflow.core.service.external.ExternalBroadcastContract
import com.tapflow.core.service.external.ExternalBroadcastEmitter
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.projection.ScreenCaptureState
import com.tapflow.core.service.shizuku.ShizukuManager
import com.tapflow.core.service.vision.ZeroAllocVisionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

private sealed interface ControlSignal {
  data object None : ControlSignal
  data object Break : ControlSignal
  data object Continue : ControlSignal
  data object EndScript : ControlSignal
}

private class StepRestartException(val targetIndex: Int) : CancellationException("Step pointer jumped")

private class ScriptFailureException(message: String) : RuntimeException(message)

@Singleton
class AutomationRunnerEngine @Inject constructor(
  @ApplicationContext private val context: Context,
  private val accessibilityBridge: AccessibilityManagerBridge,
  private val shizukuManager: ShizukuManager,
  private val mediaProjectionBridge: MediaProjectionBridge,
  private val visionEngine: ZeroAllocVisionEngine,
  private val backgroundClipboardManager: BackgroundClipboardManager,
  private val conditionEvaluator: ConditionEvaluator,
  private val preferencesRepository: TapFlowPreferencesRepository,
  private val entitlementRepository: EntitlementRepository,
  private val logRepository: LogRepository,
  private val scriptAssetManager: ScriptAssetManager,
  private val externalBroadcastEmitter: ExternalBroadcastEmitter,
  val controlHub: RunnerControlHub,
) {
  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Volatile
  private var activeJob: Job? = null

  @Volatile
  private var standbyScript: Script? = null

  @Volatile
  private var standbyVariables: VariableRuntimeStore? = null

  suspend fun prepareStandbyScript(script: Script) {
    val prevJob = activeJob
    controlHub.stopExecution()
    prevJob?.cancelAndJoin()
    activeJob = null
    standbyScript = script
    standbyVariables =
      VariableRuntimeStore(clipboardReader = { backgroundClipboardManager.readClipboardText() }).apply {
        seedIfAbsent(script.variables)
      }
    controlHub.registerStandby(
      snapshot =
        RunnerExecutionSnapshot(
          phase = RunnerPhase.IDLE,
          title = script.title,
          isPipeline = false,
          currentTopLevelAction = 0,
          totalTopLevelActions = script.actions.size,
        ),
      onPlay = { startScript(script, startPaused = false, initialSingleStep = false) },
      onPreviousStep = { startScript(script, startPaused = true, initialSingleStep = false) },
      onNextStep = { startScript(script, startPaused = false, initialSingleStep = true) },
    )
  }

  fun startScript(script: Script): Job =
    startScript(script = script, startPaused = false, initialSingleStep = false)

  fun startScript(
    script: Script,
    startPaused: Boolean = false,
    initialSingleStep: Boolean = false,
  ): Job {
    val prevJob = activeJob
    controlHub.stopExecution()
    val job =
      engineScope.launch {
        prevJob?.cancelAndJoin()
        executeStandaloneScript(
          script = script,
          startPaused = startPaused,
          initialSingleStep = initialSingleStep,
        )
      }
    activeJob = job
    return job
  }

  private suspend fun executeStandaloneScript(
    script: Script,
    startPaused: Boolean = false,
    initialSingleStep: Boolean = false,
  ) {
    val wasTriggeredExternally = controlHub.consumeExternalTriggerPending()
    val blockers = entitlementRepository.detectScriptProBlockers(script)
    if (blockers.isNotEmpty()) {
      standbyScript = null
      standbyVariables = null
      controlHub.clearSession(
        RunnerExecutionSnapshot(
          phase = RunnerPhase.PAYWALL_BLOCKED,
          title = script.title,
          totalTopLevelActions = script.actions.size,
          statusMessage = "Pro features required: ${blockers.joinToString { it.displayName }}",
          blockedProGates = blockers,
        ),
      )
      val externalEnabled =
        withContext(NonCancellable) {
          runCatching { preferencesRepository.getPreferencesOnce().shizuku.externalBroadcastEnabled }
            .getOrDefault(false)
        }
      if (externalEnabled || wasTriggeredExternally) {
        externalBroadcastEmitter.emitExecutionFinished(
          status = ExternalBroadcastContract.STATUS_REJECTED,
          mode = ExternalBroadcastContract.MODE_SCRIPT,
          targetId = script.id,
          targetTitle = script.title,
          durationMs = 0L,
          stepsExecuted = 0,
          error = ExternalBroadcastContract.ERROR_PRO_REQUIRED,
        )
      }
      return
    }

    val preparedVars =
      if (standbyScript?.id == script.id) {
        standbyVariables
      } else {
        null
      }
    standbyScript = null
    standbyVariables = null
    val variables =
      (preparedVars ?: VariableRuntimeStore(clipboardReader = { backgroundClipboardManager.readClipboardText() }))
        .apply { seedIfAbsent(script.variables) }

    val currentJob = coroutineContext[Job]!!
    controlHub.registerSession(currentJob, startPaused = startPaused) {
      engineScope.launch { accessibilityBridge.releaseActivePress() }
    }

    val sessionLog =
      runScriptInternal(
        script = script,
        variables = variables,
        isPipelineChild = false,
        recordStandaloneLog = true,
        initialSingleStep = initialSingleStep,
      )

    val finalPhase =
      when (sessionLog.status) {
        ExecutionStatus.SUCCESS -> RunnerPhase.COMPLETED
        ExecutionStatus.FAILED -> RunnerPhase.FAILED
        ExecutionStatus.STOPPED -> RunnerPhase.STOPPED
      }
    controlHub.clearSession(
      RunnerExecutionSnapshot(
        phase = finalPhase,
        title = script.title,
        currentTopLevelAction = script.actions.size,
        totalTopLevelActions = script.actions.size,
        statusMessage = sessionLog.errorReason,
        lastErrorScreenshotPath = sessionLog.errorScreenshotPath,
      ),
    )

    val externalEnabled =
      withContext(NonCancellable) {
        runCatching { preferencesRepository.getPreferencesOnce().shizuku.externalBroadcastEnabled }
          .getOrDefault(false)
      }
    if (externalEnabled || wasTriggeredExternally) {
      val broadcastStatus =
        when (sessionLog.status) {
          ExecutionStatus.SUCCESS -> ExternalBroadcastContract.STATUS_COMPLETED
          ExecutionStatus.FAILED -> ExternalBroadcastContract.STATUS_FAILED
          ExecutionStatus.STOPPED -> ExternalBroadcastContract.STATUS_STOPPED
        }
      externalBroadcastEmitter.emitExecutionFinished(
        status = broadcastStatus,
        mode = ExternalBroadcastContract.MODE_SCRIPT,
        targetId = script.id,
        targetTitle = script.title,
        durationMs = (sessionLog.finishedAtMs - sessionLog.startedAtMs).coerceAtLeast(0L),
        stepsExecuted = sessionLog.totalStepsExecuted,
        error =
          if (sessionLog.status == ExecutionStatus.SUCCESS) {
            ExternalBroadcastContract.ERROR_NONE
          } else {
            sessionLog.errorReason.orEmpty()
          },
      )
    }
  }

  /**
   * Executes [script] within an existing [variables] store and returns [ExecutionSessionLog].
   * Used both standalone and by [PipelineExecutionEngine] (non-resetting pipeline variables).
   */
  suspend fun runScriptInternal(
    script: Script,
    variables: VariableRuntimeStore,
    isPipelineChild: Boolean,
    recordStandaloneLog: Boolean,
    initialSingleStep: Boolean = false,
  ): ExecutionSessionLog = coroutineScope {
    variables.seedIfAbsent(script.variables)

    val prefs = preferencesRepository.getPreferencesOnce()
    val shizukuPrefs = prefs.shizuku
    val startedAt = System.currentTimeMillis()
    val entries = mutableListOf<ActionLogEntry>()
    val totalActions = script.actions.size
    var stepsExecuted = 0
    var cursor = 0
    var singleStepPending = initialSingleStep
    var status = ExecutionStatus.SUCCESS
    var errorReason: String? = null
    var errorScreenshotPath: String? = null

    val requiresVision = script.requiresVision()
    val projectionWatchJob =
      if (requiresVision && !shizukuPrefs.silentScreenshots) {
        launch {
          mediaProjectionBridge.captureState.collectLatest { state ->
            if (state is ScreenCaptureState.StoppedBySystem) {
              controlHub.pause(state.reason)
            }
          }
        }
      } else {
        null
      }

    try {
      while (cursor < totalActions) {
        // Check queued step commands when running
        when (val queued = controlHub.pollCommand()) {
          RunnerStepCommand.PreviousTopLevel -> {
            cursor = (cursor - 1).coerceAtLeast(0)
          }
          RunnerStepCommand.NextSingleStep -> {
            singleStepPending = true
          }
          null -> Unit
        }

        // Pause gate handling Play / Previous / Next cleanly
        while (controlHub.isPaused.value) {
          controlHub.updateSnapshot {
            it.copy(
              phase = RunnerPhase.PAUSED,
              title = if (isPipelineChild) it.title else script.title,
              currentTopLevelAction = (cursor + 1).coerceAtMost(totalActions),
              totalTopLevelActions = totalActions,
            )
          }
          val cmd = awaitResumeOrCommand()
          when (cmd) {
            null -> break // Resumed via Play
            RunnerStepCommand.PreviousTopLevel -> {
              cursor = (cursor - 1).coerceAtLeast(0)
            }
            RunnerStepCommand.NextSingleStep -> {
              singleStepPending = true
              break
            }
          }
        }

        val action = script.actions[cursor]
        controlHub.updateSnapshot {
          it.copy(
            phase = RunnerPhase.RUNNING,
            title = if (isPipelineChild) it.title else script.title,
            currentTopLevelAction = cursor + 1,
            totalTopLevelActions = totalActions,
          )
        }

        val stepSignal =
          try {
            val stepResult =
              executeActionTreeStep(
                action = action,
                stepLabel = "#${(cursor + 1).toString().padStart(2, '0')}",
                script = script,
                variables = variables,
                shizukuPrefs = shizukuPrefs,
                entries = entries,
                onCountExecuted = { stepsExecuted++ },
              )
            stepResult
          } catch (jump: StepRestartException) {
            cursor = jump.targetIndex.coerceIn(0, (totalActions - 1).coerceAtLeast(0))
            continue
          }

        if (stepSignal == ControlSignal.EndScript) {
          break
        }

        cursor++
        if (singleStepPending) {
          singleStepPending = false
          if (cursor < totalActions || isPipelineChild) {
            controlHub.pause()
          }
        }
      }
    } catch (_: CancellationException) {
      status = ExecutionStatus.STOPPED
      errorReason = "Stopped by user"
      withContext(NonCancellable) {
        accessibilityBridge.releaseActivePress()
      }
    } catch (failure: ScriptFailureException) {
      status = ExecutionStatus.FAILED
      errorReason = failure.message ?: "Script execution failed"
      errorScreenshotPath = captureErrorScreenshot(shizukuPrefs.silentScreenshots)
    } catch (unexpected: Throwable) {
      status = ExecutionStatus.FAILED
      errorReason = unexpected.message ?: unexpected::class.simpleName ?: "Unexpected error"
      errorScreenshotPath = captureErrorScreenshot(shizukuPrefs.silentScreenshots)
    } finally {
      projectionWatchJob?.cancel()
    }

    val finishedAt = System.currentTimeMillis()
    val sessionLog =
      ExecutionSessionLog(
        id = UUID.randomUUID().toString(),
        scriptId = script.id,
        scriptTitle = script.title,
        status = status,
        startedAtMs = startedAt,
        finishedAtMs = finishedAt,
        totalStepsExecuted = stepsExecuted,
        entries = entries.toList(),
        errorReason = errorReason,
        errorScreenshotPath = errorScreenshotPath,
      )

    if (recordStandaloneLog && !isPipelineChild) {
      withContext(NonCancellable) {
        logRepository.recordScriptSession(sessionLog, finishedAt)
      }
    }
    sessionLog
  }

  private suspend fun awaitResumeOrCommand(): RunnerStepCommand? = coroutineScope {
    if (!controlHub.isPaused.value) return@coroutineScope null
    val pauseWatchJob =
      launch {
        controlHub.isPaused.first { !it }
      }
    val cmd =
      select<RunnerStepCommand?> {
        pauseWatchJob.onJoin { null }
        controlHub.commandReceiveChannel.onReceive { it }
      }
    pauseWatchJob.cancel()
    cmd
  }

  private suspend fun executeActionTreeStep(
    action: ScriptAction,
    stepLabel: String,
    script: Script,
    variables: VariableRuntimeStore,
    shizukuPrefs: ShizukuPreferences,
    entries: MutableList<ActionLogEntry>,
    onCountExecuted: () -> Unit,
  ): ControlSignal {
    val startMs = System.currentTimeMillis()
    var summary = action::class.simpleName ?: "Action"
    var condTrace: ConditionEvalTrace? = null
    var signal: ControlSignal = ControlSignal.None

    // Check inline step interruption command
    when (controlHub.pollCommand()) {
      RunnerStepCommand.PreviousTopLevel -> {
        val currentIdx = stepLabel.removePrefix("#").substringBefore('.').toIntOrNull()?.minus(1) ?: 0
        throw StepRestartException((currentIdx - 1).coerceAtLeast(0))
      }
      else -> Unit
    }

    when (action) {
      is ScriptAction.Tap -> {
        val baseX = variables.resolveNumericInput(action.x)
        val baseY = variables.resolveNumericInput(action.y)
        val repeats = variables.resolveNumericInput(action.repeatCount).coerceAtLeast(1)

        repeat(repeats) { index ->
          val (tx, ty) = applyJitter(baseX, baseY, action.jitterPx)
          val pressMs = variables.resolveDurationInput(action.pressDuration, minMs = 1L)
          val shizukuSucceeded =
            shizukuPrefs.shellInputInjection &&
              pressMs <= 120L &&
              shizukuManager.isReadyNow() &&
              shizukuManager.tap(tx, ty).isSuccess
          val ok =
            shizukuSucceeded ||
              accessibilityBridge.tap(tx.toFloat(), ty.toFloat(), pressMs)
          if (!ok) throw ScriptFailureException("Tap failed at ($tx, $ty).")
          if (index < repeats - 1) {
            val interval = variables.resolveDurationInput(action.repeatInterval, minMs = 20L)
            DelayResolver.delayPauseAware(interval, controlHub.isPaused)
          }
        }
        summary = "Tap ($baseX, $baseY) x$repeats"
        onCountExecuted()
      }

      is ScriptAction.DoubleTap -> {
        val baseX = variables.resolveNumericInput(action.x)
        val baseY = variables.resolveNumericInput(action.y)
        val (tx, ty) = applyJitter(baseX, baseY, action.jitterPx)
        val shizukuReady = shizukuPrefs.shellInputInjection && shizukuManager.isReadyNow()
        val ok =
          if (shizukuReady) {
            if (shizukuManager.tap(tx, ty).isSuccess) {
              DelayResolver.delayPauseAware(action.tapIntervalMs.coerceAtLeast(30L), controlHub.isPaused)
              shizukuManager.tap(tx, ty).isSuccess ||
                accessibilityBridge.tap(tx.toFloat(), ty.toFloat(), action.pressDurationMs)
            } else {
              accessibilityBridge.doubleTap(tx.toFloat(), ty.toFloat(), action.pressDurationMs, action.tapIntervalMs)
            }
          } else {
            accessibilityBridge.doubleTap(tx.toFloat(), ty.toFloat(), action.pressDurationMs, action.tapIntervalMs)
          }
        if (!ok) throw ScriptFailureException("DoubleTap failed at ($tx, $ty).")
        summary = "DoubleTap ($tx, $ty)"
        onCountExecuted()
      }

      is ScriptAction.LongPress -> {
        val baseX = variables.resolveNumericInput(action.x)
        val baseY = variables.resolveNumericInput(action.y)
        val (tx, ty) = applyJitter(baseX, baseY, action.jitterPx)
        val ok =
          when (action.mode) {
            LongPressMode.STANDARD -> {
              val canTryShizuku =
                shizukuPrefs.shellInputInjection &&
                  !action.microMovementJitter &&
                  action.pressDurationMs <= AccessibilityManagerBridge.MAX_STROKE_SEGMENT_MS &&
                  shizukuManager.isReadyNow()
              val shizukuSucceeded =
                canTryShizuku &&
                  shizukuManager.swipe(tx, ty, tx, ty, action.pressDurationMs).isSuccess
              shizukuSucceeded ||
                accessibilityBridge.executeLongPress(
                  x = tx.toFloat(),
                  y = ty.toFloat(),
                  durationMs = action.pressDurationMs,
                  microMovementJitter = action.microMovementJitter,
                  amplitudePx = action.microJitterAmplitudePx,
                )
            }
            LongPressMode.PRESS_DOWN_ONLY ->
              accessibilityBridge.pressDownOnly(
                x = tx.toFloat(),
                y = ty.toFloat(),
                microMovementJitter = action.microMovementJitter,
                amplitudePx = action.microJitterAmplitudePx,
              )
            LongPressMode.RELEASE_ONLY ->
              accessibilityBridge.releaseActivePress()
            LongPressMode.HOLD_UNTIL -> {
              val cond = action.holdUntilCondition
                ?: throw ScriptFailureException("HoldUntil requires a condition.")
              accessibilityBridge.holdUntil(
                x = tx.toFloat(),
                y = ty.toFloat(),
                microMovementJitter = action.microMovementJitter,
                amplitudePx = action.microJitterAmplitudePx,
                maxTimeoutMs = action.holdTimeoutMs,
              ) {
                conditionEvaluator.evaluate(cond, variables, shizukuPrefs.silentScreenshots).passed
              }
            }
          }
        if (!ok) throw ScriptFailureException("LongPress (${action.mode.name}) failed at ($tx, $ty).")
        summary = "LongPress ${action.mode.name} ($tx, $ty)"
        onCountExecuted()
      }

      is ScriptAction.MultiTouch -> {
        val (strokes, maxDur) = buildMultiTouchStrokes(action)
        val ok = accessibilityBridge.dispatchMultiTouchStrokes(strokes, maxDur)
        if (!ok) throw ScriptFailureException("MultiTouch (${action.mode.name}) failed.")
        summary = "MultiTouch ${action.mode.name} (${strokes.size} fingers)"
        onCountExecuted()
      }

      is ScriptAction.SwipeDrag -> {
        val sxRaw = variables.resolveNumericInput(action.startX)
        val syRaw = variables.resolveNumericInput(action.startY)
        val exRaw = variables.resolveNumericInput(action.endX)
        val eyRaw = variables.resolveNumericInput(action.endY)
        val (sx, sy) = applyJitter(sxRaw, syRaw, action.startJitterPx)
        val (ex, ey) = applyJitter(exRaw, eyRaw, action.endJitterPx)
        val canTryShizuku =
          shizukuPrefs.shellInputInjection &&
            action.trajectoryMode == SwipeTrajectoryMode.LINEAR &&
            action.holdBeforeDragMs <= 0L &&
            action.holdAfterDragMs <= 0L &&
            shizukuManager.isReadyNow()
        val shizukuSucceeded =
          canTryShizuku && shizukuManager.swipe(sx, sy, ex, ey, action.durationMs).isSuccess

        val ok =
          shizukuSucceeded ||
            executeSwipeViaAccessibility(action, sx.toFloat(), sy.toFloat(), ex.toFloat(), ey.toFloat())
        if (!ok) throw ScriptFailureException("Swipe failed from ($sx,$sy) to ($ex,$ey).")
        summary = "Swipe ${action.trajectoryMode.name} ($sx,$sy)->($ex,$ey)"
        onCountExecuted()
      }

      is ScriptAction.InputText -> {
        val text = variables.interpolate(action.textTemplate)
        if (action.tapToFocus && action.focusX != null && action.focusY != null) {
          val fx = action.focusX!!
          val fy = action.focusY!!
          val focusTapOk =
            (shizukuPrefs.shellInputInjection && shizukuManager.isReadyNow() && shizukuManager.tap(fx, fy).isSuccess) ||
              accessibilityBridge.tap(fx.toFloat(), fy.toFloat(), 40L)
          if (focusTapOk) {
            DelayResolver.delayPauseAware(120L, controlHub.isPaused)
          }
        }
        val ok =
          when (action.engine) {
            TextInjectionEngine.ACCESSIBILITY_SET_TEXT ->
              accessibilityBridge.setTextOnFocusedNode(text, action.clearExistingText)
            TextInjectionEngine.CLIPBOARD_PASTE -> {
              if (action.clearExistingText) {
                accessibilityBridge.setTextOnFocusedNode("", clearExisting = true)
              }
              backgroundClipboardManager.copyText(text)
              DelayResolver.delayPauseAware(40L, controlHub.isPaused)
              accessibilityBridge.pasteOnFocusedNode()
            }
            TextInjectionEngine.HUMAN_TYPING -> {
              val tryShizuku =
                shizukuPrefs.shellInputInjection &&
                  isAsciiPrintable(text) &&
                  shizukuManager.isReadyNow()
              if (tryShizuku) {
                if (action.clearExistingText) {
                  accessibilityBridge.setTextOnFocusedNode("", clearExisting = true)
                }
                shizukuManager.inputText(text).isSuccess ||
                  accessibilityBridge.typeTextProgressively(text, clearExisting = false) ||
                  accessibilityBridge.setTextOnFocusedNode(text, clearExisting = false)
              } else {
                accessibilityBridge.typeTextProgressively(text, action.clearExistingText) ||
                  accessibilityBridge.setTextOnFocusedNode(text, action.clearExistingText)
              }
            }
          }
        if (!ok) throw ScriptFailureException("InputText (${action.engine.name}) failed.")
        if (action.postActionKey != PostTextActionKey.NONE) {
          val imeOk = accessibilityBridge.performPostTextImeAction(action.postActionKey)
          if (!imeOk && shizukuPrefs.enhancedKeyEvents && shizukuManager.isReadyNow()) {
            shizukuManager.sendKeyEvent(AndroidKeyEvent.KEYCODE_ENTER)
          }
        }
        if (action.autoHideKeyboard) {
          accessibilityBridge.performGlobalAction(SystemKeyType.BACK)
        }
        summary = "InputText (${action.engine.name}, ${text.length} chars)"
        onCountExecuted()
      }

      is ScriptAction.IfBlock -> {
        val chainEval =
          evaluateIfBlockChain(action) { group ->
            conditionEvaluator.evaluate(group, variables, shizukuPrefs.silentScreenshots)
          }
        condTrace = chainEval.conditionTrace
        summary = "${action.name.ifBlank { "If Block" }}: ${chainEval.branchLabel}"
        val ifEntry =
          ActionLogEntry(
            id = UUID.randomUUID().toString(),
            stepLabel = stepLabel,
            actionType = "IfBlock",
            summary = summary,
            timestampMs = startMs,
            durationMs = System.currentTimeMillis() - startMs,
            status = ExecutionStatus.SUCCESS,
            conditionTrace = condTrace,
          )
        entries += ifEntry
        controlHub.emitActionLog(ifEntry)
        onCountExecuted()

        for ((idx, child) in chainEval.actionsToRun.withIndex()) {
          val childSignal =
            executeActionTreeStep(
              action = child,
              stepLabel = "$stepLabel.${idx + 1}",
              script = script,
              variables = variables,
              shizukuPrefs = shizukuPrefs,
              entries = entries,
              onCountExecuted = onCountExecuted,
            )
          if (childSignal != ControlSignal.None) {
            return childSignal
          }
        }
        return ControlSignal.None
      }

      is ScriptAction.LoopBlock -> {
        val loopRes = executeLoopBlock(action, stepLabel, script, variables, shizukuPrefs, entries, onCountExecuted)
        return loopRes
      }

      is ScriptAction.Delay -> {
        val delayMs =
          DelayResolver.resolveDelayMs(
            action.config,
            minFloorMs = DelayConfig.MIN_STANDALONE_DELAY_MS,
          )
        DelayResolver.delayPauseAware(delayMs, controlHub.isPaused)
        summary = "Delay ${delayMs}ms"
        onCountExecuted()
      }

      is ScriptAction.Break -> {
        summary = "Break Loop"
        signal = ControlSignal.Break
        onCountExecuted()
      }

      is ScriptAction.Continue -> {
        summary = "Continue Loop"
        signal = ControlSignal.Continue
        onCountExecuted()
      }

      is ScriptAction.EndScript -> {
        summary = "End Script"
        signal = ControlSignal.EndScript
        onCountExecuted()
      }

      is ScriptAction.KeyEvent -> {
        val keyCode = mapToAndroidKeyCode(action.key)
        val shizukuSucceeded =
          shizukuPrefs.enhancedKeyEvents &&
            keyCode != null &&
            shizukuManager.isReadyNow() &&
            shizukuManager.sendKeyEvent(keyCode).isSuccess
        val ok =
          shizukuSucceeded ||
            accessibilityBridge.performGlobalAction(action.key)
        if (!ok) throw ScriptFailureException("KeyEvent ${action.key.name} failed.")
        summary = "KeyEvent ${action.key.name}"
        onCountExecuted()
      }

      is ScriptAction.LaunchApp -> {
        val launchIntent =
          context.packageManager.getLaunchIntentForPackage(action.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
          } ?: throw ScriptFailureException("Unable to launch app: ${action.packageName}")
        context.startActivity(launchIntent)
        summary = "Launch ${action.appLabel.ifBlank { action.packageName }}"
        onCountExecuted()
      }

      is ScriptAction.ForceStopApp -> {
        val packageName = action.packageName.trim()
        val label = action.appLabel.ifBlank { packageName }
        val useShizuku =
          entitlementRepository.canUseFeature(ProFeatureGate.SHIZUKU_SUITE) &&
            shizukuPrefs.rootlessForceStop &&
            shizukuManager.isReadyNow()
        val shizukuOk =
          if (useShizuku) {
            shizukuManager.forceStopApp(packageName).isSuccess
          } else {
            false
          }
        if (shizukuOk) {
          summary = "ForceStop $label"
        } else {
          val wentHome = accessibilityBridge.performGlobalAction(SystemKeyType.HOME)
          val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
          @Suppress("DEPRECATION")
          runCatching { activityManager?.killBackgroundProcesses(packageName) }
          if (!wentHome && activityManager == null) {
            throw ScriptFailureException("Failed to stop $packageName.")
          }
          summary = "Stop $label (Home + Background Kill)"
        }
        onCountExecuted()
      }

      is ScriptAction.ClipboardOp -> {
        when (action.operation) {
          ClipboardActionType.COPY -> {
            val resolved = variables.interpolate(action.valueTemplate)
            if (!backgroundClipboardManager.copyText(resolved)) {
              throw ScriptFailureException("Failed to copy to clipboard.")
            }
            if (!action.targetVariableKey.isNullOrBlank()) {
              variables.setDirect(action.targetVariableKey!!, VariableType.STRING, resolved)
            }
            summary = "Clipboard Copy (${resolved.length} chars)"
          }
          ClipboardActionType.PASTE -> {
            if (!action.targetVariableKey.isNullOrBlank()) {
              val clip = backgroundClipboardManager.readClipboardText().orEmpty()
              variables.setDirect(action.targetVariableKey!!, VariableType.STRING, clip)
            }
            if (!accessibilityBridge.pasteOnFocusedNode()) {
              throw ScriptFailureException("Clipboard Paste failed on focused view.")
            }
            summary = "Clipboard Paste"
          }
          ClipboardActionType.CLEAR -> {
            backgroundClipboardManager.clearClipboard()
            summary = "Clipboard Clear"
          }
        }
        onCountExecuted()
      }

      is ScriptAction.Vibrate -> {
        triggerVibration(action.pattern, action.customPatternMs)
        summary = "Vibrate (${action.pattern.name})"
        onCountExecuted()
      }

      is ScriptAction.PlayAlertSound -> {
        playAlertTone()
        summary = "Play Alert Sound"
        onCountExecuted()
      }

      is ScriptAction.VariableOp -> {
        when (val varResult = variables.executeVariableOp(action)) {
          is VariableOpResult.Success -> summary = varResult.summary
          is VariableOpResult.Failure -> throw ScriptFailureException(varResult.reason)
        }
        onCountExecuted()
      }
    }

    val entry =
      ActionLogEntry(
        id = UUID.randomUUID().toString(),
        stepLabel = stepLabel,
        actionType = action::class.simpleName ?: "Action",
        summary = summary,
        timestampMs = startMs,
        durationMs = System.currentTimeMillis() - startMs,
        status = ExecutionStatus.SUCCESS,
        conditionTrace = condTrace,
      )
    entries += entry
    controlHub.emitActionLog(entry)

    if (signal == ControlSignal.None) {
      val postDelayMs = DelayResolver.resolvePostActionDelayMs(action, script.globalDelay)
      if (postDelayMs > 0L) {
        DelayResolver.delayPauseAware(postDelayMs, controlHub.isPaused)
      }
    }

    return signal
  }

  private suspend fun executeLoopBlock(
    loop: ScriptAction.LoopBlock,
    stepLabel: String,
    script: Script,
    variables: VariableRuntimeStore,
    shizukuPrefs: ShizukuPreferences,
    entries: MutableList<ActionLogEntry>,
    onCountExecuted: () -> Unit,
  ): ControlSignal {
    val startMs = System.currentTimeMillis()
    val maxIters = loop.safeguards.maxIterations.coerceAtLeast(1)
    val maxTimeoutMs = loop.safeguards.maxTimeoutMs.coerceAtLeast(100L)
    val targetCount =
      when (val mode = loop.mode) {
        is LoopMode.Count -> variables.resolveNumericInput(mode.count).coerceAtLeast(0)
        else -> Int.MAX_VALUE
      }

    onCountExecuted()
    var iter = 0
    while (true) {
      val elapsed = System.currentTimeMillis() - startMs
      if (elapsed >= maxTimeoutMs || iter >= maxIters) {
        if (loop.safeguards.onTimeout == LoopTimeoutAction.STOP_AND_ALERT) {
          throw ScriptFailureException(
            "Loop '${loop.name}' exceeded safeguard (iter=$iter/$maxIters, elapsed=${elapsed}ms/${maxTimeoutMs}ms).",
          )
        } else {
          entries +=
            ActionLogEntry(
              id = UUID.randomUUID().toString(),
              stepLabel = stepLabel,
              actionType = "LoopBlock",
              summary = "${loop.name} stopped by safeguard at iteration $iter",
              timestampMs = System.currentTimeMillis(),
              durationMs = elapsed,
              status = ExecutionStatus.SUCCESS,
            )
          break
        }
      }

      val shouldContinue =
        when (val mode = loop.mode) {
          is LoopMode.Count -> iter < targetCount
          is LoopMode.WhileCondition ->
            conditionEvaluator.evaluate(mode.conditionRoot, variables, shizukuPrefs.silentScreenshots).passed
          is LoopMode.UntilCondition ->
            !conditionEvaluator.evaluate(mode.conditionRoot, variables, shizukuPrefs.silentScreenshots).passed
          LoopMode.Unlimited -> true
        }
      if (!shouldContinue) break

      if (loop.mode is LoopMode.Count) {
        val exportKey = (loop.mode as LoopMode.Count).exportIndexVarKey
        if (!exportKey.isNullOrBlank()) {
          variables.setDirect(exportKey, VariableType.NUMBER, iter.toString())
        }
      }

      var breakLoop = false
      for ((idx, child) in loop.bodyActions.withIndex()) {
        val sig =
          executeActionTreeStep(
            action = child,
            stepLabel = "$stepLabel.${idx + 1}",
            script = script,
            variables = variables,
            shizukuPrefs = shizukuPrefs,
            entries = entries,
            onCountExecuted = onCountExecuted,
          )
        when (sig) {
          ControlSignal.None -> Unit
          ControlSignal.Continue -> break
          ControlSignal.Break -> {
            breakLoop = true
            break
          }
          ControlSignal.EndScript -> return ControlSignal.EndScript
        }
      }

      if (breakLoop) break
      iter++

      val intervalMs = DelayResolver.resolveDelayMs(loop.safeguards.intervalDelay)
      DelayResolver.delayPauseAware(intervalMs, controlHub.isPaused)
    }
    return ControlSignal.None
  }

  private suspend fun executeSwipeViaAccessibility(
    action: ScriptAction.SwipeDrag,
    sx: Float,
    sy: Float,
    ex: Float,
    ey: Float,
  ): Boolean {
    val dragPath =
      Path().apply {
        when (action.trajectoryMode) {
          SwipeTrajectoryMode.LINEAR -> {
            moveTo(sx, sy)
            lineTo(ex, ey)
          }
          SwipeTrajectoryMode.BEZIER_CURVE -> {
            moveTo(sx, sy)
            val dx = ex - sx
            val dy = ey - sy
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val jitterSign = if (Random.nextBoolean()) 1f else -1f
            val curve = action.bezierCurvatureOffsetPx * jitterSign
            val cx = ((sx + ex) / 2f) + ((-dy / len) * curve)
            val cy = ((sy + ey) / 2f) + ((dx / len) * curve)
            quadTo(cx.coerceAtLeast(0f), cy.coerceAtLeast(0f), ex, ey)
          }
          SwipeTrajectoryMode.MULTI_POINT_PATH -> {
            if (action.multiPointPath.size >= 2) {
              val first = action.multiPointPath.first()
              moveTo(first.x.toFloat().coerceAtLeast(0f), first.y.toFloat().coerceAtLeast(0f))
              for (i in 1 until action.multiPointPath.size) {
                val pt = action.multiPointPath[i]
                lineTo(pt.x.toFloat().coerceAtLeast(0f), pt.y.toFloat().coerceAtLeast(0f))
              }
            } else {
              moveTo(sx, sy)
              lineTo(ex, ey)
            }
          }
        }
      }

    val holdBefore = action.holdBeforeDragMs.coerceIn(0L, AccessibilityManagerBridge.MAX_STROKE_SEGMENT_MS)
    val dragDur = action.durationMs.coerceIn(1L, AccessibilityManagerBridge.MAX_STROKE_SEGMENT_MS)
    val holdAfter = action.holdAfterDragMs.coerceIn(0L, AccessibilityManagerBridge.MAX_STROKE_SEGMENT_MS)

    if (holdBefore == 0L && holdAfter == 0L) {
      return accessibilityBridge.dispatchPathSwipe(dragPath, dragDur)
    }

    var stroke: GestureDescription.StrokeDescription
    if (holdBefore > 0L) {
      val holdPath = Path().apply { moveTo(sx, sy) }
      stroke = GestureDescription.StrokeDescription(holdPath, 0L, holdBefore, true)
      if (!accessibilityBridge.dispatchGestureAsync(GestureDescription.Builder().addStroke(stroke).build(), holdBefore + 3_000L)) {
        return false
      }
      stroke = stroke.continueStroke(dragPath, 0L, dragDur, holdAfter > 0L)
    } else {
      stroke = GestureDescription.StrokeDescription(dragPath, 0L, dragDur, true)
    }

    if (!accessibilityBridge.dispatchGestureAsync(GestureDescription.Builder().addStroke(stroke).build(), dragDur + 3_000L)) {
      return false
    }

    if (holdAfter > 0L) {
      val settlePath = Path().apply { moveTo(ex, ey) }
      val finalStroke = stroke.continueStroke(settlePath, 0L, holdAfter, false)
      return accessibilityBridge.dispatchGestureAsync(GestureDescription.Builder().addStroke(finalStroke).build(), holdAfter + 3_000L)
    }
    return true
  }

  private fun buildMultiTouchStrokes(
    action: ScriptAction.MultiTouch,
  ): Pair<List<GestureDescription.StrokeDescription>, Long> {
    val safeDur = action.durationMs.coerceIn(50L, AccessibilityManagerBridge.MAX_STROKE_SEGMENT_MS)
    return when (action.mode) {
      MultiTouchMode.CUSTOM_POINTS -> {
        var maxEnd = safeDur
        val strokes =
          action.customPoints.map { pt ->
            val (jx, jy) = applyJitter(pt.x, pt.y, pt.jitterPx)
            val start = pt.staggerDelayMs.coerceAtLeast(0L)
            val dur = pt.pressDurationMs.coerceIn(20L, AccessibilityManagerBridge.MAX_STROKE_SEGMENT_MS)
            maxEnd = maxOf(maxEnd, start + dur)
            GestureDescription.StrokeDescription(
              Path().apply { moveTo(jx.toFloat(), jy.toFloat()) },
              start,
              dur,
            )
          }
        strokes to maxEnd
      }

      MultiTouchMode.PINCH_SPREAD -> {
        val cx = action.centerX.toFloat().coerceAtLeast(0f)
        val cy = action.centerY.toFloat().coerceAtLeast(0f)
        val rStart = (action.startDistancePx / 2f).coerceAtLeast(10f)
        val rEnd = (action.endDistancePx / 2f).coerceAtLeast(10f)
        val p1 =
          Path().apply {
            moveTo((cx - rStart).coerceAtLeast(0f), cy)
            lineTo((cx - rEnd).coerceAtLeast(0f), cy)
          }
        val p2 =
          Path().apply {
            moveTo(cx + rStart, cy)
            lineTo(cx + rEnd, cy)
          }
        listOf(
          GestureDescription.StrokeDescription(p1, 0L, safeDur),
          GestureDescription.StrokeDescription(p2, 0L, safeDur),
        ) to safeDur
      }

      MultiTouchMode.ROTATE -> {
        val cx = action.centerX.toFloat().coerceAtLeast(0f)
        val cy = action.centerY.toFloat().coerceAtLeast(0f)
        val radius = action.radiusPx.toFloat().coerceAtLeast(20f)
        val p1 = buildArcPath(cx, cy, radius, action.startAngleDeg, action.endAngleDeg)
        val p2 = buildArcPath(cx, cy, radius, action.startAngleDeg + 180f, action.endAngleDeg + 180f)
        listOf(
          GestureDescription.StrokeDescription(p1, 0L, safeDur),
          GestureDescription.StrokeDescription(p2, 0L, safeDur),
        ) to safeDur
      }
    }
  }

  private fun buildArcPath(
    cx: Float,
    cy: Float,
    radius: Float,
    startDeg: Float,
    endDeg: Float,
  ): Path {
    val steps = 16
    return Path().apply {
      for (i in 0..steps) {
        val t = i.toFloat() / steps.toFloat()
        val angleRad = Math.toRadians((startDeg + ((endDeg - startDeg) * t)).toDouble())
        val px = (cx + (cos(angleRad) * radius).toFloat()).coerceAtLeast(0f)
        val py = (cy + (sin(angleRad) * radius).toFloat()).coerceAtLeast(0f)
        if (i == 0) moveTo(px, py) else lineTo(px, py)
      }
    }
  }

  private fun applyJitter(x: Int, y: Int, jitterPx: Int): Pair<Int, Int> {
    if (jitterPx <= 0) return x.coerceAtLeast(0) to y.coerceAtLeast(0)
    val dx = Random.nextInt(-jitterPx, jitterPx + 1)
    val dy = Random.nextInt(-jitterPx, jitterPx + 1)
    return (x + dx).coerceAtLeast(0) to (y + dy).coerceAtLeast(0)
  }

  private fun isAsciiPrintable(text: String): Boolean =
    text.all { it.code in 0x20..0x7E }

  private fun mapToAndroidKeyCode(key: SystemKeyType): Int? {
    return when (key) {
      SystemKeyType.HOME -> AndroidKeyEvent.KEYCODE_HOME
      SystemKeyType.BACK -> AndroidKeyEvent.KEYCODE_BACK
      SystemKeyType.RECENTS -> AndroidKeyEvent.KEYCODE_APP_SWITCH
      SystemKeyType.VOLUME_UP -> AndroidKeyEvent.KEYCODE_VOLUME_UP
      SystemKeyType.VOLUME_DOWN -> AndroidKeyEvent.KEYCODE_VOLUME_DOWN
      else -> null
    }
  }

  private fun triggerVibration(pattern: VibratePattern, customMs: List<Long>) {
    val vibrator =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      } ?: return
    if (!vibrator.hasVibrator()) return

    val effect =
      when (pattern) {
        VibratePattern.SHORT -> VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE)
        VibratePattern.LONG -> VibrationEffect.createOneShot(450L, VibrationEffect.DEFAULT_AMPLITUDE)
        VibratePattern.CUSTOM -> {
          val arr = customMs.map { it.coerceAtLeast(20L) }.toLongArray()
          if (arr.isEmpty()) VibrationEffect.createOneShot(150L, VibrationEffect.DEFAULT_AMPLITUDE)
          else VibrationEffect.createWaveform(arr, -1)
        }
      }
    runCatching { vibrator.vibrate(effect) }
  }

  private fun playAlertTone() {
    runCatching {
      val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val ringtone = RingtoneManager.getRingtone(context, uri)
      ringtone?.play()
    }
  }

  suspend fun captureErrorScreenshot(useShizukuFallback: Boolean): String? {
    val bitmap = visionEngine.captureCleanBitmap(useShizukuFallback) ?: return null
    return try {
      val outFile = File(scriptAssetManager.getErrorScreenshotsDir(), "err_${UUID.randomUUID()}.png")
      if (visionEngine.saveLosslessPng(bitmap, outFile)) outFile.absolutePath else null
    } finally {
      bitmap.recycle()
    }
  }
}

internal data class IfChainEvaluation(
  val branchLabel: String,
  val actionsToRun: List<ScriptAction>,
  val conditionTrace: ConditionEvalTrace,
)

internal suspend fun evaluateIfBlockChain(
  action: ScriptAction.IfBlock,
  evaluateCondition: suspend (ConditionGroup) -> ConditionEvalResult,
): IfChainEvaluation {
  val ifEval = evaluateCondition(action.conditionRoot)
  val ifTrace = ifEval.trace.copy(label = "If")

  if (ifEval.passed) {
    val trace =
      if (action.elseIfBranches.isEmpty()) {
        ifTrace
      } else {
        ConditionEvalTrace(
          nodeId = "${action.id}_chain",
          label = action.name.trim().ifBlank { "If / Else If Chain" },
          passed = true,
          actualSummary = "Matched If",
          expectedSummary = "1 If + ${action.elseIfBranches.size} Else If rules",
          children = listOf(ifTrace),
        )
      }
    return IfChainEvaluation(
      branchLabel = "THEN",
      actionsToRun = action.thenActions,
      conditionTrace = trace,
    )
  }

  val branchTraces = mutableListOf(ifTrace)
  for ((branchIdx, branch) in action.elseIfBranches.withIndex()) {
    val branchNum = branchIdx + 1
    val defaultLabel = "Else If #$branchNum"
    val branchLabel =
      if (branch.name.isNotBlank()) "$defaultLabel (${branch.name.trim()})" else defaultLabel
    val branchEval = evaluateCondition(branch.conditionRoot)
    val branchTrace = branchEval.trace.copy(label = branchLabel)
    branchTraces += branchTrace
    if (branchEval.passed) {
      return IfChainEvaluation(
        branchLabel = defaultLabel,
        actionsToRun = branch.actions,
        conditionTrace =
          ConditionEvalTrace(
            nodeId = "${action.id}_chain",
            label = action.name.trim().ifBlank { "If / Else If Chain" },
            passed = true,
            actualSummary = "Matched $defaultLabel",
            expectedSummary = "Evaluated ${branchTraces.size} of ${1 + action.elseIfBranches.size} rules",
            children = branchTraces,
          ),
      )
    }
  }

  val finalTrace =
    if (action.elseIfBranches.isEmpty()) {
      ifTrace
    } else {
      ConditionEvalTrace(
        nodeId = "${action.id}_chain",
        label = action.name.trim().ifBlank { "If / Else If Chain" },
        passed = false,
        actualSummary =
          if (action.elseActions.isNotEmpty()) {
            "No rule matched -> Otherwise (Else)"
          } else {
            "No rule matched"
          },
        expectedSummary = "Evaluated ${branchTraces.size} rules",
        children = branchTraces,
      )
    }

  return IfChainEvaluation(
    branchLabel = if (action.elseActions.isNotEmpty()) "ELSE" else "SKIPPED",
    actionsToRun = action.elseActions,
    conditionTrace = finalTrace,
  )
}
