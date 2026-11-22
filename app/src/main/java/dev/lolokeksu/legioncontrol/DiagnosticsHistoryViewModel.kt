package dev.lolokeksu.legioncontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray

/** A single response directed to a specific visible diagnostics pane. */
data class DiagnosticsReadResult(
    val pane: String,
    val content: String
)

/**
 * Lifecycle-aware read coordinator. One independent SQLite helper lives and dies with
 * ViewModel, never holding Activity or TextView. Existing write queue remains serialized
 * in MainActivity and schedules reads only after its previously submitted writes.
 */
class DiagnosticsHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiagnosticsHistoryRepository(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val generations = AtomicIntegerArray(5)
    private val mutableReport = MutableLiveData<DiagnosticsReadResult>()
    val report: LiveData<DiagnosticsReadResult> = mutableReport

    private fun slot(pane: String) = when (pane) {
        "history" -> 0
        "analytics" -> 1
        "thermal" -> 2
        "timed" -> 3
        "sensors" -> 4
        else -> error("Unknown diagnostics pane")
    }

    fun request(kind: DiagnosticsReadKind) {
        val index = slot(kind.pane)
        val generation = generations.incrementAndGet(index)
        worker.execute {
            val report = runCatching { repository.read(kind) }.getOrElse {
                "Ошибка чтения SQLite (${kind.name}): " + it.javaClass.simpleName
            }
            if (generations.get(index) == generation) {
                mutableReport.postValue(DiagnosticsReadResult(kind.pane, report))
            }
        }
    }

    fun invalidate(pane: String) {
        generations.incrementAndGet(slot(pane))
    }

    override fun onCleared() {
        for (index in 0..4) generations.incrementAndGet(index)
        worker.execute { runCatching { repository.close() } }
        worker.shutdown()
        super.onCleared()
    }
}
