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

data class ExperimentLabUiState(
    val message:String,
    val nonce:Int=0,
    val packages:List<Pair<String,String>>?=null,
    val sessions:List<GameSessionHeader>?=null,
    val experiments:List<SessionExperiment>?=null,
    val share:String?=null
)

data class ExperimentSessionSelection(
    val message: String,
    val header: GameSessionHeader? = null,
    val annotation: SessionAnnotation? = null,
    val fillDraft: Boolean = false
)

/** Local-only annotations, A/B reports, pickers and SAF export. Never replaces game telemetry. */
class ExperimentLabViewModel(app:Application):AndroidViewModel(app) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val generation=AtomicInteger()
    private val closed=AtomicBoolean()
    private val live=MutableLiveData<ExperimentLabUiState>()
    val state:LiveData<ExperimentLabUiState> = live
    var selectedExperiment:String?=null
        private set
    private val sessionGeneration = AtomicInteger()
    private val sessionSelection = MutableLiveData<ExperimentSessionSelection?>()
    val selection: LiveData<ExperimentSessionSelection?> = sessionSelection
    private var initialSessionRead = false
    private var requestedSessionPackage: String? = null
    var requestedSessionId: String? = null
        private set
    val selectedSession: String? get() = sessionSelection.value?.header?.id
    fun selectExperiment(id:String){selectedExperiment=id}

    fun pickPackages()=submit { n ->
        val items=repo { it.recentSessions(limit=100) }
            .map { it.packageName to it.appLabel }.distinctBy { it.first }
        ExperimentLabUiState("Приложений в последних 100 сессиях: ${items.size}",
            n,packages=items)
    }
    fun pickSessions(pkg:String?)=submit { n ->
        val items=repo { it.recentSessions(pkg,60) }
        ExperimentLabUiState("Найдено сессий: ${items.size}",n,sessions=items)
    }
    fun initializeSession(packageName: String?, id: String?, restoreDraft: Boolean) {
        if (initialSessionRead) return
        initialSessionRead = true
        if (packageName != null && !id.isNullOrBlank()) selectSession(packageName, id, !restoreDraft)
    }

    fun selectSession(packageName: String, id: String, fillDraft: Boolean = true) {
        if (closed.get()) return
        val request = sessionGeneration.incrementAndGet()
        requestedSessionPackage = packageName
        requestedSessionId = id
        sessionSelection.value = ExperimentSessionSelection("Читаю выбранную запись…")
        worker.execute {
            val result = runCatching {
                repo { repository ->
                    val header = repository.sessionForPackage(packageName, id)
                    if (header == null) {
                        ExperimentSessionSelection("Запись недоступна для выбранной игры.")
                    } else {
                        ExperimentSessionSelection(
                            message = if (header.endedAtMs == null) "Без отметки завершения" else "Завершена",
                            header = header,
                            annotation = if (fillDraft) repository.getAnnotation(id) else null,
                            fillDraft = fillDraft
                        )
                    }
                }
            }.getOrElse {
                ExperimentSessionSelection("Не удалось прочитать запись: " + it.javaClass.simpleName)
            }
            main.post {
                if (!closed.get() && sessionGeneration.get() == request) {
                    sessionSelection.value = result
                }
            }
        }
    }

    fun clearSessionForPackage(packageName: String?) {
        if (requestedSessionId == null || requestedSessionPackage == packageName) return
        sessionGeneration.incrementAndGet()
        requestedSessionPackage = null
        requestedSessionId = null
        sessionSelection.value = null
    }

    fun finishFillingDraft() {
        val session = sessionSelection.value ?: return
        sessionSelection.value = session.copy(annotation = null, fillDraft = false)
    }

    fun saveAnnotation(id:String,tag:String,note:String)=simple {
        repo { it.annotate(id,tag,note) }
        "Метка и заметка сохранены для $id. Исходные измерения не изменены."
    }
    fun showEvidence(id:String)=simple {
        repo {
            val annotation=it.getAnnotation(id)
            ExperimentAnalytics.formatEvidence(it.evidence(id))+
                "\n\nМетка: "+(annotation?.tag?.ifBlank { "—" } ?: "—")+
                "\nЗаметка: "+(annotation?.note?.ifBlank { "—" } ?: "—")
        }
    }
    fun compareLastTwo(pkg:String)=simple {
        repo {
            val rows=it.recentSessions(pkg,2)
            if(rows.size<2) "Нужно как минимум две записанные сессии этого приложения."
            else {
                val newer=it.evidence(rows[0].id)
                val older=it.evidence(rows[1].id)
                val trials=listOf(
                    ExperimentTrial("A",rows[1],"","",older),
                    ExperimentTrial("B",rows[0],"","",newer)
                )
                ExperimentAnalytics.formatComparison(
                    "Две последние сессии · "+pkg,
                    "Наблюдательное сравнение по истории",
                    ExperimentAnalytics.armSummary("A","Предыдущая",trials),
                    ExperimentAnalytics.armSummary("B","Последняя",trials)
                )+"\n\nПРЕДЫДУЩАЯ:\n"+ExperimentAnalytics.formatEvidence(older)+
                    "\n\nПОСЛЕДНЯЯ:\n"+ExperimentAnalytics.formatEvidence(newer)
            }
        }
    }
    fun roster(id:String)=simple {
        repo {
            val exp=it.experiment(id) ?: error("Эксперимент не найден")
            val trials=it.trials(id)
            if(trials.isEmpty()) "У эксперимента пока нет назначенных сессий."
            else buildString {
                append(exp.name).append(" · ").append(exp.packageName).append('\n')
                append("A: ").append(exp.labelA).append(" / B: ").append(exp.labelB).append('\n')
                for(trial in trials) {
                    append("\n").append(trial.arm).append(" · ")
                    append(DateFormat.getDateTimeInstance().format(Date(trial.session.startedAtMs)))
                    append(" · ").append(trial.session.endReason ?: "НЕ ЗАВЕРШЕНА")
                    append("\nТег: ").append(trial.tag.ifBlank { "—" })
                    append("\nЗаметка: ").append(trial.note.ifBlank { "—" })
                    append("\nЗамеров: ").append(trial.evidence.samples)
                    append(" · пропусков >45 с: ").append(trial.evidence.gapsOver45s)
                    append("\nID: ").append(trial.session.id).append('\n')
                }
            }
        }
    }
    fun createExperiment(pkg:String,name:String,h:String,a:String,b:String)=simple {
        val id=repo { it.create(pkg,name,h,a,b) }
        selectedExperiment=id
        "Эксперимент создан: $name\n$id\nНазначьте реальные сессии для A и B."
    }
    fun pickExperiments()=submit { n ->
        val found=repo { it.experiments() }
        ExperimentLabUiState("Экспериментов: ${found.size}",n,experiments=found)
    }
    fun updateExperiment(id:String,name:String,h:String,a:String,b:String)=simple {
        if(repo { it.update(id,name,h,a,b) }) "Эксперимент обновлён."
        else "Эксперимент не найден."
    }
    fun assign(exp:String,session:String,arm:String)=simple {
        repo { it.assign(exp,session,arm) }
        "Сессия $session назначена условию $arm. Сохранённые замеры не изменены."
    }
    fun unassign(exp:String,session:String)=simple {
        if(repo { it.unassign(exp,session) }) "Сессия исключена из эксперимента."
        else "Назначение отсутствовало."
    }
    fun compare(id:String)=simple { repo { it.report(id) } }
    fun share(id:String)=submit { n ->
        val report=repo { it.report(id) }
        ExperimentLabUiState(report,n,share=report)
    }
    fun delete(id:String)=simple {
        if(repo { it.delete(id) }) {
            if(selectedExperiment==id)selectedExperiment=null
            "Эксперимент удалён; история не затронута."
        } else "Эксперимент не найден."
    }
    fun exportCsv(id:String,uri:Uri)=simple {
        repo { r ->
            (getApplication<Application>().contentResolver.openOutputStream(uri,"w")
                ?: error("Cannot open export")).use { stream ->
                OutputStreamWriter(stream,Charsets.UTF_8).use { writer ->
                    r.exportCsv(id,writer)
                }
            }
        }
        "CSV сохранён в выбранный документ. Экспорт выполнен только по вашему действию."
    }
    private fun <T> repo(block:(ExperimentRepository)->T):T =
        ExperimentRepository(getApplication<Application>()).use(block)
    private fun simple(job:()->String)=submit { n -> ExperimentLabUiState(job(),n) }
    private fun submit(job:(Int)->ExperimentLabUiState) {
        if(closed.get())return
        val request=generation.incrementAndGet()
        live.value=ExperimentLabUiState("Чтение локальных данных…",request)
        worker.execute {
            val result=runCatching { job(request) }.getOrElse {
                ExperimentLabUiState("Ошибка: "+it.javaClass.simpleName+
                    " · "+it.message.orEmpty().take(150),request)
            }
            main.post { if(!closed.get() && generation.get()==request) live.value=result }
        }
    }
    fun consume(nonce:Int) {
        val s=live.value ?: return
        if(s.nonce==nonce) live.value=s.copy(packages=null,sessions=null,
            experiments=null,share=null)
    }
    override fun onCleared() {
        closed.set(true)
        generation.incrementAndGet()
        sessionGeneration.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
