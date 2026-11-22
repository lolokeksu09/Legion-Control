package dev.lolokeksu.legioncontrol

import android.content.Context
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Presentation-agnostic read keys. No Android Activity, UI view or Rust JNI mock. */
enum class DiagnosticsReadKind(val pane: String) {
    HISTORY("history"),
    SENSOR_QUALITY("analytics"),
    SESSION_PROFILE("analytics"),
    THERMAL("thermal"),
    TIMED_BATTERY("timed"),
    SENSOR_READINGS("sensors")
}

/**
 * Read-only formatted projections from the existing SQLite v12 database.
 * The Activity still owns the original write/record/export executor so write order is unchanged.
 */
class DiagnosticsHistoryRepository(context: Context) : AutoCloseable {
    private val history = LocalHistoryStore(context.applicationContext)

    fun read(kind: DiagnosticsReadKind): String = when (kind) {
        DiagnosticsReadKind.HISTORY -> savedHistory()
        DiagnosticsReadKind.SENSOR_QUALITY ->
            SensorQualityAnalytics.format(history.recentSensorAttempts())
        DiagnosticsReadKind.SESSION_PROFILE -> {
            val count = history.latestSessionReadingCount()
            val points = history.latestSessionReadings()
            SessionAnalytics.format(SessionAnalytics.analyze(points), count > points.size)
        }
        DiagnosticsReadKind.THERMAL ->
            ThermalHistoryReport.format(history.recentThermalReadings())
        DiagnosticsReadKind.TIMED_BATTERY -> timedBattery()
        DiagnosticsReadKind.SENSOR_READINGS -> sensorHistory()
    }

    private fun savedHistory(): String {
        val total = history.count()
        val sessions = history.recentSessions()
        return buildString {
            append("СОХРАНЁННАЯ ИСТОРИЯ · ")
                .append(RussianQuantity.measurements(total.toInt())).append("\n")
            append("Сеансов: ").append(sessions.size)
            if (sessions.size == 12) append(" (показаны последние 12)")
            for (session in sessions) {
                append("\n· ")
                append(DateFormat.getDateTimeInstance().format(Date(session.startedAtMs)))
                append(" · ").append(RussianQuantity.measurements(session.samples))
                val durationMs = (session.lastAtMs - session.startedAtMs).coerceAtLeast(0L)
                append(" · ").append(String.format(
                    Locale.getDefault(), "%.1f с", durationMs / 1000.0
                ))
                if (session.endedAtMs != null) append(" · завершён")
            }
            append("\nЗаписи остаются после перезапуска приложения.")
        }
    }

    private fun timedBattery(): String {
        val tests = history.recentTimedBatteryTests()
        return if (tests.isEmpty()) "ИСТОРИЯ ТЕСТОВ БАТАРЕИ · сохранённых тестов нет"
        else buildString {
            append("ИСТОРИЯ ТЕСТОВ БАТАРЕИ · последние ")
                .append(tests.size).append('\n')
            for (t in tests) {
                append("· ")
                    .append(DateFormat.getDateTimeInstance().format(Date(t.startedAtMs)))
                    .append(" · ").append(t.reason.name)
                    .append(" · SQLite ").append(t.successfulReads)
                    .append("/").append(t.attemptedReads).append(" замеров")
                if (t.firstBatteryPercent != null && t.lastBatteryPercent != null) {
                    append(" · заряд ").append(
                        String.format(Locale.US, "%+d п.п.",
                            t.lastBatteryPercent - t.firstBatteryPercent)
                    )
                }
                append('\n')
            }
        }
    }

    private fun sensorHistory(): String {
        val latest = history.lastSensorSamples()
        val total = history.sensorSampleCount()
        val attempts = history.lastSensorAttempts()
        return buildString {
            append("ИСТОРИЯ ДАТЧИКОВ SQLite · ").append(total).append(" событий\n")
            for (kind in SensorKind.entries) {
                append("· ").append(kind.title).append(": ")
                val sample = latest[kind]
                if (sample == null) append("нет сохранённых событий")
                else {
                    append(sample.values.joinToString(" / ")).append(" ")
                        .append(kind.unit).append(" · ")
                        .append(DateFormat.getDateTimeInstance().format(Date(sample.recordedAtMs)))
                }
                append('\n')
            }
            append('\n')
            append(SensorSampleReport.formatSavedAttempts(attempts))
        }
    }

    override fun close() {
        history.close()
    }
}
