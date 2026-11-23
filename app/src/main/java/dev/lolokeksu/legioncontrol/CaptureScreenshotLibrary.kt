package dev.lolokeksu.legioncontrol

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

data class CaptureScreenshot(val uri: Uri, val name: String, val bytes: Long)

/** App-owned PNGs, paginated in MediaStore; no general image permission. */
object CaptureScreenshotLibrary {
    const val RELATIVE_DIR = "Pictures/LegionControl/"
    const val PAGE_SIZE = 40
    private val expected = Regex("LegionShot_[0-9]{13}\\.png")

    fun createPending(context: Context, name: String): Uri {
        require(expected.matches(name))
        return requireNotNull(context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        )) { "MediaStore отказался создать PNG" }
    }

    fun publish(context: Context, uri: Uri) {
        check(context.contentResolver.update(uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null, null) == 1) { "Не удалось опубликовать снимок" }
    }

    fun delete(context: Context, uri: Uri): Boolean =
        context.contentResolver.delete(uri, null, null) > 0

    fun listOwn(context: Context, page: Int = 0): List<CaptureScreenshot> {
        require(page in 0..50_000)
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
                "${MediaStore.MediaColumns.IS_PENDING}=0")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(context.packageName, RELATIVE_DIR))
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, PAGE_SIZE)
            putInt(ContentResolver.QUERY_ARG_OFFSET, page * PAGE_SIZE)
        }
        val output = ArrayList<CaptureScreenshot>(PAGE_SIZE)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE), args, null
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (cursor.moveToNext()) {
                val value = cursor.getString(name) ?: continue
                if (!expected.matches(value)) continue
                output += CaptureScreenshot(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(id)), value, cursor.getLong(size).coerceAtLeast(0)
                )
            }
        }
        return output
    }

    fun isOwnPublished(context: Context, uri: Uri): Boolean {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return false
        if (uri != ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)) return false
        return context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.IS_PENDING),
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
                "${MediaStore.MediaColumns.IS_PENDING}=0",
            arrayOf(context.packageName, RELATIVE_DIR), null
        )?.use { cursor ->
            cursor.moveToFirst() && expected.matches(cursor.getString(0) ?: "") &&
                cursor.getString(1) == context.packageName &&
                cursor.getString(2) == RELATIVE_DIR && cursor.getInt(3) == 0
        } == true
    }
}
