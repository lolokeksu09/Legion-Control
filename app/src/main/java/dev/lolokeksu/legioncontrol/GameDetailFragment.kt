package dev.lolokeksu.legioncontrol

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
    private val accent = 0xFF86B7FF.toInt()
    private val muted = 0xFF9CA5B0.toInt()
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
        val background = GradientDrawable().apply {
            setColor(0xFF12161C.toInt())
            cornerRadius = 17.dp.toFloat()
            setStroke(1.dp, 0xFF252B33.toInt())
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 16.dp, 16.dp, 16.dp)
            this.background = background
            isClickable = true
            isFocusable = true
            contentDescription = title
            setOnClickListener { callback() }
        }
        row.addView(text(title, 16f, Color.WHITE, true))
        row.addView(text(desc, 12f, muted).apply { setPadding(0, 5.dp, 0, 0) })
        column.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 11.dp })
    }

    private fun <T> open(cls: Class<T>, configure: (Intent) -> Unit = {}) {
        runCatching {
            startActivity(Intent(requireContext(), cls).apply(configure))
        }.onFailure { status.text = "Не удалось открыть экран: " + it.javaClass.simpleName }
    }

    private fun render(state: HubLoadState) {
        if (!isAdded || !::column.isInitialized) return
        column.removeAllViews()
        val pkg = arguments?.getString("gamePackage")
        if (pkg == null || !ApplicationInventory.isPackageName(pkg)) {
            column.addView(text("Некорректный пакет приложения", 16f, muted))
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
                column.addView(text("Загрузка игры…", 16f, muted))
                return
            }
            state is HubLoadState.Failed -> {
                column.addView(text(state.message, 14f, muted))
                addAction("Повторить", "Прочитать данные приложения снова") { vm.refresh() }
                return
            }
            app == null -> {
                column.addView(text("Игра не видна через Launcher и не выбрана в библиотеке.", 15f, muted))
                addAction("Назад к списку", "Android ограничивает видимость приложений") {
                    NavHostFragment.findNavController(this).navigateUp()
                }
                return
            }
        }
        column.addView(text(app.label, 25f, Color.WHITE, true))
        column.addView(text(pkg, 12f, muted).apply { setPadding(0, 6.dp, 0, 12.dp) })
        column.addView(text(
            (if (app.favorite) "★ Избранное" else "Не в избранном") + " · " +
                (if (app.visible) "Доступна для запуска" else "Не видна через Android Launcher"),
            13f, accent).apply { setPadding(0, 0, 0, 17.dp) })
        status = text("Действия выполняются только по нажатию. Без Root/Shizuku.", 12f, muted)
        column.addView(status.apply { setPadding(0, 0, 0, 16.dp) })

        addAction(if (app.favorite) "Убрать из избранного" else "В избранное",
            "Сохраняются прежние категории, псевдоним и заметка в SQLite") {
            vm.toggleFavorite(pkg, app.label)
        }
        if (app.visible) {
            addAction("ЗАПУСТИТЬ ПРИЛОЖЕНИЕ", "Открыть системным Launcher Intent") {
                val launch = requireContext().packageManager.getLaunchIntentForPackage(pkg)
                if (launch == null) status.text = "Android не предоставляет запуск этого пакета."
                else runCatching { startActivity(launch) }
                    .onFailure { status.text = "Запуск отклонён: " + it.javaClass.simpleName }
            }
            addAction("Выбрать для Game Toolbox", "Сохранить пакет в настройках существующей панели") {
                vm.chooseForToolbox(pkg, app.label)
            }
        }
        addAction("Открыть Game Toolbox", "Панель поверх игр требует разрешения Android") {
            open(GameToolboxActivity::class.java)
        }
        addAction("Game Session", "Открыть запись сессии с заранее выбранным пакетом") {
            open(GameSessionActivity::class.java) {
                it.putExtra("smart_package", pkg)
                it.putExtra("smart_label", app.label)
            }
        }
        addAction("Smart Profiles", "Профиль и предупреждения именно этого приложения") {
            open(SmartProfilesActivity::class.java) {
                it.putExtra("app_center_package", pkg)
            }
        }
        addAction("Experiment Lab", "Сравнения и заметки для выбранного пакета") {
            open(ExperimentLabActivity::class.java) {
                it.putExtra("experiment_package", pkg)
            }
        }
        addAction("Capture Studio", "Начать запись только после согласия Android") {
            open(CaptureStudioActivity::class.java)
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
