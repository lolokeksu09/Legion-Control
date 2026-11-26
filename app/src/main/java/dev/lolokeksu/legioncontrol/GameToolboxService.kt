package dev.lolokeksu.legioncontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Explicit, user-started, non-focusable overlay. It only receives touches inside its
 * own visible bubble/panel, never records input from another app. An independently
 * confirmed one-shot automation may observe Android UsageEvents.
 */
class GameToolboxService : Service() {
    companion object {
        const val ACTION_START = "dev.lolokeksu.legioncontrol.toolbox.START"
        const val ACTION_STOP = "dev.lolokeksu.legioncontrol.toolbox.STOP"
        const val ACTION_REFRESH = "dev.lolokeksu.legioncontrol.toolbox.REFRESH"
        const val ACTION_AUTO = "dev.lolokeksu.legioncontrol.toolbox.AUTO"
        const val ACTION_AUTO_STOP = "dev.lolokeksu.legioncontrol.toolbox.AUTO_STOP"
        private const val CHANNEL = "game-toolbox"
        private const val NOTIFICATION_ID = 2401
        @Volatile private var active = false
        internal fun isPanelActive():Boolean=active

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, GameToolboxService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GameToolboxService::class.java).setAction(ACTION_STOP)
            )
        }

        internal fun startAutomation(context:Context,pkg:String,profile:GameLaunchProfile) {
            context.startForegroundService(Intent(context,GameToolboxService::class.java).setAction(ACTION_AUTO)
                .putExtra("auto_pkg",pkg).putExtra("auto_profile",profile.encode())
                .putExtra("auto_confirmed_at",System.currentTimeMillis()))
        }
        internal fun stopAutomation(context:Context) {
            context.startService(Intent(context,GameToolboxService::class.java).setAction(ACTION_AUTO_STOP))
        }

        fun refresh(context: Context) {
            if (active) context.startService(
                Intent(context, GameToolboxService::class.java).setAction(ACTION_REFRESH)
            )
        }
    }

    private val prefs by lazy { getSharedPreferences(ToolboxPreferences.NAME, MODE_PRIVATE) }
    private val windows by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LinearLayout? = null
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var ticker: TextView? = null
    private var feedback: TextView? = null
    private var expanded = false
    private var startedAt = 0L
    private var automation:GameAutomationController?=null
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private val clock = object : Runnable {
        override fun run() {
            val seconds = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0) / 1000
            ticker?.text = "Панель активна " + (seconds / 60) + " мин " +
                (seconds % 60).toString().padStart(2, '0') + " с"
            if (overlay != null) handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==ACTION_AUTO_STOP) {
            automation?.stop("Остановка пользователем")
            if(overlay==null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if ((intent?.action == ACTION_REFRESH || intent?.action == ACTION_START ||
                intent?.action == ACTION_AUTO) && !startupRecoveryAllowsServiceStart()) {
            if (overlay == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            if (overlay != null) applyPreferences() else stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if(intent?.action!=ACTION_START && intent?.action!=ACTION_AUTO) { if(overlay==null) stopSelfResult(startId);return START_NOT_STICKY }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlay == null) {
            try {
                promoteToForeground()
                startedAt = SystemClock.elapsedRealtime()
                createOverlay()
                active = true
                handler.post(clock)
            } catch (_: Exception) {
                stopSelf()
            }
        } else {
            applyPreferences()
        }
        if(intent?.action==ACTION_AUTO && overlay!=null && active) {
            val pkg=intent.getStringExtra("auto_pkg")
            val profile=GameLaunchProfile.decode(intent.getStringExtra("auto_profile"))
            val confirmed=intent.getLongExtra("auto_confirmed_at",0L)
            if(automation!=null) showFeedback("Авто-сессия уже активна.")
            else if(pkg!=null && profile!=null && System.currentTimeMillis()-confirmed in 0L..15_000L) {
                val controller=GameAutomationController(this,pkg,profile,confirmed,{ message ->
                    showFeedback(message); updateAutomationNotification(message)
                },{ message ->
                    automation=null;showFeedback(message);updateAutomationNotification("Автоматизация завершена. "+message)
                })
                automation=controller;controller.start()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateAutomationNotification(message:String) {
        runCatching { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID,buildNotification(message)) }
    }

    private fun promoteToForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Legion Game Toolbox", NotificationManager.IMPORTANCE_LOW)
        )
        val notification=buildNotification("Плавающая панель включена вручную · нажмите для настроек")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(message:String):Notification {
        val openIntent = Intent(this, GameToolboxActivity::class.java)
        val open = PendingIntent.getActivity(this, 2401, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2402,
            Intent(this, GameToolboxService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val autoStop=PendingIntent.getService(this,2403,Intent(this,GameToolboxService::class.java).setAction(ACTION_AUTO_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Legion Control · Game Toolbox")
            .setContentText(message.take(180))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stop)
        if(automation!=null) builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Завершить авто",autoStop)
        return builder.build()
    }

    private fun rounded(color: Int, radius: Int, border: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.dp.toFloat()
            if (border != 0) setStroke(1.dp, border)
        }

    private fun createOverlay() {
        val buttonSize = ToolboxTuning.sizeDp(
            prefs.getInt(ToolboxPreferences.SIZE, 56)).dp
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        val handle = TextView(this).apply {
            text = "LC"
            textSize = 16f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFF25272D.toInt(), 40, 0xFFE35C55.toInt())
            isClickable = true
            contentDescription = "Открыть или свернуть Game Toolbox. Удерживать и переместить."
        }
        root.addView(handle, LinearLayout.LayoutParams(buttonSize, buttonSize))
        val tools = buildPanel()
        tools.visibility = View.GONE
        root.addView(tools, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        overlay = root
        bubble = handle
        panel = tools

        val metrics = windows.currentWindowMetrics.bounds
        val config = WindowManager.LayoutParams(
            buttonSize,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ToolboxTuning.position(
                prefs.getInt(ToolboxPreferences.POS_X, 20.dp), metrics.width() - buttonSize)
            y = ToolboxTuning.position(
                prefs.getInt(ToolboxPreferences.POS_Y, 150.dp), metrics.height() - buttonSize)
        }
        params = config
        handle.setOnTouchListener(DragHandler())
        windows.addView(root, config)
        applyPreferences()
    }

    private inner class DragHandler : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var initialX = 0
        private var initialY = 0
        private var moved = false
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val p = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialX = p.x
                    initialY = p.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8.dp || kotlin.math.abs(dy) > 8.dp) moved = true
                    if (moved) {
                        val metrics = windows.currentWindowMetrics.bounds
                        p.x = ToolboxTuning.position(initialX + dx, metrics.width() - p.width)
                        p.y = ToolboxTuning.position(initialY + dy,
                            metrics.height() - (bubble?.height ?: 56.dp))
                        updateOverlay()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) prefs.edit()
                        .putInt(ToolboxPreferences.POS_X, p.x)
                        .putInt(ToolboxPreferences.POS_Y, p.y).apply()
                    else togglePanel()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    private fun buildPanel(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 12.dp, 12.dp, 14.dp)
            background = rounded(0xFF121316.toInt(), 16, 0xFF51535B.toInt())
        }
        box.addView(TextView(this).apply {
            text = "GAME TOOLBOX"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        ticker = TextView(this).apply {
            setTextColor(0xFFB6BABF.toInt())
            textSize = 12f
            setPadding(0, 5.dp, 0, 6.dp)
            text = "Таймер начинается при открытии панели"
        }
        box.addView(ticker)
        fun row(left: String, leftAction: () -> Unit, right: String, rightAction: () -> Unit) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun add(label: String, action: () -> Unit) {
                line.addView(Button(this).apply {
                    text = label
                    textSize = 12f
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        0xFF292C33.toInt())
                    minHeight = 48.dp
                    setOnClickListener { action() }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            add(left, leftAction)
            add(right, rightAction)
            box.addView(line)
        }
        row("Громкость −", { volume(-1) }, "Громкость +", { volume(1) })
        row("DND ВКЛ", { showFeedback(SmartSystemControls.setDoNotDisturb(this, true)) },
            "DND ВЫКЛ", { showFeedback(SmartSystemControls.setDoNotDisturb(this, false)) })
        row("Яркость 50%", { showFeedback(SmartSystemControls.setBrightness(this, 50)) },
            "Яркость 80%", { showFeedback(SmartSystemControls.setBrightness(this, 80)) })
        row("Вернуть яркость", {
            showFeedback(SmartSystemControls.restoreScreenSettings(this))
        }, "Открыть игру", { openSelectedGame() })
        row("Профиль игры", {
            val pkg = prefs.getString(ToolboxPreferences.GAME, null)
            showFeedback(if (pkg == null) "Сначала выберите игру в настройках."
                else GameLaunchProfiles.apply(this, pkg).message)
        }, "Вернуть профиль", {
            showFeedback(GameLaunchProfiles.restore(this).message)
        })
        row("REPLAY SAVE", {
            showFeedback(runCatching {
                if (ReplayCaptureService.save(this))
                    "Replay сохранение запрошено. Захват продолжается."
                else "Replay выключен. Включите его в Capture Studio."
            }.getOrElse { "Не удалось сохранить Replay: " + it.javaClass.simpleName })
        }, "Replay настройки", {
            showFeedback(runCatching {
                startActivity(Intent(this, CaptureStudioActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Открыт Capture Studio."
            }.getOrElse { "Не удалось открыть Capture Studio: " + it.javaClass.simpleName })
        })
        row("Настройки", { openController() }, "СТОП", { stopSelf() })
        feedback = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFD88A.toInt())
            setPadding(0, 8.dp, 0, 0)
            maxLines = 4
            text = "Все действия — вручную. Для DND и яркости нужны специальные разрешения."
        }
        box.addView(feedback)
        return box
    }

    private fun volume(direction: Int) {
        showFeedback(SmartSystemControls.withManualMutation(this) {
            runCatching {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return@runCatching "Аудиопоток не поддерживает регулировку."
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                ToolboxTuning.volumeStep(before, max, direction), 0)
            val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            "Громкость мультимедиа: " + after + " / " + max
            }.getOrElse { "Громкость недоступна: " + it.javaClass.simpleName }
        })
    }

    private fun openSelectedGame() {
        val pkg = prefs.getString(ToolboxPreferences.GAME, null)
        if (pkg.isNullOrBlank()) {
            showFeedback("Выберите игру в настройках Game Toolbox.")
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            showFeedback("Android не разрешает запуск выбранного приложения.")
            return
        }
        showFeedback(runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            "Запуск приложения запрошен."
        }.getOrElse { "Запуск отклонён системой: " + it.javaClass.simpleName })
    }

    private fun openController() {
        showFeedback(runCatching {
            startActivity(Intent(this, GameToolboxActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Открыты настройки Game Toolbox."
        }.getOrElse { "Не удалось открыть настройки: " + it.javaClass.simpleName })
    }

    private fun showFeedback(message: String) {
        feedback?.text = message.take(220)
    }

    private fun togglePanel() {
        expanded = !expanded
        panel?.visibility = if (expanded) View.VISIBLE else View.GONE
        applyPreferences()
        // Panel height becomes known after the first layout pass. Reposition the
        // complete expanded panel on screen even if the user dragged LC to the bottom.
        if (expanded) overlay?.post {
            val root = overlay ?: return@post
            val p = params ?: return@post
            val height = windows.currentWindowMetrics.bounds.height()
            p.y = ToolboxTuning.position(p.y, height - root.height)
            updateOverlay()
        }
    }

    private fun applyPreferences() {
        val p = params ?: return
        val root = overlay ?: return
        val size = ToolboxTuning.sizeDp(prefs.getInt(ToolboxPreferences.SIZE, 56)).dp
        bubble?.layoutParams = (bubble?.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = size
            height = size
        }
        root.alpha = ToolboxTuning.opacity(
            prefs.getInt(ToolboxPreferences.OPACITY, 85)) / 100f
        val metrics = windows.currentWindowMetrics.bounds
        p.width = if (expanded) minOf(284.dp, metrics.width()) else size
        p.x = ToolboxTuning.position(p.x, metrics.width() - p.width)
        p.y = ToolboxTuning.position(p.y, metrics.height() - size)
        updateOverlay()
    }

    private fun updateOverlay() {
        val root = overlay ?: return
        val config = params ?: return
        runCatching { windows.updateViewLayout(root, config) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPreferences()
    }

    override fun onDestroy() {
        active = false
        automation?.stop("Панель остановлена")
        automation=null
        handler.removeCallbacks(clock)
        overlay?.let { runCatching { windows.removeView(it) } }
        overlay = null
        bubble = null
        panel = null
        params = null
        ticker = null
        feedback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
