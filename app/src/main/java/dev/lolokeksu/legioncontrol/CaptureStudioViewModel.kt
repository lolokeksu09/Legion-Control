package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal sealed interface CaptureLibraryState {
    data object Idle : CaptureLibraryState
    data object Loading : CaptureLibraryState
    data class Ready(val page: CaptureMediaPage, val hasPrevious: Boolean) : CaptureLibraryState
    data class Error(val message: String) : CaptureLibraryState
}

internal data class CaptureMediaAccess(val item: CaptureMedia, val share: Boolean)

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

class CaptureStudioViewModel(
    application: Application,
    gamePackage: String?
) : AndroidViewModel(application) {
    private val repository = CaptureStudioRepository(application, gamePackage)
    private var pendingCaptureConsent: ActiveGameCaptureSettings? = null
    private var pendingReplayConsent: ReplayConsentSettings? = null
    private var pendingReplayAudioPermission: Int? = null
    private var reservedConsent: CaptureMode? = null

    fun reserveConsent(mode: CaptureMode): Boolean {
        if (reservedConsent != null || !CaptureCoordinator.reserveConsent(mode)) return false
        reservedConsent = mode
        return true
    }

    fun takeReservedConsent(mode: CaptureMode): Boolean {
        if (reservedConsent != mode) return false
        reservedConsent = null
        return CaptureCoordinator.isPending(mode)
    }

    fun cancelReservedConsent() {
        reservedConsent?.let(CaptureCoordinator::releasePending)
        reservedConsent = null
    }

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
    private val main = Handler(Looper.getMainLooper())
    private val revision = AtomicInteger(0)
    private var accessGeneration = 0L
    private var libraryVisible = false
    private var libraryRestored = false
    private var closed = false
    private var cursor: CaptureMediaKey? = null
    private val previous = mutableListOf<CaptureMediaKey?>()
    internal var libraryQuery = ""
        private set
    internal var libraryFormat: CaptureMediaFormat? = null
        private set
    private val deferredSearch = Runnable { refreshLibrary() }
    private val currentEdit = AtomicReference<AtomicBoolean?>(null)
    private val libraryMutable = MutableLiveData<CaptureLibraryState>(CaptureLibraryState.Idle)
    internal val library: LiveData<CaptureLibraryState> = libraryMutable
    private val accessMutable = MutableLiveData<CaptureMediaAccess>()
    internal val mediaAccess: LiveData<CaptureMediaAccess> = accessMutable
    private var pendingMediaAccess: CaptureMediaAccess? = null

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

    fun lastCaptureResult(): String = repository.lastCaptureResult()

    fun setOption(name: String, value: Int) {
        val result = runCatching { repository.updateOption(name, value) }
        result.onSuccess { settingsMutable.value = it }
            .onFailure { messageMutable.value = "Не удалось сохранить параметр: " + it.javaClass.simpleName }
    }

    internal fun restoreLibrary(query: String?, format: String?) {
        if (libraryRestored) return
        libraryRestored = true
        libraryQuery = query.orEmpty().take(CaptureMediaLibrary.MAX_SEARCH_LENGTH)
        libraryFormat = CaptureMediaFormat.entries.firstOrNull { it.name == format }
    }

    fun setLibraryVisible(visible: Boolean) {
        if (libraryVisible == visible) return
        libraryVisible = visible
        if (visible) refreshLibrary() else {
            revision.incrementAndGet()
            accessGeneration++
            pendingMediaAccess = null
            main.removeCallbacks(deferredSearch)
        }
    }

    internal fun filterLibrary(query: String, format: CaptureMediaFormat?) {
        if (query == libraryQuery && format == libraryFormat) return
        val formatChanged = format != libraryFormat
        libraryQuery = query.take(CaptureMediaLibrary.MAX_SEARCH_LENGTH)
        libraryFormat = format
        cursor = null
        previous.clear()
        revision.incrementAndGet()
        main.removeCallbacks(deferredSearch)
        if (!libraryVisible) return
        if (formatChanged) refreshLibrary() else {
            libraryMutable.value = CaptureLibraryState.Loading
            main.postDelayed(deferredSearch, 250L)
        }
    }

    fun refreshLibrary() {
        if (!libraryVisible || closed) return
        main.removeCallbacks(deferredSearch)
        val token = revision.incrementAndGet()
        val query = libraryQuery
        val format = libraryFormat
        val after = cursor
        val hasPrevious = previous.isNotEmpty()
        libraryMutable.value = CaptureLibraryState.Loading
        worker.execute {
            if (token != revision.get()) return@execute
            val result = runCatching { repository.mediaPage(query, format, after) }
            main.post {
                if (token != revision.get()) return@post
                libraryMutable.value = result.fold(
                    { CaptureLibraryState.Ready(it, hasPrevious) },
                    { CaptureLibraryState.Error("Не удалось прочитать медиатеку: " + it.javaClass.simpleName) }
                )
            }
        }
    }

    fun nextMediaPage() {
        val current = libraryMutable.value as? CaptureLibraryState.Ready ?: return
        val next = current.page.next ?: return
        previous.add(cursor)
        cursor = next
        refreshLibrary()
    }

    fun previousMediaPage() {
        if (previous.isEmpty()) return
        cursor = previous.removeAt(previous.lastIndex)
        refreshLibrary()
    }

    internal fun accessMedia(item: CaptureMedia, share: Boolean) {
        val request = ++accessGeneration
        worker.execute {
            val result = runCatching { repository.requireReadableMedia(item) }
            main.post {
                if (request != accessGeneration || !libraryVisible || closed) return@post
                result.onSuccess {
                    val access = CaptureMediaAccess(item, share)
                    pendingMediaAccess = access
                    accessMutable.value = access
                }.onFailure {
                    messageMutable.value = "Файл недоступен. Обновите медиатеку."
                    refreshLibrary()
                }
            }
        }
    }

    internal fun takeMediaAccess(event: CaptureMediaAccess): CaptureMediaAccess? =
        pendingMediaAccess?.takeIf { it === event }.also {
            if (it != null) pendingMediaAccess = null
        }

    /** The immutable source clip is never overwritten; each export creates a separate row. */
    internal fun saveClipExcerpt(clip: CaptureMedia, selection: ClipTrimSelection) {
        runClipEdit(
            success = "Фрагмент MP4 сохранён отдельно. Исходный клип не изменён.",
            screenshot = false
        ) { cancelled -> repository.trimOwnClip(clip, selection, cancelled) }
    }

    internal fun saveClipFrame(clip: CaptureMedia, atMs: Long?) {
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
            if (result.isSuccess) main.post { refreshLibrary() }
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
            val app = getApplication<Application>()
            val result = runCatching {
                LegionDataBackup.restoreOnEmptyDatabase(app, uri, confirmed)
            }
            if (result.isSuccess && !LegionDataBackup.hasPendingRestore(app)) {
                (app as? LegionApplication)?.startupRecovery?.markRecovered()
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

    /** Only invoked after confirmation in the Activity. */
    internal fun deleteConfirmed(item: CaptureMedia) {
        worker.execute {
            val result = runCatching { repository.deleteOwnMedia(item) }
            main.post {
                if (closed) return@post
                messageMutable.value = result.fold(
                    { if (it) "Файл удалён." else "Файл уже отсутствует или недоступен." },
                    { "Не удалось удалить файл: " + it.javaClass.simpleName }
                )
                refreshLibrary()
            }
        }
    }

    override fun onCleared() {
        closed = true
        cancelReservedConsent()
        pendingCaptureConsent = null
        pendingReplayConsent = null
        pendingReplayAudioPermission = null
        pendingMediaAccess = null
        currentEdit.get()?.set(true)
        main.removeCallbacks(deferredSearch)
        revision.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
