package dev.lolokeksu.legioncontrol

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Five real Navigation Component destinations, all backed by one Activity-scoped ViewModel. */
class HubPageFragment : Fragment() {
    private val bg = Color.BLACK
    private val fg = Color.WHITE
    private val muted = LegionUiSupport.MUTED
    private val accent = LegionUiSupport.ACCENT
    private val border = LegionUiSupport.BORDER
    private val panel = LegionUiSupport.PANEL
    private lateinit var viewModel: HubViewModel
    private lateinit var column: LinearLayout
    private var query = ""
    private var showAllApps = false
    private var toolQuery = ""
    private data class CatalogAction(
        val title: String,
        val description: String,
        val launch: () -> Unit
    )
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, saved: Bundle?): View {
        val root = ScrollView(requireContext()).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            clipToPadding = false
        }
        column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 20.dp, 18.dp, 28.dp)
            setBackgroundColor(bg)
        }
        root.addView(column)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[HubViewModel::class.java]
        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.actionResult.observe(viewLifecycleOwner) { message ->
            if (message.isNotBlank() && isAdded && viewModel.state.value is HubLoadState.Failed) {
                info(message)
            }
        }
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.refresh()
    }

    private fun outline(color: Int = panel): GradientDrawable = GradientDrawable().apply {
        setColor(color)
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

    private fun section(title: String, detail: String? = null) {
        column.addView(text(title, 22f, fg, true).apply {
            setPadding(0, 8.dp, 0, 7.dp)
        })
        if (detail != null) column.addView(text(detail, 13f, muted).apply {
            setPadding(0, 0, 0, 20.dp)
        })
    }

    private fun info(message: String) {
        column.addView(text(message, 13f, muted).apply {
            setPadding(0, 6.dp, 0, 16.dp)
        })
    }

    private fun tile(title: String, description: String, onClick: () -> Unit) {
        column.addView(
            LegionUiSupport.actionTile(requireContext(), title, description, onClick),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp }
        )
    }

    private fun group(label: String) {
        column.addView(text(label.uppercase(Locale.ROOT), 12f, accent, true).apply {
            setPadding(0, 19.dp, 0, 11.dp)
            letterSpacing = 0.08f
        })
    }

    private fun <T> open(cls: Class<T>, extras: Bundle? = null) {
        if (cls == GameSessionActivity::class.java) {
            runCatching { NavHostFragment.findNavController(this).navigate(R.id.gameSessionFragment,extras) }
                .onFailure { error ->
                    AlertDialog.Builder(requireContext()).setTitle("Не удалось открыть Game Session")
                        .setMessage(error.javaClass.simpleName)
                        .setPositiveButton("Понятно",null).show()
                }
            return
        }
        runCatching {
            val route = HubDestinationRoutes.destinationFor(cls)
            if (route != null) {
                NavHostFragment.findNavController(this).navigate(route, extras)
            } else {
                // Only unregistered legacy screen classes use an explicit Intent fallback.
                startActivity(Intent(requireContext(), cls).apply {
                    if (extras != null) putExtras(extras)
                })
            }
        }.onFailure { exception ->
            AlertDialog.Builder(requireContext()).setTitle("Не удалось открыть раздел")
                .setMessage(exception.javaClass.simpleName)
                .setPositiveButton("Понятно", null).show()
        }
    }

    private fun page() = HubTab.parse(arguments?.getString("page"))

    private fun render(state: HubLoadState) {
        if (!isAdded || !::column.isInitialized) return
        column.removeAllViews()
        when (state) {
            HubLoadState.Loading -> {
                if (page() == HubTab.TOOLS) {
                    showTools()
                    group("Сводка устройства")
                    info("Чтение локальных данных продолжается. Инструменты доступны.")
                } else {
                    section("LEGION · GAME HUB", "Чтение локальной библиотеки и системных данных")
                    info("Загружаю данные устройства…")
                }
            }
            is HubLoadState.Failed -> {
                if (page() == HubTab.TOOLS) {
                    showTools()
                    group("Не удалось прочитать сводку")
                    info(state.message)
                } else {
                    section("Не удалось загрузить")
                    info(state.message)
                    showLegacyShortcuts()
                }
                tile("Повторить чтение", "Без удаления данных и изменения системных настроек") {
                    viewModel.refresh()
                }
            }
            is HubLoadState.Ready -> {
                when (page()) {
                    HubTab.HOME -> showHome(state.snapshot)
                    HubTab.GAMES -> showGames(state.snapshot)
                    HubTab.TOOLS -> showTools()
                    HubTab.HISTORY -> showHistory(state.snapshot)
                    HubTab.SETTINGS -> showSettings(state.snapshot)
                }
                if (state.snapshot.warnings.isNotEmpty()) {
                    group("Ограничения доступа")
                    state.snapshot.warnings.forEach(::info)
                }
            }
        }
    }

    private fun showHome(s: HubSnapshot) {
        section("Готов к запуску", "Игры, инструменты и локальная история — в одном месте.")
        val stats = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun stat(value: String, label: String) {
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = outline()
                setPadding(8.dp, 17.dp, 8.dp, 17.dp)
            }
            box.addView(text(value, 22f, accent, true))
            box.addView(text(label, 11f, muted))
            stats.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = 3.dp
                rightMargin = 3.dp
            })
        }
        stat(s.favoriteCount.toString(), "Избранное")
        stat(s.profileCount.toString(), "Профили")
        stat(s.clipPreviewCount.toString(), "Клипы*")
        column.addView(stats)
        info("* Быстрая сводка: не более 40 последних MP4. Все записи — в Capture Studio.")
        group("Быстрый доступ")
        tile("Мои игры", "${s.apps.count { it.isGame || it.favorite }} игровых и избранных записей") {
            NavHostFragment.findNavController(this).navigate(R.id.gamesFragment)
        }
        tile("GAME TOOLBOX", "Плавающая игровая панель по запросу") {
            open(GameToolboxActivity::class.java)
        }
        tile("CAPTURE STUDIO", "Записать MP4 после согласия Android") {
            open(CaptureStudioActivity::class.java)
        }
        tile("Игровая сессия", "Ручная запись телеметрии без FPS-имитации") {
            open(GameSessionActivity::class.java)
        }
        group("Состояние Android")
        tile("Батарея · ${s.batteryPercent?.let { "$it%" } ?: "нет данных"}",
            "Частота дисплея: ${s.refreshHz?.let { String.format(Locale.US, "%.1f Гц", it) } ?: "нет данных"} · это не FPS игры") {
            open(MainActivity::class.java)
        }
        if (s.recentSessions.isNotEmpty()) {
            group("Последняя игровая сессия")
            val last = s.recentSessions.first()
            tile(last.appLabel, "Начало: " + DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(Date(last.startedAtMs))) {
                open(GameSessionActivity::class.java)
            }
        }
    }

    private fun showGames(s: HubSnapshot) {
        section("Игровая библиотека",
            "Настоящие Android-приложения из Launcher и избранное Application Center.")
        group("Игровые функции")
        tile("Game Session", "Запись состояния устройства во время игры") {
            open(GameSessionActivity::class.java)
        }
        tile("Game Toolbox", "Плавающая игровая панель управления") {
            open(GameToolboxActivity::class.java)
        }
        tile("Smart Profiles", "Профили и правила для приложений") {
            open(SmartProfilesActivity::class.java)
        }
        group("Библиотека")
        tile("Управление библиотекой", "Категории, заметки, сохранённые приложения") {
            open(ApplicationCenterActivity::class.java)
        }
        tile(if (showAllApps) "Показываются все приложения · изменить" else "Игры и избранное · показать все",
            "Видимость определяется Android, не скрытым поиском") {
            showAllApps = !showAllApps
            render(viewModel.state.value ?: HubLoadState.Loading)
        }
        val input = EditText(requireContext()).apply {
            hint = "Поиск игры или имени пакета"
            setTextColor(fg)
            setHintTextColor(muted)
            textSize = 14f
            isSingleLine = true
            setPadding(15.dp, 14.dp, 15.dp, 14.dp)
            background = outline()
            setText(query)
            contentDescription = "Поиск игр и приложений"
        }
        column.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 13.dp })
        val list = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        column.addView(list)
        fun redraw() {
            list.removeAllViews()
            val filtered = s.apps.filter {
                (showAllApps || it.isGame || it.favorite) &&
                    HubSearch.matches(it.label, it.packageName, query)
            }
            if (filtered.isEmpty()) {
                list.addView(text("Совпадений нет. Нажмите «Показать все» или добавьте игру в Application Center.", 13f, muted))
                return
            }
            if (filtered.size > 60) list.addView(text(
                "Показаны первые 60 из ${filtered.size}. Уточните поиск.", 12f, muted))
            filtered.take(60).forEach { app ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = outline()
                    setPadding(15.dp, 11.dp, 13.dp, 11.dp)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Открыть карточку " + app.label
                    setOnClickListener {
                        NavHostFragment.findNavController(this@HubPageFragment).navigate(
                            R.id.gameDetailFragment, Bundle().apply {
                                putString("gamePackage", app.packageName)
                            })
                    }
                }
                val monogram = text(app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                    20f, accent, true).apply { gravity = Gravity.CENTER }
                row.addView(monogram, LinearLayout.LayoutParams(37.dp, 49.dp))
                val labels = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text((if (app.favorite) "★  " else "") + app.label, 14f, fg, true))
                labels.addView(text(if (app.visible) app.packageName else "Не видно через Launcher · ${app.packageName}",
                    11f, muted))
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(text("›", 22f, accent))
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9.dp })
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                redraw()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        redraw()
    }

    /** Search results reveal existing controls; opening a result never runs the action. */
    private fun openStandaloneFeature(cls: Class<*>, args: Bundle) {
        runCatching { startActivity(Intent(requireContext(), cls).putExtras(args)) }
            .onFailure {
                AlertDialog.Builder(requireContext()).setTitle("Не удалось открыть действие")
                    .setMessage(it.javaClass.simpleName).setPositiveButton("Понятно", null).show()
            }
    }

    private fun openFeature(entry: FeatureEntry) {
        val args = Bundle().apply { putString(FeatureFinder.EXTRA_FEATURE_ID, entry.id) }
        when (entry.module) {
            FeatureModule.CAPTURE -> openStandaloneFeature(CaptureStudioActivity::class.java, args)
            FeatureModule.AUDIO -> open(AudioLabActivity::class.java, args)
            FeatureModule.HAPTIC -> open(HapticLabActivity::class.java, args)
            FeatureModule.NETWORK -> open(ConnectivityLabActivity::class.java, args)
            FeatureModule.PROFILE -> open(SmartProfilesActivity::class.java, args)
            FeatureModule.APPS -> open(ApplicationCenterActivity::class.java, args)
            FeatureModule.SESSION -> open(GameSessionActivity::class.java, args)
            FeatureModule.TOOLBOX -> open(GameToolboxActivity::class.java, args)
            FeatureModule.GAMING -> open(GamingDiagnosticsActivity::class.java, args)
            FeatureModule.HARDWARE -> open(HardwareLabActivity::class.java, args)
            FeatureModule.COMPAT -> open(CompatibilityLabActivity::class.java, args)
            FeatureModule.EXPERIMENT -> open(ExperimentLabActivity::class.java, args)
            FeatureModule.SIGNALS -> NavHostFragment.findNavController(this).navigate(R.id.sessionSignalsFragment, args)
            FeatureModule.INCIDENTS -> NavHostFragment.findNavController(this).navigate(R.id.sessionIncidentsFragment, args)
            FeatureModule.BASELINES -> NavHostFragment.findNavController(this).navigate(R.id.sessionBaselinesFragment, args)
            FeatureModule.DIAGNOSTICS -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.GAMEPAD -> openStandaloneFeature(GamepadLiveActivity::class.java, args)
        }
    }

    /** Searchable feature catalog; launches the existing real modules without migrating their data. */
    private fun showTools() {
        section("Инструменты", "Найдите модуль или конкретную функцию: Replay, ZIP, стерео, DNS.")
        val catalog = listOf(
            "Запись и медиа" to listOf(
                CatalogAction("Capture Studio", "Запись экрана и библиотека MP4") { open(CaptureStudioActivity::class.java) },
                CatalogAction("Audio Lab", "Стерео, частоты и аудиомаршруты") { open(AudioLabActivity::class.java) }
            ),
            "Экран и управление" to listOf(
                CatalogAction("Display Patterns", "Проверка дисплея") { open(DisplayPatternActivity::class.java) },
                CatalogAction("Haptic Lab", "Вибрация и тактильная отдача") { open(HapticLabActivity::class.java) },
                CatalogAction("Gamepad Live", "Кнопки и стики контроллера") { open(GamepadLiveActivity::class.java) }
            ),
            "Система и производительность" to listOf(
                CatalogAction("Gaming Lab", "Батарея, сеть, сенсоры и производительность") { open(GamingDiagnosticsActivity::class.java) },
                CatalogAction("Hardware Lab", "Аппаратные измерения и тесты") { open(HardwareLabActivity::class.java) },
                CatalogAction("Compatibility Lab", "Совместимость функций Android") { open(CompatibilityLabActivity::class.java) },
                CatalogAction("Диагностика Kotlin + Rust", "Батарея, датчики и отчёты CSV/ZIP") { open(MainActivity::class.java) }
            ),
            "Сеть" to listOf(
                CatalogAction("Connectivity Observatory", "DNS, IPv4/IPv6, TCP и TLS") { open(ConnectivityLabActivity::class.java) }
            ),
            "Эксперименты" to listOf(
                CatalogAction("Experiment Lab", "A/B-сравнения и заметки") { open(ExperimentLabActivity::class.java) }
            )
        )
        val input = EditText(requireContext()).apply {
            hint = "Поиск функций и инструментов"
            setTextColor(fg)
            setHintTextColor(muted)
            textSize = 14f
            isSingleLine = true
            setPadding(15.dp, 14.dp, 15.dp, 14.dp)
            background = outline()
            contentDescription = "Поиск по инструментам Legion Control"
            setText(toolQuery)
        }
        column.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12.dp })
        val results = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        column.addView(results)

        fun redraw() {
            results.removeAllViews()
            val filter = toolQuery.trim().lowercase(Locale.ROOT)
            var shown = 0
            for ((category, entries) in catalog) {
                val matching = entries.filter { entry ->
                    filter.isEmpty() || (category + " " + entry.title + " " + entry.description)
                        .lowercase(Locale.ROOT).contains(filter)
                }
                if (matching.isEmpty()) continue
                results.addView(text(category.uppercase(Locale.ROOT), 12f, accent, true).apply {
                    setPadding(0, 19.dp, 0, 11.dp)
                    letterSpacing = 0.08f
                })
                for (entry in matching) {
                    results.addView(
                        LegionUiSupport.actionTile(requireContext(), entry.title, entry.description, entry.launch),
                        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp }
                    )
                }
                shown += matching.size
            }
            if (filter.isNotEmpty()) {
                val functions = FeatureFinder.search(filter)
                if (functions.isNotEmpty()) {
                    results.addView(LegionUiSupport.sectionLabel(requireContext(), "КОНКРЕТНЫЕ ФУНКЦИИ"))
                    functions.forEach { entry ->
                        results.addView(LegionUiSupport.actionTile(
                            requireContext(), entry.title,
                            "${entry.module.displayName} · открыть действие"
                        ) { openFeature(entry) }, LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = 10.dp
                        })
                    }
                    shown += functions.size
                }
            }
            if (shown == 0) {
                results.addView(text("Инструменты не найдены. Попробуйте другое название.", 13f, muted))
                results.addView(
                    LegionUiSupport.actionTile(requireContext(), "Сбросить поиск",
                        "Показать все инструменты") { input.setText("") },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp }
                )
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                toolQuery = s?.toString().orEmpty()
                redraw()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        redraw()
    }

    private fun showHistory(s: HubSnapshot) {
        section("История", "Записи, события и результаты ручных проверок.")
        group("Записи")
        tile("Игровые сессии", "Просмотр сохранённых Game Session") { open(GameSessionActivity::class.java) }
        tile("Клипы MP4", "Movies/LegionControl/ · локальная библиотека") { open(CaptureStudioActivity::class.java) }
        group("Анализ игровых сеансов")
        tile("Session Signals", "Хронология событий, метки, разрывы телеметрии и экспорт") {
            NavHostFragment.findNavController(this).navigate(R.id.sessionSignalsFragment)
        }
        tile("Session Baselines", "Медиана прошлых сессий, исключения и экспорт") {
            NavHostFragment.findNavController(this).navigate(R.id.sessionBaselinesFragment)
        }
        tile("Session Incidents", "Длительность проблемных состояний, ручные метки и CSV") {
            NavHostFragment.findNavController(this).navigate(R.id.sessionIncidentsFragment)
        }
        tile("Эксперименты", "Сравнения и заметки") { open(ExperimentLabActivity::class.java) }
        group("Данные устройства и приложений")
        tile("Измерения устройства", "Rust JNI, батарея, тепловая история и CSV") { open(MainActivity::class.java) }
        tile("История приложений", "Отслеживаемые версии и события") { open(ApplicationCenterActivity::class.java) }
        group("Последние восемь игровых сессий")
        if (s.recentSessions.isEmpty()) {
            info("Пока нет игровых сессий. Начните запись вручную через Game Session.")
        } else s.recentSessions.forEach { session ->
            val whenRecorded = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                DateFormat.SHORT).format(Date(session.startedAtMs))
            val state = if (session.endedAtMs == null) "Нет записи завершения"
                else "Завершена: " + (session.endReason ?: "нет причины")
            tile(session.appLabel, "${whenRecorded} · ${state}") {
                NavHostFragment.findNavController(this).navigate(
                    R.id.sessionIncidentsFragment,
                    Bundle().apply {
                        putString("session_id", session.id)
                        putString("smart_package", session.packageName)
                    }
                )
            }
        }
    }

    private fun systemSettings(action: String, scoped: Boolean = false) {
        val request = Intent(action).apply {
            if (scoped) data = android.net.Uri.parse("package:" + requireContext().packageName)
        }
        runCatching { startActivity(request) }
            .recoverCatching {
                if (!scoped) throw it
                startActivity(Intent(action))
            }
            .onFailure {
                AlertDialog.Builder(requireContext()).setTitle("Настройки недоступны")
                    .setMessage("Android не открыл этот экран: " + it.javaClass.simpleName)
                    .setPositiveButton("Понятно", null).show()
            }
    }

    private fun showSettings(s: HubSnapshot) {
        section("Настройки", "Разрешения, локальные данные и параметры модулей.")
        group("Разрешения Android")
        tile("Поверх других окон", if (Settings.canDrawOverlays(requireContext())) "Разрешено" else "Не разрешено") {
            systemSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
        }
        val notifications = requireContext().getSystemService(NotificationManager::class.java)
        tile("Режим «Не беспокоить»",
            if (notifications.isNotificationPolicyAccessGranted) "Специальный доступ разрешён" else "Нужен спецдоступ Android") {
            systemSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
        tile("Изменение системных настроек",
            if (Settings.System.canWrite(requireContext())) "Разрешено" else "Нужен спецдоступ Android") {
            systemSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
        }
        tile("Уведомления", if (notifications.areNotificationsEnabled()) "Разрешено" else "Отключены в Android") {
            runCatching {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName))
            }.onFailure { systemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }
        }
        tile("Использование приложений", "Официальные настройки Android") {
            systemSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
        group("Резервное копирование и файлы")
        for (id in listOf("capture.backup", "capture.restore", "capture.resume", "capture.clips")) {
            val entry = FeatureFinder.resolve(id, FeatureModule.CAPTURE) ?: continue
            tile(entry.title, "Capture Studio · локальные данные") { openFeature(entry) }
        }
        group("Параметры модулей")
        tile("Application Center", "${s.trackedCount} записей в библиотеке · избранное и заметки") {
            open(ApplicationCenterActivity::class.java)
        }
        tile("Smart Profiles", "${s.profileCount} профилей · импорт и экспорт JSON") {
            open(SmartProfilesActivity::class.java)
        }
        tile("Game Toolbox", "Размер, прозрачность, выбранная игра") {
            open(GameToolboxActivity::class.java)
        }
        tile("Capture Studio", "Качество, звук, длительность и профили записи") {
            open(CaptureStudioActivity::class.java)
        }
        group("О проекте")
        info("Legion Control ${LegionUiSupport.versionLabel(requireContext())} · Android 13+ · Kotlin + Rust. " +
            "Нет Root/Shizuku; 30/60 FPS записи — запрос кодеку, не реальные FPS игры. " +
            "SQLite и существующая релизная подпись сохранены.")
    }

    private fun showLegacyShortcuts() {
        group("Аварийный доступ к старым экранам")
        tile("Диагностика Kotlin + Rust", "Работает независимо от данных Game Hub") {
            open(MainActivity::class.java)
        }
        tile("Game Toolbox", "Запуск по запросу") { open(GameToolboxActivity::class.java) }
        tile("Capture Studio", "Видеозапись по согласию Android") {
            open(CaptureStudioActivity::class.java)
        }
    }
}
