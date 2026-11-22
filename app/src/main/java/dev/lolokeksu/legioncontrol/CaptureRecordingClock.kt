package dev.lolokeksu.legioncontrol

/**
 * Elapsed capture time excludes paused intervals. This uses SystemClock.elapsedRealtime()
 * values passed by the recording service, never wall-clock time.
 *
 * No Android dependency: the transition policy can be verified with pure JVM tests.
 */
class CaptureRecordingClock(
    private val limitMs: Long,
    private val startedAtMs: Long
) {
    companion object {
        /** A pause is bounded so an idle MediaProjection foreground service cannot persist forever. */
        const val MAX_PAUSE_MS = 300_000L
    }

    init {
        require(limitMs > 0 && startedAtMs >= 0)
    }

    private var pausedAtMs: Long? = null
    private var completedPauseMs = 0L

    val paused: Boolean get() = pausedAtMs != null

    fun pause(nowMs: Long): Boolean {
        require(nowMs >= startedAtMs)
        if (paused) return false
        pausedAtMs = nowMs
        return true
    }

    fun resume(nowMs: Long): Boolean {
        val pauseStarted = pausedAtMs ?: return false
        require(nowMs >= pauseStarted)
        completedPauseMs += nowMs - pauseStarted
        pausedAtMs = null
        return true
    }

    fun activeElapsedMs(nowMs: Long): Long {
        val effective = pausedAtMs?.let { minOf(it, nowMs) } ?: nowMs
        return (effective - startedAtMs - completedPauseMs).coerceAtLeast(0)
    }

    fun remainingMs(nowMs: Long): Long =
        (limitMs - activeElapsedMs(nowMs)).coerceAtLeast(0)
}
