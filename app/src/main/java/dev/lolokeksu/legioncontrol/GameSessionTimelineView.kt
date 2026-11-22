package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.Locale

/** Battery-temperature timeline for the user's most recent recorded session. */
class GameSessionTimelineView(context: Context) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.CYAN
        strokeWidth=3f*resources.displayMetrics.density
        style=Paint.Style.STROKE
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=0xFF363636.toInt()
        strokeWidth=resources.displayMetrics.density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.LTGRAY
        textSize=12f*resources.displayMetrics.scaledDensity
    }
    private val warning = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=0xFFFF7360.toInt()
        style=Paint.Style.FILL
    }
    private var samples: List<GameTelemetryPoint> = emptyList()

    fun setPoints(points: List<GameTelemetryPoint>) {
        samples=points.filter { it.batteryTempDeciC in -400..1200 }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val left=54f*resources.displayMetrics.density
        val right=width-14f*resources.displayMetrics.density
        val top=26f*resources.displayMetrics.density
        val bottom=height-30f*resources.displayMetrics.density
        if(right<=left || bottom<=top) return
        canvas.drawText("ТЕМПЕРАТУРА БАТАРЕИ · °C",left,top-8f*resources.displayMetrics.density,label)
        if (samples.size < 2) {
            canvas.drawText("Недостаточно замеров для графика",left,top+36f,label)
            return
        }
        val minC=(samples.minOf { it.batteryTempDeciC!! }/10.0)
        val maxC=(samples.maxOf { it.batteryTempDeciC!! }/10.0)
        val range=(maxC-minC).coerceAtLeast(1.0)
        for(i in 0..3) {
            val y=top+(bottom-top)*i/3
            canvas.drawLine(left,y,right,y,grid)
            val value=maxC-range*i/3
            canvas.drawText(String.format(Locale.US,"%.1f",value),3f,y+4f,label)
        }
        val start=samples.first().timeMs
        val span=(samples.last().timeMs-start).coerceAtLeast(1)
        fun x(p: GameTelemetryPoint)=left+(right-left)*(p.timeMs-start).toFloat()/span
        fun y(p: GameTelemetryPoint)=bottom-(bottom-top)*((p.batteryTempDeciC!!/10.0-minC)/range).toFloat()

        // Reduce rendering while preserving the first, last and extrema in every bucket.
        val bucket=(samples.size/250).coerceAtLeast(1)
        val plot=samples.chunked(bucket).flatMap { block ->
            if(block.size==1) block else listOf(block.first(),
                block.minBy { it.batteryTempDeciC!! },block.maxBy { it.batteryTempDeciC!! },
                block.last()).distinct().sortedBy { it.timeMs }
        }.distinctBy { it.timeMs }
        for(i in 1 until plot.size) {
            canvas.drawLine(x(plot[i-1]),y(plot[i-1]),x(plot[i]),y(plot[i]),line)
        }
        plot.filter { (it.thermalStatus ?: -1)>=3 }.forEach {
            canvas.drawCircle(x(it),y(it),4f*resources.displayMetrics.density,warning)
        }
        canvas.drawText("0 мин",left,bottom+20f*resources.displayMetrics.density,label)
        canvas.drawText("${span/60_000} мин",right-44f*resources.displayMetrics.density,
            bottom+20f*resources.displayMetrics.density,label)
    }
}
