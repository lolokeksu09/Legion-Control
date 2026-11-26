package dev.lolokeksu.legioncontrol

/** Heap-relative ceilings for compressed rolling buffers; these are budgets, not PSS measurements. */
object ReplayMemoryPolicy {
    private const val MIB = 1024L * 1024L
    const val MIN_VIDEO_BYTES = 8L * MIB
    const val MAX_VIDEO_BYTES = 80L * MIB
    const val MIN_AUDIO_BYTES = 1L * MIB
    const val MAX_AUDIO_BYTES = 8L * MIB

    fun videoByteLimit(maxHeapBytes: Long, lowRamDevice: Boolean): Long {
        require(maxHeapBytes > 0L)
        val heapBound = (maxHeapBytes / 8L).coerceIn(MIN_VIDEO_BYTES, MAX_VIDEO_BYTES)
        return if (lowRamDevice) (heapBound / 2L).coerceAtLeast(MIN_VIDEO_BYTES) else heapBound
    }

    fun audioByteLimit(maxHeapBytes: Long, lowRamDevice: Boolean): Long {
        require(maxHeapBytes > 0L)
        val heapBound = (maxHeapBytes / 128L).coerceIn(MIN_AUDIO_BYTES, MAX_AUDIO_BYTES)
        return if (lowRamDevice) (heapBound / 2L).coerceAtLeast(MIN_AUDIO_BYTES) else heapBound
    }

    fun afterVideoTrim(currentBytes: Long): Long {
        require(currentBytes >= MIN_VIDEO_BYTES)
        return (currentBytes / 2L).coerceAtLeast(MIN_VIDEO_BYTES)
    }

    fun afterAudioTrim(currentBytes: Long): Long {
        require(currentBytes >= MIN_AUDIO_BYTES)
        return (currentBytes / 2L).coerceAtLeast(MIN_AUDIO_BYTES)
    }
}
