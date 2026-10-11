package dev.context.app.automation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.context.app.R
import dev.context.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that keeps the automation process alive.
 *
 * Single-ownership rule: [dev.context.app.AppContainer] owns provider registration,
 * reconcile and subscription refresh. This service only owns the foreground
 * notification and process priority. It never registers providers itself, so
 * receivers cannot be double-registered when both Application.onCreate and the
 * service run in the same process.
 */
class AutomationForegroundService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    companion object {
        const val CHANNEL_ID = "automation_service"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "dev.context.app.STOP_AUTOMATION_SERVICE"
        private const val EXTRA_SHOW_NOTIFICATION = "show_notification"

        fun start(context: Context, showNotification: Boolean = true) {
            val intent = Intent(context, AutomationForegroundService::class.java)
                .putExtra(EXTRA_SHOW_NOTIFICATION, showNotification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val showNotification = intent?.getBooleanExtra(EXTRA_SHOW_NOTIFICATION, true) ?: true
        val notification = buildNotification(showNotification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Re-sync subscriptions in case Play Services dropped geofences/alarms
        // while the process was dead. Cheap and idempotent.
        scope.launch {
            runCatching {
                dev.context.app.ContextApp.container.notifyDataChanged()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Automation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Cue automation engine running in background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(showNotification: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AutomationForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Cue")
            .setContentText("Monitoring triggers")
            .setContentIntent(openApp)
            .setOngoing(showNotification)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(if (showNotification) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)
        if (showNotification) {
            builder.addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
        }
        return builder.build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Manufacturer task killers swipe the task away; START_STICKY plus the
        // BOOT_COMPLETED / MY_PACKAGE_REPLACED receiver re-establish us. Do not
        // self-restart here to avoid restart loops on force-stop.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
