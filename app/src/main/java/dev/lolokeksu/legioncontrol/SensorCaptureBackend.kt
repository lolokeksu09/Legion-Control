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

/**
 * User-initiated one-shot capture, never a background monitor. One event per
 * sensor is accepted; unavailable and rejected events are distinguished.
 * The on-change light source gets one extra bounded 3-second window.
 */
class SensorCaptureBackend(
    context: Context,
    private val onComplete: (SensorCaptureResult) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private val captureId = UUID.randomUUID().toString()
    private val outcomes = mutableMapOf<SensorKind, SensorProbeOutcome>()
    private val registered = mutableMapOf<SensorKind, Sensor>()
    private val seen = mutableMapOf<SensorKind, Int>()
    private val rejected = mutableMapOf<SensorKind, Int>()
    private val lastRejection = mutableMapOf<SensorKind, SensorRejectionReason>()
    private val sensorNames = mutableMapOf<SensorKind, String>()
    private val sensorModes = mutableMapOf<SensorKind, Int>()
    private var active = false
    private var startedElapsedNs = 0L

    private val lightTimeout = Runnable { finish() }
    private val timeout = Runnable {
        // Accelerometer, gyro and proximity have the original 3s budget.
        for (kind in registered.keys.toList()) {
            if (kind != SensorKind.LIGHT) completeFailure(kind)
        }
        if (SensorCapturePolicy.extendForLight(registered.keys)) {
            handler.postDelayed(lightTimeout, SensorCapturePolicy.LIGHT_EXTENSION_MS)
        } else {
            finish()
        }
    }

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(!active)
        active = true
        startedElapsedNs = SystemClock.elapsedRealtimeNanos()
        for (kind in SensorKind.entries) {
            val sensor = runCatching { manager?.getDefaultSensor(kind.type) }.getOrNull()
            if (sensor == null) {
                outcomes[kind] = makeOutcome(kind, SensorProbeState.MISSING)
                continue
            }
            runCatching { sensor.name.take(80) }.getOrNull()?.let { sensorNames[kind] = it }
            runCatching { sensor.reportingMode }.getOrNull()?.let { sensorModes[kind] = it }
            val registeredOk = runCatching {
                manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
            }.getOrDefault(false)
            if (registeredOk) registered[kind] = sensor
            else outcomes[kind] = makeOutcome(kind, SensorProbeState.REGISTRATION_FAILED)
        }
        if (outcomes.size == SensorKind.entries.size) finish()
        else handler.postDelayed(timeout, SensorCapturePolicy.BASE_TIMEOUT_MS)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        val kind = SensorKind.fromType(event.sensor.type) ?: return
        if (kind !in registered || kind in outcomes) return
        seen[kind] = (seen[kind] ?: 0) + 1
        val observedNs = SystemClock.elapsedRealtimeNanos()
        val wallMs = System.currentTimeMillis()
        val invalid = SensorSampleValidation.rejectionReason(
            kind, event.values, event.accuracy, event.timestamp,
            startedElapsedNs, observedNs, wallMs
        )
        if (invalid != null) {
            rejected[kind] = (rejected[kind] ?: 0) + 1
            lastRejection[kind] = invalid
            return
        }
        val sample = SensorSampleValidation.accept(
            kind, event.values, event.accuracy, event.timestamp,
            startedElapsedNs, observedNs, wallMs
        ) ?: return
        outcomes[kind] = makeOutcome(
            kind, SensorProbeState.CAPTURED, sample,
            (observedNs - startedElapsedNs).coerceAtLeast(0L) / 1_000_000L
        )
        manager?.unregisterListener(this, registered.remove(kind))
        if (outcomes.size == SensorKind.entries.size) finish()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    /** Closing the Activity cancels all listeners without creating a fictitious event. */
    fun cancel() {
        if (!active) return
        active = false
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(lightTimeout)
        manager?.unregisterListener(this)
        registered.clear()
    }

    private fun makeOutcome(
        kind: SensorKind,
        state: SensorProbeState,
        sample: SensorSample? = null,
        latencyMs: Long? = null
    ) = SensorProbeOutcome(
        kind = kind,
        state = state,
        sample = sample,
        eventCount = seen[kind] ?: 0,
        rejectedEvents = rejected[kind] ?: 0,
        latencyMs = latencyMs,
        timeoutMs = SensorCapturePolicy.timeoutFor(kind),
        reportingMode = sensorModes[kind],
        sensorName = sensorNames[kind],
        rejectionReason = lastRejection[kind]
    )

    private fun completeFailure(kind: SensorKind) {
        val state = SensorCapturePolicy.stateWhenExpired(rejected[kind] ?: 0)
        outcomes[kind] = makeOutcome(kind, state)
        registered.remove(kind)?.let { manager?.unregisterListener(this, it) }
    }

    private fun finish() {
        if (!active) return
        for (kind in registered.keys.toList()) completeFailure(kind)
        active = false
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(lightTimeout)
        manager?.unregisterListener(this)
        registered.clear()
        onComplete(SensorCaptureResult(
            captureId = captureId,
            outcomes = SensorKind.entries.map { outcomes.getValue(it) },
            recordedAtMs = System.currentTimeMillis()
        ))
    }
}
