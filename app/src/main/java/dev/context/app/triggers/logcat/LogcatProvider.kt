package dev.context.app.triggers.logcat

import android.content.Context
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Automation
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Logcat pattern triggers for hidden framework/vendor events that expose no
 * broadcast (NFC scans, proprietary charging states, camera open/close).
 *
 * Requires the one-time ADB grant READ_LOGS; without it the stream only
 * contains our own logs and patterns simply never match. Monitoring runs only
 * while LOGCAT_MATCH automations exist, and reads (never writes) the buffer.
 */
class LogcatProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var patterns: List<String> = emptyList()

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
    }

    /** Starts/stops the reader to match the current automation set. */
    fun sync(automations: List<Automation>) {
        val next = automations
            .filter { it.enabled && it.trigger.type == TriggerType.LOGCAT_MATCH }
            .mapNotNull { it.trigger.params[Param.PATTERN]?.takeIf { p -> p.isNotBlank() } }
            .distinct()
        patterns = next
        if (next.isNotEmpty() && job == null) {
            job = scope.launch { readLoop() }
        } else if (next.isEmpty()) {
            job?.cancel()
            job = null
        }
    }

    private suspend fun readLoop() {
        val process = try {
            ProcessBuilder("logcat", "-v", "brief").redirectErrorStream(true).start()
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "logcat start failed", t)
            job = null
            return
        }
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!scope.coroutineContext.isActive) break
                    val hit = patterns.firstOrNull { pattern ->
                        runCatching { Regex(pattern).containsMatchIn(line) }.getOrDefault(false)
                    } ?: continue
                    val now = Clock.SYSTEM.nowMillis()
                    sink?.emit(
                        TriggerEvent(
                            TriggerType.LOGCAT_MATCH,
                            mapOf(
                                Param.PATTERN to hit,
                                Param.LINE to line.take(300),
                                Param.AT to now.toString(),
                            ),
                        ),
                    )
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "logcat read failed", t)
        } finally {
            runCatching { process.destroy() }
            job = null
        }
    }

    companion object {
        private const val TAG = "LogcatProvider"
    }
}
