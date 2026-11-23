package dev.lolokeksu.legioncontrol

/**
 * Screen surface video PTS and System.nanoTime normally use the same monotonic
 * clock. Reading an encoded buffer can happen much later than the captured frame.
 * Do not mistake encoder queueing latency for a source-clock offset.
 *
 * Some vendors may expose a stream-relative PTS: in that case retain the
 * legacy offset fallback, with its unavoidable one-output latency uncertainty.
 */
object ReplayVideoClock {
    private const val SAME_CLOCK_TOLERANCE_US = 800_000L

    fun offsetFromDequeue(videoPtsUs: Long, dequeueMonoUs: Long): Long {
        require(videoPtsUs >= 0L && dequeueMonoUs >= 0L)
        val delta = Math.subtractExact(videoPtsUs, dequeueMonoUs)
        return if (delta in -SAME_CLOCK_TOLERANCE_US..SAME_CLOCK_TOLERANCE_US)
            0L else delta
    }
}
