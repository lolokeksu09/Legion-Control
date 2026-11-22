package dev.lolokeksu.legioncontrol

import java.util.Locale

/** A user-authorized bounded foreground series. Values come from real Android APIs. */
data class TimedBatteryPoint(
    val elapsedRealtimeMs: Long,
    val recordedAtMs: Long,
    val batteryPercent: Int,
    val temperatureDeciC: Int,
    val currentMicroAmps: Int?,
    val voltageMillivolts: Int?,
    val thermalStatus: Int?
)

enum class TimedBatteryStopReason {
    COMPLETED, USER_STOP, APP_BACKGROUND, THERMAL_SEVERE, BATTERY_HOT, READING_FAILED
}

data class TimedBatteryTestResult(
    val testId: String,
    val sessionId: String,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val attemptedReads: Int,
    val points: List<TimedBatteryPoint>,
    val reason: TimedBatteryStopReason
) {
    init {
        require(testId.isNotBlank() && sessionId.isNotBlank())
        require(startedAtMs > 0 && finishedAtMs >= startedAtMs)
        require(attemptedReads >= points.size && attemptedReads >= 0)
    }
}

data class SavedTimedBatteryTest(
    val testId: String,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val reason: TimedBatteryStopReason,
    val attemptedReads: Int,
    val successfulReads: Int,
    val firstBatteryPercent: Int?,
    val lastBatteryPercent: Int?,
    val minTemperatureDeciC: Int?,
    val maxTemperatureDeciC: Int?,
    val maxThermalStatus: Int?
)

object TimedBatteryPolicy {
    const val DURATION_MS = 60_000L
    const val INTERVAL_MS = 10_000L
    const val HOT_TEMPERATURE_DECI_C = 450

    /**
     * The OS thermal code takes precedence. Battery temperature is a cautious
     * stop guard, NOT a substitute for Android thermal protection or a CPU reading.
     */
    fun earlyStop(point: TimedBatteryPoint): TimedBatteryStopReason? = when {
        point.thermalStatus != null && point.thermalStatus in 3..6 ->
            TimedBatteryStopReason.THERMAL_SEVERE
        point.temperatureDeciC >= HOT_TEMPERATURE_DECI_C ->
            TimedBatteryStopReason.BATTERY_HOT
        else -> null
    }

    /** Fixed wall schedule from monotonic elapsed time; never spin on missed ticks. */
    fun nextDelayMs(startElapsedMs: Long, observedElapsedMs: Long): Long? {
        if (observedElapsedMs < startElapsedMs) return null
        val elapsed = observedElapsedMs - startElapsedMs
        if (elapsed >= DURATION_MS) return null
        val next = (elapsed / INTERVAL_MS + 1L) * INTERVAL_MS
        return (next - elapsed).coerceAtLeast(1L)
    }
}

object TimedBatterySummary {
    fun format(result: TimedBatteryTestResult): String = buildString {
        append("ТЕСТ БАТАРЕИ · 60 с, шаг 10 с · только при открытом экране\n")
        append("Причина завершения: ").append(when (result.reason) {
            TimedBatteryStopReason.COMPLETED -> "период завершён"
            TimedBatteryStopReason.USER_STOP -> "остановлен пользователем"
            TimedBatteryStopReason.APP_BACKGROUND -> "приложение свернуто"
            TimedBatteryStopReason.THERMAL_SEVERE -> "остановлен: Android SEVERE или выше"
            TimedBatteryStopReason.BATTERY_HOT -> "остановлен: батарея ≥45 °C"
            TimedBatteryStopReason.READING_FAILED -> "остановлен: 3 неудачных измерения"
        }).append('\n')
        append("Попыток: ").append(result.attemptedReads)
            .append(" · принято Rust/Android: ").append(result.points.size).append('\n')
        val ordered = result.points.sortedBy { it.elapsedRealtimeMs }
        if (ordered.isEmpty()) {
            append("Нет достоверных показаний для расчёта.")
            return@buildString
        }
        val first = ordered.first()
        val last = ordered.last()
        append("Температура батареи, диапазон: ")
            .append(String.format(
                Locale.US, "%.1f–%.1f °C",
                ordered.minOf { it.temperatureDeciC } / 10.0,
                ordered.maxOf { it.temperatureDeciC } / 10.0
            )).append('\n')
        if (ordered.size >= 2 && last.elapsedRealtimeMs > first.elapsedRealtimeMs) {
            append("Изменение заряда: ")
                .append(String.format(Locale.US, "%+d п.п.", last.batteryPercent - first.batteryPercent))
                .append("\nИзменение температуры батареи: ")
                .append(String.format(Locale.US, "%+.1f °C",
                    (last.temperatureDeciC - first.temperatureDeciC) / 10.0))
                .append('\n')
        } else append("Изменения: недостаточно отдельных показаний\n")
        val current = ordered.mapNotNull { it.currentMicroAmps }
        append("Показания тока по Android: ")
            .append(if (current.isEmpty()) "недоступны" else
                String.format(
                    Locale.US, "%+.0f…%+.0f мА",
                    current.min() / 1000.0, current.max() / 1000.0
                ))
            .append('\n')
        append("Не измеряет FPS, CPU/GPU, ёмкость, точную мощность адаптера или Bypass. ")
        append("Сохранение в SQLite проверяется отдельно.")
    }
}
