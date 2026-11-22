package dev.lolokeksu.legioncontrol

/** Pure timing and capture constraints; no microphone fallback. */
object PlaybackAudioTrackPolicy {
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2
    const val PCM_BYTES_PER_FRAME = CHANNELS * 2
    const val AAC_BITRATE = 128_000
    const val MAX_COPY_SAMPLE_BYTES = 8 * 1024 * 1024
    val PLAYBACK_USAGES = listOf("media", "game")

    fun isAlignedPcmSize(bytes: Int): Boolean =
        bytes >= 0 && bytes % PCM_BYTES_PER_FRAME == 0

    fun framesForBytes(bytes: Int): Long {
        require(isAlignedPcmSize(bytes))
        return bytes.toLong() / PCM_BYTES_PER_FRAME
    }

    fun ptsUs(frames: Long): Long {
        require(frames >= 0)
        return frames * 1_000_000L / SAMPLE_RATE
    }

    /** Each extractor starts its output track at zero; audio may start slightly after video. */
    fun muxPtsUs(sampleUs: Long, trackStartUs: Long, offsetUs: Long): Long {
        require(offsetUs >= 0)
        return (sampleUs - trackStartUs).coerceAtLeast(0L) + offsetUs
    }
}
