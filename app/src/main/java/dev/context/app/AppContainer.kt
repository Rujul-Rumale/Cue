package dev.context.app

import android.content.Context
import android.content.Intent
import dev.context.app.automation.debug.DebugProvider
import dev.context.app.automation.engine.ActionExecutor
import dev.context.app.automation.engine.AutomationEngine
import dev.context.app.automation.engine.ConditionEvaluator
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.automation.repository.AutomationRepository
import dev.context.app.automation.service.AutomationForegroundService
import dev.context.app.actions.AndroidActionExecutor
import dev.context.app.core.Clock
import dev.context.app.model.TriggerType
import dev.context.app.storage.datastore.SettingsRepository
import dev.context.app.storage.room.ContextDatabase
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import dev.context.app.triggers.activity.ActivityProvider
import dev.context.app.triggers.audio.AudioProvider
import dev.context.app.triggers.battery.BatteryProvider
import dev.context.app.triggers.bluetooth.BluetoothProvider
import dev.context.app.triggers.call.CallStateProvider
import dev.context.app.triggers.calendar.CalendarProvider
import dev.context.app.triggers.celltower.CellTowerProvider
import dev.context.app.triggers.device.DeviceProvider
import dev.context.app.triggers.location.LocationInsideStore
import dev.context.app.triggers.location.LocationProvider
import dev.context.app.triggers.logcat.LogcatProvider
import dev.context.app.triggers.motion.MotionProvider
import dev.context.app.triggers.platform.AndroidStateReader
import dev.context.app.triggers.time.TimeProvider
import dev.context.app.triggers.wifi.WifiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manual dependency container. Everything the app needs is constructed here; there is no DI
 * framework, keeping the dependency graph small and auditable.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val clock: Clock = Clock.SYSTEM

    val db: ContextDatabase = ContextDatabase.get(appContext)
    val repository: AutomationRepository = AutomationRepository(db)
    val settings: SettingsRepository = SettingsRepository(appContext)
    val insideStore: LocationInsideStore = LocationInsideStore(appContext, clock)
    val stateReader: AndroidStateReader = AndroidStateReader(appContext, insideStore, clock)
    val evaluator: ConditionEvaluator = ConditionEvaluator(clock)
    val executor: ActionExecutor = AndroidActionExecutor(appContext, repository)
    val engine: AutomationEngine = AutomationEngine(repository, stateReader, evaluator, executor, clock)

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val locationProvider = LocationProvider(insideStore, settings, clock)
    val cellTowerProvider = CellTowerProvider(settings, clock)
    val calendarProvider = CalendarProvider()
    val callStateProvider = CallStateProvider()
    val logcatProvider = LogcatProvider()

    private val sink = ProviderSink { event -> dispatch(event) }

    private val providers: List<TriggerProvider> = listOf(
        BatteryProvider,
        BluetoothProvider,
        WifiProvider,
        DeviceProvider,
        AudioProvider,
        ActivityProvider,
        TimeProvider,
        locationProvider,
        cellTowerProvider,
        MotionProvider,
        calendarProvider,
        callStateProvider,
        logcatProvider,
        DebugProvider,
    )

    /** Idempotent; safe to call on app start, on boot, on process restart and after changes. */
    fun start() {
        providers.forEach { it.register(appContext, sink) }
        scope.launch {
            engine.reconcile()
            refreshSubscriptions()
        }
        // Start foreground service for persistent automation engine
        startForegroundService()
    }

    private fun startForegroundService() {
        scope.launch {
            val show = runCatching { settings.fgServiceNotification.first() }.getOrDefault(true)
            runCatching { AutomationForegroundService.start(appContext, show) }
                .onFailure { android.util.Log.w("ContextApp", "foreground service start failed", it) }
        }
    }

    /** Rebuilds the service notification, e.g. after the visibility toggle changes. */
    fun restartForegroundService() {
        scope.launch {
            val show = runCatching { settings.fgServiceNotification.first() }.getOrDefault(true)
            runCatching {
                appContext.stopService(Intent(appContext, AutomationForegroundService::class.java))
                AutomationForegroundService.start(appContext, show)
            }.onFailure { android.util.Log.w("ContextApp", "foreground service restart failed", it) }
        }
    }

    /** Re-applies time alarms, activity recognition, geofences and cell watches. */
    suspend fun refreshSubscriptions() {
        val automations = repository.getAutomations()
        val locations = repository.getLocations()
        TimeProvider.sync(automations)
        ActivityProvider.sync(automations)
        MotionProvider.sync(automations)
        callStateProvider.sync(automations)
        logcatProvider.sync(automations)
        val lowPower = runCatching { settings.lowPowerMode.first() }.getOrDefault(false)
        // CELL_TRIGGERS covers both LOCATION_* and CELL_TOWER_* place triggers.
        val (cellAutos, gpsAutos) = automations.partition { a ->
            lowPower && !a.useGpsForLocation && a.trigger.type in CellTowerProvider.CELL_TRIGGERS
        }
        locationProvider.sync(gpsAutos, locations)
        cellTowerProvider.sync(cellAutos, locations)
    }

    /** Entry point for all normalized events (providers, receivers, debug simulator). */
    fun dispatch(event: TriggerEvent) {
        scope.launch {
            runCatching { engine.handleEvent(event) }
                .onFailure { android.util.Log.w("ContextEngine", "event failed", it) }
        }
    }

    /** After any automation/location CRUD, keep subscriptions in sync. */
    fun notifyDataChanged() {
        scope.launch { refreshSubscriptions() }
    }
}