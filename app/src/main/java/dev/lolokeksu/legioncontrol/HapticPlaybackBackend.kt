package dev.lolokeksu.legioncontrol

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.atomic.AtomicBoolean

data class HapticPlaybackResult(
    val state:String,
    val vibratorCount:Int?,
    val amplitudeControl:Boolean?,
    val usedDefaultAmplitude:Boolean?,
    val errorCode:String
)

/** One short, explicit, non-repeating Android vibration request; no service or background polling. */
class HapticPlaybackBackend(private val context:Context) {
    private val cancelled=AtomicBoolean(false)
    @Volatile private var active:Vibrator?=null

    /** Called on UI thread immediately before scheduling worker; screen exit may cancel queued work. */
    fun arm() { cancelled.set(false) }

    fun stop() {
        cancelled.set(true)
        runCatching { active?.cancel() }
    }

    fun perform(waveform:HapticWaveform):HapticPlaybackResult {
        require(HapticPattern.isValid(waveform.kind,waveform.level))
        require(waveform.durationMs in 35L..1_800L)
        if(cancelled.get() || Thread.currentThread().isInterrupted)
            return HapticPlaybackResult("CANCELLED",null,null,null,"PreStartStop")
        var ids:Int?=null
        var supportsAmplitude:Boolean?=null
        var defaultAmplitude:Boolean?=null
        var status="ERROR"
        var error=""
        try {
            val manager=context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            ids=runCatching { manager.vibratorIds.size.coerceIn(0,32) }.getOrNull()
            val vibrator=manager.defaultVibrator
            if(!vibrator.hasVibrator()) throw IllegalStateException("NoVibrator")
            supportsAmplitude=vibrator.hasAmplitudeControl()
            val effective=if(supportsAmplitude==true) waveform
                else HapticPattern.withoutAmplitudeControl(waveform)
            defaultAmplitude=supportsAmplitude!=true
            val effect=VibrationEffect.createWaveform(effective.timings,effective.amplitudes,-1)
            if(cancelled.get() || Thread.currentThread().isInterrupted)
                throw InterruptedException("Stopped before vibrator request")
            active=vibrator
            vibrator.vibrate(effect)
            val start=SystemClock.elapsedRealtime()
            // Elapsed time + no API exception only; NOT an actual measurement of movement.
            while(SystemClock.elapsedRealtime()-start<waveform.durationMs+60L) {
                if(cancelled.get() || Thread.currentThread().isInterrupted)
                    throw InterruptedException("Stopped by user or lifecycle")
                Thread.sleep(20)
            }
            status=if(cancelled.get()) "CANCELLED" else "COMPLETED_REQUEST"
        } catch(ignored:InterruptedException) {
            status="CANCELLED"
            error="UserOrLifecycleStop"
            Thread.interrupted()
        } catch(failure:Exception) {
            status=if(cancelled.get())"CANCELLED" else "ERROR"
            error=failure.javaClass.simpleName.take(48)
        } finally {
            // Bounded and explicit: do not allow residual continuous vibration.
            active?.let { runCatching { it.cancel() } }
            active=null
        }
        return HapticPlaybackResult(status,ids,supportsAmplitude,defaultAmplitude,error)
    }
}
