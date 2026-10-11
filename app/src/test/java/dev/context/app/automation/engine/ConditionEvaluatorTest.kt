package dev.context.app.automation.engine

import dev.context.app.core.Clock
import dev.context.app.core.minuteOfDay
import dev.context.app.core.nowDate
import dev.context.app.core.nowDateTime
import dev.context.app.core.weekdayMask
import dev.context.app.model.ConditionSpec
import dev.context.app.model.ConditionType
import dev.context.app.model.Param
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ConditionEvaluatorTest {

    // Fixed "now" in UTC; day-of-week computed from system zone so tests are timezone-safe.
    private val fixedNow = LocalDateTime.of(2026, 9, 20, 14, 37).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val fixedClock = Clock { fixedNow }
    private val evaluator = ConditionEvaluator(fixedClock)
    private val todayBit = fixedClock.nowDate().weekdayMask()

    /** A weekday bit different from today's (wraps around safely). */
    private fun otherBit(): Int = ((todayBit shl 1) or 1) and 0b1111111

    /// Minute of day at the fixed clock in whatever the test machine's zone is.
    private fun nowMinute() = fixedClock.nowDateTime().minuteOfDay()

    private val snapshot = RuntimeSnapshot(
        batteryPercent = 30,
        charging = true,
        wifiConnected = true,
        wifiSsid = "HomeWifi",
        bluetoothEnabled = true,
        connectedBluetoothDevices = setOf("Headset"),
        dndActive = false,
        screenOn = true,
        screenUnlocked = true,
        activity = "stationary",
        locationsInside = setOf(7L),
    )

    @Test
    fun noConditions_alwaysMatches() {
        assertTrue(evaluator.matches(emptyList(), snapshot))
    }

    @Test
    fun timeRange_inWindowMatches() {
        val m = nowMinute()
        val c = ConditionSpec(ConditionType.TIME_RANGE, params = mapOf(Param.START_MINUTE to (m - 5).toString(), Param.END_MINUTE to (m + 5).toString()))
        assertTrue(evaluator.matches(listOf(c), snapshot))
    }

    @Test
    fun timeRange_outsideWindowFails() {
        val m = nowMinute()
        val c = ConditionSpec(ConditionType.TIME_RANGE, params = mapOf(Param.START_MINUTE to (m + 5).toString(), Param.END_MINUTE to (m + 10).toString()))
        assertFalse(evaluator.matches(listOf(c), snapshot))
    }

    @Test
    fun timeRange_overnightWrapAround() {
        val m = nowMinute()
        val c = ConditionSpec(ConditionType.TIME_RANGE, params = mapOf(Param.START_MINUTE to ((m + 1) % 1440).toString(), Param.END_MINUTE to m.toString()))
        assertTrue(evaluator.matches(listOf(c), snapshot))
    }

    @Test
    fun dayOfWeek_matchingBitPasses() {
        val c = ConditionSpec(ConditionType.DAY_OF_WEEK, params = mapOf(Param.DAYS to todayBit.toString()))
        assertTrue(evaluator.matches(listOf(c), snapshot))
    }

    @Test
    fun dayOfWeek_mismatchFails() {
        val c = ConditionSpec(ConditionType.DAY_OF_WEEK, params = mapOf(Param.DAYS to otherBit().toString()))
        assertFalse(evaluator.matches(listOf(c), snapshot))
    }

    @Test
    fun battery_belowEvaluates() {
        val below = ConditionSpec(ConditionType.BATTERY, params = mapOf(Param.BELOW_PERCENT to "40"))
        assertTrue(evaluator.matches(listOf(below), snapshot))
        val belowNotMet = ConditionSpec(ConditionType.BATTERY, params = mapOf(Param.BELOW_PERCENT to "20"))
        assertFalse(evaluator.matches(listOf(belowNotMet), snapshot))
    }

    @Test
    fun battery_aboveEvaluates() {
        val above = ConditionSpec(ConditionType.BATTERY, params = mapOf(Param.ABOVE_PERCENT to "25"))
        assertTrue(evaluator.matches(listOf(above), snapshot))
    }

    @Test
    fun negate_flipsResult() {
        val notToday = ConditionSpec(ConditionType.DAY_OF_WEEK, negate = true, params = mapOf(Param.DAYS to todayBit.toString()))
        assertFalse(evaluator.matches(listOf(notToday), snapshot))
        val notOther = ConditionSpec(ConditionType.DAY_OF_WEEK, negate = true, params = mapOf(Param.DAYS to otherBit().toString()))
        assertTrue(evaluator.matches(listOf(notOther), snapshot))
    }

    @Test
    fun matchAny_orLogic() {
        val same = ConditionSpec(ConditionType.DAY_OF_WEEK, params = mapOf(Param.DAYS to todayBit.toString()))
        val other = ConditionSpec(ConditionType.DAY_OF_WEEK, params = mapOf(Param.DAYS to otherBit().toString()))
        assertFalse(evaluator.matches(listOf(same, other), snapshot))
        assertTrue(evaluator.matches(listOf(same, other), snapshot, matchAny = true))
    }

    @Test
    fun matchAll_andLogic() {
        val today = ConditionSpec(ConditionType.DAY_OF_WEEK, params = mapOf(Param.DAYS to todayBit.toString()))
        val low = ConditionSpec(ConditionType.BATTERY, params = mapOf(Param.BELOW_PERCENT to "40"))
        assertTrue(evaluator.matches(listOf(today, low), snapshot))
        val high = ConditionSpec(ConditionType.BATTERY, params = mapOf(Param.ABOVE_PERCENT to "60"))
        assertFalse(evaluator.matches(listOf(today, high), snapshot))
    }

    @Test
    fun charging_matches() {
        val c = ConditionSpec(ConditionType.CHARGING, params = mapOf(Param.CHARGING to "true"))
        assertTrue(evaluator.matches(listOf(c), snapshot))
    }

    @Test
    fun wifiSsid_caseInsensitive() {
        val c = ConditionSpec(ConditionType.WIFI_SSID, params = mapOf(Param.SSID to "homewifi"))
        assertTrue(evaluator.matches(listOf(c), snapshot))
    }

    @Test
    fun bluetoothDevice_present() {
        val c = ConditionSpec(ConditionType.BLUETOOTH_DEVICE, params = mapOf(Param.DEVICE_NAME to "HEADset"))
        assertTrue(evaluator.matches(listOf(c), snapshot))
    }

    @Test
    fun locationState_inside() {
        val inside = ConditionSpec(ConditionType.LOCATION_STATE, params = mapOf(Param.LOCATION_ID to "7", Param.BOOL_VALUE to "true"))
        assertTrue(evaluator.matches(listOf(inside), snapshot))
        val outside = ConditionSpec(ConditionType.LOCATION_STATE, params = mapOf(Param.LOCATION_ID to "7", Param.BOOL_VALUE to "false"))
        assertFalse(evaluator.matches(listOf(outside), snapshot))
    }

    @Test
    fun screenState_unlocked() {
        val c = ConditionSpec(ConditionType.SCREEN_STATE, params = mapOf(Param.SCREEN_STATE to "unlocked"))
        assertTrue(evaluator.matches(listOf(c), snapshot))
    }
}