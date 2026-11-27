package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Writes and cleans up MediaStore rows created by the capture service. */
object CaptureScreenshotLibrary {
    const val RELATIVE_DIR = "Pictures/LegionControl/"

    fun createPending(context: Context, name: String): Uri {
        require(CaptureMediaFormat.PNG.acceptsName(name))
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
}
