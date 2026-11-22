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

data class SessionSignalsUiState(
    val message:String,
    val request:Int=0,
    val choices:List<GameSessionHeader>?=null,
    val report:SessionSignalsReport?=null
)

/**
 * Read-only analysis except explicit SAF export. All database reads and CSV writes
 * are serialized off main thread; no sensor service, root, polling or network I/O.
 */
class SessionSignalsViewModel(app:Application):AndroidViewModel(app) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val generation=AtomicInteger()
    private val closed=AtomicBoolean()
    private val mutable=MutableLiveData(SessionSignalsUiState("Выберите записанную сессию."))
    val state:LiveData<SessionSignalsUiState> = mutable

    @Volatile var selectedId:String?=null
        private set
    @Volatile private var filterPackage:String?=null

    fun setPackageFilter(pkg:String?) {
        if(pkg!=null && SmartProfilesRepository.validatePackage(pkg))filterPackage=pkg
    }

    fun pickSessions()=submit { n ->
        GameSessionRepository(getApplication()).use { repo ->
            val rows=repo.recent(50).let { list ->
                filterPackage?.let { pkg -> list.filter { it.packageName==pkg } } ?: list
            }
            SessionSignalsUiState(
                if(rows.isEmpty()) "Для этого приложения нет записанных сессий."
                else "Выберите одну из последних "+rows.size+" сессий.",
                n,choices=rows)
        }
    }

    fun select(id:String) {
        selectedId=id
        refresh()
    }

    fun refresh()=submit { n ->
        GameSessionRepository(getApplication()).use { repo ->
            val header=selectedId?.let { repo.header(it) } ?: repo.recent(50)
                .firstOrNull { filterPackage==null || it.packageName==filterPackage }
            if(header==null) SessionSignalsUiState("Пока нет игровых сессий. Запустите Game Session вручную.",n)
            else {
                selectedId=header.id
                val points=repo.points(header.id,SessionSignals.MAX_SAMPLES)
                val events=repo.eventsChronological(header.id,SessionSignals.MAX_EVENTS)
                val report=SessionSignals.analyze(header,points,events,
                    points.size==SessionSignals.MAX_SAMPLES,
                    events.size==SessionSignals.MAX_EVENTS)
                SessionSignalsUiState(SessionSignals.render(report),n,report=report)
            }
        }
    }

    fun export(uri:Uri)=submit { n ->
        val selected=selectedId
        if(selected==null) SessionSignalsUiState("Сначала выберите сессию.",n)
        else GameSessionRepository(getApplication()).use { repo ->
            val header=repo.header(selected) ?: error("Сессия не найдена")
            val samples=repo.points(selected,SessionSignals.MAX_SAMPLES)
            val events=repo.eventsChronological(selected,SessionSignals.MAX_EVENTS)
            val report=SessionSignals.analyze(header,samples,events,
                samples.size==SessionSignals.MAX_SAMPLES,events.size==SessionSignals.MAX_EVENTS)
            val app=getApplication<Application>()
            val stream=app.contentResolver.openOutputStream(uri,"w")
                ?: error("Системный документ недоступен")
            stream.use { OutputStreamWriter(it,Charsets.UTF_8).use { out ->
                out.write(SessionSignals.csv(report))
            }}
            SessionSignalsUiState("CSV сохранён в выбранный пользователем документ.\n"+
                SessionSignals.render(report),n,report=report)
        }
    }

    fun consumeChoices(request:Int) {
        val previous=mutable.value ?: return
        if(previous.request==request && previous.choices!=null)
            mutable.value=previous.copy(choices=null)
    }

    private fun submit(job:(Int)->SessionSignalsUiState) {
        if(closed.get())return
        val n=generation.incrementAndGet()
        mutable.value=SessionSignalsUiState("Чтение локальной истории…",n)
        worker.execute {
            val result=runCatching{job(n)}.getOrElse {
                SessionSignalsUiState("Ошибка анализа: "+it.javaClass.simpleName,n)
            }
            main.post {
                if(!closed.get() && generation.get()==n)mutable.value=result
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
