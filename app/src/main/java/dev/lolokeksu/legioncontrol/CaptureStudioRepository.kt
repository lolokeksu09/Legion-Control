package dev.lolokeksu.legioncontrol

import android.content.Context

/**
 * Single source of truth for user-selected capture presets and app-owned MediaStore clips.
 * No MediaProjection token, system consent, foreground service or Activity references live here.
 */
class CaptureStudioRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(CaptureRecordingService.PREFS, Context.MODE_PRIVATE)

    fun playbackAudioEnabled(): Boolean = prefs.getBoolean("playback_audio", false)

    fun choosePlaybackAudio(enabled: Boolean): Boolean {
        check(prefs.edit().putBoolean("playback_audio", enabled).commit())
        return playbackAudioEnabled()
    }

    fun config(): CaptureConfig = CaptureConfig.fromOptions(
        prefs.getInt("edge", 1280), prefs.getInt("fps", 30),
        prefs.getInt("mbit", 4), prefs.getInt("minutes", 5)
    )

    fun updateOption(name: String, value: Int): CaptureConfig {
        CapturePresetRules.requireSupported(name, value)
        prefs.edit().putInt(name, value).apply()
        return config()
    }

    fun status(): String {
        val running = CaptureRecordingService.recording
        val last = prefs.getString(CaptureRecordingService.LAST_RESULT,
            "Клипов ещё нет. Для первой записи нажмите «Начать» и подтвердите запрос Android.")
        val header = when {
            running && CaptureRecordingService.paused -> "ЗАПИСЬ НА ПАУЗЕ\\n"
            running -> "ЗАПИСЬ АКТИВНА\\n"
            else -> "Запись не активна\\n"
        }
        return header + last
    }

    fun clips(): List<CaptureClip> = CaptureClipLibrary.listOwn(app)

    /** Editing is strictly scoped to a published item from the app-owned MediaStore query. */
    fun trimOwnClip(
        clip: CaptureClip, range: ClipTrimSelection, cancelled: () -> Boolean
    ) = ClipWorkshop.trim(app, requireOwnClip(clip).uri, range, cancelled)

    fun exportOwnClipFrame(
        clip: CaptureClip, atMs: Long?, cancelled: () -> Boolean
    ) = ClipWorkshop.exportFrame(app, requireOwnClip(clip).uri, atMs, cancelled)

    private fun requireOwnClip(clip: CaptureClip): CaptureClip =
        clips().firstOrNull { it.uri == clip.uri }
            ?: throw IllegalArgumentException("Selected clip is not in own library")

    fun screenshots(): List<CaptureScreenshot> = CaptureScreenshotLibrary.listOwn(app)

    fun deleteOwnScreenshot(screenshot: CaptureScreenshot): Boolean {
        require(screenshots().any { it.uri == screenshot.uri }) {
            "Selected screenshot is not in own library"
        }
        return CaptureScreenshotLibrary.delete(app, screenshot.uri)
    }

    /** The dialog-confirmed URI comes only from this app's own MediaStore query. */
    fun deleteOwnClip(clip: CaptureClip): Boolean {
        require(clips().any { it.uri == clip.uri }) { "Selected clip is not in own library" }
        return CaptureClipLibrary.delete(app, clip.uri)
    }
}
