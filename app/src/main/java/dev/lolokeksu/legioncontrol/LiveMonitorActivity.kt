package dev.lolokeksu.legioncontrol

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider

/** Explicit, foreground-only live sampling. No extra permissions or system writes. */
class LiveMonitorActivity : FragmentActivity() {
    private lateinit var model: LiveMonitorViewModel
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var comparison: TextView
    private lateinit var chartSummary: TextView
    private lateinit var chart: MonitorChartView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var intervalButton: Button
    private lateinit var metricButton: Button
    private lateinit var pinButton: Button
    private lateinit var clearButton: Button
    private lateinit var exportButton: Button
    private var metric = MonitorMetric.TEMPERATURE
    private val Int.dp get() = (this * resources.displayMetrics.density + .5f).toInt()
    private val document = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && ::model.isInitialized) model.export(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[LiveMonitorViewModel::class.java]
        metric = savedInstanceState?.getString("monitor_metric")?.let {
            runCatching { MonitorMetric.valueOf(it) }.getOrNull()
        } ?: MonitorMetric.TEMPERATURE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(18.dp, 18.dp, 18.dp, 24.dp)
        }
        fun label(value: String, size: Float = 14f, color: Int = Color.WHITE): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setLineSpacing(3f, 1f)
                setPadding(0, 5.dp, 0, 10.dp)
                root.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
        fun action(title: String, click: () -> Unit): Button = Button(this).apply {
            text = title
            LegionUiSupport.styleAction(this)
            setOnClickListener { click() }
            root.addView(this)
        }
        label("LIVE MONITOR", 22f).apply {
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAccessibilityHeading = true
        }
        label("Живые показания устройства. Замеры выполняются только на открытом экране; частота дисплея не является FPS игры.",
            13f, LegionUiSupport.MUTED)
        status = label("Мониторинг не запущен.", 14f, LegionUiSupport.ACCENT)
        intervalButton = action("Интервал измерений") {
            val intervals = MonitorPolicy.intervals
            AlertDialog.Builder(this).setTitle("Интервал измерений")
                .setSingleChoiceItems(intervals.map { "${it / 1000} с" }.toTypedArray(),
                    intervals.indexOf(model.state.value?.intervalMs ?: 5000L)) { dialog, index ->
                    model.interval(intervals[index])
                    dialog.dismiss()
                }.setNegativeButton("Отмена", null).show()
        }
        startButton = action("НАЧАТЬ МОНИТОРИНГ") {
            if (model.state.value?.points?.isNotEmpty() == true) {
                AlertDialog.Builder(this).setTitle("Начать новый мониторинг?")
                    .setMessage("Новые измерения заменят текущие замеры в памяти. Сначала экспортируйте CSV, если хотите сохранить их. Контрольная точка останется.")
                    .setPositiveButton("Начать") { _, _ -> model.start() }
                    .setNegativeButton("Отмена", null).show()
            } else model.start()
        }
        stopButton = action("ОСТАНОВИТЬ МОНИТОРИНГ") { model.stop() }

        root.addView(LegionUiSupport.sectionLabel(this, "ТЕКУЩИЕ ПОКАЗАНИЯ"))
        summary = label("Показаний пока нет.")
        root.addView(LegionUiSupport.sectionLabel(this, "ГРАФИК"))
        metricButton = action("Выбрать показатель графика") {
            val options = MonitorMetric.entries
            AlertDialog.Builder(this).setTitle("Показатель графика")
                .setSingleChoiceItems(options.map { it.label }.toTypedArray(),
                    options.indexOf(metric)) { dialog, index ->
                    metric = options[index]
                    model.state.value?.let(::render)
                    dialog.dismiss()
                }.setNegativeButton("Отмена", null).show()
        }
        chartSummary = label("Нет измерений для графика.", 13f, LegionUiSupport.MUTED)
        chart = MonitorChartView(this)
        root.addView(chart, LinearLayout.LayoutParams(-1, 172.dp).apply { bottomMargin = 12.dp })
        root.addView(LegionUiSupport.sectionLabel(this, "СРАВНЕНИЕ С КОНТРОЛЬНОЙ ТОЧКОЙ"))
        pinButton = action("ЗАФИКСИРОВАТЬ КОНТРОЛЬНУЮ ТОЧКУ") { model.pinBaseline() }
        comparison = label("Контрольная точка не выбрана.")
        clearButton = action("Сбросить контрольную точку") { model.clearBaseline() }
        root.addView(LegionUiSupport.sectionLabel(this, "ЭКСПОРТ"))
        exportButton = action("СОХРАНИТЬ МОНИТОРИНГ CSV") {
            document.launch("legion-monitor-${System.currentTimeMillis()}.csv")
        }
        label("До 120 замеров в памяти. Для сохранения данных остановите мониторинг и экспортируйте CSV. Данные не отправляются в сеть и не добавляются в SQLite.",
            13f, LegionUiSupport.MUTED)
        LegionUiSupport.setModuleActivityContent(this, ScrollView(this).apply {
            addView(root)
        }, "ЖИВОЙ МОНИТОР УСТРОЙСТВА")
        model.state.observe(this, ::render)
        FeatureReveal.fromActivity(this, root, FeatureModule.MONITOR)
    }

    private fun format(value: Double?, unit: String): String =
        value?.let { "${MonitorPolicy.fmt(it)} $unit" } ?: "н/д"

    private fun render(state: MonitorState) {
        status.text = state.message + "\nИнтервал: ${state.intervalMs / 1000} с"
        intervalButton.isEnabled = !state.running
        // Keep the stable action anchor for the finder.
        intervalButton.text = "Интервал измерений"
        intervalButton.contentDescription = "Интервал измерений: ${state.intervalMs / 1000} секунд"
        startButton.isEnabled = !state.running
        stopButton.isEnabled = state.running
        pinButton.isEnabled = state.points.isNotEmpty()
        clearButton.isEnabled = state.baseline != null
        exportButton.isEnabled = !state.running && state.points.isNotEmpty()
        val last = state.points.lastOrNull()
        summary.text = if (last == null) "Показаний пока нет." else buildString {
            append("Замеров: ").append(state.points.size).append(" · время: ")
                .append(last.elapsedMs / 1000).append(" с\n")
            append("Температура батареи: ").append(format(MonitorMetric.TEMPERATURE.value(last), "°C")).append('\n')
            append("Доступная RAM: ").append(format(MonitorMetric.RAM.value(last), "ГиБ"))
                .append(" / ").append(format(last.totalRamBytes?.div(1073741824.0), "ГиБ")).append('\n')
            append("Заряд: ").append(last.battery?.let { "$it%" } ?: "н/д").append('\n')
            append("Частота дисплея: ").append(format(MonitorMetric.DISPLAY.value(last), "Гц")).append('\n')
            append("Ток батареи: ").append(format(last.currentMicroA?.div(1000.0), "мА")).append('\n')
            append("Тепловой статус Android: ").append(last.thermalStatus?.toString() ?: "н/д").append('\n')
            append("Thermal headroom: ").append(format(last.thermalHeadroom?.toDouble(), ""))
                .append(" (").append(last.headroomState.name).append(")\n")
            append("Энергосбережение: ").append(last.powerSaving?.let { if (it) "включено" else "выключено" } ?: "н/д").append('\n')
            append("Подключение: ").append(last.network ?: "н/д").append(" · проверка Android: ")
                .append(last.validatedNetwork?.let { if (it) "да" else "нет" } ?: "н/д")
            if (last.telemetryError) append("\nЧасть показаний недоступна из-за ошибки Android.")
        }
        val values = state.points.mapNotNull(metric::value)
        chartSummary.text = if (values.isEmpty()) "${metric.label} · нет доступных показаний" else
            "${metric.label} · минимум ${format(values.minOrNull(), metric.unit)} · максимум ${format(values.maxOrNull(), metric.unit)} · доступно ${values.size}/${state.points.size}"
        chart.update(state.points, metric)
        comparison.text = if (state.baseline == null) "Контрольная точка не выбрана." else buildString {
            append("Изменение относительно сохранённого замера:\n")
            for (m in MonitorMetric.entries) {
                val delta = MonitorPolicy.delta(m, state.baseline, last)
                append(m.label).append(": ")
                    .append(if (delta != null && delta > 0) "+" else "")
                    .append(format(delta, m.unit)).append('\n')
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.setVisible(true)
    }
    override fun onStop() {
        model.setVisible(false)
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("monitor_metric", metric.name)
        super.onSaveInstanceState(outState)
    }
}
