package dev.lolokeksu.legioncontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
    private val worker = Executors.newSingleThreadExecutor()
    private val revision = AtomicInteger(0)
    private val screenshotRevision = AtomicInteger(0)
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
        revision.incrementAndGet()
        screenshotRevision.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
