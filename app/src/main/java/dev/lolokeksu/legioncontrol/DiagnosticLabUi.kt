package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.LinearLayout

/** Shared lab row styling; feature identity is still attached by the caller. */
internal object DiagnosticLabUi {
    private val ordinal = Regex("^\\d+ · ")
    fun action(context: Context, label: String, onClick: () -> Unit): LinearLayout {
        val parts = label.replaceFirst(ordinal, "").split(" — ", limit = 2)
        return LegionUiSupport.actionTile(context, parts[0], parts.getOrElse(1) { "" }, onClick)
    }

    fun actionLayout(context: Context) = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = (6 * context.resources.displayMetrics.density).toInt()
    }

    fun revealResult(view: View) {
        view.requestRectangleOnScreen(Rect(0, 0, view.width, minOf(view.height, 48 *
            view.resources.displayMetrics.density.toInt().coerceAtLeast(1))), false)
        view.announceForAccessibility("Результат диагностики")
    }
}
