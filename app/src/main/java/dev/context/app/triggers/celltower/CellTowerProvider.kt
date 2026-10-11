package dev.context.app.triggers.celltower

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Automation
import dev.context.app.model.LocationMarker
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.storage.datastore.SettingsRepository
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val Context.cellStateDataStore by preferencesDataStore(name = "cell_state")

/**
 * Low-power location triggers backed by cellular identities instead of GPS.
 *
 * Zero GPS, ~zero extra battery: the modem already tracks cells, we only listen
 * for change callbacks. Places carry a fingerprint ([LocationMarker.cellIds])
 * learned via "Learn current cell" in the UI; a place counts as inside when any
 * currently visible cell is in its fingerprint.
 *
 * Emits canonical LOCATION_ENTER / LOCATION_EXIT / LOCATION_DWELL events so the
 * engine treats sensing paths uniformly (see EnginePolicy location families).
 * Inside state is persisted separately from the GPS store and self-heals on the
 * first read after a process restart: a silent init when already inside (mirrors
 * reconcile), plus an EXIT emission when the user left while we were dead.
 */
class CellTowerProvider(
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.SYSTEM,
) : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var contextRef: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var wanted: List<Automation> = emptyList()

    @Volatile
    private var places: List<LocationMarker> = emptyList()

    // In-memory inside state, seeded from the persisted store on first read.
    private val inside = mutableMapOf<Long, Boolean>()
    private val insideSince = mutableMapOf<Long, Long>()
    private val dwelled = mutableSetOf<Long>()

    private var telephonyCallback: TelephonyCallback? = null

    @Suppress("DEPRECATION")
    private var legacyListener: android.telephony.PhoneStateListener? = null

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        contextRef = context.applicationContext
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (!hasPermission(context)) return
        if (Build.VERSION.SDK_INT >= 29) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
                override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
                    onCells(cellInfo.mapNotNull { cellKey(it) }.toSet())
                }
            }
            runCatching {
                tm.registerTelephonyCallback(callbackExecutor, cb)
                telephonyCallback = cb
            }.onFailure { android.util.Log.w(TAG, "registerTelephonyCallback failed", it) }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : android.telephony.PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCellInfoChanged(cellInfo: List<CellInfo>?) {
                    onCells(cellInfo.orEmpty().mapNotNull { cellKey(it) }.toSet())
                }
            }
            runCatching {
                tm.listen(listener, android.telephony.PhoneStateListener.LISTEN_CELL_INFO)
                legacyListener = listener
            }.onFailure { android.util.Log.w(TAG, "listen CELL_INFO failed", it) }
        }
        // Seed an initial evaluation so places work without waiting for a cell change.
        scope.launch { initialRead() }
    }

    /**
     * Rebuilds which automations/places are evaluated from cell data.
     * The caller ([dev.context.app.AppContainer.refreshSubscriptions]) partitions by
     * low-power mode; this provider serves whatever it is given.
     */
    fun sync(automations: List<Automation>, locations: List<LocationMarker>) {
        wanted = automations.filter { it.enabled && it.trigger.type in CELL_TRIGGERS }
        places = locations
        if (wanted.isNotEmpty()) scope.launch { initialRead() }
    }

    /** One-shot read for "Learn current cell" in the UI. Empty when unavailable. */
    suspend fun readCellsOnce(): List<String> {
        val context = contextRef ?: return emptyList()
        if (!hasPermission(context)) return emptyList()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return emptyList()
        return runCatching { tm.allCellInfo.orEmpty().mapNotNull { cellKey(it) } }
            .getOrDefault(emptyList())
            .distinct()
    }

    private suspend fun initialRead() {
        val context = contextRef ?: return
        if (wanted.isEmpty() || !hasPermission(context)) return
        val visible = readCellsOnce().toSet()
        val now = clock.nowMillis()
        for (locId in wantedLocationIds()) {
            val marker = places.firstOrNull { it.id == locId } ?: continue
            val nowInside = CellFingerprint.matches(marker.cellIds, visible)
            val wasInside = persistedInside(context, locId)
            if (wasInside == null) {
                // Unknown history: initialize silently like reconcile does.
                setInside(context, locId, nowInside, now)
            } else if (wasInside && !nowInside) {
                // Left while we were dead: emit EXIT so the engine resets.
                setInside(context, locId, false, now)
                emit(TriggerType.LOCATION_EXIT, locId)
            } else if (!wasInside && nowInside) {
                setInside(context, locId, true, now)
            }
        }
    }

    private fun onCells(visible: Set<String>) {
        if (sink == null || wanted.isEmpty()) return
        val now = clock.nowMillis()
        scope.launch {
            val ctx = contextRef ?: return@launch
            val hyst = runCatching { settings.currentHysteresisMs() }.getOrDefault(0L)
            for (locId in wantedLocationIds()) {
                val marker = places.firstOrNull { it.id == locId } ?: continue
                val nowInside = CellFingerprint.matches(marker.cellIds, visible)
                val wasInside = inside[locId] ?: continue
                if (nowInside && !wasInside) {
                    setInside(ctx, locId, true, now)
                    emit(TriggerType.LOCATION_ENTER, locId)
                } else if (!nowInside && wasInside) {
                    val since = insideSince[locId] ?: 0L
                    if (now - since < hyst) continue
                    setInside(ctx, locId, false, now)
                    emit(TriggerType.LOCATION_EXIT, locId)
                } else if (nowInside && wasInside) {
                    evaluateDwell(locId, now)
                }
            }
        }
    }

    private suspend fun evaluateDwell(locId: Long, now: Long) {
        if (locId in dwelled) return
        val minMs = wanted
            .filter {
                (it.trigger.type == TriggerType.LOCATION_DWELL ||
                    it.trigger.type == TriggerType.CELL_TOWER_DWELL) &&
                    it.trigger.params[Param.LOCATION_ID]?.toLongOrNull() == locId
            }
            .mapNotNull { it.trigger.params[Param.DWELL_MINUTES]?.toIntOrNull() }
            .minOrNull() ?: return
        val since = insideSince[locId] ?: return
        if (now - since >= minMs * 60_000L) {
            dwelled.add(locId)
            emit(TriggerType.LOCATION_DWELL, locId)
        }
    }

    private suspend fun setInside(context: Context, locId: Long, value: Boolean, now: Long) {
        inside[locId] = value
        if (value) {
            insideSince[locId] = now
        } else {
            insideSince.remove(locId)
            dwelled.remove(locId)
        }
        runCatching {
            context.cellStateDataStore.edit { p ->
                p[booleanPreferencesKey("inside_$locId")] = value
                if (value) p[longPreferencesKey("enter_at_$locId")] = now
            }
        }
    }

    private suspend fun persistedInside(context: Context, locId: Long): Boolean? {
        return runCatching {
            context.cellStateDataStore.data.first()[booleanPreferencesKey("inside_$locId")]
        }.getOrNull()
    }

    private fun wantedLocationIds(): Set<Long> =
        wanted.mapNotNull { it.trigger.params[Param.LOCATION_ID]?.toLongOrNull() }.toSet()

    private fun emit(type: TriggerType, locId: Long) {
        sink?.emit(TriggerEvent(type, mapOf(Param.LOCATION_ID to locId.toString())))
    }

    private fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CellTowerProvider"
        val CELL_TRIGGERS = setOf(
            TriggerType.LOCATION_ENTER,
            TriggerType.LOCATION_EXIT,
            TriggerType.LOCATION_DWELL,
            TriggerType.CELL_TOWER_ENTER,
            TriggerType.CELL_TOWER_EXIT,
            TriggerType.CELL_TOWER_DWELL,
        )
    }
}

