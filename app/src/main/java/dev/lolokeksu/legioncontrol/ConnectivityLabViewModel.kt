package dev.lolokeksu.legioncontrol

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ConnectivityLabUiState(
    val message:String,
    val testing:Boolean,
    val nonce:Int=0,
    val runs:List<ConnectivityRun>?=null,
    val shareText:String?=null,
    val deletedRunId:String?=null
)

/** Explicit, cancellable DNS/TCP/TLS user tests. No Activity reference or background service. */
class ConnectivityLabViewModel(application:Application):AndroidViewModel(application) {
    companion object { private const val INTERVAL_MS=12_000L }
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var activeTicket:ConnectivityRunTicket?=null
    private val closed=AtomicBoolean()
    private val testing=AtomicBoolean()
    private val generation=AtomicInteger()
    private var task:Future<*>?=null
    private val mutableState=MutableLiveData<ConnectivityLabUiState>()
    val state:LiveData<ConnectivityLabUiState> = mutableState
    var host:String="one.one.one.one"
        private set
    var selectedRunId:String?=null
        private set

    fun chooseHost(value:String) {
        if(testing.get() || !ConnectivityProbe.validHost(value))return
        if(host!=value) generation.incrementAndGet() // invalidate old queued history/report
        host=value
        selectedRunId=null
        mutableState.value=ConnectivityLabUiState(
            "Выбран $host:443. Это внешний тестовый сервер, не игровой хост.",false)
    }

    fun selectRun(id:String) { selectedRunId=id }

    fun startTest(cycles:Int) {
        if(cycles !in listOf(1,6) || closed.get() || !testing.compareAndSet(false,true))return
        val target=host
        val ticket=ConnectivityRunTicket()
        activeTicket=ticket
        val request=generation.incrementAndGet()
        mutableState.value=ConnectivityLabUiState(
            "Запрошено подключение к $target:443. Запуск теста…",true,request)
        task=worker.submit {
            if(!ticket.start()) return@submit // cancelled before task began
            var message="Нет сохранённых результатов: запрос был отменён до начала измерений."
            var runId:String?=null
            var outcome="COMPLETE"
            try {
                ConnectivityRepository(getApplication<Application>()).use { db ->
                    try {
                        runId=db.start(target,if(cycles==1)"SINGLE" else "STABILITY")
                        for(cycle in 1..cycles) {
                            if(ticket.isCancelled() || Thread.currentThread().isInterrupted) {
                                outcome="CANCELLED"
                                break
                            }
                            val begin=SystemClock.elapsedRealtime()
                            val readings=ConnectivityProbe.capture(
                                getApplication<Application>(),target,cycle)
                            readings.forEach { db.record(runId!!,it) }
                            val short="${cycle}/$cycles · "+
                                readings.joinToString(" · ") { it.family+": "+it.result }
                            main.post {
                                if(!closed.get() && !ticket.isCancelled() && generation.get()==request)
                                    mutableState.value=ConnectivityLabUiState(
                                        "Проверка $target:443\n$short\n"+
                                        "Оставьте экран открытым; не переключайтесь в другое приложение.",
                                        true,request)
                            }
                            if(cycle<cycles) {
                                val delay=(INTERVAL_MS-(SystemClock.elapsedRealtime()-begin))
                                    .coerceAtLeast(0L)
                                if(delay>0L) Thread.sleep(delay)
                            }
                        }
                        if(ticket.isCancelled() || Thread.currentThread().isInterrupted)
                            outcome="CANCELLED"
                    } catch(interrupted:InterruptedException) {
                        outcome="CANCELLED"
                        Thread.interrupted() // Clear flag before SQLite finish()
                    } catch(error:Exception) {
                        outcome=if(ticket.isCancelled())"CANCELLED" else "FAILED"
                    } finally { runId?.let { db.finish(it,outcome) } }
                    message=runId?.let { ConnectivityAnalytics.render(db.summary(it)) } ?:
                        "Не удалось начать тест и создать запись в SQLite."
                }
            }catch(error:Exception) {
                message="Ошибка сетевого теста: "+error.javaClass.simpleName+
                    ". Приложение не выполняло скрытый фоновый тест."
            }finally {
                if(activeTicket===ticket) {
                    activeTicket=null
                    testing.set(false)
                }
                main.post {
                    if(!closed.get() && generation.get()==request) {
                        if(!ticket.isCancelled()) {
                            selectedRunId=runId
                            mutableState.value=ConnectivityLabUiState(message,false,request)
                        } else mutableState.value=ConnectivityLabUiState(
                            "Тест отменён. Частичная запись сохранена, если SQLite успела "+
                            "завершить запись. Проверьте историю.",false,request)
                    }
                }
            }
        }
    }

