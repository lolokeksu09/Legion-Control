package dev.lolokeksu.legioncontrol

/**
 * AudioRecord frame position and capture clock from AudioTimestamp.TIMEBASE_MONOTONIC.
 * Capture timestamps estimate when PCM entered the pipeline, not when read() returned.
 */
data class ReplayFrameClock(val framePosition: Long, val nanoTime: Long)

/**
 * Codec input presentation time is driven by sample count, not the timing of reads.
 * One valid AudioRecord capture timestamp supplies the initial monotonic anchor.
 * If Android cannot supply one, fall back to read completion minus PCM duration;
 * never submit zero-byte reads to the encoder or advance its media clock for them.
 *
 * No claim of exact hardware A/V sync is made: video surface timestamps still
 * require their own clock calibration on each device.
 */
class ReplayPcmTimeline(
    private val sampleRate: Int,
    private val bytesPerFrame: Int
) {
    init {
        require(sampleRate > 0 && bytesPerFrame > 0)
    }

    var totalFrames: Long = 0L
        private set
    private var originUs: Long? = null
    private var anchoredFromHardware = false
    private var previousPtsUs = -1L

    fun packetStartUs(
        bytesRead: Int,
        readEndNanoTime: Long,
        captured: ReplayFrameClock?
    ): Long? {
        require(bytesRead >= 0 && bytesRead % bytesPerFrame == 0)
        if (bytesRead == 0) return null
        require(readEndNanoTime > 0)
        val frames = bytesRead / bytesPerFrame
        val offsetUs = totalFrames * 1_000_000L / sampleRate
        val readEndUs = readEndNanoTime / 1000L
        val pcmDurationUs = frames.toLong() * 1_000_000L / sampleRate
        val fallbackUs = (readEndUs - pcmDurationUs).coerceAtLeast(0L)

        val timestampUs = captured?.takeIf {
            it.framePosition >= 0 && it.nanoTime > 0 &&
                it.nanoTime / 1000L in (readEndUs - 1_500_000L)..(readEndUs + 50_000L)
        }?.let {
            // AudioRecord's framePosition refers to a captured frame and may be
            // ahead of the frames already delivered to our read() caller.
            val relativeFrames = totalFrames - it.framePosition
            val candidateUs = it.nanoTime / 1000L +
                (relativeFrames * 1_000_000L / sampleRate)
            candidateUs.takeIf { value -> value >= 0L &&
                value in (readEndUs - 1_500_000L)..(readEndUs + 50_000L) }
        }

        if (originUs == null) {
            originUs = (timestampUs ?: fallbackUs) - offsetUs
            anchoredFromHardware = timestampUs != null
        } else if (timestampUs != null && !anchoredFromHardware) {
            // Correct a short startup read-latency estimate, but never snap
            // the timeline to a remote/faulty Android timestamp.
            val expected = originUs!! + offsetUs
            if (timestampUs - expected in -50_000L..50_000L) {
                originUs = timestampUs - offsetUs
                anchoredFromHardware = true
            }
        }
        val timestamp = maxOf(previousPtsUs + 1L, originUs!! + offsetUs)
        previousPtsUs = timestamp
        totalFrames += frames.toLong()
        return timestamp
    }
}
