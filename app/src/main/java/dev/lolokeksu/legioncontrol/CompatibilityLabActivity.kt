package dev.lolokeksu.legioncontrol

import android.app.Activity
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
class CompatibilityLabActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private var generation = 0L
    private var visible = false
    private var lastCompletedReport: String? = null
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
            execute { DisplayModeInspector.inspect(display) }
        }
        addButton(root, "2 · Audio Output Inspector") {
            execute { AudioOutputInspector.inspect(applicationContext) }
        }
        addButton(root, "3 · Gamepad Inventory") {
            execute { ControllerInventory.format(ControllerInventory.connected()) }
        }
        addButton(root, "4 · Gamepad Live · кнопки и стики") {
            if (visible) startActivity(Intent(this,GamepadLiveActivity::class.java))
        }
        addButton(root, "5 · DRM / Widevine Compatibility") {
            execute { DrmCapabilityInspector.inspect() }
        }
        addButton(root, "6 · Gaming Readiness · снимок системы") {
            execute {
                GamingReadinessRules.format(GamingReadinessRules.analyze(
                    GamingReadinessSnapshot.read(applicationContext,display)
                ))
            }
        }
        addButton(root, "7 · Quick Compute Probe · локальный CPU-тест") {
            execute { QuickComputeProbe.run(applicationContext) }
        }
        addButton(root, "Полный отчёт о совместимости") { runFullReport() }
        addButton(root, "Поделиться последним завершённым отчётом") { shareLastReport() }
        addButton(root, "Вернуться в Hardware Lab") { finish() }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
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

    private fun execute(read: () -> String) {
        if (!visible) return
        if (task?.isDone == false) {
            output.text = "Предыдущий запрос ещё выполняется."
            return
        }
        val requestId = ++generation
        output.text = "Диагностика по запросу…"
        task = executor.submit {
            val result = runCatching { read() }.getOrElse { error ->
                "Диагностика не завершилась: " + error.javaClass.simpleName
            }
            runOnUiThread {
                if (visible && !isDestroyed && requestId == generation) {
                    output.text = result
                    lastCompletedReport = result
                }
            }
        }
    }

    private fun runFullReport() {
        execute {
            // A broken OEM provider must not erase results from healthy providers.
            val sections: List<Pair<String, () -> String>> = listOf(
                "Display" to { DisplayModeInspector.inspect(display) },
                "Audio" to { AudioOutputInspector.inspect(applicationContext) },
                "Gamepads" to {
                    ControllerInventory.format(ControllerInventory.connected())
                },
                "DRM" to { DrmCapabilityInspector.inspect() },
                "Gaming Readiness" to {
                    GamingReadinessRules.format(GamingReadinessRules.analyze(
                        GamingReadinessSnapshot.read(applicationContext,display)
                    ))
                }
            )
            sections.map { (label, read) ->
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Report canceled")
                }
                runCatching { read() }.getOrElse {
                    label + " · сведения недоступны: " + it.javaClass.simpleName
                }
            }.joinToString(
                "\n\n---\n\n",
                prefix = "LEGION CONTROL · OFFLINE COMPATIBILITY REPORT\n\n"
            )
        }
    }

    private fun shareLastReport() {
        val content = lastCompletedReport
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
        visible = true
    }

    override fun onStop() {
        visible = false
        generation++
        task?.cancel(true)
        super.onStop()
    }

    override fun onDestroy() {
        task?.cancel(true)
        executor.shutdownNow()
        super.onDestroy()
    }
}
