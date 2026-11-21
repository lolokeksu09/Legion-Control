package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTrendEngineTest {
    private fun reading(
        elapsed: Long, thermal: Int? = 2, headroom: Float? = 0.75f,
        counterMicroAh: Int? = 3_000_000
    ): DeviceDiagnosticSnapshot = DeviceDiagnosticSnapshot(
        capabilities = PlatformCapabilities(thermal, true, null, emptyList()),
        resources = ResourceTelemetrySnapshot(
            elapsedRealtimeMs = elapsed,
            systemMemory = ProbeResult.unavailable(),
            appFilesystem = ProbeResult.unavailable(),
            onlineProcessorCount = ProbeResult.unavailable(),
            batteryChargeCounterMicroAh = counterMicroAh?.let { ProbeResult.available(it) }
                ?: ProbeResult.unavailable(),
            batteryCurrentNowMicroAmps = ProbeResult.unavailable(),
            batteryCurrentAverageMicroAmps = ProbeResult.unavailable(),
            batteryEnergyCounterNanoWh = ProbeResult.unavailable(),
            thermalHeadroom = headroom?.let { ProbeResult.available(it) }
                ?: ProbeResult.unavailable()
        )
    )

    @Test fun firstReadingIsBaselineAndSecondShowsMeasuredChanges() {
        val engine = DiagnosticTrendEngine()
        assertNull(engine.record(reading(1000, 2, 0.74f, 3_000_000), 50))
        val trend = engine.record(reading(121_000, 3, 0.92f, 3_200_000), 53)!!
        assertEquals(120_000L, trend.intervalMs)
        assertEquals(1, trend.thermalStatusDelta)
        assertEquals(0.18f, trend.thermalHeadroomDelta!!, 0.00001f)
        assertEquals(200_000L, trend.chargeCounterDeltaMicroAh)
        assertEquals(3, trend.percentageDelta)
        assertEquals(BatteryCounterAgreement.AGREE, trend.batteryAgreement)
        val description = DiagnosticTrendTextReport.format(trend)
        assertTrue(description.contains("120.0 с"))
        assertTrue(description.contains("+0.18"))
        assertTrue(description.contains("+200.0 мА·ч"))
    }

    @Test fun missingDataNeverBecomesAZeroDelta() {
        val engine = DiagnosticTrendEngine()
        engine.record(reading(5000, 2, 0.80f, null), 65)
        val trend = engine.record(reading(70_000, null, null, null), -1)!!
        assertNull(trend.thermalStatusDelta)
        assertNull(trend.thermalHeadroomDelta)
        assertNull(trend.chargeCounterDeltaMicroAh)
        assertNull(trend.percentageDelta)
        assertEquals(BatteryCounterAgreement.INSUFFICIENT, trend.batteryAgreement)
        val description = DiagnosticTrendTextReport.format(trend)
        assertTrue(description.contains("недоступна"))
    }

    @Test fun contradictoryCounterNeedsRepeatedValidationNotBatteryHealthClaim() {
        val engine = DiagnosticTrendEngine()
        engine.record(reading(1000, counterMicroAh = 2_200_000), 40)
        val trend = engine.record(reading(121_000, counterMicroAh = 2_000_000), 44)!!
        assertEquals(BatteryCounterAgreement.DISAGREE, trend.batteryAgreement)
        val description = DiagnosticTrendTextReport.format(trend)
        assertTrue(description.contains("расходятся"))
        assertTrue(description.contains("не является диагностикой износа"))
    }

    @Test fun stableBatteryPercentageCannotProveCounterAccuracy() {
        val engine = DiagnosticTrendEngine()
        engine.record(reading(1000, counterMicroAh = 3_000_000), 53)
        val trend = engine.record(reading(121_000, counterMicroAh = 3_015_000), 53)!!
        assertEquals(BatteryCounterAgreement.INSUFFICIENT, trend.batteryAgreement)
    }

    @Test fun shortIntervalsMustNotProduceStrongConsistencyJudgement() {
        val engine = DiagnosticTrendEngine()
        engine.record(reading(1000, counterMicroAh = 3_000_000), 50)
        val trend = engine.record(reading(3000, counterMicroAh = 3_300_000), 53)!!
        assertEquals(BatteryCounterAgreement.INSUFFICIENT, trend.batteryAgreement)
    }

    @Test fun monotonicClockRollbackAndHugeGapDiscardStaleBaseline() {
        val engine = DiagnosticTrendEngine()
        assertNull(engine.record(reading(6000), 40))
        assertNull(engine.record(reading(500), 40))
        assertEquals(1500L, engine.record(reading(2000), 40)!!.intervalMs)
        assertNull(engine.record(reading(30_000_000L), 40))
    }

    @Test fun clearedSessionMustNotCompareAgainstOldMeasurements() {
        val engine = DiagnosticTrendEngine()
        engine.record(reading(1000), 40)
        engine.clear()
        assertNull(engine.record(reading(2000), 40))
    }

    @Test fun thermalCodesMatchAndroidPlatformDefinitions() {
        assertEquals("2 · умеренное ограничение", ThermalSeverity.describe(2))
        assertEquals("0 · нет теплового ограничения", ThermalSeverity.describe(0))
        assertEquals("3 · серьёзное ограничение", ThermalSeverity.describe(3))
        assertEquals("6 · требуется выключение", ThermalSeverity.describe(6))
        assertEquals("недоступно", ThermalSeverity.describe(null))
        assertEquals("недоступно", ThermalSeverity.describe(7))
    }
}
