package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Read-only report and optional temperature series; no Activity references. */
data class GameSessionUiState(
    val report: String,
    val timeline: List<GameTelemetryPoint>? = null
)

/**
 * Lifecycle-owned Game Session read/export coordinator.
 * Service startup, notification permission and SAF consent remain explicit Activity actions.
 */
class GameSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val latest = AtomicInteger()
    private val closed = AtomicBoolean()
    private val mutableState = MutableLiveData<GameSessionUiState>()
    val state: LiveData<GameSessionUiState> = mutableState

    fun refresh() = submit {
        GameSessionRepository(getApplication()).use { repo ->
            val rows = repo.recent(8)
            if (rows.isEmpty()) GameSessionUiState("Записанных сессий ещё нет.")
            else {
                val series = repo.points(rows[0].id)
                val text = buildString {
                    append("ПОСЛЕДНИЕ СЕССИИ · ").append(rows.size).append('\n')
                    for (row in rows) {
                        val points = if (row.id == rows[0].id) series else repo.points(row.id)
                        val summary = GameSessionAnalysis.summarize(row, points)
                        append("\n").append(date(row.startedAtMs)).append(" · ")
                            .append(GameSessionAnalysis.render(summary)).append('\n')
                        if (points.size >= 10000)
                            append("Показаны первые 10 000 точек; полный CSV доступен в архиве.\n")
                        for (event in repo.events(row.id, 5))
                            append("  ").append(date(event.timeMs)).append(" · ")
                                .append(event.kind).append(": ").append(event.details).append('\n')
                    }
                }
                GameSessionUiState(text, series)
            }
        }
    }

    /** A Uri is received only from the user's ACTION_CREATE_DOCUMENT result. */
    fun export(uri: Uri, zip: Boolean) = submit {
        GameSessionRepository(getApplication()).use { repo ->
            val output = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                ?: error("Нет потока записи")
            output.use {
                if (zip) repo.exportZip(it)
                else BufferedWriter(OutputStreamWriter(it, Charsets.UTF_8)).use { writer ->
                    repo.writeCsv(writer, "samples")
                }
            }
        }
        GameSessionUiState("Экспорт завершён через выбранный системный документ.")
    }

    private fun submit(job: () -> GameSessionUiState) {
        if (closed.get()) return
        val request = latest.incrementAndGet()
        mutableState.value = GameSessionUiState("Обработка локальных данных…")
        worker.execute {
            val state = runCatching { job() }
                .getOrElse { GameSessionUiState("Ошибка: " + it.javaClass.simpleName) }
            main.post {
                if (!closed.get() && latest.get() == request) mutableState.value = state
            }
        }
    }

    private fun date(ms: Long): String =
        SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date(ms))

    override fun onCleared() {
        closed.set(true)
        latest.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
