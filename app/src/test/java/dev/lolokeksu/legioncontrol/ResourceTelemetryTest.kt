package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** No fake Android readings: validates only pure contracts and source values. */
class ResourceTelemetryTest {
    @Test fun probeResultsDistinguishReadingsFromFailures() {
        val reported = ProbeResult.available(42)
        assertEquals(ProbeState.AVAILABLE, reported.state)
        assertEquals(42, reported.value)
        for (missing in listOf(
            ProbeResult.unavailable<Int>(),
            ProbeResult.failure<Int>(),
            ProbeResult.rateLimited<Int>()
        )) {
            assertNull(missing.value)
            assertEquals(false, missing.state == ProbeState.AVAILABLE)
        }
    }

    @Test fun readGuardDistinguishesExceptionsAndInvalidManufacturerValues() {
        val failed = ProbeResult.read<Int>({ throw IllegalStateException("OEM failure") },
            TelemetryValidation::currentMicroAmps)
        assertEquals(ProbeState.ERROR, failed.state)
        assertNull(failed.value)
        val invalid = ProbeResult.read({ Int.MIN_VALUE },
            TelemetryValidation::currentMicroAmps)
        assertEquals(ProbeState.UNAVAILABLE, invalid.state)
        assertNull(invalid.value)
        val zero = ProbeResult.read({ 0 }, TelemetryValidation::currentMicroAmps)
        assertEquals(ProbeState.AVAILABLE, zero.state)
        assertEquals(0, zero.value)
    }

    @Test fun dependentProbesNeverHideMissingOrFailedService() {
        val absent = ProbeResult.unavailable<Int>()
        val error = ProbeResult.failure<Int>()
        val deferred = ProbeResult.rateLimited<Int>()
        assertEquals(ProbeState.UNAVAILABLE, absent.flatMap { ProbeResult.available(it + 1) }.state)
        assertEquals(ProbeState.ERROR, error.flatMap { ProbeResult.available(it + 1) }.state)
        assertEquals(ProbeState.RATE_LIMITED, deferred.flatMap { ProbeResult.available(it + 1) }.state)
        assertEquals(7, ProbeResult.available(6).flatMap { ProbeResult.available(it + 1) }.value)
        assertEquals(
            ProbeState.ERROR,
            ProbeResult.available(5).flatMap<Int> { error("broken dependency") }.state
        )
    }

    @Test fun memoryValidationRejectsInconsistentKernelReports() {
        val valid = TelemetryValidation.memory(
            totalBytes = 16_000L, availableBytes = 4_000L,
            thresholdBytes = 2_000L, lowMemory = false
        )
        assertEquals(MemoryMetrics(16_000L, 4_000L, 2_000L, false), valid)
        assertNull(TelemetryValidation.memory(0, 0, 0, false))
        assertNull(TelemetryValidation.memory(100, -1, 10, false))
        assertNull(TelemetryValidation.memory(100, 101, 10, false))
        assertNull(TelemetryValidation.memory(100, 50, -1, false))
        assertNull(TelemetryValidation.memory(100, 50, 101, false))
    }

    @Test fun storageValidationIsScopedToReadableFileSystem() {
        assertEquals(StorageMetrics(1000, 350), TelemetryValidation.storage(1000, 350))
        assertNull(TelemetryValidation.storage(0, 0))
        assertNull(TelemetryValidation.storage(100, -1))
        assertNull(TelemetryValidation.storage(100, 101))
    }

    @Test fun cpuCountIsOnlineProcessorsNotPhysicalCores() {
        assertEquals(8, TelemetryValidation.onlineProcessors(8))
        assertNull(TelemetryValidation.onlineProcessors(0))
        assertNull(TelemetryValidation.onlineProcessors(-1))
    }

    @Test fun batteryPropertiesKeepZeroAndSignedCurrentButRejectSentinels() {
        assertEquals(0, TelemetryValidation.currentMicroAmps(0))
        assertEquals(-900_000, TelemetryValidation.currentMicroAmps(-900_000))
        assertEquals(2_500_000, TelemetryValidation.chargeCounterMicroAh(2_500_000))
        assertEquals(0, TelemetryValidation.chargeCounterMicroAh(0))
        assertEquals(12_000_000_000L, TelemetryValidation.energyCounterNanoWh(12_000_000_000L))
        assertNull(TelemetryValidation.currentMicroAmps(Int.MIN_VALUE))
        assertNull(TelemetryValidation.currentMicroAmps(50_000_001))
        assertNull(TelemetryValidation.chargeCounterMicroAh(Int.MIN_VALUE))
        assertNull(TelemetryValidation.chargeCounterMicroAh(-1))
        assertNull(TelemetryValidation.energyCounterNanoWh(Long.MIN_VALUE))
        assertNull(TelemetryValidation.energyCounterNanoWh(-1L))
    }

    @Test fun thermalHeadroomAllowsSevereValuesAboveOneWithoutInventingThrottle() {
        assertEquals(0.0f, TelemetryValidation.thermalHeadroom(0f)!!, 0f)
        assertEquals(1.24f, TelemetryValidation.thermalHeadroom(1.24f)!!, 0f)
        assertNull(TelemetryValidation.thermalHeadroom(-0.01f))
        assertNull(TelemetryValidation.thermalHeadroom(Float.NaN))
        assertNull(TelemetryValidation.thermalHeadroom(Float.POSITIVE_INFINITY))
        assertNull(TelemetryValidation.thermalHeadroom(Float.NEGATIVE_INFINITY))
    }
}
