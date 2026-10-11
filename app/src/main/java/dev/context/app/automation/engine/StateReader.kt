package dev.context.app.automation.engine

import dev.context.app.model.TriggerType

/**
 * Provides the current device state snapshot on demand. Implemented by the platform layer;
 * the engine only ever talks to this interface.
 */
interface StateReader {
    suspend fun snapshot(): RuntimeSnapshot

    /** True/False when the trigger's situation is currently happening; null when unknown. */
    suspend fun triggerTruth(type: TriggerType, params: Map<String, String>): Boolean?
}