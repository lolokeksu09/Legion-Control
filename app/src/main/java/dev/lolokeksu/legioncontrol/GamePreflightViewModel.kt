package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

data class PreflightState(
    val apps: List<LaunchableApp> = emptyList(),
    val selected: LaunchableApp? = null,
    val preset: PreflightPreset = PreflightPreset(),
    val report: PreflightReport? = null,
    val launchFresh: Boolean = false,
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
    private var visible = false
    private var visibilityGeneration = 0L

    fun setVisible(value:Boolean) {
        if (visible == value) return
        visible=value
        visibilityGeneration++
        if(!value) update(current().copy(launchFresh=false))
    }

    private val expiry=Runnable {
        val s=current()
        if(visible && s.launchFresh && s.report!=null) {
            if(PreflightRules.fresh(s.report.checkedAtElapsed,SystemClock.elapsedRealtime())) {
                scheduleExpiry()
            } else update(s.copy(launchFresh=false,
                message="Срок проверки истёк. Выполните проверку снова."))
        }
    }

    private fun scheduleExpiry() {
        main.removeCallbacks(expiry)
        val s=current()
        val report=s.report ?: return
        if(!visible || !s.launchFresh) return
        val age=(SystemClock.elapsedRealtime()-report.checkedAtElapsed).coerceAtLeast(0L)
        main.postDelayed(expiry,(60_001L-age).coerceAtLeast(1L))
    }

    private fun current() = requireNotNull(mutable.value)
    private fun update(s: PreflightState) {
        if(closed) return
        main.removeCallbacks(expiry)
        mutable.value=s
        if(s.launchFresh) scheduleExpiry()
    }

    fun initialize(requestedPackage: String?) {
        if (initialized) return
        initialized = true
        loadApps(requestedPackage)
    }

    fun loadApps(preferred: String? = current().selected?.packageName) {
        if (current().busy) return
        update(current().copy(busy=true,report=null,launchFresh=false,message="Читаю видимые приложения Android…"))
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
        update(old.copy(busy=true,selected=null,report=null,launchFresh=false,message="Загружаю требования игры…"))
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
        update(old.copy(busy=true,report=null,launchFresh=false,message="Сохраняю требования…"))
        worker.execute {
            val saved=runCatching { PreflightPresets.save(getApplication(),app.packageName,preset) }
                .getOrDefault(false)
            main.post {
                if(closed) return@post
                update(current().copy(busy=false,preset=if(saved) preset else old.preset,
                    message=if(saved) "Требования сохранены для ${app.label}. Обновите проверку."
                        else "Не удалось подтвердить сохранение. Обновите список и проверьте сохранённые требования."))
            }
        }
    }

    fun check() {
        val old=current()
        val app=old.selected ?: return
        if(old.busy || !visible) return
        val token=visibilityGeneration
        update(old.copy(busy=true,report=null,launchFresh=false,message="Проверяю состояние устройства…"))
        worker.execute {
            val result=runCatching { GamePreflightBackend.check(getApplication(),app,old.preset) }
            main.post {
                if(closed) return@post
                val fresh=result.isSuccess && visible && token==visibilityGeneration
                update(current().copy(busy=false,report=result.getOrNull(),launchFresh=fresh,
                    message=when {
                        fresh -> "Проверка завершена. Результат действителен для запуска 60 секунд."
                        result.isSuccess -> "Снимок сохранён для отчёта. Для запуска выполните проверку снова."
                        else -> "Не удалось завершить проверку. Повторите попытку."
                    }))
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
