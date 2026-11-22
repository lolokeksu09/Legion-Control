package dev.lolokeksu.legioncontrol

/** Pure policy, no Android API; all timestamps are in milliseconds unless suffixed Us. */
data class ClipWorkshopRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

object ClipWorkshopPolicy {
    const val MIN_CLIP_MS = 1_000L
    const val MAX_EXPORT_MS = 15 * 60_000L
    const val MAX_SAMPLES_PER_TRACK = 200_000
    const val MAX_SAMPLE_BYTES = 8 * 1024 * 1024
    const val MAX_FRAME_EDGE = 1920

    fun custom(sourceDurationMs: Long, startMs: Long, endMs: Long): ClipWorkshopRange {
        require(sourceDurationMs >= MIN_CLIP_MS && startMs >= 0 && endMs > startMs)
        require(endMs <= sourceDurationMs && endMs - startMs >= MIN_CLIP_MS)
        require(endMs - startMs <= MAX_EXPORT_MS)
        return ClipWorkshopRange(startMs, endMs)
    }

    fun first(sourceDurationMs: Long, wantedMs: Long): ClipWorkshopRange {
        require(wantedMs >= MIN_CLIP_MS)
        return custom(sourceDurationMs, 0, minOf(sourceDurationMs, wantedMs))
    }

    fun last(sourceDurationMs: Long, wantedMs: Long): ClipWorkshopRange {
        require(wantedMs >= MIN_CLIP_MS)
        return custom(sourceDurationMs, (sourceDurationMs - wantedMs).coerceAtLeast(0), sourceDurationMs)
    }

    fun frameAt(sourceDurationMs: Long, positionMs: Long): Long {
        require(sourceDurationMs > 0 && positionMs >= 0 && positionMs < sourceDurationMs)
        return positionMs
    }

    fun middle(sourceDurationMs: Long): Long = frameAt(sourceDurationMs, sourceDurationMs / 2)

    /** Export starts at an actual sync frame no later than the requested boundary. */
    fun requireSyncStartUs(requestedUs: Long, foundUs: Long, isSync: Boolean): Long {
        check(isSync && requestedUs >= 0 && foundUs in 0..requestedUs) {
            "Нет декодируемого ключевого кадра перед выбранным началом"
        }
        return foundUs
    }

    fun includeSample(sampleTimeUs: Long, actualStartUs: Long, endUs: Long): Boolean =
        sampleTimeUs >= actualStartUs && sampleTimeUs < endUs

    fun normalizePtsUs(sourcePtsUs: Long, actualStartUs: Long): Long {
        require(sourcePtsUs >= actualStartUs && actualStartUs >= 0)
        return sourcePtsUs - actualStartUs
    }
}
