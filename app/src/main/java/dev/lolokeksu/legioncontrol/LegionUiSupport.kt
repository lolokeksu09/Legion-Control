package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.content.Intent
import android.content.Context
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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
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
    val WARNING = 0xFFFFCF87.toInt()
    val DANGER = 0xFFFF8B8B.toInt()
    val SUCCESS = 0xFFA7DBB4.toInt()
    private val DISABLED = 0xFF68717D.toInt()

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(view: View, fill: Int, stroke: Int, radius: Int = 17): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(view, radius).toFloat()
            setStroke(dp(view, 1).coerceAtLeast(1), stroke)
        }

    /** One compact selector for every module; native selection and keyboard support retained. */
    fun spinner(context: Context, items: List<String>): Spinner = Spinner(context, Spinner.MODE_DROPDOWN).apply {
        minimumHeight = dp(this, 48)
        backgroundTintList = ColorStateList.valueOf(MUTED)
        setPopupBackgroundDrawable(shape(this, PANEL, BORDER, 14))
        adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
            init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            private fun style(view: View): View = view.apply {
                if (this is TextView) {
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    minimumHeight = dp(this, 48)
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding(dp(this, 14), dp(this, 9), dp(this, 14), dp(this, 9))
                    if (this is android.widget.CheckedTextView) {
                        checkMarkTintList = ColorStateList.valueOf(ACCENT)
                    }
                }
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getView(position, convertView, parent))
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getDropDownView(position, convertView, parent))
        }
    }

    private fun textStates(): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_selected), intArrayOf()),
        intArrayOf(DISABLED, ACCENT, Color.WHITE)
    )

    /**
     * Accessible tile-like control with an explicit disabled/focus state.
     * Margins are amended in place; parent layout parameters and click handlers stay intact.
     */
    fun styleButtonSurface(button: Button) {
        val background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),
                shape(button, 0xFF0D1015.toInt(), 0xFF202630.toInt()))
            addState(intArrayOf(android.R.attr.state_selected),
                shape(button, 0xFF1B2B42.toInt(), ACCENT))
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
        button.minHeight = dp(button, 48)
        button.minimumHeight = dp(button, 48)
        button.setPadding(dp(button, 14), dp(button, 9), dp(button, 14), dp(button, 9))
        if(button.parent is CompactActionRow) {
            button.textSize=13f
            button.gravity=Gravity.CENTER
            button.setPadding(dp(button, 8), dp(button, 6), dp(button, 8), dp(button, 6))
        }
        val parent = button.parent
        val params = button.layoutParams
        if (parent is LinearLayout && parent.orientation == LinearLayout.VERTICAL &&
            params is LinearLayout.LayoutParams) {
            params.bottomMargin = maxOf(params.bottomMargin, dp(button, 6))
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
        // Within a module content column the first bold heading is the page
        // title; later bold headings are section dividers like those on Home.
        // Restrict this to direct children so report cards and result content
        // never inherit decorative section styling.
        if (root is LinearLayout && root.orientation == LinearLayout.VERTICAL) {
            val headings = (0 until root.childCount).mapNotNull { index ->
                (root.getChildAt(index) as? TextView)?.takeIf {
                    it !is Button && it.typeface?.isBold == true &&
                        it.textSize / it.resources.displayMetrics.scaledDensity >= 18f
                }
            }
            if (headings.size > 1) {
                headings.drop(1).forEach { heading ->
                    heading.textSize = 12f
                    heading.setTextColor(ACCENT)
                    heading.letterSpacing = 0.08f
                    heading.setPadding(heading.paddingLeft,
                        maxOf(heading.paddingTop, dp(heading, 16)),
                        heading.paddingRight, maxOf(heading.paddingBottom, dp(heading, 11)))
                }
            }
        }
    }

    /** Compact section label used by the approved Home/Game Hub hierarchy. */
    fun sectionLabel(context: android.content.Context, title: String): TextView =
        TextView(context).apply {
            text = title
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(ACCENT)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAccessibilityHeading = true
            setPadding(0, dp(this, 14), 0, dp(this, 8))
        }

    /**
     * One shared card for Home, Games, Services, History and Game Toolbox.
     * A single accessibility description includes both the action and its context.
     * This is view-only: the supplied click action retains its existing behavior.
     */
    fun actionTile(context: Context, title: String, detail: String,
                   onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = RippleDrawable(
                ColorStateList.valueOf(0x4486B7FF),
                shape(this, PANEL, BORDER), null
            )
            setPadding(dp(this, 14), dp(this, 10), dp(this, 12), dp(this, 10))
            isClickable = true
            isFocusable = true
            minimumHeight = dp(this, 48)
            contentDescription = if (detail.isBlank()) title else "$title. $detail"
            setOnClickListener { onClick() }
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        labels.addView(TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setLineSpacing(3f, 1f)
        })
        if (detail.isNotBlank()) labels.addView(TextView(context).apply {
            text = detail
            textSize = 12f
            setTextColor(MUTED)
            setPadding(0, dp(this, 5), 0, 0)
            setLineSpacing(3f, 1f)
        })
        row.addView(labels, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(context).apply {
            text = "›"
            textSize = 24f
            setTextColor(ACCENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        return row
    }

    /** Status rows retain text semantics, even with color disabled or a screen reader. */
    internal fun permissionRow(context: Context, item: PermissionItem,
                               onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = if (item.canOpenSettings)
                RippleDrawable(ColorStateList.valueOf(0x4486B7FF), shape(this, PANEL, BORDER), null)
            else shape(this, PANEL, BORDER)
            setPadding(dp(this, 14), dp(this, 11), dp(this, 14), dp(this, 11))
            minimumHeight = dp(this, 48)
            if (item.canOpenSettings) {
                isClickable = true
                isFocusable = true
                contentDescription = item.title + ". " + item.state.label + ". " + item.purpose +
                    " Открыть настройки Android."
                setOnClickListener { onClick() }
            }
        }
        fun line(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(context).apply {
                text = value
                textSize = size
                setTextColor(color)
                setLineSpacing(3f, 1f)
                if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                if (item.canOpenSettings) importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(line(item.title, 15f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        val color = when (item.state) {
            AccessState.READY -> SUCCESS
            AccessState.PERMISSION_REQUIRED, AccessState.DISABLED -> WARNING
            AccessState.UNKNOWN, AccessState.UNSUPPORTED -> MUTED
        }
        header.addView(line(item.state.label, 12f, color, true).apply {
            maxWidth = dp(this, 140)
            gravity = Gravity.END
            setPadding(dp(this, 8), 0, 0, 0)
        })
        row.addView(header)
        row.addView(line(item.purpose, 12f, MUTED).apply {
            setPadding(0, dp(this, 5), 0, 0)
        })
        if (item.canOpenSettings) row.addView(line("Открыть настройки Android ›", 12f, ACCENT).apply {
            setPadding(0, dp(this, 6), 0, 0)
        })
        return row
    }

    enum class Notice { INFO, LOADING, EMPTY, ERROR, WARNING }

    /** Apply to real status or result views without replacing text or listeners. */
    fun notice(view: TextView, kind: Notice) {
        val tint = when(kind) {
            Notice.LOADING -> ACCENT
            Notice.ERROR -> DANGER
            Notice.WARNING -> WARNING
            Notice.EMPTY, Notice.INFO -> MUTED
        }
        view.background = shape(view, PANEL, BORDER, 14)
        view.setTextColor(tint)
        view.textSize = 14f
        view.setPadding(dp(view, 12), dp(view, 9), dp(view, 12), dp(view, 9))
        view.accessibilityLiveRegion = if(kind == Notice.EMPTY) View.ACCESSIBILITY_LIVE_REGION_NONE
            else View.ACCESSIBILITY_LIVE_REGION_POLITE
        val params = view.layoutParams as? LinearLayout.LayoutParams
        if(params != null) {
            params.bottomMargin = maxOf(params.bottomMargin, dp(view, 6))
            view.layoutParams = params
        }
    }

    fun noticeView(context: Context, message: String, kind: Notice): TextView =
        TextView(context).apply { text = message; notice(this, kind) }

    /** Common compact surface for selectable reports and data cards. */
    fun reportSurface(view: View) {
        view.background = cardSurface(view)
        view.setPadding(dp(view, 14), dp(view, 10), dp(view, 14), dp(view, 10))
    }

    /** Reuse the same 17dp panel surface in non-Hub modules. */
    fun cardSurface(view: View): GradientDrawable = shape(view, PANEL, BORDER)

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
        ).apply { bottomMargin = dp(button, 6) }
    }

    /**
     * Same app chrome for standalone tools that must remain Activities
     * (MediaProjection, Rust diagnostics, controller key events).
     * Navigation goes back to the canonical NavHost; no duplicate tab data is stored.
     */
    fun setModuleActivityContent(activity: Activity, content: ViewGroup, subtitle: String) {
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

        // Share the same destination labels as NavigationHostActivity.
        // Activity-only tools should never show an obsolete second tab vocabulary.
        val labels = HubTab.entries.map { it.shortTitle }.toTypedArray()
        val icons = HubCenterIcons.tabs()
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(this, 5), dp(this, 5), dp(this, 5), dp(this, 5))
            setBackgroundColor(0xFF0D0F12.toInt())
        }
        val selectedIndex = when (activity) {
            is GameToolboxActivity, is GameSessionActivity,
            is SmartProfilesActivity, is ApplicationCenterActivity, is GamePreflightActivity -> 1
            is SessionLibraryActivity, is ExperimentLabActivity -> 3
            else -> 2
        }
        for (index in labels.indices) {
            val selected = index == selectedIndex
            val tint = if (selected) ACCENT else 0xFF858C98.toInt()
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "Открыть раздел " + HubTab.entries[index].title
                isSelected = selected
                stateDescription = if (selected) "Выбрано" else "Не выбрано"
                setOnClickListener {
                    activity.startActivity(Intent(activity, LegionHubActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("open_hub_tab", index)
                    })
                    activity.finish()
                }
            }
            item.addView(ImageView(activity).apply {
                setImageResource(icons[index])
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                imageTintList = ColorStateList.valueOf(tint)
            }, LinearLayout.LayoutParams(dp(item, 21), dp(item, 21)))
            item.addView(TextView(activity).apply {
                text = labels[index]
                textSize = 12f
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setTextColor(tint)
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setPadding(0, dp(this, 6), 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))
            item.minimumHeight = dp(bottom, 48)
            bottom.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
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
