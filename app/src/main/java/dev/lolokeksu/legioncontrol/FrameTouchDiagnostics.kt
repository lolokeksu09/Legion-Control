package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.abs

/** This measures Choreographer callbacks IN THIS APP only. */
data class FrameIntervalReport(
    val intervalsMs: List<Double>,
    val displayHz: Float
)

object FramePacingStatistics {
    fun format(r: FrameIntervalReport): String {
        val intervals = r.intervalsMs.filter { it.isFinite() && it > 0 }
        if (intervals.isEmpty()) return "FRAME PACING · недостаточно кадров"
        val sorted = intervals.sorted()
        val p95 = sorted[((sorted.size-1)*0.95).toInt()]
        val targetMs = if (r.displayHz in 1f..500f) 1000.0/r.displayHz else null
        val over = targetMs?.let { target -> intervals.count { it > 1.5*target } }
        return buildString {
            append("FRAME PACING · VSYNC callback только Legion Control\n")
            append("Интервалов: ").append(intervals.size).append("\n")
            append(String.format(Locale.US,
                "Среднее: %.2f мс · p95: %.2f мс · максимум: %.2f мс\n",
                intervals.average(), p95, sorted.last()))
            append("Дисплей заявлен: ")
                .append(if (targetMs != null) String.format(
                    Locale.US, "%.1f Гц", r.displayHz
                ) else "н/д").append("\n")
            append("Интервалов > 1.5× номинального: ")
                .append(over?.toString() ?: "н/д").append("\n")
            append("Не FPS Wild Rift, не измерение времени отображения кадра SurfaceFlinger.")
        }
    }
}

/** Automatic stop after 5 seconds; onStop always removes callback. */
class FramePacingCapture(
    private val refreshHz: Float,
    private val finished: (FrameIntervalReport) -> Unit
) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private val times = ArrayList<Double>(600)
    private var firstMs = 0L
    private var lastNs = 0L
    private var active = false

    fun start() {
        check(!active)
        active = true
        firstMs = SystemClock.uptimeMillis()
        choreographer.postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!active) return
        if (lastNs > 0L && frameTimeNanos > lastNs && times.size < 1024) {
            times += (frameTimeNanos - lastNs)/1_000_000.0
        }
        lastNs = frameTimeNanos
        if (SystemClock.uptimeMillis()-firstMs >= 5000L) {
            stop(true)
        } else choreographer.postFrameCallback(this)
    }

    fun stop(report: Boolean) {
        if (!active) return
        active = false
        choreographer.removeFrameCallback(this)
        if (report) finished(FrameIntervalReport(times.toList(), refreshHz))
    }
}

/** Full-screen drawing/touch tester. No system input injection or sampling controls. */
class TouchDiagnosticsView(
    context: Context,
    private val onUpdate: (String) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointers = mutableMapOf<Int, Pair<Float,Float>>()
    private val cells = mutableSetOf<Int>()
    private var maxPointers = 0
    private var moves = 0
    private var downs = 0
    private var maximumDispatchDelayMs = 0L
    private var lastUiAtMs = 0L

    init {
        isClickable = true
        setBackgroundColor(Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = Color.DKGRAY
        paint.strokeWidth = 1f
        for (i in 1..2) {
            canvas.drawLine(width*i/3f, 0f, width*i/3f, height.toFloat(), paint)
            canvas.drawLine(0f, height*i/3f, width.toFloat(), height*i/3f, paint)
        }
        paint.color = Color.CYAN
        pointers.values.forEach { (x,y) -> canvas.drawCircle(x,y,44f,paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            downs++
        } else if (action == MotionEvent.ACTION_MOVE) moves++
        maximumDispatchDelayMs = maxOf(
            maximumDispatchDelayMs,
            (SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0)
        )
        pointers.clear()
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                if (action == MotionEvent.ACTION_POINTER_UP && i == event.actionIndex) continue
                pointers[id] = event.getX(i) to event.getY(i)
                if (width>0 && height>0) {
                    val col=(event.getX(i)/width*3).toInt().coerceIn(0,2)
                    val row=(event.getY(i)/height*3).toInt().coerceIn(0,2)
                    cells.add(row*3+col)
                }
            }
        }
        maxPointers = maxOf(maxPointers, pointers.size)
        if (SystemClock.uptimeMillis() - lastUiAtMs >= 150L || action == MotionEvent.ACTION_UP) {
            lastUiAtMs = SystemClock.uptimeMillis()
            onUpdate(summary())
        }
        invalidate()
        return true
    }

    fun summary(): String = "TOUCH ANALYZER · внутри Legion Control\n" +
        "Одновременных касаний, максимум: "+maxPointers+"\n" +
        "Касаний вниз: "+downs+" · MOVE событий: "+moves+"\n" +
        "Покрыто областей 3×3: "+cells.size+"/9\n" +
        "Макс. задержка доставки MotionEvent: "+maximumDispatchDelayMs+" мс\n" +
        "Не аппаратная touch sampling rate и не задержка игрового движка."
}
