package dev.lolokeksu.legioncontrol

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Diagnostic calculations over actual observations. These are local probes,
 * not certified hardware benchmarks, FPS or instrument calibration.
 */
data class SandboxIoResult(
    val bytes: Long,
    val writeElapsedNs: Long,
    val readElapsedNs: Long,
    val verified: Boolean
)

object SandboxIoMetrics {
    fun mebibytesPerSecond(bytes: Long, elapsedNs: Long): Double? =
        if (bytes <= 0L || elapsedNs <= 0L) null
        else (bytes / 1_048_576.0) / (elapsedNs / 1_000_000_000.0)

    fun format(value: SandboxIoResult): String = buildString {
        append("STORAGE LAB · тест в собственном кэше Legion Control\n")
        append("Объём: ").append(String.format(Locale.US, "%.1f МиБ", value.bytes / 1_048_576.0)).append('\n')
        append("Запись с fsync: ").append(mebibytesPerSecond(value.bytes, value.writeElapsedNs)?.let {
            String.format(Locale.US, "%.1f МиБ/с", it)
        } ?: "не удалось измерить").append('\n')
        append("Чтение: ").append(mebibytesPerSecond(value.bytes, value.readElapsedNs)?.let {
            String.format(Locale.US, "%.1f МиБ/с", it)
        } ?: "не удалось измерить").append('\n')
        append("Контроль данных SHA-256: ").append(if (value.verified) "совпадает" else "ОШИБКА").append('\n')
        append("Android page cache влияет на чтение; это НЕ измерение скорости всего накопителя. ")
        append("Временный файл удаляется.")
    }
}

data class MotionObservation(val x: Double, val y: Double, val z: Double)
data class MotionReport(
    val accelerationCount: Int,
    val gyroCount: Int,
    val inclinationDeg: Double?,
    val accelerationMagnitudeVariation: Double?,
    val gyroRmsRadPerSec: Double?
)

object MotionMetrics {
    fun summarize(acceleration: List<MotionObservation>, gyro: List<MotionObservation>): MotionReport {
        val validAccel = acceleration.filter { valid(it) }
        val validGyro = gyro.filter { valid(it) }
        // Mean gravitational direction is only an approximation during motion.
        val meanX = validAccel.map { it.x }.average().takeIf { it.isFinite() }
        val meanY = validAccel.map { it.y }.average().takeIf { it.isFinite() }
        val meanZ = validAccel.map { it.z }.average().takeIf { it.isFinite() }
        val incline = if (meanX != null && meanY != null && meanZ != null) {
            val vertical = sqrt(meanX * meanX + meanY * meanY + meanZ * meanZ)
            if (vertical >= 1.0) Math.toDegrees(
                atan2(sqrt(meanX * meanX + meanY * meanY), meanZ)
            ) else null
        } else null
        val mags = validAccel.map { length(it) }
        val spread = if (mags.size >= 2) {
            val average = mags.average()
            sqrt(mags.map { (it - average) * (it - average) }.average())
        } else null
        val rms = if (validGyro.isNotEmpty()) sqrt(
            validGyro.map { val len = length(it); len * len }.average()
        ) else null
        return MotionReport(validAccel.size, validGyro.size, incline, spread, rms)
    }

    private fun valid(v: MotionObservation): Boolean =
        v.x.isFinite() && v.y.isFinite() && v.z.isFinite() &&
            kotlin.math.abs(v.x) < 500.0 && kotlin.math.abs(v.y) < 500.0 &&
            kotlin.math.abs(v.z) < 500.0
    private fun length(v: MotionObservation) = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)

    fun format(report: MotionReport): String = buildString {
        append("MOTION LAB · реальные SensorEvent за 5 секунд\n")
        append("Акселерометр: ").append(report.accelerationCount).append(" событий\n")
        append("Гироскоп: ").append(report.gyroCount).append(" событий\n")
        append("Приблизительный наклон от +Z: ")
            .append(report.inclinationDeg?.let { String.format(Locale.US, "%.1f°", it) }
                ?: "недостаточно данных").append('\n')
        append("Разброс модуля ускорения: ")
            .append(report.accelerationMagnitudeVariation?.let {
                String.format(Locale.US, "%.3f м/с²", it)
            } ?: "недостаточно данных").append('\n')
        append("RMS угловой скорости: ")
            .append(report.gyroRmsRadPerSec?.let {
                String.format(Locale.US, "%.3f рад/с", it)
            } ?: "недостаточно данных").append('\n')
        append("Наклон не является компасом или калиброванной ориентацией. ")
        append("Частота/задержка контроллера тача не измеряются.")
    }
}

data class NetworkTransition(val type: String, val elapsedMs: Long)

object NetworkTransitionMetrics {
    fun format(events: List<NetworkTransition>, durationMs: Long): String = buildString {
        append("NETWORK EVENTS · наблюдение только на открытом экране\n")
        append("Окно: ").append(String.format(Locale.US, "%.1f с", durationMs / 1000.0))
            .append(" · событий: ").append(events.size).append('\n')
        for (event in events.takeLast(16)) {
            append("· ").append(event.elapsedMs.coerceAtLeast(0L)).append(" мс: ")
                .append(event.type).append('\n')
        }
        if (events.isEmpty()) append("Изменений за окно не зарегистрировано.\n")
        append("Системные callbacks, не замер TCP jitter и не ping игры.")
    }
}
