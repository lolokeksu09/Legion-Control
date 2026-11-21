package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SensorSampleTest {
    private fun valid(
        kind: SensorKind = SensorKind.ACCELEROMETER,
        values: FloatArray = floatArrayOf(1f, 2f, 3f),
        accuracy: Int = 3,
        event: Long = 1_200L,
        started: Long = 1_000L,
        observed: Long = 1_300L
    ) = SensorSampleValidation.accept(kind, values, accuracy, event, started, observed, 42L)

    @Test fun capturesOnlySensorDimensionsAndKeepsOriginalValues() {
        val source = floatArrayOf(1.5f, -2f, 3f, 100f)
        val sample = requireNotNull(valid(values = source))
        assertEquals(listOf(1.5f, -2f, 3f), sample.values)
        source[0] = 100f
        assertEquals(1.5f, sample.values[0])
        assertEquals(3, sample.accuracy)
    }

    @Test fun rejectsStaleInvalidAccuracyAndNonFiniteValues() {
        assertNull(valid(event = 1_000L))
        assertNull(valid(event = 1_600_000_000L))
        assertNull(valid(accuracy = 9))
        assertNull(valid(values = floatArrayOf(1f, Float.NaN, 2f)))
        assertNull(valid(values = floatArrayOf(1f, Float.POSITIVE_INFINITY, 2f)))
        assertNull(valid(values = floatArrayOf(1f, 2f)))
        assertNull(valid(kind = SensorKind.LIGHT, values = floatArrayOf(-1f)))
        assertNull(valid(kind = SensorKind.PROXIMITY, values = floatArrayOf(-1f)))
    }

    @Test fun supportsLightAndProximitySingleValueAndUnits() {
        assertEquals(listOf(220f), requireNotNull(
            valid(SensorKind.LIGHT, floatArrayOf(220f))).values)
        assertEquals(listOf(0f), requireNotNull(
            valid(SensorKind.PROXIMITY, floatArrayOf(0f))).values)
    }

    @Test fun comparesOnlySameKindAndChronologicalSavedReadings() {
        val old = requireNotNull(valid()).copy(recordedAtMs = 100L)
        val current = old.copy(recordedAtMs = 200L, values = listOf(3f, 1f, 3f))
        assertEquals(listOf(2f, -1f, 0f), SensorSampleComparison.delta(current, old))
        assertNull(SensorSampleComparison.delta(old, current))
        assertNull(SensorSampleComparison.delta(current, old.copy(kind = SensorKind.GYROSCOPE)))
        assertNull(SensorSampleComparison.delta(current, null))
    }

    @Test fun reportsMissingAndTimeoutWithoutFabricatingReadings() {
        val sample = requireNotNull(valid())
        val result = SensorCaptureResult("capture-1", listOf(
            SensorProbeOutcome(SensorKind.ACCELEROMETER, SensorProbeState.CAPTURED, sample),
            SensorProbeOutcome(SensorKind.LIGHT, SensorProbeState.MISSING),
            SensorProbeOutcome(SensorKind.PROXIMITY, SensorProbeState.TIMEOUT)
        ))
        val output = SensorSampleReport.format(result, emptyMap(), 1, 1L)
        assertTrue(output.contains("1.000 / 2.000 / 3.000 м/с²"))
        assertTrue(output.contains("не обнаружен"))
        assertTrue(output.contains("нет нового события"))
        assertFalse(output.contains(" · Δ "))
    }

    @Test fun reportShowsActualDeltaOnlyWithPreviousSensorValue() {
        val previous = requireNotNull(valid()).copy(
            recordedAtMs = 40L, values = listOf(0f, 2f, 3f)
        )
        val next = requireNotNull(valid()).copy(
            recordedAtMs = 42L, values = listOf(1f, 2f, 3f)
        )
        val captured = SensorCaptureResult("capture-2", listOf(
            SensorProbeOutcome(SensorKind.ACCELEROMETER, SensorProbeState.CAPTURED, next)
        ))
        val report = SensorSampleReport.format(
            captured, mapOf(SensorKind.ACCELEROMETER to previous), 1, 2L
        )
        assertTrue(report.contains(" · Δ 1.000 / 0.000 / 0.000 м/с²"))
    }


    @Test fun identifiesReasonForRejectedEventsWithoutFabricatingMeasurement() {
        assertEquals(SensorRejectionReason.STALE_EVENT, SensorSampleValidation.rejectionReason(
            SensorKind.LIGHT, floatArrayOf(50f), 3, 1000, 1000, 1300, 42
        ))
        assertEquals(SensorRejectionReason.NON_FINITE, SensorSampleValidation.rejectionReason(
            SensorKind.LIGHT, floatArrayOf(Float.NaN), 3, 1200, 1000, 1300, 42
        ))
        assertEquals(SensorRejectionReason.NEGATIVE_MEASUREMENT,
            SensorSampleValidation.rejectionReason(
                SensorKind.LIGHT, floatArrayOf(-2f), 3, 1200, 1000, 1300, 42
            ))
        assertEquals(SensorRejectionReason.INVALID_ACCURACY,
            SensorSampleValidation.rejectionReason(
                SensorKind.ACCELEROMETER, floatArrayOf(1f, 2f, 3f),
                9, 1200, 1000, 1300, 42
            ))
    }

    @Test fun boundedLightExtensionPreservesExistingBudgetsForOtherSensors() {
        assertEquals(3000L, SensorCapturePolicy.timeoutFor(SensorKind.GYROSCOPE))
        assertEquals(6000L, SensorCapturePolicy.timeoutFor(SensorKind.LIGHT))
        assertTrue(SensorCapturePolicy.extendForLight(setOf(SensorKind.LIGHT)))
        assertFalse(SensorCapturePolicy.extendForLight(setOf(SensorKind.PROXIMITY)))
        assertEquals(SensorProbeState.INVALID_DATA, SensorCapturePolicy.stateWhenExpired(2))
        assertEquals(SensorProbeState.TIMEOUT, SensorCapturePolicy.stateWhenExpired(0))
    }

    @Test fun distinguishesTimeoutWithoutEventsFromInvalidEventsWithReasons() {
        val withoutEvent = SensorProbeOutcome(
            SensorKind.LIGHT, SensorProbeState.TIMEOUT,
            timeoutMs = 6000L, reportingMode = 1
        )
        val rejected = SensorProbeOutcome(
            SensorKind.GYROSCOPE, SensorProbeState.INVALID_DATA,
            eventCount = 3, rejectedEvents = 3,
            rejectionReason = SensorRejectionReason.STALE_EVENT
        )
        val result = SensorCaptureResult("id", listOf(withoutEvent, rejected))
        val report = SensorSampleReport.format(result, emptyMap(), 0, 0L)
        assertTrue(report.contains("нет нового события за 6 с"))
        assertTrue(report.contains("режим=по изменению"))
        assertTrue(report.contains("события получены, но не прошли проверку"))
        assertTrue(report.contains("отброшено=3"))
        assertTrue(report.contains("причина=STALE_EVENT"))
        assertFalse(report.contains(" · Δ "))
    }

    @Test fun diagnosticOutcomesEnforceStateAndEventCountConsistency() {
        assertThrows(IllegalArgumentException::class.java) {
            SensorProbeOutcome(SensorKind.LIGHT, SensorProbeState.CAPTURED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SensorProbeOutcome(
                SensorKind.LIGHT, SensorProbeState.TIMEOUT,
                eventCount = 0, rejectedEvents = 1
            )
        }
    }

}
