package dev.context.app.model

import kotlinx.serialization.Serializable

/**
 * Specification of "WHEN": one trigger per automation, parameters carried in a string map.
 * Keys are documented in [Param].
 */
@Serializable
data class TriggerSpec(
    val type: TriggerType,
    val params: Map<String, String> = emptyMap(),
)

/**
 * Specification of "IF": zero or more conditions combined with AND (or OR via [Automation.matchAny]).
 * Each condition may be negated ("not").
 */
@Serializable
data class ConditionSpec(
    val type: ConditionType,
    val negate: Boolean = false,
    val params: Map<String, String> = emptyMap(),
) {
    /** Keys whose values are meaningful for this condition; empty means "any". */
    fun summaryText(): String {
        val p = params
        return when (type) {
            ConditionType.TIME_RANGE -> {
                val a = p[Param.START_MINUTE]?.toIntOrNull() ?: 0
                val b = p[Param.END_MINUTE]?.toIntOrNull() ?: 0
                "between ${formatMinutes(a)} and ${formatMinutes(b)}"
            }
            ConditionType.DAY_OF_WEEK -> {
                val mask = p[Param.DAYS]?.toIntOrNull() ?: Shared.ALL_DAYS
                describeDays(mask)
            }
            ConditionType.BATTERY -> {
                val b = p[Param.BELOW_PERCENT]?.toIntOrNull()
                val ab = p[Param.ABOVE_PERCENT]?.toIntOrNull()
                when {
                    b != null -> "below $b%"
                    ab != null -> "at least $ab%"
                    else -> "any level"
                }
            }
            ConditionType.CHARGING ->
                if (p[Param.CHARGING] == "true") "charging" else "not charging"
            ConditionType.WIFI_STATE ->
                if (p[Param.BOOL_VALUE] == "true") "on Wi-Fi" else "not on Wi-Fi"
            ConditionType.WIFI_SSID -> p[Param.SSID] ?: "specific network"
            ConditionType.BLUETOOTH_STATE ->
                if (p[Param.BOOL_VALUE] == "true") "Bluetooth on" else "Bluetooth off"
            ConditionType.BLUETOOTH_DEVICE -> p[Param.DEVICE_NAME] ?: "device connected"
            ConditionType.DND_STATE ->
                if (p[Param.BOOL_VALUE] == "true") "DND active" else "DND off"
            ConditionType.SCREEN_STATE -> p[Param.SCREEN_STATE] ?: "screen state"
            ConditionType.ACTIVITY_STATE -> p[Param.ACTIVITY] ?: "activity"
            ConditionType.LOCATION_STATE ->
                if (p[Param.BOOL_VALUE] == "true") "in location" else "outside location"
        }
    }

    fun describe(): String = (if (negate) "not " else "") + type.label() + " " + summaryText()

    companion object {
        fun formatMinutes(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return "%02d:%02d".format(h, m)
        }

        fun describeDays(mask: Int): String {
            if (mask == Shared.ALL_DAYS) return "every day"
            if (mask == Shared.WEEKDAYS) return "weekdays"
            if (mask == Shared.WEEKEND) return "weekends"
            val arr = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                .filterIndexed { i, _ -> mask and (1 shl i) != 0 }
            return if (arr.isEmpty()) "no days" else arr.joinToString(", ")
        }
    }
}

/**
 * Specification of "THEN": ordered action list. Parameters are documented in [Param].
 */
