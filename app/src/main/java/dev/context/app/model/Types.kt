package dev.context.app.model

/** Canonical set of parameter keys used by trigger/condition/action specs. */
object Param {
    const val LOCATION_ID = "locationId"
    const val DWELL_MINUTES = "dwellMinutes"
    const val HOUR = "hour"
    const val MINUTE = "minute"
    const val DAYS = "days"                 // day-of-week bitmask, bit1=Mon..bit7=Sun
    const val THRESHOLD = "thresholdPercent"
    const val DEVICE_NAME = "deviceName"
    const val DEVICE_MAC = "deviceMac"
    const val SSID = "ssid"
    const val START_MINUTE = "startMinute"
    const val END_MINUTE = "endMinute"
    const val BELOW_PERCENT = "belowPercent"
    const val ABOVE_PERCENT = "abovePercent"
    const val CHARGING = "charging"
    const val BOOL_VALUE = "boolValue"
    const val ACTIVITY = "activity"
    const val DURATION_SECONDS = "durationSeconds"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val RADIUS = "radiusMeters"
    const val TITLE = "title"
    const val TEXT = "text"
    const val PERSISTENT = "persistent"
    const val STREAM = "stream"             // media|ring|notification|alarm
    const val VOLUME_PERCENT = "volumePercent"
    const val PACKAGE = "packageName"
    const val URL = "url"
    const val SETTINGS_TARGET = "settingsTarget"
    const val VARIABLE = "variable"
    const val VALUE = "value"
    const val MESSAGE = "message"
    const val NAME = "name"
    const val DATE = "date"
    const val SCREEN_STATE = "screenState"      // on|off|unlocked
    const val EVENT_SOURCE = "eventSource"  // system | debug | fake
    const val RINGER_MODE = "ringerMode"    // silent|vibrate|normal
    const val DURATION_MS = "durationMs"
    const val BRIGHTNESS_PERCENT = "brightnessPercent"
    const val AT = "atMillis"               // unique occurrence stamp for momentary events
    const val SENDER = "sender"             // sms sender / notification package filter
    const val BODY = "body"                 // sms body / notification text filter
    const val STATE = "state"               // call state / generic state value
    const val NUMBER = "number"             // phone number (best effort)
    const val EVENT_ID = "eventId"          // calendar event id
    const val TITLE_CONTAINS = "titleContains"
    const val PATTERN = "pattern"           // regex for logcat matching
    const val LINE = "line"                 // matched log line (truncated)
    const val KEY = "key"                   // notification key / generic key
    const val NOTIF_KEY = "notificationKey"
    const val NOTIF_TITLE = "notificationTitle"
    const val NOTIF_TEXT = "notificationText"
    const val CATEGORY = "category"         // notification category
    const val X = "x"                       // tap/swipe coordinate
    const val Y = "y"
    const val DIRECTION = "direction"       // up|down|left|right
    const val DNS_MODE = "dnsMode"          // off|auto|hostname
    const val DNS_HOST = "dnsHost"
    const val TIMEOUT_MS = "timeoutMs"       // screen timeout
}

/** UI-facing categories used to walk the user through automation creation. */
enum class TriggerCategory(
    val label: String,
    val icon: String,
) {
    LOCATION("Location", "place"),
    TIME("Time", "schedule"),
    BATTERY("Battery", "battery"),
    CONNECTIVITY("Connectivity", "wifi"),
    DEVICE("Device", "smartphone"),
    AUDIO("Audio", "headset"),
    ACTIVITY("Motion", "directions_run"),
    COMMUNICATION("Messages", "chat"),
}

