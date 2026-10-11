package dev.context.app.triggers.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import dev.context.app.triggers.platform.WifiHelper

/**
 * Emits Wi-Fi connected/disconnected events. The manifest receiver hears the protected
 * NETWORK_STATE_CHANGED / WIFI_STATE_CHANGED broadcasts; the provider deduplicates so an
 * automation only fires once per real transition.
 */
object WifiProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var lastConnected: Boolean? = null
    private var lastSsid: String? = null

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        // state will be sampled on the next broadcast
    }

    internal fun onSystemBroadcast(context: Context) {
        val state = WifiHelper.read(context)
        val changed = state.connected != lastConnected || state.ssid != lastSsid
        if (lastConnected == null || changed) {
            sink?.emit(
                TriggerEvent(
                    type = if (state.connected) TriggerType.WIFI_CONNECTED else TriggerType.WIFI_DISCONNECTED,
                    params = buildMap {
                        if (state.ssid != null) put(Param.SSID, state.ssid)
                        put(Param.BOOL_VALUE, state.connected.toString())
                    },
                ),
            )
        }
        lastConnected = state.connected
        lastSsid = state.ssid
    }

    fun resetForTesting() {
        lastConnected = null
        lastSsid = null
    }
}

class WifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION,
            android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION -> WifiProvider.onSystemBroadcast(context)
        }
    }
}