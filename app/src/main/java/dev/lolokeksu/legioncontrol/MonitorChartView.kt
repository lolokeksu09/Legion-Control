package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.max

/** Actual sample timestamps; no smoothing or interpolated values over missing samples. */
class MonitorChartView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = LegionChartFrame(this)
    private var points = emptyList<MonitorPoint>()
    private var metric = MonitorMetric.TEMPERATURE

    init {
        background = LegionUiSupport.cardSurface(this)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // A separate accessible text summary exposes metric, extrema and sample coverage.
    }

    fun update(data: List<MonitorPoint>, selected: MonitorMetric) {
        points = data
        metric = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val values = points.mapNotNull(metric::value)
        if (values.isEmpty()) { frame.empty(canvas); return }
        val lo = values.minOrNull() ?: return
        val hi = values.maxOrNull() ?: return
        val first = points.first().elapsedMs
        val duration = max(1L, points.last().elapsedMs - first)
        val plot = frame.axes(canvas, lo, hi, duration, metric.unit) ?: return
        var previousX: Float? = null
        var previousY: Float? = null
        paint.color = LegionUiSupport.ACCENT
        paint.strokeWidth = frame.lineWidth
        for (point in points) {
            val value = metric.value(point)
            if (value == null || !value.isFinite()) {
                previousX = null
                previousY = null
                continue
            }
            val x = plot.left + plot.width() * ((point.elapsedMs - first).toDouble() / duration).toFloat()
            val y = frame.y(plot, value, lo, hi)
            val px = previousX
            val py = previousY
            if (px != null && py != null) canvas.drawLine(px, py, x, y, paint)
            canvas.drawCircle(x, y, frame.pointRadius, paint)
            previousX = x
            previousY = y
        }
    }
}
