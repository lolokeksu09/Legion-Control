package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

data class CompareState(
    val packages:List<String> = emptyList(),
    val packageName:String? = null,
    val sessions:List<GameSessionHeader> = emptyList(),
    val aId:String? = null,
    val bId:String? = null,
    val minutes:Int = 10,
    val pair:ComparePair? = null,
    val busy:Boolean = false,
    val message:String = "Выберите игру и две сохранённые сессии."
)

class SessionCompareViewModel(app:Application):AndroidViewModel(app) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val live=MutableLiveData(CompareState())
    val state:LiveData<CompareState> = live
    private var initialized=false
    private var closed=false
    private fun current()=requireNotNull(live.value)
    private fun update(s:CompareState) { if(!closed) live.value=s }

    private fun submit(work:()->CompareState) {
        if(closed || current().busy) return
        val before=current()
        update(before.copy(busy=true,message="Читаю локальную историю…"))
        worker.execute {
            val result=runCatching(work).getOrElse {
                before.copy(busy=false,message="Не удалось прочитать или сохранить данные: "+it.javaClass.simpleName)
            }
            main.post { if(!closed) update(result.copy(busy=false)) }
        }
    }
    fun initialize(pkg:String?) {
        if(initialized)return
        initialized=true
        reload(pkg)
    }
    fun reload(preferred:String?=current().packageName) {
        val before=current()
        submit {
            GameSessionRepository(getApplication()).use { repo ->
                val packages=repo.knownSessionPackages()
                val selected=preferred?.takeIf { it in packages } ?: packages.firstOrNull()
                val rows=selected?.let { repo.recentForPackage(it,25) }.orEmpty()
                    .filter { it.endedAtMs!=null }
                CompareState(packages,selected,rows,
                    rows.getOrNull(1)?.id,rows.firstOrNull()?.id,before.minutes,
                    message=if(selected==null) "История пока пуста. Запишите игровую сессию."
                    else if(rows.size<2) "Нужны две завершённые записи этой игры."
                    else "Выберите A и B, затем запустите сравнение.")
            }
        }
    }
    fun choosePackage(pkg:String) {
        if(pkg in current().packages) reload(pkg)
    }
    fun chooseSession(sideA:Boolean,id:String) {
        val s=current()
        if(s.busy || s.sessions.none { it.id==id })return
        if((if(sideA)s.bId else s.aId)==id) {
            update(s.copy(message="A и B должны быть разными записями."));return
        }
        update(s.copy(aId=if(sideA)id else s.aId,bId=if(sideA)s.bId else id,
            pair=null,message="Выбор изменён. Выполните сравнение."))
    }
    fun chooseWindow(minutes:Int) {
        val s=current()
        if(!s.busy && minutes in SessionCompareAnalysis.windows)
            update(s.copy(minutes=minutes,pair=null,message="Выбрано окно $minutes мин."))
    }
    fun swap() {
        val s=current()
        if(!s.busy && s.aId!=null && s.bId!=null)
            update(s.copy(aId=s.bId,bId=s.aId,pair=null,message="A и B поменяны местами."))
    }
    fun compare() {
        val before=current()
        val a=before.aId ?: return
        val b=before.bId ?: return
        val pkg=before.packageName ?: return
        if(before.busy || a==b)return
        update(before.copy(pair=null))
        submit {
            GameSessionRepository(getApplication()).use { repo ->
                fun load(id:String):CompareWindow {
                    val h=repo.header(id) ?: error("Session unavailable")
                    require(h.packageName==pkg && h.endedAtMs!=null)
                    val raw=repo.points(id,SessionCompareAnalysis.MAX_POINTS+1)
                    return SessionCompareAnalysis.analyze(h,raw,before.minutes,
                        raw.size>SessionCompareAnalysis.MAX_POINTS)
                }
                val pair=SessionCompareAnalysis.compare(load(a),load(b))
                before.copy(pair=pair,message=if(pair.comparable) "Сравнение выполнено."
                    else "Неполные окна. Дельты скрыты; доступные показания отображены.")
            }
        }
    }
    fun export(uri:Uri,pair:ComparePair) {
        val before=current()
        if(before.busy || before.pair!==pair)return
        submit {
            val stream=getApplication<Application>().contentResolver.openOutputStream(uri,"wt")
                ?: error("Document unavailable")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(SessionCompareAnalysis.csv(pair)) }
            before.copy(message="CSV сравнения сохранён.")
        }
    }
    override fun onCleared() {
        closed=true
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onCleared()
    }
}
