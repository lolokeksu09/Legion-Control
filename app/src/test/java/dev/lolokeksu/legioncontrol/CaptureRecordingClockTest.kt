package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRecordingClockTest {
    @Test fun activeRecordingCountsElapsedTime() {
        val clock = CaptureRecordingClock(60_000, 1000)
        assertEquals(5_000L, clock.activeElapsedMs(6000))
        assertEquals(55_000L, clock.remainingMs(6000))
        assertFalse(clock.paused)
    }

    @Test fun pauseFreezesActiveTimeAndResumeExcludesPausedInterval() {
        val clock = CaptureRecordingClock(30_000, 1000)
        assertTrue(clock.pause(6000))
        assertTrue(clock.paused)
        assertEquals(5_000L, clock.activeElapsedMs(16_000))
        assertEquals(25_000L, clock.remainingMs(26_000))
        assertTrue(clock.resume(26_000))
        assertFalse(clock.paused)
        assertEquals(8_000L, clock.activeElapsedMs(29_000))
        assertEquals(22_000L, clock.remainingMs(29_000))
    }

    @Test fun twoPausesDoNotResetOrDoubleCountTime() {
        val clock = CaptureRecordingClock(50_000, 500)
        assertTrue(clock.pause(3_500))
        assertFalse(clock.pause(5_500))
        assertTrue(clock.resume(13_500))
        assertFalse(clock.resume(14_500))
        assertTrue(clock.pause(15_500))
        assertTrue(clock.resume(35_500))
        assertEquals(5_000L, clock.activeElapsedMs(35_500))
        assertEquals(45_000L, clock.remainingMs(35_500))
    }

    @Test fun activeDurationStopsAtLimitButPauseDoesNotConsumeIt() {
        val clock = CaptureRecordingClock(5_000, 0)
        assertTrue(clock.pause(4_000))
        assertEquals(1_000L, clock.remainingMs(120_000))
        assertTrue(clock.resume(120_000))
        assertEquals(0L, clock.remainingMs(121_000))
    }

    @Test fun stopFromPausedStateUsesOnlyEncodedDuration() {
        val clock = CaptureRecordingClock(60_000, 10_000)
        assertTrue(clock.pause(12_000))
        assertEquals(2_000L, clock.activeElapsedMs(400_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDuration() {
        CaptureRecordingClock(0, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsClockReversalAtResume() {
        val clock = CaptureRecordingClock(12_000, 100)
        clock.pause(400)
        clock.resume(399)
    }

    @Test fun capsRemainingAtZero() {
        val clock = CaptureRecordingClock(1000, 200)
        assertEquals(0L, clock.remainingMs(5000))
    }

    @Test fun pauseGracePeriodIsBounded() {
        assertEquals(300_000L, CaptureRecordingClock.MAX_PAUSE_MS)
    }
}
