package com.tapflow.core.service.projection

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.tapflow.core.service.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaProjectionService : Service() {
  companion object {
    const val ACTION_START = "com.tapflow.core.service.projection.ACTION_START"
    const val ACTION_STOP = "com.tapflow.core.service.projection.ACTION_STOP"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"

    private const val CHANNEL_ID = "tapflow_media_projection"
    private const val NOTIFICATION_ID = 3102
    private const val VIRTUAL_DISPLAY_NAME = "TapFlowVirtualScreen"
  }

  @Inject
  lateinit var mediaProjectionBridge: MediaProjectionBridge

  private val mainHandler = Handler(Looper.getMainLooper())
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var displayListener: DisplayManager.DisplayListener? = null
  private var currentWidth: Int = 0
  private var currentHeight: Int = 0
  private var currentDensityDpi: Int = 0
  private var isStoppingInternally: Boolean = false

  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        val reason =
          if (isStoppingInternally) {
            null
          } else {
            "Screen capture was stopped by the system (screen lock or status-bar toggle)."
          }
        releaseResources(
          nextState =
            if (reason != null) {
              ScreenCaptureState.StoppedBySystem(reason)
            } else {
              ScreenCaptureState.Idle
            },
        )
        stopSelf()
      }

      override fun onCapturedContentResize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
          rebuildSurfaceIfNeeded(width, height, currentDensityDpi)
        }
      }
    }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        isStoppingInternally = true
        releaseResources(ScreenCaptureState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
      }

      ACTION_START -> {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = extractResultData(intent)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
          mediaProjectionBridge.emitError("Invalid or missing MediaProjection consent result.")
          stopSelf()
          return START_NOT_STICKY
        }

        // Critical: Start foreground service with mediaProjection type BEFORE getMediaProjection().
        ensureNotificationChannel()
        startProjectionForeground(buildNotification())

        startProjectionSession(resultCode, resultData)
        return START_NOT_STICKY
      }

      else -> {
        stopSelf()
        return START_NOT_STICKY
      }
    }
  }

  override fun onDestroy() {
    isStoppingInternally = true
    releaseResources(
      nextState =
        if (mediaProjectionBridge.captureState.value is ScreenCaptureState.StoppedBySystem) {
          mediaProjectionBridge.captureState.value
        } else {
          ScreenCaptureState.Idle
        },
    )
    super.onDestroy()
  }

  private fun startProjectionSession(resultCode: Int, resultData: Intent) {
    isStoppingInternally = false
    releaseResources(ScreenCaptureState.Idle)

    val projectionManager =
      getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    if (projectionManager == null) {
      mediaProjectionBridge.emitError("MediaProjectionManager unavailable on this device.")
      stopSelf()
      return
    }

    val projection =
      runCatching { projectionManager.getMediaProjection(resultCode, resultData) }.getOrNull()
    if (projection == null) {
      mediaProjectionBridge.emitError("Failed to obtain MediaProjection session.")
      stopSelf()
      return
    }
    mediaProjection = projection

    // Critical: Register callback BEFORE createVirtualDisplay().
    projection.registerCallback(projectionCallback, mainHandler)

    val (width, height, densityDpi) = readCurrentDisplayMetrics()
    if (width <= 0 || height <= 0) {
      mediaProjectionBridge.emitError("Invalid display metrics for VirtualDisplay.")
      releaseResources(mediaProjectionBridge.captureState.value)
      stopSelf()
      return
    }

    currentWidth = width
    currentHeight = height
    currentDensityDpi = densityDpi

    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    mediaProjectionBridge.swapActiveReader(reader, width, height, densityDpi)

    val createdDisplay =
      runCatching {
        projection.createVirtualDisplay(
          VIRTUAL_DISPLAY_NAME,
          width,
          height,
          densityDpi,
          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
          reader.surface,
          null,
          mainHandler,
        )
      }.getOrNull()

    if (createdDisplay == null) {
      mediaProjectionBridge.emitError("Unable to create VirtualDisplay.")
      releaseResources(mediaProjectionBridge.captureState.value)
      stopSelf()
      return
    }
    virtualDisplay = createdDisplay

    registerOrientationDisplayListener()
  }

  private fun registerOrientationDisplayListener() {
    val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
    val listener =
      object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
          if (displayId != Display.DEFAULT_DISPLAY) return
          val (newW, newH, newDpi) = readCurrentDisplayMetrics()
          rebuildSurfaceIfNeeded(newW, newH, newDpi)
        }
      }
    displayListener = listener
    displayManager.registerDisplayListener(listener, mainHandler)
  }

  private fun rebuildSurfaceIfNeeded(newWidth: Int, newHeight: Int, newDensityDpi: Int) {
    if (newWidth <= 0 || newHeight <= 0) return
    val safeDpi = if (newDensityDpi > 0) newDensityDpi else currentDensityDpi.coerceAtLeast(160)
    if (newWidth == currentWidth && newHeight == currentHeight && safeDpi == currentDensityDpi) {
      return
    }
    val activeDisplay = virtualDisplay ?: return

    currentWidth = newWidth
    currentHeight = newHeight
    currentDensityDpi = safeDpi

    val newReader = ImageReader.newInstance(newWidth, newHeight, PixelFormat.RGBA_8888, 2)
    activeDisplay.resize(newWidth, newHeight, safeDpi)
    activeDisplay.setSurface(newReader.surface)
    mediaProjectionBridge.swapActiveReader(newReader, newWidth, newHeight, safeDpi)
  }

  private fun releaseResources(nextState: ScreenCaptureState) {
    displayListener?.let { listener ->
      val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
      runCatching { dm?.unregisterDisplayListener(listener) }
    }
    displayListener = null

    runCatching { virtualDisplay?.release() }
    virtualDisplay = null

    val projection = mediaProjection
    mediaProjection = null
    if (projection != null) {
      runCatching { projection.unregisterCallback(projectionCallback) }
      runCatching { projection.stop() }
    }

    mediaProjectionBridge.clearReader(nextState)
  }

  private fun readCurrentDisplayMetrics(): Triple<Int, Int, Int> {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    if (windowManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = windowManager.currentWindowMetrics.bounds
      val density = resources.configuration.densityDpi
      return Triple(bounds.width(), bounds.height(), density)
    }

    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager?.defaultDisplay?.getRealMetrics(metrics)
    return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
  }

  private fun extractResultData(intent: Intent): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(EXTRA_RESULT_DATA)
    }
  }

  private fun startProjectionForeground(notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun ensureNotificationChannel() {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(CHANNEL_ID)
    if (existing == null) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.media_projection_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.media_projection_channel_description)
          setShowBadge(false)
        }
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        0,
        Intent(this, MediaProjectionService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_tapflow_capture_notification)
      .setContentTitle(getString(R.string.media_projection_notification_title))
      .setContentText(getString(R.string.media_projection_notification_text))
      .setOngoing(true)
      .setSilent(true)
      .addAction(0, getString(R.string.media_projection_stop_action), stopPendingIntent)
      .build()
  }
}
