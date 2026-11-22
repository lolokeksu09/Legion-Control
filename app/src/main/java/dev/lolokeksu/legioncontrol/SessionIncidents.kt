package dev.lolokeksu.legioncontrol

import java.util.Locale

/**
 * Offline episode reconstruction, not game FPS, ping or causal attribution.
 *
 * Duration is confirmed ONLY between adjacent positive public-API observations.
 * Unknown readings, gaps and the final observation never extend a duration.
 */
data class SessionIncidentMark(val timeMs: Long, val note: String)
data class SessionIncident(
    val kind: SessionIncidents.Kind,
    val startMs: Long,
    val lastPositiveMs: Long,
    val confirmedMs: Long,
    val positiveSamples: Int,
    val end: SessionIncidents.End,
    val marks: List<SessionIncidentMark> = emptyList()
)
data class SessionIncidentsReport(
    val session: GameSessionHeader,
    val sampleCount: Int,
    val outOfWindowSamples: Int,
    val gapsOver45s: Int,
    val measuredContinuityMs: Long,
    val eventCount: Int,
    val sampleLimitHit: Boolean,
    val eventLimitHit: Boolean,
    val omittedIncidents: Int,
    val incidents: List<SessionIncident>
)

object SessionIncidents {
    const val MAX_SAMPLES = 10_000
    const val MAX_EVENTS = 2_000
    const val MAX_INCIDENTS = 400
    const val GAP_MS = 45_000L
    private const val MARK_WINDOW_MS = 60_000L

    enum class Kind(val label: String) {
        THERMAL("Thermal Status ≥ SEVERE"),
        BATTERY("Батарея ≥45 °C"),
        POWER("Энергосбережение Android"),
        NETWORK("Сеть не подтверждена Android"),
        DISPLAY("Снижение дисплея ниже 90 Гц")
    }
    enum class End(val label: String) {
        CLEARED("следующее показание не подтверждает состояние"),
        UNKNOWN("следующее показание неизвестно"),
        GAP("пропуск измерений >45 с"),
        OBSERVATION_END("конец доступных измерений")
    }

    /** Track state separately; a cleared state is NOT counted as positive time. */
    private class Tracker(private val kind: Kind) {
        private var start: Long? = null
        private var last: Long = 0L
        private var confirmed = 0L
        private var positives = 0

        fun step(at: Long, value: Boolean?, adjacent: Boolean, out: MutableList<SessionIncident>) {
            if (!adjacent) finish(End.GAP, out)
            when (value) {
                true -> if (start == null) {
                    start = at
                    last = at
                    confirmed = 0L
                    positives = 1
                } else {
                    // Adjacent positive readings alone contribute measured duration.
                    if (adjacent) confirmed += at - last
                    last = at
                    positives++
                }
                false -> finish(End.CLEARED, out)
                null -> finish(End.UNKNOWN, out)
            }
        }

        fun finish(reason: End, out: MutableList<SessionIncident>) {
            val first = start ?: return
            out.add(SessionIncident(kind, first, last, confirmed, positives, reason))
            start = null
            last = 0L
            confirmed = 0L
            positives = 0
        }
    }

    fun analyze(
        header: GameSessionHeader,
        recorded: List<GameTelemetryPoint>,
        events: List<GameSessionEvent>,
        sampleLimitHit: Boolean = false,
        eventLimitHit: Boolean = false
    ): SessionIncidentsReport {
        // Bound even if a caller passes a larger in-memory collection.
        val input = recorded.take(MAX_SAMPLES)
        val end = header.endedAtMs ?: Long.MAX_VALUE
        val outOfWindow = input.count { it.timeMs < header.startedAtMs || it.timeMs > end }
        val usable = input.asSequence()
            .filter { it.timeMs >= header.startedAtMs && it.timeMs <= end }
            .sortedBy { it.timeMs }
            .distinctBy { it.timeMs }
            .toList()
        val marks = events.take(MAX_EVENTS).asSequence()
            .filter { it.kind == "USER_MARK" && it.timeMs in header.startedAtMs..end }
            .mapNotNull {
                SessionSignals.normalizeMarker(it.details)?.let { note ->
                    SessionIncidentMark(it.timeMs, note)
                }
            }
            .sortedBy { it.timeMs }
            .toList()
        val trackers = Kind.entries.associateWith(::Tracker)
        val raw = ArrayList<SessionIncident>()
        var previous: GameTelemetryPoint? = null
        var gaps = 0
        var continuity = 0L
        var sawHighRefresh = false

        for (point in usable) {
            val delta = previous?.let { point.timeMs - it.timeMs }
            val adjacent = delta != null && delta in 1..GAP_MS
            if (delta != null && delta > GAP_MS) gaps++
            if (adjacent) continuity += delta!!
            val refresh = point.refreshHz?.takeIf { it.isFinite() && it in 1f..500f }
            if (refresh != null && refresh >= 90f) sawHighRefresh = true
            val values = mapOf(
                Kind.THERMAL to point.thermalStatus?.takeIf { it in 0..6 }?.let { it >= 3 },
                Kind.BATTERY to point.batteryTempDeciC?.takeIf { it in -400..1200 }?.let { it >= 450 },
                Kind.POWER to point.powerSaving,
                Kind.NETWORK to point.networkValidated?.let { !it },
                // Never call a permanently configured 60 Hz screen a "drop".
                Kind.DISPLAY to refresh?.let { if (!sawHighRefresh) null else it < 90f }
            )
            for (kind in Kind.entries) trackers.getValue(kind).step(
                point.timeMs, values[kind], adjacent, raw
            )
            previous = point
        }
        for (kind in Kind.entries) trackers.getValue(kind).finish(End.OBSERVATION_END, raw)
        val chronological = raw.sortedWith(compareBy<SessionIncident> { it.startMs }
            .thenBy { it.kind.ordinal })
        val retained = chronological.takeLast(MAX_INCIDENTS).map { incident ->
            val begin = (incident.startMs - MARK_WINDOW_MS).coerceAtLeast(header.startedAtMs)
            val finish = if (incident.lastPositiveMs > Long.MAX_VALUE - MARK_WINDOW_MS)
                Long.MAX_VALUE else incident.lastPositiveMs + MARK_WINDOW_MS
            incident.copy(marks = marks.asSequence()
                .filter { it.timeMs in begin..finish }.take(3).toList())
        }
        return SessionIncidentsReport(
            header, usable.size, outOfWindow, gaps, continuity,
            events.take(MAX_EVENTS).count { it.timeMs in header.startedAtMs..end },
            sampleLimitHit || recorded.size > MAX_SAMPLES,
            eventLimitHit || events.size > MAX_EVENTS,
            chronological.size - retained.size, retained
        )
    }

