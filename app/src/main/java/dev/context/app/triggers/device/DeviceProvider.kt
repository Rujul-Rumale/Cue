package dev.context.app.triggers.device

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider

/**
 * Device-state triggers: screen on/off/unlock and Do Not Disturb changes.
 * Screen broadcasts are only reliably delivered to runtime-registered receivers, so the
 * provider registers them in-process on every process start.
 */
object DeviceProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var registered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> sink?.emit(TriggerEvent(TriggerType.SCREEN_ON))
                Intent.ACTION_SCREEN_OFF -> sink?.emit(TriggerEvent(TriggerType.SCREEN_OFF))
                Intent.ACTION_USER_PRESENT -> sink?.emit(TriggerEvent(TriggerType.SCREEN_UNLOCKED))
            }
        }
    }

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        if (registered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(screenReceiver, filter)
            }
            registered = true
        }.onFailure { android.util.Log.w("DeviceProvider", "Failed to register", it) }
    }

    internal fun onDndChanged(active: Boolean) {
        sink?.emit(TriggerEvent(if (active) TriggerType.DND_ENABLED else TriggerType.DND_DISABLED))
    }
}

class DndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) return
        val active = dev.context.app.triggers.platform.DndHelper.active(context)
        DeviceProvider.onDndChanged(active)
    }
}