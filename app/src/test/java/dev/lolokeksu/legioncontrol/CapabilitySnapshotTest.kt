package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests only the pure validation logic; Android API integration needs a device. */
class CapabilitySnapshotTest {
    @Test fun thermalStatusKeepsReportedStatesAndRejectsUnknownCodes() {
        for (status in 0..6) assertEquals(status, CapabilitySanitizer.thermalStatus(status))
        assertNull(CapabilitySanitizer.thermalStatus(null))
        assertNull(CapabilitySanitizer.thermalStatus(-1))
        assertNull(CapabilitySanitizer.thermalStatus(7))
    }

    @Test fun displayModesRejectMalformedOemValues() {
        assertEquals(
            ReportedDisplayMode(2, 1440, 3168, 120f),
            CapabilitySanitizer.displayMode(2, 1440, 3168, 120f)
        )
        for (rate in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -1f, 1001f)) {
            assertNull(CapabilitySanitizer.displayMode(2, 1440, 3168, rate))
        }
        assertNull(CapabilitySanitizer.displayMode(0, 1440, 3168, 60f))
        assertNull(CapabilitySanitizer.displayMode(2, 0, 3168, 60f))
        assertNull(CapabilitySanitizer.displayMode(2, 1440, -1, 60f))
        assertNull(CapabilitySanitizer.displayMode(2, 16385, 3168, 60f))
    }

    @Test fun supportedModeListDoesNotInventCurrentMode() {
        val sixty = ReportedDisplayMode(1, 1440, 3168, 60f)
        val oneTwenty = ReportedDisplayMode(2, 1440, 3168, 120f)
        assertEquals(
            listOf(sixty, oneTwenty),
            CapabilitySanitizer.supportedModes(listOf(oneTwenty, sixty, oneTwenty))
        )
        assertEquals(emptyList<ReportedDisplayMode>(), CapabilitySanitizer.supportedModes(emptyList()))
    }
}
