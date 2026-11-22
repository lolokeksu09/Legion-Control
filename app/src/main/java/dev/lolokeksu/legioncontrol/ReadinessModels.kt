package dev.lolokeksu.legioncontrol

import java.util.Locale

/**
 * A rule-based interpretation of observed Android API values, not a forecast
 * of Wild Rift FPS, match ping, CPU frequency, or OEM GameTime capability.
 */
data class GamingReadinessInput(
    val batteryPercent: Int?,
    val batteryTemperatureDeciC: Int?,
    val thermalStatus: Int?,
    val powerSaving: Boolean?,
    val refreshRateHz: Float?,
    val networkConnected: Boolean?,
    val networkValidated: Boolean?,
    val memoryLow: Boolean?
)
enum class ReadinessSeverity { OK, CAUTION, CRITICAL, UNKNOWN }
data class ReadinessFinding(
    val key: String,
    val severity: ReadinessSeverity,
    val explanation: String
)

object GamingReadinessRules {
    fun analyze(v: GamingReadinessInput): List<ReadinessFinding> {
        val findings = ArrayList<ReadinessFinding>(7)
        fun add(key: String, severity: ReadinessSeverity, text: String) {
            findings += ReadinessFinding(key,severity,text)
        }
        when {
            v.thermalStatus == null -> add("thermal", ReadinessSeverity.UNKNOWN,
                "Android не выдал статус перегрева")
            v.thermalStatus >= 3 -> add("thermal",ReadinessSeverity.CRITICAL,
                "Android сообщает SEVERE или выше: нагрузку лучше прекратить")
            v.thermalStatus == 2 -> add("thermal",ReadinessSeverity.CAUTION,
                "Android сообщает MODERATE: тепловой запас ограничен")
            else -> add("thermal",ReadinessSeverity.OK,
                "Системный статус перегрева ниже MODERATE")
        }
        when {
            v.batteryTemperatureDeciC == null -> add("temperature",ReadinessSeverity.UNKNOWN,
                "Температура батареи не определена")
            v.batteryTemperatureDeciC >= 450 -> add("temperature",ReadinessSeverity.CRITICAL,
                "Температура батареи ≥45 °C: не начинайте нагрузочный тест")
            v.batteryTemperatureDeciC >= 400 -> add("temperature",ReadinessSeverity.CAUTION,
                "Температура батареи ≥40 °C")
            else -> add("temperature",ReadinessSeverity.OK,"Батарея ниже 40 °C")
        }
        when {
            v.batteryPercent == null -> add("battery",ReadinessSeverity.UNKNOWN,
                "Уровень батареи не определён")
            v.batteryPercent <= 15 -> add("battery",ReadinessSeverity.CAUTION,
                "Низкий заряд (≤15 %) — возможна ограничительная политика")
            else -> add("battery",ReadinessSeverity.OK,"Заряд более 15 %")
        }
        when (v.powerSaving) {
            true -> add("power",ReadinessSeverity.CAUTION,
                "Системное энергосбережение включено; игра может получить ограничение частоты кадров")
            false -> add("power",ReadinessSeverity.OK,"Энергосбережение выключено")
            null -> add("power",ReadinessSeverity.UNKNOWN,"Состояние энергосбережения неизвестно")
        }
        when {
            v.refreshRateHz == null || !v.refreshRateHz.isFinite() || v.refreshRateHz <= 0f ->
                add("display",ReadinessSeverity.UNKNOWN,"Текущий режим дисплея недоступен")
            v.refreshRateHz < 90f ->
                add("display",ReadinessSeverity.CAUTION,
                    "Дисплей работает в режиме ниже 90 Гц; это не FPS игры")
            else -> add("display",ReadinessSeverity.OK,
                "Текущий режим дисплея ≥90 Гц; это не доказательство FPS игры")
        }
        when {
            v.networkConnected == false -> add("network",ReadinessSeverity.CAUTION,
                "Android не видит активную сеть")
            v.networkConnected == null || v.networkValidated == null ->
                add("network",ReadinessSeverity.UNKNOWN,
                    "Проверка подключения Android недоступна")
            !v.networkValidated -> add("network",ReadinessSeverity.CAUTION,
                "Android пока не подтвердил доступ в интернет")
            else -> add("network",ReadinessSeverity.OK,
                "Android валидировал подключение; задержка до сервера игры не измерялась")
        }
        when (v.memoryLow) {
            true -> add("memory",ReadinessSeverity.CAUTION,"Android сообщает о низкой доступной RAM")
            false -> add("memory",ReadinessSeverity.OK,"Android не сообщает о критичной нехватке RAM")
            null -> add("memory",ReadinessSeverity.UNKNOWN,"Состояние RAM недоступно")
        }
        return findings
    }

    fun format(findings: List<ReadinessFinding>): String = buildString {
        append("GAMING READINESS · снимок Android API по запросу\n")
        for (finding in findings) {
            append(when(finding.severity) {
                ReadinessSeverity.OK -> "OK"
                ReadinessSeverity.CAUTION -> "ВНИМАНИЕ"
                ReadinessSeverity.CRITICAL -> "ОГРАНИЧЕНИЕ"
                ReadinessSeverity.UNKNOWN -> "НЕИЗВЕСТНО"
            }).append(" · ").append(finding.explanation).append('\n')
        }
        append("Это диагностические признаки, не прогноз FPS, ping, температуры CPU/GPU и не управление устройством.")
    }
}

/** Small CPU arithmetic workload, bounded and strictly app-process-local. */
data class ComputeProbeResult(
    val iterations: Int,
    val elapsedNanos: Long,
    val checksum: Long
)
object ComputeProbeMath {
    const val ITERATIONS = 500_000
    fun checksum(iterations: Int, stopRequested: () -> Boolean = { false }): Long {
        require(iterations in 1..ITERATIONS)
        var value = 0x1234ABCDL
        for (i in 0 until iterations) {
            if (i % 4096 == 0 && stopRequested()) throw InterruptedException("CPU probe canceled")
            value = (value * 1664525L + 1013904223L + i) and 0xFFFFFFFFL
        }
        return value
    }

    fun format(v: ComputeProbeResult): String {
        val elapsedMs = v.elapsedNanos / 1_000_000.0
        return buildString {
            append("COMPUTE PROBE · собственный процесс · фиксированные целочисленные операции\n")
            append("Операций: ").append(v.iterations).append('\n')
            append("Время: ").append(String.format(Locale.US,"%.2f мс",elapsedMs)).append('\n')
            append("Контрольная сумма: ").append(v.checksum).append('\n')
            append("Не частота CPU/GPU, не эталонный рейтинг и не замер производительности Wild Rift.")
        }
    }
}
