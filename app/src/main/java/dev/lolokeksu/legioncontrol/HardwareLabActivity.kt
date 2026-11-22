package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Manual-only hardware diagnostics. No background service, root, Shizuku,
 * protected APIs, app enumeration, hidden system settings or cloud telemetry.
 */
class HardwareLabActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var runningJob: Future<*>? = null
    private var motion: MotionStabilityCapture? = null
    private var networkEvents: ForegroundNetworkEvents? = null
    private lateinit var result: TextView
    private var lastReport: String? = null
    private var visible = false
    private var reportGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 30.dp, 18.dp, 24.dp)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "LEGION CONTROL · HARDWARE LAB"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Новые локальные инструменты · без Root и Shizuku. " +
                "Проверки запускаются вручную, слушатели работают только при открытом экране."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10.dp, 0, 12.dp)
        })
        result = TextView(this).apply {
            text = "Выберите тест. Измерения не загружаются на сервер."
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(0, 12.dp, 0, 20.dp)
            setTextIsSelectable(true)
        }
        root.addView(result)
        addButton(root, "1 · Local Route Inspector") {
            startWorker { LocalRouteDiagnostics.inspect(applicationContext) }
        }
        addButton(root, "2 · Network Events — 30 секунд") { toggleNetworkEvents() }
        addButton(root, "3 · Storage Lab — 8 МиБ / SHA-256") {
            startWorker { SandboxIoMetrics.format(SandboxIoBenchmark.run(applicationContext)) }
        }
        addButton(root, "4 · Motion Stability — 5 секунд") { toggleMotion() }
        addButton(root, "5 · Display Patterns — пиксели и цвета") {
            AlertDialog.Builder(this)
                .setTitle("Проверка экрана")
                .setMessage(
                    "Показываются сплошные чёрный, белый, красный, зелёный, " +
                        "синий и серый цвета. Нажатие меняет цвет, шестое завершает тест. " +
                        "Кнопка «Назад» закрывает тест в любой момент. " +
                        "Яркость системы не изменяется. Быстрой вспышки нет."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Начать") { _, _ ->
                    startActivity(Intent(this, DisplayPatternActivity::class.java))
                }
                .show()
        }
        addButton(root, "6 · Codec Inspector — аппаратные видеодекодеры") {
            startWorker { CodecCapabilityDiagnostics.inspect() }
        }
        addButton(root, "Поделиться последним отчётом") { shareLastReport() }
        addButton(root, "Назад в Gaming Lab") { finish() }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            minHeight = 48.dp
            setOnClickListener { action() }
        })
    }

    private fun report(text: String) {
        if (!isDestroyed && ::result.isInitialized) {
            result.text = text
            lastReport = text
        }
    }

    private fun startWorker(block: () -> String) {
        if (!visible) return
        if (runningJob?.isDone == false) {
            report("Сначала дождитесь завершения текущего теста.")
            return
        }
        val generation = ++reportGeneration
        report("Измерение выполняется по запросу…")
        runningJob = worker.submit {
            val value = runCatching { block() }.getOrElse {
                "Тест недоступен: " + it.javaClass.simpleName
            }
            runOnUiThread {
                if (visible && generation == reportGeneration && !isDestroyed)
                    report(value)
            }
        }
    }

    private fun toggleMotion() {
        val existing = motion
        if (existing != null) {
            motion = null
            existing.stop(true)
            return
        }
        if (!visible) return
        val capture = MotionStabilityCapture(applicationContext) {
            motion = null
            if (visible) report(MotionMetrics.format(it))
        }
        motion = capture
        report("Motion Stability · 5 секунд акселерометр и гироскоп…")
        runCatching { capture.start() }.onFailure {
            motion = null
            capture.stop(false)
            report("Датчики движения недоступны: " + it.javaClass.simpleName)
        }
    }

    private fun toggleNetworkEvents() {
        val existing = networkEvents
        if (existing != null) {
            networkEvents = null
            existing.stop(true)
            return
        }
        if (!visible) return
        val capture = ForegroundNetworkEvents(
            applicationContext,
            update = { if (visible) report(it) },
            completed = {
                networkEvents = null
                if (visible) report(it)
            }
        )
        if (capture.start()) {
            networkEvents = capture
            report("NETWORK EVENTS · подписка на изменения сети на 30 секунд.\n" +
                "Нажмите кнопку повторно для досрочной остановки.")
        } else report("Network Events: регистрация callback отклонена Android")
    }

    private fun shareLastReport() {
        val text = lastReport ?: return report("Сначала выполните диагностику.")
        if (text.contains("выполняется по запросу")) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Отправить отчёт")) }
            .onFailure { report("Нет приложения для отправки текста.") }
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        ++reportGeneration
        motion?.stop(false)
        motion = null
        networkEvents?.stop(false)
        networkEvents = null
        runningJob?.cancel(true)
        super.onStop()
    }

    override fun onDestroy() {
        motion?.stop(false)
        networkEvents?.stop(false)
        runningJob?.cancel(true)
        worker.shutdownNow()
        super.onDestroy()
    }
}
