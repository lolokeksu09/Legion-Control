package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Real samples only. Incomplete/charging sessions never get a discharge rate. */
data class SessionEvidence(
    val sessionId: String,
    val packageName: String,
    val appLabel: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val endReason: String?,
    val samples: Int,
    val observedMinutes: Double?,
    val sessionMinutes: Double?,
    val firstPercent: Int?,
    val lastPercent: Int?,
    val dischargePercentPerHour: Double?,
    val minBatteryTempDeciC: Int?,
    val peakBatteryTempDeciC: Int?,
    val severeThermalReadings: Int,
    val powerSaveReadings: Int,
    val networkUnvalidatedReadings: Int,
    val networkTransitions: Int,
    val gapsOver45s: Int,
    val largestGapSeconds: Double?,
    val qualityNotes: List<String>
)

data class ExperimentTrial(
    val arm: String,
    val session: GameSessionHeader,
    val note: String,
    val tag: String,
    val evidence: SessionEvidence
)

data class ExperimentArmSummary(
    val arm: String,
    val label: String,
    val sessions: Int,
    val validRates: Int,
    val averageDischargePerHour: Double?,
    val validPeaks: Int,
    val averagePeakBatteryTempC: Double?,
    val sessionsWithGaps: Int,
    val sessionsWithPowerSave: Int
)

object ExperimentAnalytics {
    private const val MIN_RATE_WINDOW_MS = 120_000L

    fun evidence(header: GameSessionHeader, points: List<GameTelemetryPoint>): SessionEvidence {
        val sorted = points.asSequence()
            .filter { it.timeMs >= header.startedAtMs &&
                (header.endedAtMs == null || it.timeMs <= header.endedAtMs) }
            .sortedBy { it.timeMs }.distinctBy { it.timeMs }.toList()
        val validBattery = sorted.filter { it.batteryPercent != null && it.batteryPercent in 0..100 }
        val validTemps = sorted.mapNotNull { it.batteryTempDeciC?.takeIf { t -> t in -400..1200 } }
        val gaps = sorted.zipWithNext().map { (a,b) -> (b.timeMs-a.timeMs).coerceAtLeast(0) }
        val first = validBattery.firstOrNull()
        val last = validBattery.lastOrNull()
        val isComplete = header.endedAtMs != null && header.endReason == "USER_STOP"
        val monotone = validBattery.zipWithNext().none { (a,b) ->
            (b.batteryPercent ?: 0) > (a.batteryPercent ?: 0)
        }
        val elapsedMs = if(first != null && last != null) last.timeMs-first.timeMs else 0L
        val rate = if(isComplete && monotone && validBattery.size>=3 &&
            elapsedMs>=MIN_RATE_WINDOW_MS && first!=null && last!=null) {
            (first.batteryPercent!!-last.batteryPercent!!)*3_600_000.0/elapsedMs
        } else null
        val actualWindow = if(sorted.size>1) (sorted.last().timeMs-sorted.first().timeMs)/60_000.0 else null
        val duration=header.endedAtMs?.let { (it-header.startedAtMs).coerceAtLeast(0)/60_000.0 }
        val transports=sorted.map { it.networkType }.filter { it!="UNKNOWN" && it!="NONE" }
        val transitions=transports.zipWithNext().count { (a,b)->a!=b }
        val quality = buildList {
            if(sorted.size<3) add("Недостаточно замеров (менее 3).")
            if(!isComplete) add("Запись не завершилась штатной остановкой пользователя.")
            if(gaps.any { it>45_000L }) add("Обнаружены интервалы между замерами более 45 секунд.")
            if(sorted.isNotEmpty() && sorted.first().timeMs-header.startedAtMs>45_000L)
                add("Первые 45 секунд сессии не покрыты замерами.")
            if(header.endedAtMs!=null && sorted.isNotEmpty() &&
                header.endedAtMs-sorted.last().timeMs>45_000L)
                add("Заключительная часть сессии не покрыта замерами.")
            if(!monotone) add("Заряд повышался во время сессии; скорость разряда не рассчитывается.")
            if(validBattery.size<3) add("Меньше 3 допустимых измерений процента батареи.")
            if(rate==null && isComplete && monotone && validBattery.size>=3)
                add("Слишком короткое окно измерения заряда для расчёта скорости.")
            if(sorted.any { it.powerSaving==true })
                add("Во время сессии наблюдалось энергосбережение.")
            if(sorted.any { it.networkValidated==false })
                add("Android не подтверждал сеть в части замеров.")
        }
        return SessionEvidence(
            sessionId=header.id,packageName=header.packageName,appLabel=header.appLabel,
            startedAtMs=header.startedAtMs,endedAtMs=header.endedAtMs,endReason=header.endReason,
            samples=sorted.size,observedMinutes=actualWindow,sessionMinutes=duration,
            firstPercent=first?.batteryPercent,lastPercent=last?.batteryPercent,
            dischargePercentPerHour=rate,
            minBatteryTempDeciC=validTemps.minOrNull(),
            peakBatteryTempDeciC=validTemps.maxOrNull(),
            severeThermalReadings=sorted.count { (it.thermalStatus ?: -1)>=3 },
            powerSaveReadings=sorted.count { it.powerSaving==true },
            networkUnvalidatedReadings=sorted.count { it.networkValidated==false },
            networkTransitions=transitions,
            gapsOver45s=gaps.count { it>45_000L },
            largestGapSeconds=gaps.maxOrNull()?.div(1000.0),
            qualityNotes=quality
        )
    }

