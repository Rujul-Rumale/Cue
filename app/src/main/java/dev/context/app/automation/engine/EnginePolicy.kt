package dev.context.app.automation.engine

import dev.context.app.model.Param
import dev.context.app.model.TriggerType

/**
 * Static policy about how each trigger type behaves, kept out of the model classes
 * so the core model stays a plain data description.
 */
object EnginePolicy {

    /**
     * Transitional triggers represent a situation that starts and later ends
     * (enter/exit, below/above, plug/unplug). They fire once per false->true transition.
     * Recurring triggers (time-of-day, boot) fire on each genuine occurrence.
     */
    fun isTransitional(type: TriggerType): Boolean = when (type) {
        TriggerType.LOCATION_ENTER, TriggerType.LOCATION_EXIT, TriggerType.LOCATION_DWELL,
        TriggerType.CELL_TOWER_ENTER, TriggerType.CELL_TOWER_EXIT, TriggerType.CELL_TOWER_DWELL,
        TriggerType.BATTERY_BELOW, TriggerType.BATTERY_ABOVE, TriggerType.BATTERY_FULL,
        TriggerType.CHARGING_START, TriggerType.CHARGING_STOP,
        TriggerType.BLUETOOTH_CONNECTED, TriggerType.BLUETOOTH_DISCONNECTED,
        TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED,
        TriggerType.SCREEN_ON, TriggerType.SCREEN_OFF, TriggerType.SCREEN_UNLOCKED,
        TriggerType.DND_ENABLED, TriggerType.DND_DISABLED,
        TriggerType.HEADSET_PLUGGED, TriggerType.HEADSET_UNPLUGGED,
        TriggerType.ACTIVITY_START, TriggerType.ACTIVITY_END,
        TriggerType.USB_CONNECTED, TriggerType.USB_DISCONNECTED -> true

        // Recurring occurrences, deduped by signature: battery levels, motion
        // moments, app switches, key presses, notifications, messages, calls,
        // calendar instances, log lines, times and boots.
        TriggerType.BATTERY_CHANGED, TriggerType.TIME_AT, TriggerType.BOOT_COMPLETED,
        TriggerType.SIGNIFICANT_MOTION, TriggerType.DEVICE_PICKUP,
        TriggerType.FOREGROUND_APP_CHANGED, TriggerType.VOLUME_KEY_PRESSED,
        TriggerType.NOTIFICATION_RECEIVED, TriggerType.SMS_RECEIVED, TriggerType.CALL_STATE_CHANGED,
        TriggerType.CALENDAR_EVENT_START, TriggerType.CALENDAR_EVENT_END,
        TriggerType.LOGCAT_MATCH -> false
    }

    /** The companion event type that ends the situation started by [type]. */
    fun resetEvent(type: TriggerType): TriggerType? = when (type) {
        TriggerType.LOCATION_ENTER, TriggerType.LOCATION_DWELL -> TriggerType.LOCATION_EXIT
        TriggerType.LOCATION_EXIT -> TriggerType.LOCATION_ENTER
        TriggerType.CELL_TOWER_ENTER, TriggerType.CELL_TOWER_DWELL -> TriggerType.CELL_TOWER_EXIT
        TriggerType.CELL_TOWER_EXIT -> TriggerType.CELL_TOWER_ENTER
        TriggerType.USB_CONNECTED -> TriggerType.USB_DISCONNECTED
        TriggerType.USB_DISCONNECTED -> TriggerType.USB_CONNECTED
        TriggerType.CHARGING_START, TriggerType.BATTERY_FULL -> TriggerType.CHARGING_STOP
        TriggerType.CHARGING_STOP -> TriggerType.CHARGING_START
        TriggerType.BLUETOOTH_CONNECTED -> TriggerType.BLUETOOTH_DISCONNECTED
        TriggerType.BLUETOOTH_DISCONNECTED -> TriggerType.BLUETOOTH_CONNECTED
        TriggerType.WIFI_CONNECTED -> TriggerType.WIFI_DISCONNECTED
        TriggerType.WIFI_DISCONNECTED -> TriggerType.WIFI_CONNECTED
        TriggerType.SCREEN_ON -> TriggerType.SCREEN_OFF
        TriggerType.SCREEN_OFF, TriggerType.SCREEN_UNLOCKED -> TriggerType.SCREEN_ON
        TriggerType.DND_ENABLED -> TriggerType.DND_DISABLED
        TriggerType.DND_DISABLED -> TriggerType.DND_ENABLED
        TriggerType.HEADSET_PLUGGED -> TriggerType.HEADSET_UNPLUGGED
        TriggerType.HEADSET_UNPLUGGED -> TriggerType.HEADSET_PLUGGED
        TriggerType.ACTIVITY_START -> TriggerType.ACTIVITY_END
        TriggerType.ACTIVITY_END -> TriggerType.ACTIVITY_START
        else -> null
    }

    /** True when [event] reverses a currently-true situation of automation trigger [type]. */
    fun isResetEvent(type: TriggerType, event: TriggerType): Boolean =
        resetEvent(type) == event

    /**
     * Place-transition family: 0 = enter, 1 = exit, 2 = dwell, -1 = not one.
     * GPS and cell-tower paths are interchangeable senses of the same place
     * transition, so the engine matches and resets across families.
     */
    fun locationFamily(type: TriggerType): Int = when (type) {
        TriggerType.LOCATION_ENTER, TriggerType.CELL_TOWER_ENTER -> 0
        TriggerType.LOCATION_EXIT, TriggerType.CELL_TOWER_EXIT -> 1
        TriggerType.LOCATION_DWELL, TriggerType.CELL_TOWER_DWELL -> 2
        else -> -1
    }

