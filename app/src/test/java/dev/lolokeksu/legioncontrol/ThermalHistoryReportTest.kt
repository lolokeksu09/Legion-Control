package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ThermalHistoryReportTest {
    @Test fun noSamplesDoesNotFabricateThermalHistory() {
        assertTrue(ThermalHistoryReport.format(emptyList()).contains("нет"))
        assertNull(ThermalHistoryReport.warning(null, null))
    }

    @Test fun severePlatformStateShowsWarningWithoutAssumingCpuTemperature() {
        val text = requireNotNull(ThermalHistoryReport.warning(3, 1.03f))
        assertTrue(text.contains("серьёзное ограничение"))
        assertNull(ThermalHistoryReport.warning(0, 0.75f))
        assertNotNull(ThermalHistoryReport.warning(null, 1.03f))
    }

    @Test fun calculatesOnlyActualManualHistoryAndRejectsLongComparisonGaps() {
        val old = ThermalHistoryItem(1_000L, 390, 0, 0.6f)
        val recent = ThermalHistoryItem(61_000L, 430, 3, 1.03f)
        val text = ThermalHistoryReport.format(listOf(old, recent))
        assertTrue(text.contains("39.0–43.0 °C"))
        assertTrue(text.contains("+4.0 °C"))
        assertTrue(text.contains("Состояний SEVERE или выше: 1"))
        assertTrue(ThermalHistoryReport.format(
            listOf(old, recent.copy(recordedAtMs = 100_000_000L))
        ).contains("недостаточно сопоставимых замеров"))
    }

    @Test fun unavailableStatusIsNotInterpretedAsSafe() {
        val text = ThermalHistoryReport.format(
            listOf(ThermalHistoryItem(1_000L, null, null, null))
        )
        assertTrue(text.contains("нет данных"))
        assertTrue(text.contains("недоступно"))
    }
}
