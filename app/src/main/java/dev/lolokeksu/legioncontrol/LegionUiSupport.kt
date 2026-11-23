package dev.lolokeksu.legioncontrol

import android.view.WindowInsets
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

/** Reusable edge-to-edge/IME handling for legacy Views screens. */
object LegionUiSupport {
    /** Surface-only styling preserves the layout parameters and click handlers of
     * existing Fragment controls. Selected/disabled states remain distinct. */
    fun styleButtonSurface(button: Button) {
        val density = button.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun shape(enabled: Boolean) = GradientDrawable().apply {
            setColor(if (enabled) 0xFF191A1E.toInt() else 0xFF101115.toInt())
            cornerRadius = dp(11).toFloat()
            setStroke(dp(1).coerceAtLeast(1),
                if (enabled) 0xFF383D45.toInt() else 0xFF25272C.toInt())
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(false))
            addState(intArrayOf(), shape(true))
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(11).toFloat()
        }
        button.background = RippleDrawable(
            ColorStateList.valueOf(0x4486B7FF), states, mask
        )
        button.setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(0xFF757A85.toInt(), Color.WHITE)
        ))
        button.isAllCaps = false
        button.minHeight = dp(48)
        button.setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    /** Applied once when a Navigation destination's View is created, excluding
     * the existing bespoke home tiles and bottom navigation. */
    fun styleFragmentButtons(root: View) {
        if (root is Button) styleButtonSurface(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) styleFragmentButtons(root.getChildAt(index))
        }
    }

    /** Shared AMOLED action treatment. Keep native Button semantics and ripple. */
    fun styleAction(button: Button) {
        val density = button.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val surface = GradientDrawable().apply {
            setColor(0xFF191A1E.toInt())
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1).coerceAtLeast(1), 0xFF3A3A42.toInt())
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(13).toFloat()
        }
        button.background = RippleDrawable(
            ColorStateList.valueOf(0x4486B7FF), surface, mask
        )
        button.setTextColor(Color.WHITE)
        button.textSize = 14f
        button.isAllCaps = false
        button.minHeight = dp(48)
        button.setPadding(dp(12), dp(8), dp(12), dp(8))
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
    }

    fun versionLabel(context: android.content.Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0L)).versionName
                ?: "pre-release"
    }.getOrDefault("pre-release")

    fun applyInsets(scroll: ScrollView) {
        scroll.clipToPadding = false
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                    WindowInsets.Type.ime()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }
}
