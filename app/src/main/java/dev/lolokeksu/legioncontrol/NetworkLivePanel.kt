package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import java.util.UUID
import java.text.DateFormat
import java.util.Date
import android.net.Uri
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/** Separate live mode inside the existing Observatory, with bounded visible-only work. */
internal class NetworkLivePanel(context:Context,private val exportRequest:(String)->Unit,private val onRunningChanged:()->Unit):LinearLayout(context) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val io=Executors.newSingleThreadExecutor()
    private var ioBusy=false
    private var visible=false
    private var plan=LiveNetworkPlan.NORMAL
    private var ipMode=LiveIpMode.AUTO
    private var chartMode=0
    private var activeId=""
    private var activeHost=""
    private var activeStarted=0L
    private var record:LiveNetworkRecord?=null
    private var saved=false
    private val token=AtomicInteger()
    private var future:Future<*>?=null
    private val points=mutableListOf<LiveNetworkPoint>()
    var running=false
        private set
    private var legacyBusy=false
    private var closed=false
    private val dp:Float get()=resources.displayMetrics.density
    private val target=EditText(context).apply {
        hint="Домен или IPv4"
        setText("one.one.one.one")
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        maxLines=1
        isSingleLine=true
    }
    private val status=TextView(context).apply {
        text="Запустите тест. После завершения можно сохранить результат в локальный архив."
        textSize=13f
        LegionUiSupport.reportSurface(this)
    }
    private val tcpChart=LiveLatencyChart(context,false)
    private val dnsChart=LiveLatencyChart(context,false,true)
    private val chartToggle=Button(context).apply { text="График: TCP"; isAllCaps=false; setOnClickListener { chooseChart() } }
    private val gatewayChart=LiveLatencyChart(context,true)
    private val ipButton=Button(context).apply { text=ipMode.label; isAllCaps=false; setOnClickListener { chooseIpMode() } }
    private val planButton=Button(context).apply { text=plan.label; isAllCaps=false; setOnClickListener { choosePlan() } }
    private val start=Button(context).apply { text="Начать тест"; isAllCaps=false; setOnClickListener { begin() } }
    private val stop=Button(context).apply { text="Остановить тест"; isAllCaps=false; isEnabled=false; setOnClickListener { stop() } }

    private val archiveStatus=TextView(context).apply { text="Сохранение вручную. Автоудаления нет."; textSize=13f }
    private val saveButton=Button(context).apply { text="Сохранить"; isAllCaps=false; setOnClickListener { saveRecord() } }
    private val historyButton=Button(context).apply { text="История"; isAllCaps=false; setOnClickListener { history() } }
    private val compareButton=Button(context).apply { text="Сравнить A/B"; isAllCaps=false; setOnClickListener { compareRecord() } }
    private val exportButton=Button(context).apply { text="CSV"; isAllCaps=false; setOnClickListener { record?.let { exportRequest(LiveNetworkReports.csv(it)) } } }
    private val deleteButton=Button(context).apply { text="Удалить запись"; isAllCaps=false; setOnClickListener { deleteRecord() } }

    init {
        orientation=VERTICAL
        addView(LegionUiSupport.sectionLabel(context,"ЖИВАЯ ДИАГНОСТИКА СЕТИ"))
        addView(TextView(context).apply {
            text="TCP :443 к указанному узлу; он увидит ваш IP. Wi-Fi шлюз: доступность через Android, не гарантированный ICMP. Не игровой ping."
            textSize=13f
        })
        addView(target)
        addView(CompactActionRow(context,planButton,ipButton))
        addView(CompactActionRow(context,start,stop))
        addView(LegionUiSupport.sectionLabel(context,"ГРАФИК ИЗМЕРЕНИЙ"))
        addView(chartToggle)
        val chartHeight=(156*dp).toInt()
        addView(tcpChart,LayoutParams(LayoutParams.MATCH_PARENT,chartHeight))
        addView(dnsChart,LayoutParams(LayoutParams.MATCH_PARENT,chartHeight))
        addView(gatewayChart,LayoutParams(LayoutParams.MATCH_PARENT,chartHeight))
        selectChart(0)
        addView(status)
        addView(LegionUiSupport.sectionLabel(context,"АРХИВ · ДО 50 ЗАПИСЕЙ"))
        addView(archiveStatus)
        addView(CompactActionRow(context,saveButton,historyButton))
        addView(CompactActionRow(context,compareButton,exportButton))
        addView(deleteButton)
        buttons()
    }
    fun setLegacyBusy(busy:Boolean) { legacyBusy=busy; buttons() }
    private fun buttons() {
        val ready=visible && !running && !legacyBusy && !ioBusy && !closed
        start.isEnabled=ready
        target.isEnabled=ready
        planButton.isEnabled=ready
        ipButton.isEnabled=ready
        stop.isEnabled=running
        start.visibility=if(running) View.GONE else View.VISIBLE
        stop.visibility=if(running) View.VISIBLE else View.GONE
        saveButton.isEnabled=ready && record!=null && !saved
        historyButton.isEnabled=ready
        compareButton.isEnabled=ready && record?.end==LiveNetworkEnd.COMPLETE
        exportButton.isEnabled=ready && record!=null
        deleteButton.isEnabled=ready && saved && record!=null
    }
    private fun begin() {
        if(!start.isEnabled)return
        if(record!=null && !saved) {
            LegionDialogs.builder(context).setTitle("Начать новый сетевой тест?")
                .setMessage("Несохранённые измерения текущего теста будут заменены.")
                .setPositiveButton("Начать") { _,_->startRun() }.setNegativeButton("Отмена",null).show()
        } else startRun()
    }
    private fun startRun() {
        if(!start.isEnabled)return
        val host=LiveNetworkPolicy.host(target.text.toString())
        if(host==null) { target.error="Введите домен или IPv4 без URL и порта"; return }
        target.error=null
        activeId=UUID.randomUUID().toString(); activeHost=host; activeStarted=System.currentTimeMillis().coerceAtLeast(1L)
        val selectedPlan=plan
        val selectedIp=ipMode
        record=null; saved=false
        points.clear()
        updateCharts(emptyList())
        val id=token.incrementAndGet()
        running=true; buttons(); onRunningChanged()
        status.text="Начинается тест: "+host+":443"
        future=worker.submit {
            try {
                val probe=LiveNetworkProbe(context.applicationContext)
                val begin=SystemClock.elapsedRealtime()
                repeat(selectedPlan.count) { index ->
                    if(token.get()!=id || Thread.currentThread().isInterrupted) return@submit
                    val point=probe.capture(host,SystemClock.elapsedRealtime()-begin,selectedIp) { token.get()!=id }
                    main.post {
                        if(!closed && token.get()==id && running) {
                            points.add(point); render("Проба "+(index+1)+"/"+selectedPlan.count)
                        }
                    }
                    if(index<selectedPlan.count-1) Thread.sleep(selectedPlan.intervalMs)
                }
                main.post { if(!closed && token.get()==id) finish("Завершено",LiveNetworkEnd.COMPLETE) }
            } catch (_:InterruptedException) {
                // Stop invalidates queued callbacks before cancellation.
            } catch (e:Exception) {
                main.post { if(!closed && token.get()==id) finish("Ошибка: "+e.javaClass.simpleName,LiveNetworkEnd.FAILED) }
            }
        }
    }
    private fun render(prefix:String) {
        val s=LiveNetworkPolicy.summarize(points)
        fun fmt(v:Double?)=v?.let { String.format(Locale.US,"%.1f",it) } ?: "н/д"
        val p=points.lastOrNull()
        fun state(value:String?)=when(value) {
            "OK" -> "успех"
            "TCP_ERROR" -> "ошибка TCP"
            "DNS_ERROR" -> "ошибка DNS"
            "NO_NETWORK" -> "нет сети"
            "NO_ADDRESS" -> "нет адреса выбранного семейства"
            "SWITCHED" -> "сеть изменилась"
            "NO_REPLY" -> "нет ответа"
            "UNAVAILABLE" -> "недоступно"
            else -> "—"
        }
        val prior=points.getOrNull(points.lastIndex-1)
        val delta=if(p!=null && prior!=null && LiveNetworkPolicy.connected(prior,p) &&
            LiveNetworkPolicy.valid(p.tcpMs) && LiveNetworkPolicy.valid(prior.tcpMs)) p.tcpMs!!-prior.tcpMs!! else null
        status.text=prefix+" · "+(p?.network ?: "—")+"\n"+
            "TCP: "+fmt(p?.tcpMs)+" мс · "+state(p?.tcpState)+" · "+(p?.family ?: "—")+"\n"+
            "DNS: "+fmt(p?.dnsMs)+" мс · Android сеть: "+(when(p?.validated){true->"подтверждена";false->"не подтверждена";null->"н/д"})+"\n"+
            "Шлюз: "+(p?.gatewayAddress ?: "н/д")+" · "+fmt(p?.gatewayMs)+" мс · "+state(p?.gatewayState)+"\n"+
            "Изменение TCP между соседними пробами: "+fmt(delta)+" мс\n"+
            "Джиттер TCP: "+fmt(s.jitterMs)+" мс · ошибки: "+fmt(s.failurePercent)+"%\n"+
            "Успех / ошибка / недоступно: "+s.successful+" / "+s.failed+" / "+s.unavailable+
            " · смены сети: "+s.switches+"\n"+
            LiveNetworkInsights.describe(points)+"\n"+
            "Ошибки проб не равны потерям IP-пакетов. Нет ответа шлюза — не доказательство поломки."
        updateCharts(points.toList())
    }
    private fun freeze(end:LiveNetworkEnd) {
        record=points.takeIf { it.isNotEmpty() }?.let {
            LiveNetworkRecord(activeId,activeHost,activeStarted,plan,end,it.toList(),ipMode)
        }
        saved=false
    }
    private fun finish(label:String,end:LiveNetworkEnd) {
        running=false; future=null; freeze(end); buttons(); render(label); onRunningChanged()
        archiveStatus.text=if(record==null) "Нет завершённых проб для сохранения." else "Тест готов: сохраните в архив или экспортируйте CSV."
    }
    private fun choosePlan() {
        if(!start.isEnabled) return
        val options=LiveNetworkPlan.entries
        LegionDialogs.builder(context).setTitle("Режим живой диагностики")
            .setItems(options.map{it.label}.toTypedArray()) { _,i->plan=options[i];planButton.text=plan.label }
            .setNegativeButton("Отмена",null).show()
    }
    private fun chooseIpMode() {
        if(!start.isEnabled) return
        val options=LiveIpMode.entries
        LegionDialogs.builder(context).setTitle("Семейство адресов")
            .setItems(options.map{it.label}.toTypedArray()) { _,i->ipMode=options[i];ipButton.text=ipMode.label }
            .setNegativeButton("Отмена",null).show()
    }
    private fun chooseChart() {
        val labels=arrayOf("TCP","DNS","Wi-Fi шлюз")
        LegionDialogs.builder(context).setTitle("График измерений")
            .setItems(labels){_,i->selectChart(i)}
            .setNegativeButton("Отмена",null).show()
    }
    private fun selectChart(index:Int) {
        chartMode=index
        tcpChart.visibility=if(chartMode==0) View.VISIBLE else View.GONE
        dnsChart.visibility=if(chartMode==1) View.VISIBLE else View.GONE
        gatewayChart.visibility=if(chartMode==2) View.VISIBLE else View.GONE
        chartToggle.text="График: "+arrayOf("TCP","DNS","Wi-Fi шлюз")[chartMode]
    }
    private fun updateCharts(data:List<LiveNetworkPoint>) {
        tcpChart.update(data);dnsChart.update(data);gatewayChart.update(data)
    }
    fun screenStarted() { visible=true;buttons() }
    fun screenStopped() { visible=false; stop();buttons() }

    private fun <T> archiveTask(work:()->T,deliverWhenHidden:Boolean=false,success:(T)->Unit) {
        if(closed || ioBusy || running || legacyBusy || !visible) return
        ioBusy=true; buttons()
        io.execute {
            val result=runCatching(work)
            main.post {
                if(!closed) {
                    ioBusy=false; buttons()
                    result.onSuccess { if(visible || deliverWhenHidden) success(it) }
                        .onFailure { archiveStatus.text="Ошибка архива: "+(it.message?.take(140) ?: it.javaClass.simpleName) }
                }
            }
        }
    }
    private fun <T> withArchive(work:(LiveNetworkArchive)->T):T {
        val archive=LiveNetworkArchive(context.applicationContext)
        return try { work(archive) } finally { archive.close() }
    }
    private fun saveRecord() {
        val snapshot=record ?: return
        archiveTask({ withArchive{it.save(snapshot)} },deliverWhenHidden=true) {
            saved=true; buttons(); archiveStatus.text="Сохранено · "+snapshot.host
        }
    }
    private fun label(r:LiveNetworkRecord)=DateFormat.getDateTimeInstance().format(Date(r.startedAt))+
        " · "+r.host+" · "+r.ipMode.name+" · "+r.points.size+"/"+r.plan.count+" · "+r.end
    private fun history() {
        archiveTask({withArchive{it.list()}}) { records->
            if(records.isEmpty()) { archiveStatus.text="Архив пуст."; return@archiveTask }
            LegionDialogs.builder(context).setTitle("Архив · "+records.size+"/50")
                .setItems(records.map(::label).toTypedArray()) { _,i->
                    if(!running && !ioBusy && !legacyBusy) {
                        val r=records[i]
                        fun open() {
                            if(!visible || running || ioBusy || legacyBusy || closed)return
                            record=r; saved=true
                            points.clear(); points.addAll(r.points)
                            status.text=LiveNetworkReports.report(r)
                            updateCharts(r.points)
                            archiveStatus.text="Открыта запись: "+label(r)
                            buttons()
                        }
                        if(record!=null && !saved) LegionDialogs.builder(context).setTitle("Открыть запись?")
                            .setMessage("Несохранённый текущий тест будет заменён.")
                            .setPositiveButton("Открыть") { _,_->open() }.setNegativeButton("Отмена",null).show()
                        else open()
                    }
                }.setNegativeButton("Отмена",null).show()
        }
    }
    private fun compareRecord() {
        val a=record ?: return
        archiveTask({withArchive{it.list()}.filter{LiveNetworkReports.comparable(a,it)}}) { choices->
            if(choices.isEmpty()) { archiveStatus.text="Нет другого завершённого теста того же узла и режима."; return@archiveTask }
            LegionDialogs.builder(context).setTitle("Выберите тест B")
                .setItems(choices.map(::label).toTypedArray()) { _,i->
                    LegionDialogs.builder(context).setTitle("Сравнение A/B")
                        .setMessage(LiveNetworkReports.compare(a,choices[i]))
                        .setPositiveButton("Закрыть",null).show()
                }.setNegativeButton("Отмена",null).show()
        }
    }
    private fun deleteRecord() {
        val r=record ?: return
        if(!saved) return
        LegionDialogs.builder(context).setTitle("Удалить сетевой тест?")
            .setMessage(label(r)+"\nДругие записи и старая история DNS/TLS не изменятся.")
            .setDestructive().setPositiveButton("Удалить") { _,_->
                if(!deleteButton.isEnabled || record?.id!=r.id) return@setPositiveButton
                archiveTask({withArchive{it.delete(r.id)}},deliverWhenHidden=true) { removed->
                    if(removed) {
                        record=null; saved=false; points.clear()
                        updateCharts(emptyList())
                        status.text="Запись удалена."; archiveStatus.text="Удалена одна запись."
                    } else archiveStatus.text="Запись уже отсутствует."
                    buttons()
                }
            }.setNegativeButton("Отмена",null).show()
    }
    fun writeCsv(uri:Uri,csv:String) {
        if(closed || ioBusy || running) {
            archiveStatus.text="Экспорт не выполнен: повторите сохранение CSV."
            return
        }
        ioBusy=true;buttons()
        io.execute {
            val result=runCatching {
                require(csv.length<=100_000)
                val stream=requireNotNull(context.applicationContext.contentResolver.openOutputStream(uri,"wt"))
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
            }
            main.post { if(!closed) {
                ioBusy=false
                archiveStatus.text=if(result.isSuccess) "CSV сохранён в выбранный документ." else "Ошибка экспорта CSV."
                buttons()
            } }
        }
    }
    fun stop() {
        if(!running) return
        token.incrementAndGet()
        future?.cancel(true); future=null
        running=false; freeze(LiveNetworkEnd.CANCELLED); buttons(); render("Остановлено"); onRunningChanged()
        archiveStatus.text=if(record==null) "Нет завершённых проб." else "Частичный тест можно сохранить."
    }
    fun close() {
        stop()
        closed=true; token.incrementAndGet(); worker.shutdownNow(); io.shutdown(); buttons()
    }
}

