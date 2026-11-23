package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ReplayVideoClockTest {
    @Test fun encoderDelayIsNotConfusedWithTimebaseOffset() {
        assertEquals(0L, ReplayVideoClock.offsetFromDequeue(10_000_000L, 10_160_000L))
        assertEquals(0L, ReplayVideoClock.offsetFromDequeue(100_000_000L, 100_420_000L))
        assertEquals(0L, ReplayVideoClock.offsetFromDequeue(5_500_000L, 5_500_000L))
    }

    @Test fun supportsVendorStreamRelativePtsWithExplicitFallback() {
        assertEquals(-90_000_000L,
            ReplayVideoClock.offsetFromDequeue(10_000_000L, 100_000_000L))
    }

    @Test fun refusesInvalidClockSamples() {
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVideoClock.offsetFromDequeue(-1, 100)
        }
    }
}
