package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

enum class CompareGraph(val label:String,val unit:String) {
    TEMP("Температура батареи","°C"),
    BATTERY("Заряд","%"),
    DISPLAY("Частота дисплея","Гц");
    fun value(p:GameTelemetryPoint):Double?=when(this) {
        TEMP -> p.batteryTempDeciC?.takeIf { it in -400..1200 }?.div(10.0)
        BATTERY -> p.batteryPercent?.takeIf { it in 0..100 }?.toDouble()
        DISPLAY -> p.refreshHz?.takeIf { it.isFinite() && it in 1f..500f }?.toDouble()
    }
}

/** Shared scales and elapsed time; no interpolation over missing data or >45 s gaps. */
class SessionCompareChart(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame=LegionChartFrame(this)
    private var pair:ComparePair?=null
    private var metric=CompareGraph.TEMP
    init {
        background=LegionUiSupport.cardSurface(this)
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun update(p:ComparePair?,m:CompareGraph) { pair=p;metric=m;invalidate() }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val p=pair ?: run { frame.empty(canvas); return }
        val values=(p.a.series+p.b.series).mapNotNull(metric::value)
        if(values.isEmpty()) { frame.empty(canvas); return }
        val lo=values.minOrNull() ?: return
        val hi=values.maxOrNull() ?: return
        val plot=frame.axes(canvas,lo,hi,p.a.minutes*60000L,metric.unit) ?: return
        fun draw(w:CompareWindow,color:Int) {
            paint.color=color
            paint.strokeWidth=frame.lineWidth
            var previous:GameTelemetryPoint?=null
            var px=0f;var py=0f
            for(point in w.series) {
                val v=metric.value(point)
                if(v==null) { previous=null;continue }
                val x=plot.left+plot.width()*((point.timeMs-w.header.startedAtMs).toDouble()/(w.minutes*60000L)).toFloat()
                val y=frame.y(plot,v,lo,hi)
                val prev=previous
                if(prev!=null && point.timeMs-prev.timeMs in 1..45000L)
                    canvas.drawLine(px,py,x,y,paint)
                canvas.drawCircle(x,y,frame.pointRadius,paint)
                previous=point;px=x;py=y
            }
        }
        draw(p.a,LegionUiSupport.ACCENT)
        draw(p.b,LegionUiSupport.WARNING)
    }
}
