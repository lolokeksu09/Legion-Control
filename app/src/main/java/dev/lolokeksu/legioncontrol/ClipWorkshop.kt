package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer

/** A requested, non-destructive edit; exact trim is rounded back to a decodable video keyframe. */
sealed interface ClipTrimSelection {
    data class First(val durationMs: Long) : ClipTrimSelection
    data class Last(val durationMs: Long) : ClipTrimSelection
    data class Custom(val startMs: Long, val endMs: Long) : ClipTrimSelection
}

/**
 * App-owned media edits. No MediaProjection, Root, Shizuku, broad storage permission, or network.
 * All work must be scheduled off Android's UI thread and may be cancelled by the ViewModel.
 */
object ClipWorkshop {
    private fun ensureRunning(cancelled: () -> Boolean) {
        if (cancelled() || Thread.currentThread().isInterrupted) {
            throw InterruptedException("Операция отменена")
        }
    }

    private fun mediaDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val parsed = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            require(parsed > 0L) { "Длительность выбранного MP4 недоступна" }
            return parsed
        } finally {
            retriever.release()
        }
    }

    private fun selection(durationMs: Long, request: ClipTrimSelection): ClipWorkshopRange =
        when (request) {
            is ClipTrimSelection.First -> ClipWorkshopPolicy.first(durationMs, request.durationMs)
            is ClipTrimSelection.Last -> ClipWorkshopPolicy.last(durationMs, request.durationMs)
            is ClipTrimSelection.Custom ->
                ClipWorkshopPolicy.custom(durationMs, request.startMs, request.endMs)
        }

    /**
     * Exports an independent MP4 from the app-owned source without re-encoding.
     * Preserves the first video track and (if available) the first audio track.
     * The actual opening may be earlier than requested due to the previous video keyframe.
     */
    fun trim(
        context: Context, source: Uri, request: ClipTrimSelection,
        cancelled: () -> Boolean = { false }
    ): Uri {
        ensureRunning(cancelled)
        val range = selection(mediaDurationMs(context, source), request)
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var output: Uri? = null
        var outMux: MediaMuxer? = null
        var muxStarted = false
        var published = false
        try {
            videoExtractor.setDataSource(context, source, null)
            val vIndex = firstTrack(videoExtractor, "video/")
            videoExtractor.selectTrack(vIndex)
            val videoFormat = videoExtractor.getTrackFormat(vIndex)
            val aIndex: Int
            audioExtractor.setDataSource(context, source, null)
            aIndex = firstTrackOrMissing(audioExtractor, "audio/")
            val audioFormat = if (aIndex >= 0) {
                audioExtractor.selectTrack(aIndex)
                audioExtractor.getTrackFormat(aIndex)
            } else null

            val requestedStartUs = range.startMs * 1000L
            val endUs = range.endMs * 1000L
            videoExtractor.seekTo(requestedStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = ClipWorkshopPolicy.requireSyncStartUs(
                requestedStartUs, videoExtractor.sampleTime,
                videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
            )
            if (aIndex >= 0) {
                audioExtractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            ensureRunning(cancelled)
            val destination = CaptureClipLibrary.createPending(
                context, CaptureConfig.fileName(System.currentTimeMillis())
            )
            output = destination
            val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(
                destination, "w"
            )) { "MediaStore отказал в записи MP4" }
            try {
                val mux = MediaMuxer(descriptor.fileDescriptor,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                outMux = mux
                // Orientation lives in the MP4 container, not in the encoded video samples.
                val orientation = MediaMetadataRetriever().let { retriever ->
                    try {
                        retriever.setDataSource(context, source)
                        retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                        )?.toIntOrNull()
                    } finally {
                        retriever.release()
                    }
                }
                if (orientation in setOf(0, 90, 180, 270)) mux.setOrientationHint(orientation!!)
                val vTarget = mux.addTrack(videoFormat)
                val aTarget = audioFormat?.let { mux.addTrack(it) }
                mux.start()
                muxStarted = true
                val videoSamples = copyWithinRange(
                    videoExtractor, mux, vTarget, actualStartUs, endUs, cancelled)
                check(videoSamples > 0) { "В выбранном диапазоне нет декодируемых видеокадров" }
                if (aTarget != null) {
                    copyWithinRange(audioExtractor, mux, aTarget, actualStartUs, endUs, cancelled)
                }
                ensureRunning(cancelled)
                mux.stop()
                muxStarted = false
                mux.release()
                outMux = null
            } finally {
                descriptor.close()
            }
            ensureRunning(cancelled)
            CaptureClipLibrary.publish(context, destination)
            published = true
            return destination
        } finally {
            if (muxStarted) runCatching { outMux?.stop() }
            runCatching { outMux?.release() }
            videoExtractor.release()
            audioExtractor.release()
            if (!published) output?.let {
                runCatching { CaptureClipLibrary.delete(context, it) }
            }
        }
    }

    /**
     * Extracts one PNG directly from a previously saved MP4; unlike screen capture,
     * it needs no MediaProjection consent and never reads a different app's videos.
     */
    fun exportFrame(
        context: Context, source: Uri, atMs: Long?,
        cancelled: () -> Boolean = { false }
    ): Uri {
        ensureRunning(cancelled)
        val durationMs = mediaDurationMs(context, source)
        val positionMs = if (atMs == null) ClipWorkshopPolicy.middle(durationMs)
            else ClipWorkshopPolicy.frameAt(durationMs, atMs)
        val retriever = MediaMetadataRetriever()
        var bitmap: Bitmap? = null
        var resized: Bitmap? = null
        var destination: Uri? = null
        var published = false
        try {
            retriever.setDataSource(context, source)
            bitmap = requireNotNull(retriever.getFrameAtTime(
                positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST
            )) { "Кадр в этом MP4 недоступен" }
            ensureRunning(cancelled)
            val full = requireNotNull(bitmap)
            val longer = maxOf(full.width, full.height)
            check(longer > 0 && full.width > 0 && full.height > 0)
            val factor = minOf(1.0, ClipWorkshopPolicy.MAX_FRAME_EDGE.toDouble() / longer)
            val image = if (factor < 1.0) {
                Bitmap.createScaledBitmap(full,
                    maxOf(1, (full.width * factor).toInt()),
                    maxOf(1, (full.height * factor).toInt()), true).also { resized = it }
            } else full

            ensureRunning(cancelled)
            val target = CaptureScreenshotLibrary.createPending(
                context, "LegionShot_${System.currentTimeMillis()}.png"
            )
            destination = target
            check(context.contentResolver.openOutputStream(target, "w")?.use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            } == true) { "Не удалось записать PNG кадра" }
            ensureRunning(cancelled)
            CaptureScreenshotLibrary.publish(context, target)
            published = true
            return target
        } finally {
            retriever.release()
            resized?.recycle()
            bitmap?.recycle()
            if (!published) destination?.let {
                runCatching { CaptureScreenshotLibrary.delete(context, it) }
            }
        }
    }

    private fun firstTrack(extractor: MediaExtractor, mimePrefix: String): Int =
        firstTrackOrMissing(extractor, mimePrefix).also {
            require(it >= 0) { "В источнике нет видеодорожки" }
        }

    private fun firstTrackOrMissing(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true)
                return i
        }
        return -1
    }

    private fun copyWithinRange(
        extractor: MediaExtractor, mux: MediaMuxer, targetTrack: Int,
        startUs: Long, endUs: Long, cancelled: () -> Boolean
    ): Int {
        val buffer = ByteBuffer.allocateDirect(ClipWorkshopPolicy.MAX_SAMPLE_BYTES)
        val info = MediaCodec.BufferInfo()
        var written = 0
        var lastPts = -1L
        while (true) {
            ensureRunning(cancelled)
            val timeUs = extractor.sampleTime
            if (timeUs < 0 || timeUs >= endUs) break
            if (timeUs < startUs) {
                if (!extractor.advance()) break
                continue
            }
            val size = extractor.sampleSize
            require(size in 1..ClipWorkshopPolicy.MAX_SAMPLE_BYTES.toLong()) {
                "Размер медиасэмпла вне лимита"
            }
            buffer.clear()
            val read = extractor.readSampleData(buffer, 0)
            check(read.toLong() == size) { "Не удалось прочитать полный медиасэмпл" }
            val flags = extractor.sampleFlags
            require(flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                "Зашифрованные сэмплы не экспортируются"
            }
            val encodedFlags =
                (if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0) or
                (if (flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0)
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME else 0)
            val ptsUs = ClipWorkshopPolicy.normalizePtsUs(timeUs, startUs)
            check(ptsUs >= lastPts) { "Немонотонные временные метки" }
            info.set(0, read, ptsUs, encodedFlags)
            buffer.position(0)
            buffer.limit(read)
            mux.writeSampleData(targetTrack, buffer, info)
            lastPts = ptsUs
            written++
            check(written <= ClipWorkshopPolicy.MAX_SAMPLES_PER_TRACK) {
                "Экспорт превышает лимит сэмплов"
            }
            if (!extractor.advance()) break
        }
        return written
    }
}
