package dev.lolokeksu.legioncontrol

import kotlin.math.abs

/** Android PowerManager thermal statuses, preserving the platform numeric code. */
object ThermalSeverity {
    fun describe(code: Int?): String = when (code) {
        0 -> "0 · нет теплового ограничения"
        1 -> "1 · лёгкое ограничение"
        2 -> "2 · умеренное ограничение"
        3 -> "3 · серьёзное ограничение"
        4 -> "4 · критическое ограничение"
        5 -> "5 · аварийное ограничение"
        6 -> "6 · требуется выключение"
        else -> "недоступно"
    }
}

enum class BatteryCounterAgreement {
    /** Not enough sufficiently separated independent changes to compare. */
    INSUFFICIENT,
    /** Percentage and charge-counter changes point in the same direction. */
    AGREE,
    /** Opposite directions; repeat before drawing any device conclusions. */
    DISAGREE
}

data class DiagnosticTrend(
    val intervalMs: Long,
    val thermalStatusDelta: Int?,
    val thermalHeadroomDelta: Float?,
    val chargeCounterDeltaMicroAh: Long?,
    val percentageDelta: Int?,
    val batteryAgreement: BatteryCounterAgreement
)

/**
 * Compares only two actual manually acquired snapshots in the current process.
 * No nominal battery capacity, wear, fake FPS, CPU temperature or OEM assumptions.
 * An elapsedRealtime rollback (reboot) or >6h gap discards the old baseline.
 */
class DiagnosticTrendEngine {
    private data class Baseline(
        val snapshot: DeviceDiagnosticSnapshot,
        val batteryPercent: Int?
    )

    private var last: Baseline? = null

    @Synchronized
    fun clear() {
        last = null
    }

    @Synchronized
    fun record(snapshot: DeviceDiagnosticSnapshot, batteryPercent: Int?): DiagnosticTrend? {
        val validPercent = batteryPercent?.takeIf { it in 0..100 }
        val current = Baseline(snapshot, validPercent)
        val before = last
        last = current
        if (before == null) return null

        val elapsed = snapshot.resources.elapsedRealtimeMs -
            before.snapshot.resources.elapsedRealtimeMs
        if (elapsed <= 0L || elapsed > MAX_COMPARISON_INTERVAL_MS) return null

        val oldThermal = before.snapshot.capabilities.thermalStatus
        val newThermal = snapshot.capabilities.thermalStatus
        val thermalDelta = if (oldThermal != null && newThermal != null)
            newThermal - oldThermal else null

        val oldHeadroom = before.snapshot.resources.thermalHeadroom.value
        val newHeadroom = snapshot.resources.thermalHeadroom.value
        val headroomDelta = if (oldHeadroom != null && newHeadroom != null)
            newHeadroom - oldHeadroom else null

        val oldCounter = before.snapshot.resources.batteryChargeCounterMicroAh.value
        val newCounter = snapshot.resources.batteryChargeCounterMicroAh.value
        val counterDelta = if (oldCounter != null && newCounter != null)
            newCounter.toLong() - oldCounter.toLong() else null

        val percentDelta = if (before.batteryPercent != null && validPercent != null)
            validPercent - before.batteryPercent else null

        val agreement = if (
            elapsed >= MIN_BATTERY_COMPARISON_INTERVAL_MS &&
            counterDelta != null && percentDelta != null &&
            abs(counterDelta) >= MIN_CHARGE_CHANGE_MICRO_AH &&
            abs(percentDelta) >= MIN_PERCENT_CHANGE
        ) {
            if ((counterDelta > 0L) == (percentDelta > 0))
                BatteryCounterAgreement.AGREE
            else BatteryCounterAgreement.DISAGREE
        } else BatteryCounterAgreement.INSUFFICIENT

        return DiagnosticTrend(
            intervalMs = elapsed,
            thermalStatusDelta = thermalDelta,
            thermalHeadroomDelta = headroomDelta,
            chargeCounterDeltaMicroAh = counterDelta,
            percentageDelta = percentDelta,
            batteryAgreement = agreement
        )
    }

    companion object {
        private const val MAX_COMPARISON_INTERVAL_MS = 6L * 60 * 60 * 1000
        private const val MIN_BATTERY_COMPARISON_INTERVAL_MS = 60_000L
        private const val MIN_CHARGE_CHANGE_MICRO_AH = 50_000L
        private const val MIN_PERCENT_CHANGE = 2
    }
}
