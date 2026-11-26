package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class HardwareProbe { ROUTE, STORAGE, CODEC }

/**
 * Real manual hardware measurements and bounded sensor/network callbacks.
 * No Activity/View, root permission, network scheduler or implicit probe.
 */
class HardwareLabViewModel(app:Application):AndroidViewModel(app) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val visible=AtomicBoolean()
    private val closed=AtomicBoolean()
    private val generation=AtomicInteger()
    private var runningJob:Future<*>?=null
    private var motion:MotionStabilityCapture?=null
    private var networkEvents:ForegroundNetworkEvents?=null
    private val mutableReport=MutableLiveData<String>()
    val report:LiveData<String> = mutableReport
    var lastReport:String?=null
        private set

    fun setVisible(on:Boolean) { visible.set(on) }

    fun run(which:HardwareProbe) {
        if(!visible.get() || closed.get())return
        if(runningJob?.isDone==false || motion != null || networkEvents != null) {
            mutableReport.value="Сначала дождитесь завершения текущего теста."
            return
        }
        val token=generation.incrementAndGet()
        mutableReport.value="Измерение выполняется по запросу…"
        runningJob=worker.submit {
            val app=getApplication<Application>()
            val result=runCatching {
                when(which) {
                    HardwareProbe.ROUTE -> LocalRouteDiagnostics.inspect(app)
                    HardwareProbe.STORAGE -> SandboxIoMetrics.format(SandboxIoBenchmark.run(app))
                    HardwareProbe.CODEC -> CodecCapabilityDiagnostics.inspect()
                }
            }.getOrElse { "Тест недоступен: "+it.javaClass.simpleName }
            main.post {
                if(visible.get() && !closed.get() && generation.get()==token)
                    deliver(result)
            }
        }
    }

    private fun deliver(message:String) {
        lastReport=message
        mutableReport.value=message
    }

    fun toggleMotion() {
        val running=motion
        if(running!=null) {
            motion=null
            running.stop(true)
            return
        }
        if(!visible.get() || closed.get())return
        if (runningJob?.isDone == false || networkEvents != null) {
            mutableReport.value = "Сначала завершите текущую диагностику."
            return
        }
        val token=generation.incrementAndGet()
        val capture=MotionStabilityCapture(getApplication<Application>()) { measured ->
            main.post {
                if(visible.get() && !closed.get() && generation.get()==token) {
                    motion=null
                    deliver(MotionMetrics.format(measured))
                }
            }
        }
        motion=capture
        mutableReport.value="Motion Stability · 5 секунд акселерометр и гироскоп…"
        runCatching { capture.start() }.onFailure {
            motion=null
            capture.stop(false)
            mutableReport.value="Датчики движения недоступны: "+it.javaClass.simpleName
        }
    }

    fun toggleNetworkEvents() {
        val running=networkEvents
        if(running!=null) {
            networkEvents=null
            running.stop(true)
            return
        }
        if(!visible.get() || closed.get())return
        if (runningJob?.isDone == false || motion != null) {
            mutableReport.value = "Сначала завершите текущую диагностику."
            return
        }
        val token=generation.incrementAndGet()
        val capture=ForegroundNetworkEvents(
            getApplication<Application>(),
            update={ value ->
                main.post {
                    if(visible.get() && !closed.get() && generation.get()==token)
                        deliver(value)
                }
            },
            completed={ value ->
                main.post {
                    if(visible.get() && !closed.get() && generation.get()==token) {
                        networkEvents=null
                        deliver(value)
                    }
                }
            }
        )
        if(capture.start()) {
            networkEvents=capture
            mutableReport.value="NETWORK EVENTS · подписка на изменения сети на 30 секунд.\n"+
                "Нажмите кнопку повторно для досрочной остановки."
        } else mutableReport.value="Network Events: регистрация callback отклонена Android"
    }

    fun cancelAll() {
        generation.incrementAndGet()
        motion?.stop(false)
        motion=null
        networkEvents?.stop(false)
        networkEvents=null
        runningJob?.cancel(true)
    }

    override fun onCleared() {
        visible.set(false)
        closed.set(true)
        cancelAll()
        worker.shutdownNow()
        super.onCleared()
    }
}
