package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class PreflightRulesTest {
    private fun findings(p:PreflightPreset, r:PreflightReading) =
        PreflightRules.evaluate(p,r).associateBy { it.key }

    @Test fun requirementsArePerGameAndNeverTurnMissingIntoPass() {
        val reading = PreflightReading(battery=30, temperatureDeciC=410,
            displayHz=60f, supportedMaxHz=120f, availableRamBytes=3L*1073741824L,
            validatedNetwork=true, powerSaving=true)
        val competitive = findings(PreflightPreset(120,40,40,4,true),reading)
        assertEquals(PreflightLevel.WARNING,competitive.getValue("display").level)
        assertEquals(PreflightLevel.WARNING,competitive.getValue("battery").level)
        assertEquals(PreflightLevel.WARNING,competitive.getValue("temperature").level)
        assertEquals(PreflightLevel.WARNING,competitive.getValue("ram").level)
        assertEquals(PreflightLevel.WARNING,competitive.getValue("power").level)
        assertEquals(PreflightLevel.UNKNOWN,competitive.getValue("thermal").level)
        val relaxed = findings(PreflightPreset(60,20,42,2,true),reading)
        for (key in listOf("display","battery","temperature","ram","network"))
            assertEquals(key,PreflightLevel.PASS,relaxed.getValue(key).level)
    }

    @Test fun severeThermalWarningsSurviveRelaxedPreferences() {
        val r = findings(PreflightPreset(0,5,45,0,false),
            PreflightReading(thermal=3,temperatureDeciC=450,androidLowRam=true))
        assertEquals(PreflightLevel.CRITICAL,r.getValue("thermal").level)
        assertEquals(PreflightLevel.CRITICAL,r.getValue("temperature").level)
        assertEquals(PreflightLevel.WARNING,r.getValue("ram").level)
        assertEquals(PreflightLevel.SKIPPED,r.getValue("display").level)
        assertEquals(PreflightLevel.SKIPPED,r.getValue("network").level)
    }

    @Test fun invalidOemValuesAreUnknownAndRefreshUsesTolerance() {
        val f = findings(PreflightPreset(120),PreflightReading(
            battery=-1,temperatureDeciC=99999,thermal=99,displayHz=Float.NaN,
            availableRamBytes=-1))
        for (key in listOf("battery","temperature","thermal","display","ram"))
            assertEquals(key,PreflightLevel.UNKNOWN,f.getValue(key).level)
        assertEquals(PreflightLevel.PASS,
            findings(PreflightPreset(120),PreflightReading(displayHz=119.88f)).getValue("display").level)
    }

    @Test fun storageEncodingAndFreshnessAreStrict() {
        val preset = PreflightPreset(144,35,41,3,false)
        assertEquals(preset,PreflightPreset.decode(preset.encode()))
        for (bad in listOf(null,"","v1|999|20|42|2|1","v1|90|20|42|2|maybe","v1|90|20|42|2|1|extra"))
            assertNull(PreflightPreset.decode(bad))
        assertTrue(PreflightRules.fresh(1000,61000))
        assertFalse(PreflightRules.fresh(1000,61001))
        assertFalse(PreflightRules.fresh(1000,999))
    }

    @Test fun busyOperationsAreWarningsNotFakePerformanceScores() {
        val f=findings(PreflightPreset(),PreflightReading(captureBusy=true,
            sessionActive=true,pendingProfile="com.example.game",pendingProfileKnown=true))
        assertEquals(PreflightLevel.WARNING,f.getValue("capture").level)
        assertEquals(PreflightLevel.INFO,f.getValue("session").level)
        assertEquals(PreflightLevel.WARNING,f.getValue("undo").level)
        assertEquals(11,f.size)
    }
}
