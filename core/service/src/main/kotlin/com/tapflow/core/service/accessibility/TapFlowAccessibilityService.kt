package com.tapflow.core.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TapFlowAccessibilityService : AccessibilityService(), AccessibilityExecutorHost {

  @Inject lateinit var bridge: AccessibilityManagerBridge

  override fun onServiceConnected() {
    super.onServiceConnected()
    serviceInfo =
      serviceInfo.apply {
        eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        flags =
          flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.DEFAULT
        notificationTimeout = 50L
      }
    bridge.attachHost(this)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      val pkg = event.packageName?.toString()
      if (!pkg.isNullOrBlank()) {
        bridge.updateForegroundPackage(pkg)
      }
    }
  }

  override fun onInterrupt() {
    // No-op: gesture callbacks handle cancellation per-gesture.
  }

  override fun onUnbind(intent: Intent?): Boolean {
    bridge.detachHost(this)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
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
