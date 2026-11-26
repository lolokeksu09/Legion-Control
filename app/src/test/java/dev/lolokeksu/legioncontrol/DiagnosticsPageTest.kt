package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsPageTest {
    @Test fun restoredPageWinsAndUnknownValuesFallBack() {
        assertEquals(DiagnosticsPage.HISTORY, DiagnosticsPage.initial("HISTORY", "MONITOR", "monitor.start"))
        assertEquals(DiagnosticsPage.OVERVIEW, DiagnosticsPage.initial("unknown", "bad", "unknown"))
        assertEquals(DiagnosticsPage.MONITOR, DiagnosticsPage.initial(null, "MONITOR", null))
    }

    @Test fun stableFeaturesRevealTheirActualSection() {
        val expected = mapOf(
            "diag.measure" to DiagnosticsPage.OVERVIEW,
            "diag.battery" to DiagnosticsPage.OVERVIEW,
            "diag.thermal" to DiagnosticsPage.HISTORY,
            "diag.zip" to DiagnosticsPage.HISTORY,
            "diag.sensors" to DiagnosticsPage.SENSORS,
            "monitor.start" to DiagnosticsPage.MONITOR,
            "monitor.chart" to DiagnosticsPage.MONITOR,
            "monitor.baseline" to DiagnosticsPage.MONITOR,
            "monitor.csv" to DiagnosticsPage.MONITOR
        )
        expected.forEach { (id, page) ->
            assertEquals(id, page, DiagnosticsPage.initial(null, null, id))
        }
    }
}
