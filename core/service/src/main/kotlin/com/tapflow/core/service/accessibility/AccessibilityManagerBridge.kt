package com.tapflow.core.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import com.tapflow.core.model.action.PostTextActionKey
import com.tapflow.core.model.action.SystemKeyType
import com.tapflow.core.service.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

interface AccessibilityExecutorHost {
  fun dispatchGestureCompat(
    gesture: GestureDescription,
    callback: AccessibilityService.GestureResultCallback,
  ): Boolean

  fun performGlobalActionCompat(actionId: Int): Boolean

  fun adjustVolume(raise: Boolean): Boolean

  fun findFocusedEditableNode(): AccessibilityNodeInfo?

  fun findAnyEditableNode(): AccessibilityNodeInfo?

  fun resolveForegroundPackageNow(): String?
}

@Singleton
class AccessibilityManagerBridge @Inject constructor(
  @ApplicationContext private val context: Context,
  private val shizukuManager: ShizukuManager,
) {
  companion object {
    /** Strictly under Android's 60,000ms GestureDescription.getMaxGestureDuration() limit. */
    const val MAX_STROKE_SEGMENT_MS: Long = 50_000L
    private const val JITTER_SEGMENT_MS: Long = 160L
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val strokeMutex = Mutex()

  @Volatile
  private var host: AccessibilityExecutorHost? = null

  @Volatile
  private var lastValidForegroundPackage: String? = null

  private val _isAccessibilityConnected = MutableStateFlow(false)
  val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

  private val _currentForegroundPackage = MutableStateFlow<String?>(null)
  val currentForegroundPackage: StateFlow<String?> = _currentForegroundPackage.asStateFlow()

  private val _hasActiveLongPressStroke = MutableStateFlow(false)
  val hasActiveLongPressStroke: StateFlow<Boolean> = _hasActiveLongPressStroke.asStateFlow()

  private var activeStroke: GestureDescription.StrokeDescription? = null
  private var activeStrokeX: Float = 0f
  private var activeStrokeY: Float = 0f
  private var activeHoldLoopJob: Job? = null

  internal fun attachHost(executorHost: AccessibilityExecutorHost) {
    host = executorHost
    _isAccessibilityConnected.value = true
  }

  internal fun detachHost(executorHost: AccessibilityExecutorHost) {
    if (host === executorHost) {
      activeHoldLoopJob?.cancel()
      activeHoldLoopJob = null
      activeStroke = null
      _hasActiveLongPressStroke.value = false
      host = null
      _isAccessibilityConnected.value = false
    }
  }

  internal fun getEnabledImePackages(): Set<String> {
    val imm =
      context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return emptySet()
    return runCatching {
      imm.enabledInputMethodList
        .flatMap { listOfNotNull(it.packageName, it.serviceInfo?.packageName) }
        .toSet()
    }.getOrDefault(emptySet())
  }

  internal fun isExcludedForegroundPackage(
    packageName: String?,
    enabledImePackages: Set<String> = getEnabledImePackages(),
  ): Boolean {
    if (packageName.isNullOrBlank()) return true
    if (packageName == context.packageName) return true
    if (packageName == SYSTEM_UI_PACKAGE) return true
    if (packageName in enabledImePackages) return true
    return false
  }

  internal fun updateForegroundPackage(packageName: String?) {
    if (!isExcludedForegroundPackage(packageName)) {
      lastValidForegroundPackage = packageName
      _currentForegroundPackage.value = packageName
    }
  }

  suspend fun resolveCurrentForegroundPackage(): String? {
    val imePackages = getEnabledImePackages()
    val fromHost = host?.resolveForegroundPackageNow()
    if (!isExcludedForegroundPackage(fromHost, imePackages)) {
      lastValidForegroundPackage = fromHost
      _currentForegroundPackage.value = fromHost
      return fromHost
    }

    if (shizukuManager.diagnosticState.value.isFullyReady) {
      val fromShizuku = shizukuManager.queryResumedActivityPackage()
      if (!isExcludedForegroundPackage(fromShizuku, imePackages)) {
        lastValidForegroundPackage = fromShizuku
        _currentForegroundPackage.value = fromShizuku
        return fromShizuku
      }
    }

    return lastValidForegroundPackage?.takeUnless { isExcludedForegroundPackage(it, imePackages) }
  }

  suspend fun dispatchGestureAsync(
    gesture: GestureDescription,
    timeoutMs: Long = 60_000L,
  ): Boolean {
    val activeHost = host ?: return false
    val result =
      withTimeoutOrNull(timeoutMs.coerceAtLeast(1_000L)) {
        suspendCancellableCoroutine { continuation ->
          val callback =
            object : AccessibilityService.GestureResultCallback() {
              override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(true)
              }

              override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(false)
              }
            }
          val accepted = activeHost.dispatchGestureCompat(gesture, callback)
          if (!accepted && continuation.isActive) {
            continuation.resume(false)
          }
        }
      }
    return result == true
  }

  suspend fun tap(x: Float, y: Float, pressDurationMs: Long = 50L): Boolean {
    val path = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
    val stroke =
      GestureDescription.StrokeDescription(
        path,
        0L,
        pressDurationMs.coerceIn(1L, MAX_STROKE_SEGMENT_MS),
      )
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGestureAsync(gesture, timeoutMs = pressDurationMs + 5_000L)
  }

  suspend fun doubleTap(
    x: Float,
    y: Float,
    pressDurationMs: Long = 50L,
    tapIntervalMs: Long = 100L,
  ): Boolean {
    val safeX = x.coerceAtLeast(0f)
    val safeY = y.coerceAtLeast(0f)
    val dur = pressDurationMs.coerceIn(1L, 1_000L)
    val gap = tapIntervalMs.coerceIn(20L, 2_000L)
    val first =
      GestureDescription.StrokeDescription(
        Path().apply { moveTo(safeX, safeY) },
        0L,
        dur,
      )
    val second =
      GestureDescription.StrokeDescription(
        Path().apply { moveTo(safeX, safeY) },
        dur + gap,
        dur,
      )
    val gesture = GestureDescription.Builder().addStroke(first).addStroke(second).build()
    return dispatchGestureAsync(gesture, timeoutMs = (dur * 2) + gap + 5_000L)
  }

  suspend fun executeLongPress(
    x: Float,
    y: Float,
    durationMs: Long,
    microMovementJitter: Boolean = false,
    amplitudePx: Int = 3,
  ): Boolean {
    val safeDuration = durationMs.coerceAtLeast(50L)
    if (safeDuration <= MAX_STROKE_SEGMENT_MS && !microMovementJitter) {
      val path = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
      val stroke = GestureDescription.StrokeDescription(path, 0L, safeDuration, false)
      return dispatchGestureAsync(
        GestureDescription.Builder().addStroke(stroke).build(),
        timeoutMs = safeDuration + 5_000L,
      )
    }

    // Long press > 50s or with micro-movement jitter uses continuous stroke chaining
    if (!pressDownOnly(x, y, microMovementJitter, amplitudePx)) {
      return false
    }
    delay(safeDuration)
    return releaseActivePress()
  }

  suspend fun pressDownOnly(
    x: Float,
    y: Float,
    microMovementJitter: Boolean = false,
    amplitudePx: Int = 3,
  ): Boolean =
    strokeMutex.withLock {
      cancelHoldLoopLocked()
      val safeX = x.coerceAtLeast(0f)
      val safeY = y.coerceAtLeast(0f)
      activeStrokeX = safeX
      activeStrokeY = safeY

      val segmentMs = if (microMovementJitter) JITTER_SEGMENT_MS else MAX_STROKE_SEGMENT_MS
      val initialPath = Path().apply { moveTo(safeX, safeY) }
      val initialStroke = GestureDescription.StrokeDescription(initialPath, 0L, segmentMs, true)
      activeStroke = initialStroke
      _hasActiveLongPressStroke.value = true

      val dispatched =
        dispatchGestureAsync(
          GestureDescription.Builder().addStroke(initialStroke).build(),
          timeoutMs = segmentMs + 3_000L,
        )
      if (!dispatched) {
        activeStroke = null
        _hasActiveLongPressStroke.value = false
        return@withLock false
      }

      activeHoldLoopJob =
        scope.launch {
          var prevX = safeX
          var prevY = safeY
          try {
            while (isActive) {
              val current = strokeMutex.withLock { activeStroke } ?: break
              val nextSegMs = if (microMovementJitter) JITTER_SEGMENT_MS else MAX_STROKE_SEGMENT_MS
              val (nextX, nextY) =
                if (microMovementJitter && amplitudePx > 0) {
                  val angle = Random.nextDouble(0.0, Math.PI * 2.0)
                  val r = Random.nextDouble(0.0, amplitudePx.toDouble())
                  (safeX + (cos(angle) * r).toFloat()).coerceAtLeast(0f) to
                    (safeY + (sin(angle) * r).toFloat()).coerceAtLeast(0f)
                } else {
                  safeX to safeY
                }
              val contPath =
                Path().apply {
                  moveTo(prevX, prevY)
                  lineTo(nextX, nextY)
                }
              val nextStroke = current.continueStroke(contPath, 0L, nextSegMs, true)
              strokeMutex.withLock {
                if (activeStroke == null) return@launch
                activeStroke = nextStroke
                activeStrokeX = nextX
                activeStrokeY = nextY
              }
              prevX = nextX
              prevY = nextY
              val ok =
                dispatchGestureAsync(
                  GestureDescription.Builder().addStroke(nextStroke).build(),
                  timeoutMs = nextSegMs + 3_000L,
                )
              if (!ok) break
            }
          } catch (_: CancellationException) {
            // Expected on releaseActivePress()
          }
        }
      true
    }

  suspend fun releaseActivePress(): Boolean =
    strokeMutex.withLock {
      cancelHoldLoopLocked()
      val current = activeStroke ?: return@withLock false
      val releasePath = Path().apply { moveTo(activeStrokeX, activeStrokeY) }
      val releaseStroke = current.continueStroke(releasePath, 0L, 15L, false)
      activeStroke = null
      _hasActiveLongPressStroke.value = false
      dispatchGestureAsync(
        GestureDescription.Builder().addStroke(releaseStroke).build(),
        timeoutMs = 3_000L,
      )
    }

  fun forceReleaseActiveStroke() {
    cancelHoldLoopLocked()
    if (activeStroke == null && !_hasActiveLongPressStroke.value) return
    scope.launch {
      releaseActivePress()
    }
  }

  suspend fun holdUntil(
    x: Float,
    y: Float,
    microMovementJitter: Boolean,
    amplitudePx: Int,
    maxTimeoutMs: Long,
    pollIntervalMs: Long = 120L,
    shouldRelease: suspend () -> Boolean,
  ): Boolean {
    if (!pressDownOnly(x, y, microMovementJitter, amplitudePx)) return false
    val deadline = System.currentTimeMillis() + maxTimeoutMs.coerceAtLeast(100L)
    try {
      while (System.currentTimeMillis() < deadline) {
        if (shouldRelease()) {
          break
        }
        delay(pollIntervalMs.coerceAtLeast(50L))
      }
    } finally {
      releaseActivePress()
    }
    return true
  }

  suspend fun dispatchPathSwipe(
    path: Path,
    durationMs: Long,
  ): Boolean {
    val safeDuration = durationMs.coerceIn(1L, MAX_STROKE_SEGMENT_MS)
    val stroke = GestureDescription.StrokeDescription(path, 0L, safeDuration, false)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGestureAsync(gesture, timeoutMs = safeDuration + 5_000L)
  }

  suspend fun dispatchMultiTouchStrokes(
    strokes: List<GestureDescription.StrokeDescription>,
    maxDurationMs: Long,
  ): Boolean {
    if (strokes.isEmpty()) return false
    val builder = GestureDescription.Builder()
    strokes.take(GestureDescription.getMaxStrokeCount()).forEach { builder.addStroke(it) }
    return dispatchGestureAsync(builder.build(), timeoutMs = maxDurationMs + 5_000L)
  }

  fun performGlobalAction(keyType: SystemKeyType): Boolean {
    val activeHost = host ?: return false
    return when (keyType) {
      SystemKeyType.HOME -> activeHost.performGlobalActionCompat(AccessibilityService.GLOBAL_ACTION_HOME)
      SystemKeyType.BACK -> activeHost.performGlobalActionCompat(AccessibilityService.GLOBAL_ACTION_BACK)
      SystemKeyType.RECENTS -> activeHost.performGlobalActionCompat(AccessibilityService.GLOBAL_ACTION_RECENTS)
      SystemKeyType.POWER_DIALOG -> activeHost.performGlobalActionCompat(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
      SystemKeyType.NOTIFICATIONS -> activeHost.performGlobalActionCompat(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
      SystemKeyType.QUICK_SETTINGS -> activeHost.performGlobalActionCompat(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
      SystemKeyType.TAKE_SCREENSHOT ->
        activeHost.performGlobalActionCompat(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
      SystemKeyType.VOLUME_UP -> activeHost.adjustVolume(raise = true)
      SystemKeyType.VOLUME_DOWN -> activeHost.adjustVolume(raise = false)
    }
  }

  fun setTextOnFocusedNode(text: String, clearExisting: Boolean = false): Boolean {
    val activeHost = host ?: return false
    val target = activeHost.findFocusedEditableNode() ?: activeHost.findAnyEditableNode() ?: return false
    return try {
      val finalText =
        if (clearExisting) {
          text
        } else {
          val existing = target.text?.toString().orEmpty()
          existing + text
        }
      val args =
        Bundle().apply {
          putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
        }
      target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    } finally {
      @Suppress("DEPRECATION")
      target.recycle()
    }
  }

  fun pasteOnFocusedNode(): Boolean {
    val activeHost = host ?: return false
    val target = activeHost.findFocusedEditableNode() ?: activeHost.findAnyEditableNode() ?: return false
    return try {
      target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    } finally {
      @Suppress("DEPRECATION")
      target.recycle()
    }
  }

  suspend fun typeTextProgressively(
    text: String,
    clearExisting: Boolean = false,
  ): Boolean {
    val activeHost = host ?: return false
    val basePrefix =
      if (clearExisting) {
        ""
      } else {
        val node = activeHost.findFocusedEditableNode() ?: activeHost.findAnyEditableNode()
        val existing = node?.text?.toString().orEmpty()
        @Suppress("DEPRECATION")
        node?.recycle()
        existing
      }

    if (text.isEmpty()) {
      return setTextOnFocusedNode("", clearExisting = true)
    }

    var allSucceeded = true
    val builder = StringBuilder(basePrefix)
    for (ch in text) {
      builder.append(ch)
      val node = activeHost.findFocusedEditableNode() ?: activeHost.findAnyEditableNode()
      if (node == null) {
        allSucceeded = false
        break
      }
      val ok =
        try {
          val args =
            Bundle().apply {
              putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                builder.toString(),
              )
            }
          node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
          @Suppress("DEPRECATION")
          node.recycle()
        }
      if (!ok) {
        allSucceeded = false
        break
      }
      delay(Random.nextLong(50L, 121L))
    }
    return allSucceeded
  }

  fun performPostTextImeAction(actionKey: PostTextActionKey): Boolean {
    if (actionKey == PostTextActionKey.NONE) return true
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val activeHost = host ?: return false
    val node = activeHost.findFocusedEditableNode() ?: activeHost.findAnyEditableNode() ?: return false
    return try {
      node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    } finally {
      @Suppress("DEPRECATION")
      node.recycle()
    }
  }

  private fun cancelHoldLoopLocked() {
    activeHoldLoopJob?.cancel()
    activeHoldLoopJob = null
  }
}
