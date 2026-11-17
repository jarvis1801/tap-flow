package com.tapflow.core.service.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.datastore.AppPreferences
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.service.R
import com.tapflow.core.service.overlay.OverlayStateBridge
import com.tapflow.core.service.projection.MediaProjectionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LocalBridgeService : Service() {
  companion object {
    const val ACTION_START = "com.tapflow.core.service.bridge.ACTION_START"
    const val ACTION_STOP = "com.tapflow.core.service.bridge.ACTION_STOP"
    const val EXTRA_PORT = "extra_port"

    private const val CHANNEL_ID = "tapflow_desktop_bridge"
    private const val NOTIFICATION_ID = 3103
  }

  @Inject lateinit var serverController: LocalBridgeServerController
  @Inject lateinit var localBridgeStateBridge: LocalBridgeStateBridge
  @Inject lateinit var overlayStateBridge: OverlayStateBridge
  @Inject lateinit var preferencesRepository: TapFlowPreferencesRepository
  @Inject lateinit var entitlementRepository: EntitlementRepository

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var observerJob: Job? = null
  private var hasObservedServerActive = false
  private var isStopping = false
  private var lastNotifiedPort: Int = AppPreferences.DEFAULT_KTOR_PORT

  override fun onCreate() {
    super.onCreate()
    localBridgeStateBridge.setBridgeRunning(true)
    ensureNotificationChannel()
    val initialPort = serverController.activePort.value.coerceIn(1024, 65535)
    lastNotifiedPort = initialPort
    startBridgeForeground(buildNotification(initialPort))
    observeBridgeLifecycle()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopBridgeFromAction(reason = "Stopped by user")
      }

      ACTION_START, null -> {
        isStopping = false
        val requestedPort =
          intent?.takeIf { it.hasExtra(EXTRA_PORT) }
            ?.getIntExtra(EXTRA_PORT, AppPreferences.DEFAULT_KTOR_PORT)
            ?.coerceIn(1024, 65535)
        val displayPort = requestedPort ?: serverController.activePort.value.coerceIn(1024, 65535)
        updateNotificationIfNeeded(displayPort, force = true)
        serviceScope.launch {
          val started = serverController.startServerFromService(requestedPort)
          if (!started) {
            stopBridgeFromAction(reason = null)
          } else {
            hasObservedServerActive = true
            updateNotificationIfNeeded(serverController.activePort.value, force = true)
          }
        }
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    localBridgeStateBridge.setBridgeRunning(false)
    localBridgeStateBridge.setLiveStreamingFromConsole(false)
    observerJob?.cancel()
    observerJob = null
    if (!isStopping && serverController.isServerRunning.value) {
      serverController.stopDesktopLiveStream()
      CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        serverController.stopServerFromService("Desktop bridge service stopped.")
      }
      stopMediaProjectionIfOverlayInactive()
    }
    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun observeBridgeLifecycle() {
    observerJob?.cancel()
    observerJob =
      serviceScope.launch {
        launch {
          combine(
            serverController.isServerRunning,
            serverController.activePort,
          ) { running, port ->
            running to port
          }
            .distinctUntilChanged()
            .collectLatest { (running, port) ->
              if (running) {
                hasObservedServerActive = true
                updateNotificationIfNeeded(port, force = false)
              } else if (hasObservedServerActive && !isStopping) {
                stopBridgeFromAction(reason = null)
              }
            }
        }

        launch {
          entitlementRepository.entitlementState.collectLatest { entitlement ->
            val hasLifetimePro =
              entitlement is EntitlementState.ProLifetime &&
                EntitlementValidator.canUseFeature(entitlement, ProFeatureGate.DESKTOP_CONSOLE)
            if (!hasLifetimePro && !isStopping) {
              stopBridgeFromAction("Desktop Studio server stopped: Lifetime Pro purchase is required.")
            }
          }
        }
      }
  }

  private fun stopBridgeFromAction(reason: String?) {
    if (isStopping) return
    isStopping = true
    observerJob?.cancel()
    observerJob = null
    serviceScope.launch {
      withContext(NonCancellable) {
        serverController.stopDesktopLiveStream()
        serverController.stopServerFromService(reason)
        stopMediaProjectionIfOverlayInactive()
        localBridgeStateBridge.setBridgeRunning(false)
        localBridgeStateBridge.setLiveStreamingFromConsole(false)
      }
      runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
      stopSelf()
    }
  }

  private fun stopMediaProjectionIfOverlayInactive() {
    if (!overlayStateBridge.isOverlayRunning.value) {
      runCatching { stopService(Intent(this, MediaProjectionService::class.java)) }
    }
  }

  private fun startBridgeForeground(notification: Notification) {
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

  private fun updateNotificationIfNeeded(port: Int, force: Boolean) {
    val safePort = port.coerceIn(1024, 65535)
    if (!force && safePort == lastNotifiedPort) return
    lastNotifiedPort = safePort
    val manager = getSystemService(NotificationManager::class.java) ?: return
    runCatching { manager.notify(NOTIFICATION_ID, buildNotification(safePort)) }
  }

  private fun ensureNotificationChannel() {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.local_bridge_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.local_bridge_channel_description)
          setShowBadge(false)
        }
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(port: Int): Notification {
    val safePort = port.coerceIn(1024, 65535)
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        3103,
        Intent(this, LocalBridgeService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val launchIntent =
      packageManager.getLaunchIntentForPackage(packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val contentIntent =
      launchIntent?.let {
        PendingIntent.getActivity(
          this,
          3104,
          it,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      }
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_tapflow_capture_notification)
      .setContentTitle(getString(R.string.local_bridge_notification_title, safePort))
      .setContentText(getString(R.string.local_bridge_notification_text, safePort))
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .setSilent(true)
      .apply {
        if (contentIntent != null) {
          setContentIntent(contentIntent)
        }
      }
      .addAction(0, getString(R.string.local_bridge_stop_action), stopPendingIntent)
      .build()
  }
}
