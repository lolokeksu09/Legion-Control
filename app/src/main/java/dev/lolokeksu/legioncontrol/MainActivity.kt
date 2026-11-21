package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.view.Display
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Read-only Android snapshots -> real Rust JNI -> private SQLite.
 * No background sampling, privileged APIs or automatic network transmission.
 */
class MainActivity : Activity() {
    private val engine = RustEngine()
    private val history by lazy { LocalHistoryStore(applicationContext) }
    private lateinit var result: TextView
    private lateinit var historyView: TextView
    private lateinit var sensorsView: TextView
    private lateinit var thermalView: TextView
    private lateinit var analyticsView: TextView
    private var latestReport: String? = null
    // New backend probes may call OEM services; never block the Android UI thread.
    private val diagnosticsExecutor = Executors.newSingleThreadExecutor()
    private val diagnosticTrends = DiagnosticTrendEngine()
    private var diagnosticRequestId = 0L
    private var historyRequestId = 0L
    private var analyticsRequestId = 0L
    private var sensorRequestId = 0L
    private var sensorCapture: SensorCaptureBackend? = null

    companion object {
        private const val REQUEST_EXPORT_CSV = 4001
        private const val REQUEST_EXPORT_ARCHIVE = 4002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 40.dp, 20.dp, 24.dp)
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(TextView(this).apply {
            text = "LEGION CONTROL"
            textSize = 23f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        layout.addView(TextView(this).apply {
            text = "Kotlin + Rust · backend alpha.12"
            textSize = 13f
            setTextColor(0xFFA5A5A5.toInt())
            setPadding(0, 8.dp, 0, 12.dp)
        })
        result = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(0, 16.dp, 0, 20.dp)
            text = "Нажмите «Обновить измерение» для записи показаний.\n" +
                "Автоматических или фоновых измерений нет."
        }
        layout.addView(result)
        layout.addView(Button(this).apply {
            text = "Обновить измерение"
            minHeight = 52.dp
            setOnClickListener { sample() }
        })
        layout.addView(Button(this).apply {
            text = "Поделиться последним отчётом"
            minHeight = 48.dp
            setOnClickListener { shareReport() }
        })
        layout.addView(Button(this).apply {
            text = "Сохранить историю в CSV"
            minHeight = 48.dp
            setOnClickListener { exportCsv() }
        })
        layout.addView(Button(this).apply {
            text = "Завершить сеанс Rust"
            minHeight = 48.dp
            setOnClickListener { confirmReset() }
        })
        historyView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, 18.dp, 0, 10.dp)
            setLineSpacing(5f, 1f)
        }
        layout.addView(historyView)
        analyticsView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, 12.dp, 0, 12.dp)
            setLineSpacing(5f, 1f)
            text = "Аналитика по сохранённым данным доступна по запросу."
        }
        layout.addView(analyticsView)
        layout.addView(Button(this).apply {
            text = "Профиль последнего сеанса"
            minHeight = 48.dp
            setOnClickListener { showSessionProfile() }
        })
        layout.addView(Button(this).apply {
            text = "Аналитика датчиков"
            minHeight = 48.dp
            setOnClickListener { showSensorQuality() }
        })
        layout.addView(Button(this).apply {
            text = "Экспорт полной диагностики ZIP"
            minHeight = 48.dp
            setOnClickListener { exportDiagnosticArchive() }
        })
        layout.addView(Button(this).apply {
            text = "Обновить список сеансов"
            minHeight = 48.dp
            setOnClickListener { showSavedHistory() }
        })
        layout.addView(Button(this).apply {
            text = "Тепловая история"
            minHeight = 48.dp
            setOnClickListener { showThermalHistory() }
        })
        thermalView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(0, 12.dp, 0, 12.dp)
            text = "Тепловая история доступна по запросу."
        }
        layout.addView(thermalView)
        sensorsView = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFBFBFBF.toInt())
            setPadding(0, 18.dp, 0, 10.dp)
            text = "Датчики: проверка ещё не запускалась."
        }
        layout.addView(sensorsView)
        layout.addView(Button(this).apply {
            text = "Проверить доступность датчиков"
            minHeight = 48.dp
            setOnClickListener { checkSensors() }
        })
        layout.addView(Button(this).apply {
            text = "Снять показания датчиков"
            minHeight = 48.dp
            setOnClickListener { startSensorCapture() }
        })
        layout.addView(Button(this).apply {
            text = "Последние показания датчиков"
            minHeight = 48.dp
            setOnClickListener { showLastSensorReadings() }
        })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(layout)
        })
        // Persisted history is visible after a restart without generating a new sample.
        showSavedHistory()
    }

    private val Int.dp: Int get() =
        (this * resources.displayMetrics.density + 0.5f).toInt()

    /** No SQLite query on UI: stale worker results cannot replace a newer list. */
    private fun showSavedHistory() {
        val requestId = ++historyRequestId
        diagnosticsExecutor.execute {
            val text = runCatching {
                val total = history.count()
                val sessions = history.recentSessions()
                buildString {
                    append("СОХРАНЁННАЯ ИСТОРИЯ · ")
                        .append(RussianQuantity.measurements(total.toInt())).append("\n")
                    append("Сеансов: ").append(sessions.size)
                    if (sessions.size == 12) append(" (показаны последние 12)")
                    for (session in sessions) {
                        append("\n· ")
                        append(DateFormat.getDateTimeInstance().format(Date(session.startedAtMs)))
                        append(" · ").append(RussianQuantity.measurements(session.samples))
                        val durationMs = (session.lastAtMs - session.startedAtMs).coerceAtLeast(0L)
                        append(" · ").append(String.format(
                            Locale.getDefault(), "%.1f с", durationMs / 1000.0
                        ))
                        if (session.endedAtMs != null) append(" · завершён")
                    }
                    append("\nЗаписи остаются после перезапуска приложения.")
                }
            }.getOrElse { "Ошибка локальной базы: " + it.javaClass.simpleName }
            runOnUiThread {
                if (!isDestroyed && requestId == historyRequestId) historyView.text = text
            }
        }
    }

    private fun showSensorQuality() {
        val requestId = ++analyticsRequestId
        analyticsView.text = "Аналитика датчиков: чтение SQLite…"
        diagnosticsExecutor.execute {
            val text = runCatching {
                SensorQualityAnalytics.format(history.recentSensorAttempts())
            }.getOrElse { "Ошибка аналитики датчиков: " + it.javaClass.simpleName }
            runOnUiThread {
                if (!isDestroyed && requestId == analyticsRequestId) analyticsView.text = text
            }
        }
    }

    private fun showSessionProfile() {
        val requestId = ++analyticsRequestId
        analyticsView.text = "Профиль последнего сеанса: чтение SQLite…"
        diagnosticsExecutor.execute {
            val text = runCatching {
                val count = history.latestSessionReadingCount()
                val points = history.latestSessionReadings()
                SessionAnalytics.format(SessionAnalytics.analyze(points), count > points.size)
            }.getOrElse { "Ошибка профиля сеанса: " + it.javaClass.simpleName }
            runOnUiThread {
                if (!isDestroyed && requestId == analyticsRequestId) analyticsView.text = text
            }
        }
    }

    private fun checkSensors() {
        val available = AndroidSensorInventory.inspect(this)
        sensorsView.text = buildString {
            append("ДАТЧИКИ · публичный SensorManager\n")
            for ((label, present) in available) {
                append("· ").append(label).append(": ")
                append(when (present) {
                    true -> "обнаружен"
                    false -> "не найден или скрыт прошивкой"
                    null -> "не удалось проверить"
                }).append('\n')
            }
            append("Обнаружение не означает получение показаний. CPU/GPU датчики не проверяются.")
        }
    }

    private fun showThermalHistory() {
        diagnosticsExecutor.execute {
            val text = runCatching {
                ThermalHistoryReport.format(history.recentThermalReadings())
            }.getOrElse { "Ошибка тепловой истории SQLite: " + it.javaClass.simpleName }
            runOnUiThread {
                if (!isDestroyed && ::thermalView.isInitialized) thermalView.text = text
            }
        }
    }

    private fun startSensorCapture() {
        if (sensorCapture != null) {
            Toast.makeText(this, "Измерение датчиков уже выполняется", Toast.LENGTH_SHORT).show()
            return
        }
        val requestId = ++sensorRequestId
        sensorsView.text = "Сбор событий датчиков: до 3 с, для освещённости до 6 с…"
        val capture = SensorCaptureBackend(this) { outcome ->
            sensorCapture = null
            // Disk access is serialized off the main/UI thread.
            diagnosticsExecutor.execute {
                val report = runCatching {
                    val before = history.lastSensorSamples()
                    val written = history.recordSensorCapture(outcome)
                    val total = history.sensorSampleCount()
                    SensorSampleReport.format(outcome, before, written, total)
                }.getOrElse { "Ошибка SQLite датчиков: " + it.javaClass.simpleName }
                runOnUiThread {
                    if (!isDestroyed && sensorRequestId == requestId) sensorsView.text = report
                }
            }
        }
        sensorCapture = capture
        capture.start()
    }

    private fun showLastSensorReadings() {
        val requestId = ++sensorRequestId
        diagnosticsExecutor.execute {
            val text = runCatching {
                val latest = history.lastSensorSamples()
                val total = history.sensorSampleCount()
                val attempts = history.lastSensorAttempts()
                buildString {
                    append("ИСТОРИЯ ДАТЧИКОВ SQLite · ").append(total).append(" событий\n")
                    for (kind in SensorKind.entries) {
                        append("· ").append(kind.title).append(": ")
                        val sample = latest[kind]
                        if (sample == null) append("нет сохранённых событий")
                        else {
                            append(sample.values.joinToString(" / ")).append(" ")
                                .append(kind.unit).append(" · ")
                                .append(DateFormat.getDateTimeInstance().format(
                                    Date(sample.recordedAtMs)
                                ))
                        }
                        append('\n')
                    }
                    append('\n')
                    append(SensorSampleReport.formatSavedAttempts(attempts))
                }
            }.getOrElse { "Ошибка истории датчиков: " + it.javaClass.simpleName }
            runOnUiThread {
                if (!isDestroyed && sensorRequestId == requestId) sensorsView.text = text
            }
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Завершить сеанс Rust?")
            .setMessage(
                "Будет очищена только текущая история Rust (до 64 образцов) в оперативной памяти. " +
                    "Сохранённые записи SQLite останутся. Начать новый сеанс можно новым измерением."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Завершить") { _, _ ->
                engine.resetSession().fold(
                    onSuccess = { discarded ->
                        val previousSession = ActiveSession.id
                        ActiveSession.id = null
                        latestReport = null
                        // Prevent a previously queued probe from restoring a cleared report.
                        diagnosticRequestId++
                        diagnosticTrends.clear()
                        result.text = "Rust-сеанс завершён. Удалено из памяти: " +
                            RussianQuantity.measurements(discarded) + ".\n" +
                            "Сохранённые записи SQLite не удаляются."
                        // Serialized after pending battery writes; never update a not-yet-inserted row.
                        diagnosticsExecutor.execute {
                            val saved = runCatching {
                                if (previousSession != null) {
                                    history.endSession(previousSession, System.currentTimeMillis())
                                }
                            }
                            runOnUiThread {
                                if (!isDestroyed) {
                                    if (saved.isFailure) Toast.makeText(
                                        this,
                                        "Не удалось завершить запись сеанса SQLite",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    showSavedHistory()
                                }
                            }
                        }
                        showSavedHistory()
                    },
                    onFailure = {
                        Toast.makeText(this, "Rust: " + it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .show()
    }

    private fun shareReport() {
        val report = latestReport
        if (report == null) {
            Toast.makeText(this, "Сначала выполните измерение", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, "Поделиться отчётом Legion Control"))
        }.onFailure {
            Toast.makeText(this, "Нет приложения для отправки отчёта", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportCsv() {
        // Existing battery CSV format is unchanged; existence check runs off UI.
        diagnosticsExecutor.execute {
            val count = runCatching { history.count() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                count.fold(
                    onSuccess = {
                        if (it == 0L) {
                            Toast.makeText(this, "Нет сохранённых измерений", Toast.LENGTH_SHORT).show()
                        } else {
                            openExportPicker(REQUEST_EXPORT_CSV, "text/csv", ".csv")
                        }
                    },
                    onFailure = {
                        Toast.makeText(this, "Не удалось прочитать историю", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun exportDiagnosticArchive() =
        openExportPicker(REQUEST_EXPORT_ARCHIVE, "application/zip", ".zip")

    private fun openExportPicker(request: Int, mime: String, extension: String) {
        val name = "Legion-Control-diagnostics-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + extension
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        @Suppress("DEPRECATION")
        runCatching { startActivityForResult(intent, request) }
            .onFailure {
                Toast.makeText(this, "Нет системного выбора файлов: " + it.message, Toast.LENGTH_LONG).show()
            }
    }

    @Deprecated("Uses the platform document picker without external storage permissions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK ||
            (requestCode != REQUEST_EXPORT_CSV && requestCode != REQUEST_EXPORT_ARCHIVE)
        ) return
        val uri = data?.data ?: return
        diagnosticsExecutor.execute {
            val result = runCatching {
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: error("Android не предоставил поток записи")
                if (requestCode == REQUEST_EXPORT_CSV) {
                    OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
                        "CSV сохранён: " + RussianQuantity.measurements(history.writeCsv(writer))
                    }
                } else {
                    val counts = output.use { history.writeDiagnosticArchive(it) }
                    "ZIP сохранён: " + counts.battery + " записей батареи, " +
                        counts.attempts + " попыток датчиков, " +
                        counts.sensors + " показаний датчиков"
                }
            }
            runOnUiThread {
                if (!isDestroyed) result.fold(
                    onSuccess = {
                        Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                    },
                    onFailure = {
                        Toast.makeText(
                            this, "Ошибка экспорта: " + it.javaClass.simpleName, Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    private fun sample() {
        @Suppress("DEPRECATION")
        val battery = runCatching {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = battery?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE
        ) ?: Int.MIN_VALUE
        val percent = if (scale > 0 && level in 0..scale) {
            (level * 100f / scale).toInt()
        } else {
            -1
        }
        val mode = (
            getSystemService(DISPLAY_SERVICE) as? DisplayManager
        )?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
        val power = AndroidPowerSource.read(this, battery)
        val batteryHealth = BatteryHealthBackend.read(battery)
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
            .append(mode?.refreshRate?.let {
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
        latestReport = reportHeader?.plus(snapshot)
        result.text = snapshot + "\n\nСистемная диагностика: выполняется…"
        // Existing manual measurement is the only trigger for the new backend.
        // Snapshot collection runs away from the UI; no background polling.
        val requestId = ++diagnosticRequestId
        val acceptedSessionId = diagnosticSessionId
        diagnosticsExecutor.execute {
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
                    refreshRateHz = mode?.refreshRate,
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
                DeviceDiagnosticsBackend.read(applicationContext)
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
            runOnUiThread {
                if (!isDestroyed && requestId == diagnosticRequestId) {
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
                    result.text = complete
                    latestReport = reportHeader?.plus(complete)
                }
            }
        }
        showSavedHistory()
    }

    override fun onStop() {
        if (sensorCapture != null) {
            sensorRequestId++
            sensorCapture?.cancel()
            sensorCapture = null
            if (::sensorsView.isInitialized) sensorsView.text = "Измерение датчиков отменено"
        }
        super.onStop()
    }

    override fun onDestroy() {
        sensorRequestId++
        diagnosticRequestId++
        sensorCapture?.cancel()
        sensorCapture = null
        // Finish accepted writes before closing the shared SQLite helper.
        diagnosticsExecutor.execute { runCatching { history.close() } }
        diagnosticsExecutor.shutdown()
        super.onDestroy()
    }
}
