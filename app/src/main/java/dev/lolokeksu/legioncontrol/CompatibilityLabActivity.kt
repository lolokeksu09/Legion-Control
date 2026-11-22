package dev.lolokeksu.legioncontrol

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
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
 * Opt-in compatibility analysis. Each read is user-triggered. Device info and
 * report text remain local unless the user invokes Android's share chooser.
 */
class CompatibilityLabActivity : ComponentActivity() {
    private lateinit var viewModel: CompatibilityLabViewModel
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 30.dp, 18.dp, 30.dp)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "LEGION CONTROL · COMPATIBILITY"
            setTextColor(Color.WHITE)
            setTypeface(null,Typeface.BOLD)
            textSize = 21f
        })
        root.addView(TextView(this).apply {
            text = "Ещё 7 инструментов · только открытые Android API. " +
                "Никаких изменений системных режимов, Root, Shizuku или перехвата чужих игр."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12.dp, 0, 18.dp)
        })
        output = TextView(this).apply {
            text = "Выбери инструмент. Последний завершённый отчёт можно отправить вручную."
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f,1f)
            setPadding(0, 14.dp, 0, 18.dp)
            setTextIsSelectable(true)
        }
        root.addView(output)
        addButton(root, "1 · Display Modes & HDR") {
            viewModel.run(CompatibilityRead.DISPLAY,display)
        }
        addButton(root, "2 · Audio Output Inspector") {
            viewModel.run(CompatibilityRead.AUDIO,display)
        }
        addButton(root, "3 · Gamepad Inventory") {
            viewModel.run(CompatibilityRead.GAMEPADS,display)
        }
        addButton(root, "4 · Gamepad Live · кнопки и стики") {
            startActivity(Intent(this,GamepadLiveActivity::class.java))
        }
        addButton(root, "5 · DRM / Widevine Compatibility") {
            viewModel.run(CompatibilityRead.DRM,display)
        }
        addButton(root, "6 · Gaming Readiness · снимок системы") {
            viewModel.run(CompatibilityRead.READINESS,display)
        }
        addButton(root, "7 · Quick Compute Probe · локальный CPU-тест") {
            viewModel.run(CompatibilityRead.COMPUTE,display)
        }
        addButton(root, "Полный отчёт о совместимости") { runFullReport() }
        addButton(root, "Поделиться последним завершённым отчётом") { shareLastReport() }
        addButton(root, "Вернуться в Hardware Lab") { finish() }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        viewModel=ViewModelProvider(this)[CompatibilityLabViewModel::class.java]
        viewModel.report.observe(this) { output.text=it }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private fun addButton(root: LinearLayout, title: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = title
            isAllCaps = false
            minHeight = 48.dp
            setOnClickListener { action() }
        })
    }

    private fun runFullReport() { viewModel.run(CompatibilityRead.ALL,display) }

    private fun shareLastReport() {
        val content = viewModel.lastCompletedReport
        if (content.isNullOrBlank()) {
            output.text = "Сначала заверши хотя бы одну диагностику."
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        runCatching {
            startActivity(Intent.createChooser(intent,"Поделиться отчётом"))
        }.onFailure { output.text = "Системное меню отправки недоступно" }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVisible(true)
    }

    override fun onStop() {
        viewModel.setVisible(false)
        viewModel.cancel()
        super.onStop()
    }
}
