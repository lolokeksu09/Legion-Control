package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager

/**
 * Video-only capture. Exactly one MediaProjection and one virtual display per system consent.
 * MediaStore rows remain pending until MediaRecorder.stop() succeeds. Never starts at boot.
 */
class CaptureRecordingService : Service() {
    companion object {
        const val ACTION_START = "dev.lolokeksu.legioncontrol.capture.START"
        const val ACTION_STOP = "dev.lolokeksu.legioncontrol.capture.STOP"
        const val EXTRA_CONSENT = "capture_consent"
        const val EXTRA_RESULT = "capture_result"
        const val EXTRA_EDGE = "capture_edge"
        const val EXTRA_FPS = "capture_fps"
        const val EXTRA_MBIT = "capture_mbit"
        const val EXTRA_MINUTES = "capture_minutes"
        const val PREFS = "legion-capture-studio"
        const val LAST_RESULT = "last_result"
        private const val CHANNEL = "legion-capture-recording"
        private const val NOTICE = 2501
        @Volatile var recording: Boolean = false
            private set

        fun stop(context: Context) {
            if (recording) context.startService(
                Intent(context, CaptureRecordingService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var output: ParcelFileDescriptor? = null
    private var pendingUri: Uri? = null
    private var recorderStarted = false
    private var startedAt = 0L
    private var stopping = false
    private val autoStop = Runnable { finish("Достигнут предел длительности записи") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish("Остановлено пользователем")
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || recording || projection != null || stopping) {
            if (!recording) stopSelf(startId)
            return START_NOT_STICKY
        }
        val consent = intent.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        if (intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) != Activity.RESULT_OK ||
            consent == null) {
            writeStatus("Запись не запущена: нет разрешения Android на захват экрана")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            // Android 14+: user consent must precede foreground mediaProjection service.
            // Foreground service must start BEFORE getMediaProjection().
            promoteToForeground()
            val config = CaptureConfig.fromOptions(
                intent.getIntExtra(EXTRA_EDGE, 1280),
                intent.getIntExtra(EXTRA_FPS, 30),
                intent.getIntExtra(EXTRA_MBIT, 4),
                intent.getIntExtra(EXTRA_MINUTES, 1)
            )
            beginRecording(consent, config)
        } catch (e: Exception) {
            finish("Не удалось начать запись: " + e.javaClass.simpleName)
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Capture Studio · запись экрана",
                NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(this, 2501,
            Intent(this, CaptureStudioActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2502,
            Intent(this, CaptureRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Legion Capture Studio")
            .setContentText("Захват экрана запущен вручную · нажмите «Остановить»")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stop)
            .build()
        startForeground(NOTICE, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun beginRecording(consent: Intent, config: CaptureConfig) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // The one-time result Intent is not stored, copied to preferences or reused.
        val capture = requireNotNull(manager.getMediaProjection(Activity.RESULT_OK, consent)) {
            "Android denied MediaProjection"
        }
        projection = capture
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { finish("Системный доступ к захвату остановлен") }
            }
        }
        projectionCallback = callback
        capture.registerCallback(callback, handler)

        val windows = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = windows.maximumWindowMetrics.bounds
        val (width, height) = CaptureConfig.scaledSize(bounds.width(), bounds.height(), config.maxEdge)
        val dpi = resources.displayMetrics.densityDpi.coerceAtLeast(1)

        val uri = CaptureClipLibrary.createPending(this, CaptureConfig.fileName(System.currentTimeMillis()))
        pendingUri = uri
        val file = requireNotNull(contentResolver.openFileDescriptor(uri, "w")) {
            "MediaStore не открыл файл"
        }
        output = file

        val mediaRecorder = MediaRecorder(this)
        // Assign before prepare(): failed codec initialization must still release native resources.
        recorder = mediaRecorder
        mediaRecorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(config.fps)
            setVideoEncodingBitRate(config.videoBitrate)
            setOutputFile(file.fileDescriptor)
            setOnErrorListener { _, _, _ ->
                handler.post { finish("MediaRecorder сообщил об ошибке кодирования") }
            }
            prepare()
        }
        // A single createVirtualDisplay call per MediaProjection object (Android 14+).
        virtualDisplay = capture.createVirtualDisplay(
            "LegionCaptureStudio", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface, null, handler
        )
        mediaRecorder.start()
        recorderStarted = true
        recording = true
        startedAt = SystemClock.elapsedRealtime()
        writeStatus("Запись началась. Видео без звука; остановить можно из уведомления.")
        handler.postDelayed(autoStop, config.maxDurationMs)
    }

    private fun writeStatus(text: String) {
        preferences.edit().putString(LAST_RESULT, text).apply()
    }

    private fun finish(reason: String) {
        if (stopping) return
        stopping = true
        handler.removeCallbacks(autoStop)
        recording = false

        val elapsed = if (recorderStarted) SystemClock.elapsedRealtime() - startedAt else 0L
        var saved = false
        if (recorderStarted && elapsed >= 1000) {
            saved = runCatching { recorder?.stop(); true }.getOrDefault(false)
        }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        recorderStarted = false
        runCatching { output?.close() }
        output = null
        val previous = projection
        projection = null
        val callback = projectionCallback
        projectionCallback = null
        if (callback != null) runCatching { previous?.unregisterCallback(callback) }
        runCatching { previous?.stop() }

        val uri = pendingUri
        pendingUri = null
        if (uri != null) {
            if (saved) {
                saved = runCatching { CaptureClipLibrary.publish(this, uri); true }
                    .getOrDefault(false)
            }
            if (!saved) runCatching { CaptureClipLibrary.delete(this, uri) }
        }
        writeStatus(
            if (saved) "Клип сохранён в Movies/LegionControl/ · " + reason
            else if (uri != null) "Видео не сохранено (короткая запись или сбой кодека) · " + reason
            else reason
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!stopping) finish("Сервис захвата завершён")
        super.onDestroy()
    }
}
