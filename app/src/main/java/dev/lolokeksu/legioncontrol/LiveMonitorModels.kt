package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Monotonic run-relative time; null fields are unknown, never fabricated zeros. */
data class MonitorPoint(
    val elapsedMs: Long,
    val battery: Int? = null,
    val temperatureDeciC: Int? = null,
    val availableRamBytes: Long? = null,
    val totalRamBytes: Long? = null,
    val displayHz: Float? = null,
    val currentMicroA: Int? = null,
    val thermalStatus: Int? = null,
    val powerSaving: Boolean? = null,
    val network: String? = null,
    val validatedNetwork: Boolean? = null,
    val thermalHeadroom: Float? = null,
    val memoryState: ProbeState = ProbeState.UNAVAILABLE,
    val headroomState: ProbeState = ProbeState.UNAVAILABLE,
    val telemetryError: Boolean = false
)

enum class MonitorMetric(val label: String, val unit: String) {
    TEMPERATURE("Температура батареи", "°C"),
    RAM("Доступная RAM", "ГиБ"),
    DISPLAY("Частота дисплея", "Гц"),
    BATTERY("Заряд батареи", "%");

    fun value(p: MonitorPoint): Double? = when (this) {
        TEMPERATURE -> p.temperatureDeciC?.div(10.0)
        RAM -> p.availableRamBytes?.div(1073741824.0)
        DISPLAY -> p.displayHz?.toDouble()
        BATTERY -> p.battery?.toDouble()
    }
}

object MonitorPolicy {
    const val MAX_POINTS = 120
    val intervals = listOf(2000L, 5000L, 10000L)
    fun interval(value: Long): Long = value.takeIf { it in intervals } ?: 5000L

    fun append(previous: List<MonitorPoint>, point: MonitorPoint): List<MonitorPoint> {
        if (point.elapsedMs < 0 || previous.size >= MAX_POINTS ||
            previous.lastOrNull()?.let { point.elapsedMs < it.elapsedMs } == true) return previous
        return previous + point
    }

    fun delta(metric: MonitorMetric, baseline: MonitorPoint?, latest: MonitorPoint?): Double? {
        val before = baseline?.let(metric::value) ?: return null
        val after = latest?.let(metric::value) ?: return null
        return after - before
    }

    fun fmt(value: Double?, digits: Int = 1): String =
        value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "н/д"
}

/** Export exactly the captured values, with explicit source state and missing fields. */
object MonitorCsv {
    fun render(points: List<MonitorPoint>): String = buildString {
        append("elapsed_ms,battery_percent,battery_temperature_deci_c,available_ram_bytes,total_ram_bytes,display_hz,current_micro_a,thermal_status,power_saving,network,network_validated,thermal_headroom,memory_state,headroom_state,telemetry_error\r\n")
        for (point in points) {
            append(listOf(
                point.elapsedMs, point.battery, point.temperatureDeciC,
                point.availableRamBytes, point.totalRamBytes, point.displayHz,
                point.currentMicroA, point.thermalStatus, point.powerSaving
            ).joinToString(",") { it?.toString().orEmpty() })
            append(',').append(point.network?.let { CsvEscaper.cell(it) }.orEmpty())
            append(',').append(point.validatedNetwork?.toString().orEmpty())
            append(',').append(point.thermalHeadroom?.toString().orEmpty())
            append(',').append(point.memoryState.name)
            append(',').append(point.headroomState.name)
            append(',').append(point.telemetryError)
            append("\r\n")
        }
    }
}
