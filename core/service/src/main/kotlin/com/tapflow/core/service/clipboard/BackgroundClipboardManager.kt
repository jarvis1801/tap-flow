package com.tapflow.core.service.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.tapflow.core.service.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Singleton
class BackgroundClipboardManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val shizukuManager: ShizukuManager,
) {
  private val clipboardManager: ClipboardManager?
    get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

  suspend fun copyText(text: String, label: String = "TapFlow"): Boolean =
    withContext(Dispatchers.Main) {
      runCatching {
        val cm = clipboardManager ?: return@withContext false
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        true
      }.getOrDefault(false)
    }

  suspend fun clearClipboard(): Boolean =
    withContext(Dispatchers.Main) {
      runCatching {
        val cm = clipboardManager ?: return@withContext false
        cm.clearPrimaryClip()
        true
      }.getOrDefault(false)
    }

  /**
   * Reads clipboard text safely even when TapFlow is in the background on Android 10+.
   * Uses Shizuku shell if ready, otherwise briefly attaches and detaches a 1x1 focusable
   * transparent overlay window to gain temporary clipboard focus.
   */
  suspend fun readClipboardText(): String? {
    if (shizukuManager.diagnosticState.value.isFullyReady) {
      val shellText = shizukuManager.readClipboardText()
      if (shellText != null) return shellText
    }

    return withContext(Dispatchers.Main) {
      if (!Settings.canDrawOverlays(context)) {
        return@withContext readPrimaryClipDirect()
      }

      val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        ?: return@withContext readPrimaryClipDirect()

      val focusDummyView =
        View(context).apply {
          alpha = 0f
          isFocusable = true
          isFocusableInTouchMode = true
        }

      val params =
        WindowManager.LayoutParams(
          1,
          1,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
          PixelFormat.TRANSLUCENT,
        ).apply {
          gravity = Gravity.TOP or Gravity.START
          x = 0
          y = 0
        }

      var attached = false
      try {
        windowManager.addView(focusDummyView, params)
        attached = true
        focusDummyView.requestFocus()
        delay(60L)
        readPrimaryClipDirect()
      } catch (_: Throwable) {
        readPrimaryClipDirect()
      } finally {
        if (attached) {
          runCatching { windowManager.removeViewImmediate(focusDummyView) }
        }
      }
    }
  }

  private fun readPrimaryClipDirect(): String? {
    return runCatching {
      val cm = clipboardManager ?: return null
      val clip = cm.primaryClip ?: return null
      if (clip.itemCount <= 0) return null
      clip.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()
  }
}
