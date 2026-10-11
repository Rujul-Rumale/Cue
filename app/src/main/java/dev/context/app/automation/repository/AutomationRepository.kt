package dev.context.app.automation.repository

import androidx.room.withTransaction
import dev.context.app.model.ActionExecution
import dev.context.app.model.ActionSpec
import dev.context.app.model.Automation
import dev.context.app.model.ConditionEvaluation
import dev.context.app.model.ConditionSpec
import dev.context.app.model.ExecutionLog
import dev.context.app.model.ExecutionMode
import dev.context.app.model.LogResult
import dev.context.app.model.LocationMarker
import dev.context.app.model.Param
import dev.context.app.model.Variable
import dev.context.app.model.VariableType
import dev.context.app.storage.room.ContextDatabase
import dev.context.app.storage.room.JsonCodec
import dev.context.app.storage.room.LocationEntity
import dev.context.app.storage.room.AutomationEntity
import dev.context.app.storage.room.ExecutionLogEntity
import dev.context.app.storage.room.VariableEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Serializers shared by the repository and import/export. */
object SpecJson {
    val trigger = dev.context.app.model.TriggerSpec.serializer()
    val automationList = ListSerializer(Automation.serializer())
    val locationList = ListSerializer(LocationMarker.serializer())
    val variableList = ListSerializer(Variable.serializer())
    val conditionList = ListSerializer(ConditionSpec.serializer())
    val actionList = ListSerializer(ActionSpec.serializer())
    val evaluationList = ListSerializer(ConditionEvaluation.serializer())
    val executionList = ListSerializer(ActionExecution.serializer())
    val string = String.serializer()
}

class AutomationRepository(private val db: ContextDatabase) {

    private companion object {
        const val MAX_LOGS = 200
    }

    // ---- Automations -------------------------------------------------------

    fun observeAutomations(): Flow<List<Automation>> =
        db.automationDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAutomations(): List<Automation> = db.automationDao().getAll().map { it.toDomain() }

    suspend fun getEnabledAutomations(): List<Automation> = db.automationDao().getEnabled().map { it.toDomain() }

    suspend fun getAutomation(id: Long): Automation? = db.automationDao().getById(id)?.toDomain()

    suspend fun saveAutomation(automation: Automation): Long {
        val id = db.automationDao().upsert(automation.toEntity())
        return id
    }

