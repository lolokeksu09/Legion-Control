package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class HapticLabUiState(
    val message: String,
    val playing: Boolean,
    val nonce: Int = 0,
    val runs: List<HapticTestRun>? = null
)

/**
 * Foreground manual vibration controller and private history. The UI owns picker/dialog
 * permissions; the backend owns the actual Android vibrator and serialized SQLite writes.
 */
class HapticLabViewModel(application: Application) : AndroidViewModel(application) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val backend = HapticPlaybackBackend(application)
    private val closed = AtomicBoolean()
    private val visible = AtomicBoolean()
    private val playing = AtomicBoolean()
    private val generation = AtomicInteger()
    private val mutableState = MutableLiveData<HapticLabUiState>()
    val state: LiveData<HapticLabUiState> = mutableState
    var selectedRunId: String? = null
        private set
    var lastReport: String? = null
        private set
    private var latestCapabilities: HapticCapabilitySnapshot? = null

    fun setVisible(value: Boolean) { visible.set(value) }
    fun stopPlayback() { backend.stop() }

    fun startTest(kind: String, level: String) {
        if (!visible.get() || !playing.compareAndSet(false,true) || closed.get()) return
        val waveform = runCatching { HapticPattern.build(kind,level) }.getOrElse {
            playing.set(false)
            mutableState.value = HapticLabUiState("Недопустимый шаблон вибрации.",false)
            return
        }
        backend.arm()
        val request = generation.incrementAndGet()
        mutableState.value = HapticLabUiState(
            "Запрос Android: $kind / $level · ${waveform.durationMs} мс…",true)
        worker.execute {
            var runId: String? = null
            val message = runCatching {
                HapticLabRepository(getApplication<Application>()).use { repo ->
                    val id=repo.start(waveform)
                    runId=id
                    val response=backend.perform(waveform)
                    repo.finish(id,response)
                    repo.render(id)
                }
            }.getOrElse { e ->
                "Ошибка Haptic Lab: " + e.javaClass.simpleName +
                    " · " + e.message.orEmpty().take(140)
            }
            playing.set(false)
            main.post {
                if (!closed.get() && generation.get() == request) {
                    selectedRunId=runId
                    lastReport=message
                    mutableState.value = HapticLabUiState(message,false,request)
                }
            }
        }
    }

    fun inspect() = local {
        val snapshot=HapticDeviceInspector.capture(getApplication<Application>())
        latestCapabilities=snapshot
        snapshot.report()
    }

    fun saveCapabilities() = local {
        val measured=latestCapabilities
            ?: return@local "Сначала нажмите «Проверить Android Vibra-capabilities»."
        HapticLabRepository(getApplication<Application>()).use { it.saveSnapshot(measured) }
        "Сохранён локальный снимок возможностей Android.\n" + measured.report()
    }

    fun savedCapabilities() = local {
        HapticLabRepository(getApplication<Application>()).use {
            it.latestSnapshot()?.report() ?: "Снимков возможностей пока нет."
        }
    }

    fun chooseRun() = localWithRuns {
        HapticLabRepository(getApplication<Application>()).use { it.runs() }
    }

    fun selectRun(id: String) = local {
        selectedRunId = id
        HapticLabRepository(getApplication<Application>()).use { it.render(id) }
    }

    fun rate(id: String, rating: String, note: String) = local {
        HapticLabRepository(getApplication<Application>()).use { repo ->
            repo.rate(id,rating,note)
            repo.render(id)
        }
    }

    fun compare() = local {
        HapticLabRepository(getApplication<Application>()).use { it.compareRecent() }
    }

    fun delete(id: String) = local {
        val removed=HapticLabRepository(getApplication<Application>()).use { it.delete(id) }
        if (removed) {
            selectedRunId=null
            "Тактильная запись удалена. Другие данные остались."
        } else "Запись уже отсутствует."
    }

    fun exportCsv(uri: Uri) = local {
        HapticLabRepository(getApplication<Application>()).use { repo ->
            (getApplication<Application>().contentResolver.openOutputStream(uri,"w")
                ?: error("No document stream")).use { stream ->
                OutputStreamWriter(stream,Charsets.UTF_8).use { writer -> repo.exportCsv(writer) }
            }
        }
        "Экспорт выполнен в выбранный CSV, без фоновой передачи данных."
    }

    private fun local(job: () -> String) {
        if (playing.get()) {
            mutableState.value = HapticLabUiState(
                "Остановите текущую вибрацию перед следующей операцией.",true)
            return
        }
        if (closed.get()) return
        val request=generation.incrementAndGet()
        mutableState.value=HapticLabUiState("Чтение локальных данных…",false)
        worker.execute {
            val message=runCatching { job() }.getOrElse { e ->
                "Ошибка: " + e.javaClass.simpleName + " · " + e.message.orEmpty().take(140)
            }
            main.post {
                if (!closed.get() && generation.get()==request) {
                    lastReport=message
                    mutableState.value=HapticLabUiState(message,false,request)
                }
            }
        }
    }

    private fun localWithRuns(job: () -> List<HapticTestRun>) {
        if (playing.get() || closed.get()) return
        val request=generation.incrementAndGet()
        mutableState.value=HapticLabUiState("Чтение локальных данных…",false)
        worker.execute {
            val result=runCatching { job() }
            main.post {
                if (!closed.get() && generation.get()==request) {
                    val runs=result.getOrNull()
                    mutableState.value=HapticLabUiState(
                        if (runs==null) "Ошибка: " + result.exceptionOrNull()?.javaClass?.simpleName
                        else "Найдено сохранённых тестов: ${runs.size}.",
                        false,request,runs)
                }
            }
        }
    }

    /** Prevent old chooser events from reopening on a configuration change. */
    fun consumePicker(nonce: Int) {
        val current=mutableState.value ?: return
        if (current.nonce==nonce && current.runs!=null) {
            mutableState.value=current.copy(runs=null)
        }
    }

    override fun onCleared() {
        closed.set(true)
        backend.stop()
        generation.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
