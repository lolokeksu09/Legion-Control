package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAudioTrackPolicyTest {
    @Test fun pcmFramesAreMonotonicAcrossPauses() {
        assertEquals(0L, PlaybackAudioTrackPolicy.ptsUs(0))
        assertEquals(1_000_000L, PlaybackAudioTrackPolicy.ptsUs(48_000))
        assertEquals(1_500_000L, PlaybackAudioTrackPolicy.ptsUs(72_000))
    }

    @Test fun byteCountRejectsIncompleteStereoFrames() {
        assertEquals(1024L, PlaybackAudioTrackPolicy.framesForBytes(4096))
        assertEquals(0L, PlaybackAudioTrackPolicy.framesForBytes(0))
        assertFalse(PlaybackAudioTrackPolicy.isAlignedPcmSize(9))
        assertTrue(PlaybackAudioTrackPolicy.isAlignedPcmSize(8192))
    }

    @Test fun muxTimestampsStartAtZeroPlusTrueCaptureOffset() {
        assertEquals(250_000L, PlaybackAudioTrackPolicy.muxPtsUs(1_250_000, 1_000_000, 0))
        assertEquals(170_000L, PlaybackAudioTrackPolicy.muxPtsUs(1_150_000, 1_000_000, 20_000))
        assertEquals(0L, PlaybackAudioTrackPolicy.muxPtsUs(5, 20, 0))
    }

    @Test fun sourceUsageIsOnlyGameAndMedia() {
        assertEquals(2, PlaybackAudioTrackPolicy.PLAYBACK_USAGES.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeSampleFrames() { PlaybackAudioTrackPolicy.ptsUs(-1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeTimeOffset() { PlaybackAudioTrackPolicy.muxPtsUs(0, 0, -1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnalignedPcm() { PlaybackAudioTrackPolicy.framesForBytes(7) }
}
