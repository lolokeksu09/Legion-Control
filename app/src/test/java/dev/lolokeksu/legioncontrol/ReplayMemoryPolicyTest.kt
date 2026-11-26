package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayMemoryPolicyTest {
    private val mib = 1024L * 1024L

    @Test fun videoBudgetScalesWithHeapAndLowRamClass() {
        assertEquals(32L * mib, ReplayMemoryPolicy.videoByteLimit(256L * mib, false))
        assertEquals(16L * mib, ReplayMemoryPolicy.videoByteLimit(256L * mib, true))
        assertEquals(80L * mib, ReplayMemoryPolicy.videoByteLimit(2L * 1024L * mib, false))
    }

    @Test fun audioBudgetIsSmallerAndHeapRelative() {
        assertEquals(2L * mib, ReplayMemoryPolicy.audioByteLimit(256L * mib, false))
        assertEquals(1L * mib, ReplayMemoryPolicy.audioByteLimit(256L * mib, true))
        assertEquals(8L * mib, ReplayMemoryPolicy.audioByteLimit(2L * 1024L * mib, false))
    }

    @Test fun trimNeverRaisesTheCurrentBudget() {
        assertEquals(16L * mib, ReplayMemoryPolicy.afterVideoTrim(32L * mib))
        assertEquals(8L * mib, ReplayMemoryPolicy.afterVideoTrim(8L * mib))
        assertEquals(2L * mib, ReplayMemoryPolicy.afterAudioTrim(4L * mib))
    }

    @Test fun rejectsInvalidHeapAndCurrentBudgets() {
        assertTrue(runCatching { ReplayMemoryPolicy.videoByteLimit(0, false) }.isFailure)
        assertTrue(runCatching { ReplayMemoryPolicy.audioByteLimit(-1, false) }.isFailure)
        assertTrue(runCatching { ReplayMemoryPolicy.afterVideoTrim(1) }.isFailure)
    }
}