    fun render(r: SessionIncidentsReport, kind: Kind? = null): String = buildString {
        val selected = r.incidents.filter { kind == null || it.kind == kind }
        append("SESSION INCIDENTS · ").append(r.session.appLabel).append('\n')
        append("Пакет: ").append(r.session.packageName).append('\n')
        append("Фильтр: ").append(kind?.label ?: "Все категории").append('\n')
        append("Измерений: ").append(r.sampleCount)
            .append(" · вне интервала: ").append(r.outOfWindowSamples)
            .append(" · событий SQLite: ").append(r.eventCount).append('\n')
        append("Подтверждённая связность: ").append(r.measuredContinuityMs / 1000)
            .append(" с · пропуски >45 с: ").append(r.gapsOver45s).append('\n')
        append("Эпизодов: ").append(selected.size)
            .append(" · всего категорий: ").append(Kind.entries.size).append('\n')
        for (category in Kind.entries.filter { kind == null || it == kind }) {
            val rows = selected.filter { it.kind == category }
            append(category.label).append(": ").append(rows.size)
                .append(" эпизодов, подтверждено ")
                .append(rows.sumOf { it.confirmedMs } / 1000).append(" с\n")
        }
        if (r.sampleLimitHit || r.eventLimitHit || r.omittedIncidents > 0) {
            append("ОГРАНИЧЕНИЕ: ")
            if (r.sampleLimitHit) append("достигнут лимит 10000 замеров; ")
            if (r.eventLimitHit) append("достигнут лимит 2000 событий; ")
            if (r.omittedIncidents > 0) append("скрыто старых эпизодов: ")
                .append(r.omittedIncidents)
            append('\n')
        }
        append("ОТРЕЗКИ НАБЛЮДЕНИЙ (последние 40)\n")
        if (selected.isEmpty()) append("В сохранённых данных эпизодов выбранного типа нет.\n")
        for (i in selected.takeLast(40)) {
            val offset = ((i.startMs - r.session.startedAtMs) / 1000).coerceAtLeast(0)
            append("+").append(String.format(Locale.US, "%02d:%02d", offset / 60, offset % 60))
                .append(" · ").append(i.kind.label).append('\n')
            append("  Подтверждено ").append(i.confirmedMs / 1000).append(" с, ")
                .append(i.positiveSamples).append(" показаний · ").append(i.end.label).append('\n')
            for (mark in i.marks) append("  МЕТКА: ")
                .append(mark.note.replace("\n", " ")).append('\n')
        }
        append("Оценка длительности — нижняя граница между подтверждающими замерами, ")
            .append("а не непрерывный мониторинг. Совпадение с меткой не устанавливает причину. ")
            .append("Гц дисплея — не FPS игры; сетевой статус — не ping.")
    }

    fun csv(r: SessionIncidentsReport, kind: Kind? = null): String = buildString {
        append("session_id,package_name,incident_kind,started_at_ms,last_positive_ms,")
            .append("confirmed_ms,positive_samples,end_reason,nearby_manual_marks\n")
        for (i in r.incidents.filter { kind == null || it.kind == kind }) {
            append(cell(r.session.id)).append(',').append(cell(r.session.packageName))
                .append(',').append(cell(i.kind.name)).append(',')
                .append(i.startMs).append(',').append(i.lastPositiveMs).append(',')
                .append(i.confirmedMs).append(',').append(i.positiveSamples)
                .append(',').append(cell(i.end.name)).append(',')
                .append(cell(i.marks.joinToString(" | ") { it.note })).append('\n')
        }
    }

    private fun cell(raw: String): String {
        val escaped = if (raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r'))
            "'" + raw else raw
        return "\"" + escaped.replace("\"", "\"\"") + "\""
    }
}
