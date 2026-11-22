package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ApplicationCenterUiState(
    val message: String,
    val nonce: Int = 0,
    val launchable: List<LaunchableApp>? = null,
    val tracked: List<TrackedApplication>? = null
)

/** Manual-only app inventory, audit, local history and SAF export coordinator. */
class ApplicationCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val closed = AtomicBoolean(false)
    private val visible = AtomicBoolean(false)
    private val mutableState = MutableLiveData<ApplicationCenterUiState>()
    val state: LiveData<ApplicationCenterUiState> = mutableState

    fun setVisible(value: Boolean) { visible.set(value) }

    fun pickApp(filter: String?) = submit { nonce ->
        val found = ApplicationInventory.launchable(getApplication<Application>()).filter { item ->
            filter.isNullOrBlank() ||
                item.label.contains(filter,ignoreCase=true) ||
                item.packageName.contains(filter,ignoreCase=true)
        }
        ApplicationCenterUiState("Из доступных Launcher API найдено: ${found.size}",
            nonce, launchable = found)
    }

    fun chooseTracked(onlyFavorites: Boolean) = submit { nonce ->
        val entries = ApplicationCenterRepository(getApplication<Application>()).use { repository ->
            repository.list().filter { !onlyFavorites || it.favorite }
        }
        ApplicationCenterUiState("Отслеживаемых: ${entries.size}", nonce, tracked = entries)
    }

    fun save(pkg: String, alias: String, category: String, note: String, favorite: Boolean) = submit {
        ApplicationCenterRepository(getApplication<Application>()).use {
            it.save(pkg,alias,category,note,favorite)
        }
        ApplicationCenterUiState("Сохранено: $pkg · избранное: $favorite. " +
            "Запустите отдельную проверку версии, когда она нужна.")
    }

    fun scanOne(pkg: String) = submit {
        val observed = ApplicationInventory.inspect(getApplication<Application>(),pkg)
        ApplicationCenterRepository(getApplication<Application>()).use { repo ->
            val outcome=repo.audit(pkg,observed)
            ApplicationCenterUiState("Проверено: $pkg\n" + outcome.description + "\n" +
                "Событие: " + (outcome.event?.name ?: "нет") + "\n\n" + observed.report())
        }
    }

    fun scanAll() = submit {
        val tracked = ApplicationCenterRepository(getApplication<Application>()).use { it.list() }
        if (tracked.isEmpty()) ApplicationCenterUiState("Библиотека пуста — нечего проверять.")
        else {
            var changed=0
            var errors=0
            var checked=0
            for (item in tracked) {
                // Same foreground boundary as old Activity: stop when user navigates away.
                if (!visible.get() || closed.get() || Thread.currentThread().isInterrupted) break
                checked++
                runCatching {
                    val observation=ApplicationInventory.inspect(
                        getApplication<Application>(),item.packageName)
                    ApplicationCenterRepository(getApplication<Application>()).use { repo ->
                        if (repo.audit(item.packageName,observation).event != null) changed++
                    }
                }.onFailure { errors++ }
            }
            ApplicationCenterUiState("Проверено по запросу: $checked из ${tracked.size} записей. " +
                "Событий: $changed · ошибок Android/SQLite: $errors. " +
                "Недоступность пакета НЕ означает удаление.")
        }
    }

    fun inspect(pkg: String) = submit {
        ApplicationCenterUiState(ApplicationInventory.inspect(
            getApplication<Application>(),pkg).report())
    }

    fun showEvents() = submit {
        val text = ApplicationCenterRepository(getApplication<Application>()).use { repo ->
            val events=repo.events(limit=80)
            if (events.isEmpty()) "История изменений пока пуста."
            else events.joinToString("\n\n") { event ->
                DateFormat.getDateTimeInstance().format(Date(event.recordedAtMs)) +
                    " · " + event.packageName + "\n" + event.kind + " · " + event.details
            }
        }
        ApplicationCenterUiState(text)
    }

    fun remove(pkg: String) = submit {
        val removed=ApplicationCenterRepository(getApplication<Application>()).use {
            it.remove(pkg)
        }
        ApplicationCenterUiState(if (removed) "Удалено из локальной библиотеки: $pkg."
            else "Пакет не найден в библиотеке.")
    }

    fun usage(pkg: String, days: Int) = submit {
        ApplicationCenterUiState(ApplicationUsageInspector.report(
            getApplication<Application>(),pkg,days))
    }

    fun exportCsv(uri: Uri) = submit {
        ApplicationCenterRepository(getApplication<Application>()).use { repo ->
            (getApplication<Application>().contentResolver.openOutputStream(uri,"w")
                ?: error("Не удалось открыть документ для записи")).use { stream ->
                OutputStreamWriter(stream,Charsets.UTF_8).use { writer ->
                    repo.writeCsv(writer)
                }
            }
        }
        ApplicationCenterUiState("Библиотека и события сохранены в CSV по выбранному пути. " +
            "Содержимое заметок стоит проверить перед публикацией.")
    }

    fun consumeDialog(nonce: Int) {
        val current=mutableState.value ?: return
        if (current.nonce == nonce && (current.launchable != null || current.tracked != null))
            mutableState.value = current.copy(launchable=null, tracked=null)
    }

    private fun submit(job: (Int) -> ApplicationCenterUiState) {
        if (closed.get()) return
        val request=generation.incrementAndGet()
        mutableState.value=ApplicationCenterUiState("Обработка локальных данных…",request)
        worker.execute {
            val result=runCatching { job(request) }.getOrElse {
                ApplicationCenterUiState("Ошибка: " + it.javaClass.simpleName +
                    " · " + it.message.orEmpty().take(160),request)
            }
            main.post {
                if (!closed.get() && generation.get() == request) mutableState.value=result
            }
        }
    }

    private fun submit(job: () -> ApplicationCenterUiState) = submit { _ -> job() }

    override fun onCleared() {
        visible.set(false)
        closed.set(true)
        generation.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
