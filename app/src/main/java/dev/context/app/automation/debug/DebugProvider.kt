package dev.context.app.automation.debug

import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider

/**
 * Simulation entry point for the debug screen. Lets the user fire any normalized
 * event exactly as a real provider would, while the "source" remains auditable.
 */
object DebugProvider : TriggerProvider {
    @Volatile
    private var sink: ProviderSink? = null

    override fun register(context: android.content.Context, sink: ProviderSink) {
        this.sink = sink
    }

    fun emit(event: TriggerEvent) {
        sink?.emit(event.copy(source = "debug"))
    }
}