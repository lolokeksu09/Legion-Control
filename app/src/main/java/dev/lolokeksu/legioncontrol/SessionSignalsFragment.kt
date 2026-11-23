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
import java.text.DateFormat
import java.util.Date

/** Session Signals: explainable offline timeline from saved user-triggered recordings. */
class SessionSignalsFragment:Fragment() {
    private lateinit var vm:SessionSignalsViewModel
    private lateinit var report:TextView
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()
    private val EXPORT_CSV=3031

    override fun onCreateView(inflater:LayoutInflater,parent:ViewGroup?,saved:Bundle?):View {
        val root=LinearLayout(requireContext()).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(18.dp,26.dp,18.dp,38.dp)
        }
        root.addView(TextView(requireContext()).apply {
            text="LEGION · SESSION SIGNALS"
            textSize=22f
            setTextColor(Color.WHITE)
            setTypeface(null,Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text="Хронология сохранённых измерений, температурных и сетевых переходов, " +
                "пропусков записи и ваших ручных меток. Только локальные данные. " +
                "Нет FPS, Root/Shizuku или автоматического тестирования сети."
            textSize=13f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0,10.dp,0,16.dp)
        })
        fun action(text:String,click:()->Unit) {
            root.addView(Button(requireContext()).apply {
                this.text=text
                isAllCaps=false
                minHeight=48.dp
                setOnClickListener{click()}
            })
        }
        action("Выбрать записанную сессию") { vm.pickSessions() }
        action("Обновить хронологию") { vm.refresh() }
        action("Сохранить события выбранной сессии CSV") { saveCsv() }
        action("Поделиться текстовым анализом") { share() }
        action("Session Incidents · длительность состояний") {
            val args = Bundle().apply {
                vm.state.value?.report?.session?.let {
                    putString("smart_package", it.packageName)
                    putString("session_id", it.id)
                }
            }
            NavHostFragment.findNavController(this).navigate(R.id.sessionIncidentsFragment, args)
        }
        action("Session Baselines · история этой игры") {
            val args=Bundle().apply {
                vm.state.value?.report?.session?.packageName?.let {
                    putString("smart_package",it)
                }
            }
            NavHostFragment.findNavController(this).navigate(R.id.sessionBaselinesFragment,args)
        }
        report=TextView(requireContext()).apply {
            setTextColor(Color.WHITE)
            textSize=14f
            setTextIsSelectable(true)
            setLineSpacing(4f,1f)
            setPadding(0,18.dp,0,28.dp)
            text="Чтение не начинается до открытия этого экрана."
        }
        root.addView(report)
        return ScrollView(requireContext()).apply{addView(root)}
    }

    override fun onViewCreated(view:View,saved:Bundle?) {
        super.onViewCreated(view,saved)
        LegionUiSupport.styleModuleTree(view)
        vm=ViewModelProvider(this)[SessionSignalsViewModel::class.java]
        vm.state.observe(viewLifecycleOwner) { state ->
            report.text=state.message
            state.choices?.let { found ->
                vm.consumeChoices(state.request)
                showSessions(found)
            }
        }
        vm.setPackageFilter(arguments?.getString("smart_package"))
        if(saved?.getString("session_signals_id") != null)
            vm.select(saved.getString("session_signals_id")!!)
        else vm.refresh()
    }

    private fun showSessions(sessions:List<GameSessionHeader>) {
        if(sessions.isEmpty())return
        val fmt=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
        val labels=sessions.map{
            it.appLabel+" · "+fmt.format(Date(it.startedAtMs))+"\n"+
                (it.endReason?:"Идёт запись / нет отметки завершения")
        }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle("Сессии · "+sessions.size)
            .setItems(labels){_,idx -> vm.select(sessions[idx].id)}
            .setNegativeButton("Отмена",null).show()
    }

    private fun saveCsv() {
        if(vm.selectedId==null) {
            report.text="Выберите сохранённую сессию.";return
        }
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="text/csv"
            putExtra(Intent.EXTRA_TITLE,"legion-session-signals.csv")
        }
        runCatching{startActivityForResult(intent,EXPORT_CSV)}.onFailure {
            report.text="Системный выбор файла недоступен: "+it.javaClass.simpleName
        }
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==EXPORT_CSV && resultCode==Activity.RESULT_OK)
            data?.data?.let { vm.export(it) }
    }

    private fun share() {
        val content=vm.state.value?.report?.let { SessionSignals.render(it) }
        if(content==null) { report.text="Сначала откройте анализ сессии.";return }
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain"
                putExtra(Intent.EXTRA_TEXT,content)
            },"Поделиться хронологией"))
        }.onFailure { report.text="Невозможно открыть меню отправки." }
    }

    override fun onSaveInstanceState(out:Bundle) {
        if(::vm.isInitialized)vm.selectedId?.let { out.putString("session_signals_id",it) }
        super.onSaveInstanceState(out)
    }
}
