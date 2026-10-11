package dev.context.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Param
import dev.context.app.model.TriggerType

/**
 * Notification triggers and programmatic dismissal.
 *
 * Enable once in system settings ("Notification access"). Posts arrive here
 * even when Cue's own process was dead - the system binds this service
 * directly. Events carry the notification key so DISMISS_NOTIFICATION can act
 * on the exact notification that fired the automation.
 */
class CueNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return // ignore our own notifications
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val category = sbn.notification.category.orEmpty()
        runCatching {
            dev.context.app.ContextApp.container.dispatch(
                TriggerEvent(
                    TriggerType.NOTIFICATION_RECEIVED,
                    mapOf(
                        Param.PACKAGE to pkg,
                        Param.NOTIF_TITLE to title,
                        Param.NOTIF_TEXT to text,
                        Param.CATEGORY to category,
                        Param.NOTIF_KEY to sbn.key,
                        Param.AT to sbn.postTime.toString(),
                    ),
                ),
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    companion object {
        @Volatile
        private var instance: CueNotificationListener? = null

        fun isEnabled(): Boolean = instance != null

        /** Dismisses one notification by key. False when the listener is off or it is gone. */
        fun dismiss(key: String): Boolean {
            val svc = instance ?: return false
            return try {
                svc.cancelNotification(key)
                true
            } catch (t: Throwable) {
                false
            }
        }
    }
}
