package dev.lolokeksu.legioncontrol

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
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
 * Manual-only hardware diagnostics. No background service, root, Shizuku,
 * protected APIs, app enumeration, hidden system settings or cloud telemetry.
 */
class HardwareLabFragment : Fragment() {
    private lateinit var viewModel: HardwareLabViewModel
    private lateinit var result: TextView
    private var batteryLab: BatteryLabPanel? = null
    private var pendingBatteryCsv:String?=null
    private val batteryCsvRequest=2151


    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 20.dp, 18.dp, 28.dp)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(requireContext()).apply {
            text = "LEGION CONTROL · HARDWARE LAB"
            textSize = 24f
            isAccessibilityHeading = true
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Локальные аппаратные измерения · без Root и Shizuku. " +
                "Проверки запускаются вручную, слушатели работают только при открытом экране."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10.dp, 0, 12.dp)
        })
        result = TextView(requireContext()).apply {
            text = "Выберите тест. Измерения не загружаются на сервер."
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(0, 12.dp, 0, 20.dp)
            setTextIsSelectable(true)
        }
        LegionUiSupport.reportSurface(result)
        root.addView(result)
        batteryLab=BatteryLabPanel(requireContext(),::exportBatteryCsv).also { root.addView(it) }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "СЕТЬ"))
        addButton(root, "1 · Local Route Inspector", "hardware.route") {
            viewModel.run(HardwareProbe.ROUTE)
        }
        addButton(root, "2 · Network Events — 30 секунд", "hardware.events") { toggleNetworkEvents() }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ПАМЯТЬ И СЕНСОРЫ"))
        addButton(root, "3 · Storage Lab — 8 МиБ / SHA-256", "hardware.storage") {
            viewModel.run(HardwareProbe.STORAGE)
        }
        addButton(root, "4 · Motion Stability — 5 секунд", "hardware.motion") { toggleMotion() }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ЭКРАН И КОДЕКИ"))
        addButton(root, "5 · Display Patterns — пиксели и цвета", "hardware.display") {
            LegionDialogs.builder(requireContext())
                .setTitle("Проверка экрана")
                .setMessage(
                    "Показываются сплошные чёрный, белый, красный, зелёный, " +
                        "синий и серый цвета. Нажатие меняет цвет, шестое завершает тест. " +
                        "Кнопка «Назад» закрывает тест в любой момент. " +
                        "Яркость системы не изменяется. Быстрой вспышки нет."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Начать") { _, _ ->
                    ModuleNavigation.open(this,DisplayPatternActivity::class.java)
                }
                .show()
        }
        addButton(root, "6 · Codec Inspector — аппаратные видеодекодеры", "hardware.codec") {
            viewModel.run(HardwareProbe.CODEC)
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ОТЧЁТ И ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА"))
        addButton(root, "COMPATIBILITY · ещё 7 функций") {
            ModuleNavigation.open(this,CompatibilityLabActivity::class.java)
        }
        addButton(root, "Поделиться последним отчётом") { shareLastReport() }
        addButton(root, "Остановить текущую проверку") {
            viewModel.cancelAll()
            report("Проверка остановлена. Можно выбрать следующий инструмент.")
        }
        addButton(root, "К результату") { DiagnosticLabUi.revealResult(result) }
        addButton(root, "Назад") {
            val nav=runCatching { NavHostFragment.findNavController(this) }.getOrNull()
            if(nav != null) nav.navigateUp() else requireActivity().finish()
        }
        return ScrollView(requireContext()).apply {
            isFillViewport=true
            addView(root)
        }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        pendingBatteryCsv=savedInstanceState?.getString("batteryCsv") ?: pendingBatteryCsv
        LegionUiSupport.styleModuleTree(view)
        FeatureReveal.fromFragment(this, view, FeatureModule.HARDWARE)
        viewModel=ViewModelProvider(this)[HardwareLabViewModel::class.java]
        viewModel.report.observe(viewLifecycleOwner) { result.text=it }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun addButton(root: LinearLayout, label: String, featureId: String? = null, action: () -> Unit) {
        root.addView(DiagnosticLabUi.action(requireContext(), label, action).apply {
            if(featureId!=null) FeatureReveal.bind(this,featureId)
        }, DiagnosticLabUi.actionLayout(requireContext()))
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

    private fun exportBatteryCsv(csv:String) {
        pendingBatteryCsv=csv
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="text/csv"
                putExtra(Intent.EXTRA_TITLE,"legion-battery-lab.csv")
            },batteryCsvRequest)
        }.onFailure { pendingBatteryCsv=null;report("Не удалось открыть документ.") }
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=batteryCsvRequest)return
        val csv=pendingBatteryCsv;pendingBatteryCsv=null
        val uri=data?.data
        if(resultCode==android.app.Activity.RESULT_OK && uri!=null) {
            val panel=batteryLab
            if(csv!=null && panel!=null) panel.writeCsv(uri,csv)
            else report("Исходные замеры недоступны. Повторите экспорт CSV.")
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        pendingBatteryCsv?.let{outState.putString("batteryCsv",it)}
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart()
        viewModel.setVisible(true)
        batteryLab?.setVisible(true)
    }

    override fun onDestroyView() {
        batteryLab?.close()
        batteryLab=null
        super.onDestroyView()
    }

    override fun onStop() {
        batteryLab?.setVisible(false)
        viewModel.setVisible(false)
        viewModel.cancelAll()
        super.onStop()
    }
}
