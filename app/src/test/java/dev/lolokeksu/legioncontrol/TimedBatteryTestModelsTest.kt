package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class TimedBatteryTestModelsTest {
    private fun point(
        elapsed: Long = 1000L, percent: Int = 40,
        temperature: Int = 400, thermal: Int? = 0,
        current: Int? = -350_000
    ) = TimedBatteryPoint(
        elapsed, elapsed + 1_000_000L, percent, temperature,
        current, 4000, thermal
    )

    @Test fun explicitTimerRunsAtMonotonic10SecondIntervalsAndEndsAfter60() {
        val start = 10_000L
        assertEquals(10_000L, TimedBatteryPolicy.nextDelayMs(start, start))
        assertEquals(1L, TimedBatteryPolicy.nextDelayMs(start, start + 9_999))
        assertEquals(10_000L, TimedBatteryPolicy.nextDelayMs(start, start + 10_000))
        assertEquals(1L, TimedBatteryPolicy.nextDelayMs(start, start + 59_999))
        assertNull(TimedBatteryPolicy.nextDelayMs(start, start + 60_000))
        assertNull(TimedBatteryPolicy.nextDelayMs(start, start - 1))
    }

    @Test fun thermalGuardStopsOnAndroidSevereOrBattery45C() {
        assertNull(TimedBatteryPolicy.earlyStop(point(thermal = null, temperature = 430)))
        assertEquals(TimedBatteryStopReason.THERMAL_SEVERE,
            TimedBatteryPolicy.earlyStop(point(thermal = 3)))
        assertEquals(TimedBatteryStopReason.BATTERY_HOT,
            TimedBatteryPolicy.earlyStop(point(thermal = 0, temperature = 450)))
        assertEquals(TimedBatteryStopReason.THERMAL_SEVERE,
            TimedBatteryPolicy.earlyStop(point(thermal = 4, temperature = 470)))
    }

    @Test fun reportUsesOnlyTwoDistinctValidatedPointsNotGuessedDrainRate() {
        val points = listOf(
            point(elapsed = 10_000L, percent = 70, temperature = 400),
            point(elapsed = 20_000L, percent = 69, temperature = 420)
        )
        val result = TimedBatteryTestResult(
            "t", "s", 1000L, 20_000L, 2, points, TimedBatteryStopReason.COMPLETED
        )
        val text = TimedBatterySummary.format(result)
        assertTrue(text.contains("-1 п.п."))
        assertTrue(text.contains("+2.0 °C"))
        assertTrue(text.contains("-350…-350 мА"))
        assertTrue(text.contains("Сохранение в SQLite проверяется отдельно"))
        assertFalse(text.contains("износ аккумулятора"))
    }

    @Test fun missingAndroidValuesOrSingleSampleRemainExplicit() {
        val result = TimedBatteryTestResult(
            "t", "s", 1000, 1100, 1,
            listOf(point(current = null, thermal = null)),
            TimedBatteryStopReason.APP_BACKGROUND
        )
        val text = TimedBatterySummary.format(result)
        assertTrue(text.contains("недостаточно отдельных показаний"))
        assertTrue(text.contains("недоступны"))
        assertTrue(text.contains("приложение свернуто"))
        assertThrows(IllegalArgumentException::class.java) {
            result.copy(attemptedReads = 0)
        }
    }

    @Test fun threeFailuresMustNotBecomeSuccessfulSampleRows() {
        val result = TimedBatteryTestResult(
            "t", "s", 1000, 3000, 3,
            emptyList(), TimedBatteryStopReason.READING_FAILED
        )
        val text = TimedBatterySummary.format(result)
        assertTrue(text.contains("3 неудачных измерения"))
        assertTrue(text.contains("Нет достоверных показаний"))
        assertFalse(text.contains("Изменение заряда:"))
    }
}