    /** Should this event type be treated as a "trigger-able" event for the automation? */
    fun triggerNeedsParamsMatch(type: TriggerType): Boolean = when (type) {
        TriggerType.LOCATION_ENTER, TriggerType.LOCATION_EXIT, TriggerType.LOCATION_DWELL,
        TriggerType.CELL_TOWER_ENTER, TriggerType.CELL_TOWER_EXIT, TriggerType.CELL_TOWER_DWELL,
        TriggerType.BLUETOOTH_CONNECTED, TriggerType.BLUETOOTH_DISCONNECTED,
        TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED,
        TriggerType.ACTIVITY_START, TriggerType.ACTIVITY_END,
        TriggerType.FOREGROUND_APP_CHANGED, TriggerType.VOLUME_KEY_PRESSED,
        TriggerType.NOTIFICATION_RECEIVED, TriggerType.SMS_RECEIVED, TriggerType.CALL_STATE_CHANGED,
        TriggerType.CALENDAR_EVENT_START, TriggerType.CALENDAR_EVENT_END,
        TriggerType.LOGCAT_MATCH -> true
        else -> false
    }

    /** Whether the event's concrete object matches what the automation is watching. */
    fun paramsMatchAutomation(type: TriggerType, specParams: Map<String, String>, eventParams: Map<String, String>): Boolean {
        return when (type) {
            TriggerType.LOCATION_ENTER, TriggerType.LOCATION_EXIT, TriggerType.LOCATION_DWELL,
            TriggerType.CELL_TOWER_ENTER, TriggerType.CELL_TOWER_EXIT, TriggerType.CELL_TOWER_DWELL -> {
                val wanted = specParams[Param.LOCATION_ID]
                wanted == null || wanted == eventParams[Param.LOCATION_ID]
            }
            TriggerType.FOREGROUND_APP_CHANGED -> {
                val wanted = specParams[Param.PACKAGE]
                wanted.isNullOrBlank() || eventParams[Param.PACKAGE]?.equals(wanted, ignoreCase = true) == true
            }
            TriggerType.VOLUME_KEY_PRESSED -> {
                val wanted = specParams[Param.KEY]
                wanted.isNullOrBlank() || eventParams[Param.KEY]?.equals(wanted, ignoreCase = true) == true
            }
            TriggerType.NOTIFICATION_RECEIVED -> {
                val pkg = specParams[Param.PACKAGE]
                val keyword = specParams[Param.TITLE_CONTAINS]
                val cat = specParams[Param.CATEGORY]
                (pkg.isNullOrBlank() || eventParams[Param.PACKAGE]?.equals(pkg, ignoreCase = true) == true) &&
                    (keyword.isNullOrBlank() ||
                        eventParams[Param.NOTIF_TITLE]?.contains(keyword, ignoreCase = true) == true ||
                        eventParams[Param.NOTIF_TEXT]?.contains(keyword, ignoreCase = true) == true) &&
                    (cat.isNullOrBlank() || eventParams[Param.CATEGORY]?.equals(cat, ignoreCase = true) == true)
            }
            TriggerType.SMS_RECEIVED -> {
                val sender = specParams[Param.SENDER]
                val body = specParams[Param.BODY]
                (sender.isNullOrBlank() || eventParams[Param.SENDER]?.contains(sender, ignoreCase = true) == true) &&
                    (body.isNullOrBlank() || eventParams[Param.BODY]?.contains(body, ignoreCase = true) == true)
            }
            TriggerType.CALL_STATE_CHANGED -> {
                val wanted = specParams[Param.STATE]
                val number = specParams[Param.NUMBER]
                (wanted.isNullOrBlank() || eventParams[Param.STATE]?.equals(wanted, ignoreCase = true) == true) &&
                    (number.isNullOrBlank() || eventParams[Param.NUMBER]?.contains(number, ignoreCase = true) == true)
            }
            TriggerType.CALENDAR_EVENT_START, TriggerType.CALENDAR_EVENT_END -> {
                val title = specParams[Param.TITLE_CONTAINS]
                title.isNullOrBlank() || eventParams[Param.NOTIF_TITLE]?.contains(title, ignoreCase = true) == true
            }
            TriggerType.LOGCAT_MATCH -> {
                val pattern = specParams[Param.PATTERN]
                pattern.isNullOrBlank() || runCatching {
                    Regex(pattern).containsMatchIn(eventParams[Param.LINE].orEmpty())
                }.getOrDefault(false)
            }
            TriggerType.BLUETOOTH_CONNECTED, TriggerType.BLUETOOTH_DISCONNECTED -> {
                val wanted = specParams[Param.DEVICE_NAME]
                wanted == null || eventParams[Param.DEVICE_NAME]?.equals(wanted, ignoreCase = true) == true
            }
            TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED -> {
                val wanted = specParams[Param.SSID]
                wanted == null || eventParams[Param.SSID]?.equals(wanted, ignoreCase = true) == true
            }
            TriggerType.ACTIVITY_START, TriggerType.ACTIVITY_END -> {
                val wanted = specParams[Param.ACTIVITY]
                wanted == null || eventParams[Param.ACTIVITY]?.equals(wanted, ignoreCase = true) == true
            }
            else -> true
        }
    }
}