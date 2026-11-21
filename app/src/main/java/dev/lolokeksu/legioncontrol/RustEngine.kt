package dev.lolokeksu.legioncontrol

/** Real JNI boundary; an unavailable library is explicit, never simulated. */
class RustEngine {
    private val library = runCatching { System.loadLibrary("legion_core") }
    private external fun recordBatterySample(
        level: Int, temperatureDeciC: Int, elapsedMs: Long
    ): Long

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
