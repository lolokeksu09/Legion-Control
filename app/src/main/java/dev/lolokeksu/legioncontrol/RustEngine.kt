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
