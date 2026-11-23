package dev.lolokeksu.legioncontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface CaptureLibraryState {
    data object Idle : CaptureLibraryState
    data object Loading : CaptureLibraryState
    data class Ready(val clips: List<CaptureClip>) : CaptureLibraryState
    data class Error(val message: String) : CaptureLibraryState
}

sealed interface CaptureScreenshotsState {
    data object Idle : CaptureScreenshotsState
    data object Loading : CaptureScreenshotsState
    data class Ready(val shots: List<CaptureScreenshot>) : CaptureScreenshotsState
    data class Error(val message: String) : CaptureScreenshotsState
}

/**
 * Owns MediaStore library I/O across Activity recreation.
 * Does not hold, store or request a MediaProjection token. Explicit system consent remains in View.
 */
class CaptureStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CaptureStudioRepository(application)
    private var pendingCaptureConsent: ActiveGameCaptureSettings? = null
    private var pendingReplayConsent: Pair<Int, CaptureConfig>? = null

    fun rememberReplayConsent(seconds: Int, config: CaptureConfig) {
        require(seconds in setOf(30, 60, 120))
        pendingReplayConsent = seconds to config
    }

    fun takeReplayConsent(): Pair<Int, CaptureConfig>? =
        pendingReplayConsent.also { pendingReplayConsent = null }

    fun clearReplayConsent() { pendingReplayConsent = null }

    private val worker = Executors.newSingleThreadExecutor()
    private val revision = AtomicInteger(0)
    private val screenshotRevision = AtomicInteger(0)
    private val currentEdit = AtomicReference<AtomicBoolean?>(null)
    private val libraryMutable = MutableLiveData<CaptureLibraryState>(CaptureLibraryState.Idle)
    val library: LiveData<CaptureLibraryState> = libraryMutable

    private val screenshotsMutable = MutableLiveData<CaptureScreenshotsState>(CaptureScreenshotsState.Idle)
    val screenshots: LiveData<CaptureScreenshotsState> = screenshotsMutable

    private val messageMutable = MutableLiveData<String>()
    val message: LiveData<String> = messageMutable

    private val settingsMutable = MutableLiveData(repository.config())
    val settings: LiveData<CaptureConfig> = settingsMutable

    private val playbackAudioMutable = MutableLiveData(repository.playbackAudioEnabled())
    val playbackAudio: LiveData<Boolean> = playbackAudioMutable

    fun captureSettings(): ActiveGameCaptureSettings = repository.captureSettings()

    /** Freeze package, video parameters and audio flag for this one Android consent. */
    fun rememberCaptureConsent(settings: ActiveGameCaptureSettings) {
        pendingCaptureConsent = settings
    }

    fun takeCaptureConsent(): ActiveGameCaptureSettings? =
        pendingCaptureConsent.also { pendingCaptureConsent = null }

    fun clearCaptureConsent() { pendingCaptureConsent = null }

    fun refreshSelectedGameSettings() {
        val settings = repository.captureSettings()
        settingsMutable.value = settings.profile.toConfig()
        playbackAudioMutable.value = settings.profile.playbackAudio
    }

    fun saveSelectedGameProfile() {
        runCatching { repository.saveSelectedGameProfile() }
            .onSuccess {
                refreshSelectedGameSettings()
                messageMutable.value = "Профиль записи для игры сохранён. Изменения параметров теперь относятся только к ней."
            }.onFailure {
                messageMutable.value = "Не удалось сохранить профиль: " + (it.message ?: it.javaClass.simpleName)
            }
    }

    fun removeSelectedGameProfile() {
        runCatching { repository.removeSelectedGameProfile() }
            .onSuccess {
                refreshSelectedGameSettings()
                messageMutable.value = "Профиль игры сброшен. Теперь действуют общие настройки записи."
            }.onFailure {
                messageMutable.value = "Не удалось сбросить профиль: " + (it.message ?: it.javaClass.simpleName)
            }
    }

    fun savedGameProfileCount(): Int = repository.savedGameProfileCount()

    fun exportGameProfiles(uri: android.net.Uri) {
        worker.execute {
            val result = runCatching { repository.exportGameProfiles(uri) }
            messageMutable.postValue(result.fold(
                { "Профили записи экспортированы в выбранный JSON-файл." },
                { "Ошибка экспорта профилей: " + (it.message ?: it.javaClass.simpleName) }
            ))
        }
    }

    fun importGameProfiles(uri: android.net.Uri) {
        worker.execute {
            val result = runCatching { repository.importGameProfiles(uri) }
            result.onSuccess {
                val fresh = repository.captureSettings()
                settingsMutable.postValue(fresh.profile.toConfig())
                playbackAudioMutable.postValue(fresh.profile.playbackAudio)
            }
            messageMutable.postValue(result.fold(
                { "Импортировано профилей: $it. Предыдущие игровые профили заменены, общие настройки не затронуты." },
                { "Импорт отменён: " + (it.message ?: it.javaClass.simpleName) }
            ))
        }
    }

    fun playbackAudioEnabled(): Boolean = repository.playbackAudioEnabled()

    fun choosePlaybackAudio(enabled: Boolean) {
        runCatching { repository.choosePlaybackAudio(enabled) }
            .onSuccess { playbackAudioMutable.value = it }
            .onFailure {
                messageMutable.value = "Не удалось изменить режим звука: " + it.javaClass.simpleName
            }
    }

    fun config(): CaptureConfig = repository.config()
    fun status(): String = repository.status()

    fun setOption(name: String, value: Int) {
        val result = runCatching { repository.updateOption(name, value) }
        result.onSuccess { settingsMutable.value = it }
            .onFailure { messageMutable.value = "Не удалось сохранить параметр: " + it.javaClass.simpleName }
    }

    fun refresh() {
        val token = revision.incrementAndGet()
        libraryMutable.value = CaptureLibraryState.Loading
        worker.execute {
            val result = runCatching { repository.clips() }
            if (token != revision.get()) return@execute
            libraryMutable.postValue(result.fold(
                { CaptureLibraryState.Ready(it) },
                { CaptureLibraryState.Error("Не удалось прочитать библиотеку: " + it.javaClass.simpleName) }
            ))
        }
    }

    /** The immutable source clip is never overwritten; each export creates a separate row. */
    fun saveClipExcerpt(clip: CaptureClip, selection: ClipTrimSelection) {
        runClipEdit(
            success = "Фрагмент MP4 сохранён отдельно. Исходный клип не изменён.",
            screenshot = false
        ) { cancelled -> repository.trimOwnClip(clip, selection, cancelled) }
    }

    fun saveClipFrame(clip: CaptureClip, atMs: Long?) {
        runClipEdit(
            success = "Кадр PNG сохранён в Pictures/LegionControl/.",
            screenshot = true
        ) { cancelled -> repository.exportOwnClipFrame(clip, atMs, cancelled) }
    }

    fun cancelClipEdit() {
        if (currentEdit.get()?.let { it.set(true); true } == true) {
            messageMutable.value = "Отмена экспорта запрошена. Незавершённый файл будет удалён."
        } else {
            messageMutable.value = "Нет активной обработки клипа."
        }
    }

    private fun runClipEdit(
        success: String,
        screenshot: Boolean,
        action: ((() -> Boolean)) -> android.net.Uri
    ) {
        val token = AtomicBoolean(false)
        if (!currentEdit.compareAndSet(null, token)) {
            messageMutable.value = "Другая операция с клипом уже выполняется."
            return
        }
        messageMutable.value = if (screenshot) "Сохраняю PNG-кадр…" else "Создаю MP4-фрагмент…"
        worker.execute {
            val result = runCatching {
                action { token.get() || Thread.currentThread().isInterrupted }
            }
            currentEdit.compareAndSet(token, null)
            val message = result.fold(
                { success },
                {
                    if (token.get() || it is InterruptedException) "Экспорт отменён."
                    else "Экспорт не удался: " + it.javaClass.simpleName + " · " +
                        (it.message ?: "проверьте исходный файл")
                }
            )
            messageMutable.postValue(message)
            if (result.isSuccess) {
                if (screenshot) {
                    val updated = runCatching { repository.screenshots() }
                    screenshotsMutable.postValue(updated.fold(
                        { CaptureScreenshotsState.Ready(it) },
                        { CaptureScreenshotsState.Error("Не удалось открыть PNG: " + it.javaClass.simpleName) }
                    ))
                } else {
                    val updated = runCatching { repository.clips() }
                    libraryMutable.postValue(updated.fold(
                        { CaptureLibraryState.Ready(it) },
                        { CaptureLibraryState.Error("Не удалось открыть MP4: " + it.javaClass.simpleName) }
                    ))
                }
            }
        }
    }

    fun refreshScreenshots() {
        val token = screenshotRevision.incrementAndGet()
        screenshotsMutable.value = CaptureScreenshotsState.Loading
        worker.execute {
            val result = runCatching { repository.screenshots() }
            if (token != screenshotRevision.get()) return@execute
            screenshotsMutable.postValue(result.fold(
                { CaptureScreenshotsState.Ready(it) },
                { CaptureScreenshotsState.Error("Не удалось прочитать снимки: " + it.javaClass.simpleName) }
            ))
        }
    }

    fun deleteConfirmedScreenshot(shot: CaptureScreenshot) {
        val token = screenshotRevision.incrementAndGet()
        screenshotsMutable.value = CaptureScreenshotsState.Loading
        worker.execute {
            val deleted = runCatching { repository.deleteOwnScreenshot(shot) }
            if (token != screenshotRevision.get()) return@execute
            messageMutable.postValue(deleted.fold(
                { if (it) "Снимок удалён." else "Снимок не удалось удалить." },
                { "Удаление PNG отклонено: " + it.javaClass.simpleName }
            ))
            val shots = runCatching { repository.screenshots() }
            if (token != screenshotRevision.get()) return@execute
            screenshotsMutable.postValue(shots.fold(
                { CaptureScreenshotsState.Ready(it) },
                { CaptureScreenshotsState.Error("Не удалось обновить снимки: " + it.javaClass.simpleName) }
            ))
        }
    }

    /** Only invoked after a confirmation in the Activity. */
    fun deleteConfirmed(clip: CaptureClip) {
        revision.incrementAndGet() // invalidate any earlier browsing result
        libraryMutable.value = CaptureLibraryState.Loading
        worker.execute {
            val result = runCatching { repository.deleteOwnClip(clip) }
            messageMutable.postValue(result.fold(
                { if (it) "Видео удалено." else "Файл уже отсутствует или не удалён." },
                { "Удаление не удалось: " + it.javaClass.simpleName }
            ))
            val clips = runCatching { repository.clips() }
            libraryMutable.postValue(clips.fold(
                { CaptureLibraryState.Ready(it) },
                { CaptureLibraryState.Error("Не удалось обновить библиотеку: " + it.javaClass.simpleName) }
            ))
        }
    }

    override fun onCleared() {
        pendingCaptureConsent = null
        pendingReplayConsent = null
        currentEdit.get()?.set(true)
        revision.incrementAndGet()
        screenshotRevision.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
