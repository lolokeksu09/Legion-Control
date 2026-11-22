package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Ten opt-in Android public-API tools. No privileged control, continuous
 * telemetry, receiver, background service, Wi-Fi location access or uploads.
 */
class GamingDiagnosticsActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private lateinit var resultView: TextView
    private lateinit var packageInput: EditText
    private var lastCharge: ChargeReading? = null
    private var frameCapture: FramePacingCapture? = null
    private var touchDialog: Dialog? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var thermalEvents = 0
    private var visible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 32.dp, 18.dp, 28.dp)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "LEGION CONTROL · GAMING LAB"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "10 инструментов · Android API · запуск только по нажатию. " +
                "Ни один модуль не даёт Root, Bypass, FPS чужой игры или частоту CPU/GPU."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10.dp, 0, 16.dp)
        })
        resultView = TextView(this).apply {
            text = "Выбери диагностический модуль. Результаты остаются на устройстве."
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(4.dp, 12.dp, 4.dp, 20.dp)
            setTextIsSelectable(true)
        }
        root.addView(resultView)

        addButton(root, "1 · Network Analyzer — TCP/DNS") {
            runWorker { TcpProbeStatistics.format(NetworkDiagnostics.probe()) }
        }
        addButton(root, "2 · Battery Intelligence") {
            runWorker { DeviceInsights.battery(applicationContext) }
        }
        addButton(root, "3 · Thermal Guard — включить/выключить события") {
            toggleThermalGuard()
        }
        addButton(root, "4 · Touch Analyzer — мультитач и края") {
            openTouchTester()
        }
        addButton(root, "5 · Frame Pacing — 5 секунд") {
            startFramePacing()
        }
        addButton(root, "6 · Wi-Fi / LTE Inspector") {
            runWorker { DeviceInsights.network(applicationContext) }
        }
        packageInput = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "Пакет игры"
            setText(GameUsageDiagnostics.DEFAULT_PACKAGE)
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        }
        root.addView(packageInput)
        addButton(root, "7 · Game Session Tracker — за 24 часа") {
            val selectedPackage = packageInput.text.toString()
            if (!GameUsageDiagnostics.hasUsageAccess(this)) {
                report("Доступ к статистике использования ещё не предоставлен. " +
                    "Нажми кнопку настройки ниже и разреши Legion Control.")
            } else runWorker { GameUsageDiagnostics.read(applicationContext, selectedPackage) }
        }
        addButton(root, "Разрешить статистику использования (настройки Android)") {
            runCatching {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }.onFailure {
                report("Не удалось открыть специальные настройки доступа: " +
                    it.javaClass.simpleName)
            }
        }
        addButton(root, "8 · Charging Analyzer — сравнить с прошлым замером") {
            runWorker {
                val current = ChargingDiagnostics.read(applicationContext)
                val text = ChargingDiagnostics.format(current, lastCharge)
                lastCharge = current
                text
            }
        }
        addButton(root, "9 · Session Comparison — последние два сеанса") {
            runWorker {
                val sessions = LocalHistoryStore(applicationContext).use { history ->
                    history.recentSessions(30).filter { it.samples >= 2 }.take(2).mapIndexed {
                        index, entry ->
                        ComparedSession(
                            "Сеанс "+(index+1),
                            requireNotNull(SessionAnalytics.analyze(
                                history.sessionReadings(entry.id)
                            ))
                        )
                    }
                }
                SessionComparison.format(sessions)
            }
        }
        addButton(root, "10 · System Health") {
            runWorker { DeviceInsights.system(applicationContext, display) }
        }
        addButton(root, "Вернуться к Legion Control") { finish() }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun addButton(root: LinearLayout, title: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = title
            minHeight = 48.dp
            isAllCaps = false
            setOnClickListener { action() }
        })
    }

    private fun report(message: String) {
        if (!isDestroyed && ::resultView.isInitialized) resultView.text = message
    }

    private fun runWorker(block: () -> String) {
        if (!visible) {
            report("Экран неактивен, диагностика не запускается.")
            return
        }
        if (task?.isDone == false) {
            report("Другая проверка ещё выполняется. Заверши её перед следующей.")
            return
        }
        report("Выполняется проверка по запросу…")
        task = worker.submit {
            val text = runCatching { block() }.getOrElse {
                "Ошибка диагностики: " + it.javaClass.simpleName
            }
            runOnUiThread {
                if (visible && !isDestroyed) report(text)
            }
        }
    }

    private fun toggleThermalGuard() {
        val power = getSystemService(POWER_SERVICE) as? PowerManager
        if (power == null) {
            report("Thermal Guard: сервис PowerManager недоступен")
            return
        }
        if (thermalListener != null) {
            stopThermalGuard()
            report("Thermal Guard остановлен; событий за сеанс: " + thermalEvents)
            return
        }
        thermalEvents = 0
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            thermalEvents++
            if (visible) report(
                "THERMAL GUARD · событие "+thermalEvents+
                    "\nAndroid Thermal Status: "+ThermalSeverity.describe(status)+
                    "\nСлушатель удаляется при сворачивании приложения."
            )
        }
        val success = runCatching {
            power.addThermalStatusListener(mainExecutor, listener)
        }.isSuccess
        if (!success) {
            report("Thermal Guard: подписка не поддерживается этой прошивкой")
            return
        }
        thermalListener = listener
        runWorker { DeviceInsights.thermal(applicationContext) +
            "\nСлушатель изменений Thermal Status активен, пока открыт экран."
        }
    }

    private fun stopThermalGuard() {
        val listener = thermalListener ?: return
        thermalListener = null
        val manager = getSystemService(POWER_SERVICE) as? PowerManager
        runCatching { manager?.removeThermalStatusListener(listener) }
    }

    private fun startFramePacing() {
        if (frameCapture != null) {
            report("Frame Pacing уже выполняется")
            return
        }
        report("Frame Pacing · 5 секунд VSYNC callback внутри Legion Control…")
        val current = FramePacingCapture(display?.mode?.refreshRate ?: 0f) {
            frameCapture = null
            if (visible) report(FramePacingStatistics.format(it))
        }
        frameCapture = current
        current.start()
    }

    private fun openTouchTester() {
        if (!visible) return
        if (touchDialog != null) return
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        var touchView: TouchDiagnosticsView? = null
        val overlay = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val status = TextView(this).apply {
            text = "Проведи пальцами по экрану, проверяй края и мультитач."
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(12.dp, 6.dp, 12.dp, 6.dp)
        }
        val canvas = TouchDiagnosticsView(this) { text ->
            status.text = text
        }
        touchView = canvas
        overlay.addView(canvas, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            setBackgroundColor(0xE5000000.toInt())
            addView(status)
            addView(Button(this@GamingDiagnosticsActivity).apply {
                text = "Завершить Touch Analyzer"
                setOnClickListener { dialog.dismiss() }
            })
        }
        overlay.addView(controls, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))
        dialog.setContentView(overlay)
        dialog.setOnDismissListener {
            if (touchDialog === dialog) {
                touchDialog = null
                if (visible) report(canvas.summary())
            }
        }
        touchDialog = dialog
        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        frameCapture?.stop(false)
        frameCapture = null
        stopThermalGuard()
        touchDialog?.dismiss()
        touchDialog = null
        task?.cancel(true)
        super.onStop()
    }

    override fun onDestroy() {
        stopThermalGuard()
        frameCapture?.stop(false)
        task?.cancel(true)
        worker.shutdownNow()
        super.onDestroy()
    }
}
