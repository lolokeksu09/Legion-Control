package dev.lolokeksu.legioncontrol

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class MonitorState(
    val running: Boolean = false,
    val points: List<MonitorPoint> = emptyList(),
    val baseline: MonitorPoint? = null,
    val intervalMs: Long = 5000L,
    val exporting: Boolean = false,
    val message: String = "Мониторинг не запущен. Выберите интервал и нажмите старт."
)

class LiveMonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val mutable = MutableLiveData(MonitorState())
    val state: LiveData<MonitorState> = mutable
    private val pendingDocument = PendingDocument<List<MonitorPoint>>()
    val hasPendingDocument get() = pendingDocument.hasPending
    private var visible = false
    private var generation = 0L
    private var started = 0L
    private var pending: Future<*>? = null
    private val tick = Runnable { sample() }

    private fun current(): MonitorState = requireNotNull(mutable.value)

    fun setVisible(value: Boolean) {
        visible = value
        if (!value) stop("Мониторинг остановлен: экран закрыт.")
    }

    fun interval(value: Long) {
        if (current().running) return
        mutable.value = current().copy(intervalMs = MonitorPolicy.interval(value))
    }

    fun start() {
        if (!visible || current().running || current().exporting || hasPendingDocument) return
        generation++
        started = SystemClock.elapsedRealtime()
        mutable.value = current().copy(running = true, points = emptyList(),
            message = "Мониторинг запущен. Ожидание первого измерения.")
        sample()
    }

    private fun sample() {
        if (!visible || !current().running) return
        val token = generation
        val runStart = started
        pending = worker.submit {
            val elapsed = (SystemClock.elapsedRealtime() - runStart).coerceAtLeast(0)
            val app = getApplication<Application>()
            val telemetry = runCatching { GameSessionTelemetry.snapshot(app) }.getOrNull()
            if (Thread.currentThread().isInterrupted) return@submit
            val resources = runCatching { ResourceTelemetryBackend.readForMonitor(app) }.getOrNull()
            val memory = resources?.systemMemory?.value
            val point = MonitorPoint(
                elapsedMs = elapsed,
                battery = telemetry?.batteryPercent,
                temperatureDeciC = telemetry?.batteryTempDeciC,
                availableRamBytes = memory?.availableBytes,
                totalRamBytes = memory?.totalBytes,
                displayHz = telemetry?.refreshHz,
                currentMicroA = telemetry?.currentMicroA,
                thermalStatus = telemetry?.thermalStatus,
                powerSaving = telemetry?.powerSaving,
                network = telemetry?.networkType,
                validatedNetwork = telemetry?.networkValidated,
                thermalHeadroom = resources?.thermalHeadroom?.value,
                memoryState = resources?.systemMemory?.state ?: ProbeState.ERROR,
                headroomState = resources?.thermalHeadroom?.state ?: ProbeState.ERROR,
                telemetryError = telemetry == null
            )
            main.post {
                if (token != generation || !visible || !current().running) return@post
                val next = MonitorPolicy.append(current().points, point)
                mutable.value = current().copy(points = next,
                    message = "Мониторинг: ${next.size} / ${MonitorPolicy.MAX_POINTS} замеров")
                if (next.size >= MonitorPolicy.MAX_POINTS) stop("Лимит замеров достигнут.")
                else main.postDelayed(tick, current().intervalMs)
            }
        }
    }

    fun stop(reason: String = "Мониторинг остановлен пользователем.") {
        if (!current().running) return
        generation++
        main.removeCallbacks(tick)
        pending?.cancel(true)
        pending = null
        mutable.value = current().copy(running = false, message = reason)
    }

    fun pinBaseline() {
        val point = current().points.lastOrNull() ?: return
        mutable.value = current().copy(baseline = point, message = "Контрольная точка сохранена.")
    }

    fun clearBaseline() {
        mutable.value = current().copy(baseline = null, message = "Контрольная точка сброшена.")
    }

    /** Freeze the run before opening the system document picker. */
    fun beginExport(): Boolean {
        val s = current()
        if(s.running || s.exporting || s.points.isEmpty()) return false
        return pendingDocument.begin(s.points.toList())
    }

    fun finishExport(uri: android.net.Uri?) {
        val points = pendingDocument.take()
        if(uri == null) {
            mutable.value = current().copy(message = "Экспорт отменён.")
            return
        }
        if(points == null) {
            mutable.value = current().copy(message = "Исходные замеры недоступны. Повторите экспорт.")
            return
        }
        if(current().running || current().exporting) return
        mutable.value = current().copy(exporting = true, message = "Сохраняю CSV…")
        val token = generation
        worker.execute {
            val result = runCatching {
                val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: error("Document provider returned no stream")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(MonitorCsv.render(points)) }
            }
            main.post {
                if (token != generation) return@post
                mutable.value = current().copy(exporting = false, message = if (result.isSuccess)
                    "CSV сохранён: ${points.size} замеров." else "Не удалось сохранить CSV.")
            }
        }
    }

    override fun onCleared() {
        pendingDocument.take()
        generation++
        main.removeCallbacks(tick)
        pending?.cancel(true)
        worker.shutdownNow()
        super.onCleared()
    }
}
