package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SessionBaselinesTest {
    private val now=1_800_000_000_000L
    private val day=86_400_000L

    private fun capture(
        id:String,daysAgo:Int,tempDeci:Int=340,dischargePoints:Int=1,
        pkg:String="com.example.game",reason:String="USER_STOP",
        gap:Boolean=false,truncated:Boolean=false,powerSaving:Boolean=false
    ):BaselineCapture {
        val start=now-daysAgo*day
        val header=GameSessionHeader(id,pkg,"=Game "+id,start,start+300_000L,reason,false)
        val readings=(0..20).filter { !gap || it !in 9..15 }.map { i ->
            GameTelemetryPoint(start+i*15_000L,
                84-(i*dischargePoints/20),tempDeci,null,1,powerSaving,
                120f,"WIFI",true)
        }
        return BaselineCapture(header,readings,truncated)
    }

    @Test fun noHistoryNeverInventsReferenceOrAlert() {
        val result=SessionBaselines.compare("com.example.game",7,now,emptyList())
        assertNull(result.latest)
        assertNull(result.temperature)
        assertNull(result.discharge)
        assertEquals(0,result.rows.size)
    }

    @Test fun medianFromThreeOlderQualifiedSessionsExcludesLatest() {
        val captures=listOf(capture("oldA",5,330),capture("oldB",4,350),
            capture("oldC",3,340),capture("latest",1,390,3))
        val result=SessionBaselines.compare("com.example.game",7,now,captures)
        assertEquals("latest",result.latest?.session?.id)
        assertEquals(3,result.temperature?.referenceCount)
        assertEquals(340.0,result.temperature!!.median,0.001)
        assertEquals(390.0,result.temperature!!.latest,0.001)
        assertTrue(result.temperature!!.aboveBaseline)
        assertEquals(3,result.discharge?.referenceCount)
        assertTrue(result.discharge!!.aboveBaseline)
        assertEquals(4,result.qualifiedSessions)
    }

    @Test fun twoPreviousSessionsMustNotProduceComparison() {
        val result=SessionBaselines.compare("com.example.game",7,now,
            listOf(capture("old1",3),capture("old2",2),capture("new",1,400,3)))
        assertNull(result.temperature)
        assertNull(result.discharge)
        assertTrue(SessionBaselines.render(result).contains("минимум 3"))
    }

    @Test fun chargingOrInterruptedNeverCountsAsValidBaseline() {
        val bad=capture("bad",4,390,2,reason="INTERRUPTED")
        val rising=capture("charging",3,340,0).let { source ->
            source.copy(points=source.points.mapIndexed { i,p -> p.copy(batteryPercent=60+i/4) })
        }
        val result=SessionBaselines.compare("com.example.game",7,now,
            listOf(bad,rising,capture("old",2),capture("new",1)))
        assertNull(result.discharge)
        assertEquals(2,result.qualifiedSessions)
    }

    @Test fun missingIntervalsAndTruncationInvalidateComparison() {
        val report=SessionBaselines.compare("com.example.game",7,now,
            listOf(capture("oldA",5),capture("gap",4,gap=true),
                capture("truncated",3,truncated=true),capture("latest",1)))
        assertTrue(report.rows.first{it.session.id=="gap"}.exclusions.isNotEmpty())
        assertTrue(report.rows.first{it.session.id=="truncated"}.exclusions.isNotEmpty())
        assertNull(report.temperature)
        assertEquals(2,report.qualifiedSessions)
    }

    @Test fun samePackageAndWindowFiltersOutOtherSessions() {
        val report=SessionBaselines.compare("com.example.game",7,now,
            listOf(capture("other",1,pkg="com.other.game"),capture("old",10),
                capture("latest",1)))
        assertEquals(listOf("latest"),report.rows.map{it.session.id})
        assertEquals(1,report.qualifiedSessions)
    }

    @Test fun withinThresholdIsNeverFlagged() {
        val report=SessionBaselines.compare("com.example.game",30,now,
            listOf(capture("a",5,340),capture("b",4,341),capture("c",3,339),
                capture("new",1,351,1)))
        assertNotNull(report.temperature)
        assertFalse(report.temperature!!.aboveBaseline)
        assertFalse(report.discharge!!.aboveBaseline)
    }

    @Test fun userControlledLabelsAreEscapedInCsvAndNoFakeFps() {
        val report=SessionBaselines.compare("com.example.game",7,now,
            listOf(capture("id",1)))
        val csv=SessionBaselines.csv(report)
        assertTrue(csv.contains("\"'=Game id\""))
        assertTrue(csv.startsWith("session_id,app_label,"))
        assertFalse(SessionBaselines.render(report).contains("FPS:"))
    }

    @Test fun unknownTemperatureIsNotCountedAsZero() {
        val good=capture("prev",2)
        val missing=capture("current",1).let {
            it.copy(points=it.points.map{p->p.copy(batteryTempDeciC=null)})
        }
        val report=SessionBaselines.compare("com.example.game",7,now,listOf(good,missing))
        assertNull(report.latest?.peakTempDeciC)
        assertNull(report.temperature)
    }
}
