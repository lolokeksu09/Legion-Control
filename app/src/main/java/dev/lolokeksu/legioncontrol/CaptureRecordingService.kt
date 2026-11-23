package dev.lolokeksu.legioncontrol

import android.Manifest
import android.content.pm.PackageManager
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
        const val ACTION_PAUSE = "dev.lolokeksu.legioncontrol.capture.PAUSE"
        const val ACTION_RESUME = "dev.lolokeksu.legioncontrol.capture.RESUME"
        const val EXTRA_CONSENT = "capture_consent"
        const val EXTRA_RESULT = "capture_result"
        const val EXTRA_EDGE = "capture_edge"
        const val EXTRA_FPS = "capture_fps"
        const val EXTRA_MBIT = "capture_mbit"
        const val EXTRA_MINUTES = "capture_minutes"
        const val EXTRA_PLAYBACK_AUDIO = "capture_playback_audio"
        const val EXTRA_AUDIO_GAME = "capture_audio_game"
        const val PREFS = "legion-capture-studio"
        const val LAST_RESULT = "last_result"
        private const val CHANNEL = "legion-capture-recording"
        private const val NOTICE = 2501
        @Volatile var recording: Boolean = false
            private set
        @Volatile var paused: Boolean = false
            private set

        fun stop(context: Context) {
            if (recording) context.startService(
                Intent(context, CaptureRecordingService::class.java).setAction(ACTION_STOP)
            )
        }

        fun pause(context: Context) {
            if (recording && !paused) context.startService(
                Intent(context, CaptureRecordingService::class.java).setAction(ACTION_PAUSE)
            )
        }

        fun resume(context: Context) {
            if (recording && paused) context.startService(
                Intent(context, CaptureRecordingService::class.java).setAction(ACTION_RESUME)
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
    private var playbackAudio: PlaybackAudioCapture? = null
    private var playbackAudioRequested = false
    private var selectedGameUid: Int? = null
    private var playbackAudioProblem: String? = null
    private var videoStartedAtMs = 0L
    private var recorderStarted = false
    private var captureClock: CaptureRecordingClock? = null
    private var stopping = false
    private val autoStop = Runnable { finish("Достигнут предел длительности записи") }
    private val pauseTimeout = Runnable {
        finish("Пауза превысила 5 минут: запись завершена для освобождения MediaProjection")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish("Остановлено пользователем")
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE || intent?.action == ACTION_RESUME) {
            if (recording && !stopping) {
                changePausedState(intent.action == ACTION_PAUSE)
            } else if (!recording && !stopping) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || recording || projection != null || stopping) {
            if (!recording && !stopping) stopSelf(startId)
            return START_NOT_STICKY
        }
        val consent = intent.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        if (intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) != Activity.RESULT_OK ||
            consent == null || CaptureScreenshotService.active || ReplayCaptureService.active) {
            CaptureCoordinator.releasePending(CaptureMode.RECORDING)
            writeStatus("Запись не запущена: нет согласия Android или уже выполняется скриншот")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        playbackAudioRequested = intent.getBooleanExtra(EXTRA_PLAYBACK_AUDIO, false)
        if (playbackAudioRequested &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CaptureCoordinator.releasePending(CaptureMode.RECORDING)
            writeStatus("Нет разрешения на захват внутреннего звука. Выберите режим без звука либо выдайте разрешение.")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (playbackAudioRequested) {
            val pkg = intent.getStringExtra(EXTRA_AUDIO_GAME)
            val uid = if (pkg != null && ApplicationInventory.isPackageName(pkg) &&
                packageManager.getLaunchIntentForPackage(pkg) != null) {
                runCatching {
                    packageManager.getApplicationInfo(
                        pkg, PackageManager.ApplicationInfoFlags.of(0L)
                    ).uid
                }.getOrNull()
            } else null
            if (uid == null || uid <= 0) {
                CaptureCoordinator.releasePending(CaptureMode.RECORDING)
                writeStatus("Выбранная игра не найдена. Сначала выберите её в Game Toolbox.")
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            selectedGameUid = uid
        }
        if (!CaptureCoordinator.begin(CaptureMode.RECORDING)) {
            writeStatus("Запись отклонена: другое MediaProjection-согласие уже используется.")
            stopSelfResult(startId)
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
            CaptureCoordinator.markActive(CaptureMode.RECORDING)
        } catch (e: Exception) {
            finish("Не удалось начать запись: " + e.javaClass.simpleName)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 2501,
            Intent(this, CaptureStudioActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2502,
            Intent(this, CaptureRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val toggle = PendingIntent.getService(this, if (paused) 2504 else 2503,
            Intent(this, CaptureRecordingService::class.java).setAction(
                if (paused) ACTION_RESUME else ACTION_PAUSE
            ), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(if (paused) "Legion Capture Studio · пауза"
                else "Legion Capture Studio · запись")
            .setContentText(if (paused) "Возобновите или остановите запись · автостоп паузы 5 мин"
                else if (playbackAudio != null) "Видео + разрешённый звук игры"
                else "Захват экрана включён вручную")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(if (paused) android.R.drawable.ic_media_play
                else android.R.drawable.ic_media_pause,
                if (paused) "Продолжить" else "Пауза", toggle)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stop)
            .build()
    }

    private fun promoteToForeground() {
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Capture Studio · запись экрана",
                NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTICE, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun updateNotification() {
        // Denied POST_NOTIFICATIONS must never crash an already running FGS.
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTICE, buildNotification())
        }
    }

    /** All service commands arrive on Android's main thread; codec transitions are serialized. */
    private fun changePausedState(wantPause: Boolean) {
        if (!recording || stopping || wantPause == paused) return
        val currentRecorder = recorder ?: return
        val clock = captureClock ?: return
        val now = SystemClock.elapsedRealtime()
        if (clock.remainingMs(now) == 0L) {
            finish("Достигнут предел длительности записи")
            return
        }
        try {
            if (wantPause) {
                currentRecorder.pause()
                runCatching { playbackAudio?.pause() }.onFailure {
                    playbackAudioProblem = "Пауза звука не поддержана: " + it.javaClass.simpleName
                }
                check(clock.pause(SystemClock.elapsedRealtime()))
                paused = true
                handler.removeCallbacks(autoStop)
                handler.postDelayed(pauseTimeout, CaptureRecordingClock.MAX_PAUSE_MS)
                writeStatus("Видеозапись на паузе. Остаток активного времени сохранён. " +
                    "Возобновите в течение 5 минут или остановите запись.")
            } else {
                currentRecorder.resume()
                runCatching { playbackAudio?.resume() }.onFailure {
                    playbackAudioProblem = "Возобновление звука не поддержано: " + it.javaClass.simpleName
                }
                val resumedAt = SystemClock.elapsedRealtime()
                check(clock.resume(resumedAt))
                paused = false
                handler.removeCallbacks(pauseTimeout)
                val remaining = clock.remainingMs(resumedAt)
                if (remaining == 0L) {
                    finish("Достигнут предел длительности записи")
                    return
                }
                handler.postDelayed(autoStop, remaining)
                writeStatus("Видеозапись продолжена. Время паузы не включено в лимит.")
            }
            updateNotification()
        } catch (e: Exception) {
            // Codec state is unknown after a failed pause/resume. Stop the entire
            // projection and let normal MP4 publish/delete handling decide validity.
            finish("Не удалось изменить состояние записи: " + e.javaClass.simpleName)
        }
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
        videoStartedAtMs = SystemClock.elapsedRealtime()
        captureClock = CaptureRecordingClock(config.maxDurationMs, videoStartedAtMs)
        paused = false
        if (playbackAudioRequested) {
            runCatching {
                PlaybackAudioCapture(this, capture, requireNotNull(selectedGameUid)).also {
                    it.start(videoStartedAtMs)
                    playbackAudio = it
                }
            }.onFailure {
                playbackAudioProblem = "Внутренний звук не удалось запустить: " +
                    it.javaClass.simpleName
            }
        }
        writeStatus(if (playbackAudio != null)
            "Видео и разрешённый внутренний звук записываются; аудио зависит от политики игры."
            else if (playbackAudioRequested)
            "Видео пишется БЕЗ ЗВУКА: " + (playbackAudioProblem ?: "Аудиозахват недоступен")
            else "Запись началась. Видео без звука; пауза и остановка доступны в уведомлении.")
        updateNotification()
        handler.postDelayed(autoStop, config.maxDurationMs)
    }

    private fun writeStatus(text: String) {
        preferences.edit().putString(LAST_RESULT, text).apply()
    }

    private fun finish(reason: String) {
        if (stopping) return
        stopping = true
        CaptureCoordinator.markStopping(CaptureMode.RECORDING)
        handler.removeCallbacks(autoStop)
        handler.removeCallbacks(pauseTimeout)
        recording = false
        paused = false

        val elapsed = if (recorderStarted)
            captureClock?.activeElapsedMs(SystemClock.elapsedRealtime()) ?: 0L else 0L
        var videoValid = false
        if (recorderStarted && elapsed >= 1000) {
            videoValid = runCatching { recorder?.stop(); true }.getOrDefault(false)
        }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        recorderStarted = false
        captureClock = null
        runCatching { output?.close() }
        output = null
        val previous = projection
        projection = null
        val callback = projectionCallback
        projectionCallback = null
        if (callback != null) runCatching { previous?.unregisterCallback(callback) }
        // Stop the AudioRecord source before revoking MediaProjection so an in-flight
        // read cannot spuriously fail and discard an otherwise valid AAC track.
        runCatching { playbackAudio?.pause() }.onFailure {
            playbackAudioProblem = "Аудиозахват остановлен с ошибкой: " + it.javaClass.simpleName
        }
        runCatching { previous?.stop() }

        val uri = pendingUri
        pendingUri = null
        val audio = playbackAudio
        playbackAudio = null
        val wasAudioRequested = playbackAudioRequested
        val audioFailure = playbackAudioProblem

        if (audio == null) {
            publishVideoOrDelete(uri, videoValid,
                if (wasAudioRequested) "Без внутреннего звука: " +
                    (audioFailure ?: "Android или игра отказали") else "Видео без звука",
                reason)
            return
        }

        // Finalize AAC and copy both codec tracks off the Android main thread.
        // Keep this foreground service alive until owned pending MediaStore rows resolve.
        Thread({
            val result = runCatching { audio.stopAndTakeResult() }.getOrNull()
            val aac = result?.file
            var combined = false
            if (videoValid && uri != null && aac != null && audioFailure == null) {
                combined = runCatching {
                    CaptureMp4Muxer.combine(this, uri, aac, result?.offsetUs ?: 0L)
                    true
                }.getOrDefault(false)
            }
            runCatching { aac?.delete() }
            handler.post {
                if (combined && uri != null) {
                    // The combined result is already published, so the intermediate MP4 is removed.
                    runCatching { CaptureClipLibrary.delete(this, uri) }
                    writeStatus("MP4 со звуком сохранён в Movies/LegionControl/ · " + reason +
                        " · отдельные игры могут блокировать звук или давать тишину")
                    stopCaptureService()
                } else {
                    publishVideoOrDelete(uri, videoValid, "Видео без звука: " +
                        (audioFailure ?: result?.reason ?: "Не удалось объединить AAC и MP4"), reason)
                }
            }
        }, "legion-audio-mp4-finalizer").start()
    }

    private fun publishVideoOrDelete(uri: Uri?, isValid: Boolean,
                                     soundNote: String, reason: String) {
        var published = false
        if (uri != null && isValid) {
            published = runCatching { CaptureClipLibrary.publish(this, uri); true }
                .getOrDefault(false)
        }
        if (uri != null && !published) runCatching { CaptureClipLibrary.delete(this, uri) }
        writeStatus(if (published)
            "MP4 сохранён в Movies/LegionControl/ · " + soundNote + " · " + reason
        else if (uri != null)
            "Видео не сохранено (короткая запись или сбой кодека) · " + reason
        else reason)
        stopCaptureService()
    }

    private fun stopCaptureService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        CaptureCoordinator.release(CaptureMode.RECORDING)
        stopSelf()
    }

    override fun onDestroy() {
        if (!stopping) finish("Сервис захвата завершён")
        super.onDestroy()
    }
}
