package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCaptureProfileTest {
    private val base = GameCaptureProfile(1280, 30, 4, 5, false)

    @Test fun everySupportedOptionCanBeChangedWithoutAffectingOthers() {
        val edge = base.setOption("edge", 1920)
        assertEquals(1920, edge.edge)
        assertEquals(base.fps, edge.fps)
        assertEquals(60, base.setOption("fps", 60).fps)
        assertEquals(12, base.setOption("mbit", 12).mbit)
        assertEquals(15, base.setOption("minutes", 15).minutes)
        assertFalse(base.playbackAudio)
        assertTrue(base.withPlaybackAudio(true).playbackAudio)
    }

    @Test fun captureConfigDoesNotModifyExistingRecorderPreset() {
        val c = base.setOption("edge", 1920).setOption("fps", 60)
            .setOption("mbit", 12).setOption("minutes", 15).toConfig()
        assertEquals(1920, c.maxEdge)
        assertEquals(60, c.fps)
        assertEquals(12_000_000, c.videoBitrate)
        assertEquals(900_000L, c.maxDurationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBitrateIsRejectedNotClampedDuringImport() {
        GameCaptureProfile(1280, 30, 128, 5, false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDurationIsRejectedNotClampedDuringImport() {
        GameCaptureProfile(1280, 30, 4, 0, false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownOptionCannotBecomeAKey() {
        base.setOption("game", 60)
    }

    @Test fun packageNamesAreValidatedBeforeStorage() {
        assertTrue(GameCaptureProfilePolicy.validPackage("com.riotgames.league.wildrift"))
        assertTrue(GameCaptureProfilePolicy.validPackage("com.example.game2"))
        assertFalse(GameCaptureProfilePolicy.validPackage("com.example/../x"))
        assertFalse(GameCaptureProfilePolicy.validPackage("com..example"))
        assertFalse(GameCaptureProfilePolicy.validPackage("com"))
        assertFalse(GameCaptureProfilePolicy.validPackage("com.example."))
        assertFalse(GameCaptureProfilePolicy.validPackage("com.example\nmalicious"))
        assertFalse(GameCaptureProfilePolicy.validPackage("a." + "a".repeat(200)))
    }

    @Test fun snapshotsKeepGameAndAudioConsistentDuringConsent() {
        val snap = ActiveGameCaptureSettings("com.example.game", base.withPlaybackAudio(true), true)
        assertEquals("com.example.game", snap.selectedGame)
        assertTrue(snap.profile.playbackAudio)
        assertTrue(snap.usingGameProfile)
        assertEquals(5, snap.profile.minutes)
    }

    @Test fun unconfiguredGameHasExplicitGlobalFallback() {
        val snap = ActiveGameCaptureSettings("com.example.game", base, false)
        assertFalse(snap.usingGameProfile)
        assertEquals(4, snap.profile.mbit)
    }
}
