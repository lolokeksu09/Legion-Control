package dev.lolokeksu.legioncontrol

import java.util.ArrayDeque
import java.util.IdentityHashMap

/** Immutable AAC packet with presentation time on the System.nanoTime() clock. */
data class ReplayAudioPacket(val encoded: ByteArray, val ptsUs: Long)

/**
 * A single-producer/single-reader bound for opt-in captured game playback audio.
 * Codec packets and the encoder output format are immutable after publication.
 * A missing or incompatible audio track never blocks a video-only replay export.
 */
internal class ReplayAudioLease(
    val packets: List<ReplayAudioPacket>,
    private val release: (ReplayAudioLease) -> Unit
) : AutoCloseable {
    override fun close() = release(this)
}

internal data class ReplayAudioWindowStats(val liveBytes: Long, val exportBytes: Long,
                                           val retainedBytes: Long, val byteLimit: Long)

class ReplayAudioBuffer(
    private val seconds: Int,
    maxBytes: Long = ReplayMemoryPolicy.MAX_AUDIO_BYTES
) {
    init {
        require(seconds in setOf(30, 60, 120))
        require(maxBytes > 0)
    }

    private val packets = ArrayDeque<ReplayAudioPacket>()
    private var size = 0L
    private var maxBytes = maxBytes
    private var exportLease: ReplayAudioLease? = null
    private var frozenPackets = IdentityHashMap<ReplayAudioPacket, Boolean>()
    private var frozenBytes = 0L
    private var sharedBytes = 0L

    private fun retainedBytes(): Long = size + frozenBytes - sharedBytes

    @Synchronized internal fun stats() = ReplayAudioWindowStats(
        size, frozenBytes, retainedBytes(), maxBytes
    )

    val sizeBytes: Long get() = synchronized(this) { size }
    val byteLimitBytes: Long get() = synchronized(this) { maxBytes }

    @Synchronized fun append(packet: ReplayAudioPacket): Boolean {
        val shared = frozenPackets.containsKey(packet)
        if (!canAdmit(packet.encoded.size, packet.ptsUs, shared)) return false
        appendAccepted(packet, shared)
        return true
    }

    /** Do not allocate a Java packet when an active export consumes the budget. */
    @Synchronized internal fun appendEncoded(size: Int, ptsUs: Long, copyBytes: () -> ByteArray): Boolean {
        if (!canAdmit(size, ptsUs, false)) return false
        val bytes = copyBytes()
        require(bytes.size == size)
        appendAccepted(ReplayAudioPacket(bytes, ptsUs), false)
        return true
    }

    private fun canAdmit(bytes: Int, ptsUs: Long, shared: Boolean): Boolean =
        bytes in 1..256 * 1024 && ptsUs >= 0L && bytes.toLong() <= maxBytes &&
            (shared || frozenBytes + bytes <= maxBytes) &&
            (packets.isEmpty() || ptsUs > packets.last.ptsUs)

    private fun appendAccepted(packet: ReplayAudioPacket, shared: Boolean) {
        packets.addLast(packet)
        size += packet.encoded.size
        if (shared) sharedBytes += packet.encoded.size
        val oldestPts = packet.ptsUs - seconds * 1_000_000L
        while (packets.isNotEmpty() &&
            (retainedBytes() > maxBytes || packets.first.ptsUs < oldestPts)) removeFirst()
    }

    private fun removeFirst() {
        val packet = packets.removeFirst()
        size -= packet.encoded.size
        if (frozenPackets.containsKey(packet)) sharedBytes -= packet.encoded.size
    }

    /** A reduced cap may be below the already frozen export; no new packet can grow it. */
    @Synchronized fun reduceByteLimit(newLimit: Long) {
        require(newLimit > 0L)
        if (newLimit >= maxBytes) return
        maxBytes = newLimit
        while (packets.isNotEmpty() && retainedBytes() > maxBytes) removeFirst()
    }

    /** No copying of sample bytes; recorder never mutates published arrays. */
    @Synchronized fun snapshot(startUs: Long, endUs: Long): List<ReplayAudioPacket> {
        if (startUs < 0 || endUs <= startUs) return emptyList()
        return packets.filter { it.ptsUs in startUs until endUs }
    }

    /** Filter against the video clock directly, without materializing the entire AAC window. */
    @Synchronized internal fun snapshotForVideo(startUs: Long, endUs: Long,
                                               offsetUs: Long): List<ReplayAudioPacket> {
        if (startUs < 0 || endUs <= startUs) return emptyList()
        return packets.filter { packet ->
            val pts = ReplayAudioTimeline.videoPts(packet.ptsUs, offsetUs)
            pts != null && pts >= startUs && pts < endUs
        }
    }

    /** A single export owns the selected immutable packets until close(). */
    @Synchronized internal fun freezeForVideo(startUs: Long, endUs: Long,
                                             offsetUs: Long): ReplayAudioLease? {
        if (exportLease != null) return null
        val selected = snapshotForVideo(startUs, endUs, offsetUs)
        if (selected.isEmpty()) return null
        val identity = IdentityHashMap<ReplayAudioPacket, Boolean>()
        selected.forEach { identity[it] = true }
        val lease = ReplayAudioLease(selected, ::releaseFrozen)
        frozenPackets = identity
        frozenBytes = selected.sumOf { it.encoded.size.toLong() }
        sharedBytes = frozenBytes
        exportLease = lease
        return lease
    }

    @Synchronized private fun releaseFrozen(lease: ReplayAudioLease) {
        if (exportLease !== lease) return
        exportLease = null
        frozenPackets.clear()
        frozenBytes = 0L
        sharedBytes = 0L
    }

    @Synchronized fun clear() {
        packets.clear()
        size = 0L
        sharedBytes = 0L
    }
}

/**
 * Converts an Android monotonic audio timestamp into a video codec PTS clock,
 * with a measured offset (video PTS - System.nanoTime at first video sample).
 *
 * The offset is an approximation. No claim is made of sample-accurate sync.
 */
object ReplayAudioTimeline {
    fun videoPts(audioClockUs: Long, offsetUs: Long): Long? {
        if (audioClockUs < 0 || offsetUs < -10_000_000_000_000L ||
            offsetUs > 10_000_000_000_000L) return null
        val shifted = runCatching { Math.addExact(audioClockUs, offsetUs) }
            .getOrNull() ?: return null
        return shifted.takeIf { it >= 0 }
    }
}
