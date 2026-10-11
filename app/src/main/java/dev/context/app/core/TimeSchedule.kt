package dev.context.app.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Pure next-occurrence math for time-of-day automations; fully unit-testable. */
object TimeSchedule {

    fun maskMatches(days: Int, date: LocalDate): Boolean {
        val iso = date.dayOfWeek.value // 1=Mon..7=Sun, bit layout matches day bits
        return days == 0 || (days and (1 shl (iso - 1))) != 0
    }

    /**
     * Millis of the next occurrence of [hour]:[minute] on a day whose weekday bit is set
     * in [days], strictly after [now]. Returns null when no occurrence exists within a week.
     */
    fun nextOccurrence(hour: Int, minute: Int, days: Int, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val target = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        var day = now.toLocalDate()
        for (offset in 0..7) {
            val candidate = LocalDateTime.of(day, target)
            if (candidate.isAfter(now) && maskMatches(days, day)) {
                return candidate.atZone(zone).toInstant().toEpochMilli()
            }
            day = day.plusDays(1)
        }
        return null
    }
}