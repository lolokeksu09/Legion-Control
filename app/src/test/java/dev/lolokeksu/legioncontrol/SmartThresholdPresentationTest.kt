package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SmartThresholdPresentationTest {
    @Test fun acceptsHumanReadableCelsiusWithoutChangingDbUnits() {
        assertEquals("42.0", SmartThresholdPresentation.showTemperature(420))
        assertEquals("30.0", SmartThresholdPresentation.showTemperature(300))
        assertEquals("55.0", SmartThresholdPresentation.showTemperature(550))
        assertEquals(420, SmartThresholdPresentation.parseTemperature("42.0"))
        assertEquals(425, SmartThresholdPresentation.parseTemperature("42,5"))
        assertEquals(420, SmartThresholdPresentation.parseTemperature("42"))
        assertEquals(300, SmartThresholdPresentation.parseTemperature("30.0"))
        assertEquals(550, SmartThresholdPresentation.parseTemperature("55.0"))
        assertEquals(20, SmartThresholdPresentation.parseChargePercent("20"))
    }

    @Test fun malformedOrOutsideLimitsAreRejected() {
        for (invalid in listOf("", "420", "42.25", "29.9", "55.1",
                "100", "-5", "abc", "4e1", "42.0.0", "42,", "  ")) {
            assertNull(invalid, SmartThresholdPresentation.parseTemperature(invalid))
        }
        for (invalid in listOf("", "4", "81", "20.5", "-1")) {
            assertNull(invalid, SmartThresholdPresentation.parseChargePercent(invalid))
        }
    }
}
