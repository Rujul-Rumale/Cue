package dev.context.app.storage.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * App preferences. Everything stays on-device.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val GEOfenceHysteresis = longPreferencesKey("geofence_hysteresis_ms")
        val developerMode = booleanPreferencesKey("developer_mode")
        val locationTransitionCooldown = longPreferencesKey("location_transition_cooldown_ms")
        val lowPowerMode = booleanPreferencesKey("low_power_mode")
        val fgServiceNotification = booleanPreferencesKey("fg_service_notification")
    }

    /** Hysteresis window after ENTER during which a repeat EXIT is swallowed. */
    val geofenceHysteresisMs: Flow<Long> = context.settingsDataStore.data
        .map { it[Keys.GEOfenceHysteresis] ?: DEFAULT_HYSTERESIS_MS }

    val developerMode: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.developerMode] ?: false }

    val locationTransitionCooldownMs: Flow<Long> = context.settingsDataStore.data
        .map { it[Keys.locationTransitionCooldown] ?: DEFAULT_HYSTERESIS_MS }

    val lowPowerMode: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.lowPowerMode] ?: false }

    val fgServiceNotification: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.fgServiceNotification] ?: true }

    suspend fun currentHysteresisMs(): Long = geofenceHysteresisMs.first()

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.developerMode] = enabled }
    }

    suspend fun setHysteresisMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.GEOfenceHysteresis] = minutes.coerceIn(0, 240) * 60_000L }
    }

    suspend fun setLowPowerMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.lowPowerMode] = enabled }
    }

    suspend fun setFgServiceNotification(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.fgServiceNotification] = enabled }
    }

    companion object {
        const val DEFAULT_HYSTERESIS_MS = 5 * 60_000L
    }
}