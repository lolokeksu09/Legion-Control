package dev.lolokeksu.legioncontrol

import java.util.Locale

/** An existing user-started session and its local Android measurements, never a game benchmark. */
data class BaselineCapture(
    val session: GameSessionHeader,
    val points: List<GameTelemetryPoint>,
    val truncated: Boolean = false
)

data class BaselineSession(
    val session: GameSessionHeader,
    val sampleCount: Int,
    val coverage: Double,
    val peakTempDeciC: Int?,
    val dischargeRate: Double?,
    val exclusions: List<String>
) {
    val comparable: Boolean get() = exclusions.isEmpty()
}

data class BaselineMetric(
    val latest: Double,
    val median: Double,
    val delta: Double,
    val referenceCount: Int,
    val aboveBaseline: Boolean
)

data class SessionBaselinesResult(
    val packageName: String,
    val days: Int,
    val rows: List<BaselineSession>,
    val qualifiedSessions: Int,
    val latest: BaselineSession?,
    val temperature: BaselineMetric?,
    val discharge: BaselineMetric?,
    val inputLimited: Boolean
)

/**
 * Historical *device conditions*, using no private APIs or inferred game FPS/ping.
 * Always compare the newest session to >=3 distinct older, comparable sessions.
 * Missing samples, charging, gaps, power saving and truncated history exclude a session.
 */
object SessionBaselines {
    const val MAX_SESSIONS = 24
    const val MAX_POINTS_PER_SESSION = 2000
    const val MIN_REFERENCES = 3
    private const val DAY_MS = 86_400_000L
    private const val MIN_SESSION_MS = 120_000L
    private const val MAX_GAP_MS = 45_000L
    private const val MIN_COVERAGE = 0.70

    fun compare(
        packageName: String, days: Int, nowMs: Long,
        captures: List<BaselineCapture>
    ): SessionBaselinesResult {
        require(days == 7 || days == 30)
        require(nowMs > 0)
        val since = (nowMs - days * DAY_MS).coerceAtLeast(0L)
        val matching = captures.asSequence()
            .filter { it.session.packageName == packageName }
            .filter { it.session.startedAtMs in since..nowMs }
            .sortedWith(compareByDescending<BaselineCapture> { it.session.startedAtMs }
                .thenByDescending { it.session.id })
            .toList()
        val rows = matching.take(MAX_SESSIONS).map(::evidence)
        val newest = rows.firstOrNull()
        val references = rows.drop(1).filter { it.comparable }
        val temp = metric(newest, references,
            current = { it.peakTempDeciC?.toDouble() },
            above = { current, median -> current >= median + 30.0 })
        val discharge = metric(newest, references,
            current = { it.dischargeRate },
            above = { current, median -> current >= median + 1.5 &&
                current >= median * 1.25 })
        return SessionBaselinesResult(
            packageName, days, rows, rows.count { it.comparable },
            newest, temp, discharge, matching.size > MAX_SESSIONS
        )
    }

    private fun evidence(capture: BaselineCapture): BaselineSession {
        val h = capture.session
        val sorted = capture.points.asSequence()
            .filter { it.timeMs >= h.startedAtMs &&
                (h.endedAtMs == null || it.timeMs <= h.endedAtMs) }
            .sortedBy { it.timeMs }
            .distinctBy { it.timeMs }
            .toList()
        val duration = h.endedAtMs?.let { it - h.startedAtMs } ?: 0L
        val observedMs = sorted.zipWithNext().sumOf { (a,b) ->
            val delta = b.timeMs - a.timeMs
            if (delta in 1..MAX_GAP_MS) delta else 0L
        }
        val coverage = if(duration > 0) (observedMs.toDouble() / duration)
            .coerceIn(0.0,1.0) else 0.0
        val reading = ExperimentAnalytics.evidence(h, sorted)
        val issues = buildList {
            if (h.endedAtMs == null || h.endReason != "USER_STOP")
                add("запись не завершена пользователем")
            if (duration < MIN_SESSION_MS) add("длительность менее 2 минут")
            if (sorted.size < 6) add("менее 6 измерений")
            if (capture.truncated) add("данные достигли лимита чтения")
            if (sorted.zipWithNext().any { (a,b) -> b.timeMs-a.timeMs > MAX_GAP_MS })
                add("разрыв измерений более 45 с")
            if (coverage < MIN_COVERAGE) add("покрытие менее 70%")
            if (sorted.firstOrNull()?.let { it.timeMs - h.startedAtMs > MAX_GAP_MS } != false)
                add("начало сессии не покрыто")
            if (h.endedAtMs != null && sorted.lastOrNull()?.let {
                h.endedAtMs - it.timeMs > MAX_GAP_MS
            } != false) add("конец сессии не покрыт")
            if (sorted.any { it.powerSaving == true }) add("энергосбережение включалось")
            if (reading.dischargePercentPerHour == null)
                add("нет допустимой оценки разряда (зарядка/пропуски/короткое окно)")
            if (reading.peakBatteryTempDeciC == null)
                add("нет измерений температуры батареи")
        }
        return BaselineSession(h,sorted.size,coverage,
            reading.peakBatteryTempDeciC, reading.dischargePercentPerHour,issues)
    }

