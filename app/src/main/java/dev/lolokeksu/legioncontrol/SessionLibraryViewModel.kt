package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

data class LibraryState(
    val filter:LibraryFilter=LibraryFilter(),
    val packages:List<String> = emptyList(),
    val rows:List<GameSessionHeader> = emptyList(),
    val next:LibraryCursor?=null,
    val previous:List<LibraryCursor?> = emptyList(),
    val cursor:LibraryCursor?=null,
    val now:Long=0,
    val detail:LibraryDetail?=null,
    val busy:Boolean=false,
    val message:String="Загрузка истории…"
)

class SessionLibraryViewModel(app:Application):AndroidViewModel(app) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val live=MutableLiveData(LibraryState())
    val state:LiveData<LibraryState> = live
    private var initialized=false
    private var closed=false
    private fun current()=requireNotNull(live.value)
    private fun update(s:LibraryState) { if(!closed) live.value=s }
    private fun submit(work:()->LibraryState) {
        if(closed || current().busy)return
        val before=current()
        update(before.copy(busy=true,message="Читаю локальные записи…"))
        worker.execute {
            val result=runCatching(work).getOrElse {
                before.copy(message="Операция не завершена: "+it.javaClass.simpleName)
            }
            main.post { if(!closed)update(result.copy(busy=false)) }
        }
    }
    fun initialize(pkg:String?) {
        if(initialized)return
        initialized=true
        refresh(LibraryFilter(pkg?.takeIf(GameLaunchProfilePolicy::validPackage)))
    }
    fun refresh(filter:LibraryFilter=current().filter) {
        submit {
            val now=System.currentTimeMillis()
            GameSessionRepository(getApplication()).use { repo ->
                val page=repo.libraryPage(filter,now)
                LibraryState(filter=filter,packages=repo.knownSessionPackages(),rows=page.rows,
                    next=page.next,now=now,message=if(page.rows.isEmpty())"Нет записей по выбранным фильтрам."
                    else "Страница 1 · записей: ${page.rows.size}")
            }
        }
    }
    private fun navigate(cursor:LibraryCursor?,previous:List<LibraryCursor?>) {
        val s=current()
        submit {
            GameSessionRepository(getApplication()).use { repo ->
                val page=repo.libraryPage(s.filter,s.now,cursor)
                s.copy(rows=page.rows,next=page.next,cursor=cursor,previous=previous,detail=null,
                    message="Страница ${previous.size+1} · записей: ${page.rows.size}")
            }
        }
    }
    fun next() {
        val s=current()
        val cursor=s.next ?: return
        if(s.previous.size>=499)return
        navigate(cursor,s.previous+s.cursor)
    }
    fun previous() {
        val s=current()
        if(s.previous.isNotEmpty())navigate(s.previous.last(),s.previous.dropLast(1))
    }
    fun select(id:String) {
        val s=current()
        val row=s.rows.firstOrNull { it.id==id } ?: return
        if(s.busy)return
        if(row.endedAtMs==null) {
            update(s.copy(detail=null,message="Нет записи завершения. Обновите историю после остановки записи."))
            return
        }
        update(s.copy(detail=null))
        submit {
            GameSessionRepository(getApplication()).use { repo ->
                val detail=repo.libraryDetail(id)
                require(detail.header.id==row.id && detail.header.packageName==row.packageName)
                s.copy(detail=detail,message="Выбрана запись ${detail.header.id.take(8)}.")
            }
        }
    }
    fun export(uri:Uri,detail:LibraryDetail) {
        val s=current()
        if(s.busy || s.detail!==detail)return
        submit {
            val stream=getApplication<Application>().contentResolver.openOutputStream(uri,"wt")
                ?: error("Document unavailable")
            SessionLibraryExport.write(detail,stream)
            s.copy(message="Архив выбранной записи сохранён.")
        }
    }
    override fun onCleared() {
        closed=true
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onCleared()
    }
}
