package dev.lolokeksu.legioncontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import java.util.concurrent.atomic.AtomicBoolean

data class ReplayAudioSnapshot(
    val format: MediaFormat,
    val packets: List<ReplayAudioPacket>
)

internal class ReplayAudioExport(val snapshot: ReplayAudioSnapshot,
                                 private val lease: ReplayAudioLease) : AutoCloseable {
    override fun close() = lease.close()
}

/**
 * Explicit opt-in Android AudioPlaybackCapture for one selected game's UID.
 * One AAC encoder produces compressed packets into a bounded window while
 * Replay Capture is running; it never opens a microphone audio source.
 */
class ReplayAudioCapture(
    private val context: Context,
    private val projection: MediaProjection,
    private val selectedGameUid: Int,
    seconds: Int,
    maxBufferBytes: Long = ReplayMemoryPolicy.MAX_AUDIO_BYTES
) {
    private val buffer = ReplayAudioBuffer(seconds, maxBufferBytes)
    private val running = AtomicBoolean(false)
    @Volatile private var outputFormat: MediaFormat? = null
    @Volatile var error: String? = null
        private set
    private var record: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var worker: Thread? = null

    fun start() {
        check(worker == null && !running.get())
        require(selectedGameUid > 0)
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Android RECORD_AUDIO consent is required for playback capture")
        }
        try {
            val playback = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUid(selectedGameUid)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            val sampleRate = 48_000
            val channelMask = AudioFormat.CHANNEL_IN_STEREO
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
            )
            require(minBuffer > 0) { "No audio PCM buffer" }
            val audio = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(channelMask)
                    .build())
                .setBufferSizeInBytes(maxOf(16_384, minBuffer * 2))
                .setAudioPlaybackCaptureConfig(playback)
                .build()
            record = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            val aac = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder = aac
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            aac.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aac.start()
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Android rejected game playback capture"
            }
            running.set(true)
            worker = Thread({ encodeLoop(audio, aac) }, "legion-replay-aac").also {
                it.start()
            }
        } catch (ex: Exception) {
            running.set(false)
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            record = null
            encoder = null
            throw ex
        }
    }

    internal fun freezeSnapshot(videoStartUs: Long, videoEndUs: Long,
                                videoMinusNanoClockUs: Long): ReplayAudioExport? {
        val format = outputFormat ?: return null
        val lease = buffer.freezeForVideo(videoStartUs, videoEndUs, videoMinusNanoClockUs)
            ?: return null
        return try {
            ReplayAudioExport(ReplayAudioSnapshot(format, lease.packets), lease)
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
    }

    internal fun stats(): ReplayAudioWindowStats = buffer.stats()

    fun reduceMemoryLimit() {
        buffer.reduceByteLimit(ReplayMemoryPolicy.afterAudioTrim(buffer.byteLimitBytes))
    }

    fun stop() {
        running.set(false)
        runCatching { record?.stop() }
    }

    private fun encodeLoop(audio: AudioRecord, aac: MediaCodec) {
        var eosQueued = false
        var eosSeen = false
        var previousPts = -1L
        val pcmTimeline = ReplayPcmTimeline(48_000, 4)
        val audioTimestamp = AudioTimestamp()
        var heldInputIndex = -1
        val info = MediaCodec.BufferInfo()
        var deadline = Long.MAX_VALUE
        try {
            while (!eosSeen) {
                if (!running.get() && deadline == Long.MAX_VALUE) {
                    deadline = System.nanoTime() + 2_000_000_000L
                }
                if (System.nanoTime() >= deadline) break
                if (!eosQueued) {
                    if (heldInputIndex < 0) heldInputIndex = aac.dequeueInputBuffer(5_000L)
                    if (heldInputIndex >= 0) {
                        val inputIndex = heldInputIndex
                        val data = requireNotNull(aac.getInputBuffer(inputIndex))
                        data.clear()
                        if (!running.get()) {
                            val pts = maxOf(0L, previousPts + 1)
                            aac.queueInputBuffer(inputIndex, 0, 0, pts,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                            heldInputIndex = -1
                        } else {
                            val n = minOf(data.remaining(), 8192)
                            val available = n - n % 4
                            val bytes = audio.read(data, available,
                                AudioRecord.READ_NON_BLOCKING)
                            if (bytes < 0) error("AudioRecord read returned $bytes")
                            if (bytes > 0) {
                                // getTimestamp uses the *capture* frame position and
                                // CLOCK_MONOTONIC, not the (jittery) time read() finished.
                                val sample = if (audio.getTimestamp(audioTimestamp,
                                        AudioTimestamp.TIMEBASE_MONOTONIC) ==
                                    AudioRecord.SUCCESS) {
                                    ReplayFrameClock(audioTimestamp.framePosition,
                                        audioTimestamp.nanoTime)
                                } else null
                                val pts = requireNotNull(pcmTimeline.packetStartUs(
                                    bytes, System.nanoTime(), sample))
                                check(pts > previousPts)
                                previousPts = pts
                                aac.queueInputBuffer(inputIndex, 0, bytes, pts, 0)
                                heldInputIndex = -1
                            }
                            // On READ_NON_BLOCKING returning zero, keep this codec
                            // input index; an empty buffer with a fake PTS would
                            // advance AAC time without any audio samples.
                        }
                    }
                }
                while (true) {
                    val outputIndex = aac.dequeueOutputBuffer(info, 0)
                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(outputFormat == null)
                        outputFormat = aac.outputFormat
                        continue
                    }
                    if (outputIndex < 0) continue
                    try {
                        if (info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            require(info.size <= 256 * 1024)
                            buffer.appendEncoded(info.size, info.presentationTimeUs) {
                                val encoded = requireNotNull(aac.getOutputBuffer(outputIndex)).duplicate()
                                encoded.position(info.offset)
                                encoded.limit(info.offset + info.size)
                                ByteArray(info.size).also { encoded.get(it) }
                            }
                        }
                        eosSeen = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally {
                        aac.releaseOutputBuffer(outputIndex, false)
                    }
                    if (eosSeen) break
                }
                if (!eosSeen) Thread.sleep(6)
            }
        } catch (ex: Exception) {
            error = ex.javaClass.simpleName
        } finally {
            running.set(false)
            buffer.clear()
            outputFormat = null
            runCatching { audio.stop() }
            runCatching { audio.release() }
            runCatching { aac.stop() }
            runCatching { aac.release() }
        }
    }
}
