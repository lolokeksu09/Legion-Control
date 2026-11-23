package dev.lolokeksu.legioncontrol

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Explicit user-approved cleanup; never touches completed recordings. */
data class CapturePendingCleanup(val deleted: Int, val failed: Int, val freedBytes: Long)

object PendingCapturePolicy {
    private val clip = Regex("LegionClip_[0-9]{13}\\.mp4")
    private val shot = Regex("LegionShot_[0-9]{13}\\.png")
    private const val SAFE_AGE_SECONDS = 24L * 60L * 60L

    fun isStale(name: String, video: Boolean, pending: Boolean,
                createdSeconds: Long, nowSeconds: Long): Boolean {
        if (!pending || createdSeconds <= 0 || nowSeconds <= createdSeconds ||
            nowSeconds - createdSeconds < SAFE_AGE_SECONDS) return false
        return (if (video) clip else shot).matches(name)
    }
}

object CapturePendingRecovery {
    /** Requires explicit UI confirmation; no silent disk deletion or background scans. */
    fun cleanStaleOwn(context: Context): CapturePendingCleanup {
        check(CaptureCoordinator.reserveConsent(CaptureMode.MAINTENANCE)) {
            "MediaProjection занят; повторите после завершения захвата."
        }
        try {
            var deleted = 0
            var failed = 0
            var bytes = 0L
            val now = System.currentTimeMillis() / 1000L
            for ((isVideo, uri, relative) in listOf(
                Triple(true, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    CaptureClipLibrary.RELATIVE_DIR),
                Triple(false, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    CaptureScreenshotLibrary.RELATIVE_DIR)
            )) {
                // setIncludePending is supported from API 29; minSdk here is 33.
                val includePending = MediaStore.setIncludePending(uri)
                val columns = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.IS_PENDING
                )
                context.contentResolver.query(
                    includePending, columns,
                    "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND " +
                        "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
                        "${MediaStore.MediaColumns.IS_PENDING}=1",
                    arrayOf(context.packageName, relative),
                    "${MediaStore.MediaColumns.DATE_ADDED} ASC"
                )?.use { cursor ->
                    val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val added = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val pending = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)
                    var checked = 0
                    while (cursor.moveToNext() && checked++ < 1000) {
                        val safeName = cursor.getString(name) ?: continue
                        if (!PendingCapturePolicy.isStale(
                                safeName, isVideo, cursor.getInt(pending) == 1,
                                cursor.getLong(added), now)) continue
                        val target: Uri = ContentUris.withAppendedId(uri, cursor.getLong(id))
                        val success = runCatching {
                            context.contentResolver.delete(target, null, null) == 1
                        }.getOrDefault(false)
                        if (success) {
                            deleted++
                            bytes += cursor.getLong(size).coerceAtLeast(0)
                        } else failed++
                    }
                }
            }
            return CapturePendingCleanup(deleted, failed, bytes)
        } finally {
            CaptureCoordinator.release(CaptureMode.MAINTENANCE)
        }
    }
}
