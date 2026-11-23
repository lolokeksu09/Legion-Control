package dev.lolokeksu.legioncontrol

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

/** Only app-owned, published videos; no broad gallery/storage permission. */
data class CaptureClip(val uri: Uri, val name: String, val bytes: Long, val durationMs: Long)

object CaptureClipLibrary {
    const val RELATIVE_DIR = "Movies/LegionControl/"
    const val PAGE_SIZE = 40
    private val expected = Regex("LegionClip_[0-9]{13}\\.mp4")

    fun createPending(context: Context, name: String): Uri {
        require(expected.matches(name))
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

    /** Server-side pagination; querying the owner's own rows never accesses other apps' media. */
    fun listOwn(context: Context, page: Int = 0): List<CaptureClip> {
        require(page in 0..50_000)
        val selection = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
            "${MediaStore.MediaColumns.IS_PENDING}=0"
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(context.packageName, RELATIVE_DIR))
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Video.Media.DATE_ADDED} DESC, ${MediaStore.Video.Media._ID} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, PAGE_SIZE)
            putInt(ContentResolver.QUERY_ARG_OFFSET, page * PAGE_SIZE)
        }
        val found = ArrayList<CaptureClip>(PAGE_SIZE)
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION),
            args, null
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val value = cursor.getString(name) ?: continue
                if (!expected.matches(value)) continue
                found.add(CaptureClip(
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(id)), value,
                    cursor.getLong(size).coerceAtLeast(0),
                    cursor.getLong(duration).coerceAtLeast(0)
                ))
            }
        }
        return found
    }

    /** Ownership verification is per-item, not restricted to the current UI page. */
    fun isOwnPublished(context: Context, uri: Uri): Boolean {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return false
        if (uri != ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)) return false
        val selection = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
            "${MediaStore.MediaColumns.IS_PENDING}=0"
        return context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.IS_PENDING), selection,
            arrayOf(context.packageName, RELATIVE_DIR), null
        )?.use { cursor ->
            cursor.moveToFirst() && expected.matches(cursor.getString(0) ?: "") &&
                cursor.getString(1) == context.packageName &&
                cursor.getString(2) == RELATIVE_DIR && cursor.getInt(3) == 0
        } == true
    }
}
