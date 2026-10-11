package dev.context.app.triggers.location

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.context.app.core.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.geofenceStateDataStore by preferencesDataStore(name = "geofence_state")

/**
 * Persisted per-location inside/outside state plus the geofence hysteresis window.
 *
 * Hysteresis behaviour: after an ENTER, any EXIT arriving within [hysteresisMs] is
 * swallowed. This prevents ENTER/EXIT flapping at the boundary of a configured radius.
 */
class LocationInsideStore(
    private val context: Context,
    private val clock: Clock = Clock.SYSTEM,
) {
    private val ds: DataStore<Preferences> by lazy { context.geofenceStateDataStore }

    private fun insideKey(id: Long) = booleanPreferencesKey("inside_$id")
    private fun enterAtKey(id: Long) = longPreferencesKey("enter_at_$id")

    /**
     * Registers a transition. Returns true when the transition is accepted (state changed),
     * false when it is a duplicate or swallowed by the hysteresis window.
     */
    suspend fun handleTransition(locId: Long, entered: Boolean, hysteresisMs: Long): Boolean {
        val now = clock.nowMillis()
        val prefs = ds.data.first()
        val inside = prefs[insideKey(locId)] ?: false
        val enterAt = prefs[enterAtKey(locId)] ?: 0L
        return when {
            entered && !inside -> {
                ds.edit { p ->
                    p[insideKey(locId)] = true
                    p[enterAtKey(locId)] = now
                }
                true
            }
            entered && inside -> false // already inside - duplicate
            !entered && !inside -> false // already outside - duplicate
            !entered && inside -> {
                if (now - enterAt < hysteresisMs) false else {
                    ds.edit { p -> p[insideKey(locId)] = false }
                    true
                }
            }
            else -> false
        }
    }

    suspend fun isInside(locId: Long): Boolean = ds.data.first()[insideKey(locId)] ?: false

    suspend fun insideIds(): Set<Long> {
        val prefs = ds.data.first()
        return prefs.asMap().entries.mapNotNull { (key, value) ->
            val name = key.name
            if (name.startsWith("inside_") && value is Boolean && value) name.removePrefix("inside_").toLongOrNull() else null
        }.toSet()
    }

    suspend fun clearAll() {
        ds.edit { p -> p.clear() }
    }

    /** Drops persisted inside/enter-at state for one place (e.g. when it is deleted). */
    suspend fun clearLocation(locId: Long) {
        ds.edit { p ->
            p.remove(insideKey(locId))
            p.remove(enterAtKey(locId))
        }
    }
}