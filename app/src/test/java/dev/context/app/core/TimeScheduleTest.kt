package dev.context.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class TimeScheduleTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun maskMatches_respectsWeekdayBits() {
        // 2026-09-20 is a Sunday (bit7 = 0b1000000 = 64)
        val sunday = LocalDate.of(2026, 9, 20)
        assertTrue(TimeSchedule.maskMatches(64, sunday))
        assertFalse(TimeSchedule.maskMatches(1, sunday)) // Monday bit not set
        assertTrue(TimeSchedule.maskMatches(0b1111111, sunday))
        assertTrue(TimeSchedule.maskMatches(0, sunday))
    }

    @Test
    fun nextOccurrence_findsNextValidWeekday() {
        // Now: Sunday 2026-09-20T10:00Z
        val now = LocalDateTime.of(2026, 9, 20, 10, 0)
        // Monday 09:00
        val monday900 = TimeSchedule.nextOccurrence(9, 0, 1, now, zone)
        assertNotNull(monday900)
        assertEquals(LocalDateTime.of(2026, 9, 21, 9, 0), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(monday900!!), zone))
    }

    @Test
    fun nextOccurrence_laterTodayWorks() {
        val now = LocalDateTime.of(2026, 9, 20, 10, 0)
        val today1900 = TimeSchedule.nextOccurrence(19, 0, 0b1111111, now, zone)
        assertEquals(LocalDateTime.of(2026, 9, 20, 19, 0), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(today1900!!), zone))
    }

    @Test
    fun nextOccurrence_skipsNonMatchingDays() {
        // Now: Sunday 2026-09-20T10:00Z; only Saturday (bit6=32) enabled -> next Saturday
        val now = LocalDateTime.of(2026, 9, 20, 10, 0)
        val saturday = TimeSchedule.nextOccurrence(11, 30, 32, now, zone)
        assertEquals(LocalDateTime.of(2026, 9, 26, 11, 30), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(saturday!!), zone))
    }

    @Test
    fun nextOccurrence_pastTimeTodayMovesToTomorrow() {
        // Now: Sunday 2026-09-20T10:00Z; want 09:00 all days -> tomorrow
        val now = LocalDateTime.of(2026, 9, 20, 10, 0)
        val next = TimeSchedule.nextOccurrence(9, 0, 0b1111111, now, zone)
        assertEquals(LocalDateTime.of(2026, 9, 21, 9, 0), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(next!!), zone))
    }

    @Test
    fun maskZeroMeansEveryDay() {
        // 2026-09-20 is a Sunday
        val sunday = LocalDate.of(2026, 9, 20)
        assertTrue(TimeSchedule.maskMatches(0, sunday))
        val now = LocalDateTime.of(2026, 9, 20, 10, 0)
        val next = TimeSchedule.nextOccurrence(9, 0, 0, now, zone)
        assertNotNull(next)
        // Time already passed today -> tomorrow, still valid under "0 = every day"
        assertEquals(LocalDateTime.of(2026, 9, 21, 9, 0), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(next!!), zone))
    }

    @Test
    fun nextOccurrence_clampsOutOfRangeClockValues() {
        val now = LocalDateTime.of(2026, 9, 20, 10, 0)
        val next = TimeSchedule.nextOccurrence(99, 99, 0b1111111, now, zone) // clamped to 23:59
        assertNotNull(next)
    }
}