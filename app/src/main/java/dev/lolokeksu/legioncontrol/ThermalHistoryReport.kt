package dev.lolokeksu.legioncontrol

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Only actual manual battery/thermal readings already persisted in SQLite v2+. */
data class ThermalHistoryItem(
    val recordedAtMs: Long,
    val batteryTemperatureDeciC: Int?,
    val thermalStatus: Int?,
    val thermalHeadroom: Float?
)

object ThermalHistoryReport {
    fun warning(status: Int?, headroom: Float?): String? {
        if (status != null && status >= 3 && status <= 6) {
            return "Тепловое ограничение Android: " + ThermalSeverity.describe(status) +
                ". Проверь температуру после снижения нагрузки."
        }
        if (headroom != null && headroom.isFinite() && headroom >= 1f) {
            return "Android Thermal Headroom ≥ 1.0: оценка достигла порога серьёзного ограничения."
        }
        return null
    }

    fun format(records: List<ThermalHistoryItem>): String {
        if (records.isEmpty()) return "ТЕПЛОВАЯ ИСТОРИЯ · сохранённых измерений нет"
        val newestFirst = records.sortedByDescending { it.recordedAtMs }
        val temps = records.mapNotNull { it.batteryTemperatureDeciC }
        val severe = records.count { it.thermalStatus != null && it.thermalStatus in 3..6 }
        val first = newestFirst.last()
        val last = newestFirst.first()
        val validInterval = last.recordedAtMs - first.recordedAtMs
        val delta = if (newestFirst.size > 1 && validInterval in 1..21_600_000L &&
            first.batteryTemperatureDeciC != null && last.batteryTemperatureDeciC != null
        ) last.batteryTemperatureDeciC - first.batteryTemperatureDeciC else null
        return buildString {
            append("ТЕПЛОВАЯ ИСТОРИЯ SQLite · последние ")
                .append(newestFirst.size).append(" ручных измерений\n")
            append("Состояний SEVERE или выше: ").append(severe).append('\n')
            append("Батарея, диапазон: ")
            append(if (temps.isEmpty()) "нет данных" else String.format(
                Locale.US, "%.1f–%.1f °C",
                temps.min() / 10.0, temps.max() / 10.0
            )).append('\n')
            append("Изменение за выбранный период: ")
            append(delta?.let { String.format(Locale.US, "%+.1f °C", it / 10.0) }
                ?: "недостаточно сопоставимых замеров").append('\n')
            newestFirst.take(12).forEach { reading ->
                append("· ")
                    .append(DateFormat.getDateTimeInstance().format(Date(reading.recordedAtMs)))
                    .append(" · батарея ")
                    .append(reading.batteryTemperatureDeciC?.let {
                        String.format(Locale.US, "%.1f °C", it / 10.0)
                    } ?: "нет данных")
                    .append(" · статус ")
                    .append(ThermalSeverity.describe(reading.thermalStatus))
                    .append(" · headroom ")
                    .append(reading.thermalHeadroom?.let {
                        String.format(Locale.US, "%.2f", it)
                    } ?: "нет данных")
                    .append('\n')
            }
            append("Не измеряет температуру CPU/GPU и не управляет охлаждением.")
        }
    }
}
