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
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/** Separate live mode inside the existing Observatory, with bounded visible-only work. */
internal class NetworkLivePanel(context:Context,private val onRunningChanged:()->Unit):LinearLayout(context) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
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
        text="Нажмите запуск для измерений. Данные живого теста хранятся только в памяти."
        textSize=13f
        LegionUiSupport.reportSurface(this)
    }
    private val tcpChart=LiveLatencyChart(context,false)
    private val gatewayChart=LiveLatencyChart(context,true)
    private val start=Button(context).apply { text="Запустить · до 20 проб"; isAllCaps=false; setOnClickListener { begin() } }
    private val stop=Button(context).apply { text="Остановить живой тест"; isAllCaps=false; isEnabled=false; setOnClickListener { stop() } }

    init {
        orientation=VERTICAL
        addView(LegionUiSupport.sectionLabel(context,"ЖИВАЯ ДИАГНОСТИКА СЕТИ"))
        addView(TextView(context).apply {
            text="TCP :443 к указанному узлу; он увидит ваш IP. Wi-Fi шлюз: доступность через Android, не гарантированный ICMP. Не игровой ping."
            textSize=13f
        })
        addView(target)
        addView(start)
        addView(stop)
        addView(status)
        addView(LegionUiSupport.sectionLabel(context,"TCP · внешний узел"))
        addView(tcpChart,LayoutParams(LayoutParams.MATCH_PARENT,(150*dp).toInt()))
        addView(LegionUiSupport.sectionLabel(context,"WI-FI · доступность шлюза"))
        addView(gatewayChart,LayoutParams(LayoutParams.MATCH_PARENT,(150*dp).toInt()))
    }
    fun setLegacyBusy(busy:Boolean) { legacyBusy=busy; buttons() }
    private fun buttons() {
        start.isEnabled=!running && !legacyBusy && !closed
        target.isEnabled=!running && !closed
        stop.isEnabled=running
    }
    private fun begin() {
        if(running || legacyBusy || closed) return
        val host=LiveNetworkPolicy.host(target.text.toString())
        if(host==null) { target.error="Введите домен или IPv4 без URL и порта"; return }
        target.error=null
        points.clear()
        tcpChart.update(emptyList()); gatewayChart.update(emptyList())
        val id=token.incrementAndGet()
        running=true; buttons(); onRunningChanged()
        status.text="Начинается тест: "+host+":443"
        future=worker.submit {
            try {
                val probe=LiveNetworkProbe(context.applicationContext)
                val begin=SystemClock.elapsedRealtime()
                repeat(20) { index ->
                    if(token.get()!=id || Thread.currentThread().isInterrupted) return@submit
                    val point=probe.capture(host,SystemClock.elapsedRealtime()-begin)
                    main.post {
                        if(!closed && token.get()==id && running) {
                            points.add(point); render("Проба "+(index+1)+"/20")
                        }
                    }
                    if(index<19) Thread.sleep(1000)
                }
                main.post { if(!closed && token.get()==id) finish("Завершено") }
            } catch (_:InterruptedException) {
                // Stop invalidates queued callbacks before cancellation.
            } catch (e:Exception) {
                main.post { if(!closed && token.get()==id) finish("Ошибка: "+e.javaClass.simpleName) }
            }
        }
    }
    private fun render(prefix:String) {
        val s=LiveNetworkPolicy.summarize(points)
        fun fmt(v:Double?)=v?.let { String.format(Locale.US,"%.1f",it) } ?: "н/д"
        val p=points.lastOrNull()
        status.text=prefix+" · "+(p?.network ?: "—")+"\n"+
            "TCP: "+fmt(p?.tcpMs)+" мс · "+(p?.tcpState ?: "—")+"\n"+
            "Шлюз: "+fmt(p?.gatewayMs)+" мс · "+(p?.gatewayState ?: "—")+"\n"+
            "Джиттер TCP: "+fmt(s.jitterMs)+" мс · ошибки: "+fmt(s.failurePercent)+"%\n"+
            "Успех / ошибка / недоступно: "+s.successful+" / "+s.failed+" / "+s.unavailable+
            " · смены сети: "+s.switches+"\n"+
            "Ошибки проб не равны потерям IP-пакетов. Нет ответа шлюза — не доказательство поломки."
        tcpChart.update(points.toList()); gatewayChart.update(points.toList())
    }
    private fun finish(label:String) { running=false; future=null; buttons(); render(label); onRunningChanged() }
    fun stop() {
        if(!running) return
        token.incrementAndGet()
        future?.cancel(true); future=null
        running=false; buttons(); render("Остановлено"); onRunningChanged()
    }
    fun close() {
        stop()
        closed=true; token.incrementAndGet(); worker.shutdownNow(); buttons()
    }
}

private class LiveLatencyChart(context:Context,private val gateway:Boolean):View(context) {
    private val frame=LegionChartFrame(this)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=if(gateway) 0xFF80CBC4.toInt() else 0xFF90CAF9.toInt()
        strokeWidth=frame.lineWidth
    }
    private var data:List<LiveNetworkPoint> = emptyList()
    private fun value(p:LiveNetworkPoint)=if(gateway) p.gatewayMs else p.tcpMs
    fun update(points:List<LiveNetworkPoint>) {
        data=points
        contentDescription=(if(gateway) "Доступность шлюза" else "TCP задержка")+
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
            if(a!=null && (if(gateway) a.route!=null && a.route==p.route && a.network==p.network else LiveNetworkPolicy.connected(a,p))) {
                val av=value(a)!!
                val ax=plot.left+plot.width()*(a.offsetMs.toDouble()/duration).toFloat()
                canvas.drawLine(ax,frame.y(plot,av,0.0,hi),x,y,paint)
            }
            canvas.drawCircle(x,y,frame.pointRadius,paint)
            previous=p
        }
    }
}