enum class TriggerType {
    LOCATION_ENTER,
    LOCATION_EXIT,
    LOCATION_DWELL,
    TIME_AT,
    BATTERY_CHANGED,
    BATTERY_BELOW,
    BATTERY_ABOVE,
    CHARGING_START,
    CHARGING_STOP,
    BATTERY_FULL,
    BLUETOOTH_CONNECTED,
    BLUETOOTH_DISCONNECTED,
    WIFI_CONNECTED,
    WIFI_DISCONNECTED,
    SCREEN_ON,
    SCREEN_OFF,
    SCREEN_UNLOCKED,
    DND_ENABLED,
    DND_DISABLED,
    HEADSET_PLUGGED,
    HEADSET_UNPLUGGED,
    ACTIVITY_START,
    ACTIVITY_END,
    BOOT_COMPLETED,
    CELL_TOWER_ENTER,
    CELL_TOWER_EXIT,
    CELL_TOWER_DWELL,
    SIGNIFICANT_MOTION,
    DEVICE_PICKUP,
    FOREGROUND_APP_CHANGED,
    VOLUME_KEY_PRESSED,
    NOTIFICATION_RECEIVED,
    SMS_RECEIVED,
    CALL_STATE_CHANGED,
    CALENDAR_EVENT_START,
    CALENDAR_EVENT_END,
    USB_CONNECTED,
    USB_DISCONNECTED,
    LOGCAT_MATCH;

    val category: TriggerCategory
        get() = when (this) {
            LOCATION_ENTER, LOCATION_EXIT, LOCATION_DWELL -> TriggerCategory.LOCATION
            TIME_AT -> TriggerCategory.TIME
            BATTERY_CHANGED, BATTERY_BELOW, BATTERY_ABOVE, CHARGING_START, CHARGING_STOP, BATTERY_FULL ->
                TriggerCategory.BATTERY
            BLUETOOTH_CONNECTED, BLUETOOTH_DISCONNECTED, WIFI_CONNECTED, WIFI_DISCONNECTED ->
                TriggerCategory.CONNECTIVITY
            SCREEN_ON, SCREEN_OFF, SCREEN_UNLOCKED, DND_ENABLED, DND_DISABLED, BOOT_COMPLETED,
            FOREGROUND_APP_CHANGED, VOLUME_KEY_PRESSED, USB_CONNECTED, USB_DISCONNECTED, LOGCAT_MATCH ->
                TriggerCategory.DEVICE
            HEADSET_PLUGGED, HEADSET_UNPLUGGED -> TriggerCategory.AUDIO
            ACTIVITY_START, ACTIVITY_END, SIGNIFICANT_MOTION, DEVICE_PICKUP -> TriggerCategory.ACTIVITY
            CELL_TOWER_ENTER, CELL_TOWER_EXIT, CELL_TOWER_DWELL -> TriggerCategory.LOCATION
            NOTIFICATION_RECEIVED, SMS_RECEIVED, CALL_STATE_CHANGED -> TriggerCategory.COMMUNICATION
            CALENDAR_EVENT_START, CALENDAR_EVENT_END -> TriggerCategory.TIME
        }

