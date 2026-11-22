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

data class SessionIncidentsUiState(
    val text: String,
    val request: Int = 0,
    val choices: List<GameSessionHeader>? = null,
    val report: SessionIncidentsReport? = null
)

/** SQLite + SAF are accessed only on one worker; no background telemetry or network calls. */
class SessionIncidentsViewModel(app: Application) : AndroidViewModel(app) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val closed = AtomicBoolean()
    private val mutable = MutableLiveData(SessionIncidentsUiState("Выберите сохранённую сессию."))
    val state: LiveData<SessionIncidentsUiState> = mutable

    @Volatile var selectedId: String? = null
        private set
    @Volatile var selectedKind: SessionIncidents.Kind? = null
        private set
    @Volatile private var filterPackage: String? = null
    @Volatile private var lastReport: SessionIncidentsReport? = null

    fun setPackageFilter(pkg: String?) {
        if (pkg != null && SmartProfilesRepository.validatePackage(pkg)) filterPackage = pkg
    }

    fun select(id: String) {
        selectedId = id
        lastReport = null
        refresh()
    }

    fun selectKind(kind: SessionIncidents.Kind?) {
        selectedKind = kind
        val previous = mutable.value?.report ?: lastReport
        if (previous != null && !closed.get()) {
            mutable.value = SessionIncidentsUiState(
                SessionIncidents.render(previous, kind), generation.get(), report = previous
            )
        }
    }

    fun pickSessions() = submit { token ->
        GameSessionRepository(getApplication()).use { repo ->
            val rows = repo.recent(50).filter {
                filterPackage == null || it.packageName == filterPackage
            }
            SessionIncidentsUiState(
                if (rows.isEmpty()) "Для выбранной игры сохранённых сессий нет."
                else "Выберите одну из " + rows.size + " локальных сессий.",
                token, choices = rows
            )
        }
    }

    fun refresh() = submit { token ->
        GameSessionRepository(getApplication()).use { repo ->
            val wanted = selectedId?.let { repo.header(it) }
            val header = if (wanted != null &&
                (filterPackage == null || wanted.packageName == filterPackage)) wanted
                else repo.recent(50).firstOrNull {
                    filterPackage == null || it.packageName == filterPackage
                }
            if (header == null) {
                lastReport = null
                SessionIncidentsUiState(
                    "Нет локальных сессий для анализа. Запустите Game Session вручную.",
                    token
                )
            } else {
                selectedId = header.id
                val points = repo.points(header.id, SessionIncidents.MAX_SAMPLES)
                val events = repo.eventsChronological(header.id, SessionIncidents.MAX_EVENTS)
                val result = SessionIncidents.analyze(
                    header, points, events,
                    points.size == SessionIncidents.MAX_SAMPLES,
                    events.size == SessionIncidents.MAX_EVENTS
                )
                if (generation.get() == token) lastReport = result
                SessionIncidentsUiState(
                    SessionIncidents.render(result, selectedKind), token, report = result
                )
            }
        }
    }

    /** Export exactly the analyzed snapshot + filter, not a silently different DB read. */
    fun exportCsv(uri: Uri) = submit { token ->
        val snapshot = lastReport
        val filter = selectedKind
        if (snapshot == null) SessionIncidentsUiState("Сначала откройте анализ.", token)
        else {
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                ?: error("Системный документ недоступен")
            stream.use { OutputStreamWriter(it, Charsets.UTF_8).use { output ->
                output.write(SessionIncidents.csv(snapshot, filter))
            } }
            SessionIncidentsUiState(
                "CSV сохранён с выбранным фильтром.\n" +
                    SessionIncidents.render(snapshot, filter),
                token, report = snapshot
            )
        }
    }

    fun consumeChoices(token: Int) {
        val old = mutable.value ?: return
        if (old.request == token && old.choices != null)
            mutable.value = old.copy(choices = null)
    }

    private fun submit(job: (Int) -> SessionIncidentsUiState) {
        if (closed.get()) return
        val token = generation.incrementAndGet()
        mutable.value = SessionIncidentsUiState("Чтение локальных измерений…", token)
        worker.execute {
            val result = runCatching { job(token) }.getOrElse {
                SessionIncidentsUiState("Ошибка анализа: " + it.javaClass.simpleName, token)
            }
            main.post {
                if (!closed.get() && generation.get() == token) mutable.value = result
            }
        }
    }

    override fun onCleared() {
        closed.set(true)
        generation.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
