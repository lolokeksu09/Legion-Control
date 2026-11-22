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

data class AudioLabUiState(
    val message:String,
    val playing:Boolean,
    val nonce:Int=0,
    val runs:List<AudioTestRun>?=null
)

/** Original manual PCM playback and local history with lifecycle-aware outputs. */
class AudioLabViewModel(application:Application):AndroidViewModel(application) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val closed=AtomicBoolean()
    private val visible=AtomicBoolean()
    private val playing=AtomicBoolean()
    private val generation=AtomicInteger()
    private val backend=AudioTestBackend(application)
    private val monitor=AudioRouteMonitor(application) { text ->
        main.post { if(!closed.get() && visible.get()) mutableRoutes.value=text }
    }
    private val mutableState=MutableLiveData<AudioLabUiState>()
    val state:LiveData<AudioLabUiState> = mutableState
    private val mutableRoutes=MutableLiveData<String>()
    val routes:LiveData<String> = mutableRoutes
    var selectedId:String?=null
        private set
    var lastCompletedReport:String?=null
        private set

    fun setVisible(value:Boolean) { visible.set(value) }
    fun startRouteMonitor() { if(visible.get() && !closed.get()) monitor.start() }
    fun stopRouteMonitor() { monitor.stop() }
    fun stopPlayback() { backend.stop() }
    fun refreshRoutes() {
        if(visible.get()) mutableRoutes.value=AudioRouteInspector.snapshot(getApplication<Application>())
    }

    fun play(type:String,which:String,hz:Int,length:Int) {
        if(!visible.get() || closed.get() || !playing.compareAndSet(false,true))return
        if(!AudioSignalGenerator.isValid(type,which,hz,length)) {
            playing.set(false)
            mutableState.value=AudioLabUiState("Недопустимый аудиосигнал.",false)
            return
        }
        backend.arm()
        lastCompletedReport=null
        val request=generation.incrementAndGet()
        mutableState.value=AudioLabUiState(
            "Подготовка исходного PCM через backend · $type / $which / $hz Гц…",true,request)
        worker.execute {
            var runId:String?=null
            val message=runCatching {
                AudioLabRepository(getApplication<Application>()).use { repo ->
                    val id=repo.start(type,which,hz,length)
                    runId=id
                    val playback=runCatching {
                        if(!visible.get()) backend.stop()
                        backend.play(type,which,hz,length) { current,total ->
                            main.post {
                                if(visible.get() && !closed.get() && playing.get() &&
                                    generation.get()==request) {
                                    mutableState.value=AudioLabUiState(
                                        "AudioTrack: $current / $total PCM кадров\n"+
                                        "Фактическая слышимость, громкость и задержка "+
                                        "по Android API не измеряются.",true,request)
                                }
                            }
                        }
                    }.getOrElse { error ->
                        AudioPlaybackResult(System.currentTimeMillis().coerceAtLeast(1),
                            "ERROR",0,null,error.javaClass.simpleName.take(48),emptyList())
                    }
                    repo.finish(id,playback)
                    repo.format(id)
                }
            }.getOrElse { error ->
                "Ошибка аудиотеста: "+error.javaClass.simpleName+" · "+
                    error.message.orEmpty().take(140)
            }
            playing.set(false)
            main.post {
                if(!closed.get() && generation.get()==request) {
                    selectedId=runId
                    lastCompletedReport=message
                    mutableState.value=AudioLabUiState(message,false,request)
                }
            }
        }
    }

    fun pickHistory() = history {
        AudioLabRepository(getApplication<Application>()).use { it.runs(60) }
    }

    fun selectRun(id:String) = local {
        selectedId=id
        AudioLabRepository(getApplication<Application>()).use { it.format(id) }
    }

    fun compare() = local {
        AudioLabRepository(getApplication<Application>()).use { it.compareRecent() }
    }

    fun delete(id:String) = local {
        val deleted=AudioLabRepository(getApplication<Application>()).use { it.delete(id) }
        if(deleted) {
            selectedId=null
            "Аудиотест удалён из локальной истории."
        }else "Аудиотест уже отсутствует."
    }

    fun exportCsv(uri:Uri) = local {
        AudioLabRepository(getApplication<Application>()).use { repo ->
            (getApplication<Application>().contentResolver.openOutputStream(uri,"w")
                ?: error("Не удалось открыть CSV")).use { out ->
                OutputStreamWriter(out,Charsets.UTF_8).use { writer -> repo.exportCsv(writer) }
            }
        }
        "CSV записан в выбранный документ; ни одна аудиозапись не создавалась и не передавалась."
    }

    private fun local(job:()->String) {
        if(playing.get()) {
            mutableState.value=AudioLabUiState("Сначала остановите воспроизведение.",true)
            return
        }
        if(closed.get())return
        val request=generation.incrementAndGet()
        mutableState.value=AudioLabUiState("Чтение локальных данных…",false,request)
        worker.execute {
            val message=runCatching { job() }.getOrElse { e ->
                "Ошибка: "+e.javaClass.simpleName+" · "+e.message.orEmpty().take(140)
            }
            main.post {
                if(!closed.get() && generation.get()==request) {
                    lastCompletedReport=message
                    mutableState.value=AudioLabUiState(message,false,request)
                }
            }
        }
    }

    private fun history(job:()->List<AudioTestRun>) {
        if(playing.get() || closed.get())return
        val request=generation.incrementAndGet()
        mutableState.value=AudioLabUiState("Чтение локальных данных…",false,request)
        worker.execute {
            val result=runCatching { job() }
            main.post {
                if(!closed.get() && generation.get()==request) {
                    val entries=result.getOrNull()
                    mutableState.value=AudioLabUiState(
                        if(entries==null) "Ошибка: "+result.exceptionOrNull()?.javaClass?.simpleName
                        else "История: ${entries.size} записей.",false,request,entries)
                }
            }
        }
    }

    fun consumePicker(nonce:Int) {
        val current=mutableState.value ?: return
        if(current.nonce==nonce && current.runs!=null)
            mutableState.value=current.copy(runs=null)
    }

    override fun onCleared() {
        closed.set(true)
        backend.stop()
        monitor.stop()
        generation.incrementAndGet()
        worker.shutdown()
        super.onCleared()
    }
}
