package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class GameSessionAnalysisTest {
    private val header = GameSessionHeader(
        "session","com.example.game","Example Game",1000L,61000L,"USER_STOP",false
    )
    private fun point(
        time: Long, percent: Int? = null, temp: Int? = null, thermal: Int? = null,
        saving: Boolean? = null, network: String = "UNKNOWN", validated: Boolean? = null
    ) = GameTelemetryPoint(time,percent,temp,null,thermal,saving,120f,network,validated)

    @Test fun absentValuesRemainUnknownNotZero() {
        val s = GameSessionAnalysis.summarize(header,listOf(point(2000L),point(3000L)))
        assertNull(s.batteryDelta)
        assertNull(s.minTempDeciC)
        assertNull(s.maxTempDeciC)
        assertEquals(2,s.pointCount)
        assertEquals(2,s.unknownNetworkCount)
        assertTrue(GameSessionAnalysis.render(s).contains("н/д"))
    }

    @Test fun realTrendsAndWarningsPreserveUnits() {
        val s = GameSessionAnalysis.summarize(header,listOf(
            point(2000L,77,340,0,false,"WIFI",true),
            point(18000L,73,390,3,true,"WIFI",false),
            point(34000L,70,450,4,true,"CELLULAR",true)
        ))
        assertEquals(-7,s.batteryDelta)
        assertEquals(340,s.minTempDeciC)
        assertEquals(450,s.maxTempDeciC)
        assertEquals(2,s.thermalSevereCount)
        assertEquals(2,s.powerSavingCount)
        assertEquals(1,s.unvalidatedNetworkCount)
        assertEquals(60000L,s.durationMs())
        assertTrue(GameSessionAnalysis.warnings(s).any { it.contains("45 °C") })
        assertTrue(GameSessionAnalysis.render(s).contains("-7 п.п."))
    }

    @Test fun invalidReadingsAreNotPartOfBatteryOrThermalStatistics() {
        val s=GameSessionAnalysis.summarize(header,listOf(
            point(2000L,-1,9999),
            point(3000L,55,350),
            point(4000L,54,360),
            point(5000L,999,-9999)
        ))
        assertEquals(-1,s.batteryDelta)
        assertEquals(350,s.minTempDeciC)
        assertEquals(360,s.maxTempDeciC)
    }

    @Test fun compareDoesNotClaimFpsOrGamingPing() {
        val a=GameSessionAnalysis.summarize(header,listOf(point(2000L,50,380),point(60000L,45,400)))
        val b=GameSessionAnalysis.summarize(header.copy(appLabel="Other Game"),listOf(point(2000L)))
        val result=GameSessionAnalysis.compare(a,b)
        assertTrue(result.contains("Example Game / Other Game"))
        assertTrue(result.contains("н/д"))
        assertTrue(result.contains("не оценка производительности"))
    }

    @Test fun noSamplesDoNotProduceFictitiousMeasurements() {
        val s=GameSessionAnalysis.summarize(header,emptyList())
        assertEquals(0,s.pointCount)
        assertNull(s.batteryDelta)
        assertTrue(GameSessionAnalysis.warnings(s).any { it.contains("Нет записанных") })
    }
}
