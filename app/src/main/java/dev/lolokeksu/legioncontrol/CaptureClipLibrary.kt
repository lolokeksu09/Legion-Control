package dev.lolokeksu.legioncontrol

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Uses only app-owned public MediaStore video records, without broad storage access. */
data class CaptureClip(val uri: Uri, val name: String, val bytes: Long, val durationMs: Long)

object CaptureClipLibrary {
    const val RELATIVE_DIR = "Movies/LegionControl/"

    fun createPending(context: Context, name: String): Uri {
        require(name.matches(Regex("LegionClip_[0-9]{13}\\.mp4")))
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

    fun listOwn(context: Context): List<CaptureClip> {
        val cols = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION
        )
        val selection = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
            "${MediaStore.MediaColumns.IS_PENDING}=0"
        val found = ArrayList<CaptureClip>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cols, selection,
            arrayOf(context.packageName, RELATIVE_DIR),
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext() && found.size < 100) {
                found.add(CaptureClip(
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(id)),
                    cursor.getString(name) ?: "video.mp4",
                    cursor.getLong(size).coerceAtLeast(0),
                    cursor.getLong(duration).coerceAtLeast(0)
                ))
            }
        }
        return found
    }
}
