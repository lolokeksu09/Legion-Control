package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ReplayAudioBufferTest {
    private fun packet(sec: Long, bytes: Int = 2000) =
        ReplayAudioPacket(ByteArray(bytes) { 0x35 }, sec * 1_000_000)

    @Test fun trimsAudioByTimeWithoutExceedingRetentionWindow() {
        val buffer = ReplayAudioBuffer(30)
        for (sec in 0L..65L) assertTrue(buffer.append(packet(sec)))
        val result = buffer.snapshot(0L, 100_000_000)
        assertEquals(35_000_000L, result.first().ptsUs)
        assertEquals(65_000_000L, result.last().ptsUs)
        assertTrue(buffer.sizeBytes <= 31L * 2000L)
    }

    @Test fun boundedBytesNeverKeepOversizedPacket() {
        val buffer = ReplayAudioBuffer(120, 2500L)
        assertTrue(buffer.append(packet(1, 2000)))
        assertTrue(buffer.append(packet(2, 2000)))
        assertEquals(2000L, buffer.sizeBytes)
        assertEquals(2_000_000L, buffer.snapshot(0, 5_000_000).first().ptsUs)
        assertFalse(buffer.append(packet(3, 3000)))
        assertEquals(2000L, buffer.sizeBytes)
    }

    @Test fun rejectsBadOrOutOfOrderPackets() {
        val buffer = ReplayAudioBuffer(60)
        assertFalse(buffer.append(ReplayAudioPacket(byteArrayOf(1), -1)))
        assertFalse(buffer.append(ReplayAudioPacket(ByteArray(0), 0)))
        assertFalse(buffer.append(ReplayAudioPacket(ByteArray(256 * 1024 + 1), 0)))
        assertTrue(buffer.append(packet(4)))
        assertFalse(buffer.append(packet(4)))
        assertFalse(buffer.append(packet(1)))
    }

    @Test fun freezeRetainsDataAfterRingMovesAndClears() {
        val buffer = ReplayAudioBuffer(30)
        assertTrue(buffer.append(packet(10)))
        val frozen = buffer.snapshot(0, 11_000_000)
        for (sec in 11L..70L) buffer.append(packet(sec))
        buffer.clear()
        assertEquals(10_000_000L, frozen.single().ptsUs)
        assertTrue(buffer.snapshot(0, 100_000_000).isEmpty())
    }

    @Test fun memoryPressureDropsOldestAudioPacketsAndKeepsTheNewBudget() {
        val buffer = ReplayAudioBuffer(60, 6000L)
        for (second in 1L..3L) assertTrue(buffer.append(packet(second, 2000)))
        val frozen = buffer.snapshot(0L, 4_000_000L)
        buffer.reduceByteLimit(2000L)
        assertEquals(2000L, buffer.sizeBytes)
        assertEquals(2000L, buffer.byteLimitBytes)
        assertEquals(3_000_000L, buffer.snapshot(0L, 4_000_000L).single().ptsUs)
        assertEquals(3, frozen.size)
    }

    @Test fun clockAdjustmentAndInvalidRanges() {
        assertEquals(1_500_000L, ReplayAudioTimeline.videoPts(1_000_000L, 500_000L))
        assertEquals(500_000L, ReplayAudioTimeline.videoPts(1_000_000L, -500_000L))
        assertNull(ReplayAudioTimeline.videoPts(100L, -500_000L))
        assertNull(ReplayAudioTimeline.videoPts(-1L, 0))
        val buffer = ReplayAudioBuffer(30)
        buffer.append(packet(1))
        assertTrue(buffer.snapshot(2_000_000, 1_000_000).isEmpty())
    }

    @Test fun videoClockSnapshotSelectsOnlyMatchingPackets() {
        val buffer = ReplayAudioBuffer(30, 100L)
        val first = ReplayAudioPacket(byteArrayOf(1), 100L)
        val selected = ReplayAudioPacket(byteArrayOf(2), 200L)
        val last = ReplayAudioPacket(byteArrayOf(3), 300L)
        listOf(first, selected, last).forEach { assertTrue(buffer.append(it)) }
        val result = buffer.snapshotForVideo(250L, 350L, 100L)
        assertEquals(listOf(selected), result)
        assertSame(selected, result.single())
        assertTrue(buffer.snapshotForVideo(350L, 250L, 100L).isEmpty())
    }


    @Test fun liveAndFrozenAudioShareOneBudget() {
        val buffer = ReplayAudioBuffer(30, 6000L)
        for (s in 1L..2L) assertTrue(buffer.append(packet(s)))
        val lease = requireNotNull(buffer.freezeForVideo(0L, 3_000_000L, 0L))
        assertEquals(ReplayAudioWindowStats(4000L, 4000L, 4000L, 6000L), buffer.stats())
        assertNull(buffer.freezeForVideo(0L, 3_000_000L, 0L))
        for (s in 3L..50L) {
            assertTrue(buffer.append(packet(s)))
            assertTrue(buffer.stats().retainedBytes <= 6000L)
        }
        assertEquals(listOf(1_000_000L, 2_000_000L), lease.packets.map { it.ptsUs })
        lease.close()
        assertEquals(buffer.sizeBytes, buffer.stats().retainedBytes)
        val next = requireNotNull(buffer.freezeForVideo(0L, 51_000_000L, 0L))
        lease.close()
        assertTrue(buffer.stats().exportBytes > 0)
        next.close()
        assertEquals(0L, buffer.stats().exportBytes)
    }

    @Test fun trimmingAndProducerShutdownKeepOnlyOwnedAudioUntilRelease() {
        val buffer = ReplayAudioBuffer(30, 6000L)
        for (s in 1L..3L) buffer.append(packet(s))
        val lease = requireNotNull(buffer.freezeForVideo(0L, 4_000_000L, 0L))
        buffer.reduceByteLimit(2000L)
        assertEquals(0L, buffer.sizeBytes)
        assertEquals(6000L, buffer.stats().retainedBytes)
        assertFalse(buffer.appendEncoded(2000, 4_000_000L) { error("must not allocate") })
        buffer.clear()
        assertEquals(3, lease.packets.size)
        assertEquals(6000L, buffer.stats().retainedBytes)
        lease.close()
        assertEquals(0L, buffer.stats().retainedBytes)
        assertTrue(buffer.append(packet(5)))
        assertEquals(2000L, buffer.stats().retainedBytes)
    }

    @Test fun rejectedAacPacketsDoNotInvokeCopyAndPtsStayOriginal() {
        val buffer = ReplayAudioBuffer(30, 4000L)
        buffer.append(packet(1))
        buffer.append(packet(2))
        val lease = requireNotNull(buffer.freezeForVideo(0L, 3_000_000L, 0L))
        var copies = 0
        fun copy(): ByteArray { copies++; return ByteArray(2000) }
        assertFalse(buffer.appendEncoded(2000, 3_000_000L, ::copy))
        assertEquals(0, copies)
        lease.close()
        assertTrue(buffer.appendEncoded(2000, 4_000_000L, ::copy))
        assertFalse(buffer.appendEncoded(2000, 4_000_000L, ::copy))
        assertEquals(1, copies)
        assertEquals(4_000_000L, buffer.snapshot(0L, 5_000_000L).last().ptsUs)
    }

}
