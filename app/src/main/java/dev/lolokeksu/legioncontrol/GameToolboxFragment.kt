package dev.lolokeksu.legioncontrol

import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Locale

/**
 * Game Toolbox controller within the canonical Game Hub shell.
 * Uses the same palette, header owner, bottom navigation and card rhythm as HubPageFragment.
 * All settings, permissions and game launches remain explicit user actions.
 */
class GameToolboxFragment : Fragment() {
    private val bg = Color.BLACK
    private val fg = Color.WHITE
    private val muted = LegionUiSupport.MUTED
    private val accent = LegionUiSupport.ACCENT
    private val border = LegionUiSupport.BORDER
    private val panel = LegionUiSupport.PANEL
    private lateinit var column: LinearLayout
    private lateinit var summary: TextView
    private lateinit var status: TextView
    private val prefs by lazy {
        requireContext().getSharedPreferences(ToolboxPreferences.NAME, Context.MODE_PRIVATE)
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun outline(fill: Int = panel): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = 17.dp.toFloat()
        setStroke(1.dp, border)
    }

    private fun text(value: String, size: Float = 14f, color: Int = fg, bold: Boolean = false) =
        TextView(requireContext()).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            setLineSpacing(3f, 1f)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun section(title: String, detail: String) {
        column.addView(text(title, 22f, fg, true).apply { setPadding(0, 8.dp, 0, 7.dp) })
        column.addView(text(detail, 13f, muted).apply { setPadding(0, 0, 0, 15.dp) })
    }

    private fun group(label: String) {
        column.addView(text(label.uppercase(Locale.ROOT), 12f, accent, true).apply {
            setPadding(0, 18.dp, 0, 11.dp)
            letterSpacing = 0.08f
        })
    }

    private fun tile(title: String, detail: String, featureId: String? = null, onClick: () -> Unit) {
        column.addView(
            LegionUiSupport.actionTile(requireContext(), title, detail, onClick).apply {
                if(featureId!=null) FeatureReveal.bind(this,featureId)
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp }
        )
    }

