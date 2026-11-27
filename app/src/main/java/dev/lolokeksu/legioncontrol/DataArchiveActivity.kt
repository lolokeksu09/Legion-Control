package dev.lolokeksu.legioncontrol

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider

/** Local data actions are explicit and use the system document picker. */
class DataArchiveActivity : ComponentActivity() {
    private val viewModel by lazy { ViewModelProvider(this)[DataArchiveViewModel::class.java] }
    private lateinit var status: TextView
    private lateinit var journal: TextView
    private lateinit var export: Button
    private lateinit var inspect: Button
    private lateinit var retry: Button
    private lateinit var undo: Button
    private lateinit var preview: TextView
    private lateinit var previewActions: LinearLayout
    private lateinit var importButton: Button
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private val createZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) viewModel.endPicker("Экспорт отменён.")
        else {
            viewModel.endPicker()
            viewModel.export(uri)
        }
    }
    private val openZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) viewModel.endPicker("Выбор архива отменён.")
        else {
            viewModel.endPicker()
            viewModel.inspect(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 20.dp, 18.dp, 28.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun note(value: String = "") = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(LegionUiSupport.MUTED)
            setPadding(0, 6.dp, 0, 10.dp)
            setLineSpacing(4f, 1f)
            root.addView(this)
        }
        fun action(label: String, featureId: String? = null, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            if (featureId != null) FeatureReveal.bind(this, featureId)
            setOnClickListener { onClick() }
            root.addView(this)
        }
        root.addView(TextView(this).apply {
            text = "Данные"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            isAccessibilityHeading = true
            setTextColor(Color.WHITE)
        })
        note("В ZIP входят локальная история SQLite, профили и выбранные настройки Legion Control. " +
            "Видео MP4 и снимки PNG хранятся отдельно в медиатеке. Архив может содержать данные о ваших приложениях и сессиях.")
        status = note().apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        journal = note().apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        retry = action("Завершить восстановление", "capture.resume") { viewModel.retryInterruptedRestore() }
        undo = action("Вернуть настройки игрового профиля") {
            GameLaunchProfileActions.confirmRestore(this) { result ->
                status.text = result
                status.visibility = View.VISIBLE
                LegionUiSupport.notice(status, LegionUiSupport.Notice.INFO)
                viewModel.refresh()
                viewModel.state.value?.let { updateActions(it) }
            }
        }
        export = action("Экспортировать ZIP", "capture.backup") {
            if (viewModel.beginPicker()) runCatching { createZip.launch("LegionControl-Data-Backup.zip") }
                .onFailure { viewModel.endPicker("Android не открыл выбор места для ZIP.", error = true) }
        }
        inspect = action("Проверить ZIP", "capture.restore") {
            if (viewModel.beginPicker()) runCatching { openZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                .onFailure { viewModel.endPicker("Android не открыл выбор архива.", error = true) }
        }
        preview = note()
        importButton = Button(this).apply {
            text = "Восстановить данные"
            isAllCaps = false
            setOnClickListener { viewModel.state.value?.checked?.let(::confirmRestore) }
        }
        previewActions = CompactActionRow(this, importButton, Button(this).apply {
            text = "Закрыть отчёт"
            isAllCaps = false
            setOnClickListener { viewModel.clearPreview() }
        })
        root.addView(previewActions)
        LegionUiSupport.setModuleActivityContent(this, ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }, "ДАННЫЕ")
        viewModel.state.observe(this, ::render)
        FeatureReveal.fromActivity(this, root, FeatureModule.DATA)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        viewModel.state.value?.let(::updateActions)
    }

    private fun render(state: DataArchiveState) {
        status.text = state.message
        status.visibility = if (state.message.isBlank()) View.GONE else View.VISIBLE
        LegionUiSupport.notice(status, when {
            state.error -> LegionUiSupport.Notice.ERROR
            state.running -> LegionUiSupport.Notice.LOADING
            else -> LegionUiSupport.Notice.INFO
        })
        val checked = state.checked?.preview
        preview.visibility = if (checked == null) View.GONE else View.VISIBLE
        previewActions.visibility = preview.visibility
        if (checked != null) {
            preview.text = "ZIP v${checked.archiveVersion} · SQLite v${checked.schema}\n" +
                "Таблиц: ${checked.tables} · записей: ${checked.rows} · параметров: ${checked.settings}\n" +
                "Сетевых тестов: ${checked.networkRuns} · тестов батареи: ${checked.batteryRuns}\n" +
                "Импорт доступен для пустой базы. Настройки из архива заменят соответствующие локальные настройки."
            LegionUiSupport.reportSurface(preview)
        }
        updateActions(state)
    }

    private fun updateActions(state: DataArchiveState) {
        val available = !state.running && !state.choosing
        export.isEnabled = available && !state.pendingRestore
        inspect.isEnabled = export.isEnabled
        importButton.isEnabled = export.isEnabled && state.checked?.preview?.identity != null
        retry.isEnabled = available && state.pendingRestore
        undo.visibility = if (GameLaunchProfiles.pendingPackage(this) != null) View.VISIBLE else View.GONE
        undo.isEnabled = available
        journal.text = if (state.pendingRestore)
            "Найден журнал прерванного импорта. Можно завершить восстановление без повторного выбора ZIP."
        else "Незавершённых импортов нет."
    }

    private fun confirmRestore(checked: CheckedArchive) {
        val versionNote = when (checked.preview.archiveVersion) {
            1 -> " Текущие требования Game Preflight и отдельные архивы диагностики сохранятся."
            2 -> " Отдельные архивы диагностики сохранятся."
            else -> " Для импорта архивов диагностики их локальные хранилища тоже должны быть пустыми."
        }
        LegionDialogs.builder(this).setTitle("Восстановить данные из проверенного ZIP?")
            .setMessage("Импорт выполняется в пустую SQLite-базу. Настройки, включённые в архив, будут заменены." +
                versionNote)
            .setPositiveButton("Восстановить") { _, _ -> viewModel.restore(checked) }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
