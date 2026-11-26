package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

data class LibraryState(
    val filter:LibraryFilter=LibraryFilter(),
    val packages:List<String> = emptyList(),
    val rows:List<GameSessionHeader> = emptyList(),
    val next:LibraryCursor?=null,
    val previous:List<LibraryCursor?> = emptyList(),
    val cursor:LibraryCursor?=null,
    val now:Long=0,
    val detail:LibraryDetail?=null,
    val analytics:UnifiedGameSessionSnapshot?=null,
    val comparisonRows:List<GameSessionHeader> = emptyList(),
    val pair:ComparePair?=null,
    val baseline:SessionBaselinesResult?=null,
    val selecting:Boolean=false,
    val busy:Boolean=false,
    val message:String="Загрузка истории…"
)

class SessionLibraryViewModel(app:Application):AndroidViewModel(app) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val live=MutableLiveData(LibraryState())
    val state:LiveData<LibraryState> = live
    internal val pendingDocument = PendingDocument<LibraryDetail>()
    internal val pendingCsv = PendingDocument<AnalyticsCsv>()
    private val selectionGeneration = AtomicLong(0)
    private var initialized=false
    private var closed=false
    private fun current()=requireNotNull(live.value)
    private fun update(s:LibraryState) { if(!closed) live.value=s }
    private fun submit(work:()->LibraryState) {
        if(closed || current().busy || pendingDocument.hasPending || pendingCsv.hasPending)return
        val before=current()
        update(before.copy(busy=true,message="Читаю локальные записи…"))
        worker.execute {
            val result=runCatching(work).getOrElse {
                before.copy(message="Операция не завершена: "+it.javaClass.simpleName)
            }
            main.post { if(!closed)update(result.copy(busy=false)) }
        }
    }
    fun initialize(pkg:String?, sessionId:String?=null) {
        if(initialized)return
        initialized=true
        val filter=LibraryFilter(pkg?.takeIf(GameLaunchProfilePolicy::validPackage))
        if(sessionId.isNullOrBlank()) {
            refresh(filter)
            return
        }
        submit {
            val now=System.currentTimeMillis()
            GameSessionRepository(getApplication()).use { repo ->
                val page=repo.libraryPage(filter,now)
                val s=LibraryState(filter=filter,packages=repo.knownSessionPackages(),rows=page.rows,
                    next=page.next,now=now)
                val row=repo.header(sessionId)
                if(row==null || (filter.packageName!=null && row.packageName!=filter.packageName))
                    s.copy(message="Запись недоступна для выбранной игры.")
                else loaded(s,row,repo)
            }
        }
    }

    private fun loaded(s:LibraryState,row:GameSessionHeader,repo:GameSessionRepository):LibraryState {
        val detail=repo.analyticsDetail(row.id)
        require(detail.header.id==row.id && detail.header.packageName==row.packageName)
        val snapshot=UnifiedGameSessionSnapshot.from(detail)
        val candidates = if (detail.header.endedAtMs == null) emptyList()
            else repo.recentForPackage(row.packageName, 25)
                .filter { it.endedAtMs != null && it.id != row.id }
        return s.copy(detail=detail,analytics=snapshot,comparisonRows=candidates,pair=null,
            baseline=null,selecting=false,message=if (detail.header.endedAtMs == null)
                "Запись без отметки завершения. Показаны данные на момент чтения."
            else "Выбрана запись ${detail.header.id.take(8)}.")
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
                    analytics=null,comparisonRows=emptyList(),pair=null,baseline=null,selecting=false,
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
        if(closed || (s.busy && !s.selecting) || pendingDocument.hasPending || pendingCsv.hasPending)return
        val token=selectionGeneration.incrementAndGet()
        val clean=s.copy(detail=null,analytics=null,comparisonRows=emptyList(),pair=null,baseline=null,
            selecting=false,busy=false)
        update(clean.copy(selecting=true,busy=true,message="Читаю выбранную сессию…"))
        worker.execute {
            if(selectionGeneration.get()!=token)return@execute
            val result=runCatching {
                GameSessionRepository(getApplication()).use { repo -> loaded(clean,row,repo) }
            }.getOrElse { clean.copy(message="Не удалось прочитать сессию: "+it.javaClass.simpleName) }
            main.post {
                if(!closed && selectionGeneration.get()==token)update(result.copy(busy=false,selecting=false))
            }
        }
    }

    fun refreshSelected(id:String) {
        val s=current()
        val row=s.detail?.header?.takeIf { it.id==id } ?: return
        submit {
            GameSessionRepository(getApplication()).use { repo -> loaded(s,row,repo) }
        }
    }

    fun clearComparison() {
        val s=current()
        if(!s.busy && !pendingCsv.hasPending && !pendingDocument.hasPending)update(s.copy(pair=null))
    }

    fun compareWith(id:String,minutes:Int) {
        val s=current()
        val snapshot=s.analytics ?: return
        if(s.busy || pendingCsv.hasPending || pendingDocument.hasPending || minutes !in SessionCompareAnalysis.windows)return
        val row=s.comparisonRows.firstOrNull { it.id==id } ?: return
        submit {
            GameSessionRepository(getApplication()).use { repo ->
                val header=repo.header(row.id) ?: error("Session unavailable")
                require(header.id!=snapshot.detail.header.id &&
                    header.packageName==snapshot.detail.header.packageName && header.endedAtMs!=null)
                val raw=repo.points(header.id,SessionCompareAnalysis.MAX_POINTS+1)
                val pair=SessionCompareAnalysis.compare(snapshot.compareWindow(minutes),
                    SessionCompareAnalysis.analyze(header,raw,minutes,raw.size>SessionCompareAnalysis.MAX_POINTS))
                s.copy(pair=pair,message="Сравнение готово.")
            }
        }
    }

    fun baseline(days:Int) {
        val s=current()
        val snapshot=s.analytics ?: return
        if(s.busy || pendingCsv.hasPending || pendingDocument.hasPending || days !in listOf(7,30))return
        submit {
            GameSessionRepository(getApplication()).use { repo ->
                val pkg=snapshot.detail.header.packageName
                val captures=repo.recentForPackage(pkg,SessionBaselines.MAX_SESSIONS+1).map { header ->
                    if(header==snapshot.detail.header) snapshot.baselineCapture()
                    else {
                        val points=repo.points(header.id,SessionBaselines.MAX_POINTS_PER_SESSION)
                        BaselineCapture(header,points,points.size==SessionBaselines.MAX_POINTS_PER_SESSION)
                    }
                }
                s.copy(baseline=SessionBaselines.compare(pkg,days,System.currentTimeMillis(),captures),
                    message="Базовые показатели готовы.")
            }
        }
    }

    internal fun exportCsv(uri:Uri,snapshot:AnalyticsCsv) {
        val s=current()
        if(s.busy)return
        submit {
            val stream=getApplication<Application>().contentResolver.openOutputStream(uri,"wt")
                ?: error("Document unavailable")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot.content) }
            s.copy(message="CSV сохранён.")
        }
    }

    fun export(uri:Uri,detail:LibraryDetail) {
        val s=current()
        if(s.busy || s.detail!==detail || detail.header.endedAtMs==null)return
        submit {
            val stream=getApplication<Application>().contentResolver.openOutputStream(uri,"wt")
                ?: error("Document unavailable")
            SessionLibraryExport.write(detail,stream)
            s.copy(message="Архив выбранной записи сохранён.")
        }
    }
    override fun onCleared() {
        pendingDocument.take()
        pendingCsv.take()
        selectionGeneration.incrementAndGet()
        closed=true
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onCleared()
    }
}
