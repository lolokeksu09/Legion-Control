package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SessionCompareAnalysisTest {
    private val start=1000000L
    private fun header(id:String="a",pkg:String="com.example.game",end:Long?=start+600000L,
        reason:String?="USER_STOP")=GameSessionHeader(id,pkg,"Game",start,end,reason,false)
    private fun points(temp:Int=400)= (0..40).map { i ->
        GameTelemetryPoint(start+i*15000L,80-i/10,temp+i,null,0,false,120f,"WIFI",true)
    }
    @Test fun comparesOnlySameElapsedWindowAndComputesBMinusA() {
        val a=SessionCompareAnalysis.analyze(header(),points(),5)
        val b=SessionCompareAnalysis.analyze(header("b"),points(420),5)
        val pair=SessionCompareAnalysis.compare(a,b)
        assertTrue(pair.comparable)
        assertEquals(21,a.points)
        assertEquals(1.0,a.coverage,0.001)
        assertEquals(2.0,pair.delta(CompareMeasure.PEAK_TEMP)!!,0.001)
        assertEquals(0.0,pair.delta(CompareMeasure.BATTERY_DROP)!!,0.001)
    }
    @Test fun gapsShortSessionsAndPartialReadSuppressDeltas() {
        val a=SessionCompareAnalysis.analyze(header(),points(),5)
        for(b in listOf(
            SessionCompareAnalysis.analyze(header("b"),points().filterIndexed { i,_->i<3||i>9 },5),
            SessionCompareAnalysis.analyze(header("b",end=start+180000),points(),5),
            SessionCompareAnalysis.analyze(header("b",reason="PROCESS_LOST"),points(),5),
            SessionCompareAnalysis.analyze(header("b"),points(),5,true))) {
            val pair=SessionCompareAnalysis.compare(a,b)
            assertFalse(pair.comparable)
            assertNull(pair.delta(CompareMeasure.PEAK_TEMP))
            assertTrue(b.issues.isNotEmpty())
        }
    }
    @Test fun unknownNetworkAndMissingEndpointAreNotZero() {
        val a=SessionCompareAnalysis.analyze(header(),points(),5)
        val b=SessionCompareAnalysis.analyze(header("b"),points().mapIndexed { i,p->
            p.copy(networkValidated=null,batteryPercent=if(i==0)null else p.batteryPercent) },5)
        val pair=SessionCompareAnalysis.compare(a,b)
        assertTrue(pair.comparable)
        assertNull(b.metrics.getValue(CompareMeasure.NETWORK).value)
        assertNull(pair.delta(CompareMeasure.NETWORK))
        assertNull(pair.delta(CompareMeasure.BATTERY_DROP))
        assertTrue(SessionCompareAnalysis.csv(pair).contains("NETWORK"))
    }
    @Test fun sameIdDifferentGameAndDifferentWindowAreRejected() {
        val a=SessionCompareAnalysis.analyze(header(),points(),5)
        for(b in listOf(a,SessionCompareAnalysis.analyze(header("b","com.other.game"),points(),5),
            SessionCompareAnalysis.analyze(header("b"),points(),10))) {
            assertTrue(runCatching { SessionCompareAnalysis.compare(a,b) }.isFailure)
        }
    }
    @Test fun duplicateAndOutOfWindowSamplesDoNotInflateCoverage() {
        val p=points()
        val a=SessionCompareAnalysis.analyze(header(),p+listOf(p[0],p[0].copy(timeMs=start-1)),5)
        assertEquals(21,a.points)
        assertEquals(2,a.duplicateOrOutside)
        assertEquals(1.0,a.coverage,0.001)
    }
}
