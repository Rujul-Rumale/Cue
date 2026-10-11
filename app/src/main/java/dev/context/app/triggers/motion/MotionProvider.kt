package dev.context.app.triggers.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEventListener
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.Automation
import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import dev.context.app.triggers.ProviderSink
import dev.context.app.triggers.TriggerProvider
import kotlin.math.sqrt

/**
 * Motion triggers from low-power hardware sensors - no GPS, no polling.
 *
 * - SIGNIFICANT_MOTION: the hardware significant-motion sensor (one-shot,
 *   re-armed after every trigger).
 * - DEVICE_PICKUP: accelerometer jerk shortly after the proximity sensor
 *   reports near (phone in pocket/hand lifting). Best effort heuristic.
 */
object MotionProvider : TriggerProvider {

    @Volatile
    private var sink: ProviderSink? = null

    @Volatile
    private var contextRef: Context? = null

    @Volatile
    private var watchingMotion = false

    @Volatile
    private var watchingPickup = false

    private var lastAccel = FloatArray(3)
    private var lastAccelAt = 0L

    @Volatile
    private var lastNearAt = 0L

    private val significantListener = object : TriggerEventListener() {
        override fun onTrigger(event: android.hardware.TriggerEvent?) {
            sink?.emit(
                TriggerEvent(
                    TriggerType.SIGNIFICANT_MOTION,
                    mapOf(Param.AT to Clock.SYSTEM.nowMillis().toString()),
                ),
            )
            rearmSignificantMotion()
        }
    }

    private val pickupListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val now = Clock.SYSTEM.nowMillis()
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> {
                    if (event.values.firstOrNull() ?: Float.MAX_VALUE < event.sensor.maximumRange) {
                        lastNearAt = now
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val v = event.values
                    if (lastAccelAt > 0) {
                        val dt = (now - lastAccelAt).coerceAtLeast(1)
                        val jerk = sqrt(
                            ((v[0] - lastAccel[0]) * (v[0] - lastAccel[0]) +
                                (v[1] - lastAccel[1]) * (v[1] - lastAccel[1]) +
                                (v[2] - lastAccel[2]) * (v[2] - lastAccel[2])).toDouble(),
                        ) / dt * 1000.0
                        if (jerk > PICKUP_JERK && now - lastNearAt < PICKUP_WINDOW_MS) {
                            lastNearAt = 0L
                            sink?.emit(
                                TriggerEvent(
                                    TriggerType.DEVICE_PICKUP,
                                    mapOf(Param.AT to now.toString()),
                                ),
                            )
                        }
                    }
                    lastAccel = v.copyOf()
                    lastAccelAt = now
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun register(context: Context, sink: ProviderSink) {
        this.sink = sink
        contextRef = context.applicationContext
    }

    /** Starts/stops hardware listeners to match the current automation set. */
    fun sync(automations: List<Automation>) {
        val context = contextRef ?: return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val wantMotion = automations.any { it.enabled && it.trigger.type == TriggerType.SIGNIFICANT_MOTION }
        val wantPickup = automations.any { it.enabled && it.trigger.type == TriggerType.DEVICE_PICKUP }
        if (wantMotion && !watchingMotion) {
            val sensor = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
            if (sensor != null && runCatching { sm.requestTriggerSensor(significantListener, sensor) }.getOrDefault(false)) {
                watchingMotion = true
            }
        } else if (!wantMotion && watchingMotion) {
            runCatching { sm.cancelTriggerSensor(significantListener, sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)) }
            watchingMotion = false
        }
        if (wantPickup && !watchingPickup) {
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val prox = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            var ok = false
            if (accel != null) {
                ok = sm.registerListener(pickupListener, accel, SensorManager.SENSOR_DELAY_NORMAL) || ok
            }
            if (prox != null) {
                ok = sm.registerListener(pickupListener, prox, SensorManager.SENSOR_DELAY_NORMAL) || ok
            }
            watchingPickup = ok
        } else if (!wantPickup && watchingPickup) {
            runCatching { sm.unregisterListener(pickupListener) }
            watchingPickup = false
        }
    }

    private fun rearmSignificantMotion() {
        val context = contextRef ?: return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        runCatching { sm.requestTriggerSensor(significantListener, sensor) }
    }

    private const val PICKUP_JERK = 28.0
    private const val PICKUP_WINDOW_MS = 2_000L
}
