package dev.lolokeksu.legioncontrol

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** A rendered report derived exclusively from actual Android and Rust readings. */
data class RustSampleUiState(val text: String, val shareableReport: String?)

/**
 * One Activity-independent recording controller per Diagnostics screen lifetime.
 * ViewModel ownership survives rotation; there is no Activity, View, or consent token here.
 * All accepted SQLite writes, test completions, exports and dependent history reads share
 * the same serial queue. Rust JNI stays native and is invoked ONLY on explicit sample/reset.
 */
class RustSampleController(application: Application) : AndroidViewModel(application) {
    val history = LocalHistoryStore(application)
    val writer = Executors.newSingleThreadExecutor()
    private val engine = RustEngine()
    private val diagnosticTrends = DiagnosticTrendEngine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var diagnosticRequestId = 0L
    private var cleared = false
    private val mutableUiState = MutableLiveData<RustSampleUiState>()
    val uiState: LiveData<RustSampleUiState> = mutableUiState

    fun sample(): TimedBatteryPoint? {
        val context = getApplication<Application>()
        val battery = ResourceTelemetryBackend.readBatteryIntent(context)
        val temp = ResourceTelemetryBackend.batteryTemperatureDeciC(battery) ?: Int.MIN_VALUE
        val percent = ResourceTelemetryBackend.batteryPercent(battery) ?: -1
        val mode = ResourceTelemetryBackend.readDisplayMode(context)
        val refreshHz = ResourceTelemetryBackend.refreshRate(mode)
        val power = AndroidPowerSource.read(context, battery)
        val batteryHealth = BatteryHealthBackend.read(battery)
        val thermalStatus = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus
        }.getOrNull()?.takeIf { it in 0..6 }
        val elapsedMs = SystemClock.elapsedRealtime()
        val recordedAtMs = System.currentTimeMillis()
        val rust = engine.record(percent, temp, elapsedMs)
        var diagnosticSessionId: String? = null
        val persisted = rust.fold(
            onSuccess = {
                val id = ActiveSession.id ?: UUID.randomUUID().toString()
                // This ID is reserved on UI, but the SQLite write occurs on worker.
                ActiveSession.id = id
                diagnosticSessionId = id
                "Сохранение SQLite: выполняется"
            },
            onFailure = { "Не сохранено: Rust отклонил измерение" }
        )
        val backend = rust.fold(
            onSuccess = { state ->
                if (state.count < 2) {
                    "Первое измерение сохранено"
                } else {
                    String.format(
                        Locale.getDefault(),
                        "%s · заряд %+d п.п. · температура %+.1f °C",
                        RussianQuantity.measurements(state.count),
                        state.chargeDelta, state.temperatureDeltaDeciC / 10f
                    )
                }
            },
            onFailure = { "Ошибка Rust: " + it.message }
        )
        val window = rust.fold(
            onSuccess = {
                engine.statistics().fold(
                    onSuccess = { stats ->
                        String.format(
                            Locale.getDefault(),
                            "%s · мин %.1f °C · макс %.1f °C · средняя %.1f °C",
                            RussianQuantity.measurements(stats.count),
                            stats.minTemperatureDeciC / 10f,
                            stats.maxTemperatureDeciC / 10f,
                            stats.meanTemperatureDeciC / 10f
                        )
                    },
                    onFailure = { "нет данных Rust: " + it.message }
                )
            },
            onFailure = { "недоступно из-за ошибки замера" }
        )
        val duration = rust.fold(
            onSuccess = {
                engine.durationMs().fold(
                    onSuccess = {
                        String.format(Locale.getDefault(), "%.1f с", it / 1000.0)
                    },
                    onFailure = { "нет данных Rust: " + it.message }
                )
            },
            onFailure = { "недоступно из-за ошибки замера" }
        )
        val output = StringBuilder()
        output.append("Устройство: ")
            .append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append('\n')
        output.append("Батарея: ")
            .append(if (percent in 0..100) "$percent%" else "нет данных").append('\n')
        output.append("Температура батареи: ")
            .append(if (temp != Int.MIN_VALUE) {
                String.format(Locale.getDefault(), "%.1f °C", temp / 10f)
            } else "нет данных").append('\n')
        output.append("Статус: ").append(power.chargingStatus).append('\n')
        output.append("Источник питания: ").append(power.chargingSource).append('\n')
        output.append("Напряжение батареи: ").append(
            power.voltageMillivolts?.let {
                String.format(Locale.getDefault(), "%.3f В", it / 1000f)
            } ?: "нет данных"
        ).append('\n')
        output.append("Ток батареи (по API Android): ").append(
            power.currentMicroAmps?.let {
                String.format(Locale.getDefault(), "%+.1f мА", it / 1000f)
            } ?: "не поддерживается"
        ).append('\n')
        output.append("Энергосбережение: ").append(
            when (power.powerSaving) {
                true -> "Включено"
                false -> "Выключено"
                null -> "нет данных"
            }
        ).append('\n')
        output.append("Экран: ")
            .append(mode?.physicalWidth?.toString() ?: "—")
            .append(" × ")
            .append(mode?.physicalHeight?.toString() ?: "—").append('\n')
        output.append("Режим дисплея: ")
            .append(refreshHz?.let {
                String.format(Locale.getDefault(), "%.1f Гц", it)
            } ?: "нет данных").append('\n')
        output.append("Rust JNI: ").append(backend).append('\n')
        output.append("Температура за историю: ").append(window).append('\n')
        output.append("Длительность окна Rust: ").append(duration).append('\n')
        output.append("Локальная история: ").append(persisted).append("\n\n")
        output.append("Данные только по запросу; нет управления системой или Bypass.")
        output.append("\n\n")
        output.append(BatteryHealthReport.format(
            batteryHealth, power.voltageMillivolts, power.currentMicroAmps
        ))
        val snapshot = output.toString()
        val reportHeader = if (rust.isSuccess) {
            "Legion Control · локальный отчёт\n" +
                DateFormat.getDateTimeInstance().format(Date(recordedAtMs)) + "\n\n"
        } else null
        mutableUiState.value = RustSampleUiState(
            snapshot + "\n\nСистемная диагностика: выполняется…",
            reportHeader?.plus(snapshot)
        )
        // Existing manual measurement is the only trigger for the new backend.
        // Snapshot collection runs away from the UI; no background polling.
        val requestId = ++diagnosticRequestId
        val acceptedSessionId = diagnosticSessionId
        writer.execute {
            // The legacy row MUST commit before the v2 child diagnostic record:
            // they share (session_id, elapsed_ms) with an enforced foreign key.
            val primarySave = if (acceptedSessionId == null) {
                Pair("Не сохранено: Rust отклонил измерение", false)
            } else runCatching {
                history.record(StoredReading(
                    sessionId = acceptedSessionId,
                    recordedAtMs = recordedAtMs,
                    elapsedMs = elapsedMs,
                    percent = percent,
                    temperatureDeciC = temp,
                    chargingStatus = power.chargingStatus,
                    chargingSource = power.chargingSource,
                    voltageMv = power.voltageMillivolts,
                    currentUa = power.currentMicroAmps,
                    powerSaving = power.powerSaving,
                    refreshRateHz = refreshHz,
                    screenWidthPx = mode?.physicalWidth,
                    screenHeightPx = mode?.physicalHeight
                ))
            }.fold(
                onSuccess = { inserted ->
                    Pair(
                        if (inserted) "Запись сохранена в SQLite"
                        else "Повторное измерение с тем же временем не продублировано",
                        inserted
                    )
                },
                onFailure = {
                    Pair("ОШИБКА сохранения SQLite: " + it.javaClass.simpleName, false)
                }
            )
            val diagnostics = runCatching {
                DeviceDiagnosticsBackend.read(context)
            }
            // Persist every successful legacy sample even when a newer UI
            // request supersedes this result. The DB write is never on UI.
            val historyResult = diagnostics.fold(
                onSuccess = { current ->
                    if (acceptedSessionId == null || !primarySave.second) {
                        Pair("SQLite диагностики: основной замер не сохранён", null)
                    } else runCatching {
                        val saved = history.recordDiagnostic(
                            DiagnosticHistorySchema.fromSnapshot(
                                sessionId = acceptedSessionId,
                                elapsedMs = elapsedMs,
                                recordedAtMs = recordedAtMs,
                                batteryPercent = percent,
                                batteryTemperatureDeciC = temp,
                                snapshot = current
                            )
                        )
                        val trend = DiagnosticSessionAnalyzer.analyze(
                            history.recentDiagnostics(acceptedSessionId)
                        )
                        Pair(
                            if (saved) "SQLite диагностики: запись сохранена"
                            else "SQLite диагностики: повторная запись пропущена",
                            DiagnosticSessionSummary.format(trend)
                        )
                    }.getOrElse {
                        Pair("SQLite диагностики: ошибка " + it.javaClass.simpleName, null)
                    }
                },
                onFailure = { Pair("SQLite диагностики: Android API недоступен", null) }
            )
            mainHandler.post {
                if (!cleared && requestId == diagnosticRequestId) {
                    val diagnosticText = diagnostics.fold(
                        onSuccess = { current ->
                            val trend = diagnosticTrends.record(current, percent)
                            DiagnosticsTextReport.format(current) + "\n\n" +
                                DiagnosticTrendTextReport.format(trend) +
                                (ThermalHistoryReport.warning(
                                    current.capabilities.thermalStatus,
                                    current.resources.thermalHeadroom.value
                                )?.let { "\n\nВНИМАНИЕ · " + it } ?: "")
                        },
                        onFailure = {
                            "Системная диагностика: ошибка " + it.javaClass.simpleName
                        }
                    )
                    val savedSnapshot = snapshot.replace(
                        "Локальная история: " + persisted,
                        "Локальная история: " + primarySave.first
                    )
                    val complete = savedSnapshot + "\n\n" + diagnosticText +
                        "\n\n" + historyResult.first +
                        (historyResult.second?.let { "\n" + it } ?: "")
                    mutableUiState.value = RustSampleUiState(complete, reportHeader?.plus(complete))
                }
            }
        }
        return if (rust.isSuccess && percent in 0..100 && temp in -400..1200) {
            TimedBatteryPoint(
                elapsedRealtimeMs = elapsedMs,
                recordedAtMs = recordedAtMs,
                batteryPercent = percent,
                temperatureDeciC = temp,
                currentMicroAmps = power.currentMicroAmps,
                voltageMillivolts = power.voltageMillivolts,
                thermalStatus = thermalStatus
            )
        } else null
    }

    /** Clear native RAM only. Previously committed SQLite rows are never erased. */
    fun resetSession(): Result<Int> {
        val result = engine.resetSession()
        result.onSuccess { discarded ->
            diagnosticRequestId++
            diagnosticTrends.clear()
            mutableUiState.value = RustSampleUiState(
                "Rust-сеанс завершён. Удалено из памяти: " +
                    RussianQuantity.measurements(discarded) +
                    ".\nСохранённые записи SQLite не удаляются.",
                null
            )
        }
        return result
    }

    override fun onCleared() {
        cleared = true
        diagnosticRequestId++
        // Queue close behind every already accepted write; never close mid-transaction.
        writer.execute { runCatching { history.close() } }
        writer.shutdown()
        super.onCleared()
    }
}
