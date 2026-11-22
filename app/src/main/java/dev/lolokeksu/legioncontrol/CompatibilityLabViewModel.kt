package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class CompatibilityRead { DISPLAY,AUDIO,GAMEPADS,DRM,READINESS,COMPUTE,ALL }

/** Read-only, opt-in public API diagnostics. No Activity, View or listener held. */
class CompatibilityLabViewModel(app:Application):AndroidViewModel(app) {
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val visible=AtomicBoolean()
    private val closed=AtomicBoolean()
    private val generation=AtomicInteger()
    private var task:Future<*>?=null
    private val current=MutableLiveData<String>()
    val report:LiveData<String> = current
    var lastCompletedReport:String?=null
        private set

    fun setVisible(on:Boolean) { visible.set(on) }
    fun run(kind:CompatibilityRead,display:Display?) {
        if(!visible.get() || closed.get())return
        if(task?.isDone==false) {
            current.value="Предыдущий запрос ещё выполняется."
            return
        }
        val request=generation.incrementAndGet()
        current.value="Диагностика по запросу…"
        task=executor.submit {
            val text=runCatching { read(kind,display) }.getOrElse {
                "Диагностика не завершилась: "+it.javaClass.simpleName
            }
            main.post {
                if(visible.get() && !closed.get() && generation.get()==request) {
                    lastCompletedReport=text
                    current.value=text
                }
            }
        }
    }

    private fun read(kind:CompatibilityRead,display:Display?):String {
        val app=getApplication<Application>()
        return when(kind) {
            CompatibilityRead.DISPLAY -> DisplayModeInspector.inspect(display)
            CompatibilityRead.AUDIO -> AudioOutputInspector.inspect(app)
            CompatibilityRead.GAMEPADS -> ControllerInventory.format(ControllerInventory.connected())
            CompatibilityRead.DRM -> DrmCapabilityInspector.inspect()
            CompatibilityRead.READINESS -> GamingReadinessRules.format(
                GamingReadinessRules.analyze(GamingReadinessSnapshot.read(app,display)))
            CompatibilityRead.COMPUTE -> QuickComputeProbe.run(app)
            CompatibilityRead.ALL -> {
                val sections:List<Pair<String,()->String>> = listOf(
                    "Display" to { DisplayModeInspector.inspect(display) },
                    "Audio" to { AudioOutputInspector.inspect(app) },
                    "Gamepads" to { ControllerInventory.format(ControllerInventory.connected()) },
                    "DRM" to { DrmCapabilityInspector.inspect() },
                    "Gaming Readiness" to {
                        GamingReadinessRules.format(GamingReadinessRules.analyze(
                            GamingReadinessSnapshot.read(app,display)))
                    }
                )
                sections.map { (label,read) ->
                    if(Thread.currentThread().isInterrupted)
                        throw InterruptedException("Report canceled")
                    runCatching { read() }.getOrElse {
                        label+" · сведения недоступны: "+it.javaClass.simpleName
                    }
                }.joinToString("\n\n---\n\n",
                    prefix="LEGION CONTROL · OFFLINE COMPATIBILITY REPORT\n\n")
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        task?.cancel(true)
    }

    override fun onCleared() {
        closed.set(true)
        visible.set(false)
        cancel()
        executor.shutdownNow()
        super.onCleared()
    }
}