    fun armSummary(arm: String, label: String, trials: List<ExperimentTrial>): ExperimentArmSummary {
        val selected=trials.filter { it.arm==arm }
        val rates=selected.mapNotNull { it.evidence.dischargePercentPerHour }
        val peaks=selected.mapNotNull { it.evidence.peakBatteryTempDeciC }
        return ExperimentArmSummary(
            arm,label,selected.size,rates.size,rates.takeIf { it.isNotEmpty() }?.average(),
            peaks.size,peaks.takeIf { it.isNotEmpty() }?.average()?.div(10.0),
            selected.count { it.evidence.gapsOver45s>0 },
            selected.count { it.evidence.powerSaveReadings>0 }
        )
    }

    fun formatEvidence(e: SessionEvidence): String=buildString {
        append("Сессия: ").append(e.appLabel).append(" · ").append(e.sessionId).append('\n')
        append("Запись: ").append(e.endReason ?: "НЕ ЗАВЕРШЕНА")
        append(" · ").append(e.samples).append(" замеров")
        append(" · время ").append(e.sessionMinutes?.let { formatNumber(it)+" мин" } ?: "н/д").append('\n')
        append("Наблюдаемое окно: ").append(e.observedMinutes?.let { formatNumber(it)+" мин" } ?: "н/д").append('\n')
        append("Заряд: ").append(e.firstPercent?.toString()?.plus("%") ?: "н/д").append(" → ")
            .append(e.lastPercent?.toString()?.plus("%") ?: "н/д").append('\n')
        append("Наблюдаемый разряд: ").append(e.dischargePercentPerHour?.let {
            formatNumber(it)+" процентных пунктов/час"
        } ?: "н/д (короткая/незавершённая/заряжаемая запись)").append('\n')
        append("Температура БАТАРЕИ min/max: ")
            .append(e.minBatteryTempDeciC?.let { formatNumber(it/10.0)+" °C" } ?: "н/д")
            .append(" / ")
            .append(e.peakBatteryTempDeciC?.let { formatNumber(it/10.0)+" °C" } ?: "н/д").append('\n')
        append("Thermal ≥ SEVERE: ").append(e.severeThermalReadings)
            .append(" · энергосбережение: ").append(e.powerSaveReadings)
            .append(" · невалидированная сеть: ").append(e.networkUnvalidatedReadings).append('\n')
        append("Смены транспорта Android: ").append(e.networkTransitions)
            .append(" · промежутков >45 с: ").append(e.gapsOver45s).append('\n')
        if(e.qualityNotes.isNotEmpty()) {
            append("ОГРАНИЧЕНИЯ ДАННЫХ:\n")
            e.qualityNotes.forEach { append("• ").append(it).append('\n') }
        }
        append("Это показания Android, а не FPS игры, серверный ping или доказательство эффекта настроек.")
    }

    fun formatComparison(name:String,hypothesis:String,
                         a:ExperimentArmSummary,b:ExperimentArmSummary):String=buildString {
        append("SESSION EXPERIMENT · ").append(name).append('\n')
        if(hypothesis.isNotBlank())append("Гипотеза пользователя: ").append(hypothesis).append('\n')
        for(s in listOf(a,b)) {
            append('\n').append(s.arm).append(" · ").append(s.label)
                .append(" · сессий: ").append(s.sessions).append('\n')
            append("Разряд: ").append(s.averageDischargePerHour?.let { formatNumber(it)+" п.п./час" } ?: "н/д")
                .append(" (валидных: ").append(s.validRates).append(")\n")
            append("Средний пик температуры БАТАРЕИ: ")
                .append(s.averagePeakBatteryTempC?.let { formatNumber(it)+" °C" } ?: "н/д")
                .append(" (валидных: ").append(s.validPeaks).append(")\n")
            append("Сессий с паузами в замерах: ").append(s.sessionsWithGaps)
                .append(" · с энергосбережением: ").append(s.sessionsWithPowerSave).append('\n')
        }
        if(a.sessions<2 || b.sessions<2)
            append("\nВыборка мала: для предварительного сравнения желательно ≥2 сессий на условие.\n")
        if(a.averageDischargePerHour==null || b.averageDischargePerHour==null)
            append("Недостаточно сопоставимых измерений разряда.\n")
        if(a.sessionsWithGaps+b.sessionsWithGaps>0)
            append("Промежутки без замеров могут искажать сравнение.\n")
        if(a.sessionsWithPowerSave+b.sessionsWithPowerSave>0)
            append("Режим энергосбережения — возможный смешивающий фактор.\n")
        append("Эксперимент наблюдательный. Разные игры, яркость, сети, питание и ")
            .append("охлаждение могут менять результат; причинный эффект и FPS НЕ установлены.")
    }

    fun formatNumber(value:Double):String = String.format(Locale.US,"%.2f",value)
}
