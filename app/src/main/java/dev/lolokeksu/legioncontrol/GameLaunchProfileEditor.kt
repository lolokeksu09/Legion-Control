package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** One editor for game detail, rules and Toolbox; never applies a profile. */
internal object GameLaunchProfileEditor {
    private fun dp(ctx: Context, value: Int) = (value * ctx.resources.displayMetrics.density).toInt()
    private fun text(ctx: Context, value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(ctx).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    fun show(ctx: Context, pkg: String, onResult: (String) -> Unit) {
        if (!GameLaunchProfilePolicy.validPackage(pkg)) {
            onResult("Сначала выберите приложение.")
            return
        }
        val current = GameLaunchProfiles.profile(ctx, pkg)
        val volumes = GameLaunchProfilePolicy.editorPercentOptions(
            listOf(0, 20, 40, 60, 80, 100), current?.mediaPercent)
        val brightness = GameLaunchProfilePolicy.editorPercentOptions(
            listOf(10, 20, 40, 60, 80, 100), current?.brightnessPercent)
        val focus: List<Boolean?> = listOf(null, true, false)
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 8))
        }
        fun selector(title: String, options: List<String>, selected: Int): android.widget.Spinner {
            val label = text(ctx, title, 13f, Color.WHITE, true).apply {
                setPadding(0, dp(ctx, 12), 0, dp(ctx, 4))
            }
            content.addView(label)
            return LegionUiSupport.spinner(ctx, options).also {
                it.id = View.generateViewId()
                label.labelFor = it.id
                it.setSelection(selected.coerceIn(options.indices))
                content.addView(it, LinearLayout.LayoutParams(-1, -2))
            }
        }
        content.addView(text(ctx,
            "Сохранение меняет только профиль. При отдельном применении изменения затронут Android целиком; " +
                "исходные значения будут сохранены для возврата.",
            12f, LegionUiSupport.MUTED
        ).apply { setPadding(0, dp(ctx, 4), 0, dp(ctx, 8)) })
        val volume = selector("Громкость мультимедиа",
            volumes.map { it?.let { value -> "$value%" } ?: "Не менять" },
            volumes.indexOf(current?.mediaPercent).coerceAtLeast(0))
        val light = selector("Яркость",
            brightness.map { it?.let { value -> "$value%" } ?: "Не менять" },
            brightness.indexOf(current?.brightnessPercent).coerceAtLeast(0))
        val dnd = selector("Режим «Не беспокоить»",
            listOf("Не менять", "Включить", "Выключить"),
            focus.indexOf(current?.focusEnabled).coerceAtLeast(0))
        val scroll = ScrollView(ctx).apply { addView(content) }
        LegionDialogs.builder(ctx).setTitle("Профиль запуска · $pkg")
            .setView(scroll)
            .setPositiveButton("Сохранить") { _, _ ->
                val profile = GameLaunchProfile(
                    volumes[volume.selectedItemPosition],
                    brightness[light.selectedItemPosition],
                    focus[dnd.selectedItemPosition]
                )
                val saved = runCatching { GameLaunchProfiles.save(ctx, pkg, profile) }.getOrDefault(false)
                onResult(if (saved) {
                    if (profile.actionable) "Профиль запуска сохранён." else "Профиль запуска удалён."
                } else "Не удалось сохранить профиль. Проверьте, не выполняется ли восстановление ZIP.")
            }
            .setNegativeButton("Отмена", null).show()
    }

}
