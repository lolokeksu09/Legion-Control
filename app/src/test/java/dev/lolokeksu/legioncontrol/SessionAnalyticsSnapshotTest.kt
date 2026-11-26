package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SessionAnalyticsSnapshotTest {
    private val header=GameSessionHeader("session-a","com.test.game","Game",1000L,601000L,"USER_STOP",false)
    private fun point(t:Long)=GameTelemetryPoint(t,80,320,null,0,false,120f,"wifi",true)
    @Test fun compatibleReportsShareTheExactSelectedSnapshot() {
        val detail=LibraryDetail(header,listOf(point(1000L),point(31000L)),emptyList(),false,false)
        val snapshot=SessionAnalyticsSnapshot.from(detail)
        assertSame(detail,snapshot.detail)
        assertEquals(2,snapshot.summary.pointCount)
        assertEquals(header,snapshot.compareWindow(5).header)
        assertEquals(header,snapshot.baselineCapture().session)
        assertEquals(detail.points,snapshot.baselineCapture().points)
    }
    @Test fun incompleteSessionIsNotAnArchive() {
        val detail=LibraryDetail(header.copy(endedAtMs=null),emptyList(),emptyList(),false,false)
        assertTrue(runCatching { SessionAnalyticsSnapshot.from(detail) }.isFailure)
    }
    @Test fun allMovedFeatureIdsResolveToTheirSection() {
        val expected=mapOf("signals.timeline" to AnalyticsPage.SIGNALS,"signals.csv" to AnalyticsPage.SIGNALS,
            "incidents.filter" to AnalyticsPage.INCIDENTS,"incidents.csv" to AnalyticsPage.INCIDENTS,
            "baselines.week" to AnalyticsPage.BASELINES,"baselines.month" to AnalyticsPage.BASELINES,
            "baselines.csv" to AnalyticsPage.BASELINES,"compare.game" to AnalyticsPage.COMPARE,
            "compare.window" to AnalyticsPage.COMPARE,"compare.run" to AnalyticsPage.COMPARE,
            "compare.graph" to AnalyticsPage.COMPARE,"compare.csv" to AnalyticsPage.COMPARE,
            "library.game" to AnalyticsPage.LIBRARY,"library.period" to AnalyticsPage.LIBRARY,
            "library.state" to AnalyticsPage.LIBRARY,"library.zip" to AnalyticsPage.OVERVIEW)
        expected.forEach { (id,page)->assertEquals(id,page,AnalyticsPage.forFeature(id)) }
        assertNull(AnalyticsPage.forFeature("unknown"))
    }
}
