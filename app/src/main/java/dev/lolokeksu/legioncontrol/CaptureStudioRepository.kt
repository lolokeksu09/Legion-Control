package dev.lolokeksu.legioncontrol

import android.content.Context
import android.net.Uri

/**
 * Single source of truth for user-selected capture presets and app-owned MediaStore clips.
 * No MediaProjection token, system consent, foreground service or Activity references live here.
 */
class CaptureStudioRepository(context: Context, private val requestedGame: String? = null) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(CaptureRecordingService.PREFS, Context.MODE_PRIVATE)

    private val profiles = GameCaptureProfiles(app)

    private fun selectedGame(): String? = GameCaptureProfilePolicy.selectGame(
        requestedGame,
        toolboxGame = {
            app.getSharedPreferences(ToolboxPreferences.NAME, Context.MODE_PRIVATE)
                .getString(ToolboxPreferences.GAME, null)
        },
        isLaunchable = { app.packageManager.getLaunchIntentForPackage(it) != null }
    )

    private fun globalProfile(): GameCaptureProfile {
        // Retain the old CaptureConfig normalization for pre-existing preference values.
        val c = CaptureConfig.fromOptions(
            prefs.getInt("edge", 1280), prefs.getInt("fps", 30),
            prefs.getInt("mbit", 4), prefs.getInt("minutes", 5)
        )
        return GameCaptureProfile(
            c.maxEdge, c.fps, c.videoBitrate / 1_000_000,
            (c.maxDurationMs / 60_000).toInt(),
            prefs.getBoolean("playback_audio", false)
        )
    }

    fun captureSettings(): ActiveGameCaptureSettings {
        val game = selectedGame()
        val override = game?.let { profiles.get(it) }
        return ActiveGameCaptureSettings(game, override ?: globalProfile(), override != null)
    }

    private fun settingsForEdit(): ActiveGameCaptureSettings = captureSettings().also {
        check(requestedGame == null || it.selectedGame != null) { "Выбранную игру нельзя запустить" }
    }

    fun playbackAudioEnabled(): Boolean = captureSettings().profile.playbackAudio

    fun choosePlaybackAudio(enabled: Boolean): Boolean {
        val current = settingsForEdit()
        val pkg = current.selectedGame
        if (pkg != null && (requestedGame != null || current.usingGameProfile)) {
            profiles.save(pkg, current.profile.withPlaybackAudio(enabled))
        } else {
            check(prefs.edit().putBoolean("playback_audio", enabled).commit())
        }
        return playbackAudioEnabled()
    }

    fun config(): CaptureConfig = captureSettings().profile.toConfig()

    fun updateOption(name: String, value: Int): CaptureConfig {
        CapturePresetRules.requireSupported(name, value)
        val current = settingsForEdit()
        val pkg = current.selectedGame
        if (pkg != null && (requestedGame != null || current.usingGameProfile)) {
            profiles.save(pkg, current.profile.setOption(name, value))
        } else {
            check(prefs.edit().putInt(name, value).commit())
        }
        return config()
    }

    /** Takes a copy of the current effective settings; does not change global defaults. */
    fun saveSelectedGameProfile(): ActiveGameCaptureSettings {
        val current = captureSettings()
        val pkg = requireNotNull(current.selectedGame) { "Выберите доступную игру" }
        profiles.save(pkg, current.profile)
        return captureSettings()
    }

    fun removeSelectedGameProfile(): ActiveGameCaptureSettings {
        val pkg = requireNotNull(selectedGame()) { "Выберите доступную игру" }
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

    fun lastCaptureResult(): String =
        prefs.getString(CaptureRecordingService.LAST_RESULT, "").orEmpty()

    internal fun mediaPage(query: String, format: CaptureMediaFormat?, after: CaptureMediaKey?): CaptureMediaPage =
        CaptureMediaLibrary.page(app, query, format, after)

    internal fun requireReadableMedia(item: CaptureMedia): Uri {
        val uri = CaptureMediaLibrary.requirePublished(app, item)
        requireNotNull(app.contentResolver.openFileDescriptor(uri, "r")) {
            "Файл недоступен. Обновите медиатеку."
        }.close()
        return uri
    }

    /** Editing is strictly scoped to a published MP4 from the app-owned MediaStore query. */
    internal fun trimOwnClip(
        clip: CaptureMedia, range: ClipTrimSelection, cancelled: () -> Boolean
    ): Uri {
        require(clip.format == CaptureMediaFormat.MP4)
        return ClipWorkshop.trim(app, CaptureMediaLibrary.requirePublished(app, clip), range, cancelled)
    }

    internal fun exportOwnClipFrame(clip: CaptureMedia, atMs: Long?, cancelled: () -> Boolean): Uri {
        require(clip.format == CaptureMediaFormat.MP4)
        return ClipWorkshop.exportFrame(app, CaptureMediaLibrary.requirePublished(app, clip), atMs, cancelled)
    }

    internal fun deleteOwnMedia(item: CaptureMedia): Boolean =
        CaptureMediaLibrary.deletePublished(app, item)
}
