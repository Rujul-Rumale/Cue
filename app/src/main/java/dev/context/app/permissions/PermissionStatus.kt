package dev.context.app.permissions

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatus(
    val id: String,
    val label: String,
    val granted: Boolean,
    val explanation: String,
)

/**
 * Reads the real capability state of the device for every feature the app uses.
 * Used by the permission screen so nothing fails silently.
 */
class PermissionStatusReader(private val context: Context) {

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun all(): List<PermissionStatus> = listOf(
        location(), notifications(), activityRecognition(), bluetooth(), dnd(),
        batteryOptimization(), exactAlarms(), secureSettings(), notificationListener(), accessibility(), usageStats(),
    )

    fun location(): PermissionStatus {
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val backgroundRequired = Build.VERSION.SDK_INT >= 29
        val background = if (backgroundRequired)
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else true
        val providers = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val enabled = providers.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            providers.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        val grantedOk = (coarse || fine) && background && enabled
        return PermissionStatus(
            id = "location",
            label = "Location",
            granted = grantedOk,
            explanation = buildString {
                append(if (coarse || fine) "Coarse/Fine granted. " else "Needed to detect when you enter or leave a saved place. ")
                if (backgroundRequired && !background) {
                    append("Background location (\"Allow all the time\") is not granted - geofences will not fire in the background. ")
                }
                if (!enabled) append("Location services are switched off. ")
            },
        )
    }

    fun notifications(): PermissionStatus {
        val has = if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.POST_NOTIFICATIONS) else true
        return PermissionStatus(
            id = "notifications",
            label = "Notifications",
            granted = has,
            explanation = if (has) "" else "Needed so automations can show you a notification when they run.",
        )
    }

    fun activityRecognition(): PermissionStatus {
        val has = granted(Manifest.permission.ACTIVITY_RECOGNITION) || granted("com.google.android.gms.permission.ACTIVITY_RECOGNITION")
        return PermissionStatus(
            id = "activity_recognition",
            label = "Activity recognition",
            granted = has,
            explanation = "Optional. Needed for walking / driving / stationary motion triggers.",
        )
    }

    fun bluetooth(): PermissionStatus {
        val available = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ||
            BluetoothAdapter.getDefaultAdapter() != null
        val has = if (Build.VERSION.SDK_INT >= 31) granted(Manifest.permission.BLUETOOTH_CONNECT) else true
        return PermissionStatus(
            id = "bluetooth",
            label = "Bluetooth",
            granted = available && has,
            explanation = when {
                !available -> "This device reports no Bluetooth hardware."
                !has -> "Needed to detect Bluetooth device connections."
                else -> ""
            },
        )
    }

    fun dnd(): PermissionStatus {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val has = nm.isNotificationPolicyAccessGranted
        return PermissionStatus(
            id = "dnd",
            label = "Do Not Disturb access",
            granted = has,
            explanation = "Required by Android for any app to change Do Not Disturb. Open system settings to grant it.",
        )
    }

    fun batteryOptimization(): PermissionStatus {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignored = pm.isIgnoringBatteryOptimizations(context.packageName)
        return PermissionStatus(
            id = "battery_optimization",
            label = "Battery optimization",
            granted = ignored,
            explanation = "Some manufacturers kill background apps aggressively. Ignoring optimization keeps automations firing. Optional.",
        )
    }

    fun exactAlarms(): PermissionStatus {
        val has = if (Build.VERSION.SDK_INT >= 31) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            am.canScheduleExactAlarms()
        } else true
        return PermissionStatus(
            id = "exact_alarms",
            label = "Alarms & reminders",
            granted = has,
            explanation = if (has) "" else "Needed for precise time-of-day triggers. Without it, times may drift by a few minutes.",
        )
    }

    fun secureSettings(): PermissionStatus {
        val has = granted(Manifest.permission.WRITE_SECURE_SETTINGS)
        return PermissionStatus(
            id = "secure_settings",
            label = "Secure settings (ADB)",
            granted = has,
            explanation = if (has) "Airplane mode, GPS, battery saver and display actions are unlocked."
            else "One-time laptop grant. Needed for airplane mode, GPS toggle, battery saver and display actions. See Advanced (ADB) setup.",
        )
    }

    fun notificationListener(): PermissionStatus {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val has = flat?.contains(context.packageName, ignoreCase = true) == true
        return PermissionStatus(
            id = "notification_listener",
            label = "Notification access",
            granted = has,
            explanation = if (has) "" else "Needed for notification triggers and dismissing notifications.",
        )
    }

    fun accessibility(): PermissionStatus {
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val has = flat?.contains(context.packageName, ignoreCase = true) == true
        return PermissionStatus(
            id = "accessibility",
            label = "Accessibility service",
            granted = has,
            explanation = if (has) "" else "Needed for lock screen, notification shade, taps, swipes, app-open and volume-key triggers.",
        )
    }

    fun usageStats(): PermissionStatus {
        if (Build.VERSION.SDK_INT < 21) {
            return PermissionStatus(id = "usage_stats", label = "Usage access", granted = true, explanation = "")
        }
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        val has = mode == android.app.AppOpsManager.MODE_ALLOWED
        return PermissionStatus(
            id = "usage_stats",
            label = "Usage access",
            granted = has,
            explanation = if (has) "" else "Optional. Enables future foreground-app detection without accessibility.",
        )
    }
}