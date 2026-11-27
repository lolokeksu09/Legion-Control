package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Writes and cleans up MediaStore rows created by the capture service. */
object CaptureClipLibrary {
    const val RELATIVE_DIR = "Movies/LegionControl/"

    fun createPending(context: Context, name: String): Uri {
        require(CaptureMediaFormat.MP4.acceptsName(name))
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return requireNotNull(context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        )) { "MediaStore не смог создать файл" }
    }

    fun publish(context: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(context.contentResolver.update(uri, values, null, null) == 1) {
            "Не удалось завершить файл в MediaStore"
        }
    }

    fun delete(context: Context, uri: Uri): Boolean =
        context.contentResolver.delete(uri, null, null) > 0
}
