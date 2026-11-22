package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ExperimentAnalyticsTest {
    private val baseline = GameSessionHeader(
        "s1","com.example.game","Example Game",1000L,901000L,"USER_STOP",false
    )
    private fun point(time:Long,percent:Int?=null,temp:Int?=null,
                      thermal:Int?=null,saving:Boolean?=null,
                      type:String="WIFI",valid:Boolean?=true) =
        GameTelemetryPoint(time,percent,temp,null,thermal,saving,120f,type,valid)

    @Test fun completedDischargeUsesSampleTimesNotWallClockOrCapacity() {
        val samples=(0..60).map { i ->
            point(1000L+i*15_000L,80-(5*i/60),380+i/3)
        }
        val e=ExperimentAnalytics.evidence(baseline,samples)
        assertEquals(61,e.samples)
        assertEquals(900000L/60000.0,e.observedMinutes!!,0.001)
        assertEquals(20.0,e.dischargePercentPerHour!!,0.001)
        assertEquals(400,e.peakBatteryTempDeciC)
        assertTrue(e.qualityNotes.isEmpty())
    }

    @Test fun interruptedSessionDoesNotInventDischargeRate() {
        val h=baseline.copy(endedAtMs=null,endReason=null)
        val e=ExperimentAnalytics.evidence(h,listOf(point(1000,80),point(301000,79),point(901000,78)))
        assertNull(e.sessionMinutes)
        assertNull(e.dischargePercentPerHour)
        assertTrue(e.qualityNotes.any { it.contains("не завершилась") })
    }

    @Test fun chargingOrUnstableBatteryIsNotReportedAsDischargeRate() {
        val samples=listOf(point(1000,60),point(301000,63),point(901000,62))
        val e=ExperimentAnalytics.evidence(baseline,samples)
        assertNull(e.dischargePercentPerHour)
        assertTrue(e.qualityNotes.any { it.contains("повышался") })
    }

    @Test fun nullsShortWindowsAndOutOfSessionSamplesAreNotInvented() {
        val e=ExperimentAnalytics.evidence(baseline,listOf(
            point(0,10,600),point(1000,null,null),point(2000,80,null),point(3000,80,null)
        ))
        assertEquals(3,e.samples)
        assertNull(e.dischargePercentPerHour)
        assertNull(e.peakBatteryTempDeciC)
        assertNull(e.minBatteryTempDeciC)
        assertTrue(ExperimentAnalytics.formatEvidence(e).contains("н/д"))
    }

    @Test fun gapsAreDetectedWithoutInventingActualGameFps() {
        val e=ExperimentAnalytics.evidence(baseline,listOf(
            point(1000,80,330,0,false,"WIFI",true),
            point(2000,80,350,3,true,"UNKNOWN",null),
            point(181000,79,380,4,true,"CELLULAR",false),
            point(901000,78,390,0,false,"CELLULAR",false)
        ))
        assertEquals(2,e.gapsOver45s)
        assertEquals(1,e.networkTransitions)
        assertEquals(2,e.severeThermalReadings)
        assertEquals(2,e.networkUnvalidatedReadings)
        assertEquals(2,e.powerSaveReadings)
        assertFalse(ExperimentAnalytics.formatEvidence(e).contains("120 FPS"))
    }

    @Test fun groupMeansOnlyUseAvailableReadingsAndDoNotRankConditions() {
        val e1=ExperimentAnalytics.evidence(baseline,listOf(
            point(1000,80,320),point(301000,78,360),point(901000,75,400)))
        val e2=ExperimentAnalytics.evidence(baseline.copy(id="s2",endReason="INTERRUPTED"),
            listOf(point(1000,null,310),point(301000,null,330)))
        val e3=ExperimentAnalytics.evidence(baseline.copy(id="s3"),listOf(
            point(1000,70,350),point(301000,68,400),point(901000,60,420)))
        val trials=listOf(
            ExperimentTrial("A",baseline,"","",e1),
            ExperimentTrial("A",baseline.copy(id="s2"),"","",e2),
            ExperimentTrial("B",baseline.copy(id="s3"),"","",e3)
        )
        val a=ExperimentAnalytics.armSummary("A","Wi-Fi",trials)
        val b=ExperimentAnalytics.armSummary("B","Cellular",trials)
        assertEquals(2,a.sessions)
        assertEquals(1,a.validRates)
        assertEquals(20.0,a.averageDischargePerHour!!,0.001)
        assertEquals(36.5,a.averagePeakBatteryTempC!!,0.001)
        assertEquals(1,b.sessions)
        assertEquals(40.0,b.averageDischargePerHour!!,0.001)
        val text=ExperimentAnalytics.formatComparison("Test","Does Wi-Fi help?",a,b)
        assertTrue(text.contains("Выборка мала"))
        assertTrue(text.contains("наблюдательный"))
        assertFalse(text.contains("победитель"))
    }

    @Test fun emptyGroupsAreUnknownNotZero() {
        val a=ExperimentAnalytics.armSummary("A","A",emptyList())
        val b=ExperimentAnalytics.armSummary("B","B",emptyList())
        assertNull(a.averageDischargePerHour)
        assertNull(a.averagePeakBatteryTempC)
        assertTrue(ExperimentAnalytics.formatComparison("Test","",a,b).contains("н/д"))
    }
}
