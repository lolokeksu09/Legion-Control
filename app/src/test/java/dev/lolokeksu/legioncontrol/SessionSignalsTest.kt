package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SessionSignalsTest {
    private val s=GameSessionHeader("id","com.example.game","Game",1000,120000,"USER_STOP",false)
    private fun p(t:Long,temp:Int?=320,thermal:Int?=0,saving:Boolean?=false,
        valid:Boolean?=true,network:String="WIFI",hz:Float?=120f,charge:Int?=80)=
        GameTelemetryPoint(t,charge,temp,null,thermal,saving,hz,network,valid)

    @Test fun emptyNeverFabricatesReadings() {
        val r=SessionSignals.analyze(s,emptyList(),emptyList())
        assertEquals(0,r.signals.size);assertEquals(0,r.pointCount)
        assertNull(r.peakBatteryTempDeciC);assertEquals(0L,r.measuredContinuityMs)
    }
    @Test fun realTransitionsAndRecoveries() {
        val r=SessionSignals.analyze(s,listOf(p(1000),
            p(16000,455,3,true,false,"NONE",60f,79),
            p(31000,460,3,true,false,"NONE",60f,79),
            p(46000,350,1,false,true,"WIFI",120f,78)),emptyList())
        for(k in listOf("THERMAL_SEVERE","THERMAL_RECOVERED","BATTERY_TEMP_HIGH",
            "BATTERY_TEMP_RECOVERED","POWER_SAVING","POWER_SAVING_OFF",
            "NETWORK_UNVALIDATED","NETWORK_VALIDATED","NETWORK_TRANSPORT","DISPLAY_REFRESH"))
            assertTrue(k,r.signals.any{it.kind==k})
        assertEquals(45000L,r.measuredContinuityMs)
        assertEquals(460,r.peakBatteryTempDeciC)
        assertEquals(80,r.firstBattery);assertEquals(78,r.lastBattery)
    }
    @Test fun missingAndGappedValuesDoNotInventRecovery() {
        val r=SessionSignals.analyze(s,listOf(p(1000,450,4,true,false),
            p(3000,null,null,null,null),p(100000,320,0,false,true)),emptyList())
        assertEquals(1,r.gapsOver45s);assertEquals(2000L,r.measuredContinuityMs)
        assertEquals(1,r.unknownTemperatureCount);assertEquals(1,r.unknownThermalCount)
        assertEquals(1,r.unknownNetworkCount)
        assertFalse(r.signals.any{it.kind.endsWith("_RECOVERED")||it.kind=="POWER_SAVING_OFF"})
        assertTrue(r.signals.any{it.kind=="SAMPLE_GAP"})
    }
    @Test fun tiedTimestampsAreNotCountedTwice() {
        val r=SessionSignals.analyze(s,listOf(p(1000),p(1000),p(500)),emptyList())
        assertEquals(500L,r.measuredContinuityMs)
    }
    @Test fun persistedMarksSortedAndCsvHardened() {
        val r=SessionSignals.analyze(s,listOf(p(4000),p(19000)),
            listOf(GameSessionEvent(8000,"USER_MARK","=HYPERLINK(\"bad\")"),
                GameSessionEvent(7000,"USER_MARK","Manual test")))
        assertEquals(listOf(7000L,8000L),r.signals.filter{it.kind=="USER_MARK"}.map{it.timeMs})
        assertTrue(SessionSignals.csv(r).contains("\"'=HYPERLINK(\"\"bad\"\")\""))
        assertTrue(SessionSignals.csv(r).contains("Manual test"))
    }
    @Test fun normalizeMarkerBoundsControlCharacters() {
        assertEquals("test note",SessionSignals.normalizeMarker("\n  test \t note  "))
        assertNull(SessionSignals.normalizeMarker("\n \u0000"))
        assertEquals(120,SessionSignals.normalizeMarker("x".repeat(400))!!.length)
    }
    @Test fun boundedSignalHistoryAdmitsTruncation() {
        val e=(0 until 730).map{GameSessionEvent(it.toLong()+10,"USER_MARK","m"+it)}
        val r=SessionSignals.analyze(s,emptyList(),e,true,true)
        assertEquals(600,r.signals.size)
        assertEquals(130,r.omittedSignals)
        assertTrue(SessionSignals.render(r).contains("ОГРАНИЧЕНИЕ"))
        assertEquals(601,SessionSignals.csv(r).lines().count{it.isNotEmpty()})
    }
}
