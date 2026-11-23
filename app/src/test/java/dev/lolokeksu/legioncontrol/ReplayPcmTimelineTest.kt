package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ReplayPcmTimelineTest {
    @Test fun usesAudioRecordMonotonicCaptureTimestampAndFramePosition() {
        val clock = ReplayPcmTimeline(48_000, 4)
        val first = clock.packetStartUs(1920, 1_000_010_000_000L,
            ReplayFrameClock(480L, 1_000_010_000_000L))
        assertEquals(1_000_000_000L, first)
        val second = clock.packetStartUs(1920, 1_000_020_000_000L,
            ReplayFrameClock(960L, 1_000_020_000_000L))
        assertEquals(1_000_010_000L, second)
        assertEquals(960L, clock.totalFrames)
    }

    @Test fun noPcmMeansNoCodecInputAndNoClockAdvance() {
        val clock = ReplayPcmTimeline(48_000, 4)
        assertNull(clock.packetStartUs(0, 2_000_000_000L, null))
        assertEquals(0L, clock.totalFrames)
        assertEquals(1_990_000L, clock.packetStartUs(1920, 2_000_000_000L, null))
        assertEquals(480L, clock.totalFrames)
    }

    @Test fun delayedReadAndTimestampJitterDoNotCauseCumulativeDrift() {
        val timeline = ReplayPcmTimeline(48_000, 4)
        val begin = 10_000_000_000L
        for (i in 0 until 12_000) {
            // 480 PCM frames = 10 ms. Read completion may be late or early.
            val expectedNs = begin + (i + 1L) * 10_000_000L
            val jitterNs = if (i % 2 == 0) 45_000_000L else -35_000_000L
            val pts = timeline.packetStartUs(1920, expectedNs + jitterNs, null)
            assertEquals(begin / 1_000L + i * 10_000L, pts)
        }
        assertEquals(12_000L * 480L, timeline.totalFrames)
    }

    @Test fun badHardwareTimestampsCannotShiftClockBackwards() {
        val clock = ReplayPcmTimeline(48_000, 4)
        val first = clock.packetStartUs(1920, 1_000_010_000_000L,
            ReplayFrameClock(480L, 1_000_010_000_000L))!!
        val second = clock.packetStartUs(1920, 1_000_020_000_000L,
            ReplayFrameClock(-1L, Long.MAX_VALUE))!!
        val third = clock.packetStartUs(1920, 1_000_030_000_000L,
            ReplayFrameClock(1440L, 999_000_000_000L))!!
        assertTrue(first < second && second < third)
        assertEquals(first + 20_000L, third)
    }

    @Test fun refusesPartialPcmFramesAndExcessiveTimestampAge() {
        val clock = ReplayPcmTimeline(48_000, 4)
        assertThrows(IllegalArgumentException::class.java) {
            clock.packetStartUs(3, 1_000_000_000L, null)
        }
        val first = clock.packetStartUs(1920, 2_000_000_000L,
            ReplayFrameClock(480L, 100L))
        assertEquals(1_990_000L, first)
    }
}
