package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsPageTest {
    @Test fun restoredPageWinsAndUnknownValuesFallBack() {
        assertEquals(DiagnosticsPage.HISTORY, DiagnosticsPage.initial("HISTORY", "MONITOR", "monitor.start"))
        assertEquals(DiagnosticsPage.OVERVIEW, DiagnosticsPage.initial("unknown", "bad", "unknown"))
        assertEquals(DiagnosticsPage.MONITOR, DiagnosticsPage.initial(null, "MONITOR", null))
    }

    @Test fun allEmbeddedFeatureRoutesResolveToTheirRealPage() {
        val modules=mapOf(
            FeatureModule.GAMING to DiagnosticsPage.GAMING,
            FeatureModule.HARDWARE to DiagnosticsPage.HARDWARE,
            FeatureModule.NETWORK to DiagnosticsPage.NETWORK,
            FeatureModule.COMPAT to DiagnosticsPage.COMPAT,
            FeatureModule.AUDIO to DiagnosticsPage.AUDIO,
            FeatureModule.HAPTIC to DiagnosticsPage.HAPTIC
        )
        for((module,page) in modules) {
            val entries=FeatureFinder.entries.filter { it.module==module }
            org.junit.Assert.assertTrue(entries.isNotEmpty())
            entries.forEach { assertEquals(it.id,page,DiagnosticsPage.initial(null,null,it.id)) }
            org.junit.Assert.assertTrue(page.isEmbeddedLab)
        }
        assertEquals(listOf(DiagnosticsPage.AUDIO,DiagnosticsPage.HAPTIC,DiagnosticsPage.DISPLAY),
            DiagnosticsPage.entries.filter { it.isPeripheral })
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
