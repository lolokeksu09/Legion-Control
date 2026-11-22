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
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One explicit screenshot per fresh Android MediaProjection consent.
 * Foreground service -> getMediaProjection -> callback -> one virtual display.
 * No Accessibility, root, background game detection, silent screenshots or token reuse.
 */
class CaptureScreenshotService : Service() {
    companion object {
        const val ACTION_START = "dev.lolokeksu.legioncontrol.screenshot.START"
        const val ACTION_CANCEL = "dev.lolokeksu.legioncontrol.screenshot.CANCEL"
        const val EXTRA_RESULT = "screenshot_result"
        const val EXTRA_CONSENT = "screenshot_consent"
        private const val CHANNEL = "legion-screenshot"
        private const val NOTICE = 3401
        @Volatile var active: Boolean = false
            private set

        fun cancel(context: Context) {
            if (active) context.startService(
                Intent(context, CaptureScreenshotService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val finished = AtomicBoolean(false)
    private var worker: HandlerThread? = null
    private var frameHandler: Handler? = null
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var captured = false
    private var captureAfterMs = 0L
    private val timeout = Runnable { end("Время ожидания кадра истекло. Снимок не сохранён.") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            end("Захват отменён пользователем.")
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || active || finished.get()) {
            if (!active) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val consent = intent.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        if (intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) != Activity.RESULT_OK ||
            consent == null || CaptureRecordingService.recording
        ) {
            status("Нет нового согласия Android либо уже идёт запись видео.")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        active = true
        try {
            promote()
            begin(consent)
        } catch (e: Exception) {
            end("Не удалось захватить экран: " + e.javaClass.simpleName)
        }
        return START_NOT_STICKY
    }

    private fun status(message: String) {
        getSharedPreferences(CaptureRecordingService.PREFS, MODE_PRIVATE).edit()
            .putString(CaptureRecordingService.LAST_RESULT, message).apply()
    }

    private fun promote() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            "Capture Studio · снимок экрана", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, NOTICE,
            Intent(this, CaptureStudioActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, NOTICE + 1,
            Intent(this, CaptureScreenshotService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Legion Capture Studio · скриншот")
            .setContentText("Через 3 секунды будет сделан один снимок экрана")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отмена", cancel)
            .build()
        startForeground(NOTICE, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun begin(consent: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val capture = requireNotNull(manager.getMediaProjection(Activity.RESULT_OK, consent))
        projection = capture
        val callback = object : MediaProjection.Callback() {
            override fun onStop() { end("Android отозвал разрешение на снимок.") }
        }
        projectionCallback = callback
        capture.registerCallback(callback, main)

        val bounds = (getSystemService(WINDOW_SERVICE) as WindowManager)
            .maximumWindowMetrics.bounds
        val (width, height) = CaptureScreenshotPolicy.dimensions(bounds.width(), bounds.height())
        val density = resources.displayMetrics.densityDpi.coerceAtLeast(1)
        val thread = HandlerThread("legion-screenshot-frames").apply { start() }
        worker = thread
        val handler = Handler(thread.looper)
        frameHandler = handler
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = imageReader
        captureAfterMs = SystemClock.elapsedRealtime() + 3000L
        imageReader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            image.use {
                if (finished.get() || captured ||
                    SystemClock.elapsedRealtime() < captureAfterMs) return@use
                captured = true
                val result = runCatching { saveImage(it, width, height) }
                end(result.fold(
                    { "Скриншот сохранён в Pictures/LegionControl/." },
                    { "Не удалось сохранить PNG: " + it.javaClass.simpleName }
                ))
            }
        }, handler)
        display = capture.createVirtualDisplay(
            "LegionScreenshot", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, handler
        )
        status("Разрешение получено. Снимок будет сделан через 3 секунды.")
        main.postDelayed(timeout, CaptureScreenshotPolicy.TIMEOUT_MS)
    }

    private fun saveImage(image: android.media.Image, width: Int, height: Int) {
        require(image.width == width && image.height == height)
        val plane = image.planes.single()
        val paddedWidth = CaptureScreenshotPolicy.paddedWidth(
            width, plane.pixelStride, plane.rowStride
        )
        // The final image row may omit trailing row padding; pack rows explicitly.
        val bytesPerRow = width * plane.pixelStride
        val paddedBytes = ByteBuffer.allocate(plane.rowStride * height)
        val source = plane.buffer.duplicate()
        val line = ByteArray(bytesPerRow)
        for (y in 0 until height) {
            source.position(y * plane.rowStride)
            source.get(line)
            paddedBytes.position(y * plane.rowStride)
            paddedBytes.put(line)
        }
        paddedBytes.rewind()
        val raw = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        try {
            raw.copyPixelsFromBuffer(paddedBytes)
            val cropped = Bitmap.createBitmap(raw, 0, 0, width, height)
            try {
                val uri = CaptureScreenshotLibrary.createPending(
                    this, CaptureScreenshotPolicy.fileName(System.currentTimeMillis())
                )
                var published = false
                try {
                    val stream = requireNotNull(contentResolver.openOutputStream(uri, "w"))
                    stream.use { check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    CaptureScreenshotLibrary.publish(this, uri)
                    published = true
                } finally {
                    if (!published) runCatching { CaptureScreenshotLibrary.delete(this, uri) }
                }
            } finally {
                cropped.recycle()
            }
        } finally {
            raw.recycle()
        }
    }

    private fun end(message: String) {
        if (!finished.compareAndSet(false, true)) return
        main.post {
            main.removeCallbacks(timeout)
            reader?.setOnImageAvailableListener(null, null)
            runCatching { display?.release() }
            display = null
            runCatching { reader?.close() }
            reader = null
            val capture = projection
            projection = null
            projectionCallback?.let { runCatching { capture?.unregisterCallback(it) } }
            projectionCallback = null
            runCatching { capture?.stop() }
            worker?.quitSafely()
            worker = null
            frameHandler = null
            active = false
            status(message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (!finished.get()) end("Сервис захвата остановлен до сохранения кадра.")
        super.onDestroy()
    }
}
