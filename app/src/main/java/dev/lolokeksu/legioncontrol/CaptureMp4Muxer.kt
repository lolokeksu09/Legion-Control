package dev.lolokeksu.legioncontrol

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Copies an app-owned finalized H264 MP4 and app-private AAC/m4a into one owned MP4.
 * Never modifies or deletes the source video; caller publishes/deletes after the outcome.
 * No transcoding, third-party media access or private APIs.
 */
object CaptureMp4Muxer {
    fun combine(context: Context, video: Uri, audio: File, audioOffsetUs: Long): Uri {
        require(audio.isFile && audio.length() > 0)
        require(audioOffsetUs in 0..5_000_000L)
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var destination: Uri? = null
        var muxer: MediaMuxer? = null
        var started = false
        var published = false
        try {
            videoExtractor.setDataSource(context, video, null)
            audioExtractor.setDataSource(audio.absolutePath)
            val videoTrack = findTrack(videoExtractor, "video/")
            val audioTrack = findTrack(audioExtractor, "audio/")
            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            val target = CaptureClipLibrary.createPending(
                context, CaptureConfig.fileName(System.currentTimeMillis() + 1L)
            )
            destination = target
            val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(target, "w"))
            try {
                val out = MediaMuxer(descriptor.fileDescriptor,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = out
                val v = out.addTrack(videoExtractor.getTrackFormat(videoTrack))
                val a = out.addTrack(audioExtractor.getTrackFormat(audioTrack))
                out.start()
                started = true
                val videoSamples = copyTrack(videoExtractor, out, v, 0)
                val audioSamples = copyTrack(audioExtractor, out, a, audioOffsetUs)
                check(videoSamples > 0 && audioSamples > 0) {
                    "Не найдены закодированные кадры видео или звука"
                }
                out.stop()
                started = false
                out.release()
                muxer = null
            } finally {
                descriptor.close()
            }
            CaptureClipLibrary.publish(context, target)
            published = true
            return target
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            videoExtractor.release()
            audioExtractor.release()
            if (!published) destination?.let {
                runCatching { CaptureClipLibrary.delete(context, it) }
            }
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true)
                return index
        }
        error("Отсутствует ${mimePrefix}дорожка")
    }

    private fun copyTrack(extractor: MediaExtractor, mux: MediaMuxer,
                          targetTrack: Int, startOffsetUs: Long): Int {
        val buffer = ByteBuffer.allocateDirect(PlaybackAudioTrackPolicy.MAX_COPY_SAMPLE_BYTES)
        val info = MediaCodec.BufferInfo()
        var firstTimestamp = -1L
        var count = 0
        while (true) {
            buffer.clear()
            val n = extractor.readSampleData(buffer, 0)
            if (n < 0) break
            require(n in 1..buffer.capacity()) { "Один медиасэмпл превышает ограничение" }
            val sampleUs = extractor.sampleTime
            require(sampleUs >= 0)
            if (firstTimestamp == -1L) firstTimestamp = sampleUs
            val sampleFlags = extractor.sampleFlags
            // MediaExtractor sample flags and MediaCodec buffer flags are not the
            // same enum: translate explicitly, never forward encrypted samples.
            check(sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                "Зашифрованный медиасэмпл нельзя объединить"
            }
            val codecFlags =
                (if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0) or
                (if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0)
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME else 0)
            info.set(0, n,
                PlaybackAudioTrackPolicy.muxPtsUs(sampleUs, firstTimestamp, startOffsetUs),
                codecFlags)
            buffer.position(0)
            buffer.limit(n)
            mux.writeSampleData(targetTrack, buffer, info)
            count++
            check(count <= 200_000) { "Слишком много медиасэмплов" }
            if (!extractor.advance()) break
        }
        return count
    }
}
