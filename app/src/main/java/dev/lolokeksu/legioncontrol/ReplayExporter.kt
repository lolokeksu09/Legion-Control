package dev.lolokeksu.legioncontrol

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer

/**
 * Publishes independent MP4 clips from frozen compressed video and, when explicitly
 * allowed and available, per-game AAC. Source frames never change or get deleted.
 * MediaStore IS_PENDING protects readers from partially muxed results.
 */
object ReplayExporter {
    fun save(
        context: Context,
        format: MediaFormat,
        frames: List<ReplayFrame>,
        audio: ReplayAudioSnapshot? = null,
        audioClockOffsetUs: Long = 0L
    ): Uri {
        require(frames.size >= 2 && frames.first().keyFrame)
        require(frames.last().ptsUs - frames.first().ptsUs >= 1_000_000L)
        val uri = CaptureClipLibrary.createPending(
            context, CaptureConfig.fileName(System.currentTimeMillis())
        )
        var published = false
        var muxer: MediaMuxer? = null
        var muxStarted = false
        try {
            val fd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "w"))
            fd.use { descriptor ->
                val writer = MediaMuxer(descriptor.fileDescriptor,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = writer
                val videoTrack = writer.addTrack(format)
                val audioTrack = audio?.takeIf { it.packets.isNotEmpty() }
                    ?.let { writer.addTrack(it.format) }
                writer.start()
                muxStarted = true
                val firstUs = frames.first().ptsUs
                var lastVideoUs = -1L
                val info = MediaCodec.BufferInfo()
                var count = 0
                for (frame in frames) {
                    require(frame.bytes.isNotEmpty() && frame.bytes.size <= 4 * 1024 * 1024)
                    require(frame.ptsUs >= firstUs)
                    val relative = frame.ptsUs - firstUs
                    require(relative > lastVideoUs) { "Out-of-order video PTS" }
                    lastVideoUs = relative
                    info.set(0, frame.bytes.size, relative,
                        if (frame.keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    writer.writeSampleData(videoTrack, ByteBuffer.wrap(frame.bytes), info)
                    count++
                    require(count <= 24_000) { "Replay exceeds safe frame count" }
                }
                check(count >= 2)
                if (audioTrack != null && audio != null) {
                    var lastAudioUs = -1L
                    var aacCount = 0
                    for (packet in audio.packets) {
                        require(packet.encoded.isNotEmpty() &&
                            packet.encoded.size <= 256 * 1024)
                        val videoPts = ReplayAudioTimeline.videoPts(
                            packet.ptsUs, audioClockOffsetUs
                        ) ?: continue
                        if (videoPts < firstUs || videoPts > frames.last().ptsUs) continue
                        val relative = videoPts - firstUs
                        if (relative <= lastAudioUs) continue
                        lastAudioUs = relative
                        info.set(0, packet.encoded.size, relative, 0)
                        writer.writeSampleData(audioTrack,
                            ByteBuffer.wrap(packet.encoded), info)
                        aacCount++
                        require(aacCount <= 24_000)
                    }
                    require(aacCount > 0) { "No audio packets overlap this replay" }
                }
                writer.stop()
                muxStarted = false
                writer.release()
                muxer = null
            }
            CaptureClipLibrary.publish(context, uri)
            published = true
            return uri
        } finally {
            if (muxStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!published) runCatching { CaptureClipLibrary.delete(context, uri) }
        }
    }
}
