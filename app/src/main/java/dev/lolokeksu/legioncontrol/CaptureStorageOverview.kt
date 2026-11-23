package dev.lolokeksu.legioncontrol

import android.content.Context
import android.provider.MediaStore
import java.util.Locale

/** Read-only app-owned capture storage accounting; never prunes recordings. */
object CaptureStorageOverview {
    private const val MAX_ITEMS = 100_000

    fun report(context: Context): String {
        val (videos, videoBytes, videoTruncated) = size(
            context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            CaptureClipLibrary.RELATIVE_DIR
        )
        val (images, imageBytes, imageTruncated) = size(
            context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            CaptureScreenshotLibrary.RELATIVE_DIR
        )
        fun mib(n: Long) = String.format(Locale.US, "%.1f", n / 1_048_576.0)
        val totalBytes = if (videoBytes > Long.MAX_VALUE - imageBytes) Long.MAX_VALUE
            else videoBytes + imageBytes
        return "Записи Legion Control (только опубликованные):\n" +
            "MP4: $videos · ${mib(videoBytes)} МиБ\n" +
            "PNG: $images · ${mib(imageBytes)} МиБ\n" +
            "Всего: ${mib(totalBytes)} МиБ\n" +
            (if (videoTruncated || imageTruncated)
                "Объём приблизительный: предел подсчёта $MAX_ITEMS на тип.\n" else "") +
            "Автоматического удаления нет. Для освобождения места удаляйте " +
            "только выбранные файлы через библиотеку."
    }

    private fun size(context: Context, uri: android.net.Uri, relative: String): Triple<Int, Long, Boolean> {
        val selection = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
            "${MediaStore.MediaColumns.IS_PENDING}=0"
        var count = 0
        var bytes = 0L
        var truncated = false
        context.contentResolver.query(uri,
            arrayOf(MediaStore.MediaColumns.SIZE),
            selection, arrayOf(context.packageName, relative), null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (count >= MAX_ITEMS) {
                    truncated = true
                    break
                }
                count++
                val size = cursor.getLong(0).coerceAtLeast(0L)
                bytes = if (bytes > Long.MAX_VALUE - size) Long.MAX_VALUE else bytes + size
            }
        }
        return Triple(count, bytes, truncated)
    }
}
