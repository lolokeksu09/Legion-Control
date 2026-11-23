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
    private val muted = 0xFF9CA5B0.toInt()
    private val accent = 0xFF86B7FF.toInt()
    private val border = 0xFF252B33.toInt()
    private val panel = 0xFF12161C.toInt()
    private lateinit var viewModel: HubViewModel
    private lateinit var column: LinearLayout
    private var query = ""
    private var showAllApps = false
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
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = outline()
            setPadding(17.dp, 15.dp, 13.dp, 15.dp)
            isClickable = true
            isFocusable = true
            contentDescription = title
            setOnClickListener { onClick() }
        }
        val detail = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        detail.addView(text(title, 15f, fg, true))
        if (description.isNotBlank()) detail.addView(text(description, 12f, muted).apply {
            setPadding(0, 5.dp, 0, 0)
        })
        row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("›", 24f, accent), LinearLayout.LayoutParams(-2, -2))
        column.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = 10.dp
        })
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
                section("LEGION · GAME HUB", "Чтение локальной библиотеки и системных данных")
                info("Загружаю данные устройства…")
            }
            is HubLoadState.Failed -> {
                section("Не удалось загрузить")
                info(state.message)
                tile("Повторить чтение", "Без удаления данных и изменения системных настроек") {
                    viewModel.refresh()
                }
                showLegacyShortcuts()
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

    private fun showTools() {
        section("Инструменты", "Все существующие функции сохранены. Ничего не включается скрыто.")
        group("Игра и захват")
        tile("Game Toolbox", "Плавающая панель, громкость, DND и яркость") { open(GameToolboxActivity::class.java) }
        tile("Capture Studio", "Запись MP4 и локальная библиотека клипов") { open(CaptureStudioActivity::class.java) }
        tile("Game Session", "Ручная запись состояния устройства во время игры") { open(GameSessionActivity::class.java) }
        tile("Smart Profiles", "Профили, правила, резервные копии") { open(SmartProfilesActivity::class.java) }
        group("Аудио, тактильность и контроллеры")
        tile("Audio Lab", "Стереотесты, частоты и аудиомаршруты") { open(AudioLabActivity::class.java) }
        tile("Haptic Lab", "Шаблоны вибрации и обратная связь") { open(HapticLabActivity::class.java) }
        tile("Gamepad Live", "Кнопки и стики на этом экране") { open(GamepadLiveActivity::class.java) }
        group("Устройство и диагностика")
        tile("Gaming Lab", "Сеть, батарея, сенсор, кадры и связь") { open(GamingDiagnosticsActivity::class.java) }
        tile("Hardware Lab", "Системные аппаратные измерения") { open(HardwareLabActivity::class.java) }
        tile("Compatibility Lab", "Реальные Android-совместимости") { open(CompatibilityLabActivity::class.java) }
        tile("Display Patterns", "Проверка дисплея") { open(DisplayPatternActivity::class.java) }
        tile("Connectivity Observatory", "DNS/IPv4/IPv6/TCP/TLS по запросу") { open(ConnectivityLabActivity::class.java) }
        tile("Experiment Lab", "Сравнения A/B и заметки") { open(ExperimentLabActivity::class.java) }
        group("Rust и локальные данные")
        tile("Диагностика Kotlin + Rust", "Старые измерения, сенсоры, отчёты и ZIP/CSV") {
            open(MainActivity::class.java)
        }
    }

    private fun showHistory(s: HubSnapshot) {
        section("История", "Все данные остались в прежней SQLite v12. Ничего не очищено.")
        tile("Игровые сессии", "Просмотр сохранённых Game Session") { open(GameSessionActivity::class.java) }
        tile("Session Signals", "Хронология событий, метки, разрывы телеметрии и экспорт") {
            NavHostFragment.findNavController(this).navigate(R.id.sessionSignalsFragment)
        }
        tile("Session Baselines", "Медиана прошлых сессий, исключения и экспорт") {
            NavHostFragment.findNavController(this).navigate(R.id.sessionBaselinesFragment)
        }
        tile("Session Incidents", "Длительность проблемных состояний, ручные метки и CSV") {
            NavHostFragment.findNavController(this).navigate(R.id.sessionIncidentsFragment)
        }
        tile("Измерения устройства", "Rust JNI, батарея, тепловая история и CSV") { open(MainActivity::class.java) }
        tile("Эксперименты", "Сравнения и заметки") { open(ExperimentLabActivity::class.java) }
        tile("Клипы MP4", "Movies/LegionControl/ · локальная библиотека") { open(CaptureStudioActivity::class.java) }
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

    private fun showSettings(s: HubSnapshot) {
        section("Настройки", "Локальные параметры. Системные спецдоступы предоставляет только Android.")
        group("Пользовательские данные")
        tile("Application Center", "${s.trackedCount} записей в библиотеке · избранное и заметки") {
            open(ApplicationCenterActivity::class.java)
        }
        tile("Smart Profiles", "${s.profileCount} профилей · импорт и экспорт JSON") {
            open(SmartProfilesActivity::class.java)
        }
        tile("Game Toolbox", "Размер, прозрачность, выбранная игра") {
            open(GameToolboxActivity::class.java)
        }
        group("Разрешения Android")
        tile("Поверх других окон", if (Settings.canDrawOverlays(requireContext())) "Разрешено" else "Не разрешено") {
            open(GameToolboxActivity::class.java)
        }
        val notifications = requireContext().getSystemService(NotificationManager::class.java)
        tile("Режим «Не беспокоить»",
            if (notifications.isNotificationPolicyAccessGranted) "Специальный доступ разрешён" else "Нужен спецдоступ Android") {
            open(GameToolboxActivity::class.java)
        }
        tile("Изменение системных настроек",
            if (Settings.System.canWrite(requireContext())) "Разрешено" else "Нужен спецдоступ Android") {
            open(SmartProfilesActivity::class.java)
        }
        tile("Использование приложений", "Официальные настройки Android") {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .onFailure { info("Настройки Android недоступны: " + it.javaClass.simpleName) }
        }
        group("О проекте")
        info("Legion Control 0.1.0-alpha.26 · Android 13+ · Kotlin + Rust. " +
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
