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
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Backend-first view: on-demand Android readings, real Rust processing,
 * and a user-confirmed in-memory session reset. No background monitoring.
 */
class MainActivity : Activity() {
    private val engine = RustEngine()
    private lateinit var result: TextView
    private var latestReport: String? = null

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
        layout.addView(Button(this).apply {
            text = "Поделиться отчётом"
            minHeight = 48.dp
            setOnClickListener { shareReport() }
        })
        layout.addView(Button(this).apply {
            text = "Очистить историю сеанса"
            minHeight = 48.dp
            setOnClickListener { confirmReset() }
        })
        setContentView(ScrollView(this).apply {
            fillViewport = true
            addView(layout)
        })
        sample()
    }

    private val Int.dp: Int get() =
        (this * resources.displayMetrics.density + 0.5f).toInt()

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Очистить историю Rust?")
            .setMessage(
                "Все измерения текущего сеанса в оперативной памяти будут потеряны. " +
                    "Восстановить их нельзя. Настройки телефона не изменятся."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Очистить") { _, _ ->
                engine.resetSession().fold(
                    onSuccess = { discarded ->
                        latestReport = null
                        result.text = "История очищена: $discarded измерений удалено.\n" +
                            "Чтобы начать новый сеанс, нажмите «Обновить измерение».\n" +
                            "Системные параметры не изменены."
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
        val percent = if (level in 0..scale && scale > 0) {
            (level * 100f / scale).toInt()
        } else {
            -1
        }
        val mode = (
            getSystemService(DISPLAY_SERVICE) as? DisplayManager
        )?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
        val power = AndroidPowerSource.read(this, battery)
        val rust = engine.record(percent, temp, SystemClock.elapsedRealtime())
        val backend = rust.fold(
            onSuccess = { state ->
                if (state.count < 2) {
                    "Первое измерение сохранено"
                } else {
                    String.format(
                        Locale.getDefault(),
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
                            Locale.getDefault(),
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
        output.append("Длительность истории: ").append(duration).append("\n\n")
        output.append("Данные по запросу. Источник тока — Android, знак зависит от прошивки.\n")
        output.append("Нет управления Bypass, частотой сенсора или системными режимами.")
        val snapshot = output.toString()
        result.text = snapshot
        latestReport = if (rust.isSuccess) {
            "Legion Control · локальный отчёт\n" +
                DateFormat.getDateTimeInstance().format(Date()) + "\n\n" +
                snapshot
        } else null
    }
}
