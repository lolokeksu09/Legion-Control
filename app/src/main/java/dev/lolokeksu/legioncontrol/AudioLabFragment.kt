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
import android.media.AudioManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Original generated tones only. UI tests, recording and microphone access do not exist.
 * All playback is started explicitly, on a worker, and stopped on screen exit.
 */
class AudioLabFragment:Fragment() {
    companion object { private const val REQUEST_CSV=2201 }
    private lateinit var viewModel:AudioLabViewModel
    private lateinit var mode:Spinner
    private lateinit var channel:Spinner
    private lateinit var frequency:Spinner
    private lateinit var duration:Spinner
    private lateinit var result:TextView
    private lateinit var routing:TextView
    private lateinit var playButton:Button
    private lateinit var stopButton:Button
    private var firstPlayConfirmed=false
    private var playing=false
    private var visible=false
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val root=LinearLayout(requireContext()).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(20.dp,28.dp,20.dp,36.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun title(text:String) {
            root.addView(TextView(requireContext()).apply {
                this.text=text; textSize=20f;setTextColor(Color.WHITE)
                setTypeface(null,Typeface.BOLD)
                setPadding(0,18.dp,0,10.dp)
            })
        }
        fun detail(text:String) {
            root.addView(TextView(requireContext()).apply {
                this.text=text; textSize=13f;setTextColor(0xFFB1B1B7.toInt())
                setLineSpacing(3f,1f)
                setPadding(0,4.dp,0,12.dp)
            })
        }
        fun select(label:String,options:List<String>,default:Int=0):Spinner {
            detail(label)
            val control=Spinner(requireContext())
            val adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,options)
            control.adapter=adapter
            control.setSelection(default.coerceIn(0,options.lastIndex))
            root.addView(control)
            return control
        }
        fun action(text:String,featureId:String?=null,click:()->Unit):Button {
            val b=Button(requireContext()).apply {
                if(featureId!=null) FeatureReveal.bind(this,featureId)
                this.text=text;isAllCaps=false;minHeight=48.dp;textSize=14f
                setTextColor(Color.WHITE)
                background=GradientDrawable().apply {
                    setColor(0xFF191A1E.toInt());cornerRadius=13.dp.toFloat()
                    setStroke(1.dp,0xFF3A3A42.toInt())
                }
                setPadding(12.dp,8.dp,12.dp,8.dp)
                setOnClickListener { click() }
            }
            root.addView(b,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=8.dp })
            return b
        }
        title("AUDIO LAB")
        detail("Новый генератор оригинальных звуковых сигналов. "+
            "Для проверки каналов и маршрутизации звука. "+
            "Не измеряет громкость, задержку динамика, частотную характеристику "+
            "или качество наушников без внешнего оборудования.")
        detail("БЕЗОПАСНОСТЬ: уменьшите системную громкость перед запуском. "+
            "Сигнал ограничен 10% цифровой амплитуды, до 3 секунд. "+
            "Системную громкость приложение не увеличивает.")
        mode=select("Режим",listOf("TONE","SWEEP","CHANNEL_ID"))
        channel=select("Канал",listOf("BOTH","LEFT","RIGHT","ALTERNATING"))
        frequency=select("Частота тона, Гц",listOf("220","440","880","1760"),1)
        duration=select("Длительность",listOf("750 мс","1500 мс","3000 мс"),1)
        playButton=action("ВОСПРОИЗВЕСТИ выбранный тест") { selectedPlay() }
        action("Левый канал · 440 Гц") { startWith("TONE","LEFT",440,1500) }
        action("Правый канал · 440 Гц") { startWith("TONE","RIGHT",440,1500) }
        action("Стерео · 440 Гц", "audio.stereo") { startWith("TONE","BOTH",440,1500) }
        action("Идентификация: левый → правый") { startWith("CHANNEL_ID","ALTERNATING",440,3000) }
        action("Частотный sweep: 220 → 1760 Гц", "audio.sweep") { startWith("SWEEP","BOTH",440,3000) }
        stopButton=action("ОСТАНОВИТЬ звук") { viewModel.stopPlayback();show("Остановка запрошена. "+
            "Плеер освобождает AudioTrack и сохраняет частичный результат.") }
        stopButton.isEnabled=false
        title("Доступные выходы Android")
        routing=TextView(requireContext()).apply {
            setTextColor(Color.WHITE);textSize=13f
            setLineSpacing(3f,1f);setPadding(0,8.dp,0,12.dp)
            text="Список выходов загружается при открытии экрана."
        }
        root.addView(routing)
        action("Обновить подключённые аудиовыходы", "audio.routes") { refreshRoutes() }
        detail("Список подключённых выходов не равен фактическому маршруту воспроизведения. "+
            "Во время теста AudioTrack сообщает route type, если прошивка предоставляет его.")
        title("Локальная история")
        action("Выбрать записанный аудиотест") { pickHistory() }
        action("Сравнить два последних теста") { compare() }
        action("Поделиться выбранным отчётом") { share() }
        action("Экспортировать историю и маршруты CSV") { exportCsv() }
        action("Удалить выбранный тест") { confirmDelete() }
        result=TextView(requireContext()).apply {
            setTextColor(Color.WHITE);textSize=14f
            setTextIsSelectable(true);setLineSpacing(4f,1f)
            setPadding(0,14.dp,0,28.dp)
            text="Выберите тест и проверьте громкость. "+
                "Никакой звук не воспроизводится без вашего нажатия."
        }
        root.addView(result)
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        LegionUiSupport.styleModuleTree(view)
        FeatureReveal.fromFragment(this, view, FeatureModule.AUDIO)
        viewModel=ViewModelProvider(this)[AudioLabViewModel::class.java]
        viewModel.state.observe(viewLifecycleOwner) { state ->
            playing=state.playing
            buttons()
            show(state.message)
            state.runs?.let { list ->
                viewModel.consumePicker(state.nonce)
                showHistoryPicker(list)
            }
        }
        viewModel.routes.observe(viewLifecycleOwner) { routing.text=it }
        if(savedInstanceState!=null) {
            mode.setSelection(savedInstanceState.getInt("mode",0).coerceIn(0,2))
            channel.setSelection(savedInstanceState.getInt("channel",0).coerceIn(0,3))
            frequency.setSelection(savedInstanceState.getInt("frequency",1).coerceIn(0,3))
            duration.setSelection(savedInstanceState.getInt("duration",1).coerceIn(0,2))
            firstPlayConfirmed=savedInstanceState.getBoolean("firstPlayConfirmed",false)
        }
    }

    private fun show(t:String) { if(::result.isInitialized) result.text=t }
    private fun buttons() {
        if(::playButton.isInitialized) {
            playButton.isEnabled=visible && !playing
            stopButton.isEnabled=visible && playing
        }
    }

    private fun selectedPlay() {
        val type=mode.selectedItem.toString()
        val which=channel.selectedItem.toString()
        val hz=frequency.selectedItem.toString().toInt()
        val length=when(duration.selectedItemPosition) {
            0 -> 750
            2 -> 3000
            else -> 1500
        }
        if(!AudioSignalGenerator.isValid(type,which,hz,length))
            return show("Для CHANNEL_ID выберите ALTERNATING. "+
                "Для TONE/SWEEP — LEFT, RIGHT или BOTH.")
        confirmAndStart(type,which,hz,length)
    }

    private fun startWith(type:String,which:String,hz:Int,length:Int) {
        mode.setSelection(AudioSignalGenerator.kinds.indexOf(type))
        channel.setSelection(listOf("BOTH","LEFT","RIGHT","ALTERNATING").indexOf(which))
        frequency.setSelection(AudioSignalGenerator.frequencies.indexOf(hz))
        duration.setSelection(when(length) {750->0;3000->2;else->1})
        confirmAndStart(type,which,hz,length)
    }

    private fun confirmAndStart(type:String,which:String,hz:Int,length:Int) {
        if(!visible || playing) return
        if(!firstPlayConfirmed) {
            AlertDialog.Builder(requireContext())
                .setTitle("Проверка громкости перед тестом")
                .setMessage("Будет воспроизведён короткий оригинальный тон. "+
                    "Убавьте системную громкость, особенно в наушниках. "+
                    "Динамик может не воспроизводить низкие частоты — это не ошибка Android.")
                .setPositiveButton("Громкость проверена — начать") { _,_ ->
                    firstPlayConfirmed=true
                    play(type,which,hz,length)
                }.setNegativeButton("Отмена",null).show()
        } else play(type,which,hz,length)
    }

    private fun play(type:String,which:String,hz:Int,length:Int) {
        if(!visible || playing) return
        viewModel.play(type,which,hz,length)
    }

    private fun refreshRoutes() { if(visible) viewModel.refreshRoutes() }

    private fun pickHistory() = viewModel.pickHistory()

    private fun showHistoryPicker(items:List<AudioTestRun>) {
        if(items.isEmpty()) { show("Аудиотестов в локальной истории ещё нет."); return }
        AlertDialog.Builder(requireContext()).setTitle("Аудиотесты · ${items.size}")
            .setItems(items.map { item ->
                DateFormat.getDateTimeInstance().format(Date(item.startedAtMs))+
                    " · "+item.kind+" · "+item.channel+" · "+item.state
            }.toTypedArray()) { _,idx -> viewModel.selectRun(items[idx].id) }
            .setNegativeButton("Отмена",null).show()
    }

    private fun compare() = viewModel.compare()

    private fun share() {
        val text=viewModel.lastCompletedReport ?: return show("Сначала откройте завершённый отчёт.")
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,text)
            },"Поделиться аудиоотчётом"))
        }.onFailure { show("Меню отправки недоступно: "+it.javaClass.simpleName) }
    }

    private fun exportCsv() {
        if(playing) return show("Сначала остановите звук.")
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="text/csv"
                putExtra(Intent.EXTRA_TITLE,"legion-audio-lab-alpha22.csv")
            },REQUEST_CSV)
        }.onFailure { show("Не удалось открыть выбор файла: "+it.javaClass.simpleName) }
    }

    private fun confirmDelete() {
        val id=viewModel.selectedId ?: return show("Сначала выберите аудиотест.")
        if(playing) return show("Остановите воспроизведение.")
        AlertDialog.Builder(requireContext()).setTitle("Удалить аудиотест?")
            .setMessage("Удалится только выбранная аудиозапись метаданных и её события маршрута. "+
                "Игровые сессии, профили и другие данные останутся.")
            .setPositiveButton("Удалить") { _,_ ->
                viewModel.delete(id)
            }.setNegativeButton("Отмена",null).show()
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=REQUEST_CSV || resultCode!=Activity.RESULT_OK) return
        val uri=data?.data ?: return
        viewModel.exportCsv(uri)
    }

    override fun onStart() {
        super.onStart()
        visible=true
        viewModel.setVisible(true)
        viewModel.startRouteMonitor()
        refreshRoutes()
        buttons()
    }

    override fun onStop() {
        visible=false
        viewModel.stopPlayback()
        viewModel.setVisible(false)
        viewModel.stopRouteMonitor()
        super.onStop()
    }

    override fun onSaveInstanceState(outState:Bundle) {
        outState.putInt("mode",mode.selectedItemPosition)
        outState.putInt("channel",channel.selectedItemPosition)
        outState.putInt("frequency",frequency.selectedItemPosition)
        outState.putInt("duration",duration.selectedItemPosition)
        outState.putBoolean("firstPlayConfirmed",firstPlayConfirmed)
        super.onSaveInstanceState(outState)
    }
}
