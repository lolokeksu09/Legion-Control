package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Manual Haptic Lab; no privileged controls, telemetry service or background vibration. */
class HapticLabFragment:Fragment() {
    companion object { private const val REQUEST_CSV=2301 }
    private lateinit var viewModel: HapticLabViewModel
    private lateinit var patternSpinner:Spinner
    private lateinit var levelSpinner:Spinner
    private lateinit var output:TextView
    private lateinit var startButton:Button
    private lateinit var stopButton:Button
    private var visible=false
    private var playing=false
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val root=LinearLayout(requireContext()).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(20.dp,26.dp,20.dp,38.dp)
        }
        fun title(t:String) {
            root.addView(TextView(requireContext()).apply {
                text=t;textSize=21f;setTextColor(Color.WHITE)
                setTypeface(null,Typeface.BOLD);setPadding(0,10.dp,0,9.dp)
            })
        }
        fun detail(t:String) {
            root.addView(TextView(requireContext()).apply {
                text=t;textSize=13f;setTextColor(0xFFB8B8C0.toInt())
                setLineSpacing(3f,1f);setPadding(0,3.dp,0,13.dp)
            })
        }
        fun spinner(label:String,items:List<String>):Spinner {
            detail(label)
            val control=Spinner(requireContext())
            val adapter=object:ArrayAdapter<String>(requireContext(),android.R.layout.simple_spinner_item,items) {
                override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                    return TextView(context).apply {
                        text=getItem(position);textSize=16f;setTextColor(Color.WHITE)
                        setBackgroundColor(0xFF17171B.toInt())
                        setPadding(13.dp,14.dp,13.dp,14.dp)
                    }
                }
                override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View {
                    return TextView(context).apply {
                        text=getItem(position);textSize=15f;setTextColor(Color.WHITE)
                        setBackgroundColor(0xFF24242C.toInt())
                        setPadding(15.dp,13.dp,15.dp,13.dp)
                    }
                }
            }
            control.adapter=adapter
            root.addView(control,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=12.dp })
            return control
        }
        fun action(t:String,run:()->Unit):Button {
            val b=Button(requireContext()).apply {
                text=t;isAllCaps=false;minHeight=48.dp;textSize=14f
                setTextColor(Color.WHITE)
                background=GradientDrawable().apply {
                    setColor(0xFF1A1B20.toInt());cornerRadius=13.dp.toFloat()
                    setStroke(1.dp,0xFF3D3D46.toInt())
                }
                setPadding(11.dp,8.dp,11.dp,8.dp)
                setOnClickListener { run() }
            }
            root.addView(b,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=9.dp })
            return b
        }
        title("HAPTIC LAB · ALPHA.23")
        detail("Настоящая ручная проверка вибромотора Android, а не повторение Audio Lab "+
            "или сенсорных тестов. Все шаблоны до 1,8 секунды. "+
            "Нет повторяющихся циклов, скрытого запуска или изменения системных параметров.")
        detail("Осторожно: вибрация может ощущаться сильнее ожидаемого. "+
            "При чувствительности к вибрации выберите GENTLE. "+
            "Вводимая интенсивность — цифровой запрос, не физическая сила.")
        patternSpinner=spinner("Сигнал",listOf(
            "TAP","DOUBLE_TAP","TRIPLE_PULSE","SLOW_PULSE","LONG_PULSE"))
        levelSpinner=spinner("Цифровая интенсивность",listOf(
            "GENTLE · 48/255","MEDIUM · 96/255","STRONG · 160/255"))
        startButton=action("ЗАПУСТИТЬ ТАКТИЛЬНЫЙ ТЕСТ") { startSelected() }
        action("Быстрая проверка: один короткий импульс") { startTest("TAP","GENTLE") }
        action("Два коротких импульса") { startTest("DOUBLE_TAP","MEDIUM") }
        action("Три импульса") { startTest("TRIPLE_PULSE","MEDIUM") }
        action("Медленная последовательность") { startTest("SLOW_PULSE","GENTLE") }
        stopButton=action("ОСТАНОВИТЬ ВИБРАЦИЮ") {
            viewModel.stopPlayback()
            show("Запрошена остановка. Результат будет отмечен CANCELLED.")
        }
        stopButton.isEnabled=false
        title("Возможности вибромотора")
        action("Проверить Android Vibra-capabilities") { inspect() }
        action("Сохранить проверенные возможности в историю") { saveCapabilities() }
        action("Последний сохранённый снимок возможностей") { savedCapabilities() }
        action("Открыть системные настройки звука и вибрации") {
            runCatching { startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                .onFailure { show("Системные настройки недоступны на этой прошивке.") }
        }
        detail("Проверяются Android vibrator IDs, поддержка регулировки амплитуды, "+
            "предопределённых эффектов и primitives. "+
            "Это информация API, а не измерение вибрации или проверка качества мотора.")
        title("История и субъективная оценка")
        action("Выбрать сохранённый тест") { chooseRun() }
        action("Отметить, ощущалась ли вибрация") { chooseRating() }
        action("Сравнить два последних теста") { compare() }
        action("Отправить выбранный текстовый отчёт") { share() }
        action("Экспортировать историю и снимки CSV") { exportCsv() }
        action("Удалить выбранный тест (подтверждение)") { confirmDelete() }
        detail("Личные оценки сохраняются только локально. "+
            "«COMPLETED_REQUEST» означает, что Android получил запрос без известной ошибки, "+
            "а не то, что мотор физически сработал.")
        output=TextView(requireContext()).apply {
            textSize=14f;setTextColor(Color.WHITE);setLineSpacing(4f,1f)
            setTextIsSelectable(true);setPadding(0,15.dp,0,32.dp)
            text="Сначала выберите шаблон или просмотрите возможности устройства."
        }
        root.addView(output)
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        viewModel=ViewModelProvider(this)[HapticLabViewModel::class.java]
        viewModel.state.observe(viewLifecycleOwner) { state ->
            playing = state.playing
            buttons()
            show(state.message)
            state.runs?.let { runs ->
                viewModel.consumePicker(state.nonce)
                chooseRunDialog(runs)
            }
        }
        if (savedInstanceState != null) {
            patternSpinner.setSelection(savedInstanceState.getInt("pattern",0).coerceIn(0,4))
            levelSpinner.setSelection(savedInstanceState.getInt("level",0).coerceIn(0,2))
        }
    }

    private fun show(text:String) { if(::output.isInitialized) output.text=text }
    private fun buttons() {
        if(::startButton.isInitialized) {
            startButton.isEnabled=visible && !playing
            stopButton.isEnabled=visible && playing
        }
    }

    private fun startSelected() {
        val kind=HapticPattern.kinds[patternSpinner.selectedItemPosition.coerceIn(0,4)]
        val level=HapticPattern.levels[levelSpinner.selectedItemPosition.coerceIn(0,2)]
        startTest(kind,level)
    }

    private fun startTest(kind:String,level:String) {
        if (!visible || playing) return
        viewModel.startTest(kind, level)
    }

    private fun inspect() = viewModel.inspect()

    private fun saveCapabilities() = viewModel.saveCapabilities()

    private fun savedCapabilities() = viewModel.savedCapabilities()

    private fun chooseRun() = viewModel.chooseRun()

    private fun chooseRunDialog(runs: List<HapticTestRun>) {
        if (runs.isEmpty()) { show("История тактильных тестов пуста."); return }
        AlertDialog.Builder(requireContext()).setTitle("История Haptic Lab · ${runs.size}")
            .setItems(runs.map { r ->
                DateFormat.getDateTimeInstance().format(Date(r.startedAtMs)) +
                    " · " + r.pattern + " · " + r.level + " · " + r.result
            }.toTypedArray()) { _,i -> viewModel.selectRun(runs[i].id) }
            .setNegativeButton("Отмена",null).show()
    }

    private fun chooseRating() {
        val id=viewModel.selectedRunId ?: return show("Сначала выберите тест из истории или выполните новый.")
        if(playing) return show("Нельзя оценивать тест до его завершения.")
        val note=EditText(requireContext()).apply {
            hint="Необязательная заметка · до 200 символов"
            setSingleLine(false);minLines=2;maxLines=3
        }
        val ratings=listOf(
            "FELT · ощущалась",
            "NOT_FELT · не ощущалась",
            "UNCERTAIN · не уверен"
        )
        AlertDialog.Builder(requireContext()).setTitle("Как вы ощутили вибрацию?")
            .setView(note)
            .setItems(ratings.toTypedArray()) { _,index ->
                val value=ratings[index].substringBefore(" ")
                val text=note.text.toString()
                if(text.length>200)show("Заметка не должна превышать 200 символов.")
                else viewModel.rate(id,value,text)
            }.setNegativeButton("Отмена",null).show()
    }

    private fun compare()=viewModel.compare()

    private fun share() {
        val text=viewModel.lastReport ?: return show("Сначала сформируйте отчёт.")
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,text)
            },"Поделиться Haptic Lab"))
        }.onFailure { show("Системное меню отправки недоступно.") }
    }

    private fun exportCsv() {
        if(playing) return show("Остановите текущую вибрацию.")
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="text/csv"
                putExtra(Intent.EXTRA_TITLE,"legion-haptic-lab-alpha23.csv")
            },REQUEST_CSV)
        }.onFailure { show("Нельзя выбрать документ: "+it.javaClass.simpleName) }
    }

    private fun confirmDelete() {
        val id=viewModel.selectedRunId ?: return show("Сначала выберите тест из истории.")
        if(playing)return show("Остановите тест перед удалением.")
        AlertDialog.Builder(requireContext()).setTitle("Удалить выбранный тактильный тест?")
            .setMessage("Удалится только локальная запись этого тактильного запроса "+
                "и ваша субъективная оценка. Другие модули сохранят данные.")
            .setPositiveButton("Удалить") { _,_ ->
                viewModel.delete(id)
            }.setNegativeButton("Отмена",null).show()
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=REQUEST_CSV || resultCode!=Activity.RESULT_OK)return
        val uri=data?.data ?: return
        viewModel.exportCsv(uri)
    }

    override fun onStart() {
        super.onStart();visible=true;viewModel.setVisible(true);buttons()
    }

    override fun onStop() {
        visible=false
        viewModel.setVisible(false)
        viewModel.stopPlayback()
        super.onStop()
    }

    override fun onSaveInstanceState(outState:Bundle) {
        outState.putInt("pattern",patternSpinner.selectedItemPosition)
        outState.putInt("level",levelSpinner.selectedItemPosition)
        super.onSaveInstanceState(outState)
    }
}
