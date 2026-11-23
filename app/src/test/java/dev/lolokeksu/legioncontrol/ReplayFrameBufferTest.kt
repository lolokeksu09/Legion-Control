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
