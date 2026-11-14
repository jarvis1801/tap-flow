package com.tapflow.core.service.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.repository.InspectRepository
import com.tapflow.core.database.repository.PipelineRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.action.LongPressMode
import com.tapflow.core.model.action.Point2D
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.action.SwipeTrajectoryMode
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.DurationInput
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import com.tapflow.core.model.script.Script
import com.tapflow.core.service.R
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.engine.AutomationRunnerEngine
import com.tapflow.core.service.engine.PipelineExecutionEngine
import com.tapflow.core.service.engine.RunnerControlHub
import com.tapflow.core.service.engine.RunnerPhase
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.projection.ScreenCaptureState
import com.tapflow.core.service.shizuku.ShizukuManager
import com.tapflow.core.service.vision.ZeroAllocVisionEngine
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Optional
import java.util.UUID
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OverlayService :
  Service(),
  LifecycleOwner,
  SavedStateRegistryOwner,
  ViewModelStoreOwner {

  companion object {
    const val ACTION_START_SCRIPT = "com.tapflow.core.service.overlay.ACTION_START_SCRIPT"
    const val ACTION_START_PIPELINE = "com.tapflow.core.service.overlay.ACTION_START_PIPELINE"
    const val ACTION_START_PICKER = "com.tapflow.core.service.overlay.ACTION_START_PICKER"
    const val ACTION_START_RECORDER = "com.tapflow.core.service.overlay.ACTION_START_RECORDER"
    const val ACTION_START_INSPECTOR = "com.tapflow.core.service.overlay.ACTION_START_INSPECTOR"
    const val ACTION_CLOSE = "com.tapflow.core.service.overlay.ACTION_CLOSE"

    const val EXTRA_TARGET_ID = "extra_target_id"
    const val EXTRA_PICKER_REQUEST_ID = "extra_picker_request_id"
    const val EXTRA_PICKER_TYPE = "extra_picker_type"

    private const val CHANNEL_ID = "tapflow_overlay_dock"
    private const val NOTIFICATION_ID = 3101
    private const val MOVE_THRESHOLD_PX = 24f
    private const val LONG_PRESS_THRESHOLD_MS = 450L
    private const val DEFAULT_INSPECT_CROP_SIZE_PX = 64
  }

  @Inject lateinit var preferencesRepository: TapFlowPreferencesRepository
  @Inject lateinit var scriptRepository: ScriptRepository
  @Inject lateinit var pipelineRepository: PipelineRepository
  @Inject lateinit var inspectRepository: InspectRepository
  @Inject lateinit var scriptAssetManager: ScriptAssetManager
  @Inject lateinit var accessibilityBridge: AccessibilityManagerBridge
  @Inject lateinit var shizukuManager: ShizukuManager
  @Inject lateinit var visionEngine: ZeroAllocVisionEngine
  @Inject lateinit var runnerControlHub: RunnerControlHub
  @Inject lateinit var automationRunnerEngine: AutomationRunnerEngine
  @Inject lateinit var pipelineExecutionEngine: PipelineExecutionEngine
  @Inject lateinit var gesturePickerBridge: GesturePickerBridge
  @Inject lateinit var mediaProjectionBridge: MediaProjectionBridge
  @Inject lateinit var optionalContentProvider: Optional<OverlayContentProvider>

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val savedStateController = SavedStateRegistryController.create(this)
  private val store = ViewModelStore()

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateController.savedStateRegistry

  override val viewModelStore: ViewModelStore
    get() = store

  private lateinit var windowManager: WindowManager
  private var captureRootView: FrameLayout? = null
  private var captureFeedbackComposeView: ComposeView? = null
  private var captureLayoutParams: WindowManager.LayoutParams? = null

  private var dockComposeView: ComposeView? = null
  private var dockLayoutParams: WindowManager.LayoutParams? = null
  private var edgeSnapAnimator: ValueAnimator? = null

  private val uiState = MutableStateFlow(OverlayUiState())

  // Active runner target & Recording / Inspect session accumulators
  private var activeRunnerTargetId: String? = null
  private var activeInspectGroupId: String? = null
  private var activeInspectGroupTitle: String? = null
  private var activeInspectGroupTags: List<String> = emptyList()
  private var inspectGroupPersisted: Boolean = false
  private var recordedActions = mutableListOf<ScriptAction>()
  private var lastRecordedActionCompletedAtMs: Long? = null
  private var isProcessingGesture = false

  private val callbacks =
    object : OverlayCallbacks {
      override fun onPlayPause() {
        val snap = uiState.value.runnerSnapshot
        val targetId = activeRunnerTargetId
        if (targetId != null &&
          (snap.phase == RunnerPhase.COMPLETED ||
            snap.phase == RunnerPhase.FAILED ||
            snap.phase == RunnerPhase.STOPPED ||
            snap.phase == RunnerPhase.IDLE)
        ) {
          when (uiState.value.mode) {
            OverlayMode.RUNNER_SCRIPT -> startScriptRunnerMode(targetId)
            OverlayMode.RUNNER_PIPELINE -> startPipelineRunnerMode(targetId)
            else -> Unit
          }
        } else {
          runnerControlHub.togglePlayPause()
        }
      }

      override fun onPreviousStep() {
        runnerControlHub.requestPreviousStep()
      }

      override fun onNextStep() {
        runnerControlHub.requestNextStep()
      }

      override fun onStopExecution() {
        when (uiState.value.mode) {
          OverlayMode.RUNNER_SCRIPT,
          OverlayMode.RUNNER_PIPELINE -> runnerControlHub.stopExecution()
          OverlayMode.LIVE_RECORDER -> stopRecorderAndSaveSession(closeAfter = false)
          OverlayMode.LIVE_INSPECTOR -> pauseCaptureListening()
          OverlayMode.GESTURE_PICKER -> cancelPickerAndReturnToApp()
          OverlayMode.IDLE -> Unit
        }
      }

      override fun onToggleCaptureListening() {
        when (uiState.value.mode) {
          OverlayMode.GESTURE_PICKER -> {
            val nextActive = !uiState.value.isCaptureActive
            setCaptureLayerInterceptable(visible = nextActive, interceptable = nextActive)
            uiState.update {
              it.copy(
                isCaptureActive = nextActive,
                capturePhase =
                  if (nextActive) OverlayCapturePhase.INTERCEPTING
                  else OverlayCapturePhase.IDLE,
                touchPreview = null,
                statusHint =
                  if (nextActive) "Touch or drag on target location"
                  else "Pick paused — navigate app then tap Inspect",
              )
            }
          }

          OverlayMode.LIVE_RECORDER -> {
            if (uiState.value.isCaptureActive) {
              stopRecorderAndSaveSession(closeAfter = false)
            } else {
              resumeOrRestartRecorderSession()
            }
          }

          OverlayMode.LIVE_INSPECTOR -> {
            val nextActive = !uiState.value.isCaptureActive
            setCaptureLayerInterceptable(visible = nextActive, interceptable = nextActive)
            uiState.update {
              it.copy(
                isCaptureActive = nextActive,
                capturePhase =
                  if (nextActive) OverlayCapturePhase.INTERCEPTING
                  else OverlayCapturePhase.IDLE,
                touchPreview = null,
                statusHint =
                  if (nextActive) "Tap screen to capture inspect targets"
                  else "Inspect paused — tap Inspect to resume",
              )
            }
          }

          else -> Unit
        }
      }

      override fun onToggleBubbleCollapse() {
        uiState.update { it.copy(isBubbleCollapsed = !it.isBubbleCollapsed) }
      }

      override fun onCloseOverlay() {
        when (uiState.value.mode) {
          OverlayMode.LIVE_RECORDER -> stopRecorderAndSaveSession(closeAfter = true)
          OverlayMode.GESTURE_PICKER -> cancelPickerAndReturnToApp()
          else -> {
            runnerControlHub.stopExecution()
            bringMainActivityToFrontPreservingState()
            closeOverlayService()
          }
        }
      }

      override fun onCancelCaptureMode() {
        onStopExecution()
      }

      override fun onDismissCaptureAlert() {
        uiState.update { it.copy(captureAlertMessage = null) }
      }

      override fun onReturnToTapFlow() {
        bringMainActivityToFrontPreservingState()
      }

      override fun onDragDelta(dx: Float, dy: Float) {
        moveDockBy(dx.roundToInt(), dy.roundToInt())
      }

      override fun onDragEnd() {
        snapDockToNearestEdgeAndSave()
      }
    }

  override fun onCreate() {
    super.onCreate()
    savedStateController.performRestore(Bundle())
    lifecycleRegistry.currentState = Lifecycle.State.CREATED
    lifecycleRegistry.currentState = Lifecycle.State.STARTED
    lifecycleRegistry.currentState = Lifecycle.State.RESUMED

    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    ensureNotificationChannel()
    startOverlayForeground(buildNotification())

    attachWindowsInStrictZOrder()

    serviceScope.launch {
      val prefs = preferencesRepository.getPreferencesOnce()
      applySavedDockPosition(prefs.dockPosition.x, prefs.dockPosition.y)
    }

    serviceScope.launch {
      runnerControlHub.snapshot.collectLatest { snapshot ->
        uiState.update { it.copy(runnerSnapshot = snapshot) }
      }
    }

    serviceScope.launch {
      mediaProjectionBridge.captureState.collectLatest { captureState ->
        uiState.update { current ->
          val alert =
            when (captureState) {
              is ScreenCaptureState.StoppedBySystem -> captureState.reason
              is ScreenCaptureState.Error -> captureState.message
              is ScreenCaptureState.Active -> null
              ScreenCaptureState.Idle -> current.captureAlertMessage
            }
          current.copy(
            captureState = captureState,
            captureAlertMessage = alert,
          )
        }
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_CLOSE -> {
        runnerControlHub.stopExecution()
        closeOverlayService()
      }

      ACTION_START_SCRIPT -> {
        val scriptId = intent.getStringExtra(EXTRA_TARGET_ID)
        if (!scriptId.isNullOrBlank()) {
          startScriptRunnerMode(scriptId)
        }
      }

      ACTION_START_PIPELINE -> {
        val pipelineId = intent.getStringExtra(EXTRA_TARGET_ID)
        if (!pipelineId.isNullOrBlank()) {
          startPipelineRunnerMode(pipelineId)
        }
      }

      ACTION_START_PICKER -> {
        val reqId = intent.getStringExtra(EXTRA_PICKER_REQUEST_ID) ?: UUID.randomUUID().toString()
        val typeName = intent.getStringExtra(EXTRA_PICKER_TYPE) ?: GesturePickerTargetType.POINT.name
        val targetType =
          runCatching { GesturePickerTargetType.valueOf(typeName) }
            .getOrDefault(GesturePickerTargetType.POINT)
        startGesturePickerMode(reqId, targetType)
      }

      ACTION_START_RECORDER -> startLiveRecorderMode()
      ACTION_START_INSPECTOR -> startLiveInspectorMode()
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    edgeSnapAnimator?.cancel()
    serviceScope.cancel()
    captureRootView?.let { runCatching { windowManager.removeViewImmediate(it) } }
    dockComposeView?.let { runCatching { windowManager.removeViewImmediate(it) } }
    captureRootView = null
    dockComposeView = null
    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    store.clear()
    super.onDestroy()
  }

  /**
   * Attaches the full-screen capture overlay FIRST and the Floating Control Dock SECOND so that
   * WindowManager Z-Order keeps the Floating Control Dock strictly ABOVE the capture layer at all times.
   */
  @SuppressLint("ClickableViewAccessibility")
  private fun attachWindowsInStrictZOrder() {
    // 1. Bottom Layer: Full-screen transparent capture layer
    val captureFlagsIdle =
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    val cParams =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        captureFlagsIdle,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }

    val feedbackCompose =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
        setContent {
          val state by uiState.collectAsStateWithLifecycle()
          val provider = optionalContentProvider.orElse(null)
          if (provider != null) {
            provider.CaptureLayerFeedbackContent(state)
          } else {
            FallbackCaptureFeedback(state)
          }
        }
      }

    val captureContainer =
      FrameLayout(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
        visibility = View.GONE
        addView(
          feedbackCompose,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          ),
        )
        setOnTouchListener(CaptureTouchInterceptor())
      }

    windowManager.addView(captureContainer, cParams)
    captureRootView = captureContainer
    captureFeedbackComposeView = feedbackCompose
    captureLayoutParams = cParams

    // 2. Top Layer: Floating Control Dock (strictly above capture layer in WindowManager Z-order)
    val dParams =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 240
      }

    val dockView =
      ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
        setContent {
          val state by uiState.collectAsStateWithLifecycle()
          val provider = optionalContentProvider.orElse(null)
          MaterialTheme {
            if (provider != null) {
              provider.FloatingDockContent(state, callbacks)
            } else {
              FallbackFloatingDock(state, callbacks)
            }
          }
        }
      }

    windowManager.addView(dockView, dParams)
    dockComposeView = dockView
    dockLayoutParams = dParams
  }

  private fun setCaptureLayerInterceptable(visible: Boolean, interceptable: Boolean) {
    val view = captureRootView ?: return
    val params = captureLayoutParams ?: return

    view.visibility = if (visible) View.VISIBLE else View.GONE
    val baseFlags =
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    params.flags =
      if (interceptable) {
        baseFlags
      } else {
        baseFlags or
          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
      }
    runCatching { windowManager.updateViewLayout(view, params) }
  }

  private fun startScriptRunnerMode(scriptId: String) {
    activeRunnerTargetId = scriptId
    setCaptureLayerInterceptable(visible = false, interceptable = false)
    uiState.update {
      it.copy(
        mode = OverlayMode.RUNNER_SCRIPT,
        isCaptureActive = false,
        capturePhase = OverlayCapturePhase.IDLE,
        pickerTargetType = null,
        touchPreview = null,
        statusHint = null,
      )
    }
    serviceScope.launch {
      val script = scriptRepository.getScriptById(scriptId)
      if (script == null) {
        uiState.update { it.copy(statusHint = "Script not found") }
        return@launch
      }
      automationRunnerEngine.startScript(script)
    }
  }

  private fun startPipelineRunnerMode(pipelineId: String) {
    activeRunnerTargetId = pipelineId
    setCaptureLayerInterceptable(visible = false, interceptable = false)
    uiState.update {
      it.copy(
        mode = OverlayMode.RUNNER_PIPELINE,
        isCaptureActive = false,
        capturePhase = OverlayCapturePhase.IDLE,
        pickerTargetType = null,
        touchPreview = null,
        statusHint = null,
      )
    }
    serviceScope.launch {
      val pipeline = pipelineRepository.getPipelineById(pipelineId)
      if (pipeline == null) {
        uiState.update { it.copy(statusHint = "Pipeline not found") }
        return@launch
      }
      pipelineExecutionEngine.startPipeline(pipeline)
    }
  }

  private fun startGesturePickerMode(requestId: String, targetType: GesturePickerTargetType) {
    activeRunnerTargetId = null
    gesturePickerBridge.beginPick(requestId, targetType)
    uiState.update {
      it.copy(
        mode = OverlayMode.GESTURE_PICKER,
        isCaptureActive = true,
        capturePhase = OverlayCapturePhase.INTERCEPTING,
        pickerTargetType = targetType,
        touchPreview = null,
        statusHint = "Touch or drag on target location",
      )
    }
    setCaptureLayerInterceptable(visible = true, interceptable = true)
  }

  private fun startLiveRecorderMode() {
    activeRunnerTargetId = null
    recordedActions.clear()
    lastRecordedActionCompletedAtMs = null
    activeInspectGroupId = UUID.randomUUID().toString()
    val stamp = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date())
    activeInspectGroupTitle = "Recorded Session $stamp"
    activeInspectGroupTags = listOf("recorded")
    inspectGroupPersisted = false
    uiState.update {
      it.copy(
        mode = OverlayMode.LIVE_RECORDER,
        isCaptureActive = true,
        capturePhase = OverlayCapturePhase.INTERCEPTING,
        pickerTargetType = null,
        touchPreview = null,
        recordedActionCount = 0,
        lastSavedScriptTitle = null,
        statusHint = "Recording live gestures",
      )
    }
    setCaptureLayerInterceptable(visible = true, interceptable = true)
  }

  private fun resumeOrRestartRecorderSession() {
    recordedActions.clear()
    lastRecordedActionCompletedAtMs = null
    activeInspectGroupId = UUID.randomUUID().toString()
    val stamp = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date())
    activeInspectGroupTitle = "Recorded Session $stamp"
    activeInspectGroupTags = listOf("recorded")
    inspectGroupPersisted = false
    uiState.update {
      it.copy(
        isCaptureActive = true,
        capturePhase = OverlayCapturePhase.INTERCEPTING,
        touchPreview = null,
        recordedActionCount = 0,
        lastSavedScriptTitle = null,
        statusHint = "Recording live gestures",
      )
    }
    setCaptureLayerInterceptable(visible = true, interceptable = true)
  }

  private fun startLiveInspectorMode() {
    activeRunnerTargetId = null
    activeInspectGroupId = UUID.randomUUID().toString()
    activeInspectGroupTitle = null
    activeInspectGroupTags = listOf("live_inspect")
    inspectGroupPersisted = false
    uiState.update {
      it.copy(
        mode = OverlayMode.LIVE_INSPECTOR,
        isCaptureActive = true,
        capturePhase = OverlayCapturePhase.INTERCEPTING,
        pickerTargetType = null,
        touchPreview = null,
        inspectedItemCount = 0,
        statusHint = "Tap screen to capture inspect targets",
      )
    }
    serviceScope.launch {
      val existingCount = inspectRepository.countGroups()
      activeInspectGroupTitle = "InspectGroup (${existingCount + 1})"
    }
    setCaptureLayerInterceptable(visible = true, interceptable = true)
  }

  private inner class CaptureTouchInterceptor : View.OnTouchListener {
    private val pathPoints = mutableListOf<Point2D>()
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTimeMs = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent): Boolean {
      if (isProcessingGesture || !uiState.value.isCaptureActive) return true
      val mode = uiState.value.mode
      if (mode != OverlayMode.GESTURE_PICKER &&
        mode != OverlayMode.LIVE_RECORDER &&
        mode != OverlayMode.LIVE_INSPECTOR
      ) {
        return false
      }

      // Strictly read physical rawX / rawY coordinates to avoid Status Bar offsets
      val rx = event.rawX.coerceAtLeast(0f)
      val ry = event.rawY.coerceAtLeast(0f)
      val secondaryPoints = extractSecondaryRawPoints(event, rx, ry)

      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          pathPoints.clear()
          downRawX = rx
          downRawY = ry
          downTimeMs = SystemClock.uptimeMillis()
          pathPoints += Point2D(rx.roundToInt(), ry.roundToInt())
          uiState.update {
            it.copy(
              touchPreview =
                OverlayTouchPreview(
                  isTouching = true,
                  startX = rx,
                  startY = ry,
                  currentX = rx,
                  currentY = ry,
                  pathPoints = pathPoints.toList(),
                  secondaryPoints = secondaryPoints,
                  durationMs = 0L,
                ),
            )
          }
          return true
        }

        MotionEvent.ACTION_POINTER_DOWN,
        MotionEvent.ACTION_POINTER_UP -> {
          val elapsed = (SystemClock.uptimeMillis() - downTimeMs).coerceAtLeast(0L)
          uiState.update {
            it.copy(
              touchPreview =
                OverlayTouchPreview(
                  isTouching = true,
                  startX = downRawX,
                  startY = downRawY,
                  currentX = rx,
                  currentY = ry,
                  pathPoints = pathPoints.toList(),
                  secondaryPoints = secondaryPoints,
                  durationMs = elapsed,
                  isLongPress = elapsed >= LONG_PRESS_THRESHOLD_MS,
                ),
            )
          }
          return true
        }

        MotionEvent.ACTION_MOVE -> {
          val last = pathPoints.lastOrNull()
          val px = rx.roundToInt()
          val py = ry.roundToInt()
          if (last == null || hypot((px - last.x).toDouble(), (py - last.y).toDouble()) >= 6.0) {
            pathPoints += Point2D(px, py)
          }
          val elapsed = (SystemClock.uptimeMillis() - downTimeMs).coerceAtLeast(0L)
          val dist = hypot((rx - downRawX).toDouble(), (ry - downRawY).toDouble()).toFloat()
          val isDrag = dist >= MOVE_THRESHOLD_PX
          uiState.update {
            it.copy(
              touchPreview =
                OverlayTouchPreview(
                  isTouching = true,
                  startX = downRawX,
                  startY = downRawY,
                  currentX = rx,
                  currentY = ry,
                  pathPoints = pathPoints.toList(),
                  secondaryPoints = secondaryPoints,
                  durationMs = elapsed,
                  isLongPress = !isDrag && elapsed >= LONG_PRESS_THRESHOLD_MS,
                  isDrag = isDrag,
                ),
            )
          }
          return true
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          val endX = rx.roundToInt()
          val endY = ry.roundToInt()
          val startX = downRawX.roundToInt()
          val startY = downRawY.roundToInt()
          if (pathPoints.lastOrNull() != Point2D(endX, endY)) {
            pathPoints += Point2D(endX, endY)
          }
          val durationMs = (SystemClock.uptimeMillis() - downTimeMs).coerceAtLeast(50L)
          val capturedPoints = pathPoints.toList()
          uiState.update { it.copy(touchPreview = null) }
          isProcessingGesture = true
          serviceScope.launch {
            try {
              execute5StepStateMachine(
                mode = mode,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                points = capturedPoints,
                durationMs = durationMs,
              )
            } finally {
              isProcessingGesture = false
            }
          }
          return true
        }
      }
      return false
    }

    private fun extractSecondaryRawPoints(
      event: MotionEvent,
      primaryRawX: Float,
      primaryRawY: Float,
    ): List<Point2D> {
      if (event.pointerCount <= 1) return emptyList()
      val offsetX = primaryRawX - event.getX(0)
      val offsetY = primaryRawY - event.getY(0)
      return buildList {
        for (i in 1 until event.pointerCount) {
          val sx = (event.getX(i) + offsetX).coerceAtLeast(0f).roundToInt()
          val sy = (event.getY(i) + offsetY).coerceAtLeast(0f).roundToInt()
          add(Point2D(sx, sy))
        }
      }
    }
  }

  /**
   * 5-Step Intercept-Capture-Disable-Replay-Restore State Machine for Modes 2, 3, and 4.
   */
  private suspend fun execute5StepStateMachine(
    mode: OverlayMode,
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    points: List<Point2D>,
    durationMs: Long,
  ) {
    val prefs = preferencesRepository.getPreferencesOnce()
    val distance = hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat()
    val isDrag = distance >= MOVE_THRESHOLD_PX
    val isLongPress = !isDrag && durationMs >= LONG_PRESS_THRESHOLD_MS

    // Step 1 completed -> Step 2: Pre-Replay Capture (Modes 3 & 4) with highlights hidden
    var preReplayBitmap: Bitmap? = null
    var capturedForegroundPkg = ""
    if (mode == OverlayMode.LIVE_RECORDER || mode == OverlayMode.LIVE_INSPECTOR) {
      uiState.update {
        it.copy(
          capturePhase = OverlayCapturePhase.CAPTURING_CLEAN_FRAME,
          touchPreview = null,
        )
      }
      capturedForegroundPkg = accessibilityBridge.resolveCurrentForegroundPackage() ?: ""
      captureFeedbackComposeView?.visibility = View.INVISIBLE
      try {
        delay(40L)
        preReplayBitmap = visionEngine.captureCleanBitmap(useShizukuFallback = prefs.shizuku.silentScreenshots)
      } finally {
        captureFeedbackComposeView?.visibility = View.VISIBLE
      }
    }

    // Step 3: Disable Passthrough (set FLAG_NOT_TOUCHABLE on capture layer)
    uiState.update { it.copy(capturePhase = OverlayCapturePhase.PASSTHROUGH_DISABLED) }
    setCaptureLayerInterceptable(visible = true, interceptable = false)

    if (mode == OverlayMode.GESTURE_PICKER) {
      // Mode 2 publishes picked result and re-fronts TapFlow MainActivity cleanly without losing sheet state
      gesturePickerBridge.publishResult(
        startX = startX,
        startY = startY,
        endX = endX,
        endY = endY,
        pathPoints = points,
        durationMs = durationMs,
      )
      setCaptureLayerInterceptable(visible = false, interceptable = false)
      uiState.update {
        it.copy(
          mode = OverlayMode.IDLE,
          capturePhase = OverlayCapturePhase.IDLE,
          statusHint = null,
        )
      }
      bringMainActivityToFrontPreservingState()
      closeOverlayService()
      return
    }

    // Step 4: Replay Gesture onto underlying app via Accessibility / Shizuku
    uiState.update { it.copy(capturePhase = OverlayCapturePhase.REPLAYING) }
    val useShizuku = prefs.shizuku.shellInputInjection && shizukuManager.diagnosticState.value.isFullyReady
    replayGestureToTargetApp(
      startX = startX,
      startY = startY,
      endX = endX,
      endY = endY,
      points = points,
      durationMs = durationMs,
      isDrag = isDrag,
      isLongPress = isLongPress,
      useShizuku = useShizuku,
    )

    // Persist recorded step or inspect snapshot cleanly off-thread
    if (mode == OverlayMode.LIVE_RECORDER) {
      persistRecordedGestureAndInspect(
        bitmap = preReplayBitmap,
        foregroundPackage = capturedForegroundPkg,
        startX = startX,
        startY = startY,
        endX = endX,
        endY = endY,
        points = points,
        durationMs = durationMs,
        isDrag = isDrag,
        isLongPress = isLongPress,
      )
    } else if (mode == OverlayMode.LIVE_INSPECTOR) {
      persistLiveInspectItem(
        bitmap = preReplayBitmap,
        foregroundPackage = capturedForegroundPkg,
        tapX = startX,
        tapY = startY,
      )
    } else {
      preReplayBitmap?.recycle()
    }

    // Step 5: Restore interceptable state for next gesture (Modes 3 & 4) if capture is still active
    if (uiState.value.isCaptureActive) {
      uiState.update { it.copy(capturePhase = OverlayCapturePhase.RESTORING) }
      setCaptureLayerInterceptable(visible = true, interceptable = true)
      uiState.update { it.copy(capturePhase = OverlayCapturePhase.INTERCEPTING) }
    } else {
      setCaptureLayerInterceptable(visible = false, interceptable = false)
      uiState.update { it.copy(capturePhase = OverlayCapturePhase.IDLE) }
    }
  }

  private suspend fun replayGestureToTargetApp(
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    points: List<Point2D>,
    durationMs: Long,
    isDrag: Boolean,
    isLongPress: Boolean,
    useShizuku: Boolean,
  ) {
    when {
      isDrag -> {
        if (useShizuku && points.size <= 2) {
          shizukuManager.swipe(startX, startY, endX, endY, durationMs)
        } else {
          val path =
            Path().apply {
              moveTo(startX.toFloat(), startY.toFloat())
              for (i in 1 until points.size) {
                lineTo(points[i].x.toFloat(), points[i].y.toFloat())
              }
            }
          accessibilityBridge.dispatchPathSwipe(path, durationMs.coerceIn(60L, 10_000L))
        }
      }

      isLongPress -> {
        accessibilityBridge.executeLongPress(
          x = startX.toFloat(),
          y = startY.toFloat(),
          durationMs = durationMs.coerceIn(LONG_PRESS_THRESHOLD_MS, 20_000L),
        )
      }

      else -> {
        if (useShizuku) {
          shizukuManager.tap(startX, startY)
        } else {
          accessibilityBridge.tap(startX.toFloat(), startY.toFloat(), durationMs.coerceIn(40L, 250L))
        }
      }
    }
  }

  private suspend fun persistRecordedGestureAndInspect(
    bitmap: Bitmap?,
    foregroundPackage: String,
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    points: List<Point2D>,
    durationMs: Long,
    isDrag: Boolean,
    isLongPress: Boolean,
  ) {
    val now = System.currentTimeMillis()
    // Inter-action delay timer starts ONLY after the 1st gesture executes
    lastRecordedActionCompletedAtMs?.let { prevDone ->
      val gapMs = (now - prevDone).coerceAtLeast(DelayConfig.MIN_STANDALONE_DELAY_MS)
      recordedActions +=
        ScriptAction.Delay(
          id = UUID.randomUUID().toString(),
          config = DelayConfig.Fixed(gapMs),
        )
    }

    val action: ScriptAction =
      when {
        isDrag ->
          ScriptAction.SwipeDrag(
            id = UUID.randomUUID().toString(),
            startX = NumericInput.Literal(startX),
            startY = NumericInput.Literal(startY),
            endX = NumericInput.Literal(endX),
            endY = NumericInput.Literal(endY),
            durationMs = durationMs.coerceIn(60L, 10_000L),
            trajectoryMode =
              if (points.size > 4) SwipeTrajectoryMode.MULTI_POINT_PATH
              else SwipeTrajectoryMode.LINEAR,
            multiPointPath = if (points.size > 4) points else emptyList(),
          )
        isLongPress ->
          ScriptAction.LongPress(
            id = UUID.randomUUID().toString(),
            x = NumericInput.Literal(startX),
            y = NumericInput.Literal(startY),
            pressDurationMs = durationMs.coerceAtLeast(LONG_PRESS_THRESHOLD_MS),
            mode = LongPressMode.STANDARD,
          )
        else ->
          ScriptAction.Tap(
            id = UUID.randomUUID().toString(),
            x = NumericInput.Literal(startX),
            y = NumericInput.Literal(startY),
            pressDuration = DurationInput.Fixed(durationMs.coerceIn(40L, 200L)),
          )
      }
    recordedActions += action
    lastRecordedActionCompletedAtMs = System.currentTimeMillis()
    uiState.update { it.copy(recordedActionCount = recordedActions.size) }

    if (bitmap != null) {
      try {
        val groupId = activeInspectGroupId ?: return
        saveInspectSnapshotForTap(
          bitmap = bitmap,
          groupId = groupId,
          foregroundPackage = foregroundPackage,
          tapX = startX,
          tapY = startY,
          includeDefaultCrop = false,
        )
      } finally {
        bitmap.recycle()
      }
    }
  }

  private suspend fun persistLiveInspectItem(
    bitmap: Bitmap?,
    foregroundPackage: String,
    tapX: Int,
    tapY: Int,
  ) {
    if (bitmap == null) return
    try {
      val groupId = activeInspectGroupId ?: return
      saveInspectSnapshotForTap(
        bitmap = bitmap,
        groupId = groupId,
        foregroundPackage = foregroundPackage,
        tapX = tapX,
        tapY = tapY,
        includeDefaultCrop = true,
      )
      uiState.update { it.copy(inspectedItemCount = it.inspectedItemCount + 1) }
    } finally {
      bitmap.recycle()
    }
  }

  private suspend fun saveInspectSnapshotForTap(
    bitmap: Bitmap,
    groupId: String,
    foregroundPackage: String,
    tapX: Int,
    tapY: Int,
    includeDefaultCrop: Boolean,
  ) {
    ensureInspectGroupPersisted(groupId)
    val itemId = UUID.randomUUID().toString()
    val fullScreenFile = File(scriptAssetManager.getInspectScreensDir(), "scr_${itemId}.png")
    if (!visionEngine.saveLosslessPng(bitmap, fullScreenFile)) return

    val safeX = tapX.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
    val safeY = tapY.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
    val sampledPixel = bitmap.getPixel(safeX, safeY)
    val hexColor = String.format(Locale.US, "#%06X", sampledPixel and 0x00FFFFFF)

    val pixelEntry =
      InspectPixel(
        id = UUID.randomUUID().toString(),
        label = "Pixel ($safeX, $safeY)",
        x = safeX,
        y = safeY,
        hexColor = hexColor,
      )

    val crops =
      if (includeDefaultCrop) {
        val cropSize = DEFAULT_INSPECT_CROP_SIZE_PX
        val cropX = (safeX - cropSize / 2).coerceIn(0, (bitmap.width - cropSize).coerceAtLeast(0))
        val cropY = (safeY - cropSize / 2).coerceIn(0, (bitmap.height - cropSize).coerceAtLeast(0))
        val cropW = cropSize.coerceAtMost(bitmap.width)
        val cropH = cropSize.coerceAtMost(bitmap.height)
        val cropFile = File(scriptAssetManager.getInspectCropsDir(), "crop_${itemId}.png")
        if (visionEngine.cropAndSaveLosslessPng(bitmap, cropX, cropY, cropW, cropH, cropFile)) {
          listOf(
            InspectCrop(
              id = UUID.randomUUID().toString(),
              label = "Target ${cropW}x${cropH}",
              cropPngPath = cropFile.absolutePath,
              x = cropX,
              y = cropY,
              width = cropW,
              height = cropH,
            ),
          )
        } else {
          emptyList()
        }
      } else {
        emptyList()
      }

    val pkg = foregroundPackage
    val appLabel = resolveAppLabel(pkg)

    inspectRepository.saveItem(
      InspectItem(
        id = itemId,
        groupId = groupId,
        fullScreenPngPath = fullScreenFile.absolutePath,
        screenWidth = bitmap.width,
        screenHeight = bitmap.height,
        appName = appLabel,
        packageName = pkg,
        pixels = listOf(pixelEntry),
        crops = crops,
        createdAtMs = System.currentTimeMillis(),
      ),
    )
  }

  private suspend fun ensureInspectGroupPersisted(groupId: String) {
    if (inspectGroupPersisted) return
    val existingCount = inspectRepository.countGroups()
    val resolvedTitle =
      activeInspectGroupTitle ?: "InspectGroup (${existingCount + 1})"
    inspectRepository.saveGroup(
      InspectGroup(
        id = groupId,
        title = resolvedTitle,
        tags = activeInspectGroupTags,
        createdAtMs = System.currentTimeMillis(),
      ),
    )
    inspectGroupPersisted = true
  }

  private fun resolveAppLabel(packageName: String): String {
    if (packageName.isBlank()) return "Screen Capture"
    return runCatching {
      val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(0L),
          )
        } else {
          @Suppress("DEPRECATION")
          packageManager.getApplicationInfo(packageName, 0)
        }
      packageManager.getApplicationLabel(info).toString().ifBlank { packageName }
    }.getOrDefault(packageName)
  }

  private fun stopRecorderAndSaveSession(closeAfter: Boolean) {
    val captured = recordedActions.toList()
    recordedActions.clear()
    lastRecordedActionCompletedAtMs = null

    setCaptureLayerInterceptable(visible = false, interceptable = false)
    uiState.update {
      it.copy(
        isCaptureActive = false,
        capturePhase = OverlayCapturePhase.IDLE,
        touchPreview = null,
      )
    }

    serviceScope.launch {
      var savedTitle: String? = null
      if (captured.isNotEmpty()) {
        val stamp = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date())
        savedTitle = "Recorded Script $stamp"
        scriptRepository.saveScript(
          Script(
            id = UUID.randomUUID().toString(),
            title = savedTitle,
            description = "Captured via Floating Recorder",
            tags = listOf("recorded"),
            actions = captured,
          ),
        )
      }
      if (closeAfter) {
        bringMainActivityToFrontPreservingState()
        closeOverlayService()
      } else {
        uiState.update {
          it.copy(
            lastSavedScriptTitle = savedTitle,
            statusHint =
              if (savedTitle != null) "Saved \"$savedTitle\" (${captured.size} steps)"
              else "Recording stopped — tap Record to start again",
          )
        }
      }
    }
  }

  private fun pauseCaptureListening() {
    setCaptureLayerInterceptable(visible = false, interceptable = false)
    uiState.update {
      it.copy(
        isCaptureActive = false,
        capturePhase = OverlayCapturePhase.IDLE,
        touchPreview = null,
        statusHint = "Inspect paused — tap Inspect to resume",
      )
    }
  }

  private fun cancelPickerAndReturnToApp() {
    gesturePickerBridge.cancelPendingPick()
    setCaptureLayerInterceptable(visible = false, interceptable = false)
    bringMainActivityToFrontPreservingState()
    closeOverlayService()
  }

  private fun closeOverlayService() {
    setCaptureLayerInterceptable(visible = false, interceptable = false)
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun bringMainActivityToFrontPreservingState() {
    val launchIntent =
      packageManager.getLaunchIntentForPackage(packageName)?.apply {
        flags =
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
      } ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val pendingIntent =
        PendingIntent.getActivity(
          this,
          2025,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      val options =
        ActivityOptions.makeBasic()
          .setPendingIntentBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
          )
          .toBundle()
      runCatching { pendingIntent.send(this, 0, null, null, null, null, options) }
        .onFailure { runCatching { startActivity(launchIntent) } }
    } else {
      runCatching { startActivity(launchIntent) }
    }
  }

  private fun applySavedDockPosition(savedX: Int, savedY: Int) {
    val view = dockComposeView ?: return
    val params = dockLayoutParams ?: return
    val (screenW, screenH) = readScreenDimensions()
    val maxY = (screenH - 160).coerceAtLeast(0)
    params.x = savedX.coerceIn(0, (screenW - 80).coerceAtLeast(0))
    params.y = savedY.coerceIn(0, maxY)
    val onRight = params.x > (screenW / 2)
    uiState.update { it.copy(isDockedOnRight = onRight) }
    runCatching { windowManager.updateViewLayout(view, params) }
  }

  private fun moveDockBy(dx: Int, dy: Int) {
    edgeSnapAnimator?.cancel()
    val view = dockComposeView ?: return
    val params = dockLayoutParams ?: return
    val (screenW, screenH) = readScreenDimensions()
    val maxX = (screenW - view.width.coerceAtLeast(64)).coerceAtLeast(0)
    val maxY = (screenH - view.height.coerceAtLeast(64)).coerceAtLeast(0)
    params.x = (params.x + dx).coerceIn(0, maxX)
    params.y = (params.y + dy).coerceIn(0, maxY)
    runCatching { windowManager.updateViewLayout(view, params) }
  }

  private fun snapDockToNearestEdgeAndSave() {
    val view = dockComposeView ?: return
    val params = dockLayoutParams ?: return
    val (screenW, _) = readScreenDimensions()
    val viewWidth = view.width.coerceAtLeast(64)
    val centerX = params.x + (viewWidth / 2)
    val snapRight = centerX >= (screenW / 2)
    val targetX = if (snapRight) (screenW - viewWidth).coerceAtLeast(0) else 0
    uiState.update { it.copy(isDockedOnRight = snapRight) }

    edgeSnapAnimator?.cancel()
    edgeSnapAnimator =
      ValueAnimator.ofInt(params.x, targetX).apply {
        duration = 180L
        interpolator = DecelerateInterpolator()
        addUpdateListener { anim ->
          params.x = anim.animatedValue as Int
          runCatching { windowManager.updateViewLayout(view, params) }
        }
        start()
      }

    val persistedY = params.y
    serviceScope.launch {
      preferencesRepository.saveFloatingDockPosition(targetX, persistedY)
    }
  }

  private fun readScreenDimensions(): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = windowManager.currentWindowMetrics.bounds
      return bounds.width() to bounds.height()
    }
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay?.getRealMetrics(metrics)
    return metrics.widthPixels to metrics.heightPixels
  }

  private fun startOverlayForeground(notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun ensureNotificationChannel() {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.overlay_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.overlay_channel_description)
          setShowBadge(false)
        }
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val closePendingIntent =
      PendingIntent.getService(
        this,
        1,
        Intent(this, OverlayService::class.java).apply { action = ACTION_CLOSE },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_tapflow_capture_notification)
      .setContentTitle(getString(R.string.overlay_notification_title))
      .setContentText(getString(R.string.overlay_notification_text))
      .setOngoing(true)
      .setSilent(true)
      .addAction(0, getString(R.string.overlay_stop_action), closePendingIntent)
      .build()
  }
}

@Composable
private fun FallbackFloatingDock(
  state: OverlayUiState,
  callbacks: OverlayCallbacks,
) {
  val dragModifier =
    Modifier.pointerInput(Unit) {
      detectDragGestures(
        onDrag = { change, dragAmount ->
          change.consume()
          callbacks.onDragDelta(dragAmount.x, dragAmount.y)
        },
        onDragEnd = { callbacks.onDragEnd() },
        onDragCancel = { callbacks.onDragEnd() },
      )
    }

  if (state.isBubbleCollapsed) {
    Surface(
      modifier = dragModifier.size(48.dp),
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer,
      shadowElevation = 6.dp,
      onClick = { callbacks.onToggleBubbleCollapse() },
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = "TF",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    return
  }

  Surface(
    modifier = dragModifier,
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
    shadowElevation = 8.dp,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (state.mode == OverlayMode.RUNNER_SCRIPT || state.mode == OverlayMode.RUNNER_PIPELINE) {
        val snap = state.runnerSnapshot
        Text(
          text = "${snap.currentTopLevelAction}/${snap.totalTopLevelActions}",
          style = MaterialTheme.typography.labelSmall,
        )
        IconButton(onClick = { callbacks.onPlayPause() }, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector =
              if (snap.phase == RunnerPhase.RUNNING) Icons.Default.Pause
              else Icons.Default.PlayArrow,
            contentDescription = "Play or Pause",
          )
        }
        IconButton(onClick = { callbacks.onPreviousStep() }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Step")
        }
        IconButton(onClick = { callbacks.onNextStep() }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.SkipNext, contentDescription = "Next Step")
        }
        IconButton(onClick = { callbacks.onStopExecution() }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.Stop, contentDescription = "Stop")
        }
      } else {
        Text(
          text =
            when (state.mode) {
              OverlayMode.GESTURE_PICKER -> "Pick"
              OverlayMode.LIVE_RECORDER -> "Rec ${state.recordedActionCount}"
              OverlayMode.LIVE_INSPECTOR -> "Ins ${state.inspectedItemCount}"
              else -> "Idle"
            },
          style = MaterialTheme.typography.labelSmall,
        )
        Row {
          IconButton(onClick = { callbacks.onStopExecution() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Stop, contentDescription = "Finish")
          }
        }
      }
      IconButton(onClick = { callbacks.onCloseOverlay() }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.Close, contentDescription = "Close Overlay")
      }
    }
  }
}

@Composable
private fun FallbackCaptureFeedback(state: OverlayUiState) {
  if (state.capturePhase == OverlayCapturePhase.CAPTURING_CLEAN_FRAME) {
    return
  }
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.08f)),
    contentAlignment = Alignment.TopCenter,
  ) {
    state.statusHint?.let { hint ->
      Surface(
        modifier = Modifier.padding(top = 48.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
      ) {
        Text(
          text = hint,
          color = MaterialTheme.colorScheme.inverseOnSurface,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
      }
    }
  }
}
