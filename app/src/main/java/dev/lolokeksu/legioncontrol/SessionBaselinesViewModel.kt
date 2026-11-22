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

data class SessionBaselinesUiState(
    val text:String,
    val result:SessionBaselinesResult?=null,
    val packages:List<String>?=null,
    val request:Int=0
)

/**
 * Read-only historical comparison on a serial worker.
 * No live sensor polling, auto profiling, network activity or database migration.
 */
class SessionBaselinesViewModel(app:Application):AndroidViewModel(app) {
    private val queue=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val generation=AtomicInteger()
    private val closed=AtomicBoolean()
    private val live=MutableLiveData(SessionBaselinesUiState("Выберите игру и период."))
    val state:LiveData<SessionBaselinesUiState> = live

    @Volatile var selectedPackage:String?=null
        private set
    @Volatile var days:Int=7
        private set
    @Volatile private var lastReport:SessionBaselinesResult?=null

    fun selectPackage(pkg:String) {
        val valid=Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(pkg)
        if(!valid) {
            live.value=SessionBaselinesUiState("Недопустимое имя пакета.")
            return
        }
        selectedPackage=pkg
        lastReport=null
        refresh()
    }

    fun useInitialPackage(pkg:String?) {
        if(selectedPackage==null && !pkg.isNullOrBlank())selectPackage(pkg)
        else if(live.value?.result==null)refresh()
    }

    fun setDays(value:Int) {
        require(value==7 || value==30)
        days=value
        lastReport=null
        refresh()
    }

    fun pickPackages()=submit { token ->
        GameSessionRepository(getApplication()).use { repo ->
            val rows=repo.knownSessionPackages()
            SessionBaselinesUiState(
                if(rows.isEmpty())"Нет сохранённых игровых сессий."
                else "Выберите приложение из локальной истории.",
                packages=rows,request=token)
        }
    }

    fun refresh()=submit { token ->
        GameSessionRepository(getApplication()).use { repo ->
            val pkg=selectedPackage ?: repo.knownSessionPackages().firstOrNull()
            if(pkg==null) {
                lastReport=null
                SessionBaselinesUiState("История пока пуста. Начните запись через Game Session.",request=token)
            } else {
                selectedPackage=pkg
                val captures=repo.recentForPackage(pkg,SessionBaselines.MAX_SESSIONS+1)
                    .map { header ->
                        val points=repo.points(header.id,SessionBaselines.MAX_POINTS_PER_SESSION)
                        BaselineCapture(header,points,
                            truncated=points.size==SessionBaselines.MAX_POINTS_PER_SESSION)
                    }
                val summary=SessionBaselines.compare(pkg,days,System.currentTimeMillis(),captures)
                lastReport=summary
                SessionBaselinesUiState(SessionBaselines.render(summary),summary,request=token)
            }
        }
    }

    fun exportCsv(uri:Uri)=submit { token ->
        // Export the same bounded report the user saw; never silently switch packages.
        val previous=lastReport
        if(previous==null)SessionBaselinesUiState("Сначала откройте анализ.",request=token)
        else {
            val stream=getApplication<Application>().contentResolver.openOutputStream(uri,"w")
                ?: error("Системный документ недоступен")
            stream.use { OutputStreamWriter(it,Charsets.UTF_8).use { writer ->
                writer.write(SessionBaselines.csv(previous))
            }}
            SessionBaselinesUiState("CSV сохранён в выбранный документ.\n"+
                SessionBaselines.render(previous),previous,request=token)
        }
    }

    fun consumePackages(request:Int) {
        val old=live.value ?: return
        if(old.request==request && old.packages!=null)
            live.value=old.copy(packages=null)
    }

    private fun submit(work:(Int)->SessionBaselinesUiState) {
        if(closed.get())return
        val token=generation.incrementAndGet()
        live.value=SessionBaselinesUiState("Читаю локальные игровые сессии…",request=token)
        queue.execute {
            val result=runCatching { work(token) }.getOrElse {
                SessionBaselinesUiState("Ошибка чтения: "+it.javaClass.simpleName,request=token)
            }
            main.post { if(!closed.get() && generation.get()==token)live.value=result }
        }
    }

    override fun onCleared() {
        closed.set(true)
        generation.incrementAndGet()
        queue.shutdown()
        super.onCleared()
    }
}
