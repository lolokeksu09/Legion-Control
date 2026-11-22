package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicBoolean

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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val generations = AtomicIntegerArray(5)
    private val cleared = AtomicBoolean(false)
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
        if (cleared.get()) return
        val index = slot(kind.pane)
        val generation = generations.incrementAndGet(index)
        runCatching {
            worker.execute {
                val report = runCatching { repository.read(kind) }.getOrElse {
                    "Ошибка чтения SQLite (${kind.name}): " + it.javaClass.simpleName
                }
                // postValue() coalesces pane events; dispatch each one on main separately.
                mainHandler.post {
                    if (!cleared.get() && generations.get(index) == generation) {
                        mutableReport.value = DiagnosticsReadResult(kind.pane, report)
                    }
                }
            }
        } // An Activity write callback may outlive a cleared ViewModel during recreation.
    }

    fun invalidate(pane: String) {
        generations.incrementAndGet(slot(pane))
    }

    override fun onCleared() {
        cleared.set(true)
        for (index in 0..4) generations.incrementAndGet(index)
        worker.execute { runCatching { repository.close() } }
        worker.shutdown()
        super.onCleared()
    }
}