    private fun metric(
        newest: BaselineSession?,
        references: List<BaselineSession>,
        current: (BaselineSession)->Double?,
        above: (Double, Double)->Boolean
    ): BaselineMetric? {
        if (newest?.comparable != true) return null
        val latest = current(newest) ?: return null
        val historical = references.mapNotNull(current).sorted()
        if (historical.size < MIN_REFERENCES) return null
        val median = if(historical.size % 2 == 1) historical[historical.size/2]
            else (historical[historical.size/2-1]+historical[historical.size/2])/2.0
        return BaselineMetric(latest,median,latest-median,historical.size,
            above(latest,median))
    }

    fun render(r: SessionBaselinesResult): String = buildString {
        append("SESSION BASELINES · ").append(r.packageName).append('\n')
        append("Окно истории: ").append(r.days).append(" дней; записей: ")
            .append(r.rows.size).append(" · сопоставимых: ").append(r.qualifiedSessions)
            .append('\n')
        if(r.inputLimited)append("ОГРАНИЧЕНИЕ: анализируются только последние 24 записи.\n")
        val recent = r.latest
        if(recent == null) {
            append("Записей этой игры за выбранный период нет.\n")
        } else {
            append("Последняя: ").append(recent.session.appLabel).append(" · ")
                .append(recent.session.startedAtMs).append('\n')
            append("Покрытие измерениями: ")
                .append(format(recent.coverage*100.0)).append("% · точек: ")
                .append(recent.sampleCount).append('\n')
            if(recent.exclusions.isNotEmpty()) {
                append("Последняя запись НЕ сопоставима:\n")
                recent.exclusions.forEach { append("• ").append(it).append('\n') }
            }
        }
        fun line(title:String, unit:String, m:BaselineMetric?, scale:Double=1.0) {
            append(title).append(": ")
            if(m==null) {
                append("н/д — нужно минимум 3 предыдущие сопоставимые сессии и ")
                    .append("качественная последняя запись.\n")
                return
            }
            append(format(m.latest/scale)).append(" / медиана ")
                .append(format(m.median/scale)).append(unit).append(" · Δ ")
                .append(if(m.delta>0) "+" else "").append(format(m.delta/scale))
                .append(unit).append(" · предыдущих: ").append(m.referenceCount).append('\n')
            if(m.aboveBaseline)append("НАБЛЮДЕНИЕ: значение выше исторической медианы")
                .append(" по заданному порогу; причина не установлена.\n")
        }
        line("Пик температуры БАТАРЕИ"," °C",r.temperature,scale=10.0)
        line("Разряд"," п.п./час",r.discharge)
        append("Разные яркость, сеть, зарядка, охлаждение и активность телефона ")
            .append("делают записи несопоставимыми даже при полном покрытии. ")
            .append("Это локальные показания Android, не FPS или ping игры и ")
            .append("не доказательство ухудшения производительности.")
    }

    fun csv(r: SessionBaselinesResult): String = buildString {
        append("session_id,app_label,package_name,started_at_ms,ended_at_ms,")
        append("end_reason,sample_count,coverage_percent,discharge_pp_per_hour,")
        append("peak_battery_temp_deci_c,comparable,exclusions\n")
        for (row in r.rows) {
            append(cell(row.session.id)).append(',')
                .append(cell(row.session.appLabel)).append(',')
                .append(cell(row.session.packageName)).append(',')
                .append(row.session.startedAtMs).append(',')
                .append(row.session.endedAtMs?.toString() ?: "").append(',')
                .append(cell(row.session.endReason ?: "")).append(',')
                .append(row.sampleCount).append(',')
                .append(format(row.coverage*100.0)).append(',')
                .append(row.dischargeRate?.let(::format) ?: "").append(',')
                .append(row.peakTempDeciC?.toString() ?: "").append(',')
                .append(if (row.comparable) "1" else "0").append(',')
                .append(cell(row.exclusions.joinToString("; "))).append('\n')
        }
    }

    private fun cell(raw:String):String {
        val safe=if(raw.firstOrNull() in listOf('=','+','-','@','\t','\r'))
            "'"+raw else raw
        return "\""+safe.replace("\"","\"\"")+"\""
    }
    private fun format(value:Double):String = String.format(Locale.US,"%.2f",value)
}
