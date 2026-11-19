package com.tapflow.core.service.external

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tapflow.core.service.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalBroadcastEmitter @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  companion object {
    private const val CHANNEL_ID = "tapflow_external_triggers"
    private val notificationIdCounter = AtomicInteger(4100)
  }

  /**
   * Emits an unrestricted standard broadcast (`ACTION_EXECUTION_FINISHED`) without `.setPackage(...)`
   * so both MacroDroid and Tasker `Intent Received` triggers receive it reliably.
   */
  fun emitExecutionFinished(
    status: String,
    mode: String,
    targetId: String = "",
    targetTitle: String = "",
    durationMs: Long = 0L,
    stepsExecuted: Int = 0,
    error: String = ExternalBroadcastContract.ERROR_NONE,
  ) {
    val intent =
      ExternalBroadcastContract.buildFinishedBroadcastIntent(
        packageName = context.packageName,
        status = status,
        mode = mode,
        targetId = targetId,
        targetTitle = targetTitle,
        durationMs = durationMs,
        stepsExecuted = stepsExecuted,
        error = error,
      )
    runCatching { context.sendBroadcast(intent) }
  }

  fun postExternalTriggerNotification(
    title: String,
    message: String,
    openSeamlessTab: Boolean = true,
  ) {
    if (!canPostNotifications()) return
    ensureChannel()

    val launchIntent =
      context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags =
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (openSeamlessTab) {
          putExtra(
            ExternalBroadcastContract.EXTRA_OPEN_HOME_TAB,
            ExternalBroadcastContract.HOME_TAB_SEAMLESS,
          )
          putExtra(
            ExternalBroadcastContract.EXTRA_FROM_EXTERNAL_NOTIFICATION,
            true,
          )
        }
      }

    val contentPendingIntent =
      launchIntent?.let { intent ->
        PendingIntent.getActivity(
          context,
          if (openSeamlessTab) 4201 else 4202,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      }

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_tapflow_capture_notification)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ERROR)
        .setAutoCancel(true)
        .apply {
          if (contentPendingIntent != null) {
            setContentIntent(contentPendingIntent)
          }
        }
        .build()

    runCatching {
      val id = 4100 + (notificationIdCounter.incrementAndGet() % 8)
      NotificationManagerCompat.from(context).notify(id, notification)
    }
  }

  private fun canPostNotifications(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  private fun ensureChannel() {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(CHANNEL_ID)
    if (existing == null || existing.importance < NotificationManager.IMPORTANCE_HIGH) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.external_trigger_channel_name),
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = context.getString(R.string.external_trigger_channel_description)
          enableVibration(true)
        }
      manager.createNotificationChannel(channel)
    }
  }
}
