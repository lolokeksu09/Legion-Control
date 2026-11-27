package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

internal data class CheckedArchive(val uri: Uri, val preview: LegionBackupPreview)

internal data class DataArchiveState(
    val pendingRestore: Boolean = false,
    val running: Boolean = false,
    val choosing: Boolean = false,
    val checked: CheckedArchive? = null,
    val message: String = "",
    val error: Boolean = false
)

/** Owns one explicit SAF/archive operation at a time and preserves the checked file across rotation. */
internal class DataArchiveViewModel(app: Application) : AndroidViewModel(app) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableLiveData(DataArchiveState(pendingRestore = LegionDataBackup.hasPendingRestore(app)))
    val state: LiveData<DataArchiveState> = mutable
    private var closed = false

    private fun current() = requireNotNull(mutable.value)

    fun refresh() {
        if (closed || current().running) return
        val pending = LegionDataBackup.hasPendingRestore(getApplication())
        if (pending != current().pendingRestore) mutable.value = current().copy(pendingRestore = pending)
    }

    fun beginPicker(): Boolean {
        val value = current()
        if (value.running || value.choosing || value.pendingRestore) return false
        mutable.value = value.copy(choosing = true)
        return true
    }

    fun endPicker(message: String? = null, error: Boolean = false) {
        mutable.value = current().let { value ->
            if (message == null) value.copy(choosing = false)
            else value.copy(choosing = false, message = message, error = error)
        }
    }

    fun export(uri: Uri) = runArchive("Создаю ZIP…", "Не удалось создать архив") {
        val exported = LegionDataBackup.export(it, uri)
        DataArchiveState(message = "ZIP сохранён. Записей SQLite: ${exported.rows}, параметров: ${exported.settings}.")
    }

    fun inspect(uri: Uri) = runArchive("Проверяю ZIP…", "Архив отклонён") {
        val preview = LegionDataBackup.preview(it, uri)
        DataArchiveState(checked = CheckedArchive(uri, preview))
    }

    fun clearPreview() {
        if (!current().running) mutable.value = current().copy(checked = null)
    }

    fun restore(approved: CheckedArchive) {
        if (current().checked !== approved || current().pendingRestore) return
        val confirmed = approved.preview.identity ?: return
        runArchive("Восстанавливаю локальные данные…", "Восстановление остановлено") { app ->
            val restored = LegionDataBackup.restoreOnEmptyDatabase(app, approved.uri, confirmed)
            (app as? LegionApplication)?.startupRecovery?.markRecovered()
            DataArchiveState(message = "Восстановлено записей SQLite: ${restored.rows}, параметров: ${restored.settings}.")
        }
    }

    fun retryInterruptedRestore() {
        if (!current().pendingRestore) return
        runArchive("Проверяю журнал импорта…", "Восстановление остановлено") { app ->
            val recovered = LegionDataBackup.retryPendingRestore(app)
            if (!LegionDataBackup.hasPendingRestore(app)) (app as? LegionApplication)?.startupRecovery?.markRecovered()
            val message = if (recovered) "Настройки и архивы диагностики восстановлены."
                else "Проверка журнала завершена. Применять настройки из ZIP не потребовалось."
            DataArchiveState(message = message)
        }
    }

    private fun runArchive(progress: String, failure: String, operation: (Application) -> DataArchiveState) {
        if (closed || current().running || current().choosing) return
        mutable.value = current().copy(running = true, checked = null, message = progress, error = false)
        worker.execute {
            val app = getApplication<Application>()
            val result = runCatching { operation(app) }.getOrElse {
                DataArchiveState(message = "$failure. ${it.message ?: it.javaClass.simpleName}", error = true)
            }
            val updated = result.copy(pendingRestore = LegionDataBackup.hasPendingRestore(app))
            main.post { if (!closed) mutable.value = updated }
        }
    }

    override fun onCleared() {
        closed = true
        // Let a transaction already in progress finish even if its screen was closed.
        worker.shutdown()
        super.onCleared()
    }
}
