package dev.context.app.automation.engine

import dev.context.app.automation.repository.AutomationRepository
import dev.context.app.core.Clock
import dev.context.app.model.ActionExecution
import dev.context.app.model.Automation
import dev.context.app.model.ExecutionLog
import dev.context.app.model.ExecutionMode
import dev.context.app.model.LogResult
import dev.context.app.model.Param
import dev.context.app.model.TriggerType

/**
 * The automation engine. Consumes normalized [TriggerEvent]s from providers and decides
 * whether any automation should run, whether its conditions pass, then fires its actions
 * and records an execution log.
 *
 * It is intentionally event-driven: no polling happens here. All Android-specific work is
 * hidden behind [StateReader] and [ActionExecutor].
 */
class AutomationEngine(
    private val repository: AutomationRepository,
    private val stateReader: StateReader,
    private val evaluator: ConditionEvaluator,
    private val executor: ActionExecutor,
    private val clock: Clock = Clock.SYSTEM,
) {

    /**
     * Called at app start (and after boot) to line up the persisted trigger state with
     * reality without firing anything. Prevents "this already happened before I created
     * the automation" surprise runs.
     */
    suspend fun reconcile() {
        val automations = repository.getAutomations()
        for (a in automations) {
            if (!EnginePolicy.isTransitional(a.trigger.type)) continue
            val truth = stateReader.triggerTruth(a.trigger.type, a.trigger.params) ?: continue
            if (truth != a.currentState) {
                repository.updateRuntimeState(a.id, a.lastFiredAt, a.lastSignature, truth)
            }
        }
    }

    suspend fun handleEvent(event: TriggerEvent) {
        val snapshot = stateReader.snapshot()
        val automations = repository.getEnabledAutomations()

        for (a in automations) {
            // Reset pass: the situation this automation watches has ended.
            if (isReset(a.trigger.type, event.type) &&
                EnginePolicy.paramsMatchAutomation(a.trigger.type, a.trigger.params, event.params)
            ) {
                if (a.currentState) {
                    repository.updateRuntimeState(a.id, a.lastFiredAt, a.lastSignature, false)
                }
            } else if (a.currentState && event.type == a.trigger.type && isExclusiveConnectionTrigger(a.trigger.type) &&
                !EnginePolicy.paramsMatchAutomation(a.trigger.type, a.trigger.params, event.params)
            ) {
                // Connected to a different network/device than the watched one:
                // that implies we are no longer connected to the watched one,
                // so re-arm instead of staying stuck on forever.
                repository.updateRuntimeState(a.id, a.lastFiredAt, a.lastSignature, false)
            }
        }

        val fresh = repository.getEnabledAutomations()
        for (a in fresh) {
            if (!eventTriggersAutomation(a, event)) continue
            processTriggerMatch(a, event, snapshot)
        }
    }

    /** Connected-to-X implies not-connected-to-Y, so a same-type event naming a
     * different concrete target ends the watched situation. Location is excluded:
     * entering place B says nothing about still being inside place A. */
    private fun isExclusiveConnectionTrigger(type: TriggerType): Boolean = when (type) {
        TriggerType.WIFI_CONNECTED, TriggerType.BLUETOOTH_CONNECTED -> true
        else -> false
    }

    /** Reset comparison is location-family aware: a GPS exit resets a cell-enter
     * watch and vice versa, since both sense the same place transition. */
    private fun isReset(trigger: TriggerType, event: TriggerType): Boolean {
        val expected = EnginePolicy.resetEvent(trigger) ?: return false
        if (expected == event) return true
        val family = EnginePolicy.locationFamily(expected)
        return family != -1 && family == EnginePolicy.locationFamily(event)
    }

    /** Does this event concern this automation's trigger at all? */
    private fun eventTriggersAutomation(a: Automation, event: TriggerEvent): Boolean {
        val typeMatches = when (a.trigger.type) {
            // Numeric battery thresholds evaluate against any battery-level event.
            TriggerType.BATTERY_BELOW, TriggerType.BATTERY_ABOVE, TriggerType.BATTERY_FULL ->
                event.type == TriggerType.BATTERY_CHANGED ||
                    event.type == a.trigger.type ||
                    event.type == TriggerType.BATTERY_FULL
            else -> {
                if (a.trigger.type == event.type) true
                else {
                    // Place transitions match across GPS/cell sensing paths.
                    val family = EnginePolicy.locationFamily(a.trigger.type)
                    family != -1 && family == EnginePolicy.locationFamily(event.type)
                }
            }
        }
        if (!typeMatches) return false
        return !EnginePolicy.triggerNeedsParamsMatch(a.trigger.type) ||
            EnginePolicy.paramsMatchAutomation(a.trigger.type, a.trigger.params, event.params)
    }

    private suspend fun processTriggerMatch(
        a: Automation,
        event: TriggerEvent,
        snapshot: RuntimeSnapshot,
    ) {
        when {
            !EnginePolicy.isTransitional(a.trigger.type) -> {
                // Recurring triggers (time-of-day, boot): each genuine occurrence fires once.
                val sig = event.signature()
                if (a.lastSignature == sig) return
                if (!cooldownOk(a, event.timestamp)) {
                    logCooldownSkip(a, event)
                    return
                }
                runAutomation(a, event, snapshot)
                repository.updateRuntimeState(a.id, event.timestamp, sig, true)
            }
            a.trigger.type == TriggerType.BATTERY_BELOW ||
                a.trigger.type == TriggerType.BATTERY_ABOVE ||
                a.trigger.type == TriggerType.BATTERY_FULL -> {
                val truth = batteryTruth(a, snapshot) ?: return
                if (truth && !a.currentState) {
                    if (!cooldownOk(a, event.timestamp)) {
                        logCooldownSkip(a, event)
                        return
                    }
                    runAutomation(a, event, snapshot)
                    repository.updateRuntimeState(a.id, event.timestamp, a.lastSignature, true)
                } else if (!truth) {
                    repository.updateRuntimeState(a.id, a.lastFiredAt, a.lastSignature, false)
                }
            }
            else -> {
                if (!a.currentState) {
                    if (!cooldownOk(a, event.timestamp)) {
                        logCooldownSkip(a, event)
                        return
                    }
                    runAutomation(a, event, snapshot)
                    repository.updateRuntimeState(a.id, event.timestamp, event.signature(), true)
                }
            }
        }
    }

    private suspend fun batteryTruth(a: Automation, snapshot: RuntimeSnapshot): Boolean? {
        val threshold = a.trigger.params[Param.THRESHOLD]?.toIntOrNull() ?: return null
        val level = snapshot.batteryPercent ?: return null
        return when (a.trigger.type) {
            TriggerType.BATTERY_BELOW -> level <= threshold
            TriggerType.BATTERY_ABOVE -> level >= threshold
            TriggerType.BATTERY_FULL -> snapshot.batteryFull == true
            else -> null
        }
    }

    private suspend fun logCooldownSkip(a: Automation, event: TriggerEvent) {
        val locationName = a.trigger.params[Param.LOCATION_ID]
            ?.toLongOrNull()
            ?.let { id -> repository.getLocation(id)?.name }
        repository.addLog(
            ExecutionLog(
                automationId = a.id,
                automationName = a.name,
                timestamp = event.timestamp,
                result = LogResult.COOLDOWN_SKIPPED,
                triggerSummary = a.trigger.type.shortLabel() + " " + a.triggerSummary(locationName),
            ),
        )
    }

    private fun cooldownOk(a: Automation, timestamp: Long): Boolean {
        if (a.mode != ExecutionMode.COOLDOWN || a.cooldownMinutes <= 0) return true
        if (a.lastFiredAt <= 0L) return true
        return timestamp - a.lastFiredAt >= a.cooldownMinutes * 60_000L
    }

    private suspend fun runAutomation(a: Automation, event: TriggerEvent, snapshot: RuntimeSnapshot) {
        val evaluations = evaluator.evaluate(a.conditions, snapshot, a.matchAny)
        val passed = a.conditions.isEmpty() ||
            (if (a.matchAny) evaluations.any { it.effectivePassed } else evaluations.all { it.effectivePassed })

        val locationName = a.trigger.params[Param.LOCATION_ID]
            ?.toLongOrNull()
            ?.let { id -> repository.getLocation(id)?.name }

        if (!passed) {
            repository.addLog(
                ExecutionLog(
                    automationId = a.id,
                    automationName = a.name,
                    timestamp = event.timestamp,
                    result = LogResult.CONDITION_FAIL,
                    triggerSummary = a.trigger.type.shortLabel() + " " + a.triggerSummary(locationName),
                    evaluations = evaluations,
                ),
            )
            return
        }

        val active = a.actions.filter { it.enabled }
        val realExecutions = if (active.isEmpty()) {
            listOf(ActionExecution(emptyAction(), ok = false, message = "no actions configured"))
        } else {
            active.map { action -> executor.execute(action, snapshot, event) }
        }
        val result = when {
            realExecutions.all { it.ok } -> LogResult.SUCCESS
            realExecutions.any { it.ok } -> LogResult.PARTIAL
            else -> LogResult.FAILED
        }
        repository.addLog(
            ExecutionLog(
                automationId = a.id,
                automationName = a.name,
                timestamp = event.timestamp,
                result = result,
                triggerSummary = a.trigger.type.shortLabel() + " " + a.triggerSummary(locationName),
                evaluations = evaluations,
                executions = realExecutions,
            ),
        )
    }

    private fun emptyAction() = dev.context.app.model.ActionSpec(
        type = dev.context.app.model.ActionType.LOG_MESSAGE,
        params = mapOf(Param.MESSAGE to "no actions configured"),
    )
}
