package dev.lolokeksu.legioncontrol

import android.app.Activity
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
class AudioLabActivity:Activity() {
    companion object { private const val REQUEST_CSV=2201 }
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var backend:AudioTestBackend
    private lateinit var routeMonitor:AudioRouteMonitor
    private lateinit var mode:Spinner
    private lateinit var channel:Spinner
    private lateinit var frequency:Spinner
    private lateinit var duration:Spinner
    private lateinit var result:TextView
    private lateinit var routing:TextView
    private lateinit var playButton:Button
    private lateinit var stopButton:Button
    private var selectedId:String?=null
    private var lastCompletedReport:String?=null
    private var firstPlayConfirmed=false
    private var playing=false
    private var visible=false
    private var pending:Future<*>?=null
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        backend=AudioTestBackend(applicationContext)
        routeMonitor=AudioRouteMonitor(applicationContext) { text ->
            runOnUiThread { if(visible && !isDestroyed && ::routing.isInitialized) routing.text=text }
        }
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(20.dp,28.dp,20.dp,36.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun title(text:String) {
            root.addView(TextView(this).apply {
                this.text=text; textSize=20f;setTextColor(Color.WHITE)
                setTypeface(null,Typeface.BOLD)
                setPadding(0,18.dp,0,10.dp)
            })
        }
        fun detail(text:String) {
            root.addView(TextView(this).apply {
                this.text=text; textSize=13f;setTextColor(0xFFB1B1B7.toInt())
                setLineSpacing(3f,1f)
                setPadding(0,4.dp,0,12.dp)
            })
        }
        fun select(label:String,options:List<String>,default:Int=0):Spinner {
            detail(label)
            val control=Spinner(this)
            val adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,options)
            control.adapter=adapter
            control.setSelection(default.coerceIn(0,options.lastIndex))
            root.addView(control)
            return control
        }
        fun action(text:String,click:()->Unit):Button {
            val b=Button(this).apply {
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
        title("AUDIO LAB · ALPHA.22")
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
        action("Стерео · 440 Гц") { startWith("TONE","BOTH",440,1500) }
        action("Идентификация: левый → правый") { startWith("CHANNEL_ID","ALTERNATING",440,3000) }
        action("Частотный sweep: 220 → 1760 Гц") { startWith("SWEEP","BOTH",440,3000) }
        stopButton=action("ОСТАНОВИТЬ звук") { backend.stop();show("Остановка запрошена. "+
            "Плеер освобождает AudioTrack и сохраняет частичный результат.") }
        stopButton.isEnabled=false
        title("Доступные выходы Android")
        routing=TextView(this).apply {
            setTextColor(Color.WHITE);textSize=13f
            setLineSpacing(3f,1f);setPadding(0,8.dp,0,12.dp)
            text="Список выходов загружается при открытии экрана."
        }
        root.addView(routing)
        action("Обновить подключённые аудиовыходы") { refreshRoutes() }
        detail("Список подключённых выходов не равен фактическому маршруту воспроизведения. "+
            "Во время теста AudioTrack сообщает route type, если прошивка предоставляет его.")
        title("Локальная история")
        action("Выбрать записанный аудиотест") { pickHistory() }
        action("Сравнить два последних теста") { compare() }
        action("Поделиться выбранным отчётом") { share() }
        action("Экспортировать историю и маршруты CSV") { exportCsv() }
        action("Удалить выбранный тест") { confirmDelete() }
        result=TextView(this).apply {
            setTextColor(Color.WHITE);textSize=14f
            setTextIsSelectable(true);setLineSpacing(4f,1f)
            setPadding(0,14.dp,0,28.dp)
            text="Выберите тест и проверьте громкость. "+
                "Никакой звук не воспроизводится без вашего нажатия."
        }
        root.addView(result)
        setContentView(ScrollView(this).apply { addView(root) })
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
            AlertDialog.Builder(this)
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
        backend.arm()
        playing=true
        buttons()
        lastCompletedReport=null
        show("Подготовка исходного PCM через backend · $type / $which / $hz Гц…")
        pending=worker.submit {
            var runId:String?=null
            val message=runCatching {
                AudioLabRepository(applicationContext).use { repo ->
                    val id=repo.start(type,which,hz,length)
                    runId=id
                    val playback=runCatching {
                        if(!visible) backend.stop()
                        backend.play(type,which,hz,length) { current,total ->
                            runOnUiThread {
                                if(visible && !isDestroyed && playing)
                                    show("AudioTrack: $current / $total PCM кадров\n"+
                                        "Фактическая слышимость, громкость и задержка "+
                                        "по Android API не измеряются.")
                            }
                        }
                    }.getOrElse { error ->
                        AudioPlaybackResult(System.currentTimeMillis().coerceAtLeast(1),
                            "ERROR",0,null,error.javaClass.simpleName.take(48),emptyList())
                    }
                    repo.finish(id,playback)
                    repo.format(id)
                }
            }.getOrElse { error ->
                "Ошибка аудиотеста: "+error.javaClass.simpleName+" · "+error.message.orEmpty().take(140)
            }
            runOnUiThread {
                playing=false
                if(visible && !isDestroyed) {
                    selectedId=runId
                    lastCompletedReport=message
                    buttons()
                    show(message)
                }
            }
        }
    }

    private fun local(block:()->String) {
        if(playing) return show("Сначала остановите воспроизведение.")
        show("Чтение локальных данных…")
        worker.execute {
            val message=runCatching { block() }.getOrElse { e ->
                "Ошибка: "+e.javaClass.simpleName+" · "+e.message.orEmpty().take(140)
            }
            runOnUiThread {
                if(visible && !isDestroyed) {
                    lastCompletedReport=message
                    show(message)
                }
            }
        }
    }

    private fun refreshRoutes() {
        if(visible) routing.text=AudioRouteInspector.snapshot(this)
    }

    private fun pickHistory() = local {
        AudioLabRepository(applicationContext).use { repo ->
            val items=repo.runs(60)
            runOnUiThread {
                if(visible && !isDestroyed) {
                    if(items.isEmpty()) show("Аудиотестов в локальной истории ещё нет.")
                    else AlertDialog.Builder(this).setTitle("Аудиотесты · ${items.size}")
                        .setItems(items.map { item ->
                            DateFormat.getDateTimeInstance().format(Date(item.startedAtMs))+
                                " · "+item.kind+" · "+item.channel+" · "+item.state
                        }.toTypedArray()) { _,idx ->
                            selectedId=items[idx].id
                            local { AudioLabRepository(applicationContext).use {
                                it.format(items[idx].id)
                            } }
                        }.setNegativeButton("Отмена",null).show()
                }
            }
            "История: ${items.size} записей."
        }
    }

    private fun compare() = local {
        AudioLabRepository(applicationContext).use { it.compareRecent() }
    }

    private fun share() {
        val text=lastCompletedReport ?: return show("Сначала откройте завершённый отчёт.")
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
        val id=selectedId ?: return show("Сначала выберите аудиотест.")
        if(playing) return show("Остановите воспроизведение.")
        AlertDialog.Builder(this).setTitle("Удалить аудиотест?")
            .setMessage("Удалится только выбранная аудиозапись метаданных и её события маршрута. "+
                "Игровые сессии, профили и другие данные останутся.")
            .setPositiveButton("Удалить") { _,_ ->
                local {
                    val didDelete=AudioLabRepository(applicationContext).use { it.delete(id) }
                    if(didDelete) {
                        selectedId=null
                        "Аудиотест удалён из локальной истории."
                    } else "Аудиотест уже отсутствует."
                }
            }.setNegativeButton("Отмена",null).show()
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=REQUEST_CSV || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        local {
            AudioLabRepository(applicationContext).use { repo ->
                (contentResolver.openOutputStream(uri,"w") ?: error("Не удалось открыть CSV")).use { out ->
                    OutputStreamWriter(out,Charsets.UTF_8).use { writer -> repo.exportCsv(writer) }
                }
            }
            "CSV записан в выбранный документ; ни одна аудиозапись не создавалась и не передавалась."
        }
    }

    override fun onStart() {
        super.onStart()
        visible=true
        routeMonitor.start()
        refreshRoutes()
        buttons()
    }

    override fun onStop() {
        visible=false
        backend.stop()
        routeMonitor.stop()
        super.onStop()
    }

    override fun onDestroy() {
        backend.stop()
        worker.shutdown()
        super.onDestroy()
    }
}
