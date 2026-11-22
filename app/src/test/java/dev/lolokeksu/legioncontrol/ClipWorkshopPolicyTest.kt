package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipWorkshopPolicyTest {
    @Test fun lastFifteenSecondsClampsToAvailableDuration() {
        assertEquals(ClipWorkshopRange(15_000, 30_000),
            ClipWorkshopPolicy.last(30_000, 15_000))
        assertEquals(ClipWorkshopRange(0, 7_000),
            ClipWorkshopPolicy.last(7_000, 15_000))
    }

    @Test fun firstThirtySecondsClampsToAvailableDuration() {
        assertEquals(ClipWorkshopRange(0, 30_000),
            ClipWorkshopPolicy.first(50_000, 30_000))
        assertEquals(ClipWorkshopRange(0, 3_000),
            ClipWorkshopPolicy.first(3_000, 30_000))
    }

    @Test fun customRangeAllowsMinimumOneSecond() {
        assertEquals(ClipWorkshopRange(2_000, 3_000),
            ClipWorkshopPolicy.custom(10_000, 2_000, 3_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroLengthRangeIsRejected() {
        ClipWorkshopPolicy.custom(12_000, 3_000, 3_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfBoundsEndIsRejected() {
        ClipWorkshopPolicy.custom(12_000, 11_000, 13_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun exceedsFifteenMinuteExportCapIsRejected() {
        ClipWorkshopPolicy.custom(1_800_000, 0, 1_800_000)
    }

    @Test fun frameChoiceIsBoundedToRecordedDuration() {
        assertEquals(0L, ClipWorkshopPolicy.frameAt(2000, 0))
        assertEquals(1000L, ClipWorkshopPolicy.frameAt(2000, 1000))
        assertEquals(1000L, ClipWorkshopPolicy.middle(2000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun frameAtEndIsRejected() {
        ClipWorkshopPolicy.frameAt(15_000, 15_000)
    }

    @Test fun syncFrameBeforeRequestPreservesDecodableVideo() {
        assertEquals(8_000_000L,
            ClipWorkshopPolicy.requireSyncStartUs(9_500_000, 8_000_000, true))
        assertEquals(1_500_000L,
            ClipWorkshopPolicy.normalizePtsUs(9_500_000, 8_000_000))
    }

    @Test(expected = IllegalStateException::class)
    fun nonSyncFrameIsRejected() {
        ClipWorkshopPolicy.requireSyncStartUs(9_500_000, 8_000_000, false)
    }

    @Test(expected = IllegalStateException::class)
    fun futureFrameIsNotValidPreviousSync() {
        ClipWorkshopPolicy.requireSyncStartUs(9_500_000, 9_600_000, true)
    }

    @Test fun samplesOutsideSelectedRangeAreExcluded() {
        assertFalse(ClipWorkshopPolicy.includeSample(999L, 1000L, 2000L))
        assertTrue(ClipWorkshopPolicy.includeSample(1000L, 1000L, 2000L))
        assertTrue(ClipWorkshopPolicy.includeSample(1999L, 1000L, 2000L))
        assertFalse(ClipWorkshopPolicy.includeSample(2000L, 1000L, 2000L))
    }
}
