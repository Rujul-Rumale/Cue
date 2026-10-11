package dev.context.app.triggers

import dev.context.app.automation.engine.TriggerEvent

/** Receives normalized events from providers. The app container implements this. */
fun interface ProviderSink {
    /** Thread-safe, fire-and-forget dispatch of a normalized device event. */
    fun emit(event: TriggerEvent)
}

/** Base contract for a trigger provider: register platform listeners on start/boot. */
interface TriggerProvider {
    fun register(context: android.content.Context, sink: ProviderSink)
}