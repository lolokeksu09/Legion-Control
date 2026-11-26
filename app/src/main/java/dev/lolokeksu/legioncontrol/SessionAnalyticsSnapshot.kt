package dev.lolokeksu.legioncontrol

/** One bounded, completed session supplies all compatible offline reports. */
data class SessionAnalyticsSnapshot(
    val detail: LibraryDetail,
    val summary: GameSessionSummary,
    val signals: SessionSignalsReport,
    val incidents: SessionIncidentsReport
) {
    companion object {
        fun from(detail: LibraryDetail): SessionAnalyticsSnapshot {
            require(detail.header.endedAtMs != null)
            return SessionAnalyticsSnapshot(
                detail, GameSessionAnalysis.summarize(detail.header, detail.points),
                SessionSignals.analyze(detail.header, detail.points, detail.events,
                    detail.sampleLimited, detail.eventLimited),
                SessionIncidents.analyze(detail.header, detail.points, detail.events,
                    detail.sampleLimited, detail.eventLimited)
            )
        }
    }

    fun compareWindow(minutes: Int): CompareWindow = SessionCompareAnalysis.analyze(
        detail.header, detail.points, minutes,
        detail.sampleLimited || detail.points.size > SessionCompareAnalysis.MAX_POINTS
    )

    fun baselineCapture(): BaselineCapture = BaselineCapture(
        detail.header, detail.points.take(SessionBaselines.MAX_POINTS_PER_SESSION),
        detail.sampleLimited || detail.points.size >= SessionBaselines.MAX_POINTS_PER_SESSION
    )
}

internal enum class AnalyticsPage(val label: String) {
    LIBRARY("Библиотека"), OVERVIEW("Обзор"), SIGNALS("Хронология"),
    INCIDENTS("Проблемы"), COMPARE("Сравнение"), BASELINES("Базовые показатели");

    companion object {
        fun forFeature(id: String?): AnalyticsPage? = when (
            FeatureFinder.entries.firstOrNull { it.id == id }?.module
        ) {
            FeatureModule.LIBRARY -> if(id=="library.zip") OVERVIEW else LIBRARY
            FeatureModule.SIGNALS -> SIGNALS
            FeatureModule.INCIDENTS -> INCIDENTS
            FeatureModule.COMPARE -> COMPARE
            FeatureModule.BASELINES -> BASELINES
            else -> null
        }
    }
}

internal data class AnalyticsCsv(val filename: String, val content: String)
