package dev.lolokeksu.legioncontrol

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import android.app.AlertDialog
import android.os.Bundle
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.os.PowerManager
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
class MainActivity : ComponentActivity() {
    private lateinit var sampleController: RustSampleController
    private lateinit var historyModel: DiagnosticsHistoryViewModel
    private val history get() = sampleController.history
    private lateinit var result: TextView
    private lateinit var historyView: TextView
    private lateinit var sensorsView: TextView
    private lateinit var thermalView: TextView
    private lateinit var analyticsView: TextView
    private lateinit var timedView: TextView
    private var latestReport: String? = null
    // New backend probes may call OEM services; never block the Android UI thread.
    private val diagnosticsExecutor get() = sampleController.writer
    private var sensorRequestId = 0L
    private var sensorCapture: SensorCaptureBackend? = null
    private var timedTest: ForegroundBatteryTest? = null
    private var timedVisible = false

    companion object {
        private const val REQUEST_EXPORT_CSV = 4001
        private const val REQUEST_EXPORT_ARCHIVE = 4002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 20.dp, 18.dp, 28.dp)
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(TextView(this).apply {
            text = "LEGION CONTROL"
            textSize = 23f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        layout.addView(TextView(this).apply {
            text = "Kotlin + Rust · " + LegionUiSupport.versionLabel(this@MainActivity) +
                " · диагностика"
            textSize = 13f
            setTextColor(0xFFA5A5A5.toInt())
            setPadding(0, 8.dp, 0, 12.dp)
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "CAPTURE STUDIO · запись экрана и клипы MP4"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CaptureStudioActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "GAME TOOLBOX · плавающая панель поверх игр"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GameToolboxActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "HAPTIC LAB · вибромотор, шаблоны и ручная оценка"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, HapticLabActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "AUDIO LAB · стерео, частоты, маршруты и история"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AudioLabActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "APPLICATION CENTER · библиотека, обновления, разрешения"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ApplicationCenterActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "CONNECTIVITY OBSERVATORY · DNS / IPv4 / IPv6 / TCP / TLS"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ConnectivityLabActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "EXPERIMENT LAB · сессии, заметки и A/B"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ExperimentLabActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "SMART PROFILES · правила, аналитика и управление"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SmartProfilesActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "GAME SESSION · запись для любой игры/приложения"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GameSessionActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Gaming Lab · сеть, сенсоры, производительность"
            minHeight = 52.dp
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GamingDiagnosticsActivity::class.java))
            }
        })
        result = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(0, 16.dp, 0, 20.dp)
            text = "Нажмите «Обновить измерение» для записи показаний.\n" +
                "Только ручные замеры или явно запущенный 60-секундный тест на экране."
        }
        layout.addView(result)
        timedView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(0, 12.dp, 0, 12.dp)
            text = "Тест батареи: не запускался. Выполняется только на открытом экране."
        }
        layout.addView(timedView)
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Тест батареи · 60 секунд"
            minHeight = 48.dp
            setOnClickListener { startTimedBatteryTest() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Остановить тест батареи"
            minHeight = 48.dp
            setOnClickListener { stopTimedBatteryTest() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "История тестов батареи"
            minHeight = 48.dp
            setOnClickListener { showTimedBatteryHistory() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Обновить измерение"
            minHeight = 52.dp
            setOnClickListener { manualSample() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Поделиться последним отчётом"
            minHeight = 48.dp
            setOnClickListener { shareReport() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Сохранить историю в CSV"
            minHeight = 48.dp
            setOnClickListener { exportCsv() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
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
            LegionUiSupport.styleAction(this)
            text = "Профиль последнего сеанса"
            minHeight = 48.dp
            setOnClickListener { showSessionProfile() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Аналитика датчиков"
            minHeight = 48.dp
            setOnClickListener { showSensorQuality() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Экспорт полной диагностики ZIP"
            minHeight = 48.dp
            setOnClickListener { exportDiagnosticArchive() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Обновить список сеансов"
            minHeight = 48.dp
            setOnClickListener { showSavedHistory() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
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
            LegionUiSupport.styleAction(this)
            text = "Проверить доступность датчиков"
            minHeight = 48.dp
            setOnClickListener { checkSensors() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Снять показания датчиков"
            minHeight = 48.dp
            setOnClickListener { startSensorCapture() }
        })
        layout.addView(Button(this).apply {
            LegionUiSupport.styleAction(this)
            text = "Последние показания датчиков"
            minHeight = 48.dp
            setOnClickListener { showLastSensorReadings() }
        })
        LegionUiSupport.setModuleActivityContent(this, ScrollView(this).apply {
            isFillViewport = true
            addView(layout)
        }, "ДИАГНОСТИКА УСТРОЙСТВА")
        // The real Rust writer survives recreation and never retains Android UI objects.
        sampleController = ViewModelProvider(this)[RustSampleController::class.java]
        sampleController.uiState.observe(this) { state ->
            result.text = state.text
            latestReport = state.shareableReport
        }
        // Observers are lifecycle-aware: no TextView is retained by the read backend.
        historyModel = ViewModelProvider(this)[DiagnosticsHistoryViewModel::class.java]
        historyModel.report.observe(this) { reading ->
            when (reading.pane) {
                "history" -> historyView.text = reading.content
                "analytics" -> analyticsView.text = reading.content
                "thermal" -> thermalView.text = reading.content
                "timed" -> if (timedTest?.isActive != true) timedView.text = reading.content
                "sensors" -> sensorsView.text = reading.content
            }
        }
        // Persisted history is visible after a restart without generating a new sample.
        showSavedHistory()
    }

    override fun onStart() {
        super.onStart()
        timedVisible = true
    }

    private fun manualSample() {
        if (timedTest?.isActive == true) {
            Toast.makeText(this, "Сначала завершите тест батареи", Toast.LENGTH_SHORT).show()
        } else sample()
    }

    private fun startTimedBatteryTest() {
        if (!timedVisible || timedTest?.isActive == true) {
            Toast.makeText(this, "Тест уже выполняется или приложение неактивно",
                Toast.LENGTH_SHORT).show()
            return
        }
        // Separate SQLite session from manual measurements; keep the old Rust
        // in-memory window untouched (we do NOT reset or erase it).
        val previousSession = ActiveSession.id
        val id = UUID.randomUUID().toString()
        ActiveSession.id = id
        if (previousSession != null) {
            diagnosticsExecutor.execute {
                runCatching { history.endSession(previousSession, System.currentTimeMillis()) }
            }
        }
        val controller = ForegroundBatteryTest(
            testId = id,
            sessionId = id,
            sample = { sample() },
            onProgress = { attempted, accepted ->
                timedView.text = "Тест батареи · попыток: $attempted · принято Rust: $accepted\n" +
                    "Остановится через 60 с, при серьёзном нагреве или закрытии экрана."
            },
            onFinished = { completed ->
                timedTest = null
                if (ActiveSession.id == completed.sessionId) ActiveSession.id = null
                timedView.text = TimedBatterySummary.format(completed) +
                    "\nСохранение итога SQLite: выполняется…"
                // All earlier sample jobs must finish before the test summary.
                diagnosticsExecutor.execute {
                    val persisted = runCatching {
                        history.recordTimedBatteryTest(completed)
                    }
                    runOnUiThread {
                        if (!isDestroyed && ::timedView.isInitialized) {
                            timedView.text = TimedBatterySummary.format(completed) + "\n" +
                                persisted.fold(
                                    onSuccess = {
                                        if (it) "Итог теста сохранён в SQLite."
                                        else "Повторная запись итогов пропущена."
                                    },
                                    onFailure = {
                                        "Ошибка сохранения теста: " + it.javaClass.simpleName
                                    }
                                )
                            showSavedHistory()
                        }
                    }
                }
            }
        )
        timedTest = controller
        controller.start()
    }

    private fun stopTimedBatteryTest() {
        if (timedTest?.isActive != true) {
            Toast.makeText(this, "Активного теста нет", Toast.LENGTH_SHORT).show()
            return
        }
        timedTest?.stop(TimedBatteryStopReason.USER_STOP)
    }

    private fun showTimedBatteryHistory() {
        timedView.text = "Чтение сохранённых тестов…"
        // Enqueue through the original write executor to preserve save-before-read order.
        diagnosticsExecutor.execute { historyModel.request(DiagnosticsReadKind.TIMED_BATTERY) }
    }

    private val Int.dp: Int get() =
        (this * resources.displayMetrics.density + 0.5f).toInt()

    /** No SQLite query on UI: stale worker results cannot replace a newer list. */
    private fun showSavedHistory() {
        // Older SQLite writes complete before this read is handed to the ViewModel.
        diagnosticsExecutor.execute { historyModel.request(DiagnosticsReadKind.HISTORY) }
    }

    private fun showSensorQuality() {
        analyticsView.text = "Аналитика датчиков: чтение SQLite…"
        diagnosticsExecutor.execute { historyModel.request(DiagnosticsReadKind.SENSOR_QUALITY) }
    }

    private fun showSessionProfile() {
        analyticsView.text = "Профиль последнего сеанса: чтение SQLite…"
        diagnosticsExecutor.execute { historyModel.request(DiagnosticsReadKind.SESSION_PROFILE) }
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
        diagnosticsExecutor.execute { historyModel.request(DiagnosticsReadKind.THERMAL) }
    }

    private fun startSensorCapture() {
        if (sensorCapture != null) {
            Toast.makeText(this, "Измерение датчиков уже выполняется", Toast.LENGTH_SHORT).show()
            return
        }
        val requestId = ++sensorRequestId
        historyModel.invalidate("sensors")
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
        diagnosticsExecutor.execute { historyModel.request(DiagnosticsReadKind.SENSOR_READINGS) }
    }

    private fun confirmReset() {
        if (timedTest?.isActive == true) {
            Toast.makeText(this, "Сначала остановите тест батареи", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Завершить сеанс Rust?")
            .setMessage(
                "Будет очищена только текущая история Rust (до 64 образцов) в оперативной памяти. " +
                    "Сохранённые записи SQLite останутся. Начать новый сеанс можно новым измерением."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Завершить") { _, _ ->
                sampleController.resetSession().fold(
                    onSuccess = { discarded ->
                        val previousSession = ActiveSession.id
                        ActiveSession.id = null
                        latestReport = null
                        // Controller invalidates queued report updates on reset.
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

    private fun sample(): TimedBatteryPoint? {
        val point = sampleController.sample()
        // Read is queued after this sample's primary and child diagnostic writes.
        showSavedHistory()
        return point
    }

    override fun onStop() {
        timedVisible = false
        timedTest?.stop(TimedBatteryStopReason.APP_BACKGROUND)
        if (sensorCapture != null) {
            sensorRequestId++
            sensorCapture?.cancel()
            sensorCapture = null
            if (::sensorsView.isInitialized) sensorsView.text = "Измерение датчиков отменено"
        }
        super.onStop()
    }

    override fun onDestroy() {
        timedTest?.stop(TimedBatteryStopReason.APP_BACKGROUND)
        sensorRequestId++
        sensorCapture?.cancel()
        sensorCapture = null
        // ViewModel closes its write queue after all accepted writes on final clear.
        super.onDestroy()
    }
}
