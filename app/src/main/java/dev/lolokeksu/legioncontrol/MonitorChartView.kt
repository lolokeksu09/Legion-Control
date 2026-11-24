package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.max

/** Actual sample timestamps; no smoothing or interpolated values over missing samples. */
class MonitorChartView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        val pad = 16f * resources.displayMetrics.density
        val left = pad
        val right = width - pad
        val top = pad
        val bottom = height - pad
        if (right <= left || bottom <= top) return
        paint.color = LegionUiSupport.BORDER
        paint.strokeWidth = resources.displayMetrics.density
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4
            canvas.drawLine(left, y, right, y, paint)
        }
        val values = points.mapNotNull(metric::value)
        if (values.isEmpty()) return
        val lo = values.minOrNull() ?: return
        val hi = values.maxOrNull() ?: return
        val range = hi - lo
        val first = points.first().elapsedMs
        val duration = max(1L, points.last().elapsedMs - first)
        var previousX: Float? = null
        var previousY: Float? = null
        paint.color = LegionUiSupport.ACCENT
        paint.strokeWidth = 2f * resources.displayMetrics.density
        for (point in points) {
            val value = metric.value(point)
            if (value == null || !value.isFinite()) {
                previousX = null
                previousY = null
                continue
            }
            val x = left + (right - left) * ((point.elapsedMs - first).toDouble() / duration).toFloat()
            val y = if (range == 0.0) (top + bottom) / 2 else
                bottom - (bottom - top) * ((value - lo) / range).toFloat()
            val px = previousX
            val py = previousY
            if (px != null && py != null) canvas.drawLine(px, py, x, y, paint)
            canvas.drawCircle(x, y, 2.5f * resources.displayMetrics.density, paint)
            previousX = x
            previousY = y
        }
    }
}
