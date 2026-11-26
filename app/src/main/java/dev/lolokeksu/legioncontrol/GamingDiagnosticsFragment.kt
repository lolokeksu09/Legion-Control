package dev.lolokeksu.legioncontrol

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.lifecycle.ViewModelProvider
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
class GamingDiagnosticsFragment : Fragment() {
    private lateinit var viewModel: GamingDiagnosticsViewModel
    private lateinit var resultView: TextView
    private lateinit var packageInput: EditText
    private var touchDialog: Dialog? = null
    private var visible = false

    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 32.dp, 18.dp, 28.dp)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(requireContext()).apply {
            text = "LEGION CONTROL · GAMING LAB"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Локальная диагностика · запуск только по нажатию. " +
                "Ни один модуль не даёт Root, Bypass, FPS чужой игры или частоту CPU/GPU."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10.dp, 0, 16.dp)
        })
        resultView = TextView(requireContext()).apply {
            text = "Выбери диагностический модуль. Результаты остаются на устройстве."
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setPadding(4.dp, 12.dp, 4.dp, 20.dp)
            setTextIsSelectable(true)
        }
        LegionUiSupport.reportSurface(resultView)
        root.addView(resultView)

        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ПРОВЕРКИ УСТРОЙСТВА"))
        addButton(root, "Сводка устройства") {
            viewModel.run(GamingProbe.OVERVIEW, refreshHzDisplay = requireActivity().display)
        }
        addButton(root, "Остановить текущую проверку") {
            viewModel.cancelAll()
            report("Проверка остановлена. Можно выбрать следующий инструмент.")
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "СЕТЬ И СОЕДИНЕНИЕ"))
        addButton(root, "1 · Network Analyzer — TCP/DNS", "gaming.network") {
            viewModel.run(GamingProbe.NETWORK)
        }
        addButton(root, "6 · Wi-Fi / LTE Inspector", "gaming.wifi") {
            viewModel.run(GamingProbe.WIFI)
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "БАТАРЕЯ И НАГРЕВ"))
        addButton(root, "2 · Battery Intelligence", "gaming.battery") {
            viewModel.run(GamingProbe.BATTERY)
        }
        addButton(root, "3 · Thermal Guard — включить/выключить события", "gaming.thermal") {
            toggleThermalGuard()
        }
        addButton(root, "8 · Charging Analyzer — сравнить с прошлым замером", "gaming.charging") {
            viewModel.run(GamingProbe.CHARGING)
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ЭКРАН И УПРАВЛЕНИЕ"))
        addButton(root, "4 · Touch Analyzer — мультитач и края", "gaming.touch") {
            openTouchTester()
        }
        addButton(root, "5 · Frame Pacing — 5 секунд", "gaming.frames") {
            startFramePacing()
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ИГРА И АКТИВНОСТЬ"))
        packageInput = EditText(requireContext()).apply {
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
            if (!GameUsageDiagnostics.hasUsageAccess(requireContext())) {
                report("Доступ к статистике использования ещё не предоставлен. " +
                    "Нажми кнопку настройки ниже и разреши Legion Control.")
            } else viewModel.run(GamingProbe.USAGE,selectedPackage)
        }
        addButton(root, "Разрешить статистику использования (настройки Android)") {
            runCatching {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }.onFailure {
                report("Не удалось открыть специальные настройки доступа: " +
                    it.javaClass.simpleName)
            }
        }
        addButton(root, "9 · Session Comparison — последние два сеанса") {
            viewModel.run(GamingProbe.SESSION)
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "СИСТЕМА И ДОПОЛНИТЕЛЬНЫЕ МОДУЛИ"))
        addButton(root, "10 · System Health", "gaming.health") {
            viewModel.run(GamingProbe.SYSTEM,refreshHzDisplay=requireActivity().display)
        }
        addButton(root, "HARDWARE LAB · ещё 6 диагностик") {
            ModuleNavigation.open(this,HardwareLabActivity::class.java)
        }
        addButton(root, "Вернуться к Legion Control") {
            val nav=runCatching { NavHostFragment.findNavController(this) }.getOrNull()
            if(nav!=null) nav.navigateUp() else requireActivity().finish()
        }
        return ScrollView(requireContext()).apply {
            isFillViewport=true
            addView(root)
        }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        LegionUiSupport.styleModuleTree(view)
        FeatureReveal.fromFragment(this, view, FeatureModule.GAMING)
        viewModel=ViewModelProvider(this)[GamingDiagnosticsViewModel::class.java]
        viewModel.report.observe(viewLifecycleOwner) { report(it) }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun addButton(root: LinearLayout, title: String, featureId: String? = null, action: () -> Unit) {
        root.addView(Button(requireContext()).apply {
            if(featureId!=null) FeatureReveal.bind(this,featureId)
            text = title
            minHeight = 48.dp
            isAllCaps = false
            setOnClickListener { action() }
        })
    }

    private fun report(message: String) {
        if (view != null && ::resultView.isInitialized) resultView.text = message
    }

    private fun toggleThermalGuard() = viewModel.toggleThermalGuard()

    private fun startFramePacing() {
        viewModel.startFramePacing(requireActivity().display?.mode?.refreshRate ?: 0f)
    }

    private fun openTouchTester() {
        if (!visible) return
        if (touchDialog != null) return
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        var touchView: TouchDiagnosticsView? = null
        val overlay = FrameLayout(requireContext()).apply { setBackgroundColor(Color.BLACK) }
        val status = TextView(requireContext()).apply {
            text = "Проведи пальцами по экрану, проверяй края и мультитач."
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(12.dp, 6.dp, 12.dp, 6.dp)
        }
        val canvas = TouchDiagnosticsView(requireContext()) { text ->
            status.text = text
        }
        touchView = canvas
        overlay.addView(canvas, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val controls = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            setBackgroundColor(0xE5000000.toInt())
            addView(status)
            addView(Button(requireContext()).apply {
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
        viewModel.setVisible(true)
    }

    override fun onStop() {
        visible = false
        viewModel.setVisible(false)
        viewModel.cancelAll()
        touchDialog?.dismiss()
        touchDialog = null
        super.onStop()
    }
}
