package dev.context.app.triggers.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Param
import dev.context.app.model.TriggerType

/** USB device attach/detach edges (transitional pair with reset). */
class UsbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> TriggerType.USB_CONNECTED
            UsbManager.ACTION_USB_DEVICE_DETACHED -> TriggerType.USB_DISCONNECTED
            else -> return
        }
        runCatching {
            dev.context.app.ContextApp.container.dispatch(
                TriggerEvent(
                    type,
                    mapOf(Param.AT to Clock.SYSTEM.nowMillis().toString()),
                ),
            )
        }
    }
}
