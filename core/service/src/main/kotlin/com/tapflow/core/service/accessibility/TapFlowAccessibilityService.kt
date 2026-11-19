package com.tapflow.core.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.service.R
import com.tapflow.core.service.engine.RunnerControlHub
import com.tapflow.core.service.engine.RunnerPhase
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TapFlowAccessibilityService : AccessibilityService(), AccessibilityExecutorHost {

  @Inject lateinit var bridge: AccessibilityManagerBridge
  @Inject lateinit var preferencesRepository: TapFlowPreferencesRepository
  @Inject lateinit var runnerControlHub: RunnerControlHub

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val screenshotExecutor: ExecutorService = Executors.newSingleThreadExecutor()
  private var preferencesJob: Job? = null

  @Volatile
  private var volumeStopEnabled: Boolean = false

  override fun onServiceConnected() {
    super.onServiceConnected()
    serviceInfo =
      serviceInfo.apply {
        eventTypes =
          AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        flags =
          flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
            AccessibilityServiceInfo.DEFAULT
        notificationTimeout = 50L
      }
    bridge.attachHost(this)
    preferencesJob?.cancel()
    preferencesJob =
      serviceScope.launch {
        preferencesRepository.preferencesFlow
          .map { it.overlay.volumeStopEnabled }
          .distinctUntilChanged()
          .collect { enabled ->
            volumeStopEnabled = enabled
          }
      }
    resolveForegroundPackageNow()?.let { bridge.updateForegroundPackage(it) }
  }

  override fun onKeyEvent(event: KeyEvent?): Boolean {
    if (event == null || !volumeStopEnabled) return false
    if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN || event.action != KeyEvent.ACTION_DOWN) {
      return false
    }
    if (runnerControlHub.snapshot.value.phase != RunnerPhase.RUNNING) {
      return false
    }
    if (bridge.isSelfVolumeKeyDispatchSuppressed()) {
      return false
    }
    if (event.repeatCount == 0) {
      bridge.forceReleaseActiveStroke()
      runnerControlHub.pause("Paused by Volume Down")
      Toast.makeText(
        this,
        getString(R.string.emergency_volume_stop_toast),
        Toast.LENGTH_SHORT,
      ).show()
    }
    return true
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val eventType = event?.eventType ?: return
    if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
      eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
    ) {
      return
    }

    val resolved = resolveForegroundPackageNow()
    if (resolved != null) {
      bridge.updateForegroundPackage(resolved)
      return
    }

    if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      val eventPkg = event.packageName?.toString()
      if (!bridge.isExcludedForegroundPackage(eventPkg) && !isNonApplicationWindowId(event.windowId)) {
        bridge.updateForegroundPackage(eventPkg)
      }
    }
  }

  override fun resolveForegroundPackageNow(): String? {
    val imePackages = bridge.getEnabledImePackages()

    val appWindows =
      runCatching { windows }
        .getOrNull()
        ?.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        ?.sortedByDescending { it.layer }
        .orEmpty()

    for (window in appWindows) {
      val root = runCatching { window.root }.getOrNull() ?: continue
      val pkg =
        try {
          root.packageName?.toString()
        } finally {
          recycleNodeCompat(root)
        }
      if (!bridge.isExcludedForegroundPackage(pkg, imePackages)) {
        return pkg
      }
    }

    val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
    if (activeRoot != null) {
      val pkg =
        try {
          activeRoot.packageName?.toString()
        } finally {
          recycleNodeCompat(activeRoot)
        }
      if (!bridge.isExcludedForegroundPackage(pkg, imePackages)) {
        return pkg
      }
    }

    return null
  }

  private fun isNonApplicationWindowId(windowId: Int): Boolean {
    if (windowId < 0) return false
    val matched =
      runCatching { windows }
        .getOrNull()
        ?.firstOrNull { it.id == windowId }
        ?: return false
    return matched.type != AccessibilityWindowInfo.TYPE_APPLICATION
  }

  private fun recycleNodeCompat(node: AccessibilityNodeInfo?) {
    if (node == null) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      @Suppress("DEPRECATION")
      runCatching { node.recycle() }
    }
  }

  override fun onInterrupt() {
    // No-op: gesture callbacks handle cancellation per-gesture.
  }

  override fun onUnbind(intent: Intent?): Boolean {
    preferencesJob?.cancel()
    preferencesJob = null
    bridge.detachHost(this)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    preferencesJob?.cancel()
    preferencesJob = null
    serviceScope.cancel()
    screenshotExecutor.shutdown()
    bridge.detachHost(this)
    super.onDestroy()
  }

  override fun dispatchGestureCompat(
    gesture: GestureDescription,
    callback: GestureResultCallback,
  ): Boolean {
    return dispatchGesture(gesture, callback, null)
  }

  override fun performGlobalActionCompat(actionId: Int): Boolean {
    return performGlobalAction(actionId)
  }

  override fun adjustVolume(raise: Boolean): Boolean {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    return true
  }

  override fun takeScreenshotCompat(onResult: (Bitmap?) -> Unit): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return false
    }
    return runCatching {
      takeScreenshot(
        Display.DEFAULT_DISPLAY,
        screenshotExecutor,
        object : TakeScreenshotCallback {
          override fun onSuccess(screenshot: ScreenshotResult) {
            val bitmap =
              try {
                val hwBitmap =
                  Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                if (hwBitmap != null) {
                  try {
                    hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                  } finally {
                    hwBitmap.recycle()
                  }
                } else {
                  null
                }
              } catch (_: Throwable) {
                null
              } finally {
                runCatching { screenshot.hardwareBuffer.close() }
              }
            onResult(bitmap)
          }

          override fun onFailure(errorCode: Int) {
            onResult(null)
          }
        },
      )
      true
    }.getOrDefault(false)
  }

  override fun findFocusedEditableNode(): AccessibilityNodeInfo? {
    val root = rootInActiveWindow ?: return null
    val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    if (focused != null && focused.isEditable) {
      @Suppress("DEPRECATION")
      root.recycle()
      return focused
    }
    @Suppress("DEPRECATION")
    focused?.recycle()
    @Suppress("DEPRECATION")
    root.recycle()
    return null
  }

  override fun findAnyEditableNode(): AccessibilityNodeInfo? {
    val root = rootInActiveWindow ?: return null
    return findEditableRecursive(root).also { found ->
      if (found !== root) {
        @Suppress("DEPRECATION")
        root.recycle()
      }
    }
  }

  private fun findEditableRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    if (node.isEditable && node.isVisibleToUser) {
      return node
    }
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      val match = findEditableRecursive(child)
      if (match != null) {
        if (match !== child) {
          @Suppress("DEPRECATION")
          child.recycle()
        }
        return match
      }
      @Suppress("DEPRECATION")
      child.recycle()
    }
    return null
  }
}
