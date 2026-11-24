package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

data class PreflightState(
    val apps: List<LaunchableApp> = emptyList(),
    val selected: LaunchableApp? = null,
    val preset: PreflightPreset = PreflightPreset(),
    val report: PreflightReport? = null,
    val busy: Boolean = false,
    val message: String = "Выберите игру из списка приложений Android."
)

class GamePreflightViewModel(app: Application): AndroidViewModel(app) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableLiveData(PreflightState())
    val state: LiveData<PreflightState> = mutable
    private var initialized = false
    private var closed = false
    private fun current() = requireNotNull(mutable.value)
    private fun update(s: PreflightState) { if (!closed) mutable.value = s }

    fun initialize(requestedPackage: String?) {
        if (initialized) return
        initialized = true
        loadApps(requestedPackage)
    }

    fun loadApps(preferred: String? = current().selected?.packageName) {
        if (current().busy) return
        update(current().copy(busy=true,report=null,message="Читаю видимые приложения Android…"))
        worker.execute {
            val app = getApplication<Application>()
            val result = runCatching {
                val apps = ApplicationInventory.launchable(app)
                val selected = apps.firstOrNull { it.packageName == preferred }
                val preset = selected?.let { PreflightPresets.read(app,it.packageName) } ?: PreflightPreset()
                Triple(apps,selected,preset)
            }
            main.post {
                if (closed) return@post
                result.fold({ (apps, selected, preset) ->
                    update(PreflightState(apps,selected,preset,message=when {
                        selected != null -> "Выбрано: ${selected.label}. Запустите проверку."
                        preferred != null -> "Выбранный ранее пакет не виден через Launcher. Выберите приложение заново."
                        apps.isEmpty() -> "Android не предоставил список приложений."
                        else -> "Выберите игру из списка приложений Android."
                    }))
                }, { update(current().copy(busy=false,selected=null,report=null,
                    message="Не удалось прочитать список приложений.")) })
            }
        }
    }

    fun select(pkg: String) {
        val old = current()
        if (old.busy) return
        val chosen = old.apps.firstOrNull { it.packageName == pkg } ?: return
        update(old.copy(busy=true,selected=null,report=null,message="Загружаю требования игры…"))
        worker.execute {
            val result = runCatching { PreflightPresets.read(getApplication(),pkg) }
            main.post {
                if (closed) return@post
                update(current().copy(selected=chosen,busy=false,
                    preset=result.getOrDefault(PreflightPreset()),report=null,
                    message=if(result.isSuccess) "Выбрано: ${chosen.label}. Запустите проверку."
                        else "Не удалось прочитать сохранённые требования. Использованы значения по умолчанию."))
            }
        }
    }

    fun savePreset(preset: PreflightPreset) {
        val old=current()
        val app=old.selected ?: return
        if(old.busy) return
        update(old.copy(busy=true,report=null,message="Сохраняю требования…"))
        worker.execute {
            val saved=runCatching { PreflightPresets.save(getApplication(),app.packageName,preset) }
                .getOrDefault(false)
            main.post {
                if(closed) return@post
                update(current().copy(busy=false,preset=if(saved) preset else old.preset,
                    message=if(saved) "Требования сохранены для ${app.label}. Обновите проверку."
                        else "Не удалось сохранить требования. Прежний профиль не изменён."))
            }
        }
    }

    fun check() {
        val old=current()
        val app=old.selected ?: return
        if(old.busy) return
        update(old.copy(busy=true,report=null,message="Проверяю состояние устройства…"))
        worker.execute {
            val result=runCatching { GamePreflightBackend.check(getApplication(),app,old.preset) }
            main.post {
                if(closed) return@post
                update(current().copy(busy=false,report=result.getOrNull(),
                    message=if(result.isSuccess) "Проверка завершена. Результат действителен для запуска 60 секунд."
                        else "Не удалось завершить проверку. Повторите попытку."))
            }
        }
    }

    override fun onCleared() {
        closed=true
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onCleared()
    }
}
