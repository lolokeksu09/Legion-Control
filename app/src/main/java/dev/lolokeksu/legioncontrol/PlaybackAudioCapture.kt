package dev.lolokeksu.legioncontrol

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.SystemClock
import java.io.File

/** Result of the owned AAC capture; file is never exposed to the shared MediaStore by itself. */
data class PlaybackAudioResult(val file: File?, val offsetUs: Long, val reason: String)

/**
 * Captures only opt-in USAGE_GAME / USAGE_MEDIA playback through MediaProjection.
 * No microphone source is ever configured. Encodes a temporary AAC/m4a track; a later
 * bounded remux copies it into the existing user-consented MP4.
 *
 * AudioRecord and AAC encoding are owned by this per-recording session. Codec work is off
 * the main thread; pause/resume stop and restart playback capture without a new projection.
 */
class PlaybackAudioCapture(
    private val context: Context,
    private val projection: MediaProjection,
    private val gameUid: Int
) {
    @Volatile private var stopRequested = false
    @Volatile private var pauseRequested = false
    @Volatile private var error: String? = null
    @Volatile private var finished = false
    private var pcm: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var worker: Thread? = null
    private var file: File? = null
    private var audioStartedAtMs: Long = 0
    private var muxStarted = false
    private var samplesWritten = 0L

    fun start(videoStartedAtMs: Long) {
        check(worker == null && !stopRequested)
        require(gameUid > 0) { "UID выбранной игры недействителен" }
        try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUid(gameUid)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(PlaybackAudioTrackPolicy.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minBytes = AudioRecord.getMinBufferSize(
                PlaybackAudioTrackPolicy.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBytes > 0) { "Нет доступного аудиобуфера PCM" }
            val record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(16_384, minBytes * 2))
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
            pcm = record
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord не инициализирован" }

            val aac = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder = aac
            aac.configure(MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                PlaybackAudioTrackPolicy.SAMPLE_RATE, PlaybackAudioTrackPolicy.CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, PlaybackAudioTrackPolicy.AAC_BITRATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aac.start()

            val output = File.createTempFile("legion-playback-", ".m4a", context.cacheDir)
            file = output
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Android отказал в захвате звука воспроизведения"
            }
            audioStartedAtMs = SystemClock.elapsedRealtime()
            check(videoStartedAtMs >= 0)
            worker = Thread({ encodeLoop(aac, record) }, "legion-playback-aac").apply { start() }
            // Offset is only the delay between starting video and starting this audio capture.
            offsetUs = (audioStartedAtMs - videoStartedAtMs).coerceAtLeast(0L) * 1000L
        } catch (e: Exception) {
            stopRequested = true
            runCatching { pcm?.stop() }
            runCatching { pcm?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            file?.delete()
            throw e
        }
    }

    private var offsetUs = 0L

    fun pause() {
        if (pauseRequested || stopRequested) return
        pauseRequested = true
        pcm?.stop()
    }

    fun resume() {
        if (!pauseRequested || stopRequested) return
        val record = requireNotNull(pcm)
        record.startRecording()
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        pauseRequested = false
    }

    private fun encodeLoop(codec: MediaCodec, record: AudioRecord) {
        var inputFrames = 0L
        var eosQueued = false
        var eosSeen = false
        val info = MediaCodec.BufferInfo()
        var track = -1
        try {
            while (!eosSeen) {
                if (!eosQueued && (stopRequested || !pauseRequested)) {
                    val index = codec.dequeueInputBuffer(8_000)
                    if (index >= 0) {
                        val input = requireNotNull(codec.getInputBuffer(index))
                        input.clear()
                        val pts = PlaybackAudioTrackPolicy.ptsUs(inputFrames)
                        if (stopRequested) {
                            codec.queueInputBuffer(index, 0, 0, pts,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                        } else {
                            val cap = minOf(input.remaining(), 8192)
                            val aligned = cap - cap % PlaybackAudioTrackPolicy.PCM_BYTES_PER_FRAME
                            val read = record.read(input, aligned, AudioRecord.READ_NON_BLOCKING)
                            if (read < 0 && !pauseRequested)
                                throw IllegalStateException("AudioRecord.read() вернул код $read")
                            val valid = if (read > 0 && !pauseRequested &&
                                PlaybackAudioTrackPolicy.isAlignedPcmSize(read)) read else 0
                            codec.queueInputBuffer(index, 0, valid, pts, 0)
                            inputFrames += PlaybackAudioTrackPolicy.framesForBytes(valid)
                        }
                    }
                }
                var drained = false
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, if (stopRequested) 10_000 else 0)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(!muxStarted && track == -1)
                        track = requireNotNull(muxer).addTrack(codec.outputFormat)
                        requireNotNull(muxer).start()
                        muxStarted = true
                        continue
                    }
                    if (index < 0) continue
                    drained = true
                    val output = codec.getOutputBuffer(index)
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        check(muxStarted && track >= 0 && output != null)
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        requireNotNull(muxer).writeSampleData(track, output, info)
                        samplesWritten++
                    }
                    eosSeen = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                    if (eosSeen) break
                }
                if (pauseRequested && !stopRequested || !drained && !stopRequested) {
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            error = e.javaClass.simpleName + ": " + (e.message ?: "")
        } finally {
            finished = true
            runCatching { record.stop() }
            runCatching { record.release() }
            if (muxStarted) runCatching { requireNotNull(muxer).stop() }
                .onFailure { error = "MediaMuxer.stop: " + it.javaClass.simpleName }
            runCatching { muxer?.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (error != null || !eosSeen || samplesWritten == 0L) file?.delete()
        }
    }

    fun stopAndTakeResult(): PlaybackAudioResult {
        stopRequested = true
        pauseRequested = false
        runCatching { pcm?.stop() }
        val thread = worker
        try {
            thread?.join(7_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (thread?.isAlive == true) {
            thread.interrupt()
            return PlaybackAudioResult(null, offsetUs, "Аудиокодек не завершился в лимит")
        }
        val saved = file?.takeIf { finished && error == null && samplesWritten > 0 && it.length() > 0 }
        return PlaybackAudioResult(saved, offsetUs,
            error ?: if (saved != null) "AAC-дорожка готова" else "Звук не удалось закодировать")
    }
}