    /** Idempotent: stop does not schedule a replacement or leave a repeating job. */
    fun cancelTest() {
        if(!testing.get())return
        val ticket=activeTicket ?: return
        val beforeStart=ticket.cancel()
        task?.cancel(true)
        if(beforeStart) {
            // Future.cancel may prevent the callable from ever entering its finally.
            if(activeTicket===ticket) {
                activeTicket=null
                testing.set(false)
                generation.incrementAndGet()
                mutableState.value=ConnectivityLabUiState(
                    "Тест отменён до начала сетевых измерений.",false,generation.get())
            }
            return
        }
        // Android DNS may be uninterruptible once the worker has begun.
        mutableState.value = ConnectivityLabUiState(
            "Отмена запрошена. DNS-запрос Android может завершиться с задержкой; " +
                "новые циклы не начнутся.", true, generation.get())
    }

    fun showHistory() {
        val target=host
        localWithRuns {
            ConnectivityRepository(getApplication<Application>()).use { it.runs(target,50) }
        }
    }

    fun showSelected(id:String) = local {
        ConnectivityRepository(getApplication<Application>()).use { db ->
            val summary=db.summary(id)
            val details=db.samples(id).joinToString("\n") { reading ->
                "цикл ${reading.cycle} · ${reading.family} · ${reading.result} · "+
                "TCP="+(reading.tcpMs?.let {
                    String.format(Locale.US,"%.1f мс",it)
                } ?: "н/д")+" · TLS="+(reading.tlsMs?.let {
                    String.format(Locale.US,"%.1f мс",it)
                } ?: "н/д")+
                (if(reading.errorCode.isBlank()) "" else " · "+reading.errorCode)
            }
            ConnectivityAnalytics.render(summary)+"\n\nПОИМЁННЫЕ ПОПЫТКИ:\n"+details
        }
    }

    fun compare() = local {
        ConnectivityRepository(getApplication<Application>()).use { it.comparison(host) }
    }

    fun weekReport() = local {
        ConnectivityRepository(getApplication<Application>()).use { it.lastSevenDays(host) }
    }

    fun share(id:String) = submit { nonce ->
        val text=ConnectivityRepository(getApplication<Application>()).use {
            ConnectivityAnalytics.render(it.summary(id))
        }
        ConnectivityLabUiState(text,false,nonce,shareText=text)
    }

    fun delete(id:String) = submit { nonce ->
        val removed=ConnectivityRepository(getApplication<Application>()).use { it.delete(id) }
        ConnectivityLabUiState(
            if(removed) "Сетевой тест удалён. История других модулей не изменена."
            else "Запись уже отсутствует.",
            false,nonce,deletedRunId=id)
    }

    fun exportCsv(uri:Uri) = local {
        ConnectivityRepository(getApplication<Application>()).use { db ->
            (getApplication<Application>().contentResolver.openOutputStream(uri,"w")
                ?: error("No output stream")).use { stream ->
                OutputStreamWriter(stream,Charsets.UTF_8).use { writer -> db.writeCsv(writer) }
            }
        }
        "Вся история Connectivity Observatory экспортирована CSV. "+
            "Никаких IP адресов, SSID или данных чужих приложений не сохранялось."
    }

    fun consumeDialog(nonce:Int) {
        val current=mutableState.value ?: return
        if(current.nonce==nonce && (current.runs!=null || current.shareText!=null))
            mutableState.value=current.copy(runs=null,shareText=null)
    }

    private fun local(job:()->String)=submit { nonce ->
        ConnectivityLabUiState(job(),false,nonce)
    }

    private fun localWithRuns(job:()->List<ConnectivityRun>)=submit { nonce ->
        val entries=job()
        ConnectivityLabUiState("Записей для выбранного узла: ${entries.size} "+
            "(показаны последние 50).",false,nonce,runs=entries)
    }

    private fun submit(job:(Int)->ConnectivityLabUiState) {
        if(testing.get() || closed.get()) {
            if(testing.get()) mutableState.value=ConnectivityLabUiState(
                "Сначала остановите сетевую проверку.",true)
            return
        }
        val request=generation.incrementAndGet()
        mutableState.value=ConnectivityLabUiState("Чтение локальной истории…",false,request)
        worker.execute {
            val state=runCatching { job(request) }.getOrElse {
                ConnectivityLabUiState("Ошибка: "+it.javaClass.simpleName+
                    " · "+it.message.orEmpty().take(120),false,request)
            }
            main.post {
                if(!closed.get() && generation.get()==request) {
                    if(state.deletedRunId!=null && selectedRunId==state.deletedRunId)
                        selectedRunId=null
                    mutableState.value=state.copy(deletedRunId=null)
                }
            }
        }
    }

    override fun onCleared() {
        cancelTest()
        closed.set(true)
        generation.incrementAndGet()
        worker.shutdownNow()
        super.onCleared()
    }
}
