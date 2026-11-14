package com.tapflow.core.service.engine

import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.action.ClipboardActionType
import com.tapflow.core.model.action.ScriptAction
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

internal object DelayResolver {
  private const val MIN_DELAY_MS = DelayConfig.MIN_DELAY_MS

  fun resolveDelayMs(config: DelayConfig, random: Random = Random.Default): Long {
    return when (val safe = config.clamped()) {
      is DelayConfig.Fixed -> safe.durationMs
      is DelayConfig.RandomRange -> {
        if (safe.minMs == safe.maxMs) safe.minMs
        else random.nextLong(safe.minMs, safe.maxMs + 1L)
      }
      is DelayConfig.Deviation -> {
        val low = (safe.baseMs - safe.deltaMs).coerceAtLeast(MIN_DELAY_MS)
        val high = (safe.baseMs + safe.deltaMs).coerceAtLeast(low)
        if (low == high) low else random.nextLong(low, high + 1L)
      }
    }
  }

  fun resolvePostActionDelayMs(
    action: ScriptAction,
    globalDelay: DelayConfig,
    random: Random = Random.Default,
  ): Long {
    val policy: PostActionDelay =
      when (action) {
        is ScriptAction.Tap -> action.postDelay
        is ScriptAction.DoubleTap -> action.postDelay
        is ScriptAction.LongPress -> action.postDelay
        is ScriptAction.MultiTouch -> action.postDelay
        is ScriptAction.SwipeDrag -> action.postDelay
        is ScriptAction.InputText -> action.postDelay
        is ScriptAction.KeyEvent -> action.postDelay
        is ScriptAction.LaunchApp -> action.postDelay
        is ScriptAction.ForceStopApp -> action.postDelay
        is ScriptAction.ClipboardOp ->
          if (action.operation == ClipboardActionType.PASTE) action.postDelay
          else PostActionDelay.Off

        // Excluded categories: Flow Control, VariableOps, Delay, Vibrate, PlayAlertSound -> strictly 0ms
        is ScriptAction.IfBlock,
        is ScriptAction.LoopBlock,
        is ScriptAction.Delay,
        is ScriptAction.Break,
        is ScriptAction.Continue,
        is ScriptAction.EndScript,
        is ScriptAction.Vibrate,
        is ScriptAction.PlayAlertSound,
        is ScriptAction.VariableOp -> PostActionDelay.Off
      }

    return when (policy) {
      PostActionDelay.Off -> 0L
      PostActionDelay.Global -> resolveDelayMs(globalDelay, random)
      is PostActionDelay.Override -> resolveDelayMs(policy.config, random)
    }
  }

  suspend fun delayPauseAware(
    durationMs: Long,
    pausedState: StateFlow<Boolean>,
  ) {
    if (durationMs <= 0L) return
    var remaining = durationMs
    val sliceMs = 50L
    while (remaining > 0L) {
      if (pausedState.value) {
        pausedState.first { !it }
      }
      val step = minOf(remaining, sliceMs)
      delay(step)
      remaining -= step
    }
  }
}
