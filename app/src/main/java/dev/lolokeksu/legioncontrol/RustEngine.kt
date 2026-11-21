package dev.lolokeksu.legioncontrol

/** Real JNI boundary; an unavailable library is explicit, never simulated. */
class RustEngine {
    private val library = runCatching { System.loadLibrary("legion_core") }
    private external fun recordBatterySample(
        level: Int, temperatureDeciC: Int, elapsedMs: Long
    ): Long

    /** Native window summary for real readings only; NOT device thermal health. */
    data class WindowStats(
        val count: Int,
        val minTemperatureDeciC: Int,
        val maxTemperatureDeciC: Int,
        val meanTemperatureDeciC: Int
    )

    private external fun readBatteryWindowStats(): Long

    fun statistics(): Result<WindowStats> {
        library.getOrElse {
            return Result.failure(
                IllegalStateException("Rust JNI unavailable: " + it.javaClass.simpleName)
            )
        }
        return runCatching {
            val bits = readBatteryWindowStats()
            require(bits >= 0) { "Rust history empty or unavailable" }
            WindowStats(
                count = ((bits ushr 48) and 0xffff).toInt(),
                minTemperatureDeciC = ((bits ushr 32) and 0xffff).toShort().toInt(),
                maxTemperatureDeciC = ((bits ushr 16) and 0xffff).toShort().toInt(),
                meanTemperatureDeciC = (bits and 0xffff).toShort().toInt()
            )
        }
    }

    private external fun resetBatterySession(): Int
    private external fun readBatterySessionDuration(): Long

    /** Clears only ephemeral native samples; never changes Android device state. */
    fun resetSession(): Result<Int> {
        library.getOrElse {
            return Result.failure(
                IllegalStateException("Rust JNI unavailable: " + it.javaClass.simpleName)
            )
        }
        return runCatching {
            val cleared = resetBatterySession()
            require(cleared >= 0) { "Rust session reset failed" }
            cleared
        }
    }

    /** Milliseconds between oldest and newest samples retained by Rust. */
    fun durationMs(): Result<Long> {
        library.getOrElse {
            return Result.failure(
                IllegalStateException("Rust JNI unavailable: " + it.javaClass.simpleName)
            )
        }
        return runCatching {
            val duration = readBatterySessionDuration()
            require(duration >= 0) { "No Rust session to measure" }
            duration
        }
    }

    data class Trend(
        val count: Int,
        val chargeDelta: Int,
        val temperatureDeltaDeciC: Int
    )

    fun record(level: Int, deciC: Int, elapsedMs: Long): Result<Trend> {
        library.getOrElse {
            return Result.failure(
                IllegalStateException("Rust JNI unavailable: " + it.javaClass.simpleName)
            )
        }
        return runCatching {
            val native = recordBatterySample(level, deciC, elapsedMs)
            require(native >= 0) { "Rust rejected invalid sensor data" }
            Trend(
                count = ((native ushr 32) and 0xffff).toInt(),
                chargeDelta = (native and 0xffff).toShort().toInt(),
                temperatureDeltaDeciC =
                    ((native ushr 16) and 0xffff).toShort().toInt()
            )
        }
    }
}
