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
data class ReplayConsentSettings(
    val seconds: Int,
    val config: CaptureConfig,
    val playbackAudio: Boolean,
    val selectedGame: String?
)

class CaptureStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CaptureStudioRepository(application)
    private var pendingCaptureConsent: ActiveGameCaptureSettings? = null
    private var pendingReplayConsent: ReplayConsentSettings? = null
    private var pendingReplayAudioPermission: Int? = null

    fun rememberReplayAudioPermission(seconds: Int) {
        require(seconds in setOf(30, 60, 120))
        pendingReplayAudioPermission = seconds
    }

    fun takeReplayAudioPermission(): Int? =
        pendingReplayAudioPermission.also { pendingReplayAudioPermission = null }

    fun rememberReplayConsent(seconds: Int, selected: ActiveGameCaptureSettings) {
        require(seconds in setOf(30, 60, 120))
        require(!selected.profile.playbackAudio || selected.selectedGame != null)
        pendingReplayConsent = ReplayConsentSettings(
            seconds, selected.profile.toConfig(),
            selected.profile.playbackAudio, selected.selectedGame)
    }

    fun takeReplayConsent(): ReplayConsentSettings? =
        pendingReplayConsent.also { pendingReplayConsent = null }

    fun clearReplayConsent() { pendingReplayConsent = null }

    private val worker = Executors.newSingleThreadExecutor()
    private val revision = AtomicInteger(0)
    private val screenshotRevision = AtomicInteger(0)
    @Volatile private var clipsPage = 0
    @Volatile private var shotsPage = 0

    fun clipsPageNumber(): Int = clipsPage + 1
    fun screenshotsPageNumber(): Int = shotsPage + 1

    fun nextClipPage() { clipsPage = (clipsPage + 1).coerceAtMost(50_000); refresh() }
    fun previousClipPage() { clipsPage = (clipsPage - 1).coerceAtLeast(0); refresh() }
    fun nextScreenshotPage() { shotsPage = (shotsPage + 1).coerceAtMost(50_000); refreshScreenshots() }
    fun previousScreenshotPage() { shotsPage = (shotsPage - 1).coerceAtLeast(0); refreshScreenshots() }
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
        val requestedPage = clipsPage
        worker.execute {
            val result = runCatching { repository.clips(requestedPage) }
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
                    val updated = runCatching { repository.screenshots(shotsPage) }
                    screenshotsMutable.postValue(updated.fold(
                        { CaptureScreenshotsState.Ready(it) },
                        { CaptureScreenshotsState.Error("Не удалось открыть PNG: " + it.javaClass.simpleName) }
                    ))
                } else {
                    val updated = runCatching { repository.clips(clipsPage) }
                    libraryMutable.postValue(updated.fold(
                        { CaptureLibraryState.Ready(it) },
                        { CaptureLibraryState.Error("Не удалось открыть MP4: " + it.javaClass.simpleName) }
                    ))
                }
            }
        }
    }

    data class BackupReady(val uri: android.net.Uri, val preview: LegionBackupPreview)
    private val backupReadyMutable = MutableLiveData<Boolean>()
    val backupReady: LiveData<Boolean> = backupReadyMutable
    @Volatile private var pendingBackup: BackupReady? = null
    fun takeBackupPreview(): BackupReady? = pendingBackup.also { pendingBackup = null }

    fun exportFullBackup(uri: android.net.Uri) {
        worker.execute {
            val result = runCatching { LegionDataBackup.export(getApplication(), uri) }
            messageMutable.postValue(result.fold(
                { "Архив создан: таблиц ${it.tables}, записей ${it.rows}, параметров ${it.settings}; сетевых тестов ${it.networkRuns}, батарейных ${it.batteryRuns}." },
                { "Не удалось создать архив: " + (it.message ?: it.javaClass.simpleName) }
            ))
        }
    }

    fun inspectFullBackup(uri: android.net.Uri) {
        worker.execute {
            val result = runCatching { LegionDataBackup.preview(getApplication(), uri) }
            result.onSuccess {
                pendingBackup = BackupReady(uri, it)
                backupReadyMutable.postValue(true)
            }.onFailure {
                messageMutable.postValue("Некорректный архив: " +
                    (it.message ?: it.javaClass.simpleName))
            }
        }
    }

    fun restoreFullBackup(uri: android.net.Uri, confirmed: BackupArchiveIdentity) {
        worker.execute {
            val result = runCatching {
                LegionDataBackup.restoreOnEmptyDatabase(getApplication(), uri, confirmed)
            }
            messageMutable.postValue(result.fold(
                { "Восстановлено: таблиц ${it.tables}, записей ${it.rows}, параметров ${it.settings}." },
                { "Восстановление отклонено: " + (it.message ?: it.javaClass.simpleName) }
            ))
        }
    }

    /** Retry a durable recovery record without reimporting or deleting SQLite. */
    fun retryInterruptedRestore() {
        worker.execute {
            val result = runCatching {
                check(!GameSessionRecorderService.sessionActive) {
                    "Завершите игровую сессию перед восстановлением."
                }
                check(CaptureCoordinator.reserveConsent(CaptureMode.MAINTENANCE)) {
                    "Остановите захват MP4/Replay и повторите."
                }
                val app = getApplication<Application>()
                val recovered = try { LegionDataBackup.recoverPendingRestore(app) }
                    finally { CaptureCoordinator.release(CaptureMode.MAINTENANCE) }
                if (!LegionDataBackup.hasPendingRestore(app)) {
                    (app as? LegionApplication)?.startupRecovery?.markRecovered()
                }
                recovered
            }
            messageMutable.postValue(result.fold(
                { if(it) "Прерванное восстановление настроек завершено. Перечитайте экраны." else
                    "Нет подтверждённого незавершённого импорта SQLite: настройки не менялись." },
                { "Не удалось завершить восстановление: " + (it.message ?: it.javaClass.simpleName) }
            ))
        }
    }

    fun showCaptureStorageUsage() {
        worker.execute {
            val result = runCatching {
                CaptureStorageOverview.report(getApplication())
            }
            messageMutable.postValue(result.getOrElse {
                "Не удалось прочитать место: " + (it.message ?: it.javaClass.simpleName)
            })
        }
    }

    fun cleanStalePending() {
        worker.execute {
            val result = runCatching {
                CapturePendingRecovery.cleanStaleOwn(getApplication())
            }
            messageMutable.postValue(result.fold(
                { "Очистка завершена. Удалено незавершённых файлов: ${it.deleted}; " +
                    "ошибок: ${it.failed}; освобождено: ${it.freedBytes / 1024L} КиБ." },
                { "Очистка отклонена: " + (it.message ?: it.javaClass.simpleName) }
            ))
        }
    }

    fun refreshScreenshots() {
        val token = screenshotRevision.incrementAndGet()
        screenshotsMutable.value = CaptureScreenshotsState.Loading
        val requestedPage = shotsPage
        worker.execute {
            val result = runCatching { repository.screenshots(requestedPage) }
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
            val shots = runCatching { repository.screenshots(shotsPage) }
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
            val clips = runCatching { repository.clips(clipsPage) }
            libraryMutable.postValue(clips.fold(
                { CaptureLibraryState.Ready(it) },
                { CaptureLibraryState.Error("Не удалось обновить библиотеку: " + it.javaClass.simpleName) }
            ))
        }
    }

    override fun onCleared() {
        pendingCaptureConsent = null
        pendingReplayConsent = null
        pendingReplayAudioPermission = null
        currentEdit.get()?.set(true)
        revision.incrementAndGet()
        screenshotRevision.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
