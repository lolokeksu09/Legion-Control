package dev.lolokeksu.legioncontrol

/** Pure, deterministic, finite vibration signals. Values are DIGITAL requests, not G-force. */
data class HapticWaveform(
    val kind:String,
    val level:String,
    val requestedAmplitude:Int,
    val timings:LongArray,
    val amplitudes:IntArray
) {
    val durationMs:Long get()=timings.sum()
}

/** Never accepts arbitrary durations/amplitudes or repeat indices from UI. */
object HapticPattern {
    val kinds=listOf("TAP","DOUBLE_TAP","TRIPLE_PULSE","SLOW_PULSE","LONG_PULSE")
    val levels=listOf("GENTLE","MEDIUM","STRONG")
    const val caveat="Android подтверждает запрос, но НЕ измеряет физическую вибрацию или силу мотора."
    fun amplitude(level:String):Int = when(level) {
        "GENTLE" -> 48
        "MEDIUM" -> 96
        "STRONG" -> 160
        else -> throw IllegalArgumentException("Unknown level")
    }
    fun isValid(kind:String,level:String)=kind in kinds && level in levels

    fun build(kind:String,level:String):HapticWaveform {
        require(isValid(kind,level))
        val times=when(kind) {
            "TAP" -> longArrayOf(0,55)
            "DOUBLE_TAP" -> longArrayOf(0,55,115,55)
            "TRIPLE_PULSE" -> longArrayOf(0,50,110,50,110,50)
            "SLOW_PULSE" -> longArrayOf(0,240,180,240)
            "LONG_PULSE" -> longArrayOf(0,880)
            else -> error("unreachable")
        }
        val amp=amplitude(level)
        val amplitudes=IntArray(times.size) { index -> if(index%2==0)0 else amp }
        return HapticWaveform(kind,level,amp,times,amplitudes).also {
            require(it.durationMs in 35..1_800)
        }
    }

    /** Standard Android DEFAULT_AMPLITUDE (-1) rather than pretending hardware level is controlled. */
    fun withoutAmplitudeControl(waveform:HapticWaveform):HapticWaveform =
        waveform.copy(amplitudes=waveform.amplitudes.map { if(it==0)0 else -1 }.toIntArray())

    fun csvCell(value:String):String {
        val left=value.trimStart()
        val safe=if(left.firstOrNull() in listOf('=','+','-','@')) "'"+value else value
        return "\"" + safe.replace("\"","\"\"") + "\""
    }
}
