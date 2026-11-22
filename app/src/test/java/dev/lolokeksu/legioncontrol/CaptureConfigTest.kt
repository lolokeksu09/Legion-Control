package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureConfigTest {
    @Test fun clampsUntrustedIntentOptions() {
        val invalid = CaptureConfig.fromOptions(4096, 240, 200, 900)
        assertEquals(1280, invalid.maxEdge)
        assertEquals(30, invalid.fps)
        assertEquals(4_000_000, invalid.videoBitrate)
        assertEquals(60_000L, invalid.maxDurationMs)
    }

    @Test fun supportsExplicitHighPreset() {
        val high = CaptureConfig.fromOptions(1920, 60, 12, 15)
        assertEquals(1920, high.maxEdge)
        assertEquals(60, high.fps)
        assertEquals(12_000_000, high.videoBitrate)
        assertEquals(900_000L, high.maxDurationMs)
    }

    @Test fun keepsOrientationAspectAndEvenDimensions() {
        val tall = CaptureConfig.scaledSize(1440, 3168, 1920)
        assertEquals(872, tall.first)
        assertEquals(1920, tall.second)
        val wide = CaptureConfig.scaledSize(3168, 1440, 1280)
        assertEquals(1280, wide.first)
        assertEquals(582, wide.second)
        assertEquals(0, tall.first % 2)
        assertEquals(0, wide.second % 2)
    }

    @Test fun doesNotUpscaleSmallScreens() {
        val result = CaptureConfig.scaledSize(360, 640, 1920)
        assertEquals(360, result.first)
        assertEquals(640, result.second)
    }

    @Test fun rejectsInvalidScreenDimension() {
        var thrown = false
        try { CaptureConfig.scaledSize(0, 1000, 1280) }
        catch (_: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)
    }

    @Test fun evenMinimumAndFileNamingAreBounded() {
        assertEquals(Pair(2, 2), CaptureConfig.scaledSize(1, 1, 1280))
        assertTrue(CaptureConfig.fileName(42L).matches(
            Regex("LegionClip_[0-9]{13}\\.mp4")))
        assertFalse(CaptureConfig.fileName(42L).contains("/"))
    }
}
