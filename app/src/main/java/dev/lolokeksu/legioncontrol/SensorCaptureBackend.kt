package dev.lolokeksu.legioncontrol

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID

/** One explicit bounded capture; listeners unregistered on completion or cancel. */
class SensorCaptureBackend(
    context: Context,
    private val onComplete: (SensorCaptureResult) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private val captureId = UUID.randomUUID().toString()
    private val outcomes = mutableMapOf<SensorKind, SensorProbeOutcome>()
    private val registered = mutableMapOf<SensorKind, Sensor>()
    private var active = false
    private var startedElapsedNs = 0L
    private val timeout = Runnable { finish() }

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(!active)
        active = true
        startedElapsedNs = SystemClock.elapsedRealtimeNanos()
        for (kind in SensorKind.entries) {
            val sensor = runCatching { manager?.getDefaultSensor(kind.type) }.getOrNull()
            if (sensor == null) {
                outcomes[kind] = SensorProbeOutcome(kind, SensorProbeState.MISSING)
                continue
            }
            val ok = runCatching {
                manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
            }.getOrDefault(false)
            if (ok) registered[kind] = sensor
            else outcomes[kind] = SensorProbeOutcome(kind, SensorProbeState.REGISTRATION_FAILED)
        }
        if (outcomes.size == SensorKind.entries.size) finish()
        else handler.postDelayed(timeout, 3000L)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        val kind = SensorKind.fromType(event.sensor.type) ?: return
        if (kind !in registered || kind in outcomes) return
        val sample = SensorSampleValidation.accept(
            kind, event.values, event.accuracy, event.timestamp, startedElapsedNs,
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis()
        ) ?: return
        outcomes[kind] = SensorProbeOutcome(kind, SensorProbeState.CAPTURED, sample)
        manager?.unregisterListener(this, registered.remove(kind))
        if (outcomes.size == SensorKind.entries.size) finish()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    fun cancel() {
        if (!active) return
        active = false
        handler.removeCallbacks(timeout)
        manager?.unregisterListener(this)
        registered.clear()
    }

    private fun finish() {
        if (!active) return
        active = false
        handler.removeCallbacks(timeout)
        manager?.unregisterListener(this)
        registered.clear()
        onComplete(SensorCaptureResult(
            captureId,
            SensorKind.entries.map { kind ->
                outcomes[kind] ?: SensorProbeOutcome(kind, SensorProbeState.TIMEOUT)
            }
        ))
    }
}
