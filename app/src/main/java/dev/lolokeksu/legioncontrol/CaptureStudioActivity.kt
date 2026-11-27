package dev.lolokeksu.legioncontrol

import androidx.activity.ComponentActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal enum class CaptureStudioPane(val label: String) {
    CAPTURE("Захват"), LIBRARY("Медиатека"), SETTINGS("Параметры");

    companion object {
        fun initial(saved: String?, requested: String?, featureId: String?): CaptureStudioPane {
            entries.firstOrNull { it.name == saved }?.let { return it }
            return when (FeatureFinder.resolve(featureId, FeatureModule.CAPTURE)?.id) {
                "capture.clips" -> LIBRARY
                "capture.quality", "capture.audio", "capture.backup", "capture.restore", "capture.resume" -> SETTINGS
                "capture.record", "capture.replay", "capture.screenshot" -> CAPTURE
                else -> entries.firstOrNull { it.name == requested } ?: CAPTURE
            }
        }
    }
}

/**
 * Video-only or opted-in game/media playback audio capture and app-owned clip library.
 * No background scan, recording without system consent, microphone or cloud upload.
 */
class CaptureStudioActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GAME_PACKAGE = "capture_game_package"
        const val EXTRA_PANE = "capture_pane"
        private const val STATE_PANE = "capture_selected_pane"
        private const val STATE_QUERY = "capture_media_query"
        private const val STATE_FORMAT = "capture_media_format"
        private const val REQUEST_CAPTURE = 25001
        private const val REQUEST_SCREENSHOT = 25002
        private const val REQUEST_PLAYBACK_PERMISSION = 25003
        private const val REQUEST_EXPORT_GAME_PROFILES = 25004
        private const val REQUEST_IMPORT_GAME_PROFILES = 25005
        private const val REQUEST_REPLAY = 25006
        private const val REQUEST_REPLAY_AUDIO_PERMISSION = 25007
        private const val REQUEST_EXPORT_FULL_BACKUP = 25008
        private const val REQUEST_IMPORT_FULL_BACKUP = 25009
    }

    private val requestedGame by lazy { intent.getStringExtra(EXTRA_GAME_PACKAGE) }
    private val viewModel by lazy {
        val factory = viewModelFactory {
            initializer { CaptureStudioViewModel(application, requestedGame) }
        }
        ViewModelProvider(this, factory)[CaptureStudioViewModel::class.java]
    }
    private lateinit var panes: Map<CaptureStudioPane, LinearLayout>
    private lateinit var paneSelector: Spinner
    private lateinit var studioScroll: ScrollView
    private var pane = CaptureStudioPane.CAPTURE
    private lateinit var mediaItems: LinearLayout
    private lateinit var status: TextView
    private lateinit var choices: TextView
    private lateinit var captureState: TextView
    private lateinit var mediaState: TextView
    private lateinit var recordStart: Button
    private lateinit var replayStart: Button
    private lateinit var shotStart: Button
    private lateinit var recordToggle: Button
    private lateinit var recordStop: Button
    private lateinit var replaySave: Button
    private lateinit var replayStop: Button
    private lateinit var shotCancel: Button
    private val uiHandler = Handler(Looper.getMainLooper())
    private val stateTick = object : Runnable {
        override fun run() {
            renderCaptureControls()
            uiHandler.postDelayed(this, 1000L)
        }
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 20.dp, 18.dp, 28.dp)
            setBackgroundColor(Color.BLACK)
        }
        var section = root
        fun heading(value: String) {
            section.addView(TextView(this).apply {
                text = value
                textSize = 20f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 14.dp, 0, 12.dp)
                isAccessibilityHeading = true
            })
        }
        fun note(value: String) {
            section.addView(TextView(this).apply {
                text = value
                textSize = 13f
                setTextColor(0xFFC1C4CB.toInt())
                setLineSpacing(4f, 1f)
                setPadding(0, 0, 0, 16.dp)
            })
        }
        fun control(value: String, featureId: String? = null, onClick: () -> Unit): Button =
            Button(this).apply {
                text = value
                if(featureId != null) FeatureReveal.bind(this, featureId)
                LegionUiSupport.styleAction(this)
                setOnClickListener { onClick(); renderCaptureControls() }
            }
        fun action(value: String, featureId: String? = null, onClick: () -> Unit): Button =
            control(value, featureId, onClick).also { section.addView(it) }
        fun stateLabel(): TextView = TextView(this).apply {
            textSize = 13f
            setTextColor(LegionUiSupport.MUTED)
            setPadding(0, 4.dp, 0, 10.dp)
        }
        heading("Медиацентр")

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFD38F.toInt())
            setPadding(0, 8.dp, 0, 18.dp)
            setLineSpacing(4f, 1f)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        LegionUiSupport.notice(status, LegionUiSupport.Notice.INFO)
        section.addView(status)

        captureState = stateLabel()
        root.addView(captureState)
        paneSelector = LegionUiSupport.spinner(this, CaptureStudioPane.entries.map { it.label }).apply {
            contentDescription = "Раздел медиацентра"
        }
        root.addView(paneSelector)
        panes = CaptureStudioPane.entries.associateWith {
            LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        }
        panes.values.forEach { root.addView(it) }
        section = panes.getValue(CaptureStudioPane.CAPTURE)
        recordStart = control("НАЧАТЬ ЗАПИСЬ ЭКРАНА", "capture.record") { askConsent() }
        replayStart = control("ЗАПУСТИТЬ REPLAY-БУФЕР", "capture.replay") {
            LegionDialogs.builder(this)
                .setTitle("Длительность Replay")
                .setItems(arrayOf("30 секунд", "60 секунд", "120 секунд")) { _, chosen ->
                    askReplayConsent(intArrayOf(30, 60, 120)[chosen])
                }.setNegativeButton("Отмена", null).show()
        }
        section.addView(CompactActionRow(this, recordStart, replayStart))
        shotStart = action("СДЕЛАТЬ СКРИНШОТ ЭКРАНА", "capture.screenshot") { askScreenshotConsent() }
        recordToggle = control("ПАУЗА") {
            if (CaptureRecordingService.recording) {
                val paused = CaptureRecordingService.paused
                runCatching {
                    if (paused) CaptureRecordingService.resume(this)
                    else CaptureRecordingService.pause(this)
                }.onSuccess {
                    status.text = if (paused) "Возобновление запрошено." else "Пауза запрошена."
                }.onFailure { status.text = "Команда записи недоступна: " + it.javaClass.simpleName }
            }
        }
        recordStop = control("ОСТАНОВИТЬ ЗАПИСЬ") {
            runCatching { CaptureRecordingService.stop(this) }
                .onSuccess { status.text = "Остановка запрошена; дождитесь завершения кодирования." }
                .onFailure { status.text = "Не удалось остановить запись: " + it.javaClass.simpleName }
        }
        section.addView(CompactActionRow(this, recordToggle, recordStop))
        replaySave = control("СОХРАНИТЬ МОМЕНТ") {
            runCatching { ReplayCaptureService.save(this) }
                .onSuccess { status.text = if (it) "Сохранение MP4 запрошено. Захват продолжается."
                    else "Replay не активен." }
                .onFailure { status.text = "Не удалось сохранить момент: " + it.javaClass.simpleName }
        }
        replayStop = control("ОСТАНОВИТЬ REPLAY") {
            runCatching { ReplayCaptureService.stop(this) }
                .onSuccess { status.text = "Остановка Replay запрошена." }
                .onFailure { status.text = "Не удалось остановить Replay: " + it.javaClass.simpleName }
        }
        section.addView(CompactActionRow(this, replaySave, replayStop))
        shotCancel = action("ОТМЕНИТЬ СКРИНШОТ") {
            runCatching { CaptureScreenshotService.cancel(this) }
                .onSuccess { status.text = "Отмена снимка запрошена." }
                .onFailure { status.text = "Не удалось отменить снимок: " + it.javaClass.simpleName }
        }
        action("Обновить состояние записи") { refreshStatus() }
        action("Открыть выбранную игру") { openSelectedGame() }
        note("Replay сохраняет реально накопленные 30/60/120 секунд без остановки захвата. " +
            "PNG снимается через 5 секунд после отдельного согласия Android. " +
            "Запись, Replay и снимок не могут работать одновременно. " +
            "Внутренний звук зависит от политики выбранной игры.")

        section = panes.getValue(CaptureStudioPane.SETTINGS)
        heading("Качество и лимиты")
        choices = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8.dp)
        }
        section.addView(choices)
        action("Разрешение · 720p / 1080p", "capture.quality") { selectOption(
            "Максимальная длинная сторона",
            "edge", intArrayOf(1280, 1920), arrayOf("1280 пикселей (720p при 16:9)", "1920 пикселей (1080p при 16:9)")
        ) }
        action("Кадры · 30 / 60 запросных FPS") { selectOption(
            "Целевая кадровая частота (не измеренный FPS)",
            "fps", intArrayOf(30, 60), arrayOf("30 FPS", "60 FPS (если кодек поддерживает)")
        ) }
        action("Битрейт · 4 / 8 / 12 Мбит/с") { selectOption(
            "Битрейт кодирования",
            "mbit", intArrayOf(4, 8, 12), arrayOf("4 Мбит/с", "8 Мбит/с", "12 Мбит/с")
        ) }
        action("Звук игры · включить / выключить", "capture.audio") {
            viewModel.choosePlaybackAudio(!viewModel.playbackAudioEnabled())
        }
        note("По умолчанию без звука. Android запросит RECORD_AUDIO при запуске со звуком; " +
            "это системное название разрешения, микрофон приложение не открывает. " +
            "Звуки других приложений могут быть исключены их политикой.")
        action("Автостоп · 1 / 5 / 15 минут") { selectOption(
            "Максимальная длительность",
            "minutes", intArrayOf(1, 5, 15), arrayOf("1 минута", "5 минут", "15 минут")
        ) }

        action("Проверить доступ к игровому звуку") {
            val access=CapabilityAccessBackend.playbackAudioState(this)
            status.text="RECORD_AUDIO: "+access.label+
                ". Разрешение не гарантирует, что выбранная игра допускает захват звука."
        }

        heading("Профили записи по играм")
        if (requestedGame == null) {
            note("Выберите игру в Game Toolbox и сохраните профиль, чтобы менять параметры отдельно для неё.")
        } else {
            note("При первом изменении параметра создаётся профиль этой игры на основе общих настроек.")
        }
        action("СОХРАНИТЬ ПРОФИЛЬ ВЫБРАННОЙ ИГРЫ") { viewModel.saveSelectedGameProfile() }
        action("СБРОСИТЬ ПРОФИЛЬ ВЫБРАННОЙ ИГРЫ") {
            LegionDialogs.builder(this)
                .setTitle("Сбросить профиль записи игры?")
                .setMessage("Будут удалены только настройки записи выбранной игры. " +
                    "Общие настройки, клипы и другие игровые профили останутся.")
                .setDestructive().setPositiveButton("Сбросить") { _, _ -> viewModel.removeSelectedGameProfile() }
                .setNegativeButton("Отмена", null).show()
        }
        action("ЭКСПОРТИРОВАТЬ ИГРОВЫЕ ПРОФИЛИ В JSON") { exportGameProfiles() }
        action("ВОССТАНОВИТЬ ИГРОВЫЕ ПРОФИЛИ ИЗ JSON") { importGameProfiles() }
        note("Резервная копия создаётся только по вашему запросу. " +
            "При импорте заменяются игровые профили, но не общие настройки и не записи видео.")

        section = panes.getValue(CaptureStudioPane.LIBRARY)
        viewModel.restoreLibrary(savedInstanceState?.getString(STATE_QUERY), savedInstanceState?.getString(STATE_FORMAT))
        val search = EditText(this).apply {
            hint = "Имя файла"
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(CaptureMediaLibrary.MAX_SEARCH_LENGTH))
            setText(viewModel.libraryQuery)
        }
        section.addView(search)
        val formats = listOf(null) + CaptureMediaFormat.entries
        val format = LegionUiSupport.spinner(this, formats.map { it?.name ?: "Все форматы" }).apply {
            contentDescription = "Формат файлов медиатеки"
            setSelection(formats.indexOf(viewModel.libraryFormat).coerceAtLeast(0))
        }
        section.addView(format)
        fun runSearch() {
            viewModel.filterLibrary(search.text.toString(), formats.getOrNull(format.selectedItemPosition))
        }
        search.doAfterTextChanged { runSearch() }
        format.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = runSearch()
        }
        action("Обновить медиатеку", "capture.clips") { viewModel.refreshLibrary() }
        mediaState = stateLabel()
        section.addView(mediaState)
        action("Место на диске") { viewModel.showCaptureStorageUsage() }
        action("Отменить обработку клипа") { viewModel.cancelClipEdit() }
        mediaItems = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        section.addView(mediaItems)

        section = panes.getValue(CaptureStudioPane.SETTINGS)
        heading("Полная локальная резервная копия")
        note("История SQLite и настройки Legion Control в одном ZIP через SAF. " +
            "Архив содержит сведения о ваших приложениях и сессиях — храните его приватно. " +
            "Сохранённые MP4 и PNG остаются доступными отдельно в медиатеке.")
        action("ЭКСПОРТ ИСТОРИИ И НАСТРОЕК", "capture.backup") {
            val choose = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "LegionControl-Data-Backup.zip")
            }
            runCatching { startActivityForResult(choose, REQUEST_EXPORT_FULL_BACKUP) }
                .onFailure { status.text = "SAF недоступен: " + it.javaClass.simpleName }
        }
        action("ПРОВЕРИТЬ И ВОССТАНОВИТЬ ИЗ ZIP", "capture.restore") {
            val choose = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            runCatching { startActivityForResult(choose, REQUEST_IMPORT_FULL_BACKUP) }
                .onFailure { status.text = "SAF недоступен: " + it.javaClass.simpleName }
        }

        action("ЗАВЕРШИТЬ ПРЕРВАННОЕ ВОССТАНОВЛЕНИЕ", "capture.resume") {
            viewModel.retryInterruptedRestore()
        }
        note("Если Android остановил приложение во время импорта ZIP, этот пункт " +
            "проверит сохранённое намерение и безопасно завершит настройку. " +
            "Существующие записи и настройки не стираются без проверенного импорта.")

        section = panes.getValue(CaptureStudioPane.LIBRARY)
        heading("Незавершённые файлы")
        note("Только ваши временные MP4/PNG с IS_PENDING=1 старше 24 часов. " +
            "Готовые клипы и снимки не удаляются. Перед операцией остановите все захваты.")
        action("НАЙТИ И УДАЛИТЬ ЗАВИСШИЕ ЧЕРНОВИКИ") {
            LegionDialogs.builder(this).setTitle("Удалить незавершённые файлы?")
                .setMessage("Будут удалены только временные файлы Legion Control " +
                    "старше суток, не опубликованные в MediaStore. Операция необратима.")
                .setDestructive().setPositiveButton("Очистить") { _, _ -> viewModel.cleanStalePending() }
                .setNegativeButton("Отмена", null).show()
        }
        section = panes.getValue(CaptureStudioPane.CAPTURE)
        note("Пауза и продолжение сохраняют один MP4. Время паузы не расходует лимит " +
            "активной записи. Через 5 минут непрерывной паузы запись останавливается. " +
            "Если кодек отклонит команду, захват будет завершён.")
        note("Каждый запуск требует нового согласия Android. " +
            "После подтверждения перейдите в игру — остановить запись можно из постоянного " +
            "уведомления или этого экрана. Ограничение качества/частоты зависит от кодека и прошивки.")
        studioScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        LegionUiSupport.setModuleActivityContent(this, studioScroll, "МЕДИАЦЕНТР")
        val initial = CaptureStudioPane.initial(savedInstanceState?.getString(STATE_PANE),
            intent.getStringExtra(EXTRA_PANE), intent.getStringExtra(FeatureFinder.EXTRA_FEATURE_ID))
        showPane(initial)
        paneSelector.setSelection(initial.ordinal)
        paneSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                CaptureStudioPane.entries.getOrNull(position)?.let(::showPane)
            }
        }
        viewModel.settings.observe(this) { refreshChoices() }
        viewModel.playbackAudio.observe(this) { refreshChoices() }
        viewModel.message.observe(this) { status.text = it; LegionUiSupport.notice(status, LegionUiSupport.Notice.INFO) }
        viewModel.backupReady.observe(this) {
            val inspected = viewModel.takeBackupPreview() ?: return@observe
            LegionDialogs.builder(this)
                .setTitle("Восстановить локальные данные?")
                .setMessage("Архив проверен: SQLite v${inspected.preview.schema}, " +
                    "таблиц ${inspected.preview.tables}, записей ${inspected.preview.rows}, " +
                    "параметров ${inspected.preview.settings}.\n" +
                    "Сетевых тестов: ${inspected.preview.networkRuns}; батарейных: ${inspected.preview.batteryRuns}.\n\n" +
                    "Импорт разрешён только в ПУСТУЮ базу и не удаляет существующую " +
                    "историю. Настройки, включённые в архив, будут заменены. " +
                    (if(inspected.preview.archiveVersion == 1)
                        "ZIP v1 не содержит Game Preflight: текущие требования игр сохранятся. "
                    else if(inspected.preview.archiveVersion==2) "ZIP v2 включает Game Preflight, но не новые архивы диагностики. "
                    else "ZIP v3 включает новые архивы диагностики; оба должны быть пустыми. ") +
                    "MP4/PNG и ключ подписи не импортируются.")
                .setPositiveButton("Восстановить") { _, _ ->
                    val confirmed = inspected.preview.identity
                    if(confirmed == null) status.text = "Архив нужно проверить заново."
                    else viewModel.restoreFullBackup(inspected.uri, confirmed)
                }
                .setNegativeButton("Отмена", null).show()
        }
        viewModel.library.observe(this, ::renderMedia)
        viewModel.mediaAccess.observe(this) { event ->
            viewModel.takeMediaAccess(event)?.let(::openMedia)
        }
        refreshChoices()
        refreshStatus()
        renderCaptureControls()
        FeatureReveal.fromActivity(this, root, FeatureModule.CAPTURE)
    }

    private fun showPane(next: CaptureStudioPane) {
        val changed = next != pane
        pane = next
        panes.forEach { (key, view) -> view.visibility = if (key == next) View.VISIBLE else View.GONE }
        viewModel.setLibraryVisible(next == CaptureStudioPane.LIBRARY &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        if (changed) studioScroll.scrollTo(0, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PANE, pane.name)
        outState.putString(STATE_QUERY, viewModel.libraryQuery)
        outState.putString(STATE_FORMAT, viewModel.libraryFormat?.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            viewModel.setLibraryVisible(pane == CaptureStudioPane.LIBRARY)
            viewModel.refreshSelectedGameSettings()
            refreshStatus()
            refreshChoices()
            uiHandler.removeCallbacks(stateTick)
            stateTick.run()
        }
    }

    override fun onPause() {
        viewModel.setLibraryVisible(false)
        uiHandler.removeCallbacks(stateTick)
        super.onPause()
    }

    private fun renderCaptureControls() {
        if (!::captureState.isInitialized || !::shotCancel.isInitialized) return
        val (mode, phase) = CaptureCoordinator.state()
        val recording = CaptureRecordingService.recording
        val replay = ReplayCaptureService.active
        val screenshot = CaptureScreenshotService.active
        val idle = mode == null && !recording && !replay && !screenshot
        val actionable = phase != CapturePhase.STOPPING
        val name = when (mode) {
            CaptureMode.RECORDING -> "Запись"
            CaptureMode.REPLAY -> "Replay"
            CaptureMode.SCREENSHOT -> "Скриншот"
            CaptureMode.MAINTENANCE -> "Обслуживание"
            null -> when {
                recording -> "Запись"
                replay -> "Replay"
                screenshot -> "Скриншот"
                else -> "Нет активного захвата"
            }
        }
        val state = when (phase) {
            CapturePhase.CONSENT_PENDING -> "ожидание согласия Android"
            CapturePhase.STARTING -> "запуск"
            CapturePhase.STOPPING -> "завершение"
            CapturePhase.ACTIVE -> if (recording && CaptureRecordingService.paused) "пауза" else "активен"
            CapturePhase.IDLE -> if (idle) "готов к запуску" else "активен"
        }
        val summary = when {
            idle -> name
            replay && actionable -> "$name · $state\n" + ReplayCaptureService.status(this)
            else -> "$name · $state"
        }
        if (captureState.text != summary) captureState.text = summary
        listOf(recordStart, replayStart, shotStart).forEach { it.isEnabled = idle }
        val toggle = if (CaptureRecordingService.paused) "ПРОДОЛЖИТЬ" else "ПАУЗА"
        if (recordToggle.text != toggle) recordToggle.text = toggle
        recordToggle.visibility = if (recording && actionable) View.VISIBLE else View.GONE
        recordStop.visibility = recordToggle.visibility
        replaySave.visibility = if (replay && actionable) View.VISIBLE else View.GONE
        replayStop.visibility = replaySave.visibility
        shotCancel.visibility = if (screenshot && actionable) View.VISIBLE else View.GONE
    }

    private fun refreshChoices() {
        val active = viewModel.captureSettings()
        val config = active.profile.toConfig()
        val game = active.selectedGame
        val profileLabel = when {
            game == null && requestedGame != null -> "Выбранную игру нельзя запустить · $requestedGame"
            game == null -> "Общие настройки · игра не выбрана"
            active.usingGameProfile -> "Профиль игры · $game"
            else -> "Общие настройки для игры · $game"
        }
        choices.text = profileLabel + "\n" +
            "Сторона до ${config.maxEdge} px · ${config.fps} FPS запрошено\n" +
            "${config.videoBitrate / 1_000_000} Мбит/с · " +
            "${config.maxDurationMs / 60_000} мин максимум\n" +
            (if (active.profile.playbackAudio)
                "Внутренний звук: ЗАПРОШЕН (AAC)\nИгра: " + (game ?: "не выбрана")
             else "Внутренний звук: ВЫКЛЮЧЕН")
    }

    private fun canStartCapture(settings: ActiveGameCaptureSettings): Boolean {
        if (settings.selectedGame != null) return true
        if (requestedGame != null) {
            status.text = "Выбранную игру нельзя запустить. Вернитесь в её карточку."
            return false
        }
        if (!settings.profile.playbackAudio) return true
        status.text = "Для захвата со звуком выберите игру в Game Toolbox."
        return false
    }

    private fun selectOption(title: String, name: String, values: IntArray, labels: Array<String>) {
        LegionDialogs.builder(this).setTitle(title).setItems(labels) { _, index ->
            viewModel.setOption(name, values[index])
        }.setNegativeButton("Отмена", null).show()
    }

    private fun refreshStatus() {
        status.text = viewModel.lastCaptureResult()
    }

    private fun askConsent() {
        val settings = viewModel.captureSettings()
        if (!canStartCapture(settings)) return
        if (settings.profile.playbackAudio &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PLAYBACK_PERMISSION)
            status.text = "Для захвата разрешённого игрового звука Android требует RECORD_AUDIO. " +
                "Микрофон не используется."
            return
        }
        if (CaptureRecordingService.recording || CaptureScreenshotService.active || ReplayCaptureService.active) {
            status.text = "Выполняется другой захват. Сначала завершите его."
            return
        }
        if (!viewModel.reserveConsent(CaptureMode.RECORDING)) {
            status.text = "MediaProjection занят: дождитесь завершения другого захвата."
            return
        }
        runCatching {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // Fresh Intent and user prompt for EVERY capture; never persist or reuse token.
            viewModel.rememberCaptureConsent(settings)
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        }.onFailure {
            viewModel.clearCaptureConsent()
            viewModel.cancelReservedConsent()
            status.text = "Системный запрос записи недоступен: " + it.javaClass.simpleName
        }
    }

    private fun askReplayConsent(wantedSeconds: Int) {
        if (ReplayCaptureService.active || CaptureRecordingService.recording ||
            CaptureScreenshotService.active) {
            status.text = "Другой захват уже работает. Сначала остановите его."
            return
        }
        val snapshot = viewModel.captureSettings()
        if (!canStartCapture(snapshot)) return
        if (snapshot.profile.playbackAudio &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            viewModel.rememberReplayAudioPermission(wantedSeconds)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_REPLAY_AUDIO_PERMISSION)
            return
        }
        if (!viewModel.reserveConsent(CaptureMode.REPLAY)) {
            status.text = "MediaProjection занят: дождитесь завершения другого захвата."
            return
        }
        runCatching {
            // Freeze game package, sound flag and quality before fresh Android consent.
            viewModel.rememberReplayConsent(wantedSeconds, snapshot)
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_REPLAY)
        }.onFailure {
            viewModel.clearReplayConsent()
            viewModel.cancelReservedConsent()
            status.text = "Не удалось запросить Replay: " + it.javaClass.simpleName
        }
    }

    private fun askScreenshotConsent() {
        if (!viewModel.reserveConsent(CaptureMode.SCREENSHOT)) {
            status.text = "Другой захват или запрос разрешения уже выполняется."
            return
        }
        runCatching {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_SCREENSHOT)
        }.onFailure {
            viewModel.cancelReservedConsent()
            status.text = "Android не разрешил запрос снимка: " + it.javaClass.simpleName
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_REPLAY_AUDIO_PERMISSION) {
            val seconds = viewModel.takeReplayAudioPermission()
            if (seconds != null && grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                askReplayConsent(seconds)
            } else {
                status.text = "Нет разрешения на звук игры. Отключите звук в профиле или выдайте разрешение."
            }
            return
        }
        if (requestCode != REQUEST_PLAYBACK_PERMISSION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            askConsent()
        } else {
            status.text = "Нет разрешения RECORD_AUDIO. Видео без звука доступно: выключите режим звука."
        }
    }

    private fun exportGameProfiles() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "LegionCaptureProfiles.json")
        }
        runCatching { startActivityForResult(intent, REQUEST_EXPORT_GAME_PROFILES) }
            .onFailure { status.text = "Не удалось открыть сохранение: " + it.javaClass.simpleName }
    }

    private fun importGameProfiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        runCatching { startActivityForResult(intent, REQUEST_IMPORT_GAME_PROFILES) }
            .onFailure { status.text = "Не удалось открыть файл: " + it.javaClass.simpleName }
    }

    @Deprecated("Legacy Activity result API works without introducing AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXPORT_FULL_BACKUP) {
            if (resultCode == RESULT_OK && data?.data != null)
                viewModel.exportFullBackup(data.data!!)
            return
        }
        if (requestCode == REQUEST_IMPORT_FULL_BACKUP) {
            if (resultCode == RESULT_OK && data?.data != null)
                viewModel.inspectFullBackup(data.data!!)
            return
        }
        if (requestCode == REQUEST_EXPORT_GAME_PROFILES) {
            if (resultCode == RESULT_OK && data?.data != null) {
                viewModel.exportGameProfiles(data.data!!)
            }
            return
        }
        if (requestCode == REQUEST_IMPORT_GAME_PROFILES) {
            if (resultCode == RESULT_OK && data?.data != null) {
                val uri = data.data!!
                LegionDialogs.builder(this).setTitle("Восстановить профили?")
                    .setMessage("Сохранённые профили ВСЕХ игр будут заменены содержимым JSON. " +
                        "Общие настройки и ваши записи не изменятся. Некорректный файл отклоняется полностью.")
                    .setPositiveButton("Импортировать") { _, _ -> viewModel.importGameProfiles(uri) }
                    .setNegativeButton("Отмена", null).show()
            }
            return
        }
        if (requestCode == REQUEST_REPLAY) {
            if (!viewModel.takeReservedConsent(CaptureMode.REPLAY)) {
                viewModel.clearReplayConsent()
                status.text = "Согласие Replay больше не действительно."
                return
            }
            val replay = viewModel.takeReplayConsent()
            if (resultCode != RESULT_OK || data == null || replay == null) {
                CaptureCoordinator.releasePending(CaptureMode.REPLAY)
                status.text = "Replay не запущен: согласие Android или параметры не получены."
                return
            }
            val config = replay.config
            if (replay.playbackAudio &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                CaptureCoordinator.releasePending(CaptureMode.REPLAY)
                status.text = "Разрешение RECORD_AUDIO отозвано. Replay не запущен."
                return
            }
            val start = Intent(this, ReplayCaptureService::class.java).apply {
                action = ReplayCaptureService.ACTION_START
                putExtra(ReplayCaptureService.EXTRA_RESULT, resultCode)
                putExtra(ReplayCaptureService.EXTRA_CONSENT, data)
                putExtra(ReplayCaptureService.EXTRA_SECONDS, replay.seconds)
                putExtra(ReplayCaptureService.EXTRA_PLAYBACK_AUDIO, replay.playbackAudio)
                if (replay.playbackAudio && replay.selectedGame != null)
                    putExtra(ReplayCaptureService.EXTRA_AUDIO_GAME, replay.selectedGame)
                putExtra(ReplayCaptureService.EXTRA_EDGE, config.maxEdge)
                putExtra(ReplayCaptureService.EXTRA_FPS, config.fps)
                putExtra(ReplayCaptureService.EXTRA_MBIT,
                    config.videoBitrate / 1_000_000)
            }
            runCatching { startForegroundService(start) }
                .onSuccess { status.text = "Replay запускается. Сохраняйте моменты из Game Toolbox." }
                .onFailure {
                    CaptureCoordinator.releasePending(CaptureMode.REPLAY)
                    status.text = "Android не разрешил запуск Replay: " +
                        it.javaClass.simpleName
                }
            return
        }
        val mode = when (requestCode) {
            REQUEST_CAPTURE -> CaptureMode.RECORDING
            REQUEST_SCREENSHOT -> CaptureMode.SCREENSHOT
            else -> return
        }
        if (!viewModel.takeReservedConsent(mode)) {
            if (mode == CaptureMode.RECORDING) viewModel.clearCaptureConsent()
            status.text = "Согласие на захват больше не действительно."
            return
        }
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQUEST_CAPTURE) {
                viewModel.clearCaptureConsent()
                CaptureCoordinator.releasePending(CaptureMode.RECORDING)
            } else CaptureCoordinator.releasePending(CaptureMode.SCREENSHOT)
            status.text = "Системный доступ к захвату не выдан. Файлы не создавались."
            return
        }
        if (requestCode == REQUEST_SCREENSHOT) {
            val screenshot = Intent(this, CaptureScreenshotService::class.java).apply {
                action = CaptureScreenshotService.ACTION_START
                putExtra(CaptureScreenshotService.EXTRA_RESULT, resultCode)
                putExtra(CaptureScreenshotService.EXTRA_CONSENT, data)
            }
            runCatching { startForegroundService(screenshot) }
                .onSuccess {
                    status.text = "Запуск снимка запрошен. В течение 5 секунд переключитесь в игру."
                }
                .onFailure {
                    CaptureCoordinator.releasePending(CaptureMode.SCREENSHOT)
                    status.text = "Сервис снимка отклонён: " + it.javaClass.simpleName
                }
            return
        }
        val snapshot = viewModel.takeCaptureConsent()
        if (snapshot == null) {
            CaptureCoordinator.releasePending(CaptureMode.RECORDING)
            status.text = "Настройки запроса утрачены. Повторите запрос записи экрана."
            return
        }
        val playbackRequested = snapshot.profile.playbackAudio
        val audioGame = if (playbackRequested) snapshot.selectedGame else null
        if (playbackRequested && audioGame == null) {
            CaptureCoordinator.releasePending(CaptureMode.RECORDING)
            status.text = "Выбранная игра недоступна. Повторите запрос записи."
            return
        }
        if (playbackRequested &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CaptureCoordinator.releasePending(CaptureMode.RECORDING)
            status.text = "Разрешение RECORD_AUDIO отозвано. Запись со звуком не начата."
            return
        }
        val config = snapshot.profile.toConfig()
        val intent = Intent(this, CaptureRecordingService::class.java).apply {
            putExtra(CaptureRecordingService.EXTRA_PLAYBACK_AUDIO, playbackRequested)
            if (audioGame != null) putExtra(CaptureRecordingService.EXTRA_AUDIO_GAME, audioGame)
            action = CaptureRecordingService.ACTION_START
            putExtra(CaptureRecordingService.EXTRA_RESULT, resultCode)
            putExtra(CaptureRecordingService.EXTRA_CONSENT, data)
            putExtra(CaptureRecordingService.EXTRA_EDGE, config.maxEdge)
            putExtra(CaptureRecordingService.EXTRA_FPS, config.fps)
            putExtra(CaptureRecordingService.EXTRA_MBIT, config.videoBitrate / 1_000_000)
            putExtra(CaptureRecordingService.EXTRA_MINUTES, (config.maxDurationMs / 60_000).toInt())
        }
        runCatching { startForegroundService(intent) }
            .onSuccess {
                status.text = "Согласие получено. Запуск захвата запрошен; проверьте уведомление. " +
                    "Если кодек или ROM откажут, файла не будет."
            }.onFailure {
                CaptureCoordinator.releasePending(CaptureMode.RECORDING)
                status.text = "Android не разрешил запустить сервис: " + it.javaClass.simpleName
            }
    }

    private fun openSelectedGame() {
        val game = viewModel.captureSettings().selectedGame
        val appIntent = game?.let { packageManager.getLaunchIntentForPackage(it) }
        if (appIntent == null) {
            status.text = if (requestedGame == null) "Выберите доступную игру в Game Toolbox."
                else "Выбранную игру нельзя запустить."
            return
        }
        runCatching { startActivity(appIntent) }
            .onFailure { status.text = "Игра не открыта: " + it.javaClass.simpleName }
    }

    private fun renderMedia(state: CaptureLibraryState) {
        mediaItems.removeAllViews()
        val notice = when (state) {
            CaptureLibraryState.Idle, CaptureLibraryState.Loading -> {
                mediaState.text = "Загружаю медиатеку…"
                LegionUiSupport.Notice.LOADING
            }
            is CaptureLibraryState.Error -> {
                mediaState.text = state.message
                LegionUiSupport.Notice.ERROR
            }
            is CaptureLibraryState.Ready -> {
                val files = state.page.items
                mediaState.text = if (files.isEmpty()) "Файлы не найдены." else "Файлов на странице: ${files.size}"
                for (item in files) {
                    val size = String.format(Locale.getDefault(), "%.1f МиБ", item.bytes / 1048576.0)
                    val length = if (item.durationMs > 0) " · ${item.durationMs / 1000} с" else ""
                    val date = if (item.key.addedSeconds > 0) {
                        " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(item.key.addedSeconds * 1000L))
                    } else ""
                    mediaItems.addView(LegionUiSupport.actionTile(this, item.name, "${item.format.name} · $size$length$date") {
                        if (item.format == CaptureMediaFormat.MP4) clipActions(item) else screenshotActions(item)
                    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6.dp })
                }
                if (state.hasPrevious) mediaPageButton("Предыдущая страница", viewModel::previousMediaPage)
                if (state.page.next != null) mediaPageButton("Следующая страница", viewModel::nextMediaPage)
                if (files.isEmpty()) LegionUiSupport.Notice.EMPTY else LegionUiSupport.Notice.INFO
            }
        }
        LegionUiSupport.notice(mediaState, notice)
    }

    private fun mediaPageButton(label: String, action: () -> Unit) {
        mediaItems.addView(Button(this).apply {
            text = label
            LegionUiSupport.styleAction(this)
            setOnClickListener { action() }
        })
    }

    private fun clipActions(clip: CaptureMedia) {
        LegionDialogs.builder(this).setTitle(clip.name)
            .setItems(arrayOf(
                "Просмотреть", "Поделиться",
                "Создать фрагмент · первые 15 секунд",
                "Создать фрагмент · последние 15 секунд",
                "Создать фрагмент · последние 30 секунд",
                "Создать фрагмент · выбрать границы",
                "Сохранить кадр PNG",
                "Удалить файл"
            )) { _, which ->
                when (which) {
                    0 -> viewModel.accessMedia(clip, share = false)
                    1 -> viewModel.accessMedia(clip, share = true)
                    2 -> viewModel.saveClipExcerpt(clip, ClipTrimSelection.First(15_000))
                    3 -> viewModel.saveClipExcerpt(clip, ClipTrimSelection.Last(15_000))
                    4 -> viewModel.saveClipExcerpt(clip, ClipTrimSelection.Last(30_000))
                    5 -> chooseExcerptRange(clip)
                    6 -> chooseVideoFrame(clip)
                    7 -> confirmDelete(clip)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun numericField(hint: String): EditText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9399A6.toInt())
        setSelectAllOnFocus(true)
    }

    private fun chooseExcerptRange(clip: CaptureMedia) {
        val begin = numericField("От, секунд · например 10")
        val end = numericField("До, секунд · например 25")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 4.dp)
            addView(begin)
            addView(end)
        }
        LegionDialogs.builder(this)
            .setTitle("Сохранить MP4-фрагмент")
            .setMessage("Границы в секундах. Минимум 1 секунда. " +
                "Видео будет начинаться с предыдущего ключевого кадра. " +
                "Исходный MP4 останется без изменений." +
                (if (clip.durationMs > 0) " Исходная длительность: ${clip.durationMs / 1000} с."
                 else " Длительность проверится перед экспортом."))
            .setView(fields)
            .setPositiveButton("Создать") { _, _ ->
                val start = begin.text.toString().toLongOrNull()
                val finish = end.text.toString().toLongOrNull()
                if (start == null || finish == null || start < 0L ||
                    finish <= start || finish > 86_400L) {
                    status.text = "Укажите корректные границы в целых секундах."
                } else {
                    viewModel.saveClipExcerpt(
                        clip, ClipTrimSelection.Custom(start * 1000L, finish * 1000L)
                    )
                }
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun chooseVideoFrame(clip: CaptureMedia) {
        LegionDialogs.builder(this).setTitle("Экспорт PNG-кадра")
            .setItems(arrayOf("Первый кадр", "Кадр из середины", "Указать секунду")) { _, choice ->
                when (choice) {
                    0 -> viewModel.saveClipFrame(clip, 0L)
                    1 -> viewModel.saveClipFrame(clip, null)
                    2 -> {
                        val at = numericField("Секунда в видео")
                        LegionDialogs.builder(this)
                            .setTitle("Кадр по времени")
                            .setView(at)
                            .setPositiveButton("Сохранить PNG") { _, _ ->
                                val seconds = at.text.toString().toLongOrNull()
                                if (seconds == null || seconds < 0 || seconds > 86_400L) {
                                    status.text = "Укажите корректную секунду."
                                } else viewModel.saveClipFrame(clip, seconds * 1000L)
                            }
                            .setNegativeButton("Отмена", null).show()
                    }
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun openMedia(access: CaptureMediaAccess) {
        val item = access.item
        runCatching {
            val target = if (access.share) {
                Intent(Intent.ACTION_SEND).apply {
                    type = item.format.mimeType
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    clipData = ClipData.newRawUri(item.name, item.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.let { Intent.createChooser(it, "Поделиться файлом") }
            } else Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, item.format.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(target)
        }.onFailure { status.text = "Не удалось открыть файл: " + it.javaClass.simpleName }
    }

    private fun screenshotActions(item: CaptureMedia) {
        LegionDialogs.builder(this).setTitle(item.name)
            .setItems(arrayOf("Просмотреть", "Поделиться", "Удалить файл")) { _, which ->
                when (which) {
                    0 -> viewModel.accessMedia(item, share = false)
                    1 -> viewModel.accessMedia(item, share = true)
                    2 -> confirmDelete(item)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun confirmDelete(item: CaptureMedia) {
        LegionDialogs.builder(this)
            .setTitle("Безвозвратно удалить файл?")
            .setMessage("Будет удалён только ${item.name}.")
            .setDestructive().setPositiveButton("Удалить") { _, _ -> viewModel.deleteConfirmed(item) }
            .setNegativeButton("Отмена", null).show()
    }
}
