package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class SmartProfilesUiState(
    val message: String,
    val nonce: Int = 0,
    val profiles: List<SmartProfile>? = null,
    val rulesPackage: String? = null,
    val rules: List<SmartRule>? = null
)

/** The ViewModel owns repository operations, never Android system controls or dialog objects. */
class SmartProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val closed = AtomicBoolean()
    private val mutableState = MutableLiveData<SmartProfilesUiState>()
    val state: LiveData<SmartProfilesUiState> = mutableState

    fun save(pkg: String, label: String, hot: Int, low: Int, tcp: Boolean) = submit {
        SmartProfilesRepository(getApplication<Application>()).use { it.save(pkg,label,hot,low,tcp) }
        SmartProfilesUiState("Сохранено: $label ($pkg). Все 6 правил доступны в настройке.")
    }

    fun delete(pkg: String) = submit {
        val deleted = SmartProfilesRepository(getApplication<Application>()).use { it.delete(pkg) }
        SmartProfilesUiState(if (deleted) "Профиль удалён. История сессий сохранена."
            else "Такого профиля нет.")
    }

    fun loadPicker() = submit { nonce ->
        val profiles = SmartProfilesRepository(getApplication<Application>()).use { it.list() }
        SmartProfilesUiState("Найдено профилей: ${profiles.size}", nonce, profiles = profiles)
    }

    fun loadRules(pkg: String) = submit { nonce ->
        val rules = SmartProfilesRepository(getApplication<Application>()).use { it.rules(pkg) }
        SmartProfilesUiState("Настройка правил $pkg", nonce, rulesPackage = pkg, rules = rules)
    }

    fun updateRules(pkg: String, rules: List<SmartRule>, flags: BooleanArray) = submit {
        require(rules.size == flags.size)
        SmartProfilesRepository(getApplication<Application>()).use { repository ->
            rules.forEachIndexed { index, rule ->
                repository.setRule(pkg, rule.kind, flags[index], rule.cooldownMs)
            }
        }
        SmartProfilesUiState("Правила сохранены. В текущей записи изменения не применяются до нового запуска.")
    }

    fun insights(pkg: String, days: Int) = submit {
        SmartProfilesUiState(SmartSessionInsights.summarize(
            getApplication<Application>(), pkg, days
        ).textReport())
    }

    fun events() = submit {
        val text = SmartProfilesRepository(getApplication<Application>()).use { repo ->
            repo.events().joinToString("\n") {
                "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.timeMs))} · " +
                    it.packageName + " · " + it.kind + " · " + it.details
            }.ifBlank { "Предупреждений пока нет." }
        }
        SmartProfilesUiState(text)
    }

    /** User-triggered only; no periodic probe or telemetry. */
    fun networkProbe() = submit {
        SmartProfilesUiState(SmartNetworkLab.inspect(getApplication<Application>()))
    }

    fun export(uri: Uri) = submit {
        SmartProfilesRepository(getApplication<Application>()).use { repo ->
            (getApplication<Application>().contentResolver.openOutputStream(uri,"w")
                ?: error("No output stream")).use { repo.writeBackup(it) }
        }
        SmartProfilesUiState("JSON профилей экспортирован через системный выбор файла.")
    }

    /** Called only AFTER the user confirms overwriting matching profiles. */
    fun importBackup(uri: Uri) = submit {
        val count = SmartProfilesRepository(getApplication<Application>()).use { repo ->
            (getApplication<Application>().contentResolver.openInputStream(uri)
                ?: error("No input stream")).use { repo.importBackup(it) }
        }
        SmartProfilesUiState("Импортировано профилей: $count. История не изменена.")
    }

    /** Dialog payload is consumed once, so rotation cannot silently replay it. */
    fun consumeDialog(nonce: Int) {
        val state = mutableState.value ?: return
        if (nonce == state.nonce && (state.profiles != null || state.rules != null)) {
            mutableState.value = state.copy(profiles = null, rules = null, rulesPackage = null)
        }
    }

    private fun submit(job: (Int) -> SmartProfilesUiState) {
        if (closed.get()) return
        val request = generation.incrementAndGet()
        mutableState.value = SmartProfilesUiState("Операция с локальными данными…", request)
        worker.execute {
            val state = runCatching { job(request) }.getOrElse {
                SmartProfilesUiState(
                    "Ошибка: ${it.javaClass.simpleName}: ${it.message.orEmpty().take(160)}",
                    request
                )
            }
            main.post {
                if (!closed.get() && generation.get() == request) mutableState.value = state
            }
        }
    }

    private fun submit(job: () -> SmartProfilesUiState) = submit { _ -> job() }

    override fun onCleared() {
        closed.set(true)
        generation.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
