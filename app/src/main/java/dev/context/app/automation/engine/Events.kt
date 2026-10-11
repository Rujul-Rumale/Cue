package dev.context.app.automation.engine

import dev.context.app.model.Param
import dev.context.app.model.TriggerType

/**
 * A normalized, cross-provider event produced by a [dev.context.app.triggers.TriggerProvider]
 * whenever something of interest happens on the device. Providers translate Android
 * callbacks into these events so the engine stays decoupled from the platform.
 */
data class TriggerEvent(
    val type: TriggerType,
    val params: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = Param.EVENT_SOURCE,
) {
    /**
     * A stable identity for deduplication - two events with the same key are considered
     * the same logical occurrence (e.g. repeated ENTER broadcast for the same geofence).
     */
    fun signature(): String = buildString {
        append(type.name)
        params.entries
            .filter { it.value.isNotBlank() }
            .sortedBy { it.key }
            .forEach { append('|').append(it.key).append('=').append(it.value) }
    }
}

/**
 * A read-only snapshot of the current device state, produced at event time so that
 * conditions are evaluated against a consistent picture.
 */
data class RuntimeSnapshot(
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val batteryTemperatureC: Float? = null,
    val batteryFull: Boolean? = null,
    val wifiConnected: Boolean? = null,
    val wifiSsid: String? = null,
    val bluetoothEnabled: Boolean? = null,
    val connectedBluetoothDevices: Set<String> = emptySet(),
    val dndActive: Boolean? = null,
    val screenOn: Boolean? = null,
    val screenUnlocked: Boolean? = null,
    val activity: String? = null,
    val locationsInside: Set<Long> = emptySet(),
    val ringerMode: String? = null,
    val isPowerSave: Boolean? = null,
    val currentTimeMillis: Long = System.currentTimeMillis(),
)