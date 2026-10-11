package dev.context.app.triggers.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import dev.context.app.triggers.platform.BluetoothConnectedCache
import dev.context.app.triggers.platform.BluetoothHelper

/**
 * Emits Bluetooth connect/disconnect events from Android broadcasts
 * (ACTION_ACL_CONNECTED / ACTION_ACL_DISCONNECTED). No polling.
 */
object BluetoothProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var registered = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) {
                runCatching { BluetoothConnectedCache.update(BluetoothHelper.connectedNames(context)) }
            }
        }
    }

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        if (registered) return
        runCatching {
            context.registerReceiver(stateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            registered = true
        }.onFailure { android.util.Log.w("BluetoothProvider", "Failed to register", it) }
    }

    /** Called by the manifest receiver and by the debug simulator. */
    internal fun onSystemBroadcast(context: Context, action: String?, device: BluetoothDevice?) {
        val name = device?.name ?: device?.address
        val connected = when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> return
        }
        runCatching { BluetoothConnectedCache.update(BluetoothHelper.connectedNames(context)) }
        val params = buildMap {
            if (name != null) put(Param.DEVICE_NAME, name)
            if (device != null) put(Param.DEVICE_MAC, device.address)
        }
        sink?.emit(
            TriggerEvent(
                type = if (connected) TriggerType.BLUETOOTH_CONNECTED else TriggerType.BLUETOOTH_DISCONNECTED,
                params = params,
            ),
        )
    }
}

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 31 && !BluetoothHelper.permissionGranted(context)) return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        BluetoothProvider.onSystemBroadcast(context, intent.action, device)
    }
}