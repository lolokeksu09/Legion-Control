package dev.lolokeksu.legioncontrol

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pure original PCM synthesis; no playback APIs, copyrighted media or microphone use. */
object AudioSignalGenerator {
    const val SAMPLE_RATE = 48_000
    const val MAX_DURATION_MS = 3_000
    const val AMPLITUDE = 0.10
    val frequencies = listOf(220,440,880,1760)
    val kinds = listOf("TONE","SWEEP","CHANNEL_ID")
    val channels = listOf("LEFT","RIGHT","BOTH","ALTERNATING")

    fun isValid(kind:String,channel:String,frequencyHz:Int,durationMs:Int):Boolean =
        kind in kinds && channel in channels && frequencyHz in frequencies &&
        durationMs in 250..MAX_DURATION_MS &&
        (if(kind=="CHANNEL_ID")channel=="ALTERNATING" else channel!="ALTERNATING")

    /** Interleaved stereo 16-bit PCM with 30ms fade to avoid transient clicks. */
    fun generate(
        kind:String,channel:String,frequencyHz:Int,durationMs:Int,
        sampleRate:Int=SAMPLE_RATE
    ):ShortArray {
        require(isValid(kind,channel,frequencyHz,durationMs))
        require(sampleRate in 8_000..192_000)
        val frames=(durationMs.toLong()*sampleRate/1000).toInt()
        require(frames in 1..576_000)
        val pcm=ShortArray(frames*2)
        val fadeFrames=(sampleRate*30/1000).coerceAtLeast(1)
        var phase=0.0
        for(i in 0 until frames) {
            val frequency=if(kind=="SWEEP") {
                220.0+(1760.0-220.0)*i/frames
            } else frequencyHz.toDouble()
            phase+=2.0*PI*frequency/sampleRate
            val globalFade=min(1.0,min(i.toDouble()/fadeFrames,
                (frames-1-i).toDouble()/fadeFrames)).coerceIn(0.0,1.0)
            val alternates=kind=="CHANNEL_ID"
            val half=frames/2
            val inFirstHalf=i<half
            // Fade each half separately; a channel flip never creates a hard edge.
            val relative=if(inFirstHalf)i else i-half
            val stageLength=if(inFirstHalf)half else frames-half
            val stageFade=if(alternates) min(1.0,min(
                relative.toDouble()/fadeFrames,
                (stageLength-1-relative).toDouble()/fadeFrames
            )).coerceIn(0.0,1.0) else 1.0
            val level=(sin(phase)*AMPLITUDE*globalFade*stageFade*Short.MAX_VALUE)
                .roundToInt().coerceIn(Short.MIN_VALUE.toInt(),Short.MAX_VALUE.toInt()).toShort()
            val l=if(alternates)inFirstHalf else channel!="RIGHT"
            val r=if(alternates)!inFirstHalf else channel!="LEFT"
            pcm[i*2]=if(l)level else 0
            pcm[i*2+1]=if(r)level else 0
        }
        return pcm
    }

    /** For UI tests only: theoretical decoded frames, not speaker output latency. */
    fun frames(durationMs:Int,sampleRate:Int=SAMPLE_RATE):Int {
        require(durationMs in 250..MAX_DURATION_MS)
        require(sampleRate in 8_000..192_000)
        return (durationMs.toLong()*sampleRate/1000).toInt()
    }
}
