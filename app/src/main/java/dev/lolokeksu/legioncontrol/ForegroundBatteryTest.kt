package dev.lolokeksu.legioncontrol

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * No service, alarm, wake lock, WorkManager or receiver.
 * The Activity owns this controller and MUST cancel it from onStop().
 */
class ForegroundBatteryTest(
    private val testId: String,
    private val sessionId: String,
    private val sample: () -> TimedBatteryPoint?,
    private val onProgress: (attempts: Int, accepted: Int) -> Unit,
    private val onFinished: (TimedBatteryTestResult) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { tick() }
    private val points = mutableListOf<TimedBatteryPoint>()
    private var startedAtMs = 0L
    private var startedElapsedMs = 0L
    private var failures = 0
    private var attempts = 0
    private var active = false
    val isActive: Boolean get() = active

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(!active)
        active = true
        startedAtMs = System.currentTimeMillis()
        startedElapsedMs = SystemClock.elapsedRealtime()
        tick()
    }

    private fun tick() {
        if (!active) return
        attempts++
        val point = runCatching { sample() }.getOrNull()
        if (point != null) {
            points += point
            failures = 0
        } else failures++
        onProgress(attempts, points.size)
        val early = point?.let { TimedBatteryPolicy.earlyStop(it) }
        if (early != null) {
            stop(early)
            return
        }
        if (failures >= 3) {
            stop(TimedBatteryStopReason.READING_FAILED)
            return
        }
        val delay = TimedBatteryPolicy.nextDelayMs(
            startedElapsedMs, SystemClock.elapsedRealtime()
        )
        if (delay == null) stop(TimedBatteryStopReason.COMPLETED)
        else handler.postDelayed(tickRunnable, delay)
    }

    fun stop(reason: TimedBatteryStopReason) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!active) return
        active = false
        handler.removeCallbacks(tickRunnable)
        onFinished(TimedBatteryTestResult(
            testId = testId,
            sessionId = sessionId,
            startedAtMs = startedAtMs,
            finishedAtMs = System.currentTimeMillis().coerceAtLeast(startedAtMs),
            attemptedReads = attempts,
            points = points.toList(),
            reason = reason
        ))
    }
}
