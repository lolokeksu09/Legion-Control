package dev.lolokeksu.legioncontrol

import android.app.Activity
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
import android.widget.TextView

/** Backend-first Android -> Rust JNI -> Android demonstration. */
class MainActivity : Activity() {
    private val engine = RustEngine()
    private lateinit var result: TextView

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
            text = "Kotlin + Rust · backend alpha"
            textSize = 13f
            setTextColor(0xFFA5A5A5.toInt())
            setPadding(0, 8.dp, 0, 12.dp)
        })
        result = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(0, 16.dp, 0, 20.dp)
        }
        layout.addView(result)
        layout.addView(Button(this).apply {
            text = "Обновить измерение"
            minHeight = 52.dp
            setOnClickListener { sample() }
        })
        setContentView(layout)
        sample()
    }

    private val Int.dp: Int get() =
        (this * resources.displayMetrics.density + 0.5f).toInt()

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
        val percent = if (level >= 0 && scale > 0) {
            (level * 100f / scale).toInt()
        } else {
            -1
        }
        val mode = (
            getSystemService(DISPLAY_SERVICE) as? DisplayManager
        )?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
        val rust = engine.record(percent, temp, SystemClock.elapsedRealtime())
        val backend = rust.fold(
            onSuccess = { state ->
                if (state.count < 2) {
                    "Первое измерение сохранено"
                } else {
                    String.format(
                        "%d измерений · заряд %+d п.п. · температура %+.1f °C",
                        state.count, state.chargeDelta,
                        state.temperatureDeltaDeciC / 10f
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
                            java.util.Locale.getDefault(),
                            "%d измерений · мин %.1f °C · макс %.1f °C · средняя %.1f °C",
                            stats.count,
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
        val batteryText = "нет данных"
        val output = StringBuilder()
        output.append("Устройство: ")
            .append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append('\n')
        output.append("Батарея: ")
            .append(if (percent in 0..100) percent.toString() + "%" else batteryText)
            .append('\n')
        output.append("Температура: ")
            .append(if (temp != Int.MIN_VALUE) {
                String.format("%.1f °C", temp / 10f)
            } else "нет данных").append('\n')
        output.append("Экран: ")
            .append(mode?.physicalWidth?.toString() ?: "—")
            .append(" × ")
            .append(mode?.physicalHeight?.toString() ?: "—").append('\n')
        output.append("Режим дисплея: ")
            .append(mode?.refreshRate?.let { String.format("%.1f Гц", it) }
                ?: "нет данных").append('\n')
        output.append("Rust JNI: ").append(backend).append('\n')
        output.append("Температура за историю: ").append(window).append("\n\n")
        output.append("Только измерения по запросу. Системные настройки не изменяются.")
        result.text = output.toString()
    }
}
