package dev.context.app.automation.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.context.app.ContextApp
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.automation.service.AutomationForegroundService
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import kotlinx.coroutines.launch

/**
 * Re-activates the automation stack after a reboot or app update.
 * (Application.onCreate has already rebuilt the container by the time this runs.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restart the persistent service first; its onStartCommand re-syncs
        // subscriptions in case Play Services dropped geofences/alarms. Note:
        // on Android 12+ a background FGS start can be denied (no exemption for
        // BOOT_COMPLETED); that failure is swallowed and geofences/alarms still
        // work because they live in Play Services / AlarmManager, not in our process.
        runCatching { AutomationForegroundService.start(context.applicationContext) }
        val container = runCatching { ContextApp.container }.getOrNull() ?: return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                container.scope.launch {
                    container.refreshSubscriptions()
                    container.dispatch(
                        TriggerEvent(
                            type = TriggerType.BOOT_COMPLETED,
                            params = mapOf(Param.NAME to android.os.SystemClock.elapsedRealtime().toString()),
                        ),
                    )
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                container.scope.launch { container.refreshSubscriptions() }
            }
        }
    }
}
