package dev.lolokeksu.legioncontrol

import android.content.Context
import android.net.Uri

/**
 * Single source of truth for user-selected capture presets and app-owned MediaStore clips.
 * No MediaProjection token, system consent, foreground service or Activity references live here.
 */
class CaptureStudioRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(CaptureRecordingService.PREFS, Context.MODE_PRIVATE)

    private val profiles = GameCaptureProfiles(app)

    /** Only the explicitly selected and currently launchable Game Toolbox app is eligible. */
    fun selectedGame(): String? =
        app.getSharedPreferences(ToolboxPreferences.NAME, Context.MODE_PRIVATE)
            .getString(ToolboxPreferences.GAME, null)
            ?.takeIf {
                GameCaptureProfilePolicy.validPackage(it) &&
                    ApplicationInventory.isPackageName(it) &&
                    app.packageManager.getLaunchIntentForPackage(it) != null
            }

    private fun globalProfile(): GameCaptureProfile = GameCaptureProfile(
        prefs.getInt("edge", 1280),
        prefs.getInt("fps", 30),
        prefs.getInt("mbit", 4),
        prefs.getInt("minutes", 5),
        prefs.getBoolean("playback_audio", false)
    )

    fun captureSettings(): ActiveGameCaptureSettings {
        val game = selectedGame()
        val override = game?.let { profiles.get(it) }
        return ActiveGameCaptureSettings(game, override ?: globalProfile(), override != null)
    }

    fun playbackAudioEnabled(): Boolean = captureSettings().profile.playbackAudio

    fun choosePlaybackAudio(enabled: Boolean): Boolean {
        val current = captureSettings()
        val pkg = current.selectedGame
        if (current.usingGameProfile && pkg != null) {
            profiles.save(pkg, current.profile.withPlaybackAudio(enabled))
        } else {
            check(prefs.edit().putBoolean("playback_audio", enabled).commit())
        }
        return playbackAudioEnabled()
    }

    fun config(): CaptureConfig = captureSettings().profile.toConfig()

    fun updateOption(name: String, value: Int): CaptureConfig {
        CapturePresetRules.requireSupported(name, value)
        val current = captureSettings()
        val pkg = current.selectedGame
        if (current.usingGameProfile && pkg != null) {
            profiles.save(pkg, current.profile.setOption(name, value))
        } else {
            check(prefs.edit().putInt(name, value).commit())
        }
        return config()
    }

    /** Takes a copy of the current effective settings; does not change global defaults. */
    fun saveSelectedGameProfile(): ActiveGameCaptureSettings {
        val current = captureSettings()
        val pkg = requireNotNull(current.selectedGame) { "Сначала выберите игру в Game Toolbox" }
        profiles.save(pkg, current.profile)
        return captureSettings()
    }

    fun removeSelectedGameProfile(): ActiveGameCaptureSettings {
        val pkg = requireNotNull(selectedGame()) { "Сначала выберите игру в Game Toolbox" }
        profiles.delete(pkg)
        return captureSettings()
    }

    fun savedGameProfileCount(): Int = profiles.count()

    /** SAF import/export only: no storage/gallery permissions or background synchronization. */
    fun exportGameProfiles(uri: Uri) {
        val json = profiles.backupJson()
        val stream = requireNotNull(app.contentResolver.openOutputStream(uri, "wt")) {
            "Невозможно открыть файл для записи"
        }
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }

    fun importGameProfiles(uri: Uri): Int {
        val stream = requireNotNull(app.contentResolver.openInputStream(uri)) {
            "Невозможно прочитать файл"
        }
        val text = stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val block = ByteArray(8192)
            while (true) {
                val len = input.read(block)
                if (len < 0) break
                if (len == 0) continue
                require(out.size() + len <= GameCaptureProfilePolicy.MAX_JSON_BYTES) {
                    "Файл профилей превышает лимит 128 КиБ"
                }
                out.write(block, 0, len)
            }
            out.toString(Charsets.UTF_8.name())
        }
        return profiles.restoreJson(text)
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
