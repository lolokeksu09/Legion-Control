package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Battery-temperature timeline for the user's most recent recorded session. */
class GameSessionTimelineView(context: Context) : View(context) {
    private val frame = LegionChartFrame(this)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=LegionUiSupport.ACCENT
        strokeWidth=frame.lineWidth
    }
    private val warning = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=LegionUiSupport.DANGER
        style=Paint.Style.FILL
    }
    init {
        background=LegionUiSupport.cardSurface(this)
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription="Температура батареи: нет данных."
    }
    private var samples: List<GameTelemetryPoint> = emptyList()

    fun setPoints(points: List<GameTelemetryPoint>) {
        samples=points.filter { it.batteryTempDeciC in -400..1200 }
        contentDescription=if(samples.isEmpty()) "Температура батареи: нет данных."
        else "Температура батареи: от ${samples.minOf { it.batteryTempDeciC!! }/10.0} до ${samples.maxOf { it.batteryTempDeciC!! }/10.0} °C. Замеров: ${samples.size}."
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(samples.isEmpty()) { frame.empty(canvas); return }
        val minC=samples.minOf { it.batteryTempDeciC!! }/10.0
        val maxC=samples.maxOf { it.batteryTempDeciC!! }/10.0
        val start=samples.first().timeMs
        val span=(samples.last().timeMs-start).coerceAtLeast(1)
        val plot=frame.axes(canvas,minC,maxC,span,"°C") ?: return
        fun x(p:GameTelemetryPoint)=plot.left+plot.width()*(p.timeMs-start).toFloat()/span
        fun y(p:GameTelemetryPoint)=frame.y(plot,p.batteryTempDeciC!!/10.0,minC,maxC)

        // Reduce rendering while preserving the first, last and extrema in every bucket.
        val bucket=(samples.size/250).coerceAtLeast(1)
        val reduced=samples.chunked(bucket).flatMap { block ->
            if(block.size==1) block else listOf(block.first(),
                block.minBy { it.batteryTempDeciC!! },block.maxBy { it.batteryTempDeciC!! },
                block.last()).distinct().sortedBy { it.timeMs }
        }.distinctBy { it.timeMs }
        for(i in 1 until reduced.size) {
            canvas.drawLine(x(reduced[i-1]),y(reduced[i-1]),x(reduced[i]),y(reduced[i]),line)
        }
        if(reduced.size == 1) canvas.drawCircle(x(reduced[0]),y(reduced[0]),frame.pointRadius,line)
        reduced.filter { (it.thermalStatus ?: -1)>=3 }.forEach {
            canvas.drawCircle(x(it),y(it),4f*resources.displayMetrics.density,warning)
        }
    }
}
