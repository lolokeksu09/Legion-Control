package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Only direct public-Android-API readings. Null is genuinely unknown. */
data class GameTelemetryPoint(
    val timeMs: Long,
    val batteryPercent: Int?,
    val batteryTempDeciC: Int?,
    val currentMicroA: Int?,
    val thermalStatus: Int?,
    val powerSaving: Boolean?,
    val refreshHz: Float?,
    val networkType: String,
    val networkValidated: Boolean?
)

data class GameSessionHeader(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val endReason: String?,
    val tcpOptIn: Boolean
)

data class GameSessionEvent(
    val timeMs: Long,
    val kind: String,
    val details: String
)

data class GameSessionSummary(
    val label: String,
    val packageName: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val pointCount: Int,
    val firstPercent: Int?,
    val lastPercent: Int?,
    val minTempDeciC: Int?,
    val maxTempDeciC: Int?,
    val thermalSevereCount: Int,
    val powerSavingCount: Int,
    val unvalidatedNetworkCount: Int,
    val unknownNetworkCount: Int,
    val stopReason: String?
) {
    val batteryDelta: Int?
        get() = if (firstPercent == null || lastPercent == null) null else lastPercent - firstPercent
    fun durationMs(nowMs: Long = System.currentTimeMillis()): Long =
        ((endedAtMs ?: nowMs) - startedAtMs).coerceAtLeast(0)
}

object GameSessionAnalysis {
    fun summarize(header: GameSessionHeader, points: List<GameTelemetryPoint>): GameSessionSummary {
        val validBattery = points.mapNotNull { it.batteryPercent?.takeIf { p -> p in 0..100 } }
        val validTemps = points.mapNotNull { it.batteryTempDeciC?.takeIf { t -> t in -400..1200 } }
        return GameSessionSummary(
            header.appLabel, header.packageName, header.startedAtMs, header.endedAtMs,
            points.size, validBattery.firstOrNull(), validBattery.lastOrNull(),
            validTemps.minOrNull(), validTemps.maxOrNull(),
            points.count { (it.thermalStatus ?: -1) >= 3 },
            points.count { it.powerSaving == true },
            points.count { it.networkValidated == false },
            points.count { it.networkType == "UNKNOWN" },
            header.endReason
        )
    }

    fun warnings(s: GameSessionSummary): List<String> = buildList {
        if (s.pointCount == 0) add("Нет записанных измерений: показатели недоступны.")
        if (s.thermalSevereCount > 0) add("Android сообщал Thermal Status ≥ SEVERE: ${s.thermalSevereCount} замеров.")
        if (s.powerSavingCount > 0) add("Энергосбережение было включено: ${s.powerSavingCount} замеров.")
        if (s.unvalidatedNetworkCount > 0) add("Сеть не была подтверждена Android: ${s.unvalidatedNetworkCount} замеров.")
        if (s.unknownNetworkCount > 0) add("Состояние сети было неизвестно: ${s.unknownNetworkCount} замеров.")
        if ((s.maxTempDeciC ?: -1000) >= 450) add("Температура батареи достигала 45 °C и выше.")
    }

    fun render(s: GameSessionSummary): String = buildString {
        append(s.label).append(" (").append(s.packageName).append(")\n")
        append("Продолжительность: ").append(s.durationMs()/60_000).append(" мин · ")
            .append("Замеров: ").append(s.pointCount).append('\n')
        append("Батарея: ").append(s.firstPercent?.let { "$it%" } ?: "н/д")
            .append(" → ").append(s.lastPercent?.let { "$it%" } ?: "н/д")
        s.batteryDelta?.let { append(" (").append(if (it > 0) "+" else "").append(it).append(" п.п.)") }
        append('\n')
        append("Температура батареи: ")
        append(s.minTempDeciC?.let { String.format(Locale.US,"%.1f",it/10.0) } ?: "н/д")
            .append("–")
            .append(s.maxTempDeciC?.let { String.format(Locale.US,"%.1f °C",it/10.0) } ?: "н/д")
            .append('\n')
        if (s.endedAtMs == null) append("Сессия не завершена (возможен обрыв процесса).\n")
        else append("Завершение: ").append(s.stopReason ?: "не указано").append('\n')
        warnings(s).forEach { append("• ").append(it).append('\n') }
        append("Это диагностика устройства, не FPS, ping игрового сервера и не доказательство причин лагов.")
    }

    fun compare(a: GameSessionSummary, b: GameSessionSummary): String = buildString {
        append("СРАВНЕНИЕ ДВУХ ПОСЛЕДНИХ СЕССИЙ\n")
        append(a.label).append(" / ").append(b.label).append('\n')
        append("Длительность: ").append(a.durationMs()/60_000)
            .append(" / ").append(b.durationMs()/60_000).append(" мин\n")
        append("Заряд Δ: ").append(a.batteryDelta?.let { "$it п.п." } ?: "н/д")
            .append(" / ").append(b.batteryDelta?.let { "$it п.п." } ?: "н/д").append('\n')
        append("Пик температуры батареи: ")
            .append(a.maxTempDeciC?.let { "${it/10.0} °C" } ?: "н/д")
            .append(" / ")
            .append(b.maxTempDeciC?.let { "${it/10.0} °C" } ?: "н/д").append('\n')
        append("Замеров Thermal ≥3: ").append(a.thermalSevereCount)
            .append(" / ").append(b.thermalSevereCount).append('\n')
        append("Невалидированная сеть: ").append(a.unvalidatedNetworkCount)
            .append(" / ").append(b.unvalidatedNetworkCount).append('\n')
        append("Это сравнение условий, а не оценка производительности игр.")
    }
}