@Serializable
data class ActionSpec(
    val type: ActionType,
    val params: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
) {
    fun summaryText(): String {
        val p = params
        return when (type) {
            ActionType.SHOW_NOTIFICATION -> (p[Param.TITLE] ?: p[Param.TEXT] ?: "") +
                (if (p[Param.PERSISTENT] == "true") " (persistent)" else "")
            ActionType.SET_DND -> if (p[Param.BOOL_VALUE] == "true") "enable DND" else "disable DND"
            ActionType.SET_VOLUME ->
                (p[Param.STREAM] ?: "media") + " volume " + (p[Param.VOLUME_PERCENT] ?: "?") + "%"
            ActionType.SET_RINGER_MODE -> when (p[Param.RINGER_MODE]) {
                "silent" -> "set silent"
                "vibrate" -> "set vibrate"
                else -> "set ring"
            }
            ActionType.VIBRATE -> "vibrate " + (p[Param.DURATION_MS] ?: "500") + " ms"
            ActionType.SET_BRIGHTNESS -> "dim to " + (p[Param.BRIGHTNESS_PERCENT] ?: "?") + "%"
            ActionType.SET_NIGHT_LIGHT -> "night light"
            ActionType.SET_WIFI -> "Wi-Fi " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.SET_MOBILE_DATA -> "mobile data " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.SET_BLUETOOTH -> "Bluetooth " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.OPEN_APP -> p[Param.PACKAGE] ?: "app"
            ActionType.OPEN_URL -> p[Param.URL] ?: "url"
            ActionType.OPEN_SETTINGS -> (p[Param.SETTINGS_TARGET] ?: "settings").replace('_', ' ').lowercase()
            ActionType.SET_VARIABLE -> (p[Param.VARIABLE] ?: "") + " = " + (p[Param.VALUE] ?: "")
            ActionType.INCREMENT_VARIABLE -> (p[Param.VARIABLE] ?: "var") + "++"
            ActionType.CLEAR_VARIABLE -> p[Param.VARIABLE] ?: "var"
            ActionType.LOG_MESSAGE -> p[Param.MESSAGE] ?: ""
            ActionType.SET_AIRPLANE_MODE -> "airplane " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.SET_LOCATION_MODE -> when (p[Param.STATE]) {
                "off" -> "location off"
                else -> "location high accuracy"
            }
            ActionType.SET_BATTERY_SAVER -> "battery saver " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.SET_ALWAYS_ON_DISPLAY -> "always-on display " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.SET_NIGHT_LIGHT_SECURE -> "night light " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.SET_PRIVATE_DNS -> "private DNS " + (p[Param.DNS_MODE] ?: "auto")
            ActionType.SET_IMMERSIVE_MODE -> "immersive " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.SET_ANIMATION_SCALES -> "animations " + (p[Param.VALUE] ?: "1x")
            ActionType.SET_STAY_ON_WHILE_CHARGING -> "stay awake while charging " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.SET_SCREEN_TIMEOUT -> "screen timeout " + (p[Param.TIMEOUT_MS] ?: "?") + " ms"
            ActionType.SET_AUTO_ROTATE -> "auto-rotate " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.PLAY_TTS -> p[Param.TEXT] ?: p[Param.MESSAGE] ?: "speak"
            ActionType.FLASHLIGHT -> "flashlight " + if (p[Param.BOOL_VALUE] == "true") "on" else "off"
            ActionType.LOCK_SCREEN -> "lock screen"
            ActionType.OPEN_NOTIFICATIONS -> "open notification shade"
            ActionType.OPEN_QUICK_SETTINGS -> "open quick settings"
            ActionType.TAKE_SCREENSHOT -> "take screenshot"
            ActionType.CLICK_AT_COORDS -> "tap (" + (p[Param.X] ?: "?") + ", " + (p[Param.Y] ?: "?") + ")"
            ActionType.CLICK_BY_TEXT -> "tap \"" + (p[Param.TEXT] ?: "?") + "\""
            ActionType.SWIPE_GESTURE -> "swipe " + (p[Param.DIRECTION] ?: "")
            ActionType.DISMISS_NOTIFICATION -> "dismiss " + (p[Param.PACKAGE] ?: p[Param.KEY] ?: "notification")
        }
    }

    fun describe(): String = type.label() + ": " + summaryText()
}

/**
 * A user-defined automation: WHEN / IF / THEN plus execution policy.
 */
