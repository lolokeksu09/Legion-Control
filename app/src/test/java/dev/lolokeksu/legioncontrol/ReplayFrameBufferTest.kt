package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ReplayFrameBufferTest {
    private fun frame(second: Int, key: Boolean, bytes: Int = 20) =
        ReplayFrame(ByteArray(bytes) { 7 }, second.toLong() * 1_000_000L, key)

    @Test fun startsOnlyFromKeyframe() {
        val buffer = ReplayFrameBuffer(30)
        assertFalse(buffer.add(frame(0, false)))
        assertTrue(buffer.snapshot().isEmpty())
        assertTrue(buffer.add(frame(1, true)))
        assertTrue(buffer.add(frame(2, false)))
        assertEquals(2, buffer.snapshot().size)
        assertTrue(buffer.snapshot().first().keyFrame)
    }

    @Test fun trimsToNextSyncFrameWithoutRetainingUndecodablePrefix() {
        val buffer = ReplayFrameBuffer(30)
        for (s in 0..60) assertTrue(buffer.add(frame(s, s % 2 == 0)))
        val saved = buffer.snapshot()
        assertTrue(saved.isNotEmpty())
        assertTrue(saved.first().keyFrame)
        assertTrue(saved.first().ptsUs >= 30_000_000L)
        assertEquals(60_000_000L, saved.last().ptsUs)
    }

    @Test fun cappedBytesDiscardOnlyWholeKeyframeGroups() {
        val buffer = ReplayFrameBuffer(120, 65L)
        assertTrue(buffer.add(frame(0, true)))
        assertTrue(buffer.add(frame(1, false)))
        assertTrue(buffer.add(frame(2, true)))
        assertTrue(buffer.add(frame(3, false)))
        assertTrue(buffer.sizeBytes <= 65L)
        assertTrue(buffer.snapshot().first().keyFrame)
        assertEquals(2_000_000L, buffer.snapshot().first().ptsUs)
    }

    @Test fun cannotExceedByteCeilingWithoutAnotherKeyframe() {
        val buffer = ReplayFrameBuffer(120, 65L)
        assertTrue(buffer.add(frame(0, true, 50)))
        assertTrue(buffer.add(frame(1, false, 50)))
        assertEquals(0L, buffer.sizeBytes)
        assertTrue(buffer.snapshot().isEmpty())
        assertFalse(buffer.add(frame(2, false)))
        assertTrue(buffer.add(frame(3, true)))
    }

    @Test fun refusesInvalidOutOfOrderAndOversizedSamples() {
        val buffer = ReplayFrameBuffer(30, 100L)
        assertFalse(buffer.add(ReplayFrame(byteArrayOf(1), -1, true)))
        assertFalse(buffer.add(ReplayFrame(ByteArray(101), 0, true)))
        assertTrue(buffer.add(frame(1, true)))
        assertFalse(buffer.add(frame(1, false)))
        assertFalse(buffer.add(frame(0, true)))
    }

    @Test fun snapshotRemainsStableWhenNewFramesEvictOldFrames() {
        val buffer = ReplayFrameBuffer(30)
        for (s in 0..31) buffer.add(frame(s, s % 2 == 0))
        val previous = buffer.snapshot()
        for (s in 32..90) buffer.add(frame(s, s % 2 == 0))
        assertEquals(31_000_000L, previous.last().ptsUs)
        assertEquals(90_000_000L, buffer.snapshot().last().ptsUs)
        assertTrue(previous.first().keyFrame)
    }

    @Test fun saveTimeWindowNeverIncludesStaleKeyframes() {
        val buffer = ReplayFrameBuffer(30)
        for (sec in 0..65) buffer.add(frame(sec, sec % 2 == 0))
        val recent = buffer.snapshotAt(65_000_000L)
        assertEquals(36_000_000L, recent.first().ptsUs)
        assertTrue(recent.last().ptsUs <= 65_000_000L)
        assertTrue(buffer.snapshotAt(200_000_000L).isEmpty())
        assertTrue(buffer.snapshotAt(-1L).isEmpty())
    }

    @Test fun memoryPressureDropsWholeGopsAndNeverRaisesTheLimit() {
        val buffer = ReplayFrameBuffer(120, 200L)
        assertTrue(buffer.add(frame(0, true, 50)))
        assertTrue(buffer.add(frame(1, false, 50)))
        assertTrue(buffer.add(frame(2, true, 50)))
        assertTrue(buffer.add(frame(3, false, 50)))
        buffer.reduceByteLimit(100L)
        assertEquals(100L, buffer.sizeBytes)
        assertEquals(100L, buffer.byteLimitBytes)
        assertEquals(2_000_000L, buffer.snapshot().first().ptsUs)
        buffer.reduceByteLimit(150L)
        assertEquals(100L, buffer.byteLimitBytes)
    }

    @Test fun repeatedEvictionAndClearDoNotLeaveStaleSyncCount() {
        val buffer = ReplayFrameBuffer(120, 65L)
        for (start in 0..50 step 5) {
            buffer.add(frame(start, true))
            buffer.add(frame(start+1, false))
            buffer.add(frame(start+2, true))
            buffer.add(frame(start+3, false))
            assertEquals(40L, buffer.sizeBytes)
            assertEquals((start+2)*1_000_000L, buffer.snapshot().first().ptsUs)
            buffer.clear()
            assertTrue(buffer.add(frame(start+4, true, 50)))
            assertTrue(buffer.add(frame(start+5, false, 50)))
            assertEquals(0L, buffer.sizeBytes)
            assertFalse(buffer.add(frame(start+6, false)))
        }
    }

    @Test fun clippedSnapshotKeepsOnlyFreshGopAndStableReferences() {
        val buffer = ReplayFrameBuffer(30)
        for (s in 0..31) buffer.add(frame(s, s % 2 == 0))
        val clipped = buffer.snapshotAt(40_000_000L)
        assertEquals(10_000_000L, clipped.first().ptsUs)
        assertEquals(31_000_000L, clipped.last().ptsUs)
        assertTrue(clipped.first().keyFrame)
        buffer.clear()
        assertEquals(22, clipped.size)
        assertTrue(buffer.snapshotAt(40_000_000L).isEmpty())
    }

    @Test fun snapshotRejectsFreshWindowShorterThanOneSecond() {
        val buffer = ReplayFrameBuffer(30)
        buffer.add(frame(0, true))
        buffer.add(frame(2, false))
        buffer.add(frame(30, true))
        assertTrue(buffer.snapshotAt(60_000_000L).isEmpty())
    }

    @Test fun statsReflectRetainedFramesAndReducedLimit() {
        val buffer = ReplayFrameBuffer(30, 200L)
        assertEquals(ReplayWindowStats(0, 0L, 0L, 200L), buffer.stats())
        for (s in 0..3) buffer.add(frame(s, s % 2 == 0, 50))
        assertEquals(ReplayWindowStats(4, 3_000_000L, 200L, 200L), buffer.stats())
        buffer.reduceByteLimit(100L)
        assertEquals(ReplayWindowStats(2, 1_000_000L, 100L, 100L), buffer.stats())
        buffer.clear()
        assertEquals(ReplayWindowStats(0, 0L, 0L, 100L), buffer.stats())
    }

    @Test fun resetDropsOnlyInMemoryFrames() {
        val buffer = ReplayFrameBuffer(60)
        buffer.add(frame(0, true))
        buffer.add(frame(2, false))
        buffer.clear()
        assertEquals(0L, buffer.sizeBytes)
        assertEquals(0, buffer.frameCount)
        assertTrue(buffer.snapshot().isEmpty())
    }
}
