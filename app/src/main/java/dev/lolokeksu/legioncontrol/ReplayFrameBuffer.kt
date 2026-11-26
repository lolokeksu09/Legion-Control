package dev.lolokeksu.legioncontrol

import java.util.ArrayDeque

/**
 * Memory-bounded H.264 replay window. Immutable encoded access units only.
 * Time is the codec's presentation timestamp, NEVER wall clock or display Hz.
 * The retained head is always a sync frame; trimming may shorten available history.
 */
data class ReplayFrame(val bytes: ByteArray, val ptsUs: Long, val keyFrame: Boolean)

class ReplayFrameBuffer(
    private val windowSeconds: Int,
    byteLimit: Long = ReplayMemoryPolicy.MAX_VIDEO_BYTES
) {
    init {
        require(windowSeconds in setOf(30, 60, 120))
        require(byteLimit > 0L)
    }

    private val frames = ArrayDeque<ReplayFrame>()
    private var byteCount = 0L
    private var byteLimit = byteLimit
    val sizeBytes: Long get() = synchronized(this) { byteCount }
    val byteLimitBytes: Long get() = synchronized(this) { byteLimit }
    val frameCount: Int get() = synchronized(this) { frames.size }

    @Synchronized fun add(frame: ReplayFrame): Boolean {
        if (frame.bytes.isEmpty() || frame.ptsUs < 0 ||
            frame.bytes.size.toLong() > byteLimit) return false
        if (frames.isEmpty() && !frame.keyFrame) return false
        if (frames.isNotEmpty() && frame.ptsUs <= frames.last.ptsUs) return false

        frames.addLast(frame)
        byteCount += frame.bytes.size
        trim(frame.ptsUs)
        return true
    }

    /** Reduce the cap under memory pressure without retaining an undecodable GOP prefix. */
    @Synchronized fun reduceByteLimit(newLimit: Long) {
        require(newLimit > 0L)
        if (newLimit >= byteLimit) return
        byteLimit = newLimit
        if (frames.isNotEmpty()) trim(frames.last.ptsUs)
    }

    private fun trim(latestPtsUs: Long) {
        val threshold = latestPtsUs - windowSeconds.toLong() * 1_000_000L
        while (frames.isNotEmpty() &&
            (byteCount > byteLimit || frames.first.ptsUs < threshold) && hasSecondKey()) {
            evictThroughNextKey()
        }
        // A single GOP larger than the budget is not safe to keep or export.
        if (byteCount > byteLimit) clear()
    }

    private fun hasSecondKey(): Boolean =
        frames.asSequence().drop(1).any { it.keyFrame }

    private fun evictThroughNextKey() {
        if (frames.isEmpty()) return
        removeFirst()
        while (frames.isNotEmpty() && !frames.first.keyFrame) removeFirst()
    }

    private fun removeFirst() {
        byteCount -= frames.removeFirst().bytes.size
    }

    /** Holds references to immutable samples, not copies of encoded byte arrays. */
    @Synchronized fun snapshot(): List<ReplayFrame> {
        if (frames.size < 2) return emptyList()
        val copy = frames.toList()
        if (copy.last().ptsUs - copy.first().ptsUs < 1_000_000L) return emptyList()
        check(copy.first().keyFrame)
        return copy
    }

    /** Snapshot bounded against SAVE time, not just the last generated video frame. */
    @Synchronized fun snapshotAt(videoNowUs: Long): List<ReplayFrame> {
        if (videoNowUs < 0L) return emptyList()
        val available = snapshot()
        if (available.isEmpty()) return emptyList()
        val oldestUs = (videoNowUs - windowSeconds.toLong() * 1_000_000L).coerceAtLeast(0L)
        if (available.last().ptsUs < oldestUs) return emptyList()
        val start = available.indexOfFirst { it.keyFrame && it.ptsUs >= oldestUs }
        if (start < 0) return emptyList()
        val clipped = available.drop(start)
        return if (clipped.size >= 2 &&
            clipped.last().ptsUs - clipped.first().ptsUs >= 1_000_000L) clipped else emptyList()
    }

    @Synchronized fun clear() {
        frames.clear()
        byteCount = 0L
    }
}
