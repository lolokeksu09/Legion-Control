package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType

/** One shared manual system control surface. Each explicit Apply changes only one setting. */
internal object SystemControlPanel {
    fun show(ctx: Context) {
        fun dp(value: Int) = (value * ctx.resources.displayMetrics.density).toInt()
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(10))
        }
        val current = TextView(ctx).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
        }
        LegionUiSupport.reportSurface(current)
        content.addView(current)
        val result = TextView(ctx).apply {
            textSize = 13f
            setTextColor(LegionUiSupport.ACCENT)
            setPadding(0, dp(8), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(result)
        fun refresh(message: String? = null) {
            current.text = runCatching { SmartSystemControls.status(ctx) }
                .getOrDefault("Состояние настроек недоступно.")
            if (message != null) result.text = message
        }
        fun perform(action: () -> String) {
            refresh(runCatching(action).getOrElse { "Действие не выполнено: " + it.javaClass.simpleName })
        }
        fun section(title: String) { content.addView(LegionUiSupport.sectionLabel(ctx, title)) }
        fun button(title: String, action: () -> Unit): Button = Button(ctx).apply {
            text = title
            setOnClickListener { action() }
        }
        fun permission(title: String, capability: CapabilityAccess) {
            content.addView(LegionUiSupport.actionTile(ctx, title, "Открыть настройки Android") {
                val intent = CapabilityAccessBackend.settingsIntent(ctx, capability)
                if (intent == null) refresh("Настройки этого доступа недоступны.")
                else runCatching { ctx.startActivity(intent) }
                    .onFailure { refresh("Не удалось открыть системные настройки.") }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        fun percentage(title: String, min: Int, apply: (Int) -> String) {
            section(title)
            val field = EditText(ctx).apply {
                hint = "$min–100 %"
                inputType = InputType.TYPE_CLASS_NUMBER
                isSingleLine = true
                id = View.generateViewId()
                contentDescription = title + ", процент"
            }
            content.addView(field, LinearLayout.LayoutParams(-1, -2))
            content.addView(CompactActionRow(ctx,
                button("Применить") {
                    val value = field.text.toString().toIntOrNull()
                    if (value == null || value !in min..100) refresh("Укажите значение $min–100%.")
                    else perform { apply(value) }
                },
                button("Обновить") { refresh("Текущие показания обновлены.") }
            ))
        }

        percentage("ГРОМКОСТЬ МУЛЬТИМЕДИА", 0) { SmartSystemControls.setMediaVolume(ctx, it) }
        percentage("ЯРКОСТЬ ЭКРАНА", 10) { SmartSystemControls.setBrightness(ctx, it) }
        permission("Разрешение менять экран", CapabilityAccess.WRITE_SETTINGS)
        section("ТАЙМ-АУТ ЭКРАНА")
        val seconds = listOf(30, 60, 120, 300, 600)
        val timeout = LegionUiSupport.spinner(ctx, listOf("Выберите значение") +
            seconds.map { if (it < 60) "$it с" else "${it / 60} мин" }).apply {
            contentDescription = "Тайм-аут экрана"
        }
        content.addView(timeout, LinearLayout.LayoutParams(-1, -2))
        content.addView(CompactActionRow(ctx, button("Применить") {
            val value = seconds.getOrNull(timeout.selectedItemPosition - 1)
            if (value == null) refresh("Сначала выберите тайм-аут.")
            else perform { SmartSystemControls.setScreenTimeout(ctx, value) }
        }))
        section("НЕ БЕСПОКОИТЬ")
        permission("Доступ к управлению DND", CapabilityAccess.DND)
        content.addView(CompactActionRow(ctx,
            button("Включить") { perform { SmartSystemControls.setDoNotDisturb(ctx, true) } },
            button("Выключить") { perform { SmartSystemControls.setDoNotDisturb(ctx, false) } }))
        section("РУЧНОЙ ВОЗВРАТ")
        content.addView(LegionUiSupport.actionTile(ctx, "Восстановить экран",
            "Яркость и тайм-аут · с сохранением внешних изменений") {
            perform { SmartSystemControls.restoreScreenSettings(ctx) }
        })
        content.addView(TextView(ctx).apply {
            text = "Настройки общесистемные. Громкость и DND меняются вручную; экран имеет отдельную запись возврата. " +
                "Активный игровой профиль блокирует ручные изменения до завершения его возврата."
            textSize = 12f
            setTextColor(LegionUiSupport.MUTED)
            setPadding(0, dp(10), 0, 0)
        })
        refresh()
        val scroll = ScrollView(ctx).apply { addView(content) }
        LegionDialogs.builder(ctx).setTitle("Системное управление").setView(scroll)
            .setPositiveButton("Закрыть", null).show()
    }
}