@Serializable
data class Automation(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val trigger: TriggerSpec,
    val conditions: List<ConditionSpec> = emptyList(),
    val matchAny: Boolean = false,
    val actions: List<ActionSpec> = emptyList(),
    val mode: ExecutionMode = ExecutionMode.ONCE_PER_TRANSITION,
    val cooldownMinutes: Int = 0,
    val createdAt: Long = 0L,
    val lastFiredAt: Long = 0L,
    val lastSignature: String = "",
    val currentState: Boolean = false,
    val useGpsForLocation: Boolean = false,
) {
    fun triggerSummary(label: String? = null): String {
        val p = trigger.params
        return when (trigger.type) {
            TriggerType.LOCATION_ENTER, TriggerType.LOCATION_EXIT ->
                p[Param.LOCATION_ID]?.let { "($label)" } ?: ""
            TriggerType.LOCATION_DWELL ->
                "for ${p[Param.DWELL_MINUTES]} min"
            TriggerType.TIME_AT -> {
                val h = p[Param.HOUR] ?: "00"
                val m = p[Param.MINUTE] ?: "00"
                val days = p[Param.DAYS]?.toIntOrNull() ?: Shared.ALL_DAYS
                val d = if (days == Shared.ALL_DAYS) "every day" else ConditionSpec.describeDays(days)
                "$h:$m, $d"
            }
            TriggerType.BATTERY_BELOW -> "below ${p[Param.THRESHOLD]}%"
            TriggerType.BATTERY_ABOVE -> "reaches ${p[Param.THRESHOLD]}%"
            TriggerType.BLUETOOTH_CONNECTED, TriggerType.BLUETOOTH_DISCONNECTED ->
                p[Param.DEVICE_NAME] ?: ""
            TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED -> p[Param.SSID] ?: ""
            TriggerType.ACTIVITY_START -> p[Param.ACTIVITY] ?: ""
            TriggerType.CELL_TOWER_ENTER, TriggerType.CELL_TOWER_EXIT ->
                p[Param.LOCATION_ID]?.let { "($label)" } ?: ""
            TriggerType.CELL_TOWER_DWELL -> "for ${p[Param.DWELL_MINUTES]} min"
            TriggerType.SIGNIFICANT_MOTION -> "motion detected"
            TriggerType.DEVICE_PICKUP -> "picked up"
            TriggerType.FOREGROUND_APP_CHANGED -> p[Param.PACKAGE] ?: ""
            TriggerType.VOLUME_KEY_PRESSED -> p[Param.KEY] ?: ""
            TriggerType.NOTIFICATION_RECEIVED -> p[Param.PACKAGE] ?: p[Param.TITLE_CONTAINS] ?: ""
            TriggerType.SMS_RECEIVED -> p[Param.SENDER] ?: ""
            TriggerType.CALL_STATE_CHANGED -> p[Param.STATE] ?: ""
            TriggerType.CALENDAR_EVENT_START, TriggerType.CALENDAR_EVENT_END ->
                p[Param.TITLE_CONTAINS] ?: ""
            TriggerType.USB_CONNECTED -> "USB attached"
            TriggerType.USB_DISCONNECTED -> "USB detached"
            TriggerType.LOGCAT_MATCH -> p[Param.PATTERN] ?: ""
            else -> ""
        }
    }
}

@Serializable
data class LocationMarker(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 500,
    val createdAt: Long = 0L,
    val cellIds: List<String> = emptyList(),
)

@Serializable
data class Variable(
    val key: String,
    val type: VariableType = VariableType.STRING,
    val value: String = "",
    val updatedAt: Long = 0L,
) {
    fun asNumber(): Long = value.toLongOrNull() ?: 0L
}

@Serializable
data class ConditionEvaluation(
    val condition: ConditionSpec,
    val passed: Boolean,
    val detail: String = "",
) {
    val effectivePassed: Boolean get() = if (condition.negate) !passed else passed
}

@Serializable
data class ActionExecution(
    val action: ActionSpec,
    val ok: Boolean,
    val message: String = "",
) {
    companion object {
        const val OK = true
        const val FAILED = false
    }
}

/** One performed run of one automation. */
data class ExecutionLog(
    val id: Long = 0,
    val automationId: Long = 0,
    val automationName: String,
    val timestamp: Long = 0L,
    val result: LogResult,
    val triggerSummary: String = "",
    val evaluations: List<ConditionEvaluation> = emptyList(),
    val executions: List<ActionExecution> = emptyList(),
    val detail: String = "",
) {
    fun conditionsMarkdown(): String = evaluations.joinToString("\n") { ev ->
        (if (ev.effectivePassed) "\u2713" else "\u2717") + " " + ev.condition.describe() + " " + ev.detail
    }

    fun actionsMarkdown(): String = executions.joinToString("\n") { e ->
        (if (e.ok) "\u2713" else "\u2717") + " " + e.action.describe() + (if (e.message.isNotEmpty()) " \u00b7 " + e.message else "")
    }
}