package dev.context.app.automation.engine

import dev.context.app.model.ActionExecution
import dev.context.app.model.ActionSpec

/**
 * Runs one automation action against the device. The engine treats the executor as a
 * black box; the fake executor is used in tests.
 */
interface ActionExecutor {
    suspend fun execute(action: ActionSpec, snapshot: RuntimeSnapshot, event: TriggerEvent): ActionExecution
}