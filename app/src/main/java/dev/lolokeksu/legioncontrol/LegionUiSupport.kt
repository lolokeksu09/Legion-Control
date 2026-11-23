package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Legion Control AMOLED design system for traditional Android Views.
 *
 * Deliberately does not replace View instances, click listeners, ids, text contents,
 * enablement, selection or backend state. The five Hub tabs, their icons, and the
 * bespoke Game Toolbox and Game Detail cards retain their own approved surfaces.
 */
object LegionUiSupport {
    val PANEL = 0xFF12161C.toInt()
    val BORDER = 0xFF252B33.toInt()
    val ACCENT = 0xFF86B7FF.toInt()
    val MUTED = 0xFF9CA5B0.toInt()
    private val DISABLED = 0xFF68717D.toInt()

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(view: View, fill: Int, stroke: Int, radius: Int = 17): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(view, radius).toFloat()
            setStroke(dp(view, 1).coerceAtLeast(1), stroke)
        }

    private fun textStates(): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(DISABLED, Color.WHITE)
    )

    /**
     * Accessible tile-like control with an explicit disabled/focus state.
     * Margins are amended in place; parent layout parameters and click handlers stay intact.
     */
    fun styleButtonSurface(button: Button) {
        val background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),
                shape(button, 0xFF0D1015.toInt(), 0xFF202630.toInt()))
            addState(intArrayOf(android.R.attr.state_focused),
                shape(button, 0xFF1B2B42.toInt(), ACCENT))
            addState(intArrayOf(),
                shape(button, PANEL, BORDER))
        }
        button.background = RippleDrawable(
            ColorStateList.valueOf(0x4486B7FF), background,
            shape(button, Color.WHITE, Color.WHITE)
        )
        button.setTextColor(textStates())
        button.textSize = 14f
        button.isAllCaps = false
        button.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        button.minHeight = dp(button, 56)
        button.setPadding(dp(button, 17), dp(button, 13), dp(button, 15), dp(button, 13))
        val parent = button.parent
        val params = button.layoutParams
        if (parent is LinearLayout && parent.orientation == LinearLayout.VERTICAL &&
            params is LinearLayout.LayoutParams) {
            params.bottomMargin = maxOf(params.bottomMargin, dp(button, 10))
            button.layoutParams = params
        }
    }

    private fun styleInput(input: EditText) {
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),
                shape(input, 0xFF101319.toInt(), BORDER, 14))
            addState(intArrayOf(android.R.attr.state_focused),
                shape(input, PANEL, ACCENT, 14))
            addState(intArrayOf(), shape(input, PANEL, BORDER, 14))
        }
        input.background = states
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(MUTED)
        input.textSize = 14f
        input.minHeight = dp(input, 52)
        input.setPadding(dp(input, 15), dp(input, 12), dp(input, 15), dp(input, 12))
        val params = input.layoutParams
        if (input.parent is LinearLayout && (input.parent as LinearLayout).orientation ==
            LinearLayout.VERTICAL && params is LinearLayout.LayoutParams) {
            params.bottomMargin = maxOf(params.bottomMargin, dp(input, 10))
            input.layoutParams = params
        }
    }

    private fun styleCheck(check: CheckBox) {
        check.setTextColor(textStates())
        check.textSize = 14f
        check.minHeight = dp(check, 48)
        check.buttonTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ), intArrayOf(DISABLED, ACCENT, MUTED)
        )
    }

    private fun styleText(label: TextView) {
        val sizeSp = label.textSize / label.resources.displayMetrics.scaledDensity
        if (label.typeface?.isBold == true && sizeSp >= 18f) {
            // Unify module heading hierarchy; keep existing text and color semantics
            // on compact labels, live measurements and warning/result panels.
            label.setTextColor(Color.WHITE)
            label.textSize = 21f
        } else {
            when (label.currentTextColor) {
                Color.LTGRAY, Color.GRAY, 0xFFAAAAAA.toInt(),
                0xFFAFAFAF.toInt(), 0xFFB1B1B7.toInt(),
                0xFFB8B8C0.toInt(), 0xFFC1C4CB.toInt(),
                0xFFA5A5A5.toInt() -> label.setTextColor(MUTED)
            }
        }
    }

    /** Pass over an already built module View hierarchy, without modifying its behavior. */
    fun styleModuleTree(root: View) {
        when (root) {
            is CheckBox -> styleCheck(root)
            is EditText -> styleInput(root)
            is Button -> styleButtonSurface(root)
            is TextView -> styleText(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) styleModuleTree(root.getChildAt(index))
        }
    }

    /** Kept for callers of the earlier alpha.41 Fragment styling API. */
    fun styleFragmentButtons(root: View) = styleModuleTree(root)

    /**
     * Legacy helpers used before the controls are attached to their parent.
     * The matching card spacing is intentionally retained here.
     */
    fun styleAction(button: Button) {
        styleButtonSurface(button)
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(button, 10) }
    }

    /**
     * Same app chrome for standalone tools that must remain Activities
     * (MediaProjection, Rust diagnostics, controller key events).
     * Navigation goes back to the canonical NavHost; no duplicate tab data is stored.
     */
    fun setModuleActivityContent(activity: Activity, content: ScrollView, subtitle: String) {
        styleModuleTree(content)
        content.setBackgroundColor(Color.BLACK)
        content.clipToPadding = false

        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.ime()
                )
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                insets
            }
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(this, 16), dp(this, 15), dp(this, 16), dp(this, 13))
        }
        header.addView(TextView(activity).apply {
            text = "‹"
            textSize = 35f
            gravity = Gravity.CENTER
            setTextColor(ACCENT)
            isClickable = true
            isFocusable = true
            contentDescription = "Назад"
            setOnClickListener { activity.finish() }
        }, LinearLayout.LayoutParams(dp(header, 48), dp(header, 52)))
        val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(activity).apply {
            text = "LEGION CONTROL"
            textSize = 21f
            letterSpacing = 0.045f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        titles.addView(TextView(activity).apply {
            text = subtitle
            textSize = 11.5f
            setTextColor(MUTED)
            setPadding(0, dp(this, 4), 0, 0)
        })
        header.addView(titles, LinearLayout.LayoutParams(-1, -2))
        shell.addView(header)
        shell.addView(View(activity).apply { setBackgroundColor(0xFF22262B.toInt()) },
            LinearLayout.LayoutParams(-1, dp(shell, 1)))
        shell.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val labels = arrayOf("Домой", "Игры", "Сервисы", "История", "Настройки")
        val icons = intArrayOf(
            android.R.drawable.ic_menu_view, android.R.drawable.ic_media_play,
            android.R.drawable.ic_menu_manage, android.R.drawable.ic_menu_recent_history,
            android.R.drawable.ic_menu_preferences
        )
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(this, 7), dp(this, 9), dp(this, 7), dp(this, 8))
            setBackgroundColor(0xFF0D0F12.toInt())
        }
        for (index in labels.indices) {
            val selected = index == 2
            val tint = if (selected) ACCENT else 0xFF858C98.toInt()
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "Открыть раздел " + labels[index]
                isSelected = selected
                stateDescription = if (selected) "Выбрано" else "Не выбрано"
                setOnClickListener {
                    activity.startActivity(Intent(activity, NavigationHostActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("open_hub_tab", index)
                    })
                    activity.finish()
                }
            }
            item.addView(ImageView(activity).apply {
                setImageResource(icons[index])
                imageTintList = ColorStateList.valueOf(tint)
            }, LinearLayout.LayoutParams(dp(item, 21), dp(item, 21)))
            item.addView(TextView(activity).apply {
                text = labels[index]
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(tint)
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setPadding(0, dp(this, 6), 0, 0)
            })
            bottom.addView(item, LinearLayout.LayoutParams(0, dp(bottom, 57), 1f))
        }
        shell.addView(bottom)
        activity.window.statusBarColor = Color.BLACK
        activity.window.navigationBarColor = Color.BLACK
        activity.setContentView(shell)
    }

    fun versionLabel(context: android.content.Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0L)).versionName
                ?: "pre-release"
    }.getOrDefault("pre-release")

    /** Remaining dedicated no-chrome surfaces can retain their explicit inset listener. */
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