private fun cellKey(info: CellInfo): String? {
    val id = info.cellIdentity ?: return null
    return when (id) {
        is CellIdentityLte -> CellFingerprint.key(
            id.mccString, id.mncString, id.tac.toString(), id.ci.toString(),
        )
        is CellIdentityGsm -> CellFingerprint.key(
            id.mccString, id.mncString, id.lac.toString(), id.cid.toString(),
        )
        is CellIdentityWcdma -> CellFingerprint.key(
            id.mccString, id.mncString, id.lac.toString(), id.cid.toString(),
        )
        // CellIdentityNr only exists on API 29+; anything else falls through to
        // the compat branch below so this file verifies on API 26+.
        else -> CellFingerprint.key(id.mccStringCompat(), id.mncStringCompat(), null, id.cellIdCompat())
    }
}

private fun android.telephony.CellIdentity.cellIdCompat(): String? = runCatching {
    val candidates = listOf("getCi", "getCid", "getNci", "getPci", "getBasestationId")
    for (name in candidates) {
        val m = runCatching { this::class.java.getMethod(name) }.getOrNull() ?: continue
        val v = runCatching { m.invoke(this) }.getOrNull() ?: continue
        val s = v.toString()
        if (s.isNotBlank() && s != "-1" && s != "2147483647") return s
    }
    null
}.getOrNull()

private fun android.telephony.CellIdentity.mccStringCompat(): String? = runCatching {
    val m = this::class.java.getMethod("getMccString")
    m.invoke(this) as? String
}.getOrNull()

private fun android.telephony.CellIdentity.mncStringCompat(): String? = runCatching {
    val m = this::class.java.getMethod("getMncString")
    m.invoke(this) as? String
}.getOrNull()
