package dev.lolokeksu.legioncontrol

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity

/** Compatibility entry point; all monitoring now lives in the diagnostics center. */
class LiveMonitorActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(ModuleNavigation.deviceIntent(this, DiagnosticsPage.MONITOR, intent.extras))
        finish()
    }
}

/** Existing foreground-only monitor UI, embedded without a second sampler or Activity. */
internal class LiveMonitorPanel(
    private val activity: ComponentActivity,
    private val model: LiveMonitorViewModel,
    savedInstanceState: Bundle?
) {
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
    private val Int.dp get() = (this * activity.resources.displayMetrics.density + .5f).toInt()
    private val document = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        model.finishExport(uri)
    }
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        metric = savedInstanceState?.getString("monitor_metric")?.let {
            runCatching { MonitorMetric.valueOf(it) }.getOrNull()
        } ?: MonitorMetric.TEMPERATURE

        fun label(value: String, size: Float = 14f, color: Int = Color.WHITE): TextView =
            TextView(activity).apply {
                text = value
                textSize = size
                setTextColor(color)
                setLineSpacing(3f, 1f)
                setPadding(0, 5.dp, 0, 10.dp)
                root.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
        fun action(title: String, featureId: String? = null, click: () -> Unit): Button = Button(activity).apply {
            text = title
            if(featureId != null) FeatureReveal.bind(this, featureId)
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
            LegionDialogs.builder(activity).setTitle("Интервал измерений")
                .setSingleChoiceItems(intervals.map { "${it / 1000} с" }.toTypedArray(),
                    intervals.indexOf(model.state.value?.intervalMs ?: 5000L)) { dialog, index ->
                    model.interval(intervals[index])
                    dialog.dismiss()
                }.setNegativeButton("Отмена", null).show()
        }
        startButton = action("НАЧАТЬ МОНИТОРИНГ", "monitor.start") {
            if (model.state.value?.points?.isNotEmpty() == true) {
                LegionDialogs.builder(activity).setTitle("Начать новый мониторинг?")
                    .setMessage("Новые измерения заменят текущие замеры в памяти. Сначала экспортируйте CSV, если хотите сохранить их. Контрольная точка останется.")
                    .setPositiveButton("Начать") { _, _ -> model.start() }
                    .setNegativeButton("Отмена", null).show()
            } else model.start()
        }
        stopButton = action("ОСТАНОВИТЬ МОНИТОРИНГ") { model.stop() }

        root.addView(LegionUiSupport.sectionLabel(activity, "ТЕКУЩИЕ ПОКАЗАНИЯ"))
        summary = label("Показаний пока нет.").apply { LegionUiSupport.reportSurface(this) }
        root.addView(LegionUiSupport.sectionLabel(activity, "ГРАФИК"))
        metricButton = action("Выбрать показатель графика", "monitor.chart") {
            val options = MonitorMetric.entries
            LegionDialogs.builder(activity).setTitle("Показатель графика")
                .setSingleChoiceItems(options.map { it.label }.toTypedArray(),
                    options.indexOf(metric)) { dialog, index ->
                    metric = options[index]
                    model.state.value?.let(::render)
                    dialog.dismiss()
                }.setNegativeButton("Отмена", null).show()
        }
        chartSummary = label("Нет измерений для графика.", 13f, LegionUiSupport.MUTED)
        chart = MonitorChartView(activity)
        root.addView(chart, LinearLayout.LayoutParams(-1, 172.dp).apply { bottomMargin = 12.dp })
        root.addView(LegionUiSupport.sectionLabel(activity, "СРАВНЕНИЕ С КОНТРОЛЬНОЙ ТОЧКОЙ"))
        pinButton = action("ЗАФИКСИРОВАТЬ КОНТРОЛЬНУЮ ТОЧКУ", "monitor.baseline") { model.pinBaseline() }
        comparison = label("Контрольная точка не выбрана.")
        clearButton = action("Сбросить контрольную точку") { model.clearBaseline() }
        root.addView(LegionUiSupport.sectionLabel(activity, "ЭКСПОРТ"))
        exportButton = action("СОХРАНИТЬ МОНИТОРИНГ CSV", "monitor.csv") {
            if (!model.beginExport()) return@action
            model.state.value?.let(::render)
            runCatching { document.launch("legion-monitor-${System.currentTimeMillis()}.csv") }
                .onFailure {
                    model.finishExport(null)
                    status.text = "Не удалось открыть выбор документа."
                }
        }
        label("До 120 замеров в памяти. Для сохранения данных остановите мониторинг и экспортируйте CSV. Данные не отправляются в сеть и не добавляются в SQLite.",
            13f, LegionUiSupport.MUTED)
        model.state.observe(activity, ::render)
    }

    fun revealFeature() = FeatureReveal.fromActivity(activity, root, FeatureModule.MONITOR)

    private fun format(value: Double?, unit: String): String =
        value?.let { "${MonitorPolicy.fmt(it)} $unit" } ?: "н/д"

    private fun render(state: MonitorState) {
        status.text = state.message + "\nИнтервал: ${state.intervalMs / 1000} с"
        LegionUiSupport.notice(status, if(state.exporting) LegionUiSupport.Notice.LOADING else LegionUiSupport.Notice.INFO)
        intervalButton.isEnabled = !state.running
        // Keep the stable action anchor for the finder.
        intervalButton.text = "Интервал измерений"
        intervalButton.contentDescription = "Интервал измерений: ${state.intervalMs / 1000} секунд"
        startButton.isEnabled = !state.running && !state.exporting && !model.hasPendingDocument
        stopButton.isEnabled = state.running
        pinButton.isEnabled = state.points.isNotEmpty()
        clearButton.isEnabled = state.baseline != null
        exportButton.isEnabled = !state.running && !state.exporting && !model.hasPendingDocument && state.points.isNotEmpty()
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
            "${metric.label} · минимум ${format(values.minOrNull(), metric.unit)} · максимум ${format(values.maxOrNull(), metric.unit)} · последнее ${format(state.points.lastOrNull()?.let(metric::value), metric.unit)} · доступно ${values.size}/${state.points.size}"
        chart.update(state.points, metric)
        comparison.text = if (state.baseline == null) "Контрольная точка не выбрана." else buildString {
            append("Изменение относительно сохранённого замера:\n")
            for (m in MonitorMetric.entries) {
                val delta = MonitorPolicy.delta(m, state.baseline, last)
                append(m.label).append(": ")
                    .append(if (delta != null && delta > 0) "+" else "")
                    .append(format(delta, m.deltaUnit)).append('\n')
            }
        }
    }

    fun setVisible(value: Boolean) = model.setVisible(value)

    fun saveState(outState: Bundle) {
        outState.putString("monitor_metric", metric.name)
    }
}
