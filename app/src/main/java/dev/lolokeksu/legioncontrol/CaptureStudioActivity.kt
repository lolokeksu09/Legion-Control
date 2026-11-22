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
    }

    private val viewModel by lazy { ViewModelProvider(this)[CaptureStudioViewModel::class.java] }
    private lateinit var clipsContainer: LinearLayout
    private lateinit var shotsContainer: LinearLayout
    private lateinit var status: TextView
    private lateinit var choices: TextView
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                isAllCaps = false
                minHeight = 51.dp
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

        heading("Мои клипы")
        note("Готовые MP4 — в системной папке Movies/LegionControl/. " +
            "Здесь показываются только клипы, сохранённые самим Legion Control. " +
            "Никакие чужие видео приложение не читает.")
        action("ОТКРЫТЬ БИБЛИОТЕКУ КЛИПОВ") { browseClips() }
        clipsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(clipsContainer)
        heading("Мои снимки")
        note("PNG сохраняются в Pictures/LegionControl/ · только снимки этого приложения.")
        action("ОТКРЫТЬ БИБЛИОТЕКУ СНИМКОВ") { viewModel.refreshScreenshots() }
        shotsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(shotsContainer)
        note("Пауза и продолжение сохраняют один MP4. Время паузы не расходует лимит " +
            "активной записи. Через 5 минут непрерывной паузы запись останавливается. " +
            "Если кодек отклонит команду, захват будет завершён.")
        note("Каждый запуск требует нового согласия Android. " +
            "После подтверждения перейдите в игру — остановить запись можно из постоянного " +
            "уведомления или этого экрана. Ограничение качества/частоты зависит от кодека и прошивки.")
        setContentView(ScrollView(this).apply { addView(root) })
        viewModel.settings.observe(this) { refreshChoices() }
        viewModel.playbackAudio.observe(this) { refreshChoices() }
        viewModel.message.observe(this) { status.text = it }
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

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
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
        choices.text = "Сторона до ${config.maxEdge} px · ${config.fps} FPS запрошено\\n" +
            "${config.videoBitrate / 1_000_000} Мбит/с · " +
            "${config.maxDurationMs / 60_000} мин максимум\\n" +
            (if (viewModel.playbackAudioEnabled())
                "Внутренний звук: ЗАПРОШЕН (AAC)\\nИгра: " +
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
        if (viewModel.playbackAudioEnabled() && selectedAudioGame() == null) {
            status.text = "Сначала выберите игру для Game Toolbox. Звук других приложений не записывается."
            return
        }
        if (viewModel.playbackAudioEnabled() &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PLAYBACK_PERMISSION)
            status.text = "Для захвата разрешённого игрового звука Android требует RECORD_AUDIO. " +
                "Микрофон не используется."
            return
        }
        if (CaptureRecordingService.recording || CaptureScreenshotService.active) {
            status.text = "Выполняется другой захват. Сначала завершите его."
            return
        }
        runCatching {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // Fresh Intent and user prompt for EVERY capture; never persist or reuse token.
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        }.onFailure {
            status.text = "Системный запрос записи недоступен: " + it.javaClass.simpleName
        }
    }

    private fun askScreenshotConsent() {
        if (CaptureRecordingService.recording || CaptureScreenshotService.active) {
            status.text = "Сначала завершите текущий захват."
            return
        }
        runCatching {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_SCREENSHOT)
        }.onFailure {
            status.text = "Android не разрешил запрос снимка: " + it.javaClass.simpleName
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PLAYBACK_PERMISSION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            askConsent()
        } else {
            status.text = "Нет разрешения RECORD_AUDIO. Видео без звука доступно: выключите режим звука."
        }
    }

    @Deprecated("Legacy Activity result API works without introducing AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE && requestCode != REQUEST_SCREENSHOT) return
        if (resultCode != RESULT_OK || data == null) {
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
                    status.text = "Сервис снимка отклонён: " + it.javaClass.simpleName
                }
            return
        }
        val playbackRequested = viewModel.playbackAudioEnabled()
        val audioGame = if (playbackRequested) selectedAudioGame() else null
        if (playbackRequested && audioGame == null) {
            status.text = "Выбранная игра недоступна. Повторите выбор в Game Toolbox."
            return
        }
        if (playbackRequested &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Разрешение RECORD_AUDIO отозвано. Запись со звуком не начата."
            return
        }
        val config = viewModel.config()
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
        status.text = if (clips.isEmpty()) "В папке пока нет готовых MP4."
            else "Найдено клипов: ${clips.size}"
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
    }

    private fun clipActions(clip: CaptureClip) {
        AlertDialog.Builder(this).setTitle(clip.name)
            .setItems(arrayOf("Просмотреть", "Поделиться", "Удалить файл")) { _, which ->
                when (which) {
                    0 -> viewClip(clip.uri)
                    1 -> shareClip(clip.uri)
                    2 -> confirmDelete(clip)
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
        status.text = if (shots.isEmpty()) "Сохранённых PNG пока нет."
            else "Найдено снимков: ${shots.size}"
        for (shot in shots) {
            val size = String.format(Locale.US, "%.1f", shot.bytes / 1048576.0)
            shotsContainer.addView(Button(this).apply {
                text = "${shot.name}\n${size} МиБ"
                isAllCaps = false
                minHeight = 48.dp
                setOnClickListener { screenshotActions(shot) }
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
