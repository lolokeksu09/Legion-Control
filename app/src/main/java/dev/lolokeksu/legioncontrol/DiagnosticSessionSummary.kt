package dev.lolokeksu.legioncontrol

import java.util.Locale

/**
 * Computes a bounded, on-demand trend from real saved SQLite samples.
 * No forecasting, background monitoring or assumed full battery capacity.
 */
data class DiagnosticSessionTrend(
    val samples: Int,
    val elapsedMs: Long,
    val batteryTemperatureDeltaDeciC: Int?,
    val batteryTemperatureMinimumDeciC: Int?,
    val batteryTemperatureMaximumDeciC: Int?,
    val thermalHeadroomDelta: Float?,
    val chargeCounterDeltaMicroAh: Long?,
    val batteryPercentDelta: Int?,
    val averageChargeCounterRateMa: Double?
)

object DiagnosticSessionAnalyzer {
    fun analyze(readings: List<DiagnosticStoredSample>): DiagnosticSessionTrend? {
        if (readings.size < 2) return null
        val chronological = readings.sortedBy { it.elapsedMs }
        val first = chronological.first()
        val last = chronological.last()
        if (chronological.any { it.sessionId != first.sessionId }) return null
        val duration = last.elapsedMs - first.elapsedMs
        if (duration <= 0L || duration > 6L * 60 * 60 * 1000) return null
        val temperatures = chronological.mapNotNull { it.batteryTemperatureDeciC }
        val temperatureDelta = if (first.batteryTemperatureDeciC != null &&
            last.batteryTemperatureDeciC != null
        ) last.batteryTemperatureDeciC - first.batteryTemperatureDeciC else null
        val headroomDelta = if (first.thermalHeadroom != null && last.thermalHeadroom != null)
            last.thermalHeadroom - first.thermalHeadroom else null
        val chargeDelta = if (first.chargeCounterMicroAh != null &&
            last.chargeCounterMicroAh != null
        ) last.chargeCounterMicroAh.toLong() - first.chargeCounterMicroAh.toLong() else null
        val percentDelta = if (first.batteryPercent != null && last.batteryPercent != null)
            last.batteryPercent - first.batteryPercent else null
        val averageChargeRate = if (duration >= 60_000L && chargeDelta != null) {
            // µAh per elapsed hour = µA; divide by 1000 to obtain mA.
            chargeDelta.toDouble() * 3_600.0 / duration
        } else null

        return DiagnosticSessionTrend(
            samples = chronological.size,
            elapsedMs = duration,
            batteryTemperatureDeltaDeciC = temperatureDelta,
            batteryTemperatureMinimumDeciC = temperatures.minOrNull(),
            batteryTemperatureMaximumDeciC = temperatures.maxOrNull(),
            thermalHeadroomDelta = headroomDelta,
            chargeCounterDeltaMicroAh = chargeDelta,
            batteryPercentDelta = percentDelta,
            averageChargeCounterRateMa = averageChargeRate
        )
    }
}

object DiagnosticSessionSummary {
    fun format(trend: DiagnosticSessionTrend?): String {
        if (trend == null) {
            return "ИСТОРИЯ ДИАГНОСТИКИ SQLite · требуется минимум два сохранённых замера в одном сеансе"
        }
        return buildString {
            append("ИСТОРИЯ ДИАГНОСТИКИ SQLite · ")
                .append(trend.samples).append(" замеров за ")
                .append(String.format(Locale.US, "%.1f с", trend.elapsedMs / 1000.0))
                .append('\n')
            append("Температура батареи: ")
                .append(trend.batteryTemperatureDeltaDeciC?.let {
                    String.format(Locale.US, "%+.1f °C", it / 10.0)
                } ?: "недоступно")
                .append("; диапазон ")
                .append(if (trend.batteryTemperatureMinimumDeciC != null &&
                    trend.batteryTemperatureMaximumDeciC != null
                ) String.format(
                    Locale.US, "%.1f–%.1f °C",
                    trend.batteryTemperatureMinimumDeciC / 10.0,
                    trend.batteryTemperatureMaximumDeciC / 10.0
                ) else "недоступно")
                .append('\n')
            append("Thermal Headroom: ").append(trend.thermalHeadroomDelta?.let {
                String.format(Locale.US, "%+.2f", it)
            } ?: "недоступно").append('\n')
            append("Уровень батареи: ").append(trend.batteryPercentDelta?.let {
                String.format(Locale.US, "%+d п.п.", it)
            } ?: "недоступно").append('\n')
            append("Счётчик заряда: ").append(trend.chargeCounterDeltaMicroAh?.let {
                String.format(Locale.US, "%+.1f мА·ч", it / 1000.0)
            } ?: "недоступно").append('\n')
            append("Средний темп по счётчику: ")
                .append(trend.averageChargeCounterRateMa?.let {
                    String.format(Locale.US, "%+.0f мА", it)
                } ?: "недоступно")
                .append(" (не мощность зарядного устройства и не диагноз батареи)")
        }
    }
}
