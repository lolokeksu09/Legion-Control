package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SessionIncidentsTest {
    private val h = GameSessionHeader("id","com.example.game","Game",1000,900_000,"USER_STOP",false)
    private fun p(t: Long, thermal: Int? = 0, temp: Int? = 330,
        power: Boolean? = false, net: Boolean? = true, hz: Float? = 120f) =
        GameTelemetryPoint(t,75,temp,null,thermal,power,hz,"WIFI",net)
    private fun report(points: List<GameTelemetryPoint>, marks: List<GameSessionEvent> = emptyList()) =
        SessionIncidents.analyze(h,points,marks)
    private fun of(r: SessionIncidentsReport,k: SessionIncidents.Kind) =
        r.incidents.filter { it.kind == k }

    @Test fun emptyIsUnknownNotAllClear() {
        val r=report(emptyList())
        assertTrue(r.incidents.isEmpty())
        assertEquals(0L,r.measuredContinuityMs)
        assertTrue(SessionIncidents.render(r).contains("нет"))
    }
    @Test fun confirmedIntervalsOnlyUseTwoPositiveEndpoints() {
        val r=report(listOf(p(1000,thermal=3),p(16000,thermal=3),p(31000,thermal=1)))
        val x=of(r,SessionIncidents.Kind.THERMAL).single()
        assertEquals(15000L,x.confirmedMs)
        assertEquals(2,x.positiveSamples)
        assertEquals(16000L,x.lastPositiveMs)
        assertEquals(SessionIncidents.End.CLEARED,x.end)
    }
    @Test fun singlePositiveHasZeroConfirmedDuration() {
        val x=of(report(listOf(p(1000,temp=455),p(16000,temp=330))),
            SessionIncidents.Kind.BATTERY).single()
        assertEquals(0L,x.confirmedMs)
        assertEquals(1,x.positiveSamples)
    }
    @Test fun gapSplitsEpisodesWithoutFillingMissingMinutes() {
        val r=report(listOf(p(1000,power=true),p(16000,power=true),
            p(116000,power=true),p(131000,power=true)))
        val x=of(r,SessionIncidents.Kind.POWER)
        assertEquals(2,x.size);assertEquals(1,r.gapsOver45s)
        assertEquals(listOf(15000L,15000L),x.map{it.confirmedMs})
        assertEquals(SessionIncidents.End.GAP,x.first().end)
    }
    @Test fun missingValueCutsWithoutInventingRecovery() {
        val r=report(listOf(p(1000,net=false),p(16000,net=false),
            p(31000,net=null),p(46000,net=true)))
        val x=of(r,SessionIncidents.Kind.NETWORK).single()
        assertEquals(15000L,x.confirmedMs)
        assertEquals(SessionIncidents.End.UNKNOWN,x.end)
    }
    @Test fun endOfRecordingDoesNotExtendMeasuredDuration() {
        val r=report(listOf(p(1000,temp=480),p(16000,temp=485)))
        val x=of(r,SessionIncidents.Kind.BATTERY).single()
        assertEquals(15000L,x.confirmedMs)
        assertEquals(SessionIncidents.End.OBSERVATION_END,x.end)
        assertNotEquals(900000L,x.lastPositiveMs)
    }
    @Test fun aFixed60HzScreenIsNotClassifiedAsDrop() {
        assertTrue(of(report(listOf(p(1000,hz=60f),p(16000,hz=60f))),
            SessionIncidents.Kind.DISPLAY).isEmpty())
    }
    @Test fun documentedDisplayDropOnlyAfterObservedFastRefresh() {
        val r=report(listOf(p(1000,hz=120f),p(16000,hz=60f),
            p(31000,hz=60f),p(46000,hz=120f)))
        val x=of(r,SessionIncidents.Kind.DISPLAY).single()
        assertEquals(16000L,x.startMs)
        assertEquals(15000L,x.confirmedMs)
        assertEquals(SessionIncidents.End.CLEARED,x.end)
    }
    @Test fun invalidFieldsNeverProduceAnIncident() {
        val r=report(listOf(p(1000,thermal=100,temp=2000,hz=Float.NaN),
            p(16000,thermal=-5,temp=-500,hz=Float.POSITIVE_INFINITY)))
        assertTrue(r.incidents.isEmpty())
    }
    @Test fun simultaneousConditionsRemainIndependent() {
        val r=report(listOf(p(1000,thermal=3,power=true,net=false,temp=460),
            p(16000,thermal=3,power=true,net=false,temp=460)))
        assertEquals(4,r.incidents.size)
        assertEquals(15000L,of(r,SessionIncidents.Kind.THERMAL).single().confirmedMs)
        assertEquals(15000L,of(r,SessionIncidents.Kind.NETWORK).single().confirmedMs)
    }
    @Test fun outOfSessionAndDuplicateTimestampsNeverInflateDurations() {
        val r=report(listOf(p(400,thermal=3),p(1000,thermal=3),
            p(1000,thermal=3),p(16000,thermal=3),p(910000,thermal=3)))
        assertEquals(2,r.sampleCount)
        assertEquals(2,r.outOfWindowSamples)
        assertEquals(15000L,of(r,SessionIncidents.Kind.THERMAL).single().confirmedMs)
    }
    @Test fun orderIsChronologicalIndependentOfRawRows() {
        val r=report(listOf(p(31000,power=false),p(16000,power=true),p(1000,power=true)))
        val x=of(r,SessionIncidents.Kind.POWER).single()
        assertEquals(1000L,x.startMs)
        assertEquals(15000L,x.confirmedMs)
    }
    @Test fun userMarksAreProximityOnlyAndOutOfSessionExcluded() {
        val r=report(listOf(p(1000,thermal=3),p(16000,thermal=3)),
            listOf(GameSessionEvent(17000,"USER_MARK","=HYPERLINK(\"x\")"),
                GameSessionEvent(19000,"SERVICE","not user text"),
                GameSessionEvent(999999,"USER_MARK","outside"),
                GameSessionEvent(230000,"USER_MARK","too far")))
        val x=of(r,SessionIncidents.Kind.THERMAL).single()
        assertEquals(1,x.marks.size)
        assertTrue(SessionIncidents.csv(r).contains("\"'=HYPERLINK(\"\"x\"\")\""))
        assertFalse(SessionIncidents.csv(r).contains("outside"))
        assertFalse(SessionIncidents.csv(r).contains("not user text"))
    }
    @Test fun filtersAffectReportAndCsvTogether() {
        val r=report(listOf(p(1000,power=true,net=false),p(16000,power=true,net=false)))
        val t=SessionIncidents.render(r,SessionIncidents.Kind.POWER)
        val c=SessionIncidents.csv(r,SessionIncidents.Kind.POWER)
        assertTrue(t.contains("Фильтр: Энергосбережение Android"))
        assertFalse(c.contains(",\"NETWORK\","))
        assertTrue(c.contains(",\"POWER\","))
    }
    @Test fun incidentCapAndTruncationHonest() {
        val points=(0 until 1002).map { i ->
            p(1000L+i*500L, power=i%2==0)
        }
        val r=SessionIncidents.analyze(h,points,emptyList(),true,true)
        assertEquals(400,r.incidents.size)
        assertEquals(101,r.omittedIncidents)
        assertTrue(SessionIncidents.render(r).contains("ОГРАНИЧЕНИЕ"))
    }
}
