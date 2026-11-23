package dev.lolokeksu.legioncontrol

import java.util.ArrayDeque

/**
 * Memory-bounded H.264 replay window. Immutable encoded access units only.
 * Time is the codec's presentation timestamp, NEVER wall clock or display Hz.
 *
 * The retained head is always a sync frame. Trimming may shorten the available
 * history; callers must not claim that a full requested duration was captured.
 */
data class ReplayFrame(val bytes: ByteArray, val ptsUs: Long, val keyFrame: Boolean)

class ReplayFrameBuffer(
    private val windowSeconds: Int,
    private val byteLimit: Long = 80L * 1024L * 1024L
) {
    init {
        require(windowSeconds in setOf(30, 60, 120))
        require(byteLimit > 0L)
    }

    private val frames = ArrayDeque<ReplayFrame>()
    private var byteCount = 0L
    val sizeBytes: Long get() = byteCount
    val frameCount: Int get() = frames.size

    fun add(frame: ReplayFrame): Boolean {
        if (frame.bytes.isEmpty() || frame.ptsUs < 0 ||
            frame.bytes.size.toLong() > byteLimit) return false
        if (frames.isEmpty() && !frame.keyFrame) return false
        if (frames.isNotEmpty() && frame.ptsUs <= frames.last.ptsUs) return false

        frames.addLast(frame)
        byteCount += frame.bytes.size
        val threshold = frame.ptsUs - windowSeconds.toLong() * 1_000_000L
        while (frames.isNotEmpty() &&
            (byteCount > byteLimit || frames.first.ptsUs < threshold) && hasSecondKey()) {
            evictThroughNextKey()
        }
        // One oversized group with no next keyframe is not safe to keep or export.
        if (byteCount > byteLimit) clear()
        return true
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

    /** Returns original immutable frame references; the caller holds its own list. */
    fun snapshot(): List<ReplayFrame> {
        if (frames.size < 2) return emptyList()
        val copy = frames.toList()
        if (copy.last().ptsUs - copy.first().ptsUs < 1_000_000L) return emptyList()
        check(copy.first().keyFrame)
        return copy
    }

    /** Snapshot bounded against SAVE time, not just the last generated video frame. */
    fun snapshotAt(videoNowUs: Long): List<ReplayFrame> {
        if (videoNowUs < 0L) return emptyList()
        val available = snapshot()
        if (available.isEmpty()) return emptyList()
        val oldestUs = (videoNowUs - windowSeconds.toLong() * 1_000_000L)
            .coerceAtLeast(0L)
        if (available.last().ptsUs < oldestUs) return emptyList()
        val start = available.indexOfFirst { it.keyFrame && it.ptsUs >= oldestUs }
        if (start < 0) return emptyList()
        val clipped = available.drop(start)
        return if (clipped.size >= 2 &&
            clipped.last().ptsUs - clipped.first().ptsUs >= 1_000_000L)
            clipped else emptyList()
    }

    fun clear() {
        frames.clear()
        byteCount = 0L
    }
}
