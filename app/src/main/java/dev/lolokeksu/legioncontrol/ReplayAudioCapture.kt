package dev.lolokeksu.legioncontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import java.util.concurrent.atomic.AtomicBoolean

data class ReplayAudioSnapshot(
    val format: MediaFormat,
    val packets: List<ReplayAudioPacket>
)

/**
 * Explicit opt-in Android AudioPlaybackCapture for one selected game's UID.
 * One AAC encoder produces compressed packets into a bounded window while
 * Replay Capture is running; it never opens a microphone audio source.
 */
class ReplayAudioCapture(
    private val context: Context,
    private val projection: MediaProjection,
    private val selectedGameUid: Int,
    seconds: Int
) {
    private val buffer = ReplayAudioBuffer(seconds)
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

    fun snapshot(videoStartUs: Long, videoEndUs: Long,
                 videoMinusNanoClockUs: Long): ReplayAudioSnapshot? {
        val format = outputFormat ?: return null
        // Both samples use monotonic clocks. Audio PTS is anchored at PCM read,
        // video PTS is from Android's surface timestamps; adjust by measured delta.
        val shifted = buffer.snapshot(0L, Long.MAX_VALUE)
            .filter { packet ->
                val pts = ReplayAudioTimeline.videoPts(packet.ptsUs, videoMinusNanoClockUs)
                pts != null && pts >= videoStartUs && pts < videoEndUs
            }
        return if (shifted.isEmpty()) null else ReplayAudioSnapshot(format, shifted)
    }

    fun stop() {
        running.set(false)
        runCatching { record?.stop() }
    }

    private fun encodeLoop(audio: AudioRecord, aac: MediaCodec) {
        var eosQueued = false
        var eosSeen = false
        var previousPts = -1L
        val info = MediaCodec.BufferInfo()
        var deadline = Long.MAX_VALUE
        try {
            while (!eosSeen) {
                if (!running.get() && deadline == Long.MAX_VALUE) {
                    deadline = System.nanoTime() + 2_000_000_000L
                }
                if (System.nanoTime() >= deadline) break
                if (!eosQueued) {
                    val inputIndex = aac.dequeueInputBuffer(5_000L)
                    if (inputIndex >= 0) {
                        val data = requireNotNull(aac.getInputBuffer(inputIndex))
                        data.clear()
                        if (!running.get()) {
                            val pts = maxOf(0L, previousPts + 1)
                            aac.queueInputBuffer(inputIndex, 0, 0, pts,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                        } else {
                            val n = minOf(data.remaining(), 8192)
                            val available = n - n % 4
                            val bytes = audio.read(data, available,
                                AudioRecord.READ_NON_BLOCKING)
                            if (bytes < 0) error("AudioRecord read returned $bytes")
                            val accepted = if (bytes > 0 && bytes % 4 == 0) bytes else 0
                            // Timestamp of the current PCM chunk on the nanoTime clock.
                            // Up to one read/encoder buffer of sync uncertainty.
                            val durationUs = accepted.toLong() * 1_000_000L / (4L * 48_000)
                            val nowUs = System.nanoTime() / 1000L
                            val pts = maxOf(previousPts + 1, nowUs - durationUs)
                            previousPts = pts
                            aac.queueInputBuffer(inputIndex, 0, accepted, pts, 0)
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
                            val encoded = requireNotNull(aac.getOutputBuffer(outputIndex))
                                .duplicate()
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            encoded.get(bytes)
                            buffer.append(ReplayAudioPacket(bytes, info.presentationTimeUs))
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
