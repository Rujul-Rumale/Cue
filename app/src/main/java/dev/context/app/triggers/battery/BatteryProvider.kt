package dev.context.app.triggers.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import dev.context.app.triggers.platform.BatteryHelper

/**
 * Emits battery events using Android broadcasts only - never polling.
 *
 *  - ACTION_BATTERY_CHANGED is sticky; a runtime receiver hears level changes (~1%/event).
 *  - The manifest receiver covers charging edges.
 */
object BatteryProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var registered = false

    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onBatteryChanged(context)
        }
    }

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        if (registered) return
        runCatching {
            context.registerReceiver(runtimeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            registered = true
        }.onFailure { android.util.Log.w("BatteryProvider", "Failed to register", it) }
    }

    private fun onBatteryChanged(context: Context) {
        val info = BatteryHelper.read(context)
        sink?.emit(
            TriggerEvent(
                type = TriggerType.BATTERY_CHANGED,
                params = mapOf(Param.THRESHOLD to info.percent.toString()),
            ),
        )
    }

    /** Called by the manifest receiver for charging edges. */
    internal fun onSystemBroadcast(context: Context, action: String?) {
        when (action) {
            Intent.ACTION_POWER_CONNECTED -> sink?.emit(TriggerEvent(TriggerType.CHARGING_START))
            Intent.ACTION_POWER_DISCONNECTED -> sink?.emit(TriggerEvent(TriggerType.CHARGING_STOP))
            else -> onBatteryChanged(context)
        }
    }
}

/** Manifest-declared receiver for charging edge broadcasts. */
class BatteryChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BatteryProvider.onSystemBroadcast(context, intent.action)
    }
}