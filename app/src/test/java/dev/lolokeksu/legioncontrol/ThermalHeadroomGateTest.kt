package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ensures Android thermal service is not polled faster than its documented limit. */
class ThermalHeadroomGateTest {
    @Test fun frequentInvocationsSkipThePlatformWithoutReturningStaleSamples() {
        val gate = ThermalHeadroomGate()
        var reads = 0
        fun probe(): Float {
            reads++
            return 0.6f
        }

        assertEquals(ProbeState.AVAILABLE, gate.sample(1000, ::probe).state)
        val second = gate.sample(1200, ::probe)
        assertEquals(ProbeState.RATE_LIMITED, second.state)
        assertNull(second.value)
        assertEquals(1, reads)
        assertEquals(ProbeState.RATE_LIMITED, gate.sample(2499, ::probe).state)
        assertEquals(ProbeState.AVAILABLE, gate.sample(2500, ::probe).state)
        assertEquals(2, reads)
    }

    @Test fun invalidAndFailingPlatformResponsesAreNotReportedAsMeasurements() {
        val gate = ThermalHeadroomGate()
        assertEquals(ProbeState.UNAVAILABLE, gate.sample(-1) { 0.2f }.state)
        assertEquals(ProbeState.UNAVAILABLE, gate.sample(0) { Float.NaN }.state)
        assertEquals(ProbeState.ERROR, gate.sample(1600) { error("OEM failure") }.state)
        assertEquals(ProbeState.AVAILABLE, gate.sample(3100) { 1.3f }.state)
    }

    @Test fun rebootOrClockResetReopensTheMonotonicSamplingWindow() {
        val gate = ThermalHeadroomGate()
        assertEquals(ProbeState.AVAILABLE, gate.sample(99_000) { 0.2f }.state)
        assertEquals(ProbeState.AVAILABLE, gate.sample(500) { 0.3f }.state)
    }
}
