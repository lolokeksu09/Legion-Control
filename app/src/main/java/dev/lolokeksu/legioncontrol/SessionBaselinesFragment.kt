package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.AlertDialog
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

/**
 * Real offline median-of-previous-session baseline, with explicit data-quality exclusions.
 */
class SessionBaselinesFragment:Fragment() {
    private lateinit var vm:SessionBaselinesViewModel
    private lateinit var output:TextView
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()
    companion object { private const val EXPORT_CSV=3131 }

    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,
        savedInstanceState:Bundle?):View {
        val root=LinearLayout(requireContext()).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(18.dp,24.dp,18.dp,38.dp)
        }
        root.addView(TextView(requireContext()).apply {
            text="LEGION · SESSION BASELINES"
            textSize=22f
            setTypeface(null,Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(requireContext()).apply {
            text="Сравнение условий устройства в записях одной игры. " +
                "Последняя сессия против медианы минимум трёх предыдущих. " +
                "Разрывы, незавершённые записи и зарядка исключаются из сравнения."
            textSize=13f
            setTextColor(0xFFB4B4B4.toInt())
            setPadding(0,12.dp,0,14.dp)
        })
        fun button(label:String,click:()->Unit) {
            root.addView(Button(requireContext()).apply {
                text=label
                isAllCaps=false
                minHeight=48.dp
                setOnClickListener { click() }
            })
        }
        button("Выбрать игру из локальной истории") { vm.pickPackages() }
        button("За последние 7 дней") { vm.setDays(7) }
        button("За последние 30 дней") { vm.setDays(30) }
        button("Обновить базовые показатели") { vm.refresh() }
        button("Открыть Session Signals выбранной игры") { openSignals() }
        button("Экспортировать сравнение CSV") { export() }
        button("Поделиться текстовым сравнением") { share() }
        output=TextView(requireContext()).apply {
            text="Нет загруженных данных."
            setTextColor(Color.WHITE)
            textSize=14f
            setTextIsSelectable(true)
            setLineSpacing(4f,1f)
            setPadding(0,18.dp,0,32.dp)
        }
        root.addView(output)
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        vm=ViewModelProvider(this)[SessionBaselinesViewModel::class.java]
        vm.state.observe(viewLifecycleOwner) { state ->
            output.text=state.text
            state.packages?.let {
                vm.consumePackages(state.request)
                pickPackageDialog(it)
            }
        }
        val pkg=savedInstanceState?.getString("session_baselines_package")
            ?: arguments?.getString("smart_package")
        val period=savedInstanceState?.getInt("session_baselines_days",7) ?:7
        vm.useInitialPackage(pkg)
        if(period!=vm.days)vm.setDays(period)
    }

    private fun pickPackageDialog(packages:List<String>) {
        if(packages.isEmpty())return
        AlertDialog.Builder(requireContext()).setTitle("История игр")
            .setItems(packages.toTypedArray()) { _,which ->
                vm.selectPackage(packages[which])
            }.setNegativeButton("Отмена",null).show()
    }

    private fun openSignals() {
        val pkg=vm.selectedPackage
        if(pkg==null) {
            output.text="Сначала выберите игру.";return
        }
        NavHostFragment.findNavController(this).navigate(R.id.sessionSignalsFragment,
            Bundle().apply { putString("smart_package",pkg) })
    }

    private fun export() {
        if(vm.state.value?.result==null) {
            output.text="Сначала выполните анализ.";return
        }
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="text/csv"
            putExtra(Intent.EXTRA_TITLE,"legion-session-baselines.csv")
        }
        runCatching { startActivityForResult(intent,EXPORT_CSV) }.onFailure {
            output.text="Выбор документа недоступен: "+it.javaClass.simpleName
        }
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==EXPORT_CSV && resultCode==Activity.RESULT_OK)
            data?.data?.let { vm.exportCsv(it) }
    }

    private fun share() {
        val data=vm.state.value?.result
        if(data==null) { output.text="Сначала выполните анализ.";return }
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain"
                putExtra(Intent.EXTRA_TEXT,SessionBaselines.render(data))
            },"Поделиться сравнением"))
        }.onFailure { output.text="Меню отправки недоступно." }
    }

    override fun onSaveInstanceState(out:Bundle) {
        if(::vm.isInitialized) {
            vm.selectedPackage?.let { out.putString("session_baselines_package",it) }
            out.putInt("session_baselines_days",vm.days)
        }
        super.onSaveInstanceState(out)
    }
}
