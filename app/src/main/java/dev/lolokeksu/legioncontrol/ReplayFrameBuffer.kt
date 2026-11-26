package dev.lolokeksu.legioncontrol

import java.util.ArrayDeque

/**
 * Memory-bounded H.264 replay window. Immutable encoded access units only.
 * Time is the codec's presentation timestamp, NEVER wall clock or display Hz.
 * The retained head is always a sync frame; trimming may shorten available history.
 */
data class ReplayFrame(val bytes: ByteArray, val ptsUs: Long, val keyFrame: Boolean)

/** Consistent, read-only accounting of retained compressed video, not process RAM. */
data class ReplayWindowStats(val frameCount: Int, val retainedDurationUs: Long,
                             val encodedBytes: Long, val byteLimit: Long)

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
    private var keyFrameCount = 0
    private var byteLimit = byteLimit
    val sizeBytes: Long get() = synchronized(this) { byteCount }
    val byteLimitBytes: Long get() = synchronized(this) { byteLimit }
    val frameCount: Int get() = synchronized(this) { frames.size }

    @Synchronized fun stats(): ReplayWindowStats = ReplayWindowStats(
        frames.size,
        if (frames.size < 2) 0L else frames.last.ptsUs - frames.first.ptsUs,
        byteCount, byteLimit
    )

    @Synchronized fun add(frame: ReplayFrame): Boolean {
        if (frame.bytes.isEmpty() || frame.ptsUs < 0 ||
            frame.bytes.size.toLong() > byteLimit) return false
        if (frames.isEmpty() && !frame.keyFrame) return false
        if (frames.isNotEmpty() && frame.ptsUs <= frames.last.ptsUs) return false

        frames.addLast(frame)
        byteCount += frame.bytes.size
        if (frame.keyFrame) keyFrameCount++
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

    private fun hasSecondKey(): Boolean = keyFrameCount > 1

    private fun evictThroughNextKey() {
        if (frames.isEmpty()) return
        removeFirst()
        while (frames.isNotEmpty() && !frames.first.keyFrame) removeFirst()
    }

    private fun removeFirst() {
        val removed = frames.removeFirst()
        byteCount -= removed.bytes.size
        if (removed.keyFrame) keyFrameCount--
    }

    /** Holds references to immutable samples, not copies of encoded byte arrays. */
    @Synchronized fun snapshot(): List<ReplayFrame> {
        if (frames.size < 2) return emptyList()
        if (frames.last.ptsUs - frames.first.ptsUs < 1_000_000L) return emptyList()
        check(frames.first.keyFrame)
        return frames.toList()
    }

    /** Snapshot bounded against SAVE time, not just the last generated video frame. */
    @Synchronized fun snapshotAt(videoNowUs: Long): List<ReplayFrame> {
        if (videoNowUs < 0L) return emptyList()
        if (frames.size < 2) return emptyList()
        val oldestUs = (videoNowUs - windowSeconds.toLong() * 1_000_000L).coerceAtLeast(0L)
        val latestUs = frames.last.ptsUs
        if (latestUs < oldestUs) return emptyList()
        val iterator = frames.iterator()
        while (iterator.hasNext()) {
            val first = iterator.next()
            if (!first.keyFrame || first.ptsUs < oldestUs) continue
            if (latestUs - first.ptsUs < 1_000_000L) return emptyList()
            // Allocate only one list, and only for a valid window.
            val result = ArrayList<ReplayFrame>()
            result.add(first)
            while (iterator.hasNext()) result.add(iterator.next())
            return result
        }
        return emptyList()
    }

    @Synchronized fun clear() {
        frames.clear()
        byteCount = 0L
        keyFrameCount = 0
    }
}
