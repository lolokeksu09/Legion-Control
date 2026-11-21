package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class AnalyticsAlpha12Test {
    private fun attempt(
        kind: SensorKind, state: SensorProbeState, latency: Long? = null,
        rejected: Int = 0
    ) = SavedSensorAttempt(
        kind, state, 12L, rejected + 1, rejected, latency, 3000L, null, null, null
    )

    @Test fun missingSensorsAndTimeoutsAreNotCountedAsCaptured() {
        val source = listOf(
            attempt(SensorKind.LIGHT, SensorProbeState.TIMEOUT),
            attempt(SensorKind.LIGHT, SensorProbeState.MISSING),
            attempt(SensorKind.LIGHT, SensorProbeState.INVALID_DATA, rejected = 2),
            attempt(SensorKind.LIGHT, SensorProbeState.CAPTURED, latency = 250),
            attempt(SensorKind.LIGHT, SensorProbeState.CAPTURED, latency = 150),
            attempt(SensorKind.GYROSCOPE, SensorProbeState.CAPTURED, latency = 30)
        )
        val light = requireNotNull(SensorQualityAnalytics.analyze(source)
            .firstOrNull { it.kind == SensorKind.LIGHT })
        assertEquals(5, light.attempts)
        assertEquals(2, light.captured)
        assertEquals(1, light.timeouts)
        assertEquals(1, light.missing)
        assertEquals(1, light.invalid)
        assertEquals(2L, light.rejectedEvents)
        assertEquals(200.0, light.averageCapturedLatencyMs!!, 0.01)
        assertTrue(SensorQualityAnalytics.format(source).contains("успешно 2/5"))
    }

    @Test fun emptyAnalyticsDoesNotInventUptime() {
        assertTrue(SensorQualityAnalytics.format(emptyList()).contains("нет"))
        val onlyTimeouts = SensorQualityAnalytics.analyze(
            listOf(attempt(SensorKind.LIGHT, SensorProbeState.TIMEOUT))
        )
        assertNull(onlyTimeouts.first().averageCapturedLatencyMs)
    }

    private fun reading(
        session: String = "s1", elapsed: Long = 1000,
        battery: Int = 70, temp: Int = 350,
        refresh: Float? = null, saving: Boolean? = null
    ) = StoredReading(
        session, 2000L + elapsed, elapsed, battery, temp,
        "Разряжается", "Не подключена", null, null, saving, refresh, null, null
    )

    @Test fun sessionProfileUsesRecordedPointsNotSyntheticSamples() {
        val input = listOf(
            reading(elapsed = 61000L, battery = 66, temp = 410, refresh = 120f),
            reading(elapsed = 1000L, battery = 70, temp = 350, refresh = 90f, saving = true)
        )
        val snapshot = requireNotNull(SessionAnalytics.analyze(input))
        assertEquals(60_000L, snapshot.elapsedMs)
        assertEquals(-4, snapshot.batteryPercentDelta)
        assertEquals(60, snapshot.batteryTemperatureDeltaDeciC)
        assertEquals(1, snapshot.powerSavingReadings)
        assertEquals(90f, snapshot.minRecordedRefreshHz!!, 0.01f)
        assertTrue(SessionAnalytics.format(snapshot).contains("-4 п.п."))
        assertTrue(SessionAnalytics.format(snapshot).contains("не непрерывное измерение"))
    }

    @Test fun onePointAndMixedSessionsDoNotFabricateDeltas() {
        val one = requireNotNull(SessionAnalytics.analyze(listOf(reading())))
        assertNull(one.elapsedMs)
        assertNull(one.batteryPercentDelta)
        assertNull(SessionAnalytics.analyze(listOf(reading(), reading(session = "other"))))
        assertTrue(SessionAnalytics.format(null).contains("нет"))
    }
}
