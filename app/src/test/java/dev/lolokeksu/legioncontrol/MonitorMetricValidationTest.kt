package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class MonitorMetricValidationTest {
    @Test fun invalidMeasurementsAreNotGraphedOrUsedInDelta() {
        val point=MonitorPoint(0,battery=101,temperatureDeciC=1201,
            availableRamBytes=100,totalRamBytes=50,displayHz=Float.NaN)
        MonitorMetric.entries.forEach { assertNull(it.value(point)) }
        assertNull(MonitorPolicy.delta(MonitorMetric.BATTERY,point,MonitorPoint(1,battery=50)))
    }

    @Test fun chargeChangeUsesPercentagePoints() {
        val before=MonitorPoint(0,battery=80)
        val after=MonitorPoint(1000,battery=75)
        assertEquals(-5.0,MonitorPolicy.delta(MonitorMetric.BATTERY,before,after)!!,0.001)
        assertEquals("%",MonitorMetric.BATTERY.unit)
        assertEquals("п.п.",MonitorMetric.BATTERY.deltaUnit)
        assertEquals("Гц",MonitorMetric.DISPLAY.deltaUnit)
    }
}
