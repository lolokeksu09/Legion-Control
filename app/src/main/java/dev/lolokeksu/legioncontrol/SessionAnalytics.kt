package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Statistics derived exclusively from recorded rows of one Rust/SQLite session. */
data class SessionAnalyticsSnapshot(
    val sessionId: String,
    val samples: Int,
    val elapsedMs: Long?,
    val batteryPercentDelta: Int?,
    val batteryTemperatureDeltaDeciC: Int?,
    val minBatteryTemperatureDeciC: Int,
    val maxBatteryTemperatureDeciC: Int,
    val minRecordedRefreshHz: Float?,
    val maxRecordedRefreshHz: Float?,
    val powerSavingReadings: Int,
    val observedChargingReadings: Int
)

object SessionAnalytics {
    fun analyze(readings: List<StoredReading>): SessionAnalyticsSnapshot? {
        if (readings.isEmpty()) return null
        val sessionId = readings.first().sessionId
        if (sessionId.isBlank() || readings.any { it.sessionId != sessionId }) return null
        val sorted = readings.sortedWith(compareBy({ it.elapsedMs }, { it.recordedAtMs }))
        val first = sorted.first()
        val last = sorted.last()
        val elapsed = last.elapsedMs - first.elapsedMs
        val validDuration = elapsed.takeIf { sorted.size >= 2 && it > 0L &&
            it <= 30L * 24 * 60 * 60 * 1000 }
        val refresh = sorted.mapNotNull { it.refreshRateHz?.takeIf { hz ->
            hz.isFinite() && hz > 0f && hz <= 500f
        } }
        return SessionAnalyticsSnapshot(
            sessionId = sessionId,
            samples = sorted.size,
            elapsedMs = validDuration,
            batteryPercentDelta = if (validDuration != null) last.percent - first.percent else null,
            batteryTemperatureDeltaDeciC =
                if (validDuration != null) last.temperatureDeciC - first.temperatureDeciC else null,
            minBatteryTemperatureDeciC = sorted.minOf { it.temperatureDeciC },
            maxBatteryTemperatureDeciC = sorted.maxOf { it.temperatureDeciC },
            minRecordedRefreshHz = refresh.minOrNull(),
            maxRecordedRefreshHz = refresh.maxOrNull(),
            powerSavingReadings = sorted.count { it.powerSaving == true },
            observedChargingReadings = sorted.count { it.chargingStatus == "Заряжается" }
        )
    }

    fun format(snapshot: SessionAnalyticsSnapshot?, truncated: Boolean = false): String {
        if (snapshot == null) return "ПРОФИЛЬ СЕАНСА · сохранённых измерений пока нет"
        fun temperature(t: Int): String = String.format(Locale.US, "%.1f °C", t / 10.0)
        return buildString {
            append("ПРОФИЛЬ ПОСЛЕДНЕГО СЕАНСА SQLite · ")
                .append(snapshot.samples).append(" записанных точек")
                .append(if (truncated) " (показаны последние точки)" else "")
                .append('\n')
            append("Период между крайними замерами: ")
                .append(snapshot.elapsedMs?.let {
                    String.format(Locale.US, "%.1f мин", it / 60_000.0)
                } ?: "не определён").append('\n')
            append("Изменение заряда: ").append(snapshot.batteryPercentDelta?.let {
                String.format(Locale.US, "%+d п.п.", it)
            } ?: "недостаточно замеров").append('\n')
            append("Изменение температуры батареи: ")
                .append(snapshot.batteryTemperatureDeltaDeciC?.let {
                    String.format(Locale.US, "%+.1f °C", it / 10.0)
                } ?: "недостаточно замеров").append('\n')
            append("Диапазон температуры батареи: ")
                .append(temperature(snapshot.minBatteryTemperatureDeciC))
                .append("–")
                .append(temperature(snapshot.maxBatteryTemperatureDeciC))
                .append('\n')
            append("Заявленные Android режимы дисплея в точках: ")
                .append(if (snapshot.minRecordedRefreshHz != null &&
                    snapshot.maxRecordedRefreshHz != null
                ) String.format(
                    Locale.US, "%.1f–%.1f Гц",
                    snapshot.minRecordedRefreshHz, snapshot.maxRecordedRefreshHz
                ) else "нет данных").append('\n')
            append("Замеров с энергосбережением: ")
                .append(snapshot.powerSavingReadings).append('\n')
            append("Замеров со статусом «Заряжается»: ")
                .append(snapshot.observedChargingReadings).append('\n')
            append("Это сравнение ручных точек, не непрерывное измерение FPS, расхода энергии или автономности.")
        }
    }
}
