package dev.lolokeksu.legioncontrol

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns

internal enum class CaptureMediaFormat(
    val mimeType: String,
    val directory: String,
    private val prefix: String
) {
    MP4("video/mp4", CaptureClipLibrary.RELATIVE_DIR, "LegionClip"),
    PNG("image/png", CaptureScreenshotLibrary.RELATIVE_DIR, "LegionShot");

    private val suffix = name.lowercase()
    private val expectedName = Regex("${prefix}_[0-9]{13}\\.$suffix")
    val nameGlob = "${prefix}_${"[0-9]".repeat(13)}.$suffix"

    fun acceptsName(name: String): Boolean = expectedName.matches(name)

    val collection: Uri get() = when (this) {
        MP4 -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        PNG -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}

internal data class CaptureMediaKey(val addedSeconds: Long, val id: Long)

internal data class CaptureMedia(
    val key: CaptureMediaKey,
    val uri: Uri,
    val format: CaptureMediaFormat,
    val name: String,
    val bytes: Long,
    val durationMs: Long
)

internal data class CaptureMediaPage(val items: List<CaptureMedia>, val next: CaptureMediaKey?)

internal object CaptureMediaLibrary {
    const val PAGE_SIZE = 40
    const val MAX_SEARCH_LENGTH = 96

    private val collection get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private const val OWNER_FILTER = "${MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
        "${MediaColumns.IS_PENDING}=0 AND ${MediaColumns.IS_TRASHED}=0"
    private const val FORMAT_FILTER = "(${MediaColumns.RELATIVE_PATH}=? AND " +
        "${MediaColumns.MIME_TYPE}=? AND ${MediaColumns.DISPLAY_NAME} GLOB ?)"

    /** Search by literal filename fragment. MediaStore performs matching before paging. */
    internal fun searchPattern(query: String): String =
        "%" + query.trim().take(MAX_SEARCH_LENGTH)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

    fun page(
        context: Context, query: String = "", format: CaptureMediaFormat? = null,
        after: CaptureMediaKey? = null
    ): CaptureMediaPage {
        val formats = if (format == null) CaptureMediaFormat.entries else listOf(format)
        val clauses = mutableListOf(OWNER_FILTER, formats.joinToString(" OR ", "(", ")") { FORMAT_FILTER })
        val values = mutableListOf(context.packageName)
        formats.forEach { values.addAll(formatArgs(it)) }
        if (query.isNotBlank()) {
            clauses += "${MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '!'"
            values += searchPattern(query)
        }
        if (after != null) {
            clauses += "(${MediaColumns.DATE_ADDED}<? OR " +
                "(${MediaColumns.DATE_ADDED}=? AND ${MediaColumns._ID}<?))"
            values += listOf(after.addedSeconds.toString(), after.addedSeconds.toString(), after.id.toString())
        }
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, clauses.joinToString(" AND "))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, values.toTypedArray())
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaColumns.DATE_ADDED, MediaColumns._ID))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, PAGE_SIZE + 1)
        }
        val selection = arrayOf(MediaColumns._ID, MediaColumns.DATE_ADDED, MediaColumns.MIME_TYPE,
            MediaColumns.DISPLAY_NAME, MediaColumns.SIZE, MediaColumns.DURATION)
        val items = requireNotNull(context.contentResolver.query(collection, selection, args, null)) {
            "MediaStore не вернул список файлов"
        }.use { cursor ->
            buildList {
                while (size <= PAGE_SIZE && cursor.moveToNext()) {
                    val kind = formats.firstOrNull { it.mimeType == cursor.getString(2) } ?: continue
                    val name = cursor.getString(3)?.takeIf(kind::acceptsName) ?: continue
                    val id = cursor.getLong(0)
                    add(CaptureMedia(CaptureMediaKey(cursor.getLong(1), id),
                        ContentUris.withAppendedId(kind.collection, id), kind, name,
                        cursor.getLong(4).coerceAtLeast(0), cursor.getLong(5).coerceAtLeast(0)))
                }
            }
        }
        return CaptureMediaPage(items.take(PAGE_SIZE), items.getOrNull(PAGE_SIZE - 1)?.key
            ?.takeIf { items.size > PAGE_SIZE })
    }

    /** Check a canonical item URI and the exact identity at the time of an action. */
    fun requirePublished(context: Context, item: CaptureMedia): Uri {
        require(item.uri == ContentUris.withAppendedId(item.format.collection, item.key.id))
        val args = arrayOf(context.packageName, *formatArgs(item.format), item.name, item.key.addedSeconds.toString())
        val found = context.contentResolver.query(item.uri, arrayOf(MediaColumns._ID),
            itemFilter(), args, null)?.use { it.moveToFirst() } == true
        check(found) { "Файл недоступен. Обновите медиатеку." }
        return item.uri
    }

    /** Include ownership and identity in the delete itself, even if the row changes after listing. */
    fun deletePublished(context: Context, item: CaptureMedia): Boolean {
        require(item.uri == ContentUris.withAppendedId(item.format.collection, item.key.id))
        return context.contentResolver.delete(item.uri, itemFilter(),
            arrayOf(context.packageName, *formatArgs(item.format), item.name, item.key.addedSeconds.toString())) == 1
    }

    private fun formatArgs(format: CaptureMediaFormat): Array<String> =
        arrayOf(format.directory, format.mimeType, format.nameGlob)

    private fun itemFilter(): String =
        "$OWNER_FILTER AND $FORMAT_FILTER AND ${MediaColumns.DISPLAY_NAME}=? AND ${MediaColumns.DATE_ADDED}=?"
}
