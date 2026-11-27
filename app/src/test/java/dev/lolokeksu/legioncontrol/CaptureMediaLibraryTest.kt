package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class CaptureMediaLibraryTest {
    @Test fun filenameSearchTreatsWildcardsAsLiteralCharacters() {
        assertEquals("%LegionClip!_12!%!!%", CaptureMediaLibrary.searchPattern(" LegionClip_12%! "))
        assertEquals("%" + "x".repeat(CaptureMediaLibrary.MAX_SEARCH_LENGTH) + "%",
            CaptureMediaLibrary.searchPattern("x".repeat(CaptureMediaLibrary.MAX_SEARCH_LENGTH + 10)))
    }

    @Test fun captureFormatsMatchNamesTheRecordersProduce() {
        assertTrue(CaptureMediaFormat.MP4.acceptsName("LegionClip_1795704500000.mp4"))
        assertTrue(CaptureMediaFormat.PNG.acceptsName("LegionShot_1795704500000.png"))
        assertFalse(CaptureMediaFormat.PNG.acceptsName("LegionClip_1795704500000.png"))
        assertFalse(CaptureMediaFormat.MP4.acceptsName("LegionClip_1795704500000-copy.mp4"))
    }

    @Test fun mediaDestinationsRevealTheRelevantPanel() {
        assertEquals(CaptureStudioPane.LIBRARY, CaptureStudioPane.initial(null, "LIBRARY", null))
        assertEquals(CaptureStudioPane.SETTINGS, CaptureStudioPane.initial(null, null, "capture.audio"))
        assertEquals(CaptureStudioPane.SETTINGS, CaptureStudioPane.initial(null, null, "capture.restore"))
        assertEquals(CaptureStudioPane.CAPTURE, CaptureStudioPane.initial(null, "LIBRARY", "capture.replay"))
        assertEquals(CaptureStudioPane.LIBRARY, CaptureStudioPane.initial("LIBRARY", null, "capture.replay"))
    }
}
