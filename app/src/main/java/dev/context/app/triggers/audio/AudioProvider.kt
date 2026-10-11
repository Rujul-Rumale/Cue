package dev.context.app.triggers.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider

/** Wired headphone plug/unplug events (Android's HEADSET_PLUG broadcast). */
object AudioProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
    }

    internal fun onSystemBroadcast(context: Context, state: Int) {
        sink?.emit(
            TriggerEvent(
                type = if (state == 1) TriggerType.HEADSET_PLUGGED else TriggerType.HEADSET_UNPLUGGED,
            ),
        )
    }
}

class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_HEADSET_PLUG) {
            AudioProvider.onSystemBroadcast(context, intent.getIntExtra("state", -1))
        }
    }
}