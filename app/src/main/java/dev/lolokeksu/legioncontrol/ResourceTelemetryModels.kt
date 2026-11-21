package dev.lolokeksu.legioncontrol

/**
 * Field-level provenance matters: absence, API failures and deliberate throttling
 * must never be presented as a zero measurement or a healthy device.
 */
enum class ProbeState { AVAILABLE, UNAVAILABLE, ERROR, RATE_LIMITED }

data class ProbeResult<T : Any> private constructor(
    val state: ProbeState,
    val value: T?
) {
    init {
        require((state == ProbeState.AVAILABLE) == (value != null)) {
            "Only AVAILABLE probe results may contain a measurement"
        }
    }

    companion object {
        fun <T : Any> available(value: T): ProbeResult<T> =
            ProbeResult(ProbeState.AVAILABLE, value)

        fun <T : Any> unavailable(): ProbeResult<T> =
            ProbeResult(ProbeState.UNAVAILABLE, null)

        fun <T : Any> failure(): ProbeResult<T> =
            ProbeResult(ProbeState.ERROR, null)

        fun <T : Any> rateLimited(): ProbeResult<T> =
            ProbeResult(ProbeState.RATE_LIMITED, null)

        /** Field-level exception containment, testable without Android classes. */
        fun <T : Any> read(read: () -> T, normalize: (T) -> T?): ProbeResult<T> =
            try {
                val validated = normalize(read())
                if (validated == null) unavailable() else available(validated)
            } catch (_: Exception) {
                failure()
            }
    }

    /** Preserve unavailable/error state across dependent API reads. */
    fun <R : Any> flatMap(transform: (T) -> ProbeResult<R>): ProbeResult<R> =
        when (state) {
            ProbeState.AVAILABLE -> try {
                transform(requireNotNull(value))
            } catch (_: Exception) {
                failure()
            }
            ProbeState.UNAVAILABLE -> unavailable()
            ProbeState.ERROR -> failure()
            ProbeState.RATE_LIMITED -> rateLimited()
        }
}

/** Device-wide kernel memory figures, not the current game's memory usage. */
data class MemoryMetrics(
    val totalBytes: Long,
    val availableBytes: Long,
    val lowMemoryThresholdBytes: Long,
    val lowMemory: Boolean
)

/** Only the filesystem containing this application's private files directory. */
data class StorageMetrics(
    val totalBytes: Long,
    val availableBytes: Long
)

/**
 * One explicitly requested read-only snapshot. elapsedRealtimeMs is monotonic
 * boot-relative time, NOT UNIX epoch time or elapsed gaming-session time.
 *
 * Fields can be unavailable independently. Battery current is a manufacturer-
 * reported signed microampere value, NOT charger wattage or Bypass status.
 */
data class ResourceTelemetrySnapshot(
    val elapsedRealtimeMs: Long,
    val systemMemory: ProbeResult<MemoryMetrics>,
    val appFilesystem: ProbeResult<StorageMetrics>,
    val onlineProcessorCount: ProbeResult<Int>,
    val batteryChargeCounterMicroAh: ProbeResult<Int>,
    val batteryCurrentNowMicroAmps: ProbeResult<Int>,
    val batteryCurrentAverageMicroAmps: ProbeResult<Int>,
    val batteryEnergyCounterNanoWh: ProbeResult<Long>,
    val thermalHeadroom: ProbeResult<Float>
)

/** Pure OEM-value validation, suitable for direct JVM testing. */
object TelemetryValidation {
    fun memory(
        totalBytes: Long,
        availableBytes: Long,
        thresholdBytes: Long,
        lowMemory: Boolean
    ): MemoryMetrics? {
        if (totalBytes <= 0L || availableBytes !in 0L..totalBytes ||
            thresholdBytes !in 0L..totalBytes
        ) return null
        return MemoryMetrics(totalBytes, availableBytes, thresholdBytes, lowMemory)
    }

    fun storage(totalBytes: Long, availableBytes: Long): StorageMetrics? {
        if (totalBytes <= 0L || availableBytes !in 0L..totalBytes) return null
        return StorageMetrics(totalBytes, availableBytes)
    }

    /** Android returns processors currently usable by this process, not SoC core count. */
    fun onlineProcessors(count: Int): Int? = count.takeIf { it in 1..1024 }

    /** Defensive sanity range only. No assumptions about the OEM's current sign. */
    fun currentMicroAmps(value: Int?): Int? =
        value?.takeIf { it in -50_000_000..50_000_000 }

    /** Android BatteryManager charge counter is in microampere-hours. */
    fun chargeCounterMicroAh(value: Int?): Int? =
        value?.takeIf { it in 0..100_000_000 }

    /** Android energy counter is in nanowatt-hours. */
    fun energyCounterNanoWh(value: Long?): Long? =
        value?.takeIf { it in 0L..10_000_000_000_000L }

    /**
     * Android thermal headroom is not a percentage. A value >1 is possible;
     * 1.0 corresponds to the platform's severe-throttling threshold.
     */
    fun thermalHeadroom(value: Float?): Float? =
        value?.takeIf { it.isFinite() && it >= 0f }
}
