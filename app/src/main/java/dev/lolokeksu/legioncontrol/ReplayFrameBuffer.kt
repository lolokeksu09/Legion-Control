package dev.lolokeksu.legioncontrol

import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * Memory-bounded H.264 replay window. Immutable encoded access units only.
 * Time is the codec's presentation timestamp, NEVER wall clock or display Hz.
 * The retained head is always a sync frame; trimming may shorten available history.
 */
data class ReplayFrame(val bytes: ByteArray, val ptsUs: Long, val keyFrame: Boolean)

/** Consistent, read-only accounting of retained compressed video, not process RAM. */
data class ReplayWindowStats(val frameCount: Int, val retainedDurationUs: Long,
                             val encodedBytes: Long, val byteLimit: Long,
                             val exportBytes: Long = 0L, val retainedBytes: Long = encodedBytes)

/** The exporter owns this immutable sample list until close(), even if live capture evicts it. */
internal class ReplayVideoLease internal constructor(
    val samples: List<ReplayFrame>,
    private val release: (ReplayVideoLease) -> Unit
) : AutoCloseable {
    override fun close() = release(this)
}

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
    private var exportLease: ReplayVideoLease? = null
    private val frozenFrames = IdentityHashMap<ReplayFrame, Boolean>()
    private var frozenBytes = 0L
    private var sharedBytes = 0L

    private fun retainedBytes(): Long = byteCount + frozenBytes - sharedBytes
    val sizeBytes: Long get() = synchronized(this) { byteCount }
    val byteLimitBytes: Long get() = synchronized(this) { byteLimit }
    val frameCount: Int get() = synchronized(this) { frames.size }

    @Synchronized fun stats(): ReplayWindowStats = ReplayWindowStats(
        frames.size,
        if (frames.size < 2) 0L else frames.last.ptsUs - frames.first.ptsUs,
        byteCount, byteLimit, frozenBytes, retainedBytes()
    )

    @Synchronized fun add(frame: ReplayFrame): Boolean {
        val shared = frozenFrames.containsKey(frame)
        if (!canAdmit(frame.bytes.size, frame.ptsUs, frame.keyFrame, shared)) return false
        appendAccepted(frame, shared)
        return true
    }

    /** Codec output stays in its native buffer until the rolling window can admit it. */
    @Synchronized internal fun addEncoded(
        size: Int, ptsUs: Long, keyFrame: Boolean, copyBytes: () -> ByteArray
    ): Boolean {
        if (!canAdmit(size, ptsUs, keyFrame, false)) return false
        val bytes = copyBytes()
        require(bytes.size == size)
        appendAccepted(ReplayFrame(bytes, ptsUs, keyFrame), false)
        return true
    }

    private fun canAdmit(size: Int, ptsUs: Long, keyFrame: Boolean, shared: Boolean): Boolean {
        if (size <= 0 || ptsUs < 0L) return false
        if (frames.isNotEmpty() && ptsUs <= frames.last.ptsUs) return false
        if (size.toLong() > byteLimit || (!shared && frozenBytes + size > byteLimit)) {
            // A missing access unit invalidates the live GOP. Wait for a new sync frame.
            clear()
            return false
        }
        if (frames.isEmpty() && !keyFrame) return false
        return true
    }

    private fun appendAccepted(frame: ReplayFrame, shared: Boolean) {
        frames.addLast(frame)
        byteCount += frame.bytes.size
        if (shared) sharedBytes += frame.bytes.size
        if (frame.keyFrame) keyFrameCount++
        trim(frame.ptsUs)
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
            (retainedBytes() > byteLimit || frames.first.ptsUs < threshold) && hasSecondKey()) {
            evictThroughNextKey()
        }
        // A single GOP larger than the budget is not safe to keep or export.
        if (retainedBytes() > byteLimit) clear()
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
        if (frozenFrames.containsKey(removed)) sharedBytes -= removed.bytes.size
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

    /**
     * Atomically reserve a SAVE window. Count shared references once; evicted
     * frozen samples remain charged until the single exporter releases them.
     */
    @Synchronized internal fun freezeAt(videoNowUs: Long): ReplayVideoLease? {
        if (exportLease != null) return null
        val samples = snapshotAt(videoNowUs)
        if (samples.isEmpty()) return null
        val lease = ReplayVideoLease(samples, ::releaseFrozen)
        for (frame in samples) frozenFrames[frame] = true
        frozenBytes = samples.sumOf { it.bytes.size.toLong() }
        sharedBytes = frozenBytes
        exportLease = lease
        return lease
    }

    @Synchronized private fun releaseFrozen(lease: ReplayVideoLease) {
        if (exportLease !== lease) return
        exportLease = null
        frozenFrames.clear()
        frozenBytes = 0L
        sharedBytes = 0L
    }

    @Synchronized fun clear() {
        frames.clear()
        byteCount = 0L
        keyFrameCount = 0
        sharedBytes = 0L
    }
}
