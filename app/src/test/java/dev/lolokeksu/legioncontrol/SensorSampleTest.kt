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
        assertFalse(output.contains("Δ "))
    }
}
