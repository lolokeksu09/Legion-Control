package dev.lolokeksu.legioncontrol

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Sensor values only during a user-started five-second foreground capture. */
class MotionStabilityCapture(
    context: Context,
    private val completed: (MotionReport) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private val accelerations = ArrayList<MotionObservation>(500)
    private val rotations = ArrayList<MotionObservation>(500)
    private var startedNs = 0L
    private var active = false
    private val deadline = Runnable { stop(true) }

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(!active)
        active = true
        startedNs = SystemClock.elapsedRealtimeNanos()
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (accel != null) manager.registerListener(
            this, accel, SensorManager.SENSOR_DELAY_GAME, handler
        )
        if (gyro != null) manager.registerListener(
            this, gyro, SensorManager.SENSOR_DELAY_GAME, handler
        )
        handler.postDelayed(deadline, 5_000L)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.values.size < 3) return
        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (event.timestamp < startedNs - 250_000_000L ||
            event.timestamp > nowNs + 100_000_000L) return
        val value = MotionObservation(
            event.values[0].toDouble(), event.values[1].toDouble(),
            event.values[2].toDouble()
        )
        if (!value.x.isFinite() || !value.y.isFinite() || !value.z.isFinite()) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> if (accelerations.size < 500) accelerations.add(value)
            Sensor.TYPE_GYROSCOPE -> if (rotations.size < 500) rotations.add(value)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun stop(report: Boolean) {
        if (!active) return
        active = false
        handler.removeCallbacks(deadline)
        manager.unregisterListener(this)
        if (report) completed(MotionMetrics.summarize(accelerations, rotations))
    }
}

/**
 * Opt-in Android network change notifications; 30s, only while Activity visible.
 * Reports callback categories, no IP harvesting, packets, or game telemetry.
 */
class ForegroundNetworkEvents(
    context: Context,
    private val update: (String) -> Unit,
    private val completed: (String) -> Unit
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private val observations = ArrayList<NetworkTransition>(64)
    private var startedMs = 0L
    private var active = false
    private val timeout = Runnable { stop(true) }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = add("доступна активная сеть")
        override fun onLost(network: Network) = add("активная сеть потеряна")
        override fun onCapabilitiesChanged(
            network: Network, capabilities: NetworkCapabilities
        ) {
            val verified = capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
            add(if (verified) "Android подтверждает интернет"
                else "сеть не валидирована Android")
        }
        override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) =
            add("изменились параметры маршрута")
    }

    private fun add(message: String) {
        if (!active) return
        if (observations.size >= 64) return
        val elapsed = (SystemClock.elapsedRealtime() - startedMs).coerceAtLeast(0L)
        // Suppress duplicate callbacks in rapid succession.
        val last = observations.lastOrNull()
        if (last?.type == message && elapsed - last.elapsedMs < 300L) return
        observations += NetworkTransition(message, elapsed)
        update(NetworkTransitionMetrics.format(observations, elapsed))
    }

    fun start(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(!active)
        startedMs = SystemClock.elapsedRealtime()
        active = true
        val result = runCatching { cm.registerDefaultNetworkCallback(callback, handler) }
        if (result.isFailure) {
            active = false
            return false
        }
        handler.postDelayed(timeout, 30_000L)
        return true
    }

    fun stop(report: Boolean) {
        if (!active) return
        active = false
        handler.removeCallbacks(timeout)
        runCatching { cm.unregisterNetworkCallback(callback) }
        if (report) completed(NetworkTransitionMetrics.format(
            observations, (SystemClock.elapsedRealtime() - startedMs).coerceAtLeast(0L)
        ))
    }
}
