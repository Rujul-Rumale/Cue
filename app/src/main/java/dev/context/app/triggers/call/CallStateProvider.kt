package dev.context.app.triggers.call

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Automation
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import java.util.concurrent.Executors

/**
 * Call-state occurrences: ringing / active (off-hook) / idle.
 * Best effort on the caller number (often hidden); the state always fires.
 */
class CallStateProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var contextRef: Context? = null

    private val executor = Executors.newSingleThreadExecutor()
    private var callback: TelephonyCallback? = null

    @Suppress("DEPRECATION")
    private var legacyListener: android.telephony.PhoneStateListener? = null

    @Volatile
    private var watching = false

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        contextRef = context.applicationContext
    }

    /** Subscribes only while call-state automations exist. */
    fun sync(automations: List<Automation>) {
        val context = contextRef ?: return
        val want = automations.any { it.enabled && it.trigger.type == TriggerType.CALL_STATE_CHANGED }
        if (want && !watching) {
            if (!hasPermission(context)) return
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        emitState(state, null)
                    }
                }
                if (runCatching { tm.registerTelephonyCallback(executor, cb) }.isSuccess) {
                    callback = cb
                    watching = true
                }
            } else {
                @Suppress("DEPRECATION")
                val listener = object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        emitState(state, phoneNumber)
                    }
                }
                runCatching {
                    tm.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
                    legacyListener = listener
                    watching = true
                }
            }
        } else if (!want && watching) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            callback?.let { runCatching { tm?.unregisterTelephonyCallback(it) } }
            callback = null
            @Suppress("DEPRECATION")
            legacyListener?.let { runCatching { tm?.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE) } }
            legacyListener = null
            watching = false
        }
    }

    private fun emitState(state: Int, number: String?) {
        val name = when (state) {
            android.telephony.TelephonyManager.CALL_STATE_RINGING -> "ringing"
            android.telephony.TelephonyManager.CALL_STATE_OFFHOOK -> "active"
            else -> "idle"
        }
        sink?.emit(
            TriggerEvent(
                TriggerType.CALL_STATE_CHANGED,
                buildMap {
                    put(Param.STATE, name)
                    if (!number.isNullOrBlank()) put(Param.NUMBER, number)
                    put(Param.AT, Clock.SYSTEM.nowMillis().toString())
                },
            ),
        )
    }

    private fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
}
