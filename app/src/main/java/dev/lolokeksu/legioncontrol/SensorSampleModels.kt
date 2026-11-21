package dev.lolokeksu.legioncontrol

import java.util.Locale
import kotlin.math.abs

/** Four public SensorManager types with explicit units. */
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

enum class SensorProbeState { CAPTURED, MISSING, REGISTRATION_FAILED, TIMEOUT, INVALID_DATA }

enum class SensorRejectionReason {
    INVALID_CLOCK, STALE_EVENT, FUTURE_EVENT, INVALID_ACCURACY, WRONG_DIMENSIONS,
    NON_FINITE, IMPLAUSIBLE_MAGNITUDE, NEGATIVE_MEASUREMENT
}

data class SensorProbeOutcome(
    val kind: SensorKind,
    val state: SensorProbeState,
    val sample: SensorSample? = null,
    val eventCount: Int = 0,
    val rejectedEvents: Int = 0,
    val latencyMs: Long? = null,
    val timeoutMs: Long = SensorCapturePolicy.BASE_TIMEOUT_MS,
    val reportingMode: Int? = null,
    val sensorName: String? = null,
    val rejectionReason: SensorRejectionReason? = null
) {
    init {
        require((state == SensorProbeState.CAPTURED) == (sample != null))
        require(sample == null || sample.kind == kind)
        require(eventCount >= rejectedEvents && rejectedEvents >= 0)
        require(latencyMs == null || latencyMs >= 0L)
        require(timeoutMs in 1000L..30_000L)
    }
}

data class SensorCaptureResult(
    val captureId: String,
    val outcomes: List<SensorProbeOutcome>,
    val recordedAtMs: Long = 0L
)

/** No polling loop; a separate 3-second grace window for light/on-change sources. */
object SensorCapturePolicy {
    const val BASE_TIMEOUT_MS = 3000L
    const val LIGHT_EXTENSION_MS = 3000L
    fun extendForLight(pending: Set<SensorKind>): Boolean = SensorKind.LIGHT in pending
    fun timeoutFor(kind: SensorKind): Long =
        if (kind == SensorKind.LIGHT) BASE_TIMEOUT_MS + LIGHT_EXTENSION_MS
        else BASE_TIMEOUT_MS
    fun stateWhenExpired(rejectedEvents: Int): SensorProbeState =
        if (rejectedEvents > 0) SensorProbeState.INVALID_DATA else SensorProbeState.TIMEOUT
}

/** Classifies rejected events without incorrectly counting missing data as zero. */
object SensorSampleValidation {
    fun rejectionReason(
        kind: SensorKind,
        values: FloatArray,
        accuracy: Int,
        eventTimestampNs: Long,
        startedElapsedNs: Long,
        observedElapsedNs: Long,
        recordedAtMs: Long
    ): SensorRejectionReason? {
        if (recordedAtMs <= 0 || startedElapsedNs <= 0 ||
            observedElapsedNs < startedElapsedNs) return SensorRejectionReason.INVALID_CLOCK
        if (eventTimestampNs <= startedElapsedNs) return SensorRejectionReason.STALE_EVENT
        if (eventTimestampNs > observedElapsedNs + 500_000_000L) {
            return SensorRejectionReason.FUTURE_EVENT
        }
        if (accuracy !in -1..3) return SensorRejectionReason.INVALID_ACCURACY
        if (values.size < kind.dimensions) return SensorRejectionReason.WRONG_DIMENSIONS
        val actual = values.take(kind.dimensions)
        if (actual.any { !it.isFinite() }) return SensorRejectionReason.NON_FINITE
        if (actual.any { abs(it) > 1_000_000f }) return SensorRejectionReason.IMPLAUSIBLE_MAGNITUDE
        if ((kind == SensorKind.LIGHT || kind == SensorKind.PROXIMITY) && actual[0] < 0f) {
            return SensorRejectionReason.NEGATIVE_MEASUREMENT
        }
        return null
    }

    fun accept(
        kind: SensorKind,
        values: FloatArray,
        accuracy: Int,
        eventTimestampNs: Long,
        startedElapsedNs: Long,
        observedElapsedNs: Long,
        recordedAtMs: Long
    ): SensorSample? {
        if (rejectionReason(
                kind, values, accuracy, eventTimestampNs, startedElapsedNs,
                observedElapsedNs, recordedAtMs
            ) != null
        ) return null
        return SensorSample(
            kind, eventTimestampNs, recordedAtMs, accuracy, values.take(kind.dimensions).toList()
        )
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
    private fun values(v: List<Float>): String =
        v.joinToString(" / ") { String.format(Locale.US, "%.3f", it) }

    private fun mode(value: Int?): String = when (value) {
        0 -> "непрерывный"
        1 -> "по изменению"
        2 -> "одно событие"
        3 -> "специальный"
        else -> "неизвестен"
    }

    fun format(
        capture: SensorCaptureResult,
        previous: Map<SensorKind, SensorSample>,
        saved: Int,
        total: Long
    ): String = buildString {
        append("ДАТЧИКИ · фактические события по запросу\n")
        for (outcome in capture.outcomes) {
            append("· ").append(outcome.kind.title).append(": ")
            when (outcome.state) {
                SensorProbeState.MISSING -> append("не обнаружен")
                SensorProbeState.REGISTRATION_FAILED -> append("отказ регистрации Android")
                SensorProbeState.TIMEOUT -> append("нет нового события за ")
                    .append(outcome.timeoutMs / 1000L).append(" с")
                SensorProbeState.INVALID_DATA -> append("события получены, но не прошли проверку")
                SensorProbeState.CAPTURED -> {
                    val sample = requireNotNull(outcome.sample)
                    append(values(sample.values)).append(" ").append(outcome.kind.unit)
                    append(" · точность Android=").append(sample.accuracy)
                    SensorSampleComparison.delta(sample, previous[outcome.kind])?.let {
                        append(" · Δ ").append(values(it)).append(" ").append(outcome.kind.unit)
                    }
                }
            }
            append(" · событий=").append(outcome.eventCount)
            append(" · отброшено=").append(outcome.rejectedEvents)
            outcome.latencyMs?.let { append(" · задержка=").append(it).append(" мс") }
            outcome.rejectionReason?.let { append(" · причина=").append(it.name) }
            append(" · режим=").append(mode(outcome.reportingMode))
            outcome.sensorName?.let { append(" · ").append(it) }
            append('\n')
        }
        append("Сохранено показаний: ").append(saved)
            .append("; всего показаний: ").append(total)
            .append(". Все результаты попыток, включая тайм-ауты, сохраняются отдельно.")
        append("\nΔ — разность с прошлым сохранённым показанием; без фонового мониторинга.")
    }

    fun formatSavedAttempts(attempts: Map<SensorKind, SavedSensorAttempt>): String = buildString {
        append("ПОСЛЕДНИЕ ПОПЫТКИ · SQLite\n")
        for (kind in SensorKind.entries) {
            append("· ").append(kind.title).append(": ")
            val a = attempts[kind]
            if (a == null) append("попыток не было")
            else {
                append(a.state.name).append(" · событий=").append(a.eventCount)
                append(" · отброшено=").append(a.rejectedEvents)
                a.rejectionReason?.let { append(" · причина=").append(it) }
                a.latencyMs?.let { append(" · задержка=").append(it).append(" мс") }
                append(" · ожидание=").append(a.timeoutMs / 1000L).append(" с")
            }
            append('\n')
        }
    }
}