    /** Human-readable short form, e.g. "Enter location", "Battery drops below". */
    fun shortLabel(): String = when (this) {
        LOCATION_ENTER -> "Enter location"
        LOCATION_EXIT -> "Leave location"
        LOCATION_DWELL -> "Stay at location"
        TIME_AT -> "At a specific time"
        BATTERY_BELOW -> "Battery below"
        BATTERY_ABOVE -> "Battery reaches"
        BATTERY_CHANGED -> "Battery changed"
        CHARGING_START -> "Charging starts"
        CHARGING_STOP -> "Charging stops"
        BATTERY_FULL -> "Battery full"
        BLUETOOTH_CONNECTED -> "Bluetooth device connected"
        BLUETOOTH_DISCONNECTED -> "Bluetooth device disconnected"
        WIFI_CONNECTED -> "Wi-Fi connected"
        WIFI_DISCONNECTED -> "Wi-Fi disconnected"
        SCREEN_ON -> "Screen on"
        SCREEN_OFF -> "Screen off"
        SCREEN_UNLOCKED -> "Screen unlocked"
        DND_ENABLED -> "Do Not Disturb enabled"
        DND_DISABLED -> "Do Not Disturb disabled"
        HEADSET_PLUGGED -> "Headphones plugged in"
        HEADSET_UNPLUGGED -> "Headphones unplugged"
        ACTIVITY_START -> "Started moving by"
        ACTIVITY_END -> "Stopped"
        BOOT_COMPLETED -> "Device booted"
        CELL_TOWER_ENTER -> "Enter cell area"
        CELL_TOWER_EXIT -> "Leave cell area"
        CELL_TOWER_DWELL -> "Stay in cell area"
        SIGNIFICANT_MOTION -> "Significant motion"
        DEVICE_PICKUP -> "Device picked up"
        FOREGROUND_APP_CHANGED -> "App opened"
        VOLUME_KEY_PRESSED -> "Volume key pressed"
        NOTIFICATION_RECEIVED -> "Notification received"
        SMS_RECEIVED -> "SMS received"
        CALL_STATE_CHANGED -> "Call state changed"
        CALENDAR_EVENT_START -> "Calendar event starts"
        CALENDAR_EVENT_END -> "Calendar event ends"
        USB_CONNECTED -> "USB connected"
        USB_DISCONNECTED -> "USB disconnected"
        LOGCAT_MATCH -> "Log pattern matched"
    }

    fun isSupportedOn(api: Int): Boolean {
        if (this == BOOT_COMPLETED) return true
        return true
    }
}

enum class ConditionType {
    TIME_RANGE,
    DAY_OF_WEEK,
    BATTERY,
    CHARGING,
    WIFI_STATE,
    WIFI_SSID,
    BLUETOOTH_STATE,
    BLUETOOTH_DEVICE,
    DND_STATE,
    SCREEN_STATE,
    ACTIVITY_STATE,
    LOCATION_STATE;

    fun label(): String = when (this) {
        TIME_RANGE -> "Time range"
        DAY_OF_WEEK -> "Days of week"
        BATTERY -> "Battery level"
        CHARGING -> "Charging"
        WIFI_STATE -> "Wi-Fi"
        WIFI_SSID -> "Wi-Fi network"
        BLUETOOTH_STATE -> "Bluetooth"
        BLUETOOTH_DEVICE -> "Bluetooth device"
        DND_STATE -> "Do Not Disturb"
        SCREEN_STATE -> "Screen"
        ACTIVITY_STATE -> "Activity"
        LOCATION_STATE -> "Location"
    }
}

enum class ActionType {
    SHOW_NOTIFICATION,
    SET_DND,
    SET_VOLUME,
    SET_RINGER_MODE,
    VIBRATE,
    SET_BRIGHTNESS,
    SET_NIGHT_LIGHT,
    SET_WIFI,
    SET_MOBILE_DATA,
    SET_BLUETOOTH,
    OPEN_APP,
    OPEN_URL,
    OPEN_SETTINGS,
    SET_VARIABLE,
    CLEAR_VARIABLE,
    INCREMENT_VARIABLE,
    LOG_MESSAGE,
    SET_AIRPLANE_MODE,
    SET_LOCATION_MODE,
    SET_BATTERY_SAVER,
    SET_ALWAYS_ON_DISPLAY,
    SET_NIGHT_LIGHT_SECURE,
    SET_PRIVATE_DNS,
    SET_IMMERSIVE_MODE,
    SET_ANIMATION_SCALES,
    SET_STAY_ON_WHILE_CHARGING,
    SET_SCREEN_TIMEOUT,
    SET_AUTO_ROTATE,
    PLAY_TTS,
    FLASHLIGHT,
    LOCK_SCREEN,
    OPEN_NOTIFICATIONS,
    OPEN_QUICK_SETTINGS,
    TAKE_SCREENSHOT,
    CLICK_AT_COORDS,
    CLICK_BY_TEXT,
    SWIPE_GESTURE,
    DISMISS_NOTIFICATION;

