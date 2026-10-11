package dev.context.app.actions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import dev.context.app.automation.engine.ActionExecutor
import dev.context.app.automation.executor.SecureSettingsExecutor
import dev.context.app.automation.engine.RuntimeSnapshot
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.automation.repository.AutomationRepository
import dev.context.app.core.Clock
import dev.context.app.model.ActionExecution
import dev.context.app.model.ActionSpec
import dev.context.app.model.ActionType
import dev.context.app.model.Param
import dev.context.app.model.Variable
import dev.context.app.model.VariableType
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Executes automation actions against the device using only public Android APIs.
 * Every action reports Success/Failure so the execution log is honest.
 */
class AndroidActionExecutor(
    private val context: Context,
    private val repository: AutomationRepository,
    private val clock: Clock = Clock.SYSTEM,
) : ActionExecutor {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val secure = SecureSettingsExecutor(context)

    override suspend fun execute(action: ActionSpec, snapshot: RuntimeSnapshot, event: TriggerEvent): ActionExecution {
        val result = withContext(Dispatchers.IO) { dispatch(action, snapshot, event) }
        return result
    }

    private suspend fun dispatch(action: ActionSpec, snapshot: RuntimeSnapshot, event: TriggerEvent): ActionExecution {
        return try {
            when (action.type) {
                ActionType.SHOW_NOTIFICATION -> showNotification(action)
                ActionType.SET_DND -> setDnd(action)
                ActionType.SET_VOLUME -> setVolume(action)
                ActionType.SET_RINGER_MODE -> setRingerMode(action)
                ActionType.VIBRATE -> vibrate(action)
                ActionType.SET_BRIGHTNESS -> setBrightness(action)
                ActionType.SET_NIGHT_LIGHT -> setNightLight(action)
                ActionType.SET_WIFI -> setWifi(action)
                ActionType.SET_MOBILE_DATA -> setMobileData(action)
                ActionType.SET_BLUETOOTH -> setBluetooth(action)
                ActionType.OPEN_APP -> openApp(action)
                ActionType.OPEN_URL -> openUrl(action)
                ActionType.OPEN_SETTINGS -> openSettings(action)
                ActionType.SET_VARIABLE -> setVariable(action)
                ActionType.INCREMENT_VARIABLE -> incrementVariable(action)
                ActionType.CLEAR_VARIABLE -> clearVariable(action)
                ActionType.LOG_MESSAGE -> ActionExecution(action, ok = true, message = action.params[Param.MESSAGE] ?: "")
                ActionType.SET_AIRPLANE_MODE, ActionType.SET_LOCATION_MODE, ActionType.SET_BATTERY_SAVER,
                ActionType.SET_ALWAYS_ON_DISPLAY, ActionType.SET_NIGHT_LIGHT_SECURE, ActionType.SET_PRIVATE_DNS,
                ActionType.SET_IMMERSIVE_MODE, ActionType.SET_ANIMATION_SCALES,
                ActionType.SET_STAY_ON_WHILE_CHARGING -> secure.execute(action, snapshot, event)
                ActionType.SET_SCREEN_TIMEOUT -> setScreenTimeout(action)
                ActionType.SET_AUTO_ROTATE -> setAutoRotate(action)
                ActionType.PLAY_TTS -> playTts(action)
                ActionType.FLASHLIGHT -> setFlashlight(action)
                ActionType.LOCK_SCREEN, ActionType.OPEN_NOTIFICATIONS, ActionType.OPEN_QUICK_SETTINGS,
                ActionType.TAKE_SCREENSHOT, ActionType.CLICK_AT_COORDS, ActionType.CLICK_BY_TEXT,
                ActionType.SWIPE_GESTURE -> dev.context.app.accessibility.CueAccessibilityService.perform(action)
                ActionType.DISMISS_NOTIFICATION -> dismissNotification(action, event)
            }
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private suspend fun showNotification(action: ActionSpec): ActionExecution {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return ActionExecution(action, ok = false, message = "notification permission missing")
        }
        ensureChannel()
        val title = substitute(action.params[Param.TITLE] ?: "Cue")
        val text = substitute(action.params[Param.TEXT] ?: title)
        val persistent = action.params[Param.PERSISTENT] == "true"
        // Key the notification off its own content so different automations (or
        // different messages) never overwrite each other, while re-firing the
        // same message still replaces itself instead of stacking.
        val id = (action.type.name + "|" + title + "|" + text).hashCode()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(persistent)
            .setAutoCancel(!persistent)
            .setCategory(if (persistent) NotificationCompat.CATEGORY_SERVICE else NotificationCompat.CATEGORY_STATUS)
        notificationManager.notify(id, builder.build())
        return ActionExecution(action, ok = true, message = if (persistent) "persistent" else "")
    }

    private fun setDnd(action: ActionSpec): ActionExecution {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return ActionExecution(action, ok = false, message = "DND access not granted")
        }
        val enable = action.params[Param.BOOL_VALUE] != "false"
        if (enable) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        } else {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        return ActionExecution(action, ok = true, message = if (enable) "DND on" else "DND off")
    }

    private fun setVolume(action: ActionSpec): ActionExecution {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (action.params[Param.STREAM]) {
            "ring" -> AudioManager.STREAM_RING
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val pct = (action.params[Param.VOLUME_PERCENT]?.toIntOrNull() ?: 50).coerceIn(0, 100)
        val max = am.getStreamMaxVolume(stream)
        val target = max * pct / 100
        am.setStreamVolume(stream, target, 0)
        return ActionExecution(action, ok = true, message = "$pct%")
    }

    private fun setRingerMode(action: ActionSpec): ActionExecution {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = when (action.params[Param.RINGER_MODE]) {
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return try {
            am.ringerMode = mode
            ActionExecution(action, ok = true, message = action.params[Param.RINGER_MODE] ?: "normal")
        } catch (t: SecurityException) {
            ActionExecution(action, ok = false, message = "grant notification policy access")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun vibrate(action: ActionSpec): ActionExecution {
        val ms = (action.params[Param.DURATION_MS]?.toLongOrNull() ?: 500L).coerceIn(50, 10_000L)
        return try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val v = vm.defaultVibrator
                if (!v.hasVibrator()) return ActionExecution(action, ok = false, message = "no vibrator")
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (!v.hasVibrator()) return ActionExecution(action, ok = false, message = "no vibrator")
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            }
            ActionExecution(action, ok = true, message = "$ms ms")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun setBrightness(action: ActionSpec): ActionExecution {
        val pct = (action.params[Param.BRIGHTNESS_PERCENT]?.toIntOrNull() ?: 20).coerceIn(0, 100)
        if (!Settings.System.canWrite(context)) {
            return ActionExecution(action, ok = false, message = "grant 'Modify system settings' (WRITE_SETTINGS) to dim")
        }
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, pct * 255 / 100)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            ActionExecution(action, ok = true, message = "$pct%")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun setNightLight(action: ActionSpec): ActionExecution {
        val intent = if (Build.VERSION.SDK_INT >= 25) {
            Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)
        } else {
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        }
        return launchSettings(action, intent, "opened night light settings (no direct API)")
    }

    private fun setWifi(action: ActionSpec): ActionExecution {
        val enable = action.params[Param.BOOL_VALUE] != "false"
        if (Build.VERSION.SDK_INT >= 29) {
            return launchSettings(action, Intent(Settings.ACTION_WIFI_SETTINGS), "Android 10+ blocks direct toggling; opened Wi-Fi settings")
        }
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            @Suppress("DEPRECATION")
            val ok = wm.setWifiEnabled(enable)
            ActionExecution(action, ok = ok, message = if (ok) "Wi-Fi ${if (enable) "on" else "off"}" else "toggle failed")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: "not permitted")
        }
    }

    private fun setMobileData(action: ActionSpec): ActionExecution {
        val enable = action.params[Param.BOOL_VALUE] != "false"
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method = ConnectivityManager::class.java.getMethod("setMobileDataEnabled", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(cm, enable)
            ActionExecution(action, ok = true, message = "mobile data ${if (enable) "on" else "off"}")
        } catch (t: Throwable) {
            launchSettings(action, Intent(Settings.ACTION_DATA_USAGE_SETTINGS), "direct mobile data toggle not exposed; opened settings")
        }
    }

    private fun setBluetooth(action: ActionSpec): ActionExecution {
        val enable = action.params[Param.BOOL_VALUE] != "false"
        if (Build.VERSION.SDK_INT >= 33) {
            return launchSettings(action, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Android 13+ blocks direct toggling; opened Bluetooth settings")
        }
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bt = bm.adapter ?: return ActionExecution(action, ok = false, message = "no bluetooth")
        return try {
            @Suppress("DEPRECATION")
            if (enable) bt.enable() else bt.disable()
            ActionExecution(action, ok = true, message = "Bluetooth ${if (enable) "on" else "off"}")
        } catch (t: SecurityException) {
            ActionExecution(action, ok = false, message = "BLUETOOTH_CONNECT not granted")
        }
    }

    /** Opens a system settings screen; the honest fallback for actions Android forbids to apps. */
    private fun launchSettings(action: ActionSpec, intent: Intent, message: String): ActionExecution =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionExecution(action, ok = false, message = message)
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }

    private fun openApp(action: ActionSpec): ActionExecution {
        val pkg = action.params[Param.PACKAGE] ?: return ActionExecution(action, ok = false, message = "no package")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_MAIN).setPackage(pkg)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return ActionExecution(action, ok = true)
        } catch (t: ActivityNotFoundException) {
            return ActionExecution(action, ok = false, message = "app not installed")
        }
    }

    private suspend fun openUrl(action: ActionSpec): ActionExecution {
        val url = substitute(action.params[Param.URL] ?: "")
        val uri = Uri.parse(url)
        if (uri.scheme == null) return ActionExecution(action, ok = false, message = "invalid url")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return ActionExecution(action, ok = true)
        } catch (t: ActivityNotFoundException) {
            return ActionExecution(action, ok = false, message = "no handler")
        }
    }

    private fun openSettings(action: ActionSpec): ActionExecution {
        val target = action.params[Param.SETTINGS_TARGET]
        val intent = when (target) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "power_save" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "nfc" -> Intent(Settings.ACTION_NFC_SETTINGS)
            "data_usage" -> Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "app_notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            "app_details" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
            "battery_optimization" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
            "volume" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return ActionExecution(action, ok = true)
        } catch (t: Throwable) {
            return ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private suspend fun setVariable(action: ActionSpec): ActionExecution {
        val key = action.params[Param.VARIABLE] ?: return ActionExecution(action, ok = false, message = "no variable")
        val raw = action.params[Param.VALUE] ?: ""
        val now = clock.nowMillis()
        val value = when (raw) {
            "now" -> now.toString()
            "current_time" -> now.toString()
            else -> substitute(raw)
        }
        val existing = repository.getVariable(key)
        repository.setVariable(
            Variable(
                key = key,
                type = existing?.type ?: inferType(value),
                value = value,
                updatedAt = now,
            ),
        )
        return ActionExecution(action, ok = true, message = "$key = $value")
    }

    private suspend fun incrementVariable(action: ActionSpec): ActionExecution {
        val key = action.params[Param.VARIABLE] ?: return ActionExecution(action, ok = false, message = "no variable")
        val current = repository.getVariable(key)?.asNumber() ?: 0L
        val delta = action.params[Param.VALUE]?.toLongOrNull() ?: 1L
        repository.setVariable(
            Variable(key = key, type = VariableType.INTEGER, value = (current + delta).toString(), updatedAt = clock.nowMillis()),
        )
        return ActionExecution(action, ok = true)
    }

    private suspend fun clearVariable(action: ActionSpec): ActionExecution {
        val key = action.params[Param.VARIABLE] ?: return ActionExecution(action, ok = false, message = "no variable")
        repository.deleteVariable(key)
        return ActionExecution(action, ok = true)
    }

    private fun setScreenTimeout(action: ActionSpec): ActionExecution {
        val ms = (action.params[Param.TIMEOUT_MS]?.toLongOrNull() ?: 60_000L).coerceIn(5_000L, 3_600_000L)
        if (!Settings.System.canWrite(context)) {
            return ActionExecution(action, ok = false, message = "grant 'Modify system settings' (WRITE_SETTINGS)")
        }
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms.toInt())
            ActionExecution(action, ok = true, message = "${ms / 1000}s")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun setAutoRotate(action: ActionSpec): ActionExecution {
        if (!Settings.System.canWrite(context)) {
            return ActionExecution(action, ok = false, message = "grant 'Modify system settings' (WRITE_SETTINGS)")
        }
        val on = action.params[Param.BOOL_VALUE] != "false"
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (on) 1 else 0,
            )
            ActionExecution(action, ok = true, message = if (on) "rotate on" else "rotate off")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private suspend fun playTts(action: ActionSpec): ActionExecution {
        val text = substitute(action.params[Param.TEXT] ?: action.params[Param.MESSAGE] ?: "")
        if (text.isBlank()) return ActionExecution(action, ok = false, message = "empty text")
        val ready = java.util.concurrent.CountDownLatch(1)
        var engine: android.speech.tts.TextToSpeech? = null
        return try {
            engine = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) ready.countDown()
            }
            if (!ready.await(4, java.util.concurrent.TimeUnit.SECONDS)) {
                return ActionExecution(action, ok = false, message = "tts engine unavailable")
            }
            @Suppress("DEPRECATION")
            engine?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "cue-${clock.nowMillis()}")
            ActionExecution(action, ok = true, message = "speaking")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        } finally {
            val e = engine
            kotlin.concurrent.thread {
                try {
                    Thread.sleep(3_000)
                } catch (ignored: InterruptedException) {
                }
                runCatching { e?.shutdown() }
            }
        }
    }

    private fun setFlashlight(action: ActionSpec): ActionExecution {
        val on = action.params[Param.BOOL_VALUE] != "false"
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val id = cm.cameraIdList.firstOrNull { id ->
                runCatching {
                    cm.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }.getOrDefault(false)
            } ?: return ActionExecution(action, ok = false, message = "no flash")
            cm.setTorchMode(id, on)
            ActionExecution(action, ok = true, message = if (on) "torch on" else "torch off")
        } catch (t: SecurityException) {
            ActionExecution(action, ok = false, message = "camera permission not granted")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun dismissNotification(action: ActionSpec, event: TriggerEvent): ActionExecution {
        val key = action.params[Param.KEY].takeUnless { it.isNullOrBlank() }
            ?: event.params[Param.NOTIF_KEY].takeUnless { it.isNullOrBlank() }
            ?: return ActionExecution(action, ok = false, message = "no notification key")
        val ok = dev.context.app.notification.CueNotificationListener.dismiss(key)
        return ActionExecution(
            action,
            ok = ok,
            message = if (ok) "dismissed" else "listener off or already gone",
        )
    }

    private fun inferType(value: String): VariableType = when {
        value.toLongOrNull() != null -> VariableType.INTEGER
        value == "true" || value == "false" -> VariableType.BOOLEAN
        else -> VariableType.STRING
    }

    private suspend fun substitute(input: String): String {
        var out = input
        repository.getVariables().forEach { v -> out = out.replace("{${v.key}}", v.value) }
        return out
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Automations", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Notifications shown by your automations"
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "automations"
    }
}