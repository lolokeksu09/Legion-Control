package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class SmartInsightsPresentationTest {
    private fun result(thermalKnown: Long = 0, networkKnown: Long = 0) = SmartInsightsResult(
        packageName = "com.example.game", days = 7, sessions = 4,
        completedSessions = 2, totalCompletedMinutes = 90, sampleCount = 12,
        peakBatteryTempDeciC = 435, severeReadings = 2, unvalidatedReadings = 1,
        thermalSampleCount = thermalKnown, networkSampleCount = networkKnown,
        interruptedSessions = 1
    )

    @Test fun ratiosUseOnlyKnownMeasurements() {
        val rows = SmartInsightsPresentation.metrics(result(5, 4))
        assertEquals("40.0% · 2/5", rows.single { it.title == "Thermal ≥ SEVERE" }.value)
        assertEquals("25.0% · 1/4", rows.single { it.title == "Сеть не подтверждена" }.value)
    }

    @Test fun unknownDenominatorsStayUnknownInsteadOfZeroPercent() {
        val rows = SmartInsightsPresentation.metrics(result())
        assertEquals("н/д", rows.single { it.title == "Thermal ≥ SEVERE" }.value)
        assertEquals("н/д", rows.single { it.title == "Сеть не подтверждена" }.value)
    }

    @Test fun completedDurationExcludesInterruptedSessions() {
        val rows = SmartInsightsPresentation.metrics(result(5, 4))
        assertEquals("2", rows.single { it.title == "Завершено вручную" }.value)
        assertEquals("1", rows.single { it.title == "Прервано / завершено иначе" }.value)
        assertEquals("1", rows.single { it.title == "Без завершения" }.value)
        assertEquals("90 мин", rows.single { it.title == "Время завершённых сессий" }.value)
    }
}
