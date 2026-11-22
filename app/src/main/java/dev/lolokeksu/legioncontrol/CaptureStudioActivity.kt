package dev.lolokeksu.legioncontrol

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
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
 * Video-only user-consented screen capture and app-owned clip library.
 * No background scan, recording without system consent, microphone or cloud upload.
 */
class CaptureStudioActivity : ComponentActivity() {
    companion object { private const val REQUEST_CAPTURE = 25001 }

    private val viewModel by lazy { ViewModelProvider(this)[CaptureStudioViewModel::class.java] }
    private lateinit var clipsContainer: LinearLayout
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
            "Только видео: без микрофона и игрового звука. Вы сами решаете, что попадёт в запись. " +
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
        action("Автостоп · 1 / 5 / 15 минут") { selectOption(
            "Максимальная длительность",
            "minutes", intArrayOf(1, 5, 15), arrayOf("1 минута", "5 минут", "15 минут")
        ) }

        heading("Запись")
        action("НАЧАТЬ ЗАПИСЬ ЭКРАНА") { askConsent() }
        action("ОСТАНОВИТЬ ЗАПИСЬ") {
            if (CaptureRecordingService.recording) {
                CaptureRecordingService.stop(this)
                status.text = "Остановка запрошена; результат появится после завершения кодирования."
            } else status.text = "Активной записи нет."
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
        note("Каждый запуск требует нового согласия Android. " +
            "После подтверждения перейдите в игру — остановить запись можно из постоянного " +
            "уведомления или этого экрана. Ограничение качества/частоты зависит от кодека и прошивки.")
        setContentView(ScrollView(this).apply { addView(root) })
        viewModel.settings.observe(this) { refreshChoices() }
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
        refreshChoices()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun refreshChoices() {
        val config = viewModel.config()
        choices.text = "Сторона до ${config.maxEdge} px · ${config.fps} FPS запрошено\n" +
            "${config.videoBitrate / 1_000_000} Мбит/с · " +
            "${config.maxDurationMs / 60_000} мин максимум"
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
        if (CaptureRecordingService.recording) {
            status.text = "Запись уже активна. Сначала остановите текущую."
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

    @Deprecated("Legacy Activity result API works without introducing AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "Вы отказали в записи. MediaProjection и файлы не создавались."
            return
        }
        val config = viewModel.config()
        val intent = Intent(this, CaptureRecordingService::class.java).apply {
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
