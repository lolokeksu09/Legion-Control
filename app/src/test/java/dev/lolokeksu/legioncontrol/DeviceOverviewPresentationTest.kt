package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class DeviceOverviewPresentationTest {
    @Test fun unknownMemoryAndPowerDoNotLookHealthy() {
        val report = DeviceOverviewPresentation.system(
            ProbeResult.failure(), ProbeResult.unavailable(), null, null, null)
        assertTrue(report.contains("ошибка чтения"))
        assertTrue(report.contains("Энергосбережение: н/д"))
        assertFalse(report.contains("Android lowMemory: нет"))
    }

    @Test fun signedCurrentAndZeroChargeAreRealValues() {
        val report = DeviceOverviewPresentation.battery(
            ProbeResult.available(0), ProbeResult.available(1_000_000L),
            ProbeResult.available(-1500), ProbeResult.rateLimited())
        assertTrue(report.contains("0.0 мА·ч"))
        assertTrue(report.contains("-2 мА"))
        assertTrue(report.contains("1.00 мВт·ч"))
        assertTrue(report.contains("ограничена частота"))
    }

    @Test fun headroomRateLimitIsNotAZeroMeasurement() {
        val report = DeviceOverviewPresentation.thermal(null, ProbeResult.rateLimited())
        assertTrue(report.contains("ограничена частота"))
        assertFalse(report.contains("Headroom: 0.00"))
    }
}
