package dev.lolokeksu.legioncontrol

import java.util.Locale

internal data class SmartInsightMetric(val title: String, val value: String, val detail: String)

internal object SmartInsightsPresentation {
    private fun ratio(value: Long, known: Long): String =
        if (known <= 0L) "н/д" else String.format(
            Locale.US, "%.1f%% · %d/%d", value.toDouble() * 100.0 / known, value, known
        )

    fun metrics(result: SmartInsightsResult): List<SmartInsightMetric> = listOf(
        SmartInsightMetric("Всего сессий", result.sessions.toString(), "Все записи за выбранный период"),
        SmartInsightMetric("Завершено вручную", result.completedSessions.toString(), "end_reason = USER_STOP"),
        SmartInsightMetric("Прервано / завершено иначе", result.interruptedSessions.toString(),
            "Не считается штатным завершением пользователя"),
        SmartInsightMetric("Время завершённых сессий", "${result.totalCompletedMinutes} мин",
            "Только записи, остановленные пользователем"),
        SmartInsightMetric("Измерения", result.sampleCount.toString(), "Фактически сохранённые точки SQLite"),
        SmartInsightMetric("Пик температуры батареи",
            result.peakBatteryTempDeciC?.let { String.format(Locale.US, "%.1f °C", it / 10.0) } ?: "н/д",
            "Не температура CPU/GPU"),
        SmartInsightMetric("Thermal ≥ SEVERE", ratio(result.severeReadings, result.thermalSampleCount),
            "Доля среди точек с известным Android Thermal Status"),
        SmartInsightMetric("Сеть не подтверждена", ratio(result.unvalidatedReadings, result.networkSampleCount),
            "Доля среди точек с известным Network Validation")
    )

    fun report(result: SmartInsightsResult): String =
        "АНАЛИТИКА · ${result.packageName} · ${result.days} дн.\n" +
            "Показаны только сохранённые данные. Отсутствующие значения не считаются нулём. " +
            "Температура батареи не равна температуре CPU/GPU; частота дисплея не FPS игры; " +
            "Network Validation не является игровым ping."
}
