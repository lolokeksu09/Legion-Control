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
    private var pair:ComparePair?=null
    private var metric=CompareGraph.TEMP
    init {
        background=LegionUiSupport.cardSurface(this)
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun update(p:ComparePair?,m:CompareGraph) { pair=p;metric=m;invalidate() }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val p=pair ?: return
        val values=(p.a.series+p.b.series).mapNotNull(metric::value)
        if(values.isEmpty())return
        val lo=values.minOrNull() ?: return
        val hi=values.maxOrNull() ?: return
        val pad=16f*resources.displayMetrics.density
        val left=pad;val right=width-pad;val top=pad;val bottom=height-pad
        if(right<=left || bottom<=top)return
        paint.strokeWidth=resources.displayMetrics.density
        paint.color=LegionUiSupport.BORDER
        for(i in 0..4) {
            val y=top+(bottom-top)*i/4
            canvas.drawLine(left,y,right,y,paint)
        }
        fun draw(w:CompareWindow,color:Int) {
            paint.color=color
            paint.strokeWidth=2f*resources.displayMetrics.density
            var previous:GameTelemetryPoint?=null
            var px=0f;var py=0f
            for(point in w.series) {
                val v=metric.value(point)
                if(v==null) { previous=null;continue }
                val x=left+(right-left)*((point.timeMs-w.header.startedAtMs).toDouble()/(w.minutes*60000L)).toFloat()
                val y=if(hi==lo)(top+bottom)/2 else bottom-(bottom-top)*((v-lo)/(hi-lo)).toFloat()
                val prev=previous
                if(prev!=null && point.timeMs-prev.timeMs in 1..45000L)
                    canvas.drawLine(px,py,x,y,paint)
                canvas.drawCircle(x,y,2f*resources.displayMetrics.density,paint)
                previous=point;px=x;py=y
            }
        }
        draw(p.a,LegionUiSupport.ACCENT)
        draw(p.b,0xFFFFCF87.toInt())
    }
}
