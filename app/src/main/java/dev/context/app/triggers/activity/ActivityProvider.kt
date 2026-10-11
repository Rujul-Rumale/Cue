package dev.context.app.triggers.activity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider

/**
 * Motion/activity triggers based on Google's Activity Recognition transitions API.
 * The platform only wakes us on real activity transitions - no continuous sampling.
 */
object ActivityProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var contextRef: Context? = null

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        contextRef = context.applicationContext
    }

    fun sync(automations: List<dev.context.app.model.Automation>) {
        val context = contextRef ?: return
        val anyActivity = automations.any {
            it.enabled && it.trigger.type in setOf(TriggerType.ACTIVITY_START, TriggerType.ACTIVITY_END)
        }
        if (!hasPermission(context)) {
            return
        }
        val client = ActivityRecognition.getClient(context)
        if (!anyActivity) {
            runCatching { client.removeActivityTransitionUpdates(pendingIntent(context)) }
            return
        }
        val transitions = listOf(
            DetectedActivity.STILL,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE,
        ).flatMap { activityType ->
            listOf(
                ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                ActivityTransition.ACTIVITY_TRANSITION_EXIT,
            ).map { transitionType ->
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(transitionType)
                    .build()
            }
        }
        val request = ActivityTransitionRequest(transitions)
        runCatching { client.requestActivityTransitionUpdates(request, pendingIntent(context)) }
    }

    internal fun onTransitions(context: Context, transitions: List<ActivityTransitionEvent>) {
        for (t in transitions) {
            val name = activityName(t.activityType) ?: continue
            ActivityStateCache.update(name)
            sink?.emit(
                TriggerEvent(
                    type = if (t.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        TriggerType.ACTIVITY_START
                    } else {
                        TriggerType.ACTIVITY_END
                    },
                    params = mapOf(Param.ACTIVITY to name),
                ),
            )
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ActivityRecognitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.ACTIVITY_RECOGNITION") == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission("com.google.android.gms.permission.ACTIVITY_RECOGNITION") == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

private fun activityName(detected: Int): String? = when (detected) {
            DetectedActivity.STILL -> "stationary"
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.ON_BICYCLE -> "cycling"
            DetectedActivity.IN_VEHICLE -> "driving"
            else -> null
        }

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = runCatching {
            com.google.android.gms.location.ActivityTransitionResult.extractResult(intent)
        }.getOrNull() ?: return
        val events = result.transitionEvents
        if (events.isEmpty()) return
        ActivityProvider.onTransitions(context, events)
    }
}