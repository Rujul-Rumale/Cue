package dev.context.app.triggers.location

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dev.context.app.automation.repository.AutomationRepository
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Automation
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.storage.datastore.SettingsRepository
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Location triggers backed by Android's Geofencing API. Geofences live in Google Play
 * services, so the app stays asleep between transitions. On a transition the manifest
 * receiver wakes us, applies hysteresis via [LocationInsideStore], and emits normalized
 * events to the engine.
 */
class LocationProvider(
    private val insideStore: LocationInsideStore,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.SYSTEM,
) : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var contextRef: Context? = null
    private lateinit var geofencingClient: GeofencingClient

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        contextRef = context.applicationContext
        geofencingClient = LocationServices.getGeofencingClient(context)
    }

    /** Rebuilds the geofence set from the current automations/locations. */
    suspend fun sync(automations: List<Automation>, locations: List<dev.context.app.model.LocationMarker>) {
        val context = contextRef ?: return
        if (!hasLocationPermission(context)) return
        removeAll()
        val wanted = automations
            .filter { it.enabled && it.trigger.type in GPS_TRIGGERS }
        if (wanted.isEmpty()) return
        // The caller partitions by low-power mode; whatever arrives here is served
        // via GPS geofence, including explicit CELL_TOWER_* watches (the engine
        // matches place transitions across sensing paths).

        // Group by location id; compute the loitering delay for dwell automations.
        val byLocation = wanted.groupBy { it.trigger.params[Param.LOCATION_ID]?.toLongOrNull() }
        val geofences = byLocation.mapNotNull { (locId, automationsFor) ->
            val locIdLong = locId ?: return@mapNotNull null
            val marker = locations.firstOrNull { it.id == locIdLong } ?: return@mapNotNull null
            var transitions = Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            val dwellMinutes = automationsFor
                .filter {
                    it.trigger.type == TriggerType.LOCATION_DWELL ||
                        it.trigger.type == TriggerType.CELL_TOWER_DWELL
                }
                .mapNotNull { it.trigger.params[Param.DWELL_MINUTES]?.toIntOrNull() }
                .minOrNull()
            if (dwellMinutes != null) transitions = transitions or Geofence.GEOFENCE_TRANSITION_DWELL
            Geofence.Builder()
                .setRequestId(locIdLong.toString())
                .setCircularRegion(marker.latitude, marker.longitude, marker.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(transitions)
                .setLoiteringDelay((dwellMinutes ?: 0) * 60_000) // no-op for non-dwell
                .build()
        }

        if (geofences.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()
        geofencingClient.addGeofences(request, pendingIntent(context))
            .addOnFailureListener { e ->
                android.util.Log.w(TAG, "addGeofences failed", e)
            }
    }

    private suspend fun removeAll() {
        val context = contextRef ?: return
        runCatching { geofencingClient.removeGeofences(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQ_CODE,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun hasLocationPermission(context: Context): Boolean {
        val hasForeground = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackground = Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasForeground && !hasBackground) {
            android.util.Log.w("LocationProvider", "Foreground location granted but BACKGROUND location missing - geofences will not work")
        }
        return hasForeground && hasBackground
    }

    /** Called from the manifest receiver on a geofence transition. */
    suspend fun onGeofencingEvent(context: Context, event: GeofencingEvent) {
        if (event.hasError()) return
        val transition = event.geofenceTransition
        val triggering = event.triggeringGeofences ?: emptyList()
        val hysteresisMs = settings.currentHysteresisMs()
        for (gf in triggering) {
            val locId = gf.requestId.toLongOrNull() ?: continue
            when (transition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    if (insideStore.handleTransition(locId, entered = true, hysteresisMs)) {
                        sink?.emit(TriggerEvent(TriggerType.LOCATION_ENTER, mapOf(Param.LOCATION_ID to locId.toString())))
                    }
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    if (insideStore.handleTransition(locId, entered = false, hysteresisMs)) {
                        sink?.emit(TriggerEvent(TriggerType.LOCATION_EXIT, mapOf(Param.LOCATION_ID to locId.toString())))
                    }
                }
                Geofence.GEOFENCE_TRANSITION_DWELL -> {
                    // Dwell means "still here after loiteringDelay": fire a dwell event.
                    insideStore.handleTransition(locId, entered = true, hysteresisMs)
                    sink?.emit(TriggerEvent(TriggerType.LOCATION_DWELL, mapOf(Param.LOCATION_ID to locId.toString())))
                }
            }
        }
    }

    companion object {
        private const val TAG = "LocationProvider"
        private const val GEOFENCE_REQ_CODE = 4001
        val LOCATION_TRIGGERS = setOf(
            TriggerType.LOCATION_ENTER,
            TriggerType.LOCATION_EXIT,
            TriggerType.LOCATION_DWELL,
        )

        /**
         * Everything this provider can serve via GPS geofence, including explicit
         * cell-tower watches routed here when low-power mode is off (or overridden).
         * The engine matches place transitions across sensing paths.
         */
        val GPS_TRIGGERS = LOCATION_TRIGGERS + setOf(
            TriggerType.CELL_TOWER_ENTER,
            TriggerType.CELL_TOWER_EXIT,
            TriggerType.CELL_TOWER_DWELL,
        )
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) return
        val provider = dev.context.app.ContextApp.container.locationProvider
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                provider.onGeofencingEvent(context.applicationContext, event)
            } finally {
                result.finish()
            }
        }
    }
}