package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSessionSummaryTest {
    private fun sample(
        elapsed: Long, percent: Int? = 50, temp: Int? = 370,
        counter: Int? = 4_000_000, headroom: Float? = 0.8f,
        session: String = "session-1"
    ) = DiagnosticStoredSample(
        sessionId = session, elapsedMs = elapsed, recordedAtMs = 10000 + elapsed,
        batteryPercent = percent, batteryTemperatureDeciC = temp,
        thermalStatus = 0, thermalHeadroom = headroom,
        chargeCounterMicroAh = counter, currentNowMicroAmps = null,
        currentAverageMicroAmps = null, ramAvailableBytes = null
    )

    @Test fun noTrendForSingleOrMixedSessionAndResetClock() {
        assertNull(DiagnosticSessionAnalyzer.analyze(emptyList()))
        assertNull(DiagnosticSessionAnalyzer.analyze(listOf(sample(1_000))))
        assertNull(DiagnosticSessionAnalyzer.analyze(listOf(
            sample(1_000), sample(2_000, session = "session-2")
        )))
        assertNull(DiagnosticSessionAnalyzer.analyze(listOf(
            sample(1_000), sample(1_000)
        )))
    }

    @Test fun calculatesActualChargeRateFromSeveralManualMeasurements() {
        val values = listOf(
            sample(elapsed = 1_000, percent = 50, temp = 380,
                counter = 4_000_000, headroom = 0.98f),
            sample(elapsed = 100_000, percent = 51, temp = 370,
                counter = 4_080_000, headroom = 0.93f),
            sample(elapsed = 204_600, percent = 52, temp = 360,
                counter = 4_168_200, headroom = 0.92f)
        )
        val summary = DiagnosticSessionAnalyzer.analyze(values.reversed())!!
        assertEquals(3, summary.samples)
        assertEquals(203_600L, summary.elapsedMs)
        assertEquals(-20, summary.batteryTemperatureDeltaDeciC)
        assertEquals(360, summary.batteryTemperatureMinimumDeciC)
        assertEquals(380, summary.batteryTemperatureMaximumDeciC)
        assertEquals(-0.06f, summary.thermalHeadroomDelta!!, 0.00001f)
        assertEquals(168_200L, summary.chargeCounterDeltaMicroAh)
        assertEquals(2, summary.batteryPercentDelta)
        assertEquals(2974.85, summary.averageChargeCounterRateMa!!, 1.0)
        val text = DiagnosticSessionSummary.format(summary)
        assertTrue(text.contains("3 замеров"))
        assertTrue(text.contains("-2.0 °C"))
        assertTrue(text.contains("не мощность зарядного устройства"))
    }

    @Test fun missingFieldsNeverTurnIntoZero() {
        val summary = DiagnosticSessionAnalyzer.analyze(listOf(
            sample(1000, temp = null, counter = null, headroom = null, percent = null),
            sample(101_000, temp = 390, counter = 4_900_000,
                headroom = 1.1f, percent = 65)
        ))!!
        assertNull(summary.batteryTemperatureDeltaDeciC)
        assertNull(summary.thermalHeadroomDelta)
        assertNull(summary.chargeCounterDeltaMicroAh)
        assertNull(summary.batteryPercentDelta)
        assertNull(summary.averageChargeCounterRateMa)
        assertTrue(DiagnosticSessionSummary.format(summary).contains("недоступно"))
    }

    @Test fun shortIntervalsDoNotClaimChargeRate() {
        val summary = DiagnosticSessionAnalyzer.analyze(listOf(
            sample(1000, counter = 2_000_000),
            sample(2000, counter = 2_010_000)
        ))!!
        assertNull(summary.averageChargeCounterRateMa)
        assertEquals(10_000L, summary.chargeCounterDeltaMicroAh)
    }

    @Test fun tooLongGapCannotBeReportedAsCurrentSessionTrend() {
        assertNull(DiagnosticSessionAnalyzer.analyze(listOf(
            sample(1000), sample(30_000_000)
        )))
    }
}
