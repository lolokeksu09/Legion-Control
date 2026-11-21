package dev.lolokeksu.legioncontrol

import java.util.Locale

/**
 * Statistics over saved, user-requested capture attempts only. This is NOT
 * sensor uptime, sampling frequency, hardware accuracy or availability.
 */
data class SensorQualitySummary(
    val kind: SensorKind,
    val attempts: Int,
    val captured: Int,
    val timeouts: Int,
    val invalid: Int,
    val missing: Int,
    val registrationFailures: Int,
    val rejectedEvents: Long,
    val averageCapturedLatencyMs: Double?
)

object SensorQualityAnalytics {
    fun analyze(attempts: List<SavedSensorAttempt>): List<SensorQualitySummary> =
        SensorKind.entries.mapNotNull { kind ->
            val relevant = attempts.filter { it.kind == kind }
            if (relevant.isEmpty()) return@mapNotNull null
            val captured = relevant.filter { it.state == SensorProbeState.CAPTURED }
            val latencies = captured.mapNotNull { it.latencyMs }
            SensorQualitySummary(
                kind = kind,
                attempts = relevant.size,
                captured = captured.size,
                timeouts = relevant.count { it.state == SensorProbeState.TIMEOUT },
                invalid = relevant.count { it.state == SensorProbeState.INVALID_DATA },
                missing = relevant.count { it.state == SensorProbeState.MISSING },
                registrationFailures = relevant.count {
                    it.state == SensorProbeState.REGISTRATION_FAILED
                },
                rejectedEvents = relevant.sumOf { it.rejectedEvents.toLong() },
                averageCapturedLatencyMs = if (latencies.isEmpty()) null
                    else latencies.map { it.toDouble() }.average()
            )
        }

    fun format(attempts: List<SavedSensorAttempt>): String {
        val summaries = analyze(attempts)
        if (summaries.isEmpty()) return "АНАЛИТИКА ДАТЧИКОВ · завершённых попыток пока нет"
        return buildString {
            append("АНАЛИТИКА ДАТЧИКОВ SQLite · последние ")
                .append(attempts.size).append(" завершённых попыток\n")
            for (row in summaries) {
                append("· ").append(row.kind.title)
                    .append(": успешно ").append(row.captured).append("/")
                    .append(row.attempts)
                    .append(" · тайм-аут ").append(row.timeouts)
                    .append(" · неверные события ").append(row.invalid)
                    .append(" · отсутствует ").append(row.missing)
                    .append(" · отказ регистрации ").append(row.registrationFailures)
                    .append(" · отброшено событий ").append(row.rejectedEvents)
                    .append(" · средняя задержка успешных: ")
                    .append(row.averageCapturedLatencyMs?.let {
                        String.format(Locale.US, "%.0f мс", it)
                    } ?: "нет данных")
                    .append('\n')
            }
            append("Только ручные попытки; проценты и показатели времени работы датчиков не вычисляются.")
        }
    }
}
