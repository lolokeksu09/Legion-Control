package dev.lolokeksu.legioncontrol

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment

/**
 * Navigable game detail. Explicit actions delegate to the original validated Android
 * Activities and repositories; no Android privileged game controls are invented.
 */
class GameDetailFragment : Fragment() {
    private val accent = LegionUiSupport.ACCENT
    private val muted = LegionUiSupport.MUTED
    private lateinit var vm: HubViewModel
    private lateinit var column: LinearLayout
    private lateinit var status: TextView
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val scroll = ScrollView(requireContext()).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
        }
        column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 22.dp, 18.dp, 26.dp)
        }
        scroll.addView(column)
        return scroll
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        vm = ViewModelProvider(requireActivity())[HubViewModel::class.java]
        vm.state.observe(viewLifecycleOwner) { render(it) }
        vm.actionResult.observe(viewLifecycleOwner) {
            if (::status.isInitialized) status.text = it
        }
        vm.refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::vm.isInitialized) vm.refresh()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(requireContext()).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun addAction(title: String, desc: String, callback: () -> Unit) {
        column.addView(LegionUiSupport.actionTile(requireContext(), title, desc, callback),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6.dp })
    }

    private fun section(title: String) {
        column.addView(LegionUiSupport.sectionLabel(requireContext(), title))
    }

    private fun <T> open(cls: Class<T>, configure: (Intent) -> Unit = {}) {
        runCatching {
            val intent = Intent(requireContext(), cls).apply(configure)
            val route = HubDestinationRoutes.destinationFor(cls)
            if (route != null) NavHostFragment.findNavController(this).navigate(route, intent.extras)
            else startActivity(intent)
        }.onFailure { status.text = "Не удалось открыть экран: " + it.javaClass.simpleName }
    }

    private fun render(state: HubLoadState) {
        if (!isAdded || !::column.isInitialized) return
        column.removeAllViews()
        val pkg = arguments?.getString("gamePackage")
        if (pkg == null || !ApplicationInventory.isPackageName(pkg)) {
            column.addView(LegionUiSupport.noticeView(requireContext(), "Некорректный пакет приложения", LegionUiSupport.Notice.ERROR))
            addAction("Назад к играм", "Вернуться к безопасному списку Launcher") {
                NavHostFragment.findNavController(this).navigateUp()
            }
            return
        }
        val app = (state as? HubLoadState.Ready)?.snapshot?.apps?.firstOrNull {
            it.packageName == pkg
        }
        when {
            state is HubLoadState.Loading -> {
                column.addView(LegionUiSupport.noticeView(requireContext(), "Загрузка игры…", LegionUiSupport.Notice.LOADING))
                return
            }
            state is HubLoadState.Failed -> {
                column.addView(LegionUiSupport.noticeView(requireContext(), state.message, LegionUiSupport.Notice.ERROR))
                addAction("Повторить", "Прочитать данные приложения снова") { vm.refresh() }
                return
            }
            app == null -> {
                column.addView(LegionUiSupport.noticeView(requireContext(), "Игра не видна через Launcher и не выбрана в библиотеке.", LegionUiSupport.Notice.EMPTY))
                addAction("Назад к списку", "Android ограничивает видимость приложений") {
                    NavHostFragment.findNavController(this).navigateUp()
                }
                return
            }
        }
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12.dp)
        }
        if (app.visible) {
            val icon = runCatching { requireContext().packageManager.getApplicationIcon(pkg) }.getOrNull()
            if (icon != null) header.addView(ImageView(requireContext()).apply {
                setImageDrawable(icon)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(56.dp, 56.dp).apply { rightMargin = 12.dp })
        }
        val identity = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        identity.addView(text(app.label, 24f, Color.WHITE, true).apply { isAccessibilityHeading = true })
        identity.addView(text(pkg, 12f, muted).apply { setPadding(0, 5.dp, 0, 0) })
        header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        column.addView(header)
        column.addView(text(
            (if (app.favorite) "★ Избранное" else "Не в избранном") + " · " +
                (if (app.visible) "Доступна для запуска" else "Не видна через Android Launcher"),
            13f, accent).apply { setPadding(0, 0, 0, 17.dp) })
        val launchProfile = GameLaunchProfiles.profile(requireContext(), pkg)
        val pendingRestore = GameLaunchProfiles.pendingPackage(requireContext())
        column.addView(LegionUiSupport.sectionLabel(requireContext(), "ПРОФИЛЬ ЗАПУСКА"))
        val profileAccess = launchProfile?.takeIf { it.actionable }?.let {
            CapabilityAccessBackend.profileAccess(requireContext(), it)
        }
        val profileReport = GameProfilePresentation.report(
            launchProfile, profileAccess, pendingRestore, pkg
        )
        val profileInfo = text(profileReport, 13f, Color.WHITE).apply {
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            background = LegionUiSupport.cardSurface(this)
            contentDescription = profileReport
        }
        LegionUiSupport.reportSurface(profileInfo)
        column.addView(profileInfo, ViewGroup.LayoutParams(-1, -2))
        status = text("Действия выполняются только по нажатию.", 12f, muted)
        column.addView(status.apply {
            setPadding(0, 8.dp, 0, 12.dp)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        })

        addAction("Редактировать профиль запуска", "Громкость, яркость и DND для этой игры") {
            GameLaunchProfileEditor.show(requireContext(), pkg) { message ->
                if (view != null && isAdded) {
                    status.text = message
                    val saved = GameLaunchProfiles.profile(requireContext(), pkg)
                    profileInfo.text = GameProfilePresentation.report(saved,
                        saved?.takeIf { it.actionable }?.let {
                            CapabilityAccessBackend.profileAccess(requireContext(), it)
                        }, GameLaunchProfiles.pendingPackage(requireContext()), pkg)
                    profileInfo.contentDescription = profileInfo.text
                }
            }
        }

        section("ЗАПУСК И ПОДГОТОВКА")
        if (app.visible) {
            addAction("ЗАПУСТИТЬ ПРИЛОЖЕНИЕ", "Открыть системным Launcher Intent") {
                val launch = requireContext().packageManager.getLaunchIntentForPackage(pkg)
                if (launch == null) status.text = "Android не предоставляет запуск этого пакета."
                else runCatching { startActivity(launch) }
                    .onFailure { status.text = "Запуск отклонён: " + it.javaClass.simpleName }
            }
            addAction("Открыть Game Toolbox", "Выбрать именно эту игру и управлять панелью") {
                val sourceView = view
                status.text = "Выбор игры…"
                vm.chooseForToolbox(pkg, app.label) {
                    if (sourceView != null && view === sourceView && isAdded &&
                        NavHostFragment.findNavController(this).currentDestination?.id == R.id.gameDetailFragment)
                        open(GameToolboxActivity::class.java)
                }
            }
        }
        addAction("Game Preflight", "Проверить заряд, температуру, дисплей, RAM и сеть перед запуском") {
            open(GamePreflightActivity::class.java) { it.putExtra(GamePreflightActivity.EXTRA_PACKAGE,pkg) }
        }
        addAction("Game Session", "Открыть запись сессии с заранее выбранным пакетом") {
            NavHostFragment.findNavController(this).navigate(
                R.id.gameSessionFragment, Bundle().apply {
                    putString("smart_package",pkg)
                    putString("smart_label",app.label)
                }
            )
        }
        addAction("Smart Profiles", "Профиль и предупреждения именно этого приложения") {
            open(SmartProfilesActivity::class.java) {
                it.putExtra("app_center_package", pkg)
            }
        }
        section("ИСТОРИЯ И АНАЛИТИКА")
        addAction("Игровая аналитика", "История, события, проблемы, графики и сравнения этой игры") {
            open(SessionLibraryActivity::class.java) { it.putExtra(SessionLibraryActivity.EXTRA_PACKAGE,pkg) }
        }
        addAction("Experiment Lab", "Сравнения и заметки для выбранного пакета") {
            open(ExperimentLabActivity::class.java) {
                it.putExtra("experiment_package", pkg)
            }
        }
        section("МЕДИА И БИБЛИОТЕКА")
        addAction(if (app.favorite) "Убрать из избранного" else "В избранное",
            "Сохраняются прежние категории, псевдоним и заметка в SQLite") {
            vm.toggleFavorite(pkg, app.label)
        }
        addAction("Capture Studio", "Начать запись только после согласия Android") {
            open(CaptureStudioActivity::class.java) {
                it.putExtra(CaptureStudioActivity.EXTRA_GAME_PACKAGE, pkg)
            }
        }
        addAction("Application Center", "Версии, права и заметки об этой игре") {
            open(ApplicationCenterActivity::class.java) {
                it.putExtra("app_center_package", pkg)
            }
        }
        if (!app.visible) {
            column.addView(text("Важно: отсутствие в MAIN/LAUNCHER не означает удаления приложения.", 12f, muted))
        }
    }
}
