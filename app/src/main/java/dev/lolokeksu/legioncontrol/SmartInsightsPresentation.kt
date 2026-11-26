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
        SmartInsightMetric("Без завершения", (result.sessions - result.completedSessions -
            result.interruptedSessions).coerceAtLeast(0).toString(),
            "Сессии без ended_at_ms"),
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

    fun report(result: SmartInsightsResult): String = buildString {
        append("АНАЛИТИКА · ").append(result.packageName).append(" · ").append(result.days).append(" дн.\n")
        append("Сессий: ").append(result.sessions)
            .append(" · вручную завершено: ").append(result.completedSessions)
            .append(" · прервано: ").append(result.interruptedSessions)
            .append(" · открыто: ").append((result.sessions - result.completedSessions -
                result.interruptedSessions).coerceAtLeast(0)).append('\n')
        append("Время завершённых сессий: ").append(result.totalCompletedMinutes).append(" мин\n")
        append("Пик температуры батареи: ")
            .append(result.peakBatteryTempDeciC?.let { String.format(Locale.US, "%.1f °C", it / 10.0) } ?: "н/д")
            .append('\n')
        append("Thermal ≥ SEVERE: ").append(ratio(result.severeReadings, result.thermalSampleCount)).append('\n')
        append("Сеть не подтверждена: ").append(ratio(result.unvalidatedReadings, result.networkSampleCount)).append('\n')
        append("Показаны только сохранённые данные. Отсутствующие значения не считаются нулём. ")
        append("Температура батареи не равна температуре CPU/GPU; частота дисплея не FPS игры; ")
        append("Network Validation не является игровым ping.")
    }

}
