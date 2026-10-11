package dev.context.app.triggers.platform

import android.content.Context
import dev.context.app.automation.engine.RuntimeSnapshot
import dev.context.app.automation.engine.StateReader
import dev.context.app.core.Clock
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.activity.ActivityStateCache
import dev.context.app.triggers.location.LocationInsideStore

/**
 * Bridges the engine's [StateReader] contract to this device's actual state.
 */
class AndroidStateReader(
    private val context: Context,
    private val insideStore: LocationInsideStore,
    private val clock: Clock = Clock.SYSTEM,
) : StateReader {

    override suspend fun snapshot(): RuntimeSnapshot {
        val battery = BatteryHelper.read(context)
        val wifi = WifiHelper.read(context)
        val btEnabled = BluetoothHelper.enabled(context)
        return RuntimeSnapshot(
            batteryPercent = battery.percent,
            charging = battery.charging,
            batteryFull = battery.full,
            batteryTemperatureC = battery.temperatureC,
            wifiConnected = wifi.connected,
            wifiSsid = wifi.ssid,
            bluetoothEnabled = btEnabled,
            connectedBluetoothDevices = if (btEnabled) BluetoothHelper.connectedNames(context) else emptySet(),
            dndActive = DndHelper.active(context),
            screenOn = ScreenHelper.isOn(context),
            screenUnlocked = ScreenHelper.isUnlocked(context),
            activity = ActivityStateCache.last,
            locationsInside = insideStore.insideIds(),
            isPowerSave = PowerSaveHelper.active(context),
            currentTimeMillis = clock.nowMillis(),
        )
    }

    override suspend fun triggerTruth(type: TriggerType, params: Map<String, String>): Boolean? {
        return when (type) {
            TriggerType.LOCATION_ENTER, TriggerType.LOCATION_DWELL -> {
                params[Param.LOCATION_ID]?.toLongOrNull()?.let { insideStore.isInside(it) }
            }
            TriggerType.LOCATION_EXIT -> {
                params[Param.LOCATION_ID]?.toLongOrNull()?.let { !insideStore.isInside(it) }
            }
            TriggerType.BATTERY_BELOW -> batteryCompare(params, below = true)
            TriggerType.BATTERY_ABOVE -> batteryCompare(params, below = false)
            TriggerType.BATTERY_FULL -> BatteryHelper.read(context).full
            TriggerType.CHARGING_START -> BatteryHelper.read(context).charging
            TriggerType.CHARGING_STOP -> !BatteryHelper.read(context).charging
            TriggerType.SCREEN_ON -> ScreenHelper.isOn(context)
            TriggerType.SCREEN_OFF -> !ScreenHelper.isOn(context)
            TriggerType.SCREEN_UNLOCKED -> ScreenHelper.isUnlocked(context)
            TriggerType.DND_ENABLED -> DndHelper.active(context)
            TriggerType.DND_DISABLED -> !DndHelper.active(context)
            TriggerType.BLUETOOTH_CONNECTED ->
                if (!BluetoothHelper.enabled(context)) false else BluetoothHelper.connectedNames(context).isNotEmpty()
            TriggerType.BLUETOOTH_DISCONNECTED -> !BluetoothHelper.enabled(context)
            TriggerType.WIFI_CONNECTED -> {
                val wifi = WifiHelper.read(context)
                val ssid = params[Param.SSID]
                when {
                    ssid == null -> wifi.connected
                    else -> wifi.connected && wifi.ssid.equals(ssid, ignoreCase = true)
                }
            }
            TriggerType.WIFI_DISCONNECTED -> {
                val wifi = WifiHelper.read(context)
                val ssid = params[Param.SSID]
                when {
                    ssid == null -> !wifi.connected
                    else -> !wifi.connected || !wifi.ssid.equals(ssid, ignoreCase = true)
                }
            }
            TriggerType.HEADSET_PLUGGED, TriggerType.HEADSET_UNPLUGGED -> null
            TriggerType.USB_CONNECTED -> UsbHelper.connected(context)
            TriggerType.USB_DISCONNECTED -> !UsbHelper.connected(context)
            // Cell-tower inside state lives in CellTowerProvider (own persisted
            // store, self-heals on first read); momentary/system occurrences
            // carry unique params and reconcile past them safely.
            TriggerType.CELL_TOWER_ENTER, TriggerType.CELL_TOWER_DWELL,
            TriggerType.CELL_TOWER_EXIT,
            TriggerType.SIGNIFICANT_MOTION, TriggerType.DEVICE_PICKUP,
            TriggerType.FOREGROUND_APP_CHANGED, TriggerType.VOLUME_KEY_PRESSED,
            TriggerType.NOTIFICATION_RECEIVED, TriggerType.SMS_RECEIVED,
            TriggerType.CALL_STATE_CHANGED, TriggerType.CALENDAR_EVENT_START,
            TriggerType.CALENDAR_EVENT_END, TriggerType.LOGCAT_MATCH -> null
            TriggerType.ACTIVITY_START -> {
                val wanted = params[Param.ACTIVITY]
                ActivityStateCache.last?.equals(wanted, ignoreCase = true) ?: false
            }
            TriggerType.ACTIVITY_END -> {
                // Inverted vs START: "stopped X" already holds while X is NOT
                // the current activity, so reconcile arms correctly and the next
                // real stop transition fires instead of being skipped.
                val wanted = params[Param.ACTIVITY]
                !(ActivityStateCache.last?.equals(wanted, ignoreCase = true) ?: false)
            }
            else -> null
        }
    }

    private fun batteryCompare(params: Map<String, String>, below: Boolean): Boolean? {
        val threshold = params[Param.THRESHOLD]?.toIntOrNull() ?: return null
        val pct = BatteryHelper.read(context).percent ?: return null
        return if (below) pct <= threshold else pct >= threshold
    }
}