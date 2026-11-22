package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * User-operated controller for the floating toolbox. No auto-launch, Root or Shizuku.
 * Android may refuse overlays on lock screen, protected surfaces or individual OEM ROMs.
 */
class GameToolboxActivity : Activity() {
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences(ToolboxPreferences.NAME, MODE_PRIVATE) }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 32.dp, 20.dp, 32.dp)
            setBackgroundColor(Color.BLACK)
        }

        fun title(value: String, size: Float) {
            root.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, 14.dp)
            })
        }
        fun note(value: String) {
            root.addView(TextView(this).apply {
                text = value
                textSize = 13f
                setTextColor(0xFFC3C3C3.toInt())
                setPadding(0, 0, 0, 14.dp)
                setLineSpacing(4f, 1f)
            })
        }
        fun action(label: String, onClick: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                minHeight = 50.dp
                setOnClickListener { onClick() }
            })
        }

        title("GAME TOOLBOX", 24f)
        note("Плавающая игровая панель для любых приложений. Работает без Root и Shizuku. " +
            "Не ускоряет CPU/GPU, не считывает ввод игры и не измеряет FPS.")
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFCE82.toInt())
            setPadding(0, 10.dp, 0, 20.dp)
            setLineSpacing(4f, 1f)
        }
        root.addView(status)

        title("Запуск", 17f)
        action("Выбрать игру или приложение") { selectGame() }
        action("РАЗРЕШИТЬ ПОВЕРХ ДРУГИХ ОКОН") { overlaySettings() }
        action("ВКЛЮЧИТЬ ПЛАВАЮЩУЮ ПАНЕЛЬ") { launchToolbox(false) }
        action("ЗАПУСТИТЬ ПАНЕЛЬ И ИГРУ") { launchToolbox(true) }
        action("ОСТАНОВИТЬ ПЛАВАЮЩУЮ ПАНЕЛЬ") {
            GameToolboxService.stop(this)
            status.text = "Запрошена остановка панели. Громкость, DND и яркость не сбрасываются автоматически."
        }

        title("Игровой профиль · действия Android", 17f)
        note("Для каждой игры можно сохранить громкость, яркость и режим «Не беспокоить». " +
            "Изменения выполняются только по нажатию, а возврат сохраняется даже после " +
            "перезапуска приложения. Настройки общесистемные, не параметры самой игры.")
        action("НАСТРОИТЬ ПРОФИЛЬ ВЫБРАННОЙ ИГРЫ") { configureGameLaunchProfile() }
        action("ПРИМЕНИТЬ ПРОФИЛЬ") { applyGameLaunchProfile(false) }
        action("ПРИМЕНИТЬ ПРОФИЛЬ И ЗАПУСТИТЬ ИГРУ") { applyGameLaunchProfile(true) }
        action("ВОССТАНОВИТЬ ПРЕЖНИЕ НАСТРОЙКИ") {
            status.text = GameLaunchProfiles.restore(this).message
        }

        title("Внешний вид", 17f)
        note("Прозрачность и диаметр кнопки сохраняются. Положение запоминается после перетаскивания.")
        action("Прозрачность · 60%") { configureOpacity(60) }
        action("Прозрачность · 80%") { configureOpacity(80) }
        action("Прозрачность · 100%") { configureOpacity(100) }
        action("Размер кнопки · компактный 44 dp") { configureSize(44) }
        action("Размер кнопки · стандарт 56 dp") { configureSize(56) }
        action("Размер кнопки · крупный 68 dp") { configureSize(68) }
        action("Сбросить положение панели") {
            prefs.edit().remove(ToolboxPreferences.POS_X).remove(ToolboxPreferences.POS_Y).apply()
            GameToolboxService.refresh(this)
            renderStatus()
        }

        title("Системные разрешения и возврат", 17f)
        action("Разрешение «Не беспокоить»") {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                .onFailure { status.text = "Не удалось открыть настройки: ${it.javaClass.simpleName}" }
        }
        action("Разрешение на изменение яркости") {
            runCatching { startActivity(SmartSystemControls.writeSettingsIntent(this)) }
                .onFailure { status.text = "Не удалось открыть настройки: ${it.javaClass.simpleName}" }
        }
        action("ВОССТАНОВИТЬ ИСХОДНУЮ ЯРКОСТЬ") {
            status.text = SmartSystemControls.restoreScreenSettings(this)
        }
        note("Громкость, DND и яркость меняются только по нажатию конкретной кнопки " +
            "в панели. Закрытие панели не отменяет системные изменения. " +
            "Исходная яркость сохраняется существующим механизмом Smart Profiles; " +
            "Ручные изменения DND нужно отменять вручную; профиль имеет отдельный возврат.")

        setContentView(ScrollView(this).apply { addView(root) })
        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) renderStatus()
    }

    private fun renderStatus() {
        val enabled = Settings.canDrawOverlays(this)
        val pkg = prefs.getString(ToolboxPreferences.GAME, null)
        val label = prefs.getString(ToolboxPreferences.LABEL, null)
        status.text = buildString {
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
                val profile = GameLaunchProfiles.profile(this@GameToolboxActivity, pkg)
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
            GameLaunchProfiles.pendingPackage(this@GameToolboxActivity)?.let {
                append("\nОжидает ручного возврата: ").append(it)
            }
        }
    }

    private fun selectGame() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val applications = packageManager.queryIntentActivities(
            query, PackageManager.ResolveInfoFlags.of(0L)
        ).filter { it.activityInfo?.packageName != null && it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (applications.isEmpty()) {
            status.text = "Android не показывает запускаемые приложения через MAIN/LAUNCHER."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Запуск выбранного приложения")
            .setItems(applications.map {
                it.loadLabel(packageManager).toString() + "\n" + it.activityInfo.packageName
            }.toTypedArray()) { _, index ->
                val app = applications[index]
                prefs.edit()
                    .putString(ToolboxPreferences.GAME, app.activityInfo.packageName)
                    .putString(ToolboxPreferences.LABEL, app.loadLabel(packageManager).toString())
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
        AlertDialog.Builder(this)
            .setTitle("Громкость мультимедиа")
            .setItems(volumes.map { it?.let { v -> "$v%" } ?: "Не менять" }.toTypedArray()) { _, vi ->
                AlertDialog.Builder(this)
                    .setTitle("Яркость")
                    .setItems(brightness.map { it?.let { v -> "$v%" } ?: "Не менять" }.toTypedArray()) { _, bi ->
                        AlertDialog.Builder(this)
                            .setTitle("Режим «Не беспокоить»")
                            .setItems(arrayOf("Не менять", "Включить", "Выключить")) { _, fi ->
                                val profile = GameLaunchProfile(volumes[vi], brightness[bi], focus[fi])
                                status.text = if (GameLaunchProfiles.save(this, pkg, profile)) {
                                    if (profile.actionable) "Профиль игры сохранён." else "Профиль игры удалён."
                                } else "Android не смог сохранить профиль."
                                // Do not call renderStatus here: preserve actionable feedback.
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
        val launchIntent = if (withGame) packageManager.getLaunchIntentForPackage(pkg) else null
        if (withGame && launchIntent == null) {
            status.text = "Android не разрешает запустить выбранную игру."
            return
        }
        val result = GameLaunchProfiles.apply(this, pkg)
        status.text = result.message
        if (result.success && launchIntent != null) {
            runCatching { startActivity(launchIntent) }
                .onFailure { status.text = "Профиль применён, но запуск игры отклонён: " +
                    it.javaClass.simpleName }
        }
    }

    private fun overlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
            .onFailure { status.text = "Системные настройки недоступны: ${it.javaClass.simpleName}" }
    }

    private fun configureOpacity(value: Int) {
        prefs.edit().putInt(ToolboxPreferences.OPACITY, ToolboxTuning.opacity(value)).apply()
        GameToolboxService.refresh(this)
        renderStatus()
    }

    private fun configureSize(value: Int) {
        prefs.edit().putInt(ToolboxPreferences.SIZE, ToolboxTuning.sizeDp(value)).apply()
        GameToolboxService.refresh(this)
        renderStatus()
    }

    private fun launchToolbox(withGame: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "Сначала выдайте специальное разрешение «Поверх других окон»."
            overlaySettings()
            return
        }
        val launchIntent = if (withGame) {
            prefs.getString(ToolboxPreferences.GAME, null)?.let {
                packageManager.getLaunchIntentForPackage(it)
            }
        } else null
        if (withGame && launchIntent == null) {
            status.text = "Приложение не выбрано либо больше недоступно для запуска."
            return
        }
        val started = runCatching { GameToolboxService.start(this) }
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
