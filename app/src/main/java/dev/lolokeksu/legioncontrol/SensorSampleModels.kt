package dev.lolokeksu.legioncontrol

import java.util.Locale
import kotlin.math.abs

/** Four public sensors; values and units always originate from SensorEvent. */
enum class SensorKind(val type: Int, val title: String, val dimensions: Int, val unit: String) {
    ACCELEROMETER(1, "Акселерометр", 3, "м/с²"),
    GYROSCOPE(4, "Гироскоп", 3, "рад/с"),
    LIGHT(5, "Освещённость", 1, "лк"),
    PROXIMITY(8, "Приближение", 1, "см");

    companion object {
        fun fromType(type: Int): SensorKind? = entries.firstOrNull { it.type == type }
    }
}

data class SensorSample(
    val kind: SensorKind,
    val eventTimestampNs: Long,
    val recordedAtMs: Long,
    val accuracy: Int,
    val values: List<Float>
)

enum class SensorProbeState { CAPTURED, MISSING, REGISTRATION_FAILED, TIMEOUT }

data class SensorProbeOutcome(
    val kind: SensorKind,
    val state: SensorProbeState,
    val sample: SensorSample? = null
) {
    init {
        require((state == SensorProbeState.CAPTURED) == (sample != null))
        require(sample == null || sample.kind == kind)
    }
}

data class SensorCaptureResult(
    val captureId: String,
    val outcomes: List<SensorProbeOutcome>
)

/** Pure JVM-testable validation; stale and invalid readings never become zero. */
object SensorSampleValidation {
    fun accept(
        kind: SensorKind,
        values: FloatArray,
        accuracy: Int,
        eventTimestampNs: Long,
        startedElapsedNs: Long,
        observedElapsedNs: Long,
        recordedAtMs: Long
    ): SensorSample? {
        if (recordedAtMs <= 0 || startedElapsedNs <= 0 ||
            eventTimestampNs <= startedElapsedNs || observedElapsedNs < startedElapsedNs ||
            eventTimestampNs > observedElapsedNs + 500_000_000L ||
            accuracy !in -1..3 || values.size < kind.dimensions
        ) return null
        val trimmed = values.take(kind.dimensions)
        if (trimmed.any { !it.isFinite() || abs(it) > 1_000_000f }) return null
        if ((kind == SensorKind.LIGHT || kind == SensorKind.PROXIMITY) && trimmed[0] < 0f) {
            return null
        }
        return SensorSample(kind, eventTimestampNs, recordedAtMs, accuracy, trimmed.toList())
    }
}

object SensorSampleComparison {
    fun delta(current: SensorSample, previous: SensorSample?): List<Float>? {
        if (previous == null || previous.kind != current.kind ||
            previous.values.size != current.values.size ||
            previous.recordedAtMs > current.recordedAtMs
        ) return null
        return current.values.zip(previous.values) { now, before -> now - before }
    }
}

object SensorSampleReport {
    private fun formatValues(values: List<Float>): String =
        values.joinToString(" / ") { String.format(Locale.US, "%.3f", it) }

    fun format(
        capture: SensorCaptureResult,
        previous: Map<SensorKind, SensorSample>,
        saved: Int,
        total: Long
    ): String = buildString {
        append("РЕАЛЬНЫЕ ПОКАЗАНИЯ ДАТЧИКОВ · по запросу\n")
        for (outcome in capture.outcomes) {
            append("· ").append(outcome.kind.title).append(": ")
            when (outcome.state) {
                SensorProbeState.MISSING -> append("не обнаружен")
                SensorProbeState.REGISTRATION_FAILED -> append("не удалось зарегистрировать")
                SensorProbeState.TIMEOUT -> append("нет нового события за 3 с")
                SensorProbeState.CAPTURED -> {
                    val sample = requireNotNull(outcome.sample)
                    append(formatValues(sample.values)).append(" ")
                        .append(outcome.kind.unit)
                        .append(" · точность Android=").append(sample.accuracy)
                    SensorSampleComparison.delta(sample, previous[outcome.kind])?.let {
                        append(" · Δ ").append(formatValues(it)).append(" ")
                            .append(outcome.kind.unit)
                    }
                }
            }
            append('\n')
        }
        append("В SQLite сохранено: ").append(saved).append("; всего событий: ")
            .append(total).append(". Δ — разность с предыдущим сохранённым замером.")
        append("\nНет непрерывного мониторинга, FPS или показаний CPU/GPU.")
    }
}