    private fun choices(
        title: String,
        options: List<Pair<Int, String>>,
        selected: () -> Int,
        onSelect: (Int) -> Unit
    ) {
        column.addView(text(title, 13f, muted).apply {
            setPadding(0, 4.dp, 0, 9.dp)
        })
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val chips = mutableListOf<Pair<Int, TextView>>()
        fun update() {
            for ((value, chip) in chips) {
                val checked = value == selected()
                chip.isSelected = checked
                chip.stateDescription = if (checked) "Выбрано" else "Не выбрано"
                chip.setTextColor(if (checked) accent else fg)
                chip.background = RippleDrawable(
                    ColorStateList.valueOf(0x4486B7FF),
                    GradientDrawable().apply {
                        setColor(if (checked) 0xFF1B2B42.toInt() else panel)
                        cornerRadius = 14.dp.toFloat()
                        setStroke(1.dp, if (checked) accent else border)
                    }, null
                )
            }
        }
        for ((value, label) in options) {
            val chip = text(label, 13f, fg, true).apply {
                gravity = Gravity.CENTER
                minHeight = 48.dp
                isClickable = true
                isFocusable = true
                contentDescription = "$title: $label"
                setOnClickListener { onSelect(value); update() }
            }
            chips.add(value to chip)
            row.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = 3.dp
                rightMargin = 3.dp
                bottomMargin = 10.dp
            })
        }
        column.addView(row)
        update()
    }

    override fun onCreateView(
        inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val scroll = ScrollView(requireContext()).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(bg)
        }
        column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 20.dp, 18.dp, 28.dp)
            setBackgroundColor(bg)
        }
        scroll.addView(column)
        section("GAME TOOLBOX", "Плавающая игровая панель, профили и настройки Android. " +
            "Работает без Root и Shizuku; не ускоряет CPU/GPU и не измеряет FPS.")

        group("Текущее состояние")
        val stateCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = outline()
            setPadding(16.dp, 15.dp, 16.dp, 15.dp)
        }
        summary = text("", 13f, muted)
        stateCard.addView(summary)
        status = text("Ручные действия или отдельно подтверждённая авто-сессия.", 12f, accent).apply {
            setPadding(0, 10.dp, 0, 0)
            isFocusable = true
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        stateCard.addView(status)
        column.addView(stateCard, LinearLayout.LayoutParams(-1, -2))

        group("Запуск панели")
        tile("Выбрать игру или приложение", "Список приложений из Android Launcher") { selectGame() }
        tile("Разрешение поверх других окон", "Открыть системные настройки доступа") { overlaySettings() }
        tile("Включить плавающую панель", "Показать кнопку поверх приложений") { launchToolbox(false) }
        tile("Запустить панель и игру", "Открыть выбранную игру вместе с панелью") { launchToolbox(true) }
        tile("Остановить плавающую панель", "Завершает авто-сессию; ручной профиль возвращается отдельно") {
            GameToolboxService.stop(requireContext())
            status.text = "Запрошена остановка панели. Авто-сессия попробует вернуть только свои изменения."
        }

        group("Игровой профиль")
        column.addView(text(
            "Сохраняет громкость, яркость и «Не беспокоить» для выбранной игры. " +
                "Изменения общесистемные; автоматический режим требует отдельного согласия.",
            13f, muted
        ).apply { setPadding(0, 0, 0, 12.dp) })
        tile("Настроить профиль", "Выбрать громкость, яркость и режим DND", "toolbox.profile") {
            configureGameLaunchProfile()
        }
        tile("Применить профиль", "Без запуска игры") { applyGameLaunchProfile(false) }
        tile("Применить профиль и запустить игру", "Сохранить возможность отката") {
            applyGameLaunchProfile(true)
        }
        tile("Восстановить прежние настройки", "Вернуть сохранённое состояние Android", "toolbox.undo") {
            status.text = GameLaunchProfiles.restore(requireContext()).message
            renderStatus()
        }

        group("Game Automation")
        tile("Доступ к статистике использования", "Android Usage Access; нужен только для авто-сессии") {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .onFailure { status.text="Не удалось открыть Usage Access." }
        }
        tile("Авто-профиль · запустить игру", "Одноразовая сессия, возврат при наблюдаемом выходе") { startAutomation() }
        tile("Завершить авто-сессию", "Остановить наблюдение и вернуть собственные изменения") {
            runCatching { GameToolboxService.stopAutomation(requireContext()) }
                .onFailure { status.text="Не удалось отправить запрос остановки." }
            status.text="Запрошена остановка автоматизации. Проверяйте запись возврата."
        }
        tile("Последний отчёт автоматизации", "Локальное наблюдаемое время и результат возврата") {
            LegionDialogs.builder(requireContext()).setTitle("Game Automation")
                .setMessage(GameAutomationController.lastReport(requireContext()) ?: "Отчётов пока нет.")
                .setPositiveButton("Закрыть",null).show()
        }

        group("Внешний вид")
        choices("Прозрачность плавающей кнопки",
            listOf(60 to "60%", 80 to "80%", 85 to "85%", 100 to "100%"),
            { ToolboxTuning.opacity(prefs.getInt(ToolboxPreferences.OPACITY, 85)) },
            { configureOpacity(it) })
        choices("Размер плавающей кнопки",
            listOf(44 to "44 dp", 56 to "56 dp", 68 to "68 dp"),
            { ToolboxTuning.sizeDp(prefs.getInt(ToolboxPreferences.SIZE, 56)) },
            { configureSize(it) })
        tile("Сбросить положение панели", "Положение запоминается после перетаскивания") {
            prefs.edit().remove(ToolboxPreferences.POS_X).remove(ToolboxPreferences.POS_Y).apply()
            GameToolboxService.refresh(requireContext())
            renderStatus()
        }

        group("Системные разрешения и возврат")
        tile("Доступ к режиму «Не беспокоить»", "Управляется настройками Android") {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                .onFailure { status.text = "Не удалось открыть настройки: ${it.javaClass.simpleName}" }
        }
        tile("Разрешение изменять яркость", "Управляется настройками Android") {
            runCatching { startActivity(SmartSystemControls.writeSettingsIntent(requireContext())) }
                .onFailure { status.text = "Не удалось открыть настройки: ${it.javaClass.simpleName}" }
        }
        tile("Восстановить исходную яркость", "Использует сохранённое состояние Smart Profiles") {
            status.text = SmartSystemControls.restoreScreenSettings(requireContext())
        }
        column.addView(text(
            "Закрытие панели пытается вернуть только принадлежащие автоматизации настройки. Ручные изменения DND " +
                "нужно отменять вручную; игровой профиль имеет отдельный возврат.",
            12f, muted
        ).apply { setPadding(0, 4.dp, 0, 20.dp) })
        renderStatus()
        FeatureReveal.fromFragment(this, scroll, FeatureModule.TOOLBOX)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        if (::summary.isInitialized) renderStatus()
    }

    private fun renderStatus() {
        val enabled = Settings.canDrawOverlays(requireContext())
        val pkg = prefs.getString(ToolboxPreferences.GAME, null)
        val label = prefs.getString(ToolboxPreferences.LABEL, null)
        summary.text = buildString {
            append("Показ поверх окон: ")
            append(if (enabled) "РАЗРЕШЁН" else "НЕТ ДОСТУПА")
            append("\nВыбрано: ").append(label ?: "не выбрано")
            if (pkg != null) append(" (").append(pkg).append(")")
            append("\nПрозрачность: ")
            append(ToolboxTuning.opacity(prefs.getInt(ToolboxPreferences.OPACITY, 85)))
            append("% · размер: ")
            append(ToolboxTuning.sizeDp(prefs.getInt(ToolboxPreferences.SIZE, 56)))
            append(" dp")
            if (pkg != null) {
                val profile = GameLaunchProfiles.profile(requireContext(), pkg)
                append("\nИгровой профиль: ")
                append(profile?.let {
                    buildString {
                        append("звук ").append(it.mediaPercent?.let { v -> "$v%" } ?: "не менять")
                        append(" · яркость ").append(it.brightnessPercent?.let { v -> "$v%" } ?: "не менять")
                        append(" · DND ").append(when (it.focusEnabled) {
                            true -> "вкл"; false -> "выкл"; null -> "не менять"
                        })
                    }
                } ?: "не настроен")
            }
            GameLaunchProfiles.pendingPackage(requireContext())?.let {
                append("\nОжидает ручного возврата: ").append(it)
            }
        }
    }

    private fun selectGame() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val applications = requireContext().packageManager.queryIntentActivities(
            query, PackageManager.ResolveInfoFlags.of(0L)
        ).filter { it.activityInfo?.packageName != null && it.activityInfo.packageName != requireContext().packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(requireContext().packageManager).toString().lowercase() }
        if (applications.isEmpty()) {
            status.text = "Android не показывает запускаемые приложения через MAIN/LAUNCHER."
            return
        }
        LegionDialogs.builder(requireContext())
            .setTitle("Запуск выбранного приложения")
            .setItems(applications.map {
                it.loadLabel(requireContext().packageManager).toString() + "\n" + it.activityInfo.packageName
            }.toTypedArray()) { _, index ->
                val app = applications[index]
                prefs.edit()
                    .putString(ToolboxPreferences.GAME, app.activityInfo.packageName)
                    .putString(ToolboxPreferences.LABEL, app.loadLabel(requireContext().packageManager).toString())
                    .apply()
                renderStatus()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun configureGameLaunchProfile() {
        val pkg = prefs.getString(ToolboxPreferences.GAME, null)
        if (pkg == null || !GameLaunchProfilePolicy.validPackage(pkg)) {
            status.text = "Сначала выберите игру."
            return
        }
        val volumes: List<Int?> = listOf(null, 0, 20, 40, 60, 80, 100)
        val brightness: List<Int?> = listOf(null, 20, 40, 60, 80, 100)
        val focus: List<Boolean?> = listOf(null, true, false)
        LegionDialogs.builder(requireContext())
            .setTitle("Громкость мультимедиа")
            .setItems(volumes.map { it?.let { v -> "$v%" } ?: "Не менять" }.toTypedArray()) { _, vi ->
                LegionDialogs.builder(requireContext())
                    .setTitle("Яркость")
                    .setItems(brightness.map { it?.let { v -> "$v%" } ?: "Не менять" }.toTypedArray()) { _, bi ->
                        LegionDialogs.builder(requireContext())
                            .setTitle("Режим «Не беспокоить»")
                            .setItems(arrayOf("Не менять", "Включить", "Выключить")) { _, fi ->
                                val profile = GameLaunchProfile(volumes[vi], brightness[bi], focus[fi])
                                status.text = if (GameLaunchProfiles.save(requireContext(), pkg, profile)) {
                                    if (profile.actionable) "Профиль игры сохранён." else "Профиль игры удалён."
                                } else "Android не смог сохранить профиль."
                                // Refresh the summary without replacing the separate actionable feedback.
                                renderStatus()
                            }
                            .setNegativeButton("Отмена", null).show()
                    }
                    .setNegativeButton("Отмена", null).show()
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun applyGameLaunchProfile(withGame: Boolean) {
        val pkg = prefs.getString(ToolboxPreferences.GAME, null)
        if (pkg == null) {
            status.text = "Сначала выберите игру."
            return
        }
        val launchIntent = if (withGame) requireContext().packageManager.getLaunchIntentForPackage(pkg) else null
        if (withGame && launchIntent == null) {
            status.text = "Android не разрешает запустить выбранную игру."
            return
        }
        val result = GameLaunchProfiles.apply(requireContext(), pkg)
        status.text = result.message
        if (result.success && launchIntent != null) {
            runCatching { startActivity(launchIntent) }
                .onFailure { failure ->
                    // System settings are global, so a failed activity launch
                    // must attempt the safe compare-before-restore immediately.
                    val rollback = GameLaunchProfiles.restore(requireContext())
                    status.text = "Игра не запущена: " + failure.javaClass.simpleName +
                        ". Возврат системных настроек: " + rollback.message
                }
        }
    }

    private fun startAutomation() {
        val ctx=requireContext()
        val pkg=prefs.getString(ToolboxPreferences.GAME,null)
        if(pkg==null || !GameLaunchProfilePolicy.validPackage(pkg) || pkg==ctx.packageName) {
            status.text="Сначала выберите игру.";return
        }
        val profile=GameLaunchProfiles.profile(ctx,pkg)
        if(profile==null || !profile.actionable){status.text="Сначала сохраните профиль.";return}
        if(GameLaunchProfiles.pendingPackage(ctx)!=null){status.text="Сначала восстановите предыдущий профиль.";return}
        if(!Settings.canDrawOverlays(ctx)){status.text="Разрешите показ поверх окон.";overlaySettings();return}
        if(!GameUsageDiagnostics.hasUsageAccess(ctx)){status.text="Выдайте Usage Access вручную.";return}
        val nm=ctx.getSystemService(android.app.NotificationManager::class.java)
        if(nm?.areNotificationsEnabled()!=true){status.text="Разрешите уведомления для явного контроля авто-сессии.";return}
        val launch=ctx.packageManager.getLaunchIntentForPackage(pkg)
        if(launch==null){status.text="Игра недоступна для запуска.";return}
        LegionDialogs.builder(ctx).setTitle("Включить одноразовую автоматизацию?")
            .setMessage("Приложение: "+pkg+"\nГромкость: "+(profile.mediaPercent?.let{"$it%"} ?: "не менять")+
                " · яркость: "+(profile.brightnessPercent?.let{"$it%"} ?: "не менять")+
                " · DND: "+(profile.focusEnabled?.let{if(it)"вкл" else "выкл"} ?: "не менять")+
                "\nПанель наблюдает события Android только до завершения этой сессии (максимум 2 часа). При выходе, блокировке или остановке попытка вернуть собственные значения. Android может задержать события или завершить процесс; тогда возврат доступен вручную. Уведомление должно оставаться включено.")
            .setPositiveButton("Включить и открыть"){_,_->
                val result=runCatching { GameToolboxService.startAutomation(ctx,pkg,profile) }
                if(result.isFailure){status.text="Запуск автоматизации отклонён Android.";return@setPositiveButton}
                runCatching{startActivity(launch)}.onFailure{
                    runCatching{GameToolboxService.stopAutomation(ctx)}
                    status.text="Игра не запущена. Запрошено завершение авто-сессии."
                }
            }.setNegativeButton("Отмена",null).show()
    }

    private fun overlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireContext().packageName}"))
        runCatching { startActivity(intent) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
            .onFailure { status.text = "Системные настройки недоступны: ${it.javaClass.simpleName}" }
    }

    private fun configureOpacity(value: Int) {
        prefs.edit().putInt(ToolboxPreferences.OPACITY, ToolboxTuning.opacity(value)).apply()
        GameToolboxService.refresh(requireContext())
        renderStatus()
    }

    private fun configureSize(value: Int) {
        prefs.edit().putInt(ToolboxPreferences.SIZE, ToolboxTuning.sizeDp(value)).apply()
        GameToolboxService.refresh(requireContext())
        renderStatus()
    }

    private fun launchToolbox(withGame: Boolean) {
        if (!Settings.canDrawOverlays(requireContext())) {
            status.text = "Сначала выдайте специальное разрешение «Поверх других окон»."
            overlaySettings()
            return
        }
        val launchIntent = if (withGame) {
            prefs.getString(ToolboxPreferences.GAME, null)?.let {
                requireContext().packageManager.getLaunchIntentForPackage(it)
            }
        } else null
        if (withGame && launchIntent == null) {
            status.text = "Приложение не выбрано либо больше недоступно для запуска."
            return
        }
        val started = runCatching { GameToolboxService.start(requireContext()) }
        if (started.isFailure) {
            status.text = "Не удалось запустить Game Toolbox: " +
                started.exceptionOrNull()?.javaClass?.simpleName
            return
        }
        if (launchIntent != null) {
            runCatching { startActivity(launchIntent) }
                .onFailure { status.text = "Не удалось открыть игру: ${it.javaClass.simpleName}" }
        } else {
            status.text = "Запуск панели запрошен. Проверьте плавающую кнопку и уведомление."
        }
    }
}
