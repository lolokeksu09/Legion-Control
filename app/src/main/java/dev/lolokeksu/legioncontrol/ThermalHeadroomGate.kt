package dev.lolokeksu.legioncontrol

/**
 * Android's thermal service discourages polling more often than once per second.
 * Calls inside this 1500 ms monotonic window do NOT access the service and
 * return RATE_LIMITED rather than an old sample mislabelled as current.
 *
 * This is process-only state: no service, timers, callbacks or background work.
 */
class ThermalHeadroomGate(private val minimumIntervalMs: Long = 1500L) {
    init {
        require(minimumIntervalMs >= 1000L)
    }

    private var lastAttemptElapsedMs: Long? = null

    @Synchronized
    fun sample(nowElapsedMs: Long, probe: () -> Float): ProbeResult<Float> {
        if (nowElapsedMs < 0L) return ProbeResult.unavailable()
        val previous = lastAttemptElapsedMs
        if (previous != null && nowElapsedMs >= previous &&
            nowElapsedMs - previous < minimumIntervalMs
        ) {
            return ProbeResult.rateLimited()
        }

        // Count an API failure as an attempt too; otherwise a broken OEM service
        // could be hammered by repeated callers.
        lastAttemptElapsedMs = nowElapsedMs
        return try {
            val validated = TelemetryValidation.thermalHeadroom(probe())
            if (validated == null) ProbeResult.unavailable()
            else ProbeResult.available(validated)
        } catch (_: Exception) {
            ProbeResult.failure()
        }
    }
}
