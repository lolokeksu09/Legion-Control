package dev.lolokeksu.legioncontrol

import android.content.Context

/**
 * Single source of truth for user-selected capture presets and app-owned MediaStore clips.
 * No MediaProjection token, system consent, foreground service or Activity references live here.
 */
class CaptureStudioRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(CaptureRecordingService.PREFS, Context.MODE_PRIVATE)

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
        return (if (running) "ЗАПИСЬ АКТИВНА\n" else "Запись не активна\n") + last
    }

    fun clips(): List<CaptureClip> = CaptureClipLibrary.listOwn(app)

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
