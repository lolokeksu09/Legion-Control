package dev.lolokeksu.legioncontrol

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * App-owned AMOLED dialog presentation. Android still owns focus, cancellation,
 * list choice semantics, keyboard and window lifecycle. Never use for system consent.
 */
object LegionDialogs {
    fun builder(context: Context): Builder = Builder(context)

    class Builder internal constructor(private val context: Context) {
        private val delegate = AlertDialog.Builder(context, R.style.LegionDialogTheme)
        private var title: CharSequence? = null
        private var customView: View? = null
        private var destructive = false

        fun setTitle(value: CharSequence) = apply {
            title = value
            delegate.setTitle(value)
        }
        fun setMessage(value: CharSequence) = apply { delegate.setMessage(value) }
        fun setView(view: View) = apply {
            customView = view
            delegate.setView(view)
        }
        fun setItems(items: Array<out CharSequence>, listener: DialogInterface.OnClickListener) =
            apply { delegate.setItems(items, listener) }
        fun setSingleChoiceItems(items: Array<out CharSequence>, checked: Int,
                                 listener: DialogInterface.OnClickListener) =
            apply { delegate.setSingleChoiceItems(items, checked, listener) }
        fun setMultiChoiceItems(items: Array<out CharSequence>, checked: BooleanArray,
                                listener: DialogInterface.OnMultiChoiceClickListener) =
            apply { delegate.setMultiChoiceItems(items, checked, listener) }

        fun setPositiveButton(label: CharSequence, listener: DialogInterface.OnClickListener?) =
            apply { delegate.setPositiveButton(label, listener) }
        fun setNegativeButton(label: CharSequence, listener: DialogInterface.OnClickListener?) =
            apply { delegate.setNegativeButton(label, listener) }
        fun setNeutralButton(label: CharSequence, listener: DialogInterface.OnClickListener?) =
            apply { delegate.setNeutralButton(label, listener) }
        fun setDestructive() = apply { destructive = true }

        fun create(): AlertDialog {
            title?.let { value ->
                delegate.setCustomTitle(TextView(delegate.context).apply {
                    text = value
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(dp(20), dp(18), dp(20), dp(12))
                    isAccessibilityHeading = true
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                })
            }
            customView?.let(LegionUiSupport::styleModuleTree)
            val dialog = delegate.create()
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    window.setBackgroundDrawable(GradientDrawable().apply {
                        setColor(LegionUiSupport.PANEL)
                        cornerRadius = dp(18).toFloat()
                        setStroke(dp(1).coerceAtLeast(1), LegionUiSupport.BORDER)
                    })
                    val bounds = context.getSystemService(WindowManager::class.java)
                        ?.currentWindowMetrics?.bounds
                    val width = bounds?.width() ?: context.resources.displayMetrics.widthPixels
                    window.setLayout((width - dp(32)).coerceAtLeast(dp(160)).coerceAtMost(dp(440)),
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
                dialog.findViewById<TextView>(android.R.id.message)?.apply {
                    setTextColor(LegionUiSupport.MUTED)
                    textSize = 14f
                    setLineSpacing(dp(2).toFloat(), 1f)
                }
                dialog.listView?.apply {
                    divider = ColorDrawable(LegionUiSupport.BORDER)
                    dividerHeight = dp(1)
                }
                for (which in intArrayOf(DialogInterface.BUTTON_NEGATIVE,
                    DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_POSITIVE)) {
                    dialog.getButton(which)?.apply {
                        LegionUiSupport.styleButtonSurface(this)
                        gravity = Gravity.CENTER
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            setTextColor(if (destructive) 0xFFFF8B8B.toInt() else LegionUiSupport.ACCENT)
                        }
                    }
                }
            }
            return dialog
        }

        fun show(): AlertDialog = create().apply { show() }

        private fun dp(value: Int): Int =
            (value * context.resources.displayMetrics.density).roundToInt()
    }
}
