package dev.lolokeksu.legioncontrol

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioRouting
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class AudioPlaybackResult(
    val startedAtMs:Long,
    val state:String,
    val observedFrames:Int,
    val routedType:Int?,
    val errorCode:String,
    val routes:List<AudioRouteRecord>
)

/**
 * Plays only original low-level sine PCM. Does NOT record microphone or intercept audio.
 * No repeat/loop, no background service, never changes the Android system volume.
 */
class AudioTestBackend(private val context:Context) {
    private val stopFlag=AtomicBoolean(false)
    @Volatile private var track:AudioTrack?=null

    /** Safe user/lifecycle request. The worker alone owns release and SQLite completion. */
    fun stop() {
        stopFlag.set(true)
        runCatching { track?.pause() }
    }

    /** The UI arms a new test BEFORE submitting the worker; screen exit can cancel queued work. */
    fun arm() { stopFlag.set(false) }

    fun play(
        kind:String,channel:String,frequency:Int,durationMs:Int,
        progress:(Int,Int)->Unit = { _,_ -> }
    ):AudioPlaybackResult {
        require(AudioSignalGenerator.isValid(kind,channel,frequency,durationMs))
        val now=System.currentTimeMillis().coerceAtLeast(1)
        val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val routeEvents=ConcurrentLinkedQueue<AudioRouteRecord>()
        val pcm=AudioSignalGenerator.generate(kind,channel,frequency,durationMs)
        val expected=AudioSignalGenerator.frames(durationMs)
        var observed=0
        var routed:Int?=null
        var state="ERROR"
        var error=""
        var focus:AudioFocusRequest?=null
        var audioTrack:AudioTrack?=null
        var listener:AudioTrack.OnRoutingChangedListener?=null
        try {
            if(manager.mode!=AudioManager.MODE_NORMAL)
                throw IllegalStateException("AudioModeNotNormal")
            if(stopFlag.get() || Thread.currentThread().isInterrupted)
                throw InterruptedException()
            val attributes=AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener({ change ->
                    if(change<0) stopFlag.set(true)
                },Handler(Looper.getMainLooper())).build()
            if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw IllegalStateException("AudioFocusDenied")
            val format=AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AudioSignalGenerator.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            val created=AudioTrack.Builder()
                .setAudioAttributes(attributes).setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size*Short.SIZE_BYTES).build()
            audioTrack=created
            track=created
            if(created.state!=AudioTrack.STATE_INITIALIZED)
                throw IllegalStateException("AudioTrackNotInitialized")
            val written=created.write(pcm,0,pcm.size,AudioTrack.WRITE_BLOCKING)
            if(written!=pcm.size) throw IllegalStateException("AudioWriteIncomplete")
            if(stopFlag.get() || Thread.currentThread().isInterrupted)
                throw InterruptedException()
            val routeListener=AudioTrack.OnRoutingChangedListener { routing:AudioRouting ->
                val newType=routing.routedDevice?.type
                routeEvents.add(AudioRouteRecord(
                    System.currentTimeMillis().coerceAtLeast(1),"ROUTE_CHANGE",newType,
                    "Android AudioTrack routing callback; not proof of audible output"))
            }
            listener=routeListener
            created.addOnRoutingChangedListener(routeListener,Handler(Looper.getMainLooper()))
            created.play()
            routed=created.routedDevice?.type
            routeEvents.add(AudioRouteRecord(
                System.currentTimeMillis().coerceAtLeast(1),"ROUTE_START",routed,
                "Selected device as observed by AudioTrack; may be unknown"))
            val started=SystemClock.elapsedRealtime()
            while(SystemClock.elapsedRealtime()-started<=durationMs+1_200L) {
                if(stopFlag.get() || Thread.currentThread().isInterrupted)
                    throw InterruptedException()
                observed=created.playbackHeadPosition.coerceIn(0,expected)
                progress(observed,expected)
                if(observed>=expected-48) break
                Thread.sleep(18)
            }
            observed=created.playbackHeadPosition.coerceIn(0,expected)
            routed=created.routedDevice?.type ?: routed
            if(observed>=expected-48) state="COMPLETE"
            else {state="ERROR";error="AudioHeadIncomplete"}
        } catch(interrupted:InterruptedException) {
            state="STOPPED"; error="UserOrLifecycleStop"
            Thread.interrupted()
        } catch(failure:Exception) {
            state=if(stopFlag.get())"STOPPED" else "ERROR"
            error=failure.javaClass.simpleName.take(48)
        } finally {
            val played=audioTrack
            if(played!=null) {
                runCatching { listener?.let { played.removeOnRoutingChangedListener(it) } }
                runCatching { played.pause() }
                runCatching { played.flush() }
                runCatching { played.release() }
            }
            track=null
            focus?.let { runCatching { manager.abandonAudioFocusRequest(it) } }
        }
        val recorded=routeEvents.toList()
        val filtered=ArrayList<AudioRouteRecord>()
        var previousType:Int?=null
        for(e in recorded) {
            if(e.kind=="ROUTE_START" || e.type!=previousType) {
                filtered+=e
                previousType=e.type
            }
        }
        return AudioPlaybackResult(now,state,observed,routed,error,filtered.take(40))
    }
}
