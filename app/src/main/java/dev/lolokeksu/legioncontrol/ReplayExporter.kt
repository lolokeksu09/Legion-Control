package dev.lolokeksu.legioncontrol

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer

/**
 * Publishes a separate H.264 MP4 from a frozen encoded-frame snapshot.
 * Does not transcode, consume another app's media, or change an existing clip.
 */
object ReplayExporter {
    fun save(context: Context, format: MediaFormat, frames: List<ReplayFrame>): Uri {
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
                writer.start()
                muxStarted = true
                val firstUs = frames.first().ptsUs
                var lastUs = -1L
                val info = MediaCodec.BufferInfo()
                var count = 0
                for (frame in frames) {
                    require(frame.bytes.isNotEmpty() && frame.bytes.size <= 4 * 1024 * 1024)
                    require(frame.ptsUs >= firstUs)
                    val relative = frame.ptsUs - firstUs
                    // MediaMuxer requires monotonically increasing video timestamps.
                    require(relative > lastUs) { "Out-of-order codec timestamp" }
                    lastUs = relative
                    info.set(0, frame.bytes.size, relative,
                        if (frame.keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    writer.writeSampleData(videoTrack, ByteBuffer.wrap(frame.bytes), info)
                    count++
                    require(count <= 24_000) { "Replay exceeds safe frame count" }
                }
                check(count >= 2)
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
