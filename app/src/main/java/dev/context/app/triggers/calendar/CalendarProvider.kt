package dev.context.app.triggers.calendar

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Calendar event triggers. Watches the calendar provider for changes, then
 * looks for instances starting/ending within a small window around now.
 * Occurrence-style: each instance carries its id + begin time, so repeats
 * across days are distinct signatures, not latch state.
 */
class CalendarProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var contextRef: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: ContentObserver? = null

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        contextRef = context.applicationContext
        if (observer == null) {
            val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    evaluate()
                }
            }
            observer = obs
            runCatching {
                context.contentResolver.registerContentObserver(
                    CalendarContract.Instances.CONTENT_URI, true, obs,
                )
            }.onFailure { android.util.Log.w(TAG, "calendar observer failed", it) }
        }
        // Evaluate once at start so events near process launch are not missed.
        evaluate()
    }

    private fun evaluate() {
        val context = contextRef ?: return
        if (!hasPermission(context)) return
        scope.launch {
            val now = Clock.SYSTEM.nowMillis()
            findBoundaryInstances(context, now, starting = true).forEach { (id, title, begin) ->
                sink?.emit(
                    TriggerEvent(
                        TriggerType.CALENDAR_EVENT_START,
                        mapOf(
                            Param.EVENT_ID to id,
                            Param.NOTIF_TITLE to title,
                            Param.AT to begin.toString(),
                        ),
                    ),
                )
            }
            findBoundaryInstances(context, now, starting = false).forEach { (id, title, end) ->
                sink?.emit(
                    TriggerEvent(
                        TriggerType.CALENDAR_EVENT_END,
                        mapOf(
                            Param.EVENT_ID to id,
                            Param.NOTIF_TITLE to title,
                            Param.AT to end.toString(),
                        ),
                    ),
                )
            }
        }
    }

    private fun findBoundaryInstances(context: Context, now: Long, starting: Boolean): List<Triple<String, String, Long>> {
        val out = mutableListOf<Triple<String, String, Long>>()
        val windowMs = 150_000L // 2.5 min each side of now
        val begin = now - windowMs
        val end = now + windowMs
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .let { android.content.ContentUris.appendId(it, begin) }
            .let { android.content.ContentUris.appendId(it, end) }
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
                while (c.moveToNext()) {
                    val mark = if (starting) c.getLong(beginIdx) else c.getLong(endIdx)
                    if (mark in (now - windowMs)..now) {
                        out.add(
                            Triple(
                                c.getLong(idIdx).toString(),
                                c.getString(titleIdx).orEmpty(),
                                mark,
                            ),
                        )
                    }
                }
            }
        }.onFailure { android.util.Log.w(TAG, "calendar query failed", it) }
        return out
    }

    private fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CalendarProvider"
    }
}
