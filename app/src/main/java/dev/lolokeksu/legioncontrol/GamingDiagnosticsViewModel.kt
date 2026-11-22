package dev.lolokeksu.legioncontrol

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class GamingProbe { NETWORK,BATTERY,WIFI,USAGE,CHARGING,SESSION,SYSTEM }

/** One-off public API inspections, real local SQLite comparisons and user-managed listeners. */
class GamingDiagnosticsViewModel(app:Application):AndroidViewModel(app) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val visible=AtomicBoolean()
    private val closed=AtomicBoolean()
    private val generation=AtomicInteger()
    private var task:Future<*>?=null
    private var lastCharge:ChargeReading?=null
    private var frameCapture:FramePacingCapture?=null
    private var thermalListener:PowerManager.OnThermalStatusChangedListener?=null
    private var thermalEvents=0
    private val mutableReport=MutableLiveData<String>()
    val report:LiveData<String> = mutableReport

    fun setVisible(on:Boolean) { visible.set(on) }

    fun run(kind:GamingProbe,pkg:String="",refreshHzDisplay:Display?=null) {
        if(closed.get() || !visible.get())return
        if(task?.isDone==false) {
            mutableReport.value="Другая проверка ещё выполняется. Заверши её перед следующей."
            return
        }
        val token=generation.incrementAndGet()
        mutableReport.value="Выполняется проверка по запросу…"
        task=worker.submit {
            val app=getApplication<Application>()
            val value=runCatching {
                when(kind) {
                    GamingProbe.NETWORK -> TcpProbeStatistics.format(NetworkDiagnostics.probe())
                    GamingProbe.BATTERY -> DeviceInsights.battery(app)
                    GamingProbe.WIFI -> DeviceInsights.network(app)
                    GamingProbe.USAGE -> GameUsageDiagnostics.read(app,pkg)
                    GamingProbe.CHARGING -> {
                        val reading=ChargingDiagnostics.read(app)
                        val text=ChargingDiagnostics.format(reading,lastCharge)
                        lastCharge=reading
                        text
                    }
                    GamingProbe.SESSION -> {
                        val sessions=LocalHistoryStore(app).use { history ->
                            history.recentSessions(30).filter { it.samples>=2 }.take(2)
                                .mapIndexed { index,entry ->
                                    ComparedSession("Сеанс "+(index+1),
                                        requireNotNull(SessionAnalytics.analyze(
                                            history.sessionReadings(entry.id))))
                                }
                        }
                        SessionComparison.format(sessions)
                    }
                    GamingProbe.SYSTEM -> DeviceInsights.system(app,refreshHzDisplay)
                }
            }.getOrElse { "Ошибка диагностики: "+it.javaClass.simpleName }
            main.post {
                if(!closed.get() && visible.get() && token==generation.get())
                    mutableReport.value=value
            }
        }
    }

    fun toggleThermalGuard() {
        if(closed.get() || !visible.get())return
        if(thermalListener!=null) {
            stopThermalGuard()
            mutableReport.value="Thermal Guard остановлен; событий за сеанс: $thermalEvents"
            return
        }
        val app=getApplication<Application>()
        val manager=app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if(manager==null) {
            mutableReport.value="Thermal Guard: сервис PowerManager недоступен"
            return
        }
        thermalEvents=0
        val listener=PowerManager.OnThermalStatusChangedListener { status ->
            main.post {
                if(visible.get() && !closed.get() && thermalListener!=null) {
                    thermalEvents++
                    mutableReport.value="THERMAL GUARD · событие $thermalEvents\n"+
                        "Android Thermal Status: "+ThermalSeverity.describe(status)+
                        "\nСлушатель удаляется при сворачивании приложения."
                }
            }
        }
        val success=runCatching {
            manager.addThermalStatusListener(app.mainExecutor,listener)
        }.isSuccess
        if(!success) {
            mutableReport.value="Thermal Guard: подписка не поддерживается этой прошивкой"
            return
        }
        thermalListener=listener
        run(GamingProbe.SYSTEM) // Initial public-API snapshot is manual and read off the UI thread.
    }

    fun stopThermalGuard() {
        val listener=thermalListener ?: return
        thermalListener=null
        val power=getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        runCatching { power?.removeThermalStatusListener(listener) }
    }

    fun startFramePacing(hz:Float) {
        if(!visible.get() || closed.get())return
        if(frameCapture!=null) {
            mutableReport.value="Frame Pacing уже выполняется"
            return
        }
        val token=generation.incrementAndGet()
        mutableReport.value="Frame Pacing · 5 секунд VSYNC callback внутри Legion Control…"
        val capture=FramePacingCapture(hz) { outcome ->
            main.post {
                if(visible.get() && !closed.get() && token==generation.get()) {
                    frameCapture=null
                    mutableReport.value=FramePacingStatistics.format(outcome)
                }
            }
        }
        frameCapture=capture
        capture.start()
    }

    fun cancelAll() {
        generation.incrementAndGet()
        frameCapture?.stop(false)
        frameCapture=null
        stopThermalGuard()
        task?.cancel(true)
    }

    override fun onCleared() {
        closed.set(true)
        visible.set(false)
        cancelAll()
        worker.shutdownNow()
        super.onCleared()
    }
}
