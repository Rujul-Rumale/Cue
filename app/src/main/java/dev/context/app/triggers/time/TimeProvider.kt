package dev.context.app.triggers.time

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.TimeSchedule
import dev.context.app.model.Automation
import dev.context.app.model.Param
import dev.context.app.model.Shared
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Schedules time-of-day triggers with AlarmManager. The app sleeps; an alarm wakes us at the
 * configured time, the receiver emits one normalized event, and reschedules the next day.
 *
 * Exact alarms are used when the platform allows (API 31+ needs SCHEDULE_EXACT_ALARM),
 * otherwise a near-exact `setAndAllowWhileIdle` is used.
 */
object TimeProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var appContext: Context? = null

override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        appContext = context.applicationContext
    }

    /** Re-sync every time-of-day alarm to match the current automation set. */
    fun sync(automations: List<Automation>) {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        automations.forEach { cancel(ctx, am, it.id) }
        automations
            .filter { it.enabled && it.trigger.type == TriggerType.TIME_AT }
            .forEach { schedule(ctx, am, it) }
    }

    fun scheduleForAutomation(automation: Automation) {
        val ctx = appContext ?: return
        sync(listOf(automation))
    }

    private fun schedule(ctx: Context, am: AlarmManager, automation: Automation) {
        val p = automation.trigger.params
        val hour = p[Param.HOUR]?.toIntOrNull() ?: 0
        val minute = p[Param.MINUTE]?.toIntOrNull() ?: 0
        val days = p[Param.DAYS]?.toIntOrNull() ?: Shared.ALL_DAYS
        val at = nextOccurrence(hour, minute, days)
        if (at == null) return
        val intent = alarmIntent(ctx, automation.id, hour, minute, days)
        val pi = pendingIntent(ctx, automation.id, intent)
        try {
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (t: SecurityException) {
            // SCHEDULE_EXACT_ALARM revoked after registration - fall back to inexact
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (t2: Throwable) {
                // give up quietly; exactness is a bonus, not a requirement
            }
        }
    }

    private fun cancel(ctx: Context, am: AlarmManager, automationId: Long) {
        // FLAG_NO_CREATE fetches the live PendingIntent without mutating it:
        // the old code rebuilt it with FLAG_UPDATE_CURRENT (zeroed extras) first.
        // Extras are ignored for alarm matching, but never clobber state you use.
        val existing = PendingIntent.getBroadcast(
            ctx,
            automationId.toInt(),
            alarmIntent(ctx, automationId, 0, 0, 0),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        am.cancel(existing)
        existing.cancel()
    }

private fun pendingIntent(ctx: Context, automationId: Long, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(ctx, automationId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

    private fun alarmIntent(ctx: Context, automationId: Long, hour: Int, minute: Int, days: Int): Intent =
        Intent(ctx, TimeAlarmReceiver::class.java)
            .putExtra(EXTRA_ID, automationId)
            .putExtra(EXTRA_HOUR, hour)
            .putExtra(EXTRA_MINUTE, minute)
            .putExtra(EXTRA_DAYS, days)

private fun nextOccurrence(hour: Int, minute: Int, days: Int): Long? =
        TimeSchedule.nextOccurrence(hour, minute, days, LocalDateTime.now())

    internal fun onAlarm(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0) return
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val days = intent.getIntExtra(EXTRA_DAYS, Shared.ALL_DAYS)
        val today = LocalDate.now().toString()
        sink?.emit(
            TriggerEvent(
                type = TriggerType.TIME_AT,
                params = mapOf(
                    Param.HOUR to hour.toString(),
                    Param.MINUTE to minute.toString(),
                    Param.DAYS to days.toString(),
                    Param.DATE to today,
                ),
            ),
        )
        // Schedule tomorrow's occurrence.
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent2 = alarmIntent(context, id, hour, minute, days)
        val pi = pendingIntent(context, id, intent2)
        val at = nextOccurrence(hour, minute, days) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (t: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    private const val EXTRA_ID = "automationId"
    private const val EXTRA_HOUR = "hour"
    private const val EXTRA_MINUTE = "minute"
    private const val EXTRA_DAYS = "days"
}

class TimeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimeProvider.onAlarm(context, intent)
    }
}
