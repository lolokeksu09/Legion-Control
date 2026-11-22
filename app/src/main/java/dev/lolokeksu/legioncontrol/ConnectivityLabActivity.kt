package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** Visible-only, opt-in public endpoint probes. No foreground service or hidden scheduler. */
class ConnectivityLabActivity : Activity() {
    companion object {
        private const val EXPORT_CSV = 2001
        private const val INTERVAL_MS = 12_000L
    }

    private val worker:ExecutorService=Executors.newSingleThreadExecutor()
    private val cancelled=AtomicBoolean(false)
    @Volatile private var testing=false
    private var task:Future<*>?=null
    private var host="one.one.one.one"
    private var selectedRunId:String?=null
    private lateinit var hostButton:Button
    private lateinit var singleButton:Button
    private lateinit var stabilityButton:Button
    private lateinit var stopButton:Button
    private lateinit var resultView:TextView
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(20.dp,30.dp,20.dp,40.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun title(value:String) {
            root.addView(TextView(this).apply {
                text=value
                textSize=22f
                setTextColor(Color.WHITE)
                setTypeface(null,Typeface.BOLD)
                setPadding(0,8.dp,0,14.dp)
            })
        }
        fun description(value:String) {
            root.addView(TextView(this).apply {
                text=value
                textSize=13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0,6.dp,0,14.dp)
                setLineSpacing(3f,1f)
            })
        }
        fun button(textValue:String,onClick:()->Unit):Button {
            return Button(this).apply {
                text=textValue
                minHeight=50.dp
                isAllCaps=false
                textSize=14f
                setTextColor(Color.WHITE)
                background=GradientDrawable().apply {
                    setColor(0xFF1E1E21.toInt())
                    cornerRadius=14.dp.toFloat()
                    setStroke(1.dp,0xFF38383E.toInt())
                }
                setPadding(12.dp,7.dp,12.dp,7.dp)
                setOnClickListener { onClick() }
                val p=LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin=9.dp
                root.addView(this,p)
            }
        }
        title("CONNECTIVITY OBSERVATORY")
        description("Legion Control · alpha.20 · диагностика любой сети Android 13+. "+
            "Все проверки добровольные и обращаются к публичному серверу: "+
            "это НЕ ping игрового сервера, НЕ FPS и НЕ ICMP.")
        hostButton=button("Сервер: Cloudflare · one.one.one.one") { chooseHost() }
        singleButton=button("ОДИН ЗАМЕР · DNS / IPv4 / IPv6 / TCP / TLS") { startTest(1) }
        stabilityButton=button("ТЕСТ СТАБИЛЬНОСТИ · до 6 циклов (~60 секунд)") { startTest(6) }
        stopButton=button("ОСТАНОВИТЬ текущий тест") { cancelTest(true) }
        stopButton.isEnabled=false
        description("Каждый цикл: DNS, до одного соединения IPv4 и IPv6, "+
            "TCP connect и TLS handshake с проверкой сертификата и имени сайта. "+
            "DNS-кэш Android может влиять на результаты. "+
            "При уходе с экрана запись отменяется; фоновый сервис не запускается.")
        button("История проверок · выбрать запись") { showHistory() }
        button("Подробный отчёт выбранной записи") { showSelected() }
        button("Сравнить два последних запуска одного сервера") { compare() }
        button("Сводка по этому серверу за 7 дней") { weekReport() }
        button("Экспорт ВСЕЙ истории сетевых тестов CSV") { exportCsv() }
        button("Поделиться выбранным отчётом") { share() }
        button("Удалить выбранную запись (с подтверждением)") { confirmDelete() }
        description("История и сравнения остаются локальными. CSV сохраняется только "+
            "через системный выбор документа. DNS/TCP/TLS-результаты не доказывают "+
            "качество подключения к конкретной игре. Новые Android разрешения не нужны.")
        resultView=TextView(this).apply {
            textSize=14f
            setTextColor(Color.WHITE)
            setLineSpacing(4f,1f)
            setPadding(0,14.dp,0,28.dp)
            text="Сервер диагностики выбран. Чтобы проверить сеть, нажмите замер. "+
                "Обращение к публичному узлу начнётся только после нажатия."
        }
        root.addView(resultView)
        setContentView(ScrollView(this).apply { addView(root) })
        updateButtons()
    }

    private fun updateButtons() {
        if(::singleButton.isInitialized) {
            singleButton.isEnabled=!testing
            stabilityButton.isEnabled=!testing
            hostButton.isEnabled=!testing
            stopButton.isEnabled=testing
        }
    }

    private fun show(text:String) {
        if(::resultView.isInitialized) resultView.text=text
    }

    private fun chooseHost() {
        if(testing) return
        val names=arrayOf(
            "Cloudflare · one.one.one.one",
            "Google DNS · dns.google",
            "Quad9 · dns.quad9.net"
        )
        AlertDialog.Builder(this).setTitle("Публичный сервер для диагностики")
            .setItems(names) { _,index ->
                host=ConnectivityProbe.targets[index]
                hostButton.text="Сервер: "+names[index]
                selectedRunId=null
                show("Выбран "+host+":443. Это внешний тестовый сервер, не игровой хост.")
            }.setNegativeButton("Отмена",null).show()
    }

    private fun startTest(cycles:Int) {
        if(testing) return
        val target=host
        testing=true
        cancelled.set(false)
        updateButtons()
        show("Запрошено подключение к $target:443. Запуск теста…")
        task=worker.submit {
            var message="Нет сохранённых результатов: запрос был отменён до начала измерений."
            var runId:String?=null
            var outcome="COMPLETE"
            try {
                ConnectivityRepository(applicationContext).use { db ->
                    try {
                        runId=db.start(target,if(cycles==1)"SINGLE" else "STABILITY")
                        for(cycle in 1..cycles) {
                            if(cancelled.get() || Thread.currentThread().isInterrupted) {
                                outcome="CANCELLED"
                                break
                            }
                            val begin=SystemClock.elapsedRealtime()
                            val readings=ConnectivityProbe.capture(applicationContext,target,cycle)
                            readings.forEach { db.record(runId!!,it) }
                            val short="${cycle}/$cycles · "+
                                readings.joinToString(" · ") { it.family+": "+it.result }
                            runOnUiThread {
                                if(!isFinishing && !isDestroyed && !cancelled.get())
                                    show("Проверка $target:443\n$short\n"+
                                        "Оставьте экран открытым; не переключайтесь в другое приложение.")
                            }
                            if(cycle<cycles) {
                                val waitMs=(INTERVAL_MS-(SystemClock.elapsedRealtime()-begin))
                                    .coerceAtLeast(0L)
                                if(waitMs>0) Thread.sleep(waitMs)
                            }
                        }
                        if(cancelled.get() || Thread.currentThread().isInterrupted)
                            outcome="CANCELLED"
                    } catch(interrupted:InterruptedException) {
                        outcome="CANCELLED"
                        Thread.interrupted() // clear flag so SQLite finalization can run
                    } catch(error:Exception) {
                        outcome=if(cancelled.get())"CANCELLED" else "FAILED"
                    } finally {
                        runId?.let { id -> db.finish(id,outcome) }
                    }
                    message=runId?.let { ConnectivityAnalytics.render(db.summary(it)) } ?:
                        "Не удалось начать тест и создать запись в SQLite."
                }
            } catch(error:Exception) {
                message="Ошибка сетевого теста: "+error.javaClass.simpleName+
                    ". Приложение не выполняло скрытый фоновый тест."
            } finally {
                testing=false
                runOnUiThread {
                    if(!isFinishing && !isDestroyed) {
                        updateButtons()
                        if(!cancelled.get()) {
                            selectedRunId=runId
                            show(message)
                        } else show("Тест отменён. Частичная запись сохранена, если "+
                            "SQLite успела завершить запись. Проверьте историю.")
                    }
                }
            }
        }
    }

    private fun cancelTest(display:Boolean) {
        if(!testing) return
        cancelled.set(true)
        task?.cancel(true)
        if(display && ::resultView.isInitialized)
            show("Отмена запрошена. Уже начатое DNS-разрешение может завершиться позже; "+
                "дальнейшие циклы запускаться не будут.")
    }

    private fun task(block:()->String) {
        if(testing) {
            show("Сначала остановите сетевую проверку.")
            return
        }
        show("Чтение локальной истории…")
        worker.execute {
            val message=runCatching { block() }.getOrElse {
                "Ошибка: "+it.javaClass.simpleName+" · "+it.message.orEmpty().take(120)
            }
            runOnUiThread { if(!isFinishing && !isDestroyed) show(message) }
        }
    }

    private fun showHistory() = task {
        ConnectivityRepository(applicationContext).use { db ->
            val items=db.runs(host,50)
            runOnUiThread {
                if(!isFinishing && !isDestroyed) {
                    if(items.isEmpty()) show("Для "+host+" пока нет сохранённых запусков.")
                    else AlertDialog.Builder(this).setTitle("История · "+host)
                        .setItems(items.map { r ->
                            DateFormat.getDateTimeInstance().format(Date(r.startedAtMs))+
                                " · "+r.mode+" · "+r.status
                        }.toTypedArray()) { _,index ->
                            selectedRunId=items[index].id
                            show("Выбрана запись "+items[index].id+
                                "\nНажмите «Подробный отчёт».")
                        }.setNegativeButton("Отмена",null).show()
                }
            }
            "Записей для выбранного узла: ${items.size} (показаны последние 50)."
        }
    }

    private fun showSelected() {
        val id=selectedRunId ?: return show("Сначала выберите тест из истории.")
        task {
            ConnectivityRepository(applicationContext).use { db ->
                val summary=db.summary(id)
                val details=db.samples(id).joinToString("\n") { r ->
                    "цикл ${r.cycle} · ${r.family} · ${r.result} · "+
                    "TCP="+(r.tcpMs?.let { String.format(Locale.US,"%.1f мс",it) } ?: "н/д")+
                    " · TLS="+(r.tlsMs?.let { String.format(Locale.US,"%.1f мс",it) } ?: "н/д")+
                    (if(r.errorCode.isBlank())"" else " · "+r.errorCode)
                }
                ConnectivityAnalytics.render(summary)+"\n\nПОИМЁННЫЕ ПОПЫТКИ:\n"+details
            }
        }
    }

    private fun compare() = task {
        ConnectivityRepository(applicationContext).use { it.comparison(host) }
    }

    private fun weekReport() = task {
        ConnectivityRepository(applicationContext).use { it.lastSevenDays(host) }
    }

    private fun exportCsv() {
        if(testing) return show("Остановите тест перед экспортом.")
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="text/csv"
                putExtra(Intent.EXTRA_TITLE,"legion-network-history-alpha20.csv")
            },EXPORT_CSV)
        }.onFailure { show("Документ не открыт: "+it.javaClass.simpleName) }
    }

    private fun share() {
        val id=selectedRunId ?: return show("Выберите запись для передачи.")
        task {
            ConnectivityRepository(applicationContext).use { db ->
                val text=ConnectivityAnalytics.render(db.summary(id))
                runOnUiThread {
                    if(!isFinishing && !isDestroyed) {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type="text/plain"
                            putExtra(Intent.EXTRA_TEXT,text)
                        },"Поделиться сетевым отчётом"))
                    }
                }
                text
            }
        }
    }

    private fun confirmDelete() {
        val id=selectedRunId ?: return show("Сначала выберите запись.")
        if(testing) return show("Остановите тест перед удалением.")
        AlertDialog.Builder(this).setTitle("Удалить эту запись?")
            .setMessage("Удалятся только этот сетевой тест и его DNS/TCP/TLS замеры. "+
                "Другие записи, игровые сессии и профили сохранятся.")
            .setPositiveButton("Удалить") { _,_ ->
                task {
                    val deleted=ConnectivityRepository(applicationContext).use { it.delete(id) }
                    if(deleted) {
                        selectedRunId=null
                        "Сетевой тест удалён. История других модулей не изменена."
                    } else "Запись уже отсутствует."
                }
            }.setNegativeButton("Отмена",null).show()
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=EXPORT_CSV || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        task {
            ConnectivityRepository(applicationContext).use { db ->
                (contentResolver.openOutputStream(uri,"w") ?: error("No output stream")).use { stream ->
                    OutputStreamWriter(stream,Charsets.UTF_8).use { writer ->
                        db.writeCsv(writer)
                    }
                }
            }
            "Вся история Connectivity Observatory экспортирована CSV. "+
                "Никаких IP адресов, SSID или данных чужих приложений не сохранялось."
        }
    }

    override fun onStop() {
        cancelTest(false)
        super.onStop()
    }

    override fun onDestroy() {
        cancelTest(false)
        worker.shutdownNow()
        super.onDestroy()
    }
}
