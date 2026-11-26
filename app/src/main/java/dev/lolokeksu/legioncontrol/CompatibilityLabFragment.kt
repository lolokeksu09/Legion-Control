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
 * Opt-in compatibility analysis. Each read is user-triggered. Device info and
 * report text remain local unless the user invokes Android's share chooser.
 */
class CompatibilityLabFragment : Fragment() {
    private lateinit var viewModel: CompatibilityLabViewModel
    private lateinit var output: TextView

    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 20.dp, 18.dp, 28.dp)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(requireContext()).apply {
            text = "LEGION CONTROL · COMPATIBILITY"
            setTextColor(Color.WHITE)
            setTypeface(null,Typeface.BOLD)
            textSize = 24f
            isAccessibilityHeading = true
        })
        root.addView(TextView(requireContext()).apply {
            text = "Режимы дисплея, аудио и поддержка функций Android. " +
                "Никаких изменений системных режимов, Root, Shizuku или перехвата чужих игр."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12.dp, 0, 18.dp)
        })
        output = TextView(requireContext()).apply {
            text = "Выбери инструмент. Последний завершённый отчёт можно отправить вручную."
            textSize = 14f
            setTextColor(Color.WHITE)
            setLineSpacing(5f,1f)
            setPadding(0, 14.dp, 0, 18.dp)
            setTextIsSelectable(true)
        }
        LegionUiSupport.reportSurface(output)
        root.addView(output)
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ЭКРАН И ЗВУК"))
        addButton(root, "1 · Display Modes & HDR", "compat.hdr") {
            viewModel.run(CompatibilityRead.DISPLAY,requireActivity().display)
        }
        addButton(root, "2 · Audio Output Inspector", "compat.audio") {
            viewModel.run(CompatibilityRead.AUDIO,requireActivity().display)
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "КОНТРОЛЛЕРЫ"))
        addButton(root, "3 · Gamepad Inventory", "compat.controllers") {
            viewModel.run(CompatibilityRead.GAMEPADS,requireActivity().display)
        }
        addButton(root, "4 · Gamepad Live · кнопки и стики") {
            ModuleNavigation.open(this,GamepadLiveActivity::class.java)
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "СОВМЕСТИМОСТЬ И ПРОИЗВОДИТЕЛЬНОСТЬ"))
        addButton(root, "5 · DRM / Widevine Compatibility", "compat.drm") {
            viewModel.run(CompatibilityRead.DRM,requireActivity().display)
        }
        addButton(root, "6 · Gaming Readiness · снимок системы") {
            viewModel.run(CompatibilityRead.READINESS,requireActivity().display)
        }
        addButton(root, "7 · Quick Compute Probe · локальный CPU-тест", "compat.cpu") {
            viewModel.run(CompatibilityRead.COMPUTE,requireActivity().display)
        }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ОТЧЁТ"))
        addButton(root, "Полный отчёт о совместимости", "compat.report") { runFullReport() }
        addButton(root, "Поделиться последним завершённым отчётом") { shareLastReport() }
        addButton(root, "Остановить текущую проверку") {
            viewModel.cancel()
            output.text = "Проверка остановлена. Можно выбрать следующий инструмент."
        }
        addButton(root, "К результату") { DiagnosticLabUi.revealResult(output) }
        addButton(root, "Назад") {
            val controller=runCatching { NavHostFragment.findNavController(this) }.getOrNull()
            if(controller!=null) controller.navigateUp() else requireActivity().finish()
        }
        return ScrollView(requireContext()).apply {
            isFillViewport=true
            addView(root)
        }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        LegionUiSupport.styleModuleTree(view)
        FeatureReveal.fromFragment(this, view, FeatureModule.COMPAT)
        viewModel=ViewModelProvider(this)[CompatibilityLabViewModel::class.java]
        viewModel.report.observe(viewLifecycleOwner) { output.text=it }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private fun addButton(root: LinearLayout, title: String, featureId: String? = null, action: () -> Unit) {
        root.addView(DiagnosticLabUi.action(requireContext(), title, action).apply {
            if(featureId!=null) FeatureReveal.bind(this,featureId)
        }, DiagnosticLabUi.actionLayout(requireContext()))
    }

    private fun runFullReport() { viewModel.run(CompatibilityRead.ALL,requireActivity().display) }

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
