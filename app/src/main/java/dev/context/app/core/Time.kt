package dev.context.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Wall-clock helpers that keep the engine portable and testable. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
    }
}

fun Clock.nowDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis()), zone)

fun Clock.nowDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis()), zone)

/** Minutes since midnight. */
fun LocalDateTime.minuteOfDay(): Int = hour * 60 + minute

/** day-of-week bitmask (bit1=Mon..bit7=Sun). */
fun LocalDate.weekdayMask(): Int {
    val iso = this.dayOfWeek.value // 1=Mon..7=Sun
    return 1 shl (iso - 1)
}

fun formatTimestamp(epochMillis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        .format(Clock.FORMATTER)