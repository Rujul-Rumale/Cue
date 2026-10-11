package dev.context.app.automation.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.context.app.automation.repository.AutomationRepository
import dev.context.app.model.ActionExecution
import dev.context.app.model.ActionSpec
import dev.context.app.model.ActionType
import dev.context.app.model.Automation
import dev.context.app.model.ConditionSpec
import dev.context.app.model.ConditionType
import dev.context.app.model.ExecutionMode
import dev.context.app.model.ExecutionLog
import dev.context.app.model.LogResult
import dev.context.app.model.Param
import dev.context.app.model.TriggerSpec
import dev.context.app.model.TriggerType
import dev.context.app.storage.room.ContextDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationEngineTest {

    private lateinit var db: ContextDatabase
    private lateinit var repository: AutomationRepository
    private lateinit var fakeReader: FakeStateReader
    private lateinit var fakeExecutor: FakeActionExecutor
    private lateinit var engine: AutomationEngine

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ContextDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AutomationRepository(db)
        fakeReader = FakeStateReader()
        fakeExecutor = FakeActionExecutor()
        engine = AutomationEngine(repository, fakeReader, ConditionEvaluator(), fakeExecutor)
        fakeExecutor.runs = 0
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun batteryBelow_firesOnTransitionOncePerArm() = runBlocking {
        val a = repository.saveAutomation(
            Automation(
                name = "Low battery",
                trigger = TriggerSpec(TriggerType.BATTERY_BELOW, params = mapOf(Param.THRESHOLD to "50")),
                actions = listOf(ActionSpec(ActionType.LOG_MESSAGE)),
            ),
        )
        fakeReader.snapshot = RuntimeSnapshot(batteryPercent = 45)
        engine.handleEvent(TriggerEvent(TriggerType.BATTERY_CHANGED, emptyMap(), timestamp = 1_000L))
        assertEquals(1, fakeExecutor.runs)
        assertEquals(LogResult.SUCCESS, latestLog()?.result)
        // Same level -> no second fire
        engine.handleEvent(TriggerEvent(TriggerType.BATTERY_CHANGED, emptyMap(), timestamp = 2_000L))
        assertEquals(1, fakeExecutor.runs)
        // Level recovers -> disarm
        fakeReader.snapshot = RuntimeSnapshot(batteryPercent = 85)
        engine.handleEvent(TriggerEvent(TriggerType.BATTERY_CHANGED, emptyMap(), timestamp = 3_000L))
        assertEquals(1, fakeExecutor.runs)
        // Falls again -> fires again
        fakeReader.snapshot = RuntimeSnapshot(batteryPercent = 40)
        engine.handleEvent(TriggerEvent(TriggerType.BATTERY_CHANGED, emptyMap(), timestamp = 4_000L))
        assertEquals(2, fakeExecutor.runs)
    }

    @Test
    fun batteryChanged_firesOnEveryNewLevel() = runBlocking {
        repository.saveAutomation(
            Automation(
                name = "Level watcher",
                trigger = TriggerSpec(TriggerType.BATTERY_CHANGED),
                actions = listOf(ActionSpec(ActionType.LOG_MESSAGE)),
            ),
        )
        engine.handleEvent(TriggerEvent(TriggerType.BATTERY_CHANGED, mapOf(Param.THRESHOLD to "45"), timestamp = 1_000L))
        assertEquals(1, fakeExecutor.runs)
        // Same level replayed -> deduped, not re-fired
        engine.handleEvent(TriggerEvent(TriggerType.BATTERY_CHANGED, mapOf(Param.THRESHOLD to "45"), timestamp = 2_000L))
        assertEquals(1, fakeExecutor.runs)
        // New level -> fires again (recurring, not latch-once)
        engine.handleEvent(TriggerEvent(TriggerType.BATTERY_CHANGED, mapOf(Param.THRESHOLD to "44"), timestamp = 3_000L))
        assertEquals(2, fakeExecutor.runs)
    }

    @Test
    fun timeAt_dedupesSameSignature() = runBlocking {
        val a = repository.saveAutomation(
            Automation(
                name = "Clock",
                trigger = TriggerSpec(TriggerType.TIME_AT, params = mapOf(Param.HOUR to "9", Param.MINUTE to "0", Param.DAYS to "127")),
                actions = listOf(ActionSpec(ActionType.LOG_MESSAGE)),
            ),
        )
        val ev = TriggerEvent(TriggerType.TIME_AT, mapOf(Param.HOUR to "9", Param.MINUTE to "0", Param.DAYS to "127", Param.DATE to "2026-09-20"), timestamp = 1_000L)
        engine.handleEvent(ev)
        assertEquals(1, fakeExecutor.runs)
        // Same logical occurrence replayed -> skipped
        engine.handleEvent(ev)
        assertEquals(1, fakeExecutor.runs)
        // Next day -> new signature -> fires
        val next = ev.copy(params = ev.params + (Param.DATE to "2026-09-21"), timestamp = 2_000L)
        engine.handleEvent(next)
        assertEquals(2, fakeExecutor.runs)
    }

    @Test
    fun cooldown_respectsLastFiredAt() = runBlocking {
        repository.saveAutomation(
            Automation(
                name = "Cooldown",
                trigger = TriggerSpec(TriggerType.BOOT_COMPLETED),
                mode = ExecutionMode.COOLDOWN,
                cooldownMinutes = 1,
                actions = listOf(ActionSpec(ActionType.LOG_MESSAGE)),
            ),
        )
        // Distinct signatures so dedupe does not interfere
        engine.handleEvent(TriggerEvent(TriggerType.BOOT_COMPLETED, mapOf(Param.NAME to "boot1"), timestamp = 1_000L))
        assertEquals(1, fakeExecutor.runs)
        engine.handleEvent(TriggerEvent(TriggerType.BOOT_COMPLETED, mapOf(Param.NAME to "boot2"), timestamp = 31_000L))
        assertEquals(1, fakeExecutor.runs)
        engine.handleEvent(TriggerEvent(TriggerType.BOOT_COMPLETED, mapOf(Param.NAME to "boot3"), timestamp = 61_000L))
        assertEquals(2, fakeExecutor.runs)
    }

    @Test
    fun conditionsThatFail_logConditionFailAndDoNotExecute() = runBlocking {
        repository.saveAutomation(
            Automation(
                name = "Gated",
                trigger = TriggerSpec(TriggerType.BOOT_COMPLETED),
                conditions = listOf(ConditionSpec(ConditionType.BATTERY, params = mapOf(Param.BELOW_PERCENT to "40"))),
                actions = listOf(ActionSpec(ActionType.LOG_MESSAGE)),
            ),
        )
        fakeReader.snapshot = RuntimeSnapshot(batteryPercent = 80)
        engine.handleEvent(TriggerEvent(TriggerType.BOOT_COMPLETED, mapOf(Param.NAME to "boot1"), timestamp = 1_000L))
        assertEquals(0, fakeExecutor.runs)
        assertEquals(LogResult.CONDITION_FAIL, latestLog()?.result)
    }

    @Test
    fun locationEnter_firesOncePerTransition() = runBlocking {
        val locId = repository.saveLocation(
            dev.context.app.model.LocationMarker(name = "Home", latitude = 1.0, longitude = 1.0, radiusMeters = 100),
        )
        repository.saveAutomation(
            Automation(
                name = "Geofence",
                trigger = TriggerSpec(TriggerType.LOCATION_ENTER, params = mapOf(Param.LOCATION_ID to locId.toString())),
                actions = listOf(ActionSpec(ActionType.LOG_MESSAGE)),
            ),
        )
        val enter = TriggerEvent(TriggerType.LOCATION_ENTER, mapOf(Param.LOCATION_ID to locId.toString()), timestamp = 1_000L)
        engine.handleEvent(enter)
        assertEquals(1, fakeExecutor.runs)
        // Repeated broadcast of the same transition -> no re-fire
        engine.handleEvent(enter)
        assertEquals(1, fakeExecutor.runs)
        // Exit resets; enter again fires
        engine.handleEvent(TriggerEvent(TriggerType.LOCATION_EXIT, mapOf(Param.LOCATION_ID to locId.toString()), timestamp = 2_000L))
        engine.handleEvent(enter.copy(timestamp = 3_000L))
        assertEquals(2, fakeExecutor.runs)
    }

    @Test
    fun cellAndGpsPlaceEvents_aliasAcrossSensingPaths() = runBlocking {
        val locId = repository.saveLocation(
            dev.context.app.model.LocationMarker(name = "Home", latitude = 1.0, longitude = 1.0, radiusMeters = 100),
        )
        val params = mapOf(Param.LOCATION_ID to locId.toString())
        repository.saveAutomation(
            Automation(
                name = "Cell watcher",
                trigger = TriggerSpec(TriggerType.CELL_TOWER_ENTER, params = params),
                actions = listOf(ActionSpec(ActionType.LOG_MESSAGE)),
            ),
        )
        // GPS geofence enter also satisfies the cell-tower watch.
        engine.handleEvent(TriggerEvent(TriggerType.LOCATION_ENTER, params, timestamp = 1_000L))
        assertEquals(1, fakeExecutor.runs)
        // A GPS exit resets the cell watch (cross-path reset).
        engine.handleEvent(TriggerEvent(TriggerType.LOCATION_EXIT, params, timestamp = 2_000L))
        engine.handleEvent(TriggerEvent(TriggerType.LOCATION_ENTER, params, timestamp = 3_000L))
        assertEquals(2, fakeExecutor.runs)
    }

    @Test
    fun reconcileLinesUpStateWithoutFiring() = runBlocking {
        val locId = repository.saveLocation(
            dev.context.app.model.LocationMarker(name = "Home", latitude = 1.0, longitude = 1.0, radiusMeters = 100),
        )
        repository.saveAutomation(
            Automation(
                name = "Geofence",
                trigger = TriggerSpec(TriggerType.LOCATION_ENTER, params = mapOf(Param.LOCATION_ID to locId.toString())),
                actions = listOf(ActionSpec(ActionType.LOG_MESSAGE)),
            ),
        )
        fakeReader.snapshot = RuntimeSnapshot(locationsInside = setOf(locId))
        engine.reconcile()
        assertEquals(0, fakeExecutor.runs)
        val state = repository.getAutomations().first { it.trigger.type == TriggerType.LOCATION_ENTER }
        assertTrue(state.currentState)
    }

    private suspend fun latestLog(): ExecutionLog? = repository.getLogs().firstOrNull()

    private class FakeStateReader : StateReader {
        var snapshot: RuntimeSnapshot = RuntimeSnapshot()
        override suspend fun snapshot(): RuntimeSnapshot = snapshot

        override suspend fun triggerTruth(type: TriggerType, params: Map<String, String>): Boolean? =
            snapshot.locationsInside.isNotEmpty()
    }

    private class FakeActionExecutor : ActionExecutor {
        var runs: Int = 0
        override suspend fun execute(action: ActionSpec, snapshot: RuntimeSnapshot, event: TriggerEvent): ActionExecution {
            runs++
            return ActionExecution(action, ok = true)
        }
    }
}