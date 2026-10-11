package dev.context.app.automation.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.context.app.automation.engine.ActionExecutor
import dev.context.app.automation.engine.RuntimeSnapshot
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.model.ActionExecution
import dev.context.app.model.ActionSpec
import dev.context.app.model.ActionType
import dev.context.app.model.Param

/**
 * Device-level actions backed by Settings.Global / Settings.Secure.
 *
 * Requires the one-time ADB grant `WRITE_SECURE_SETTINGS` (see the in-app
 * setup wizard). Without it every action fails honestly with instructions
 * instead of crashing, so locked options degrade gracefully in the UI.
 */
class SecureSettingsExecutor(
    private val context: Context,
) : ActionExecutor {

    override suspend fun execute(action: ActionSpec, snapshot: RuntimeSnapshot, event: TriggerEvent): ActionExecution {
        if (!hasPermission()) {
            return ActionExecution(
                action,
                ok = false,
                message = "needs ADB grant: pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
            )
        }
        return try {
            when (action.type) {
                ActionType.SET_AIRPLANE_MODE -> setAirplane(action)
                ActionType.SET_LOCATION_MODE -> setLocationMode(action)
                ActionType.SET_BATTERY_SAVER -> putGlobal("low_power", action, bool = true)
                ActionType.SET_ALWAYS_ON_DISPLAY -> putSecure("doze_always_on", action)
                ActionType.SET_NIGHT_LIGHT_SECURE -> putSecure("night_display_activated", action)
                ActionType.SET_PRIVATE_DNS -> setPrivateDns(action)
                ActionType.SET_IMMERSIVE_MODE -> setImmersive(action)
                ActionType.SET_ANIMATION_SCALES -> setAnimationScales(action)
                ActionType.SET_STAY_ON_WHILE_CHARGING -> setStayOn(action)
                else -> ActionExecution(action, ok = false, message = "not a secure-settings action")
            }
        } catch (t: SecurityException) {
            ActionExecution(action, ok = false, message = "secure settings revoked?")
        } catch (t: Throwable) {
            ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun hasPermission(): Boolean =
        runCatching {
            Settings.Global.putString(context.contentResolver, "cue_perm_probe", "1")
            context.contentResolver.delete(Settings.Global.getUriFor("cue_perm_probe"), null, null)
            true
        }.getOrDefault(false)

    private fun onOff(action: ActionSpec): Int =
        if (action.params[Param.BOOL_VALUE] != "false") 1 else 0

    private fun putGlobal(key: String, action: ActionSpec, bool: Boolean): ActionExecution {
        val value = if (bool) onOff(action).toString() else (action.params[Param.VALUE] ?: "1")
        Settings.Global.putString(context.contentResolver, key, value)
        return ActionExecution(action, ok = true, message = "$key=$value")
    }

    private fun putSecure(key: String, action: ActionSpec): ActionExecution {
        Settings.Secure.putString(context.contentResolver, key, onOff(action).toString())
        return ActionExecution(action, ok = true, message = "$key=${onOff(action)}")
    }

    private fun setAirplane(action: ActionSpec): ActionExecution {
        val value = onOff(action)
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, value)
        context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", value == 1))
        return ActionExecution(action, ok = true, message = if (value == 1) "airplane on" else "airplane off")
    }

    private fun setLocationMode(action: ActionSpec): ActionExecution {
        // 0 = off, 3 = high accuracy.
        val mode = if (action.params[Param.STATE] == "off") 0 else 3
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.LOCATION_MODE, mode)
        return ActionExecution(action, ok = true, message = if (mode == 0) "location off" else "location on")
    }

    private fun setPrivateDns(action: ActionSpec): ActionExecution {
        val mode = action.params[Param.DNS_MODE] ?: "auto"
        val value = when (mode) {
            "off" -> "off"
            "hostname" -> "hostname"
            else -> "opportunistic"
        }
        Settings.Global.putString(context.contentResolver, "private_dns_mode", value)
        val host = action.params[Param.DNS_HOST]
        if (value == "hostname" && !host.isNullOrBlank()) {
            Settings.Global.putString(context.contentResolver, "private_dns_specifier", host.trim())
        }
        return ActionExecution(action, ok = true, message = "dns $value${if (value == "hostname" && !host.isNullOrBlank()) " ${host.trim()}" else ""}")
    }

    private fun setImmersive(action: ActionSpec): ActionExecution {
        val value = if (onOff(action) == 1) "immersive.status=*,immersive.navigation=*" else null
        Settings.Global.putString(context.contentResolver, "policy_control", value)
        return ActionExecution(action, ok = true, message = if (value == null) "immersive off" else "immersive on")
    }

    private fun setAnimationScales(action: ActionSpec): ActionExecution {
        val scale = action.params[Param.VALUE]?.toFloatOrNull() ?: 1f
        val clamped = scale.coerceIn(0f, 10f).toString()
        Settings.Global.putString(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, clamped)
        Settings.Global.putString(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, clamped)
        Settings.Global.putString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, clamped)
        return ActionExecution(action, ok = true, message = "${clamped}x")
    }

    private fun setStayOn(action: ActionSpec): ActionExecution {
        val value = if (onOff(action) == 1) 3 else 0 // AC|USB|wireless vs never
        Settings.Global.putInt(context.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, value)
        return ActionExecution(action, ok = true, message = if (value == 0) "stay-awake off" else "stay-awake on")
    }

    companion object {
        fun openWriteSecureSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
