package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import java.text.DateFormat
import java.util.Date

/** Post-session incident intervals reconstructed from user-started local recordings. */
class SessionIncidentsFragment : Fragment() {
    private lateinit var vm: SessionIncidentsViewModel
    private lateinit var output: TextView
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object { private const val EXPORT_CSV = 3232 }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(18.dp, 24.dp, 18.dp, 38.dp)
        }
        root.addView(TextView(requireContext()).apply {
            text = "LEGION · SESSION INCIDENTS"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Интервалы состояния устройства из записанной сессии: Thermal, " +
                "температура батареи, энергосбережение, сеть и частота экрана. " +
                "Только подтверждённые соседними замерами секунды. " +
                "Метки рядом с эпизодом не доказывают причину лагов."
            textSize = 13f
            setTextColor(0xFFB4B4B4.toInt())
            setPadding(0, 12.dp, 0, 16.dp)
        })
        fun button(label: String, featureId: String? = null, onClick: () -> Unit) {
            root.addView(Button(requireContext()).apply {
                if(featureId!=null) FeatureReveal.bind(this,featureId)
                text = label
                isAllCaps = false
                minHeight = 48.dp
                setOnClickListener { onClick() }
            })
        }
        button("Выбрать записанную сессию") { vm.pickSessions() }
        button("Обновить эпизоды") { vm.refresh() }
        button("Фильтр категорий", "incidents.filter") { pickFilter() }
        button("Сохранить эпизоды CSV", "incidents.csv") { exportCsv() }
        button("Поделиться результатом") { share() }
        button("Открыть Session Signals") {
            val report = vm.state.value?.report
            val args = Bundle().apply {
                report?.session?.packageName?.let { putString("smart_package", it) }
            }
            NavHostFragment.findNavController(this).navigate(R.id.sessionSignalsFragment, args)
        }
        output = TextView(requireContext()).apply {
            text = "Ожидание локальной истории."
            textSize = 14f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            setLineSpacing(4f, 1f)
            setPadding(0, 18.dp, 0, 30.dp)
        }
        LegionUiSupport.reportSurface(output)
        root.addView(output)
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LegionUiSupport.styleModuleTree(view)
        FeatureReveal.fromFragment(this, view, FeatureModule.INCIDENTS)
        vm = ViewModelProvider(this)[SessionIncidentsViewModel::class.java]
        vm.state.observe(viewLifecycleOwner) { state ->
            output.text = state.text
            state.choices?.let {
                vm.consumeChoices(state.request)
                showSessions(it)
            }
        }
        vm.setPackageFilter(arguments?.getString("smart_package"))
        savedInstanceState?.getString("incidents_kind")?.let { saved ->
            SessionIncidents.Kind.entries.firstOrNull { it.name == saved }?.let(vm::selectKind)
        }
        val id = savedInstanceState?.getString("incidents_id")
            ?: arguments?.getString("session_id")
        if (!id.isNullOrBlank()) vm.select(id) else vm.refresh()
    }

    private fun showSessions(rows: List<GameSessionHeader>) {
        if (rows.isEmpty()) return
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val labels = rows.map {
            it.appLabel + " · " + fmt.format(Date(it.startedAtMs)) + "\n" +
                (it.endReason ?: "Запись не завершена")
        }.toTypedArray()
        LegionDialogs.builder(requireContext()).setTitle("Сессии · " + rows.size)
            .setItems(labels) { _, position -> vm.select(rows[position].id) }
            .setNegativeButton("Отмена", null).show()
    }

    private fun pickFilter() {
        val kinds = SessionIncidents.Kind.entries
        val choices = (listOf("Все категории") + kinds.map { it.label }).toTypedArray()
        LegionDialogs.builder(requireContext()).setTitle("Выберите тип")
            .setItems(choices) { _, position ->
                vm.selectKind(if (position == 0) null else kinds[position - 1])
            }.setNegativeButton("Отмена", null).show()
    }

    private fun exportCsv() {
        if (vm.state.value?.report == null) {
            output.text = "Сначала откройте анализ."; return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "legion-session-incidents.csv")
        }
        runCatching { startActivityForResult(intent, EXPORT_CSV) }.onFailure {
            output.text = "Выбор документа недоступен: " + it.javaClass.simpleName
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_CSV && resultCode == Activity.RESULT_OK)
            data?.data?.let { vm.exportCsv(it) }
    }

    private fun share() {
        val result = vm.state.value?.report
        if (result == null) { output.text = "Сначала откройте анализ."; return }
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, SessionIncidents.render(result, vm.selectedKind))
            }, "Поделиться эпизодами"))
        }.onFailure { output.text = "Меню отправки недоступно." }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::vm.isInitialized) {
            vm.selectedId?.let { outState.putString("incidents_id", it) }
            vm.selectedKind?.let { outState.putString("incidents_kind", it.name) }
        }
        super.onSaveInstanceState(outState)
    }
}
