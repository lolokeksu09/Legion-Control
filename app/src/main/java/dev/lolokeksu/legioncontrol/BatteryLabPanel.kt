package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors

internal class BatteryLabPanel(context:Context,private val exportRequest:(String)->Unit):LinearLayout(context) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val io=Executors.newSingleThreadExecutor()
    private var visible=false
    private var closed=false
    private var running=false
    private var ioBusy=false
    private var token=0L
    private var plan=BatteryLabPlan.QUICK
    private var began=0L
    private var started=0L
    private var runId=""
    private var failures=0
    private var points=mutableListOf<BatteryLabPoint>()
    private var run:BatteryLabRun?=null
    private var saved=false
    private val tick=Runnable{sample()}
    private val report=TextView(context).apply{ text="Запустите наблюдение или откройте сохранённый тест.";textSize=13f;LegionUiSupport.reportSurface(this) }
    private val notice=TextView(context).apply{text="Ручной архив · до 30 записей";textSize=13f}
    private val chart=BatteryLabChart(context)
    private fun button(label:String,action:()->Unit)=Button(context).apply{text=label;isAllCaps=false;setOnClickListener{action()}}
    private val planButton=button("Длительность: "+plan.label){choosePlan()}
    private val startButton=button("Начать наблюдение"){begin()}
    private val stopButton=button("Остановить"){finish(BatteryLabEnd.USER_STOP)}
    private val chartButton=button("График: мощность"){chart.next();chartButtonLabel()}
    private val saveButton=button("Сохранить тест"){save()}
    private val historyButton=button("История Battery Lab"){history()}
    private val compareButton=button("Сравнить тесты A/B"){compare()}
    private val shareButton=button("Поделиться отчётом"){share()}
    private val exportButton=button("Экспорт CSV"){run?.let{exportRequest(BatteryLabReport.csv(it))}}
    private val deleteButton=button("Удалить сохранённый тест"){delete()}
    init {
        orientation=VERTICAL
        addView(LegionUiSupport.sectionLabel(context,"BATTERY LAB 2.0"))
        addView(TextView(context).apply{text="Наблюдение только на открытом экране. Мощность рассчитана по току и напряжению батареи; + заряд, − разряд. Не измерение адаптера.";textSize=13f})
        addView(planButton);addView(startButton);addView(stopButton);addView(report)
        addView(chartButton)
        addView(chart,LayoutParams(LayoutParams.MATCH_PARENT,(180*resources.displayMetrics.density).toInt()))
        addView(notice);addView(saveButton);addView(historyButton);addView(compareButton);addView(shareButton);addView(exportButton);addView(deleteButton)
        buttons()
    }
    private fun chartButtonLabel(){chartButton.text="График: "+chart.metric.label}
    fun setVisible(value:Boolean) { visible=value;if(!value) finish(BatteryLabEnd.BACKGROUND);buttons() }
    private fun buttons() {
        val ready=visible&&!closed&&!running&&!ioBusy
        planButton.isEnabled=ready;startButton.isEnabled=ready;stopButton.isEnabled=running
        saveButton.isEnabled=ready&&run!=null&&!saved
        historyButton.isEnabled=ready;compareButton.isEnabled=ready&&run?.end==BatteryLabEnd.COMPLETE
        shareButton.isEnabled=ready&&run!=null;exportButton.isEnabled=ready&&run!=null;deleteButton.isEnabled=ready&&saved&&run!=null
    }
    private fun choosePlan() {
        if(!startButton.isEnabled)return
        val list=BatteryLabPlan.entries
        LegionDialogs.builder(context).setTitle("Длительность наблюдения")
            .setItems(list.map{it.label}.toTypedArray()){_,i->plan=list[i];planButton.text="Длительность: "+plan.label}
            .setNegativeButton("Отмена",null).show()
    }
    private fun begin() {
        if(!startButton.isEnabled)return
        if(run!=null&&!saved) {
            LegionDialogs.builder(context).setTitle("Начать новый тест?")
                .setMessage("Несохранённые измерения текущего теста будут заменены.")
                .setPositiveButton("Начать"){_,_->startRun()}.setNegativeButton("Отмена",null).show()
        } else startRun()
    }
    private fun startRun() {
        if(!startButton.isEnabled)return
        token++;running=true;failures=0;points.clear();run=null;saved=false
        runId=UUID.randomUUID().toString();began=SystemClock.elapsedRealtime();started=System.currentTimeMillis().coerceAtLeast(1)
        report.text="Ожидание измерения…";chart.update(emptyList());buttons();sample()
    }
    private fun sample() {
        if(!visible||closed||!running)return
        val generation=token;val origin=began
        worker.execute {
            val elapsed=(SystemClock.elapsedRealtime()-origin).coerceAtLeast(0)
            val p=runCatching{BatteryLabSource.read(context.applicationContext,elapsed)}.getOrNull()
            main.post {
                if(generation!=token||!running||!visible||closed)return@post
                if(p==null){failures++}else{
                    failures=0
                    if(points.lastOrNull()?.let{p.elapsedMs>it.elapsedMs}!=false && points.size<121)points.add(p)
                    chart.update(points.toList());report.text=BatteryLabReport.report(points)
                }
                when {
                    p!=null&&BatteryLabPolicy.hot(p) -> finish(BatteryLabEnd.HOT)
                    failures>=3 -> finish(BatteryLabEnd.READ_FAILED)
                    elapsed>=plan.durationMs || points.size>=121 -> finish(BatteryLabEnd.COMPLETE)
                    else -> main.postDelayed(tick,BatteryLabPolicy.INTERVAL_MS)
                }
            }
        }
    }
    private fun finish(reason:BatteryLabEnd) {
        if(!running)return
        token++;running=false;main.removeCallbacks(tick)
        run=points.takeIf{it.isNotEmpty()}?.let{BatteryLabRun(runId,started,plan,reason,it.toList())}
        report.text=(when(reason){
            BatteryLabEnd.HOT -> "Остановка: батарея ≥45 °C или Android thermal SEVERE+."
            BatteryLabEnd.COMPLETE -> "Тест завершён."
            BatteryLabEnd.BACKGROUND -> "Остановлено при уходе с экрана."
            BatteryLabEnd.READ_FAILED -> "Остановлено: три ошибки чтения."
            BatteryLabEnd.USER_STOP -> "Остановлено пользователем."
        })+"\n"+BatteryLabReport.report(points)
        buttons()
    }
    private fun <T> archive(work:(BatteryLabArchive)->T,done:(T)->Unit) {
        if(closed||running||ioBusy||!visible)return
        ioBusy=true;buttons()
        io.execute {
            val result=runCatching{
                val db=BatteryLabArchive(context.applicationContext)
                try{work(db)}finally{db.close()}
            }
            main.post {
                if(!closed){ioBusy=false
                    result.onSuccess{if(visible)done(it)}.onFailure{notice.text="Ошибка архива: "+it.message.orEmpty().take(120)}
                    buttons()
                }
            }
        }
    }
    private fun label(r:BatteryLabRun)=DateFormat.getDateTimeInstance().format(Date(r.startedAt))+" · "+r.plan.label+" · "+r.points.first().plug+" · "+r.end
    private fun save() {
        val r=run?:return
        archive({it.save(r)}){saved=true;notice.text="Тест сохранён."}
    }
    private fun history() {
        archive({it.list()}){list->
            if(list.isEmpty()){notice.text="Архив пуст.";return@archive}
            LegionDialogs.builder(context).setTitle("История · "+list.size+"/30")
                .setItems(list.map(::label).toTypedArray()){_,i->
                    val selected=list[i]
                    fun open(){run=selected;saved=true;points=selected.points.toMutableList();chart.update(points);report.text=label(selected)+"\n"+BatteryLabReport.report(points);notice.text="Открыта запись.";buttons()}
                    if(run!=null&&!saved) LegionDialogs.builder(context).setTitle("Открыть запись?")
                        .setMessage("Несохранённый текущий тест будет заменён.").setPositiveButton("Открыть"){_,_->open()}
                        .setNegativeButton("Отмена",null).show() else open()
                }.setNegativeButton("Отмена",null).show()
        }
    }
    private fun compare() {
        val a=run?:return
        archive({it.list().filter{b->BatteryLabPolicy.comparable(a,b)}}){list->
            if(list.isEmpty()){notice.text="Нет сопоставимого завершённого теста: нужен тот же план, близкие начальные заряд/температура и ≥80% покрытия.";return@archive}
            LegionDialogs.builder(context).setTitle("Выберите тест B").setItems(list.map(::label).toTypedArray()){_,i->
                LegionDialogs.builder(context).setTitle("Сравнение A/B").setMessage(BatteryLabReport.compare(a,list[i])).setPositiveButton("Закрыть",null).show()
            }.setNegativeButton("Отмена",null).show()
        }
    }
    private fun share() {
        val r=run?:return
        val text=label(r)+"\n"+BatteryLabReport.report(r.points)
        runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Отправить отчёт"))}
            .onFailure{notice.text="Не удалось открыть меню отправки."}
    }
    private fun delete() {
        val r=run?:return
        if(!saved)return
        LegionDialogs.builder(context).setTitle("Удалить тест Battery Lab?")
            .setMessage("Удалится только выбранная запись.").setDestructive()
            .setPositiveButton("Удалить"){_,_->archive({it.delete(r.id)}){removed->
                if(removed){run=null;saved=false;points.clear();chart.update(emptyList());report.text="Запись удалена."}
                notice.text=if(removed)"Удалена одна запись." else "Запись отсутствует."
            }}.setNegativeButton("Отмена",null).show()
    }
    fun writeCsv(uri:android.net.Uri,csv:String) {
        if(closed||ioBusy||running)return
        ioBusy=true;buttons()
        io.execute {
            val result=runCatching {
                require(csv.length<=150_000)
                val stream=requireNotNull(context.applicationContext.contentResolver.openOutputStream(uri,"wt"))
                stream.bufferedWriter(Charsets.UTF_8).use{it.write(csv)}
            }
            main.post{if(!closed){ioBusy=false;notice.text=if(result.isSuccess)"CSV сохранён." else "Ошибка экспорта CSV.";buttons()}}
        }
    }
    fun close(){setVisible(false);closed=true;token++;worker.shutdownNow();io.shutdown();buttons()}
}
internal enum class BatteryLabMetric(val label:String,val unit:String) {
    POWER("мощность","Вт"), TEMPERATURE("температура","°C"), CURRENT("ток","мА"), CHARGE("заряд","%");
    fun value(p:BatteryLabPoint):Double?=when(this){
        POWER->p.watts;TEMPERATURE->p.tempDeciC?.takeIf{it in -400..1200}?.div(10.0);CURRENT->p.currentMicroA?.takeIf{it in -50_000_000..50_000_000}?.div(1000.0);CHARGE->p.percent?.takeIf{it in 0..100}?.toDouble()
    }
}
private class BatteryLabChart(context:Context):View(context) {
    private val frame=LegionChartFrame(this)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=LegionUiSupport.ACCENT;strokeWidth=frame.lineWidth}
    private var points=emptyList<BatteryLabPoint>()
    var metric=BatteryLabMetric.POWER
        private set
    fun next(){metric=BatteryLabMetric.entries[(metric.ordinal+1)%BatteryLabMetric.entries.size];update(points)}
    fun update(data:List<BatteryLabPoint>){points=data;contentDescription=metric.label+" · "+data.count{metric.value(it)!=null}+" измерений";invalidate()}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val values=points.mapNotNull(metric::value).filter{it.isFinite()}
        if(values.isEmpty()){frame.empty(canvas);return}
        val duration=points.last().elapsedMs.coerceAtLeast(1)
        val lo=values.min();val hi=values.max()
        val plot=frame.axes(canvas,lo,hi,duration,metric.unit)?:return
        var prev:BatteryLabPoint?=null
        for(p in points){
            val v=metric.value(p)
            if(v==null||!v.isFinite()){prev=null;continue}
            val x=plot.left+plot.width()*(p.elapsedMs.toDouble()/duration).toFloat();val y=frame.y(plot,v,lo,hi)
            val a=prev
            if(a!=null&&p.elapsedMs-a.elapsedMs in 1..BatteryLabPolicy.MAX_GAP_MS&&a.plug==p.plug&&a.status==p.status){
                val ax=plot.left+plot.width()*(a.elapsedMs.toDouble()/duration).toFloat()
                canvas.drawLine(ax,frame.y(plot,metric.value(a)!!,lo,hi),x,y,paint)
            }
            canvas.drawCircle(x,y,frame.pointRadius,paint);prev=p
        }
    }
}
