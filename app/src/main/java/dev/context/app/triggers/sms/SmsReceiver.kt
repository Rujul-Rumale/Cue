package dev.context.app.triggers.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Param
import dev.context.app.model.TriggerType

/** Incoming SMS, with optional sender/body filters evaluated by the engine. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent).toList()
        }.getOrDefault(emptyList())
        if (parts.isEmpty()) return
        val sender = parts.firstOrNull()?.originatingAddress.orEmpty()
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val now = Clock.SYSTEM.nowMillis()
        runCatching {
            dev.context.app.ContextApp.container.dispatch(
                TriggerEvent(
                    TriggerType.SMS_RECEIVED,
                    mapOf(
                        Param.SENDER to sender,
                        Param.BODY to body,
                        Param.AT to now.toString(),
                    ),
                ),
            )
        }
    }
}