    suspend fun deleteAutomation(id: Long) {
        db.automationDao().delete(id)
        // A deleted automation leaves no history behind.
        db.logDao().deleteForAutomation(id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = db.automationDao().setEnabled(id, enabled)

    suspend fun updateRuntimeState(id: Long, lastFiredAt: Long, lastSignature: String, currentState: Boolean) =
        db.automationDao().updateRuntimeState(id, lastFiredAt, lastSignature, currentState)

    // ---- Locations ---------------------------------------------------------

    fun observeLocations(): Flow<List<LocationMarker>> =
        db.locationDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getLocations(): List<LocationMarker> = db.locationDao().getAll().map { it.toDomain() }

    suspend fun getLocation(id: Long): LocationMarker? = db.locationDao().getById(id)?.toDomain()

    suspend fun saveLocation(location: LocationMarker): Long = db.locationDao().upsert(location.toEntity())

    suspend fun deleteLocation(id: Long) {
        db.locationDao().delete(db.locationDao().getById(id) ?: return)
    }

    // ---- Logs --------------------------------------------------------------

    suspend fun addLog(log: ExecutionLog) {
        db.logDao().insert(log.toEntity())
        trimLogs()
    }

    private suspend fun trimLogs() {
        val count = db.logDao().count()
        if (count > MAX_LOGS) {
            // keep the newest MAX_LOGS by deleting old ones
            val all = db.logDao().getRecent(Int.MAX_VALUE)
            val cutoff = all.getOrNull(MAX_LOGS - 1)?.timestamp ?: return
            db.logDao().deleteOlderThan(cutoff)
        }
    }

fun observeLogs(limit: Int = MAX_LOGS): Flow<List<ExecutionLog>> =
        db.logDao().observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getLogs(limit: Int = MAX_LOGS): List<ExecutionLog> =
        db.logDao().getRecent(limit).map { it.toDomain() }

    suspend fun clearLogs() = db.logDao().deleteAll()

    suspend fun deleteLog(id: Long) = db.logDao().delete(id)

    // ---- Variables ---------------------------------------------------------

    fun observeVariables(): Flow<List<Variable>> =
        db.variableDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getVariable(key: String): Variable? = db.variableDao().get(key)?.toDomain()

    suspend fun getVariables(): List<Variable> = db.variableDao().getAll().map { it.toDomain() }

    suspend fun setVariable(variable: Variable) = db.variableDao().upsert(variable.toEntity())

    suspend fun deleteVariable(key: String) = db.variableDao().delete(key)

    suspend fun clearVariables() = db.variableDao().deleteAll()

    // ---- Import / restore --------------------------------------------------

    suspend fun replaceAll(
        automations: List<Automation>,
        locations: List<LocationMarker>,
        variables: List<Variable>,
    ) {
db.withTransaction {
            db.automationDao().deleteAll()
            db.locationDao().deleteAll()
            db.variableDao().deleteAll()
            for (a in automations) db.automationDao().upsert(a.toEntity())
            for (l in locations) db.locationDao().upsert(l.toEntity())
            for (v in variables) db.variableDao().upsert(v.toEntity())
        }
    }
}

private fun Automation.toEntity(): AutomationEntity = AutomationEntity(
    id = id,
    name = name,
    enabled = enabled,
    triggerJson = JsonCodec.json.encodeToString(SpecJson.trigger, trigger),
    conditionsJson = JsonCodec.json.encodeToString(SpecJson.conditionList, conditions),
    matchAny = matchAny,
    actionsJson = JsonCodec.json.encodeToString(SpecJson.actionList, actions),
    mode = mode.name,
    cooldownMinutes = cooldownMinutes,
    createdAt = createdAt,
    lastFiredAt = lastFiredAt,
    lastSignature = lastSignature,
    currentState = currentState,
    useGpsForLocation = useGpsForLocation,
)

private fun AutomationEntity.toDomain(): Automation = Automation(
    id = id,
    name = name,
    enabled = enabled,
    trigger = try {
        JsonCodec.json.decodeFromString(SpecJson.trigger, triggerJson)
    } catch (e: Exception) {
        dev.context.app.model.TriggerSpec(dev.context.app.model.TriggerType.BOOT_COMPLETED, emptyMap())
    },
    conditions = try { JsonCodec.json.decodeFromString(SpecJson.conditionList, conditionsJson) } catch (e: Exception) { emptyList() },
    matchAny = matchAny,
    actions = try { JsonCodec.json.decodeFromString(SpecJson.actionList, actionsJson) } catch (e: Exception) { emptyList() },
    mode = runCatching { ExecutionMode.valueOf(mode) }.getOrDefault(ExecutionMode.ONCE_PER_TRANSITION),
    cooldownMinutes = cooldownMinutes,
    createdAt = createdAt,
    lastFiredAt = lastFiredAt,
    lastSignature = lastSignature,
    currentState = currentState,
    useGpsForLocation = useGpsForLocation,
)

private val cellIdListSerializer = ListSerializer(String.serializer())

private fun LocationMarker.toEntity(): LocationEntity = LocationEntity(
    id = id, name = name, latitude = latitude, longitude = longitude,
    radiusMeters = radiusMeters, createdAt = createdAt,
    cellIds = JsonCodec.json.encodeToString(cellIdListSerializer, cellIds),
)

private fun LocationEntity.toDomain(): LocationMarker = LocationMarker(
    id = id, name = name, latitude = latitude, longitude = longitude,
    radiusMeters = radiusMeters, createdAt = createdAt,
    cellIds = try { JsonCodec.json.decodeFromString(cellIdListSerializer, cellIds) } catch (e: Exception) { emptyList() },
)

private fun ExecutionLog.toEntity(): ExecutionLogEntity = ExecutionLogEntity(
    id = id,
    automationId = automationId,
    automationName = automationName,
    timestamp = timestamp,
    result = result.name,
    triggerSummary = triggerSummary,
    evaluationsJson = JsonCodec.json.encodeToString(SpecJson.evaluationList, evaluations),
    executionsJson = JsonCodec.json.encodeToString(SpecJson.executionList, executions),
    detail = detail,
)

private fun ExecutionLogEntity.toDomain(): ExecutionLog = ExecutionLog(
    id = id,
    automationId = automationId,
    automationName = automationName,
    timestamp = timestamp,
    result = runCatching { LogResult.valueOf(result) }.getOrDefault(LogResult.FAILED),
    triggerSummary = triggerSummary,
    evaluations = try { JsonCodec.json.decodeFromString(SpecJson.evaluationList, evaluationsJson) } catch (e: Exception) { emptyList() },
    executions = try { JsonCodec.json.decodeFromString(SpecJson.executionList, executionsJson) } catch (e: Exception) { emptyList() },
    detail = detail,
)

private fun Variable.toEntity(): VariableEntity = VariableEntity(
    key = key, type = type.name, value = value, updatedAt = updatedAt,
)

private fun VariableEntity.toDomain(): Variable = Variable(
    key = key,
    type = runCatching { VariableType.valueOf(type) }.getOrDefault(VariableType.STRING),
    value = value,
    updatedAt = updatedAt,
)
