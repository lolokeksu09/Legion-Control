package dev.lolokeksu.legioncontrol

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
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
class HardwareLabActivity : ComponentActivity() {
    private lateinit var viewModel: HardwareLabViewModel
    private lateinit var result: TextView


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
            viewModel.run(HardwareProbe.ROUTE)
        }
        addButton(root, "2 · Network Events — 30 секунд") { toggleNetworkEvents() }
        addButton(root, "3 · Storage Lab — 8 МиБ / SHA-256") {
            viewModel.run(HardwareProbe.STORAGE)
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
            viewModel.run(HardwareProbe.CODEC)
        }
        addButton(root, "COMPATIBILITY · ещё 7 функций") {
            startActivity(Intent(this, CompatibilityLabActivity::class.java))
        }
        addButton(root, "Поделиться последним отчётом") { shareLastReport() }
        addButton(root, "Назад в Gaming Lab") { finish() }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        viewModel=ViewModelProvider(this)[HardwareLabViewModel::class.java]
        viewModel.report.observe(this) { result.text=it }
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

    private fun toggleMotion() = viewModel.toggleMotion()

    private fun toggleNetworkEvents() = viewModel.toggleNetworkEvents()

    private fun report(text: String) { if (::result.isInitialized) result.text=text }

    private fun shareLastReport() {
        val text = viewModel.lastReport ?: return report("Сначала выполните диагностику.")
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
        viewModel.setVisible(true)
    }

    override fun onStop() {
        viewModel.setVisible(false)
        viewModel.cancelAll()
        super.onStop()
    }
}
