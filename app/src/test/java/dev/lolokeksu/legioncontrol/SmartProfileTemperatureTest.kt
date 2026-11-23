package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartProfileTemperatureTest {
    @Test fun celsiusConvertsToExistingDeciCContract() {
        assertEquals(420,SmartProfileTemperature.parseDeciC("42"))
        assertEquals(420,SmartProfileTemperature.parseDeciC("42,0"))
        assertEquals(425,SmartProfileTemperature.parseDeciC("42.5"))
        assertEquals(300,SmartProfileTemperature.parseDeciC("30"))
        assertEquals(550,SmartProfileTemperature.parseDeciC("55,0"))
    }
    @Test fun ambiguousOrInvalidNumbersRejected() {
        for(s in listOf("420","29,9","55,1","42,99","NaN","","42,","-42","42 °C")) {
            assertNull(s,SmartProfileTemperature.parseDeciC(s))
        }
    }
    @Test fun persistedDeciCValuesRoundTripThroughVisibleText() {
        for(v in listOf(300,301,420,425,549,550)) {
            assertEquals(v,SmartProfileTemperature.parseDeciC(SmartProfileTemperature.format(v)))
        }
    }
}
