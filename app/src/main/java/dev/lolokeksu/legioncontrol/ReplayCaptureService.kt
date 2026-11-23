package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.app.ActivityManager
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
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-initiated, video-only rolling replay. One fresh MediaProjection and exactly
 * one VirtualDisplay per session. Compressed H.264 frames are retained in a bounded
 * memory window; each explicit SAVE exports a separate owned MP4 without stopping capture.
 *
 * No recording at boot, no hidden APIs, no microphone, no background game detection.
 */
class ReplayCaptureService : Service() {
    companion object {
        const val ACTION_START = "dev.lolokeksu.legioncontrol.replay.START"
        const val ACTION_SAVE = "dev.lolokeksu.legioncontrol.replay.SAVE"
        const val ACTION_STOP = "dev.lolokeksu.legioncontrol.replay.STOP"
        const val EXTRA_CONSENT = "replay_consent"
        const val EXTRA_RESULT = "replay_result"
        const val EXTRA_SECONDS = "replay_seconds"
        const val EXTRA_EDGE = "replay_edge"
        const val EXTRA_FPS = "replay_fps"
        const val EXTRA_MBIT = "replay_mbit"
        const val EXTRA_PLAYBACK_AUDIO = "replay_playback_audio"
        const val EXTRA_AUDIO_GAME = "replay_audio_game"
        private const val CHANNEL = "legion-replay-capture"
        private const val NOTICE = 3901
        private const val REPLAY_STATUS = "replay_status"
        @Volatile var active: Boolean = false
            private set
        @Volatile var seconds: Int = 60
            private set

        fun save(context: Context): Boolean {
            if (!active) return false
            context.startService(Intent(context, ReplayCaptureService::class.java)
                .setAction(ACTION_SAVE))
            return true
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ReplayCaptureService::class.java)
                .setAction(ACTION_STOP))
        }

        fun status(context: Context): String =
            context.getSharedPreferences(CaptureRecordingService.PREFS, MODE_PRIVATE)
                .getString(REPLAY_STATUS, "Replay не запускался.") ?: "Replay не запускался."
    }

    private val handler = Handler(Looper.getMainLooper())
    private val safetyStop = Runnable { requestStop("Replay завершён по часовому ограничению захвата.") }
    private var capture: MediaProjection? = null
    private var captureCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var codecThread: Thread? = null
    private var playbackAudio: ReplayAudioCapture? = null
    private var selectedGameUid: Int? = null
    private var audioRequested = false
    private var audioProblem: String? = null
    private val running = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val saveRequested = AtomicBoolean(false)
    @Volatile private var saveRequestedAtMs = 0L
    private val exporting = AtomicBoolean(false)
    @Volatile private var drainComplete = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SAVE -> {
                if (active && !stopping.get()) {
                    if (exporting.get() || saveRequested.get()) {
                        writeStatus("Replay: сохранение уже запрошено или выполняется.")
                    } else {
                        saveRequestedAtMs = SystemClock.elapsedRealtime()
                        saveRequested.set(true)
                        writeStatus("Replay: сохраняю последний доступный фрагмент…")
                    }
                } else writeStatus("Replay не активен. Сначала начните захват с разрешением Android.")
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                requestStop("Replay остановлен пользователем.")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (active || running.get() || stopping.get() ||
                    CaptureRecordingService.recording || CaptureScreenshotService.active) {
                    writeStatus("Другой захват уже выполняется. Остановите его перед Replay.")
                    if (!active && !running.get()) stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                val token = intent.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
                val approved = intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) ==
                    Activity.RESULT_OK
                val wanted = intent.getIntExtra(EXTRA_SECONDS, 60)
                if (!approved || token == null || wanted !in setOf(30, 60, 120)) {
                    CaptureCoordinator.releasePending(CaptureMode.REPLAY)
                    writeStatus("Replay не запущен: разрешение Android или параметры неверны.")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                val config = CaptureConfig.fromOptions(
                    intent.getIntExtra(EXTRA_EDGE, 1280),
                    intent.getIntExtra(EXTRA_FPS, 30),
                    intent.getIntExtra(EXTRA_MBIT, 4), 1
                )
                audioRequested = intent.getBooleanExtra(EXTRA_PLAYBACK_AUDIO, false)
                if (audioRequested) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED) {
                        CaptureCoordinator.releasePending(CaptureMode.REPLAY)
                        writeStatus("Replay со звуком не запущен: RECORD_AUDIO запрещён.")
                        stopSelfResult(startId)
                        return START_NOT_STICKY
                    }
                    val game = intent.getStringExtra(EXTRA_AUDIO_GAME)
                    val uid = if (game != null && GameCaptureProfilePolicy.validPackage(game) &&
                        packageManager.getLaunchIntentForPackage(game) != null) {
                        runCatching {
                            packageManager.getApplicationInfo(game,
                                PackageManager.ApplicationInfoFlags.of(0L)).uid
                        }.getOrNull()
                    } else null
                    if (uid == null || uid <= 0) {
                        CaptureCoordinator.releasePending(CaptureMode.REPLAY)
                        writeStatus("Replay: игра для захвата звука не найдена.")
                        stopSelfResult(startId)
                        return START_NOT_STICKY
                    }
                    selectedGameUid = uid
                }
                if (!CaptureCoordinator.begin(CaptureMode.REPLAY)) {
                    writeStatus("Replay отклонён: другой захват уже выполняется.")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                try {
                    seconds = wanted
                    promoteToForeground()
                    begin(token, config, wanted)
                    CaptureCoordinator.markActive(CaptureMode.REPLAY)
                } catch (error: Exception) {
                    writeStatus("Replay не удалось запустить: " + error.javaClass.simpleName)
                    requestStop("Replay остановлен после ошибки запуска.")
                }
                return START_NOT_STICKY
            }
            else -> {
                if (!active) stopSelfResult(startId)
                return START_NOT_STICKY
            }
        }
    }

    private fun notice(): Notification {
        val open = PendingIntent.getActivity(this, NOTICE,
            Intent(this, CaptureStudioActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val save = PendingIntent.getService(this, NOTICE + 1,
            Intent(this, ReplayCaptureService::class.java).setAction(ACTION_SAVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, NOTICE + 2,
            Intent(this, ReplayCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Legion Replay · буфер " + seconds + " с")
            .setContentText(if (playbackAudio != null)
                "Видео + разрешённый звук игры. Сохранение — по нажатию."
                else "Запись видео. Сохранение — только по нажатию.")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_save, "Сохранить", save)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stop)
            .build()
    }

    private fun promoteToForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL, "Legion Replay", NotificationManager.IMPORTANCE_LOW
        ))
        startForeground(NOTICE, notice(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun begin(token: Intent, config: CaptureConfig, windowSeconds: Int) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = requireNotNull(manager.getMediaProjection(Activity.RESULT_OK, token))
        capture = projection
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { requestStop("Android отозвал захват экрана Replay.") }
            }
        }
        captureCallback = callback
        projection.registerCallback(callback, handler)

        val bounds = (getSystemService(WINDOW_SERVICE) as WindowManager)
            .maximumWindowMetrics.bounds
        val size = CaptureConfig.scaledSize(
            bounds.width(), bounds.height(), config.maxEdge
        )
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC, size.first, size.second
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec = encoder
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val input = encoder.createInputSurface()
        surface = input
        encoder.start()
        // Android 14+: never call createVirtualDisplay twice with this projection.
        virtualDisplay = projection.createVirtualDisplay(
            "LegionReplay", size.first, size.second,
            resources.displayMetrics.densityDpi.coerceAtLeast(1),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, input, null, handler
        )
        running.set(true)
        active = true
        drainComplete = false
        if (audioRequested) {
            runCatching {
                ReplayAudioCapture(this, projection, requireNotNull(selectedGameUid),
                    windowSeconds).also {
                    it.start()
                    playbackAudio = it
                }
            }.onFailure {
                audioProblem = "Аудиозахват недоступен: " + it.javaClass.simpleName
            }
        }
        codecThread = Thread({
            drainVideo(encoder, ReplayFrameBuffer(windowSeconds))
        }, "legion-replay-video").apply { start() }
        handler.postDelayed(safetyStop, 60L * 60_000L)
        writeStatus("Replay активен: буфер " + windowSeconds +
            " с, H.264. " + when {
                playbackAudio != null -> "Звук игры запрошен; приложение может запретить его."
                audioRequested -> "Видео БЕЗ звука: " + (audioProblem ?: "Android отказал")
                else -> "Видео без звука."
            } + " Нажмите Сохранить после момента в игре.")
    }

    private fun drainVideo(encoder: MediaCodec, buffer: ReplayFrameBuffer) {
        var videoFormat: MediaFormat? = null
        var videoMinusNanoClockUs: Long? = null
        val info = MediaCodec.BufferInfo()
        var deadline = Long.MAX_VALUE
        var nextSafetyAt = SystemClock.elapsedRealtime() + 2_000L
        try {
            while (running.get() || SystemClock.elapsedRealtime() < deadline) {
                if (!running.get() && deadline == Long.MAX_VALUE) {
                    deadline = SystemClock.elapsedRealtime() + 2500L
                }
                val now = SystemClock.elapsedRealtime()
                if (running.get() && now >= nextSafetyAt) {
                    nextSafetyAt = now + 2_000L
                    val thermal = runCatching {
                        (getSystemService(POWER_SERVICE) as PowerManager).currentThermalStatus
                    }.getOrNull()
                    val lowMemory = runCatching {
                        ActivityManager.MemoryInfo().also {
                            (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                                .getMemoryInfo(it)
                        }.lowMemory
                    }.getOrDefault(false)
                    if (lowMemory || (thermal != null &&
                        thermal >= PowerManager.THERMAL_STATUS_SEVERE)) {
                        handler.post {
                            requestStop(if (lowMemory) "Replay остановлен: мало доступной RAM."
                                else "Replay остановлен: Android сообщает сильный перегрев.")
                        }
                    }
                }
                val index = encoder.dequeueOutputBuffer(info, 10_000L)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(videoFormat == null) {
                            "Encoder output format changed mid-session"
                        }
                        videoFormat = encoder.outputFormat
                    }
                    index >= 0 -> {
                        try {
                            if (info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                                info.size <= 4 * 1024 * 1024) {
                                val data = requireNotNull(encoder.getOutputBuffer(index))
                                    .duplicate()
                                data.position(info.offset)
                                data.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                data.get(bytes)
                                if (videoMinusNanoClockUs == null) {
                                    videoMinusNanoClockUs =
                                        info.presentationTimeUs - System.nanoTime() / 1000L
                                }
                                buffer.add(ReplayFrame(bytes, info.presentationTimeUs,
                                    info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0))
                            }
                            val isEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (isEnd) break
                        } finally {
                            encoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
                // A still game can produce no new encoded frames: process SAVE
                // even on INFO_TRY_AGAIN_LATER, without creating another display.
                if (saveRequested.get() && !stopping.get() && videoFormat != null &&
                    !exporting.get()) {
                    val nowVideoUs = ReplayAudioTimeline.videoPts(
                        System.nanoTime() / 1000L, videoMinusNanoClockUs ?: 0L)
                    val snapshot = if (nowVideoUs != null) buffer.snapshotAt(nowVideoUs)
                        else emptyList()
                    if (snapshot.isNotEmpty() && saveRequested.compareAndSet(true, false)) {
                        exportSnapshot(requireNotNull(videoFormat), snapshot,
                            videoMinusNanoClockUs ?: 0L)
                    } else if (SystemClock.elapsedRealtime() - saveRequestedAtMs > 5_000L &&
                        saveRequested.compareAndSet(true, false)) {
                        writeStatus("Replay: нет достаточного свежего видео для сохранения.")
                    }
                }
            }
        } catch (error: Exception) {
            writeStatus("Replay: ошибка кодирования — " + error.javaClass.simpleName)
        } finally {
            active = false
            running.set(false)
            saveRequested.set(false)
            buffer.clear()
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            handler.post {
                codec = null
                drainComplete = true
                if (stopping.compareAndSet(false, true)) {
                    CaptureCoordinator.markStopping(CaptureMode.REPLAY)
                    writeStatus("Replay остановлен: кодек завершил работу.")
                }
                finishIfReady()
            }
        }
    }

    private fun exportSnapshot(format: MediaFormat, frozen: List<ReplayFrame>,
                               clockOffsetUs: Long) {
        if (!exporting.compareAndSet(false, true)) {
            writeStatus("Replay: предыдущий клип ещё сохраняется.")
            return
        }
        val audioSnapshot = playbackAudio?.snapshot(
            frozen.first().ptsUs, frozen.last().ptsUs, clockOffsetUs)
        Thread({
            val durationMs = (frozen.last().ptsUs - frozen.first().ptsUs) / 1000L
            var audioSaved = false
            val result = runCatching {
                if (audioSnapshot != null) {
                    runCatching {
                        ReplayExporter.save(applicationContext, format, frozen,
                            audioSnapshot, clockOffsetUs)
                    }.fold(
                        { audioSaved = true; it },
                        { ReplayExporter.save(applicationContext, format, frozen) }
                    )
                } else ReplayExporter.save(applicationContext, format, frozen)
            }
            writeStatus(result.fold(
                { "Replay MP4 сохранён в Movies/LegionControl/. Длительность: " +
                    durationMs / 1000L + " с. " +
                    (if (audioSaved) "Видео и AAC-звук игры." else "Видео без звука.") +
                    " Захват продолжается, если не остановлен." },
                { "Replay MP4 не сохранён: " + it.javaClass.simpleName }
            ))
            exporting.set(false)
            handler.post { finishIfReady() }
        }, "legion-replay-export").start()
    }

    private fun writeStatus(message: String) {
        getSharedPreferences(CaptureRecordingService.PREFS, MODE_PRIVATE)
            .edit().putString(REPLAY_STATUS, message).apply()
    }

    private fun requestStop(reason: String) {
        if (!stopping.compareAndSet(false, true)) return
        CaptureCoordinator.markStopping(CaptureMode.REPLAY)
        handler.removeCallbacks(safetyStop)
        writeStatus(reason)
        active = false
        running.set(false)
        playbackAudio?.stop()
        val encoder = codec
        if (encoder != null && codecThread != null) {
            runCatching { encoder.signalEndOfInputStream() }
        } else {
            runCatching { encoder?.release() }
            codec = null
            drainComplete = true
        }
        // VirtualDisplay is removed promptly; codec can flush an already encoded tail.
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        finishIfReady()
    }

    private fun finishIfReady() {
        if (!stopping.get() || !drainComplete || exporting.get()) return
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { surface?.release() }
        surface = null
        val projection = capture
        val callback = captureCallback
        capture = null
        captureCallback = null
        if (callback != null) runCatching { projection?.unregisterCallback(callback) }
        playbackAudio?.stop()
        playbackAudio = null
        runCatching { projection?.stop() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        CaptureCoordinator.release(CaptureMode.REPLAY)
        stopSelf()
    }

    override fun onDestroy() {
        if (!stopping.get()) requestStop("Replay прерван системой.")
        super.onDestroy()
    }
}
