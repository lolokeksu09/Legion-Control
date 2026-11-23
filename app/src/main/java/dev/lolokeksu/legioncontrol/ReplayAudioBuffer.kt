package dev.lolokeksu.legioncontrol

import java.util.ArrayDeque

/** Immutable AAC packet with presentation time on the System.nanoTime() clock. */
data class ReplayAudioPacket(val encoded: ByteArray, val ptsUs: Long)

/**
 * A single-producer/single-reader bound for opt-in captured game playback audio.
 * Codec packets and the encoder output format are immutable after publication.
 * A missing or incompatible audio track never blocks a video-only replay export.
 */
class ReplayAudioBuffer(
    private val seconds: Int,
    private val maxBytes: Long = 8L * 1024L * 1024L
) {
    init {
        require(seconds in setOf(30, 60, 120))
        require(maxBytes > 0)
    }

    private val packets = ArrayDeque<ReplayAudioPacket>()
    private var size = 0L
    val sizeBytes: Long get() = synchronized(this) { size }

    @Synchronized fun append(packet: ReplayAudioPacket): Boolean {
        if (packet.encoded.isEmpty() || packet.ptsUs < 0 ||
            packet.encoded.size > 256 * 1024 ||
            packet.encoded.size.toLong() > maxBytes ||
            (packets.isNotEmpty() && packet.ptsUs <= packets.last.ptsUs)) return false
        packets.addLast(packet)
        size += packet.encoded.size
        val oldestPts = packet.ptsUs - seconds * 1_000_000L
        while (packets.isNotEmpty() && (size > maxBytes || packets.first.ptsUs < oldestPts)) {
            size -= packets.removeFirst().encoded.size
        }
        return true
    }

    /** No copying of sample bytes; recorder never mutates published arrays. */
    @Synchronized fun snapshot(startUs: Long, endUs: Long): List<ReplayAudioPacket> {
        if (startUs < 0 || endUs <= startUs) return emptyList()
        return packets.filter { it.ptsUs in startUs until endUs }
    }

    @Synchronized fun clear() {
        packets.clear()
        size = 0L
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
