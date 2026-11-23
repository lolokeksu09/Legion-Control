package dev.lolokeksu.legioncontrol

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import android.Manifest
import android.content.pm.PackageManager
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Video-only or opted-in game/media playback audio capture and app-owned clip library.
 * No background scan, recording without system consent, microphone or cloud upload.
 */
class CaptureStudioActivity : ComponentActivity() {
    companion object {
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

    private val viewModel by lazy { ViewModelProvider(this)[CaptureStudioViewModel::class.java] }
    private lateinit var clipsContainer: LinearLayout
    private lateinit var shotsContainer: LinearLayout
    private lateinit var status: TextView
    private lateinit var choices: TextView
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 34.dp, 20.dp, 32.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun heading(value: String) {
            root.addView(TextView(this).apply {
                text = value
                textSize = 20f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 14.dp, 0, 12.dp)
            })
        }
        fun note(value: String) {
            root.addView(TextView(this).apply {
                text = value
                textSize = 13f
                setTextColor(0xFFC1C4CB.toInt())
                setLineSpacing(4f, 1f)
                setPadding(0, 0, 0, 16.dp)
            })
        }
        fun action(value: String, onClick: () -> Unit) {
            root.addView(Button(this).apply {
                text = value
                LegionUiSupport.styleAction(this)
                setOnClickListener { onClick() }
            })
        }
        heading("CAPTURE STUDIO")
        note("Записывает экран в MP4 по разовому системному разрешению Android. " +
            "Внутренний звук включается отдельно; микрофон не используется. " +
            "DRM и защищённые окна могут быть чёрными.")

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFD38F.toInt())
            setPadding(0, 8.dp, 0, 18.dp)
            setLineSpacing(4f, 1f)
        }
        root.addView(status)

        heading("Качество и лимиты")
        choices = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8.dp)
        }
        root.addView(choices)
        action("Разрешение · 720p / 1080p") { selectOption(
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
        action("Звук игры · включить / выключить") {
            viewModel.choosePlaybackAudio(!viewModel.playbackAudioEnabled())
        }
        note("По умолчанию без звука. Android запросит RECORD_AUDIO при запуске со звуком; " +
            "это системное название разрешения, микрофон приложение не открывает. " +
            "Звуки других приложений могут быть исключены их политикой.")
        action("Автостоп · 1 / 5 / 15 минут") { selectOption(
            "Максимальная длительность",
            "minutes", intArrayOf(1, 5, 15), arrayOf("1 минута", "5 минут", "15 минут")
        ) }

        heading("Профили записи по играм")
        note("Выберите игру в Game Toolbox. До сохранения её профиля применяются общие настройки. " +
            "После сохранения разрешение, FPS, битрейт, лимит и звук изменяются отдельно для этой игры.")
        action("СОХРАНИТЬ ПРОФИЛЬ ВЫБРАННОЙ ИГРЫ") { viewModel.saveSelectedGameProfile() }
        action("СБРОСИТЬ ПРОФИЛЬ ВЫБРАННОЙ ИГРЫ") {
            AlertDialog.Builder(this)
                .setTitle("Сбросить профиль записи игры?")
                .setMessage("Будут удалены только настройки записи выбранной игры. " +
                    "Общие настройки, клипы и другие игровые профили останутся.")
                .setPositiveButton("Сбросить") { _, _ -> viewModel.removeSelectedGameProfile() }
                .setNegativeButton("Отмена", null).show()
        }
        action("ЭКСПОРТИРОВАТЬ ИГРОВЫЕ ПРОФИЛИ В JSON") { exportGameProfiles() }
        action("ВОССТАНОВИТЬ ИГРОВЫЕ ПРОФИЛИ ИЗ JSON") { importGameProfiles() }
        note("Резервная копия создаётся только по вашему запросу. " +
            "При импорте заменяются игровые профили, но не общие настройки и не записи видео.")

        heading("Запись")
        action("НАЧАТЬ ЗАПИСЬ ЭКРАНА") { askConsent() }
        action("ПОСТАВИТЬ ЗАПИСЬ НА ПАУЗУ") {
            if (CaptureRecordingService.recording && !CaptureRecordingService.paused) {
                runCatching { CaptureRecordingService.pause(this) }
                    .onSuccess { status.text = "Пауза запрошена. Состояние обновится в уведомлении." }
                    .onFailure { status.text = "Пауза недоступна: " + it.javaClass.simpleName }
            } else {
                status.text = if (CaptureRecordingService.paused) "Запись уже на паузе."
                    else "Активной записи нет."
            }
        }
        action("ВОЗОБНОВИТЬ ЗАПИСЬ") {
            if (CaptureRecordingService.recording && CaptureRecordingService.paused) {
                runCatching { CaptureRecordingService.resume(this) }
                    .onSuccess { status.text = "Возобновление запрошено. Проверьте уведомление." }
                    .onFailure { status.text = "Возобновление недоступно: " + it.javaClass.simpleName }
            } else {
                status.text = if (CaptureRecordingService.recording) "Запись уже идёт."
                    else "Активной записи нет."
            }
        }
        action("ОСТАНОВИТЬ ЗАПИСЬ") {
            if (CaptureRecordingService.recording) {
                CaptureRecordingService.stop(this)
                status.text = "Остановка запрошена; результат появится после завершения кодирования."
            } else status.text = "Активной записи нет."
        }
        heading("INSTANT REPLAY")
        note("Сохраняет последние 30/60/120 секунд игры без остановки захвата. " +
            "Работает только после разового системного согласия Android. " +
            "Внутренний звук выбранной игры записывается при согласии Android и " +
            "если игра допускает AudioPlaybackCapture; иначе сохраняется видео без звука. " +
            "Сохраняется только реально накопленный фрагмент.")
        action("ЗАПУСТИТЬ REPLAY-БУФЕР") {
            AlertDialog.Builder(this)
                .setTitle("Длительность Replay")
                .setItems(arrayOf("30 секунд", "60 секунд", "120 секунд")) { _, chosen ->
                    askReplayConsent(intArrayOf(30, 60, 120)[chosen])
                }.setNegativeButton("Отмена", null).show()
        }
        action("СОХРАНИТЬ ПОСЛЕДНИЕ СЕКУНДЫ") {
            status.text = if (ReplayCaptureService.save(this))
                "Сохранение MP4 запрошено. Захват продолжается."
            else "Replay не активен. Сначала запустите буфер."
        }
        action("ОСТАНОВИТЬ REPLAY") {
            if (ReplayCaptureService.active) {
                ReplayCaptureService.stop(this)
                status.text = "Остановка Replay запрошена."
            } else status.text = "Replay не активен."
        }
        action("ПРОВЕРИТЬ REPLAY") { status.text = ReplayCaptureService.status(this) }

        heading("Скриншот")
        note("Один PNG после отдельного согласия Android. Через 5 секунд после согласия " +
            "будет захвачен текущий экран — переключитесь на нужную игру. " +
            "Защищённые окна могут скрываться.")
        action("СДЕЛАТЬ СКРИНШОТ ЭКРАНА") { askScreenshotConsent() }
        action("ОТМЕНИТЬ СКРИНШОТ") {
            CaptureScreenshotService.cancel(this)
            status.text = "Отмена снимка запрошена."
        }
        action("Открыть выбранную игру Game Toolbox") { openSelectedGame() }
        action("Обновить состояние записи") { refreshStatus() }

        heading("Место для записей")
        note("Показывает объём только собственных опубликованных MP4/PNG. " +
            "Автоматическое удаление отключено.")
        action("ПОСМОТРЕТЬ ОБЪЁМ ЗАПИСЕЙ") { viewModel.showCaptureStorageUsage() }

        heading("Мои клипы")
        note("Готовые MP4 — в системной папке Movies/LegionControl/. " +
            "Здесь показываются только клипы, сохранённые самим Legion Control. " +
            "Никакие чужие видео приложение не читает.")
        action("ОТКРЫТЬ БИБЛИОТЕКУ КЛИПОВ") { browseClips() }
        note("CLIP WORKSHOP · Выделяйте фрагменты MP4 без потери качества и " +
            "сохраняйте отдельные PNG-кадры. Начало MP4 округляется к предыдущему " +
            "ключевому кадру; исходный файл сохраняется.")
        action("ОТМЕНИТЬ ОБРАБОТКУ КЛИПА") { viewModel.cancelClipEdit() }
        clipsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(clipsContainer)
        heading("Мои снимки")
        note("PNG сохраняются в Pictures/LegionControl/ · только снимки этого приложения.")
        action("ОТКРЫТЬ БИБЛИОТЕКУ СНИМКОВ") { viewModel.refreshScreenshots() }
        shotsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(shotsContainer)
        heading("Полная локальная резервная копия")
        note("История SQLite и настройки Legion Control в одном ZIP через SAF. " +
            "Архив содержит сведения о ваших приложениях и сессиях — храните его приватно. " +
            "Видеофайлы MP4 и PNG в архив НЕ входят.")
        action("ЭКСПОРТ ИСТОРИИ И НАСТРОЕК") {
            val choose = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "LegionControl-Data-Backup.zip")
            }
            runCatching { startActivityForResult(choose, REQUEST_EXPORT_FULL_BACKUP) }
                .onFailure { status.text = "SAF недоступен: " + it.javaClass.simpleName }
        }
        action("ПРОВЕРИТЬ И ВОССТАНОВИТЬ ИЗ ZIP") {
            val choose = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            runCatching { startActivityForResult(choose, REQUEST_IMPORT_FULL_BACKUP) }
                .onFailure { status.text = "SAF недоступен: " + it.javaClass.simpleName }
        }

        heading("Очистка незавершённых файлов")
        note("Только ваши временные MP4/PNG с IS_PENDING=1 старше 24 часов. " +
            "Готовые клипы и снимки не удаляются. Перед операцией остановите все захваты.")
        action("НАЙТИ И УДАЛИТЬ ЗАВИСШИЕ ЧЕРНОВИКИ") {
            AlertDialog.Builder(this).setTitle("Удалить незавершённые файлы?")
                .setMessage("Будут удалены только временные файлы Legion Control " +
                    "старше суток, не опубликованные в MediaStore. Операция необратима.")
                .setPositiveButton("Очистить") { _, _ -> viewModel.cleanStalePending() }
                .setNegativeButton("Отмена", null).show()
        }
        note("Пауза и продолжение сохраняют один MP4. Время паузы не расходует лимит " +
            "активной записи. Через 5 минут непрерывной паузы запись останавливается. " +
            "Если кодек отклонит команду, захват будет завершён.")
        note("Каждый запуск требует нового согласия Android. " +
            "После подтверждения перейдите в игру — остановить запись можно из постоянного " +
            "уведомления или этого экрана. Ограничение качества/частоты зависит от кодека и прошивки.")
        setContentView(ScrollView(this).apply {
            addView(root)
            LegionUiSupport.applyInsets(this)
        })
        viewModel.settings.observe(this) { refreshChoices() }
        viewModel.playbackAudio.observe(this) { refreshChoices() }
        viewModel.message.observe(this) { status.text = it }
        viewModel.backupReady.observe(this) {
            val inspected = viewModel.takeBackupPreview() ?: return@observe
            AlertDialog.Builder(this)
                .setTitle("Восстановить локальные данные?")
                .setMessage("Архив проверен: SQLite v${inspected.preview.schema}, " +
                    "таблиц ${inspected.preview.tables}, записей ${inspected.preview.rows}, " +
                    "параметров ${inspected.preview.settings}.\n\n" +
                    "Импорт разрешён только в ПУСТУЮ базу и не удаляет существующую " +
                    "историю. Все текущие пользовательские настройки будут заменены. " +
                    "MP4/PNG и ключ подписи не импортируются.")
                .setPositiveButton("Восстановить") { _, _ ->
                    viewModel.restoreFullBackup(inspected.uri)
                }
                .setNegativeButton("Отмена", null).show()
        }
        viewModel.library.observe(this) { state ->
            when (state) {
                CaptureLibraryState.Idle -> Unit
                CaptureLibraryState.Loading -> {
                    status.text = "Читаю список собственных MP4 из MediaStore…"
                }
                is CaptureLibraryState.Ready -> showClipList(state.clips)
                is CaptureLibraryState.Error -> status.text = state.message
            }
        }
        viewModel.screenshots.observe(this) { state ->
            when (state) {
                CaptureScreenshotsState.Idle -> Unit
                CaptureScreenshotsState.Loading -> status.text = "Читаю собственные PNG из MediaStore…"
                is CaptureScreenshotsState.Ready -> showScreenshotList(state.shots)
                is CaptureScreenshotsState.Error -> status.text = state.message
            }
        }
        refreshChoices()
        refreshStatus()
    }

    override fun onDestroy() {
        // If the user closes Capture Studio while the Android consent dialog
        // has no callback, do not leave the app locked in CONSENT_PENDING.
        // Never release a STARTING/ACTIVE service that already consumed consent.
        if (isFinishing) {
            CaptureCoordinator.releasePending(CaptureMode.RECORDING)
            CaptureCoordinator.releasePending(CaptureMode.REPLAY)
            CaptureCoordinator.releasePending(CaptureMode.SCREENSHOT)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            viewModel.refreshSelectedGameSettings()
            refreshStatus()
            refreshChoices()
        }
    }

    private fun selectedAudioGame(): String? =
        getSharedPreferences(ToolboxPreferences.NAME, MODE_PRIVATE)
            .getString(ToolboxPreferences.GAME, null)
            ?.takeIf { ApplicationInventory.isPackageName(it) &&
                packageManager.getLaunchIntentForPackage(it) != null }

    private fun refreshChoices() {
        val config = viewModel.config()
        val active = viewModel.captureSettings()
        choices.text = (if (active.usingGameProfile)
                "Профиль игры: ${active.selectedGame}\n"
            else if (active.selectedGame != null)
                "Общие настройки (для ${active.selectedGame} профиль не сохранён)\n"
            else "Общие настройки · игра не выбрана\n") +
            "Сторона до ${config.maxEdge} px · ${config.fps} FPS запрошено\n" +
            "${config.videoBitrate / 1_000_000} Мбит/с · " +
            "${config.maxDurationMs / 60_000} мин максимум\n" +
            (if (viewModel.playbackAudioEnabled())
                "Внутренний звук: ЗАПРОШЕН (AAC)\nИгра: " +
                    (selectedAudioGame() ?: "НЕ ВЫБРАНА — сначала Game Toolbox")
             else "Внутренний звук: ВЫКЛЮЧЕН")
    }

    private fun selectOption(title: String, name: String, values: IntArray, labels: Array<String>) {
        AlertDialog.Builder(this).setTitle(title).setItems(labels) { _, index ->
            viewModel.setOption(name, values[index])
        }.setNegativeButton("Отмена", null).show()
    }

    private fun refreshStatus() {
        status.text = viewModel.status()
    }

    private fun askConsent() {
        val settings = viewModel.captureSettings()
        if (settings.profile.playbackAudio && settings.selectedGame == null) {
            status.text = "Сначала выберите игру для Game Toolbox. Звук других приложений не записывается."
            return
        }
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
        if (!CaptureCoordinator.reserveConsent(CaptureMode.RECORDING)) {
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
            CaptureCoordinator.release(CaptureMode.RECORDING)
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
        if (snapshot.profile.playbackAudio && snapshot.selectedGame == null) {
            status.text = "Для Replay со звуком сначала выберите игру в Game Toolbox."
            return
        }
        if (snapshot.profile.playbackAudio &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            viewModel.rememberReplayAudioPermission(wantedSeconds)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_REPLAY_AUDIO_PERMISSION)
            return
        }
        if (!CaptureCoordinator.reserveConsent(CaptureMode.REPLAY)) {
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
            CaptureCoordinator.release(CaptureMode.REPLAY)
            status.text = "Не удалось запросить Replay: " + it.javaClass.simpleName
        }
    }

    private fun askScreenshotConsent() {
        if (!CaptureCoordinator.reserveConsent(CaptureMode.SCREENSHOT)) {
            status.text = "Другой захват или запрос разрешения уже выполняется."
            return
        }
        runCatching {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_SCREENSHOT)
        }.onFailure {
            CaptureCoordinator.release(CaptureMode.SCREENSHOT)
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
                AlertDialog.Builder(this).setTitle("Восстановить профили?")
                    .setMessage("Сохранённые профили ВСЕХ игр будут заменены содержимым JSON. " +
                        "Общие настройки и ваши записи не изменятся. Некорректный файл отклоняется полностью.")
                    .setPositiveButton("Импортировать") { _, _ -> viewModel.importGameProfiles(uri) }
                    .setNegativeButton("Отмена", null).show()
            }
            return
        }
        if (requestCode == REQUEST_REPLAY) {
            val replay = viewModel.takeReplayConsent()
            if (resultCode != RESULT_OK || data == null || replay == null ||
                !CaptureCoordinator.isPending(CaptureMode.REPLAY)) {
                CaptureCoordinator.release(CaptureMode.REPLAY)
                status.text = "Replay не запущен: согласие Android или параметры не получены."
                return
            }
            val config = replay.config
            if (replay.playbackAudio &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                CaptureCoordinator.release(CaptureMode.REPLAY)
                status.text = "Разрешение RECORD_AUDIO отозвано. Replay не запущен."
                return
            }
            if (!CaptureCoordinator.isPending(CaptureMode.REPLAY)) {
                CaptureCoordinator.release(CaptureMode.REPLAY)
                status.text = "Согласие Replay больше не действительно."
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
                    CaptureCoordinator.release(CaptureMode.REPLAY)
                    status.text = "Android не разрешил запуск Replay: " +
                        it.javaClass.simpleName
                }
            return
        }
        if (requestCode != REQUEST_CAPTURE && requestCode != REQUEST_SCREENSHOT) return
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQUEST_CAPTURE) {
                viewModel.clearCaptureConsent()
                CaptureCoordinator.release(CaptureMode.RECORDING)
            } else CaptureCoordinator.release(CaptureMode.SCREENSHOT)
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
                    CaptureCoordinator.release(CaptureMode.SCREENSHOT)
                    status.text = "Сервис снимка отклонён: " + it.javaClass.simpleName
                }
            return
        }
        val snapshot = viewModel.takeCaptureConsent()
        if (snapshot == null) {
            CaptureCoordinator.release(CaptureMode.RECORDING)
            status.text = "Настройки запроса утрачены. Повторите запрос записи экрана."
            return
        }
        val playbackRequested = snapshot.profile.playbackAudio
        val audioGame = if (playbackRequested) snapshot.selectedGame else null
        if (playbackRequested && audioGame == null) {
            CaptureCoordinator.release(CaptureMode.RECORDING)
            status.text = "Выбранная игра недоступна. Повторите выбор в Game Toolbox."
            return
        }
        if (playbackRequested &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CaptureCoordinator.release(CaptureMode.RECORDING)
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
                CaptureCoordinator.release(CaptureMode.RECORDING)
                status.text = "Android не разрешил запустить сервис: " + it.javaClass.simpleName
            }
    }

    private fun openSelectedGame() {
        val game = getSharedPreferences(ToolboxPreferences.NAME, MODE_PRIVATE)
            .getString(ToolboxPreferences.GAME, null)
        val appIntent = game?.let { packageManager.getLaunchIntentForPackage(it) }
        if (appIntent == null) {
            status.text = "В Game Toolbox не выбрано доступное приложение."
            return
        }
        runCatching { startActivity(appIntent) }
            .onFailure { status.text = "Игра не открыта: " + it.javaClass.simpleName }
    }

    private fun browseClips() {
        viewModel.refresh()
    }

    /** Inline screen state, not a popup re-triggered by Activity recreation. */
    private fun showClipList(clips: List<CaptureClip>) {
        clipsContainer.removeAllViews()
        status.text = if (clips.isEmpty()) "На этой странице нет готовых MP4."
            else "MP4 · страница ${viewModel.clipsPageNumber()} · файлов: ${clips.size}"
        if (viewModel.clipsPageNumber() > 1) {
            clipsContainer.addView(Button(this).apply {
                text = "ПРЕДЫДУЩАЯ СТРАНИЦА MP4"
                LegionUiSupport.styleAction(this)
                setOnClickListener { viewModel.previousClipPage() }
            })
        }
        for (clip in clips) {
            val size = String.format(Locale.US, "%.1f", clip.bytes / 1048576.0)
            val length = if (clip.durationMs > 0) " · ${clip.durationMs / 1000} с" else ""
            clipsContainer.addView(Button(this).apply {
                text = "${clip.name}\n${size} МиБ${length}"
                isAllCaps = false
                minHeight = 48.dp
                setOnClickListener { clipActions(clip) }
            })
        }
        if (clips.size == CaptureClipLibrary.PAGE_SIZE) {
            clipsContainer.addView(Button(this).apply {
                text = "СЛЕДУЮЩАЯ СТРАНИЦА MP4"
                LegionUiSupport.styleAction(this)
                setOnClickListener { viewModel.nextClipPage() }
            })
        }
    }

    private fun clipActions(clip: CaptureClip) {
        AlertDialog.Builder(this).setTitle(clip.name)
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
                    0 -> viewClip(clip.uri)
                    1 -> shareClip(clip.uri)
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

    private fun chooseExcerptRange(clip: CaptureClip) {
        val begin = numericField("От, секунд · например 10")
        val end = numericField("До, секунд · например 25")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 4.dp)
            addView(begin)
            addView(end)
        }
        AlertDialog.Builder(this)
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

    private fun chooseVideoFrame(clip: CaptureClip) {
        AlertDialog.Builder(this).setTitle("Экспорт PNG-кадра")
            .setItems(arrayOf("Первый кадр", "Кадр из середины", "Указать секунду")) { _, choice ->
                when (choice) {
                    0 -> viewModel.saveClipFrame(clip, 0L)
                    1 -> viewModel.saveClipFrame(clip, null)
                    2 -> {
                        val at = numericField("Секунда в видео")
                        AlertDialog.Builder(this)
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

    private fun viewClip(uri: Uri) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }.onFailure { status.text = "Нет приложения для просмотра: " + it.javaClass.simpleName }
    }

    private fun shareClip(uri: Uri) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Поделиться клипом"))
        }.onFailure { status.text = "Не удалось открыть отправку: " + it.javaClass.simpleName }
    }

    private fun showScreenshotList(shots: List<CaptureScreenshot>) {
        shotsContainer.removeAllViews()
        status.text = if (shots.isEmpty()) "На этой странице нет PNG."
            else "PNG · страница ${viewModel.screenshotsPageNumber()} · файлов: ${shots.size}"
        if (viewModel.screenshotsPageNumber() > 1) {
            shotsContainer.addView(Button(this).apply {
                text = "ПРЕДЫДУЩАЯ СТРАНИЦА PNG"
                LegionUiSupport.styleAction(this)
                setOnClickListener { viewModel.previousScreenshotPage() }
            })
        }
        for (shot in shots) {
            val size = String.format(Locale.US, "%.1f", shot.bytes / 1048576.0)
            shotsContainer.addView(Button(this).apply {
                text = "${shot.name}\n${size} МиБ"
                isAllCaps = false
                minHeight = 48.dp
                setOnClickListener { screenshotActions(shot) }
            })
        }
        if (shots.size == CaptureScreenshotLibrary.PAGE_SIZE) {
            shotsContainer.addView(Button(this).apply {
                text = "СЛЕДУЮЩАЯ СТРАНИЦА PNG"
                LegionUiSupport.styleAction(this)
                setOnClickListener { viewModel.nextScreenshotPage() }
            })
        }
    }

    private fun screenshotActions(shot: CaptureScreenshot) {
        AlertDialog.Builder(this).setTitle(shot.name)
            .setItems(arrayOf("Просмотреть", "Поделиться", "Удалить PNG")) { _, which ->
                when (which) {
                    0 -> runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(shot.uri, "image/png")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }.onFailure {
                        status.text = "Просмотр PNG недоступен: " + it.javaClass.simpleName
                    }
                    1 -> runCatching {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, shot.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Поделиться снимком"))
                    }.onFailure {
                        status.text = "Отправка PNG недоступна: " + it.javaClass.simpleName
                    }
                    2 -> AlertDialog.Builder(this)
                        .setTitle("Удалить выбранный снимок?")
                        .setMessage("Безвозвратно удалится только ${shot.name}.")
                        .setPositiveButton("Удалить") { _, _ ->
                            viewModel.deleteConfirmedScreenshot(shot)
                        }
                        .setNegativeButton("Отмена", null).show()
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun confirmDelete(clip: CaptureClip) {
        AlertDialog.Builder(this)
            .setTitle("Безвозвратно удалить MP4?")
            .setMessage("Будет удалён только выбранный файл ${clip.name}. " +
                "История сессий, диагностика и другие клипы сохранятся.")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteConfirmed(clip)
            }.setNegativeButton("Отмена", null).show()
    }

}
