package dev.lolokeksu.legioncontrol

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment

/**
 * The only launcher Activity. Five destinations have a real Navigation back stack.
 * Existing backend Activities stay callable without source or database migration.
 */
class NavigationHostActivity : FragmentActivity() {
    private val accent = 0xFF86B7FF.toInt()
    private val muted = 0xFF858C98.toInt()
    private lateinit var nav: NavController
    private lateinit var subtitle: TextView
    private lateinit var back: TextView
    private val buttons = LinkedHashMap<Int, Pair<ImageView, TextView>>()
    private val tabSurfaces = LinkedHashMap<Int, View>()
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
    private fun versionLabel(): String = runCatching {
        packageManager.getPackageInfo(packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0L)).versionName
            ?: "pre-release"
    }.getOrDefault("pre-release")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fail closed if an interrupted cross-store restore cannot be recovered.
        // Do not let the UI mutate or inspect partially restored user data.
        val recovery = runCatching { LegionDataBackup.recoverInterruptedRestore(this) }
        if (recovery.isFailure) {
            val blocked = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
                setPadding(24.dp, 50.dp, 24.dp, 24.dp)
            }
            blocked.addView(TextView(this).apply {
                text = "Не удалось завершить восстановление данных. " +
                    "История и настройки не будут открыты, пока проверка не пройдёт. " +
                    (recovery.exceptionOrNull()?.javaClass?.simpleName ?: "")
                textSize = 18f
                setTextColor(Color.WHITE)
            })
            blocked.addView(Button(this).apply {
                text = "Повторить восстановление"
                setOnClickListener { recreate() }
            })
            setContentView(blocked)
            return
        }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0

        // Each module styles its own View in onViewCreated, regardless of entry point.

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 15.dp, 16.dp, 13.dp)
        }
        back = TextView(this).apply {
            text = "‹"
            textSize = 35f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(0, 0, 12.dp, 0)
            visibility = View.GONE
            contentDescription = "Назад"
            isFocusable = true
            setOnClickListener { nav.navigateUp() }
        }
        header.addView(back, LinearLayout.LayoutParams(48.dp, 52.dp))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "LEGION CONTROL"
            textSize = 21f
            setTextColor(Color.WHITE)
            letterSpacing = 0.045f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        subtitle = TextView(this).apply {
            text = "GAME HUB · " + versionLabel()
            textSize = 11.5f
            setTextColor(muted)
            setPadding(0, 4.dp, 0, 0)
        }
        titles.addView(subtitle)
        header.addView(titles)
        root.addView(header)

        val separator = View(this).apply { setBackgroundColor(0xFF22262B.toInt()) }
        root.addView(separator, LinearLayout.LayoutParams(-1, 1.dp))

        val content = FrameLayout(this).apply { id = R.id.hub_nav_host_container }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(7.dp, 9.dp, 7.dp, 8.dp)
            setBackgroundColor(0xFF0D0F12.toInt())
        }
        root.addView(bottom)
        setContentView(root)

        val host = (supportFragmentManager.findFragmentById(R.id.hub_nav_host_container)
            as? NavHostFragment) ?: NavHostFragment.create(R.navigation.legion_nav_graph).also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.hub_nav_host_container, it, "legion-nav-host")
                    .setPrimaryNavigationFragment(it)
                    .commitNow()
            }
        nav = host.navController

        val ids = intArrayOf(R.id.homeFragment, R.id.gamesFragment,
            R.id.toolsFragment, R.id.historyFragment, R.id.settingsFragment)
        val icons = intArrayOf(android.R.drawable.ic_menu_view, android.R.drawable.ic_media_play,
            android.R.drawable.ic_menu_manage, android.R.drawable.ic_menu_recent_history,
            android.R.drawable.ic_menu_preferences)
        val tabs = HubTab.entries
        for (index in ids.indices) {
            val id = ids[index]
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "Открыть раздел " + tabs[index].title
            }
            val icon = ImageView(this).apply {
                setImageResource(icons[index])
                imageTintList = ColorStateList.valueOf(muted)
            }
            column.addView(icon, LinearLayout.LayoutParams(21.dp, 21.dp))
            val text = TextView(this).apply {
                text = tabs[index].shortTitle
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(0, 6.dp, 0, 0)
                maxLines = 1
            }
            column.addView(text)
            bottom.addView(column, LinearLayout.LayoutParams(0, 57.dp, 1f))
            buttons[id] = icon to text
            tabSurfaces[id] = column
            column.setOnClickListener {
                if (nav.currentDestination?.id != id) {
                    nav.navigate(id, null, NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setPopUpTo(R.id.homeFragment, false, true)
                        .build())
                }
            }
        }
        nav.addOnDestinationChangedListener { _, destination, _ ->
            val actual = destination.id
            // Select the owning top-level section, not the Activity/Fragment
            // implementation family. A contextual shortcut does not change ownership.
            val active = when (actual) {
                R.id.gameDetailFragment, R.id.gameSessionFragment,
                R.id.applicationCenterDestination, R.id.gameToolboxDestination,
                R.id.smartProfilesDestination -> R.id.gamesFragment
                R.id.sessionSignalsFragment, R.id.sessionIncidentsFragment,
                R.id.sessionBaselinesFragment -> R.id.historyFragment
                R.id.audioLabDestination, R.id.hapticLabDestination,
                R.id.connectivityLabDestination, R.id.experimentLabDestination,
                R.id.compatibilityLabDestination, R.id.hardwareLabDestination,
                R.id.gamingDiagnosticsDestination -> R.id.toolsFragment
                else -> actual
            }
            buttons.forEach { (id, views) ->
                val selected = id == active
                views.first.imageTintList = ColorStateList.valueOf(if (selected) accent else muted)
                views.second.setTextColor(if (selected) accent else muted)
                views.second.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                tabSurfaces[id]?.let { tab ->
                    tab.isSelected = selected
                    tab.stateDescription = if (selected) "Выбрано" else "Не выбрано"
                }
            }
            back.visibility = if (actual == R.id.gameDetailFragment ||
                actual == R.id.gameSessionFragment ||
                actual == R.id.sessionSignalsFragment ||
                actual == R.id.sessionIncidentsFragment ||
                actual == R.id.sessionBaselinesFragment ||
                actual == R.id.applicationCenterDestination ||
                actual == R.id.gameToolboxDestination ||
                actual == R.id.smartProfilesDestination ||
                actual == R.id.audioLabDestination ||
                actual == R.id.hapticLabDestination ||
                actual == R.id.connectivityLabDestination ||
                actual == R.id.experimentLabDestination ||
                actual == R.id.compatibilityLabDestination ||
                actual == R.id.hardwareLabDestination ||
                actual == R.id.gamingDiagnosticsDestination) View.VISIBLE else View.GONE
            subtitle.text = when (actual) {
                R.id.homeFragment -> "GAME HUB · " + versionLabel()
                R.id.gamesFragment -> "ИГРОВАЯ БИБЛИОТЕКА"
                R.id.toolsFragment -> "ВСЕ СИСТЕМНЫЕ ИНСТРУМЕНТЫ"
                R.id.historyFragment -> "ИСТОРИЯ И ДИАГНОСТИКА"
                R.id.settingsFragment -> "НАСТРОЙКИ И КОНТРОЛЬ"
                R.id.gameDetailFragment -> "НАСТРОЙКИ ИГРЫ"
                R.id.gameSessionFragment -> "ЗАПИСЬ ИГРОВОЙ СЕССИИ"
                R.id.sessionSignalsFragment -> "ХРОНОЛОГИЯ И СОБЫТИЯ"
                R.id.sessionBaselinesFragment -> "ИСТОРИЧЕСКИЕ БАЗОВЫЕ ПОКАЗАТЕЛИ"
                R.id.sessionIncidentsFragment -> "ИНТЕРВАЛЫ СОСТОЯНИЯ УСТРОЙСТВА"
                R.id.applicationCenterDestination -> "БИБЛИОТЕКА ПРИЛОЖЕНИЙ"
                R.id.gameToolboxDestination -> "ИГРОВАЯ ПАНЕЛЬ"
                R.id.smartProfilesDestination -> "УМНЫЕ ПРОФИЛИ"
                R.id.audioLabDestination -> "АУДИО ЛАБОРАТОРИЯ"
                R.id.hapticLabDestination -> "ТАКТИЛЬНАЯ ЛАБОРАТОРИЯ"
                R.id.connectivityLabDestination -> "СЕТЕВАЯ ДИАГНОСТИКА"
                R.id.experimentLabDestination -> "A/B ЭКСПЕРИМЕНТЫ"
                R.id.compatibilityLabDestination -> "СОВМЕСТИМОСТЬ ANDROID"
                R.id.hardwareLabDestination -> "АППАРАТНАЯ ДИАГНОСТИКА"
                R.id.gamingDiagnosticsDestination -> "ИГРОВАЯ ДИАГНОСТИКА"
                else -> "LEGION CONTROL"
            }
        }
        if(savedInstanceState==null && intent.getBooleanExtra("open_session_signals",false)) {
            val args=Bundle().apply {
                intent.getStringExtra("smart_package")?.let { putString("smart_package",it) }
            }
            nav.navigate(R.id.sessionSignalsFragment,args)
        }
        if(savedInstanceState==null && intent.getBooleanExtra("open_session_baselines",false)) {
            val args=Bundle().apply {
                intent.getStringExtra("smart_package")?.let { putString("smart_package",it) }
            }
            nav.navigate(R.id.sessionBaselinesFragment,args)
        }
        if (savedInstanceState == null && intent.getBooleanExtra("open_session_incidents", false)) {
            val args = Bundle().apply {
                intent.getStringExtra("smart_package")?.let { putString("smart_package", it) }
                intent.getStringExtra("session_id")?.let { putString("session_id", it) }
            }
            nav.navigate(R.id.sessionIncidentsFragment, args)
        }
        if (savedInstanceState == null && intent.getBooleanExtra("open_game_toolbox", false)) {
            nav.navigate(R.id.gameToolboxDestination)
        }
        openRequestedTab(intent)
        if (savedInstanceState == null) openRequestedModule(intent)
        ViewModelProvider(this)[HubViewModel::class.java].refresh()
    }

    private fun openRequestedModule(request: Intent) {
        val route = request.getIntExtra(ModuleNavigation.EXTRA_MODULE_ROUTE, -1)
        // Never navigate to arbitrary resources supplied by external Intents.
        val permitted = when (route) {
            R.id.gameSessionFragment, R.id.smartProfilesDestination,
            R.id.applicationCenterDestination, R.id.audioLabDestination,
            R.id.hapticLabDestination, R.id.gamingDiagnosticsDestination,
            R.id.hardwareLabDestination, R.id.compatibilityLabDestination,
            R.id.connectivityLabDestination, R.id.experimentLabDestination -> true
            else -> false
        }
        request.removeExtra(ModuleNavigation.EXTRA_MODULE_ROUTE)
        val args = request.getBundleExtra(ModuleNavigation.EXTRA_MODULE_ARGS)
        request.removeExtra(ModuleNavigation.EXTRA_MODULE_ARGS)
        if (!permitted) return
        nav.navigate(route, args, NavOptions.Builder().setLaunchSingleTop(true).build())
    }

    private fun openRequestedTab(request: Intent) {
        val index = request.getIntExtra("open_hub_tab", -1)
        val destinations = intArrayOf(R.id.homeFragment, R.id.gamesFragment,
            R.id.toolsFragment, R.id.historyFragment, R.id.settingsFragment)
        if (index !in destinations.indices) return
        request.removeExtra("open_hub_tab")
        val destination = destinations[index]
        if (nav.currentDestination?.id != destination) {
            nav.navigate(destination, null, NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(R.id.homeFragment, false, true)
                .build())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::nav.isInitialized) {
            openRequestedTab(intent)
            openRequestedModule(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::nav.isInitialized) ViewModelProvider(this)[HubViewModel::class.java].refresh()
    }
}
