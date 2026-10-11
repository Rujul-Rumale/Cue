package dev.context.app.automation.engine

import dev.context.app.core.Clock
import dev.context.app.core.minuteOfDay
import dev.context.app.core.nowDateTime
import dev.context.app.core.weekdayMask
import dev.context.app.model.ConditionEvaluation
import dev.context.app.model.ConditionSpec
import dev.context.app.model.ConditionType
import dev.context.app.model.Param
import dev.context.app.model.Shared

/**
 * Evaluates condition specifications against a [RuntimeSnapshot] taken at event time.
 * Pure logic - no Android dependencies - so it is fully unit-testable.
 */
class ConditionEvaluator(
    private val clock: Clock = Clock.SYSTEM,
) {

    fun evaluate(
        conditions: List<ConditionSpec>,
        snapshot: RuntimeSnapshot,
        matchAny: Boolean = false,
    ): List<ConditionEvaluation> {
        val results = conditions.map { evaluateOne(it, snapshot) }
        return results
    }

    /** Returns true if the whole condition set matches for this snapshot. */
    fun matches(
        conditions: List<ConditionSpec>,
        snapshot: RuntimeSnapshot,
        matchAny: Boolean = false,
    ): Boolean {
        if (conditions.isEmpty()) return true
        val effective = conditions.map { evaluateOne(it, snapshot).effectivePassed }
        return if (matchAny) effective.any { it } else effective.all { it }
    }

    fun evaluateOne(condition: ConditionSpec, snapshot: RuntimeSnapshot): ConditionEvaluation {
        val now = clock.nowDateTime()
        val passed: Boolean
        val detail: String
        when (condition.type) {
            ConditionType.TIME_RANGE -> {
                val start = condition.params[Param.START_MINUTE]?.toIntOrNull() ?: 0
                val end = condition.params[Param.END_MINUTE]?.toIntOrNull() ?: 1439
                val cur = now.minuteOfDay()
                passed = inMinutesRange(cur, start, end)
                detail = "now=${ConditionSpec.formatMinutes(cur)}"
            }
            ConditionType.DAY_OF_WEEK -> {
                val mask = condition.params[Param.DAYS]?.toIntOrNull() ?: Shared.ALL_DAYS
                val today = now.toLocalDate().weekdayMask()
                passed = (today and mask) != 0
                detail = "today=${now.dayOfWeek.name.replaceFirstChar { it.uppercase() }}"
            }
            ConditionType.BATTERY -> {
                val below = condition.params[Param.BELOW_PERCENT]?.toIntOrNull()
                val above = condition.params[Param.ABOVE_PERCENT]?.toIntOrNull()
                val level = snapshot.batteryPercent ?: -1
                passed = when {
                    below != null -> level <= below
                    above != null -> level >= above
                    else -> false
                }
                detail = "battery=$level%"
            }
            ConditionType.CHARGING -> {
                val expected = condition.params[Param.CHARGING] == "true"
                passed = snapshot.charging == expected
                detail = "charging=${snapshot.charging}"
            }
            ConditionType.WIFI_STATE -> {
                val expected = condition.params[Param.BOOL_VALUE] == "true"
                passed = snapshot.wifiConnected == expected
                detail = "wifi=${snapshot.wifiConnected}"
            }
            ConditionType.WIFI_SSID -> {
                val ssid = condition.params[Param.SSID]
                passed = !ssid.isNullOrBlank() && snapshot.wifiSsid?.equals(ssid, ignoreCase = true) == true
                detail = "ssid=${snapshot.wifiSsid}"
            }
            ConditionType.BLUETOOTH_STATE -> {
                val expected = condition.params[Param.BOOL_VALUE] == "true"
                passed = snapshot.bluetoothEnabled == expected
                detail = "bt=${snapshot.bluetoothEnabled}"
            }
            ConditionType.BLUETOOTH_DEVICE -> {
                val name = condition.params[Param.DEVICE_NAME]
                passed = !name.isNullOrBlank() && snapshot.connectedBluetoothDevices.any { it.equals(name, ignoreCase = true) }
                detail = snapshot.connectedBluetoothDevices.joinToString(", ")
            }
            ConditionType.DND_STATE -> {
                val expected = condition.params[Param.BOOL_VALUE] == "true"
                passed = snapshot.dndActive == expected
                detail = "dnd=${snapshot.dndActive}"
            }
            ConditionType.SCREEN_STATE -> {
                val state = when (condition.params[Param.SCREEN_STATE]) {
                    "on" -> snapshot.screenOn == true
                    "off" -> snapshot.screenOn == false
                    "unlocked" -> snapshot.screenUnlocked == true
                    else -> false
                }
                passed = state
                detail = "screen=${snapshot.screenOn},unlocked=${snapshot.screenUnlocked}"
            }
            ConditionType.ACTIVITY_STATE -> {
                val expected = condition.params[Param.ACTIVITY]
                passed = snapshot.activity.equals(expected, ignoreCase = true)
                detail = "activity=${snapshot.activity}"
            }
            ConditionType.LOCATION_STATE -> {
                val id = condition.params[Param.LOCATION_ID]?.toLongOrNull() ?: -1L
                val expectInside = condition.params[Param.BOOL_VALUE] != "false"
                val inside = id in snapshot.locationsInside
                passed = inside == expectInside
                detail = "inside=$inside"
            }
        }
        return ConditionEvaluation(condition, passed, detail)
    }

    private fun inMinutesRange(cur: Int, start: Int, end: Int): Boolean {
        return if (start <= end) cur in start..end else cur >= start || cur <= end
    }
}