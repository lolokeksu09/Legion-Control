package dev.lolokeksu.legioncontrol

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class CaptureScreenshot(val uri: Uri, val name: String, val bytes: Long)

/** Own-only MediaStore image collection; not broad gallery or storage access. */
object CaptureScreenshotLibrary {
    const val RELATIVE_DIR = "Pictures/LegionControl/"

    fun createPending(context: Context, name: String): Uri {
        require(name.matches(Regex("LegionShot_[0-9]{13}\\.png")))
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

    fun listOwn(context: Context): List<CaptureScreenshot> {
        val output = ArrayList<CaptureScreenshot>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE),
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
                "${MediaStore.MediaColumns.IS_PENDING}=0",
            arrayOf(context.packageName, RELATIVE_DIR),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (cursor.moveToNext() && output.size < 100) {
                val value = cursor.getString(name) ?: continue
                if (!value.matches(Regex("LegionShot_[0-9]{13}\\.png"))) continue
                output += CaptureScreenshot(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(id)),
                    value,
                    cursor.getLong(size).coerceAtLeast(0)
                )
            }
        }
        return output
    }
}
