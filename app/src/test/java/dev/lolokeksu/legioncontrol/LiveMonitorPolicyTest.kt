package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class LiveMonitorPolicyTest {
    @Test fun boundedMonotonicRunRejectsLateAndOverflowSamples() {
        var points = emptyList<MonitorPoint>()
        for (i in 0 until 140) points = MonitorPolicy.append(points, MonitorPoint(i * 2000L))
        assertEquals(120, points.size)
        assertEquals(238000L, points.last().elapsedMs)
        val short = listOf(MonitorPoint(10))
        assertSame(short, MonitorPolicy.append(short, MonitorPoint(9)))
        assertSame(short, MonitorPolicy.append(short, MonitorPoint(-1)))
    }

    @Test fun intervalAndComparisonDoNotInventMissingMeasurements() {
        assertEquals(5000L, MonitorPolicy.interval(1))
        assertEquals(2000L, MonitorPolicy.interval(2000))
        val before = MonitorPoint(0, temperatureDeciC = 400, availableRamBytes = 2147483648L)
        val after = MonitorPoint(5000, temperatureDeciC = 415, availableRamBytes = 1073741824L)
        assertEquals(1.5, MonitorPolicy.delta(MonitorMetric.TEMPERATURE, before, after)!!, 0.001)
        assertEquals(-1.0, MonitorPolicy.delta(MonitorMetric.RAM, before, after)!!, 0.001)
        assertNull(MonitorPolicy.delta(MonitorMetric.DISPLAY, before, after))
        assertNull(MonitorPolicy.delta(MonitorMetric.RAM, null, after))
    }

    @Test fun csvPreservesMissingAndSignedValuesWithoutFormulaInjection() {
        val csv = MonitorCsv.render(listOf(MonitorPoint(
            2000, battery = 80, currentMicroA = -150000,
            network = "=FORMULA()", memoryState = ProbeState.ERROR,
            headroomState = ProbeState.RATE_LIMITED
        )))
        assertTrue(csv.startsWith("elapsed_ms,battery_percent,"))
        assertTrue(csv.contains("2000,80,,,,,-150000,,,"))
        assertTrue(csv.contains("\"'=FORMULA()\""))
        assertTrue(csv.contains(",ERROR,RATE_LIMITED,false\r\n"))
        assertEquals(2, csv.trimEnd().split("\r\n").size)
    }
}