private class LiveLatencyChart(context:Context,private val gateway:Boolean,private val dns:Boolean=false):View(context) {
    private val frame=LegionChartFrame(this)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=if(gateway) 0xFF80CBC4.toInt() else 0xFF90CAF9.toInt()
        strokeWidth=frame.lineWidth
    }
    private var data:List<LiveNetworkPoint> = emptyList()
    private fun value(p:LiveNetworkPoint)=when { gateway -> p.gatewayMs; dns -> p.dnsMs; else -> p.tcpMs }
    fun update(points:List<LiveNetworkPoint>) {
        data=points
        contentDescription=(if(gateway) "Доступность шлюза" else if(dns) "Время DNS" else "TCP задержка")+
            ": "+points.count { LiveNetworkPolicy.valid(value(it)) }+" успешных измерений"
        invalidate()
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val good=data.mapNotNull { value(it)?.takeIf { v -> LiveNetworkPolicy.valid(v) } }
        if(good.isEmpty()) { frame.empty(canvas); return }
        val duration=data.last().offsetMs.coerceAtLeast(1L)
        val hi=good.max().coerceAtLeast(1.0)
        val plot=frame.axes(canvas,0.0,hi,duration,"мс") ?: return
        var previous:LiveNetworkPoint?=null
        for(p in data) {
            val v=value(p)
            if(v==null || !LiveNetworkPolicy.valid(v)) { previous=null; continue }
            val x=plot.left+plot.width()*(p.offsetMs.toDouble()/duration).toFloat()
            val y=frame.y(plot,v,0.0,hi)
            val a=previous
            if(a!=null && (if(gateway) a.route!=null && a.route==p.route && a.network==p.network && a.gatewayKey!=null && a.gatewayKey==p.gatewayKey else if(dns) a.route!=null && a.route==p.route && a.network==p.network else LiveNetworkPolicy.connected(a,p))) {
                val av=value(a)!!
                val ax=plot.left+plot.width()*(a.offsetMs.toDouble()/duration).toFloat()
                canvas.drawLine(ax,frame.y(plot,av,0.0,hi),x,y,paint)
            }
            canvas.drawCircle(x,y,frame.pointRadius,paint)
            previous=p
        }
    }
}
