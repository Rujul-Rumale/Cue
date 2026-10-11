package dev.context.app.triggers.platform

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Lightweight, feature-isolated readers for current device state. */
data class BatteryInfo(val percent: Int?, val charging: Boolean, val full: Boolean, val temperatureC: Float?)

object BatteryHelper {
    fun read(context: Context): BatteryInfo {
        val i = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (t: Throwable) {
            null
        } ?: return BatteryInfo(null, false, false, null)
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val status = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
        val temp = i.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)
        return BatteryInfo(
            percent = pct,
            charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0,
            full = status == android.os.BatteryManager.BATTERY_STATUS_FULL,
            temperatureC = if (temp >= 0) temp / 10f else null,
        )
    }
}

data class WifiState(val connected: Boolean, val ssid: String?)

object WifiHelper {
    fun read(context: Context): WifiState {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        var ssid: String? = null
        if (connected && Build.VERSION.SDK_INT >= 29) {
            ssid = runCatching {
                val m = NetworkCapabilities::class.java.getMethod("getSsid")
                m.invoke(caps) as? String
            }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
        }
        if (ssid == null || ssid == "<unknown ssid>") {
            ssid = try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                wifi.connectionInfo?.ssid?.trim('"')?.takeUnless { it == "<unknown ssid>" }
            } catch (t: Throwable) {
                null
            }
        }
        return WifiState(connected = connected, ssid = ssid)
    }
}

object BluetoothHelper {
    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun enabled(context: Context): Boolean =
        if (!permissionGranted(context)) false else runCatching {
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        }.getOrDefault(false)

    /** Names of currently connected Bluetooth devices, best effort. */
    fun connectedNames(context: Context): Set<String> {
        if (!permissionGranted(context)) return emptySet()
        return runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val names = mutableSetOf<String>()
            manager.getConnectedDevices(android.bluetooth.BluetoothProfile.GATT).forEach { names.add(it.name ?: it.address) }
            names.addAll(BluetoothConnectedCache.knownConnected)
            names
        }.getOrDefault(BluetoothConnectedCache.knownConnected)
    }
}

/** Cache of connected BT device identities maintained by the connectivity provider. */
object BluetoothConnectedCache {
    @Volatile
    var knownConnected: Set<String> = emptySet()
        private set

    fun update(names: Set<String>) {
        knownConnected = names
    }
}

object DndHelper {
    fun active(context: Context): Boolean {
        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val f = nm.currentInterruptionFilter
            f != NotificationManager.INTERRUPTION_FILTER_ALL &&
                f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }.getOrDefault(false)
    }
}

object ScreenHelper {
    fun isOn(context: Context): Boolean {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isInteractive
        }.getOrDefault(false)
    }

    fun isUnlocked(context: Context): Boolean {
        return runCatching {
            !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked()
        }.getOrDefault(true)
    }
}

object PowerSaveHelper {
    fun active(context: Context): Boolean =
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isPowerSaveMode
        }.getOrDefault(false)
}

object UsbHelper {
    fun connected(context: Context): Boolean =
        runCatching {
            val um = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
            um.deviceList.isNotEmpty() || um.accessoryList?.isNotEmpty() == true
        }.getOrDefault(false)
}

object AirplaneModeHelper {
    fun active(context: Context): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1 }
            .getOrDefault(false)
}