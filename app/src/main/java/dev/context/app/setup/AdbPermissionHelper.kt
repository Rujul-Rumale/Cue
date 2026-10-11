package dev.context.app.setup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Helper to check ADB-granted permissions.
 *
 * Note: `adb` only exists on the developer's PC, never inside the app sandbox,
 * so this helper deliberately only *checks* state and builds copy-paste commands.
 * Granting always happens from the laptop during one-time setup.
 */
object AdbPermissionHelper {

    data class AdbPermission(
        val name: String,
        val description: String,
        val check: (Context) -> Boolean,
        val adbCommand: String,
    )

    fun getAllAdbPermissions(context: Context): List<AdbPermission> = listOf(
        AdbPermission(
            name = "WRITE_SECURE_SETTINGS",
            description = "Enables: Airplane mode, GPS toggle, Battery saver, Always-on display, Night Light, Private DNS, Immersive mode, Animation scales",
            check = { hasSecureSettingsPermission(it) },
            adbCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
        ),
        AdbPermission(
            name = "READ_LOGS",
            description = "Enables: Logcat monitoring for hidden system events (NFC tags, vendor charging states, camera events)",
            check = { ContextCompat.checkSelfPermission(it, "android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED },
            adbCommand = "adb shell pm grant ${context.packageName} android.permission.READ_LOGS",
        ),
        AdbPermission(
            name = "DUMP",
            description = "Enables: System state dumps for debugging and advanced triggers",
            check = { ContextCompat.checkSelfPermission(it, "android.permission.DUMP") == PackageManager.PERMISSION_GRANTED },
            adbCommand = "adb shell pm grant ${context.packageName} android.permission.DUMP",
        ),
        AdbPermission(
            name = "PACKAGE_USAGE_STATS",
            description = "Enables: Foreground app detection via UsageStatsManager (instant, zero-latency). Can also be granted in Settings > Apps > Special app access > Usage access.",
            check = { hasUsageStatsPermission(it) },
            adbCommand = "adb shell pm grant ${context.packageName} android.permission.PACKAGE_USAGE_STATS",
        ),
    )

    private fun hasSecureSettingsPermission(context: Context): Boolean {
        // WRITE_SECURE_SETTINGS has no runtime prompt; probe by writing a harmless test key.
        return try {
            Settings.Global.putString(context.contentResolver, "cue_test_permission", "test")
            context.contentResolver.delete(Settings.Global.getUriFor("cue_test_permission"), null, null)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 21) return true
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
}
