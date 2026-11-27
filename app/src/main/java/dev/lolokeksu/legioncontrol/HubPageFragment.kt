package dev.lolokeksu.legioncontrol

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.widget.ImageView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Button
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import rikka.shizuku.Shizuku

/** Five real Navigation Component destinations, all backed by one Activity-scoped ViewModel. */
class HubPageFragment : Fragment() {
    private val bg = Color.BLACK
    private val fg = Color.WHITE
    private val muted = LegionUiSupport.MUTED
    private val accent = LegionUiSupport.ACCENT
    private val border = LegionUiSupport.BORDER
    private val panel = LegionUiSupport.PANEL
    private lateinit var viewModel: HubViewModel
    private var historyModel: SessionLibraryViewModel? = null
    private var historyPackage: String? = null
    private lateinit var column: LinearLayout
    private var query = ""
    private var libraryFilter = HubGameFilter.GAMES
    private var librarySort = HubGameSort.PRIORITY
    private var libraryPage = 0
    private var toolQuery = ""
    private var toolCategory = 0
    private var catalogExpanded = false
    private var permissionsExpanded = false
    private val searchHandler=Handler(Looper.getMainLooper())
    private var pendingSearch:Runnable?=null
    private var observedSnapshot:HubLoadState?=null
    private var systemSummary: TextView? = null
    private var shizukuStatusView: TextView? = null
    private var systemAudit: ShizukuSystemAudit? = null
    private var shizukuProbeEpoch = 0
    private var shizukuProbeRunning = false
    private var shizukuListening = false
    private val binderReceived = Shizuku.OnBinderReceivedListener { invalidateShizuku() }
    private val binderDead = Shizuku.OnBinderDeadListener { invalidateShizuku() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == ShizukuAccessBackend.REQUEST_CODE) invalidateShizuku()
    }
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = savedInstanceState ?: return
        historyPackage = saved.getString("historyPackage")
        query = saved.getString("libraryQuery").orEmpty()
        libraryFilter = HubLibraryPolicy.restore(HubGameFilter.entries, saved.getString("libraryFilter"), HubGameFilter.GAMES)
        librarySort = HubLibraryPolicy.restore(HubGameSort.entries, saved.getString("librarySort"), HubGameSort.PRIORITY)
        libraryPage = saved.getInt("libraryPage", 0).coerceAtLeast(0)
        toolQuery = saved.getString("toolQuery").orEmpty()
        toolCategory = saved.getInt("toolCategory", 0).coerceAtLeast(0)
        catalogExpanded = saved.getBoolean("catalogExpanded")
        permissionsExpanded = saved.getBoolean("permissionsExpanded")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("historyPackage", historyPackage)
        outState.putString("libraryQuery", query)
        outState.putString("libraryFilter", libraryFilter.name)
        outState.putString("librarySort", librarySort.name)
        outState.putInt("libraryPage", libraryPage)
        outState.putString("toolQuery", toolQuery)
        outState.putInt("toolCategory", toolCategory)
        outState.putBoolean("catalogExpanded", catalogExpanded)
        outState.putBoolean("permissionsExpanded", permissionsExpanded)
    }

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
        if (page() == HubTab.HISTORY) {
            val model = ViewModelProvider(this)[SessionLibraryViewModel::class.java]
            historyModel = model
            model.state.observe(viewLifecycleOwner, ::showHistory)
            model.initialize(historyPackage)
            return
        }
        viewModel = ViewModelProvider(requireActivity())[HubViewModel::class.java]
        observedSnapshot=null
        viewModel.state.observe(viewLifecycleOwner) { state ->
            val previous=observedSnapshot
            observedSnapshot=state
            if(!HubRenderPolicy.equivalent(page(),previous,state)) render(state)
            else if (state is HubLoadState.Ready) systemSummary?.text = deviceSummaryText(state.snapshot)
        }
        viewModel.actionResult.observe(viewLifecycleOwner) { message ->
            if (message.isNotBlank() && isAdded && viewModel.state.value is HubLoadState.Failed) {
                info(message)
            }
        }
        viewModel.refresh()
    }

    override fun onStart() {
        super.onStart()
        if (page() == HubTab.SETTINGS) {
            Shizuku.addBinderReceivedListener(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
            shizukuListening = true
            refreshShizuku()
        }
    }

    override fun onStop() {
        if (shizukuListening) {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
            shizukuListening = false
        }
        systemAudit?.cancel()
        systemAudit = null
        shizukuProbeEpoch++
        shizukuProbeRunning = false
        super.onStop()
    }

    private fun refreshShizuku() {
        val ctx = context ?: return
        shizukuStatusView?.text = ShizukuAccessBackend.snapshot(ctx).report()
    }

    private fun invalidateShizuku() {
        systemAudit?.cancel()
        systemAudit = null
        shizukuProbeEpoch++
        shizukuProbeRunning = false
        refreshShizuku()
    }

    private fun configureShizuku() {
        val ctx = requireContext()
        val state = ShizukuAccessBackend.snapshot(ctx)
        if (state.connection == ShizukuConnection.SHELL_READY) {
            refreshShizuku()
            return
        }
        runCatching {
            if (!ShizukuAccessBackend.requestAccess(ctx)) {
                val intent = ShizukuAccessBackend.managerIntent(ctx)
                if (intent != null) startActivity(intent)
                else shizukuStatusView?.text = state.report() +
                    "\nУстановите и запустите Shizuku, затем вернитесь в Legion Control."
            }
        }.onFailure { shizukuStatusView?.text = state.report() + "\nНе удалось запросить доступ." }
    }

    private fun inspectShizuku() {
        if (shizukuProbeRunning || systemAudit != null) return
        val ctx = requireContext().applicationContext
        val epoch = ++shizukuProbeEpoch
        shizukuProbeRunning = true
        shizukuStatusView?.text = "Проверка прав ADB shell…"
        Thread({
            val report = ShizukuAccessBackend.inspectShell(ctx)
            searchHandler.post {
                if (epoch == shizukuProbeEpoch && shizukuListening) {
                    shizukuProbeRunning = false
                    shizukuStatusView?.text = report
                }
            }
        }, "legion-shizuku-inspect").start()
    }

    private fun inspectBackgroundPolicy() {
        if (shizukuProbeRunning || systemAudit != null) return
        val ctx = requireContext()
        val epoch = ++shizukuProbeEpoch
        val audit = ShizukuSystemAudit(ctx)
        systemAudit = audit
        shizukuStatusView?.text = "Чтение конфигурации фоновых процессов…"
        audit.run { report ->
            if (epoch == shizukuProbeEpoch && shizukuListening) {
                systemAudit = null
                shizukuStatusView?.text = report
            }
        }
    }

    override fun onResume() {
        super.onResume()
        historyModel?.let {
            it.refresh(LibraryFilter(historyPackage))
            return
        }
        if (::viewModel.isInitialized) {
            if(page()==HubTab.SETTINGS) render(viewModel.state.value ?: HubLoadState.Loading)
            viewModel.refresh()
        }
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
            isAccessibilityHeading = true
        })
        if (detail != null) column.addView(text(detail, 13f, muted).apply {
            setPadding(0, 0, 0, 20.dp)
        })
    }

    private fun notice(message: String, kind: LegionUiSupport.Notice) {
        column.addView(LegionUiSupport.noticeView(requireContext(), message, kind),
            LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = 6.dp })
    }

    private fun info(message: String) {
        column.addView(text(message, 13f, muted).apply {
            setPadding(0, 6.dp, 0, 16.dp)
        })
    }

    private fun tile(title: String, description: String, onClick: () -> Unit) {
        column.addView(
            LegionUiSupport.actionTile(requireContext(), title, description, onClick),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6.dp }
        )
    }

    private fun group(label: String) {
        column.addView(text(label.uppercase(Locale.ROOT), 12f, accent, true).apply {
            setPadding(0, 19.dp, 0, 11.dp)
            letterSpacing = 0.08f
            isAccessibilityHeading = true
        })
    }

    private fun <T> open(cls: Class<T>, extras: Bundle? = null) {
        // Old catalog aliases resolve to the actual pages of the unified host.
        val diagnosticPage = ModuleNavigation.devicePageFor(cls)
        if (diagnosticPage != null) {
            openDevicePage(diagnosticPage, extras)
            return
        }
        if (cls == GameSessionActivity::class.java) {
            runCatching { NavHostFragment.findNavController(this).navigate(R.id.gameSessionFragment,extras) }
                .onFailure { error ->
                    LegionDialogs.builder(requireContext()).setTitle("Не удалось открыть Game Session")
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
            LegionDialogs.builder(requireContext()).setTitle("Не удалось открыть раздел")
                .setMessage(exception.javaClass.simpleName)
                .setPositiveButton("Понятно", null).show()
        }
    }

    private fun openDevicePage(page: DiagnosticsPage, extras: Bundle? = null) {
        startActivity(ModuleNavigation.deviceIntent(requireContext(), page, extras))
    }

    private fun page() = HubTab.parse(arguments?.getString("page"))

    private fun render(state: HubLoadState) {
        if (!isAdded || !::column.isInitialized) return
        cancelSearch()
        column.removeAllViews()
        systemSummary = null
        shizukuStatusView = null
        if (page() == HubTab.SETTINGS) {
            showSettings((state as? HubLoadState.Ready)?.snapshot)
            LegionDataBackup.recoveryWarning()?.let(::info)
            if(state is HubLoadState.Failed) {
                notice(state.message, LegionUiSupport.Notice.ERROR)
                tile("Повторить чтение сводки", "Настройки и восстановление доступны независимо") {
                    viewModel.refresh(force=true)
                }
            }
            if(state is HubLoadState.Ready) state.snapshot.warnings.forEach { notice(it, LegionUiSupport.Notice.WARNING) }
            return
        }
        when (state) {
            HubLoadState.Loading -> {
                if (page() == HubTab.TOOLS) {
                    showTools(state)
                } else {
                    section("LEGION · GAME HUB", "Чтение локальной библиотеки и системных данных")
                    notice("Загружаю данные устройства…", LegionUiSupport.Notice.LOADING)
                }
            }
            is HubLoadState.Failed -> {
                if (page() == HubTab.TOOLS) {
                    showTools(state)
                    group("Не удалось прочитать сводку")
                    notice(state.message, LegionUiSupport.Notice.ERROR)
                } else {
                    section("Не удалось загрузить")
                    notice(state.message, LegionUiSupport.Notice.ERROR)
                    showLegacyShortcuts()
                }
                tile("Повторить чтение", "Без удаления данных и изменения системных настроек") {
                    viewModel.refresh(force=true)
                }
            }
            is HubLoadState.Ready -> {
                when (page()) {
                    HubTab.HOME -> showHome(state.snapshot)
                    HubTab.GAMES -> showGames(state.snapshot)
                    HubTab.TOOLS -> showTools(state)
                    HubTab.HISTORY -> Unit
                    HubTab.SETTINGS -> showSettings(state.snapshot)
                }
                if (state.snapshot.warnings.isNotEmpty()) {
                    group("Ограничения доступа")
                    state.snapshot.warnings.forEach { notice(it, LegionUiSupport.Notice.WARNING) }
                }
            }
        }
    }

    /** Five primary destinations from the approved 0.2.0 wireframe. */
    private fun openCenter(center: HubCenter) {
        when (center) {
            HubCenter.GAMES -> NavHostFragment.findNavController(this).navigate(R.id.gamesFragment)
            HubCenter.DEVICE -> openDevicePage(DiagnosticsPage.OVERVIEW)
            HubCenter.MEDIA -> openStandaloneFeature(CaptureStudioActivity::class.java, Bundle())
            HubCenter.PERIPHERALS -> openDevicePage(DiagnosticsPage.DISPLAY)
            HubCenter.ANALYTICS -> openStandaloneFeature(SessionLibraryActivity::class.java, Bundle())
        }
    }

    private fun centerCard(center: HubCenter): LinearLayout = LinearLayout(requireContext()).apply {
        id = when (center) {
            HubCenter.GAMES -> R.id.hub_center_games
            HubCenter.DEVICE -> R.id.hub_center_device
            HubCenter.MEDIA -> R.id.hub_center_media
            HubCenter.PERIPHERALS -> R.id.hub_center_peripherals
            HubCenter.ANALYTICS -> R.id.hub_center_analytics
        }
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP
        setPadding(16.dp, 16.dp, 16.dp, 15.dp)
        minimumHeight = 112.dp
        background = RippleDrawable(ColorStateList.valueOf(0x2986B7FF),
            LegionUiSupport.cardSurface(this), null)
        isClickable = true
        isFocusable = true
        contentDescription = "${center.title}. ${center.description}"
        addView(ImageView(requireContext()).apply {
            setImageResource(HubCenterIcons.center(center))
            imageTintList = ColorStateList.valueOf(accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(26.dp,26.dp).apply { bottomMargin = 10.dp })
        addView(text(center.title, 16f, fg, true).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        addView(text(center.description, 12f, muted).apply {
            setPadding(0,5.dp,0,0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        setOnClickListener { openCenter(center) }
    }

    private fun centerGrid(centers: List<HubCenter>) {
        val twoColumns = resources.configuration.screenWidthDp >= 340 &&
            resources.configuration.fontScale < 1.35f
        val rows = if (twoColumns) centers.chunked(2) else centers.chunked(1)
        for (pair in rows) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            pair.forEachIndexed { index, center ->
                row.addView(centerCard(center), LinearLayout.LayoutParams(0,-2,1f).apply {
                    if(index>0) leftMargin=10.dp
                })
            }
            column.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=10.dp })
        }
    }

    private fun deviceSummaryText(snapshot: HubSnapshot?): String {
        if (snapshot == null) return "Сводка устройства недоступна. Инструменты можно открыть ниже."
        val defaults = Locale.getDefault()
        val level = snapshot.batteryPercent?.let { "$it%" } ?: "н/д"
        val temperature = snapshot.batteryTemperatureDeciC?.let {
            String.format(defaults, "%.1f °C", it / 10.0)
        } ?: "н/д"
        val refresh = snapshot.refreshHz?.let { String.format(defaults, "%.1f Гц", it) } ?: "н/д"
        val available = snapshot.memory?.let {
            String.format(defaults, "%.1f из %.1f ГиБ", it.availableBytes / 1073741824.0, it.totalBytes / 1073741824.0)
        } ?: "н/д"
        return buildString {
            append("Заряд ").append(level).append(" · Батарея ").append(temperature)
            append("\nRAM доступно ").append(available).append(" · Дисплей ").append(refresh)
            snapshot.resourcesReadAtMs?.let {
                append("\nПоказания на ").append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
            }
        }
    }

    private fun deviceSummary(snapshot: HubSnapshot?): TextView = text(deviceSummaryText(snapshot)).apply {
        background = outline()
        setPadding(12.dp, 12.dp, 12.dp, 12.dp)
        column.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp })
    }

    private fun showHome(s: HubSnapshot) {
        // Primary layout matches the approved 2 + 2 + 1 center grid.
        centerGrid(HubCenter.entries.toList())
        group("Состояние устройства")
        deviceSummary(s)
        val favorites=HubLibraryPolicy.homeFavorites(s.apps)
        if(favorites.isNotEmpty()) {
            group("Избранное")
            favorites.forEach { app ->
                tile(app.label, if(app.visible) "Карточка игры" else "Не видно через Launcher") { openGameCard(app) }
            }
        }
        s.recentSessions.firstOrNull()?.let { last ->
            group("Последняя запись")
            tile(last.appLabel, DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
                .format(Date(last.startedAtMs))) {
                openRecentSession(last)
            }
        }
        group("Быстрые действия")
        tile("Игровая панель", "Управление поверх игры") { open(GameToolboxActivity::class.java) }
        tile("Запись сессии", "Ручной запуск телеметрии") { open(GameSessionActivity::class.java) }
    }

    private fun openRecentSession(session: GameSessionHeader) {
        startActivity(SessionLibraryActivity.intent(requireContext(), session.packageName, session.id))
    }

    private fun openGameCard(app: HubApp) {
        NavHostFragment.findNavController(this).navigate(R.id.gameDetailFragment, Bundle().apply {
            putString("gamePackage", app.packageName)
        })
    }

    private fun showGames(s: HubSnapshot) {
        section("Игры", "Выберите игру для запуска, профиля и анализа.")
        val filter = LegionUiSupport.spinner(requireContext(), HubGameFilter.entries.map { it.label })
        val sort = LegionUiSupport.spinner(requireContext(), HubGameSort.entries.map { it.label })
        filter.contentDescription = "Фильтр библиотеки"
        sort.contentDescription = "Сортировка библиотеки"
        filter.setSelection(libraryFilter.ordinal)
        sort.setSelection(librarySort.ordinal)
        val filters=LinearLayout(requireContext()).apply {
            orientation=if(resources.configuration.screenWidthDp>=360 &&
                resources.configuration.fontScale<1.3f) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        listOf(filter,sort).forEach {
            filters.addView(it,if(filters.orientation==LinearLayout.HORIZONTAL)
                LinearLayout.LayoutParams(0,-2,1f) else LinearLayout.LayoutParams(-1,-2))
        }
        column.addView(filters,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=8.dp })
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
        val count = text("", 13f, muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(0, 0, 0, 8.dp)
        }
        column.addView(count)
        val list = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        column.addView(list)
        val previous = Button(requireContext()).apply { text = "Назад"; contentDescription = "Предыдущая страница" }
        val next = Button(requireContext()).apply { text = "Далее"; contentDescription = "Следующая страница" }
        val pagination=CompactActionRow(requireContext(), previous, next)
        column.addView(pagination)
        fun redraw() {
            list.removeAllViews()
            val result = HubLibraryPolicy.select(s.apps, libraryFilter, librarySort, query, libraryPage)
            libraryPage = result.page
            count.text = if(result.pageCount>1) "Найдено: ${result.total} · ${result.page + 1}/${result.pageCount}"
                else "Найдено: ${result.total}"
            previous.isEnabled = result.page > 0
            next.isEnabled = result.page + 1 < result.pageCount
            pagination.visibility = if(result.pageCount > 1) View.VISIBLE else View.GONE
            if (result.items.isEmpty()) {
                list.addView(LegionUiSupport.noticeView(requireContext(),
                    "Совпадений нет. Измените фильтр или запрос.", LegionUiSupport.Notice.EMPTY))
                return
            }
            result.items.forEach { app ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = outline()
                    setPadding(15.dp, 11.dp, 13.dp, 11.dp)
                    isClickable = true
                    isFocusable = true
                    minimumHeight = 48.dp
                    contentDescription = "Открыть карточку " + app.label +
                        (if (app.favorite) ". Избранное." else "") +
                        (if (!app.visible) " Не видно через Launcher." else "")
                    setOnClickListener {
                        openGameCard(app)
                    }
                }
                val monogram = text(app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                    20f, accent, true).apply {
                    gravity = Gravity.CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val icon = if(app.visible) runCatching {
                    requireContext().packageManager.getApplicationIcon(app.packageName)
                }.getOrNull() else null
                val artwork:View = if(icon==null) monogram else ImageView(requireContext()).apply {
                    setImageDrawable(icon)
                    scaleType=ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                row.addView(artwork, LinearLayout.LayoutParams(42.dp,42.dp).apply { rightMargin=10.dp })
                val labels = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
                labels.addView(text((if (app.favorite) "★  " else "") + app.label, 14f, fg, true))
                labels.addView(text(if (app.visible) app.packageName else "Не видно через Launcher · ${app.packageName}",
                    11f, muted))
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(text("›", 22f, accent).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9.dp })
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                libraryPage = 0
                cancelSearch()
                val task=Runnable { if(isAdded && view!=null && list.isAttachedToWindow) redraw() }
                pendingSearch=task
                searchHandler.postDelayed(task,HubRefreshGate.SEARCH_DELAY_MS)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        fun movePage(delta: Int) {
            libraryPage += delta
            redraw()
            (view as? ScrollView)?.smoothScrollTo(0, count.top)
        }
        previous.setOnClickListener { movePage(-1) }
        next.setOnClickListener { movePage(1) }
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value = HubGameFilter.entries.getOrNull(position) ?: return
                if (value != libraryFilter) {
                    libraryFilter = value; libraryPage = 0; cancelSearch(); redraw()
                }
            }
        }
        sort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value = HubGameSort.entries.getOrNull(position) ?: return
                if (value != librarySort) {
                    librarySort = value; libraryPage = 0; cancelSearch(); redraw()
                }
            }
        }
        redraw()
        tile("Управление библиотекой", "Категории, заметки, сохранённые приложения") {
            open(ApplicationCenterActivity::class.java)
        }
        tile("Игровая панель", "Настройки Game Toolbox") { open(GameToolboxActivity::class.java) }
    }

    private fun cancelSearch() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch=null
    }
    override fun onDestroyView() {
        shizukuStatusView = null
        historyModel = null
        systemSummary = null
        cancelSearch()
        observedSnapshot=null
        super.onDestroyView()
    }

    /** Search results reveal existing controls; opening a result never runs the action. */
    private fun openStandaloneFeature(cls: Class<*>, args: Bundle) {
        runCatching { startActivity(Intent(requireContext(), cls).putExtras(args)) }
            .onFailure {
                LegionDialogs.builder(requireContext()).setTitle("Не удалось открыть действие")
                    .setMessage(it.javaClass.simpleName).setPositiveButton("Понятно", null).show()
            }
    }

    private fun openFeature(entry: FeatureEntry) {
        val args = Bundle().apply { putString(FeatureFinder.EXTRA_FEATURE_ID, entry.id) }
        when (entry.module) {
            FeatureModule.CAPTURE -> openStandaloneFeature(CaptureStudioActivity::class.java, args)
            FeatureModule.AUDIO -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.HAPTIC -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.NETWORK -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.PROFILE -> open(SmartProfilesActivity::class.java, args)
            FeatureModule.APPS -> open(ApplicationCenterActivity::class.java, args)
            FeatureModule.SESSION -> open(GameSessionActivity::class.java, args)
            FeatureModule.TOOLBOX -> open(GameToolboxActivity::class.java, args)
            FeatureModule.GAMING -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.HARDWARE -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.COMPAT -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.EXPERIMENT -> open(ExperimentLabActivity::class.java, args)
            FeatureModule.SIGNALS -> openStandaloneFeature(SessionLibraryActivity::class.java, args)
            FeatureModule.INCIDENTS -> openStandaloneFeature(SessionLibraryActivity::class.java, args)
            FeatureModule.BASELINES -> openStandaloneFeature(SessionLibraryActivity::class.java, args)
            FeatureModule.DIAGNOSTICS -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.GAMEPAD -> openStandaloneFeature(GamepadLiveActivity::class.java, args)
            FeatureModule.MONITOR -> openStandaloneFeature(MainActivity::class.java, args)
            FeatureModule.PREFLIGHT -> openStandaloneFeature(GamePreflightActivity::class.java, args)
            FeatureModule.COMPARE -> openStandaloneFeature(SessionLibraryActivity::class.java, args)
            FeatureModule.LIBRARY -> openStandaloneFeature(SessionLibraryActivity::class.java, args)
        }
    }

    private fun quickToolRows(items: List<View>) {
        val pairSize = if (resources.configuration.screenWidthDp >= 360 &&
            resources.configuration.fontScale < 1.3f) 2 else 1
        for (pair in items.chunked(pairSize)) {
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, item ->
                row.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index > 0) marginStart = 8.dp
                })
            }
            column.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6.dp })
        }
    }

    /** Searchable feature catalog; launches the existing real modules without migrating their data. */
    private fun showTools(state: HubLoadState) {
        section("Система")
        fun destination(title: String, page: DiagnosticsPage): View =
            LegionUiSupport.actionTile(requireContext(), title, "") { openDevicePage(page) }.apply { tag = page }
        systemSummary = deviceSummary((state as? HubLoadState.Ready)?.snapshot).apply {
            if (state == HubLoadState.Loading) text = "Читаю состояние устройства…"
        }
        group("Устройство")
        quickToolRows(listOf(
            destination("Ресурсы", DiagnosticsPage.MONITOR),
            destination("Сеть", DiagnosticsPage.NETWORK),
            destination("Аппаратные тесты", DiagnosticsPage.HARDWARE),
            destination("Датчики", DiagnosticsPage.SENSORS),
            destination("Батарея", DiagnosticsPage.OVERVIEW),
            destination("Совместимость", DiagnosticsPage.COMPAT)
        ))
        group("Периферия")
        quickToolRows(listOf(
            destination("Звук", DiagnosticsPage.AUDIO),
            destination("Вибрация", DiagnosticsPage.HAPTIC),
            LegionUiSupport.actionTile(requireContext(), "Экран", "") { open(DisplayPatternActivity::class.java) },
            LegionUiSupport.actionTile(requireContext(), "Геймпад", "") { open(GamepadLiveActivity::class.java) }
        ))
        tile("История и экспорт", "Сохранённые замеры и отчёты устройства") {
            openDevicePage(DiagnosticsPage.HISTORY)
        }
        tile("Параметры Android", "Громкость, яркость, DND и тайм-аут") {
            SystemControlPanel.show(requireContext())
        }
        group("Поиск функций")
        val catalog = HubToolCatalog.groups
        val input = EditText(requireContext()).apply {
            id = R.id.hub_feature_search
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
        val categorySelector = LegionUiSupport.spinner(requireContext(), listOf("Все категории") + catalog.map { it.title })
        toolCategory = toolCategory.coerceIn(0, catalog.size)
        categorySelector.setSelection(toolCategory)
        categorySelector.contentDescription = "Категория инструментов"
        val expand=Button(requireContext()).apply { LegionUiSupport.styleAction(this) }
        column.addView(expand)
        column.addView(categorySelector, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp })
        val resultCount = text("", 13f, muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        column.addView(resultCount)
        val results = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        column.addView(results)

        fun redraw() {
            results.removeAllViews()
            val filter = toolQuery.trim().lowercase(Locale.ROOT)
            var shown = 0
            // Search always covers the entire catalog; category is a browse-only filter.
            categorySelector.isEnabled = filter.isEmpty()
            val show=filter.isNotEmpty() || catalogExpanded
            expand.text=if(catalogExpanded) "Скрыть полный каталог" else "Все инструменты"
            expand.visibility=if(filter.isEmpty())View.VISIBLE else View.GONE
            categorySelector.visibility=if(show && filter.isEmpty())View.VISIBLE else View.GONE
            resultCount.visibility=if(show)View.VISIBLE else View.GONE
            results.visibility=if(show)View.VISIBLE else View.GONE
            if(!show)return
            for ((index, moduleGroup) in catalog.withIndex()) {
                val category = moduleGroup.title
                if (filter.isEmpty() && toolCategory != 0 && toolCategory != index + 1) continue
                val matching = moduleGroup.entries.filter { entry ->
                    HubToolCatalog.matches(moduleGroup, entry, filter)
                }
                if (matching.isEmpty()) continue
                results.addView(text(category.uppercase(Locale.ROOT), 12f, accent, true).apply {
                    setPadding(0, 19.dp, 0, 11.dp)
                    letterSpacing = 0.08f
                    isAccessibilityHeading = true
                })
                for (entry in matching) {
                    results.addView(
                        LegionUiSupport.actionTile(requireContext(), entry.title, entry.description) { open(entry.activity) },
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
                        ) { openFeature(entry) }.also { FeatureReveal.bind(it, entry.id) },
                            LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = 10.dp
                        })
                    }
                    shown += functions.size
                }
            }
            resultCount.text = "Найдено: $shown" + if (filter.isNotEmpty()) " · поиск по всем категориям" else ""
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
                cancelSearch()
                val task=Runnable { if(isAdded && view!=null && results.isAttachedToWindow) redraw() }
                pendingSearch=task
                searchHandler.postDelayed(task,HubRefreshGate.SEARCH_DELAY_MS)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        expand.setOnClickListener { catalogExpanded=!catalogExpanded;redraw() }
        categorySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (toolCategory != position) {
                    toolCategory = position
                    cancelSearch()
                    redraw()
                }
            }
        }
        redraw()
    }

    private fun showHistory(state: LibraryState) {
        column.removeAllViews()
        section("Аналитика")
        group("Последние записи")
        val filterLabel = state.gameLabel(historyPackage)
        tile("Игра", filterLabel) { pickHistoryGame(state) }
        when {
            state.queryFailed -> {
                notice(state.message, LegionUiSupport.Notice.ERROR)
                tile("Повторить чтение", filterLabel) { historyModel?.refresh(LibraryFilter(historyPackage)) }
            }
            state.busy -> notice("Читаю локальные записи…", LegionUiSupport.Notice.LOADING)
            state.rows.isEmpty() -> notice(
                if (historyPackage == null) "Записей пока нет. Начните игровую сессию вручную."
                else "У выбранной игры пока нет записей.",
                LegionUiSupport.Notice.EMPTY
            )
            else -> {
                val dates = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                for (session in state.rows.take(8)) {
                    val time = dates.format(Date(session.startedAtMs))
                    val completion = when {
                        session.endedAtMs == null -> "Без отметки завершения"
                        session.endReason == "INTERRUPTED" -> "Прервана"
                        else -> "Завершена"
                    }
                    tile(session.appLabel, "$time · $completion") { openRecentSession(session) }
                }
            }
        }
        tile("Все сессии", "Библиотека, сравнение и экспорт") {
            startActivity(SessionLibraryActivity.intent(requireContext(), historyPackage))
        }
        group("Инструменты")
        tile("Текущая игровая сессия", "Управление записью и анализ незавершённых сессий") {
            open(GameSessionActivity::class.java)
        }
        tile("Эксперименты A/B", "Создание экспериментов, группы и заметки") {
            open(ExperimentLabActivity::class.java, Bundle().apply {
                historyPackage?.let { putString(ExperimentLabActivity.EXTRA_PACKAGE, it) }
            })
        }
        tile("Медиатека", "Локальные MP4 и PNG") {
            open(CaptureStudioActivity::class.java, Bundle().apply {
                putString(CaptureStudioActivity.EXTRA_PANE, CaptureStudioPane.LIBRARY.name)
            })
        }
        group("Данные устройства и приложений")
        tile("Измерения устройства", "История Kotlin + Rust, тесты батареи, тепло и CSV/ZIP") {
            openDevicePage(DiagnosticsPage.HISTORY)
        }
        tile("История приложений", "Отслеживаемые версии и события") { open(ApplicationCenterActivity::class.java) }
    }

    private fun pickHistoryGame(state: LibraryState) {
        if (state.busy) return
        val packages = listOf(null) + (state.packages.keys + listOfNotNull(historyPackage))
        LegionDialogs.builder(requireContext()).setTitle("Игра")
            .setSingleChoiceItems(packages.map(state::gameLabel).toTypedArray(),
                packages.indexOf(historyPackage)) { dialog, index ->
                val target = packages[index]
                if (historyPackage != target) {
                    historyPackage = target
                    historyModel?.refresh(LibraryFilter(target))
                }
                dialog.dismiss()
            }.setNegativeButton("Отмена", null).show()
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
                LegionDialogs.builder(requireContext()).setTitle("Настройки недоступны")
                    .setMessage("Android не открыл этот экран: " + it.javaClass.simpleName)
                    .setPositiveButton("Понятно", null).show()
            }
    }

    private fun openCapabilitySettings(capability: CapabilityAccess) {
        val ctx = requireContext()
        val request = CapabilityAccessBackend.settingsIntent(ctx, capability) ?: return
        runCatching { startActivity(request) }.onFailure {
            when (capability) {
                CapabilityAccess.OVERLAY -> systemSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                CapabilityAccess.NOTIFICATIONS, CapabilityAccess.PLAYBACK_AUDIO ->
                    systemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                else -> LegionDialogs.builder(ctx).setTitle("Настройки недоступны")
                    .setMessage("Android не открыл этот экран: " + it.javaClass.simpleName)
                    .setPositiveButton("Понятно", null).show()
            }
        }
    }

    private fun showSettings(s: HubSnapshot?) {
        section("Настройки", "Разрешения, локальные данные и параметры модулей.")
        group("Центр разрешений")
        val access = CapabilityAccessBackend.read(requireContext())
        val permissions = PermissionCenterModel.permissions(access)
        info(PermissionCenterModel.summary(permissions) + ". Доступы выдаются только в Android; " +
            "не все нужны для каждой функции.")
        val permissionPanel=LinearLayout(requireContext()).apply { orientation=LinearLayout.VERTICAL }
        val permissionToggle=Button(requireContext()).apply { LegionUiSupport.styleAction(this) }
        fun refreshPermissionPanel() {
            permissionPanel.visibility=if(permissionsExpanded)View.VISIBLE else View.GONE
            permissionToggle.text=if(permissionsExpanded)"Скрыть список разрешений" else "Управление разрешениями"
        }
        permissionToggle.setOnClickListener { permissionsExpanded=!permissionsExpanded;refreshPermissionPanel() }
        column.addView(permissionToggle)
        for (item in permissions) {
            permissionPanel.addView(LegionUiSupport.permissionRow(requireContext(), item) {
                openCapabilitySettings(item.capability)
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6.dp })
        }
        column.addView(permissionPanel)
        refreshPermissionPanel()
        group("Системное управление")
        tile("Параметры Android", "Громкость, яркость, DND, тайм-аут и возврат") {
            SystemControlPanel.show(requireContext())
        }
        group("Расширенный доступ")
        shizukuStatusView = LegionUiSupport.noticeView(requireContext(),
            ShizukuAccessBackend.snapshot(requireContext()).report(), LegionUiSupport.Notice.INFO)
        column.addView(requireNotNull(shizukuStatusView))
        tile("Настроить Shizuku", "Подключить ADB shell или открыть менеджер Shizuku") { configureShizuku() }
        tile("Проверить права ADB shell", "Только чтение · доступ к системным возможностям") { inspectShizuku() }
        tile("Фоновые процессы", "Freezer, App Standby, энергосбережение · чтение через Shizuku") {
            inspectBackgroundPolicy()
        }
        group("Возможности и ограничения")
        for (item in PermissionCenterModel.limitations(access)) {
            column.addView(LegionUiSupport.permissionRow(requireContext(), item) {},
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6.dp })
        }
        group("Резервное копирование и файлы")
        for (id in listOf("capture.backup", "capture.restore", "capture.resume")) {
            val entry = FeatureFinder.resolve(id, FeatureModule.CAPTURE) ?: continue
            tile(entry.title, "Capture Studio · локальные данные") { openFeature(entry) }
        }
        group("Модули")
        tile("Каталог инструментов", "Все модули и поиск конкретных функций") {
            NavHostFragment.findNavController(this).navigate(R.id.toolsFragment)
        }
        group("О проекте")
        info("Legion Control ${LegionUiSupport.versionLabel(requireContext())} · Android 13+ · Kotlin + Rust. " +
            "Shizuku опционален; обычные функции без Root. 30/60 FPS записи — запрос кодеку, не FPS игры. " +
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
