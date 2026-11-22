package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendRefactorRulesTest {
    @Test fun acceptOnlyKnownCapturePresetValues() {
        for ((key, values) in mapOf(
            "edge" to listOf(1280,1920),
            "fps" to listOf(30,60),
            "mbit" to listOf(4,8,12),
            "minutes" to listOf(1,5,15)
        )) {
            for (value in values) assertTrue(CapturePresetRules.accepts(key, value))
        }
        for ((key, value) in listOf(
            "edge" to 720, "fps" to 120, "mbit" to 0,
            "minutes" to -1, "unknown" to 30, "" to 4
        )) assertFalse(CapturePresetRules.accepts(key, value))
    }

    @Test fun invalidPresetDoesNotBecomePreferenceEntry() {
        var rejected = false
        try { CapturePresetRules.requireSupported("fps", 240) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        CapturePresetRules.requireSupported("fps", 60)
    }

    @Test fun diagnosticReadKeysHaveDistinctPaneTargets() {
        assertEquals(6, DiagnosticsReadKind.entries.size)
        assertEquals(5, DiagnosticsReadKind.entries.map { it.pane }.toSet().size)
        assertEquals("analytics", DiagnosticsReadKind.SENSOR_QUALITY.pane)
        assertEquals("analytics", DiagnosticsReadKind.SESSION_PROFILE.pane)
    }
}
