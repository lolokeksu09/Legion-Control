package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureScreenshotPolicyTest {
    @Test fun boundsLandscapeAndPortrait() {
        val portrait = CaptureScreenshotPolicy.dimensions(1440, 3168)
        assertEquals(872 to 1920, portrait)
        assertEquals(1920 to 872, CaptureScreenshotPolicy.dimensions(3168, 1440))
    }

    @Test fun preservesSmallerScreens() {
        assertEquals(720 to 1280, CaptureScreenshotPolicy.dimensions(720, 1280))
    }

    @Test fun namesAreStableAndRestrictedToPng() {
        assertEquals("LegionShot_0000000000042.png", CaptureScreenshotPolicy.fileName(42))
        assertTrue(CaptureScreenshotPolicy.fileName(1795400000000).endsWith(".png"))
    }

    @Test fun paddedWidthCoversRowAlignment() {
        assertEquals(1088, CaptureScreenshotPolicy.paddedWidth(1080, 4, 4352))
        assertEquals(1080, CaptureScreenshotPolicy.paddedWidth(1080, 4, 4320))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesHugePaddedRow() {
        CaptureScreenshotPolicy.paddedWidth(1080, 4, 10_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesUnexpectedPixelFormat() {
        CaptureScreenshotPolicy.paddedWidth(1080, 2, 2160)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesInvalidFileTimestamp() {
        CaptureScreenshotPolicy.fileName(0)
    }
}
