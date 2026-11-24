package dev.lolokeksu.legioncontrol

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import java.util.Locale

/** Shared plot geometry and axes. Metric validation, gap policy and samples stay with the owner. */
internal class LegionChartFrame(private val view: View) {
    private val density = view.resources.displayMetrics.density
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LegionUiSupport.BORDER
        strokeWidth = density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LegionUiSupport.MUTED
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, view.resources.displayMetrics)
    }
    val lineWidth = 2f * density
    val pointRadius = 2.5f * density
    private fun number(value: Double): String =
        String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")

    fun empty(canvas: Canvas) {
        val message = "Нет данных для графика"
        val oldSize = label.textSize
        val available = (view.width - 24f * density).coerceAtLeast(1f)
        if (label.measureText(message) > available)
            label.textSize = oldSize * available / label.measureText(message)
        canvas.drawText(message, (view.width - label.measureText(message)) / 2f,
            view.height / 2f - (label.ascent() + label.descent()) / 2f, label)
        label.textSize = oldSize
    }

    fun axes(canvas: Canvas, lo: Double, hi: Double, durationMs: Long, unit: String): RectF? {
        if (!lo.isFinite() || !hi.isFinite() || hi < lo || durationMs < 0L) return null
        val textHeight = label.descent() - label.ascent()
        val left = maxOf(label.measureText(number(lo)), label.measureText(number(hi))) + 20f * density
        val right = view.width - 14f * density
        val top = textHeight + 12f * density
        val bottom = view.height - textHeight - 14f * density
        if (right - left < 32f * density || bottom - top < 24f * density) return null
        val plot = RectF(left, top, right, bottom)
        canvas.drawText(unit, 12f * density, -label.ascent() + 5f * density, label)
        val intervals = if (plot.height() >= textHeight * 5f) 4 else 2
        for (i in 0..intervals) {
            val y = top + plot.height() * i / intervals
            canvas.drawLine(left, y, right, y, grid)
            if (hi != lo || i == intervals / 2) {
                val text = number(hi - (hi - lo) * i / intervals)
                canvas.drawText(text, left - 8f * density - label.measureText(text),
                    y - (label.ascent() + label.descent()) / 2f, label)
            }
        }
        val duration = if (durationMs >= 60000L) "${number(durationMs / 60000.0)} мин"
                       else "${number(durationMs / 1000.0)} с"
        val baseline = bottom + textHeight + 4f * density
        canvas.drawText("0", left, baseline, label)
        val rightText = right - label.measureText(duration)
        if (rightText > left + label.measureText("0") + 8f * density)
            canvas.drawText(duration, rightText, baseline, label)
        return plot
    }

    fun y(plot: RectF, value: Double, lo: Double, hi: Double): Float =
        if (hi == lo) plot.centerY() else
            plot.bottom - plot.height() * ((value - lo) / (hi - lo)).toFloat()
}