    fun label(): String = when (this) {
        SHOW_NOTIFICATION -> "Show notification"
        SET_DND -> "Set Do Not Disturb"
        SET_VOLUME -> "Set volume"
        SET_RINGER_MODE -> "Set ringer mode"
        VIBRATE -> "Vibrate"
        SET_BRIGHTNESS -> "Dim screen"
        SET_NIGHT_LIGHT -> "Night light"
        SET_WIFI -> "Set Wi-Fi"
        SET_MOBILE_DATA -> "Set mobile data"
        SET_BLUETOOTH -> "Set Bluetooth"
        OPEN_APP -> "Open app"
        OPEN_URL -> "Open link"
        OPEN_SETTINGS -> "Open settings"
        SET_VARIABLE -> "Set variable"
        CLEAR_VARIABLE -> "Delete variable"
        INCREMENT_VARIABLE -> "Increment variable"
        LOG_MESSAGE -> "Log a message"
        SET_AIRPLANE_MODE -> "Set airplane mode"
        SET_LOCATION_MODE -> "Set location mode"
        SET_BATTERY_SAVER -> "Set battery saver"
        SET_ALWAYS_ON_DISPLAY -> "Set always-on display"
        SET_NIGHT_LIGHT_SECURE -> "Set night light"
        SET_PRIVATE_DNS -> "Set private DNS"
        SET_IMMERSIVE_MODE -> "Set immersive mode"
        SET_ANIMATION_SCALES -> "Set animation speed"
        SET_STAY_ON_WHILE_CHARGING -> "Keep screen on while charging"
        SET_SCREEN_TIMEOUT -> "Set screen timeout"
        SET_AUTO_ROTATE -> "Set auto-rotate"
        PLAY_TTS -> "Speak text"
        FLASHLIGHT -> "Set flashlight"
        LOCK_SCREEN -> "Lock screen"
        OPEN_NOTIFICATIONS -> "Open notifications"
        OPEN_QUICK_SETTINGS -> "Open quick settings"
        TAKE_SCREENSHOT -> "Take screenshot"
        CLICK_AT_COORDS -> "Tap screen position"
        CLICK_BY_TEXT -> "Tap text on screen"
        SWIPE_GESTURE -> "Swipe on screen"
        DISMISS_NOTIFICATION -> "Dismiss notification"
    }
}

enum class ExecutionMode {
    ONCE_PER_TRANSITION,   // default: fire when the condition becomes true
    EVERY_MATCH,           // fire on every matching event
    COOLDOWN;              // fire, then ignore until cooldown elapses

    fun label(): String = when (this) {
        ONCE_PER_TRANSITION -> "Once per change"
        EVERY_MATCH -> "Every time"
        COOLDOWN -> "With cooldown"
    }
}

enum class VariableType(val label: String) {
    BOOLEAN("Boolean"),
    INTEGER("Number"),
    STRING("Text"),
    TIMESTAMP("Timestamp");
}

enum class AutomationStatus { ENABLED, DISABLED }

enum class LogResult {
    SUCCESS,          // all actions ran
    PARTIAL,          // some actions ran, some failed/skipped
    FAILED,           // conditions met but all actions failed
    CONDITION_FAIL,   // conditions were not met
    COOLDOWN_SKIPPED, // trigger matched but cooldown active
    DEDUPED,          // duplicate of a transfer already handled
    DISABLED;         // automation disabled at dispatch time
}

object Shared {
    const val DAY_BIT_MONDAY = 1
    const val DAY_BIT_SUNDAY = 64
    const val ALL_DAYS: Int = 0b1111111
    const val WEEKDAYS: Int = 0b0011111
    const val WEEKEND: Int = 0b1100000
}

fun weekdayBit(dayOfWeekIso: java.time.DayOfWeek): Int {
    // ISO: Monday=1 .. Sunday=7 ; our bit layout matches (bit1=Mon .. bit7=Sun)
    return 1 shl (dayOfWeekIso.value - 1)
}