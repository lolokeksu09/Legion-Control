package dev.lolokeksu.legioncontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Visible user-started FGS. No boot receiver, restart, hidden tracker or auto upload.
 * Android does not promise uninterrupted sampling under OEM/background restrictions.
 */
class GameSessionRecorderService : Service() {
    companion object {
        @Volatile var sessionActive = false
            private set
        const val ACTION_START = "dev.lolokeksu.legioncontrol.game.START"
        const val ACTION_STOP = "dev.lolokeksu.legioncontrol.game.STOP"
        const val ACTION_MARK = "dev.lolokeksu.legioncontrol.game.MARK"
        const val EXTRA_MARK_TEXT = "mark_text"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LABEL = "label"
        const val EXTRA_TCP = "tcp"
        private const val CHANNEL = "game-session-recording"
        private const val NOTICE = 1701
        private const val SAMPLE_SECONDS = 15L
        private const val TCP_PERIOD_MS = 120_000L
        fun start(context: Context, pkg: String, label: String, tcp: Boolean) {
            val i = Intent(context,GameSessionRecorderService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACKAGE,pkg)
                putExtra(EXTRA_LABEL,label)
                putExtra(EXTRA_TCP,tcp)
            }
            context.startForegroundService(i)
        }
        fun stop(context: Context) {
            context.startService(Intent(context,GameSessionRecorderService::class.java).apply { action = ACTION_STOP })
        }
        /** A user-tapped marker is appended only to an already active session. */
        fun mark(context: Context, note: String) {
            val normalized = SessionSignals.normalizeMarker(note)
                ?: throw IllegalArgumentException("Метка пуста")
            context.startService(Intent(context,GameSessionRecorderService::class.java).apply {
                action = ACTION_MARK
                putExtra(EXTRA_MARK_TEXT, normalized)
            })
        }
    }

    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var ticker: ScheduledFuture<*>? = null
    private var storage: GameSessionRepository? = null
    private var smartStorage: SmartProfilesRepository? = null
    private var smartRules: List<SmartRule> = emptyList()
    private val smartEngine=SmartRulesEngine()
    private var recordingPackage: String = ""
    @Volatile private var started = false
    @Volatile private var stopping = false
    private var activeId: String? = null
    private var networkManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var thermalManager: PowerManager? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var lastNetwork = ""
    private var tcpAllowed = false
    private var lastTcpAt:Long? = null
    private val observedNetwork=DistinctObservation<Pair<Long,Boolean>>()
    private val observedThermal=DistinctObservation<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
            createNotificationChannel(NotificationChannel(CHANNEL,"Запись игровой сессии",NotificationManager.IMPORTANCE_LOW))
            createNotificationChannel(NotificationChannel("smart-rule-alerts","Предупреждения профиля",NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording("USER_STOP")
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_MARK) {
            // startService may create an idle Service. Never start recording for a mark.
            if (!started || stopping) {
                if (!started) stopSelfResult(startId)
                return START_NOT_STICKY
            }
            val note = SessionSignals.normalizeMarker(
                intent.getStringExtra(EXTRA_MARK_TEXT).orEmpty())
            if (note != null) enqueueEvent("USER_MARK",note)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START && !startupRecoveryAllowsServiceStart()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || started || stopping) return START_NOT_STICKY
        if (CaptureCoordinator.state().first == CaptureMode.MAINTENANCE) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)?.trim().orEmpty()
        if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(pkg)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val label = intent.getStringExtra(EXTRA_LABEL)?.take(100)?.ifBlank { pkg } ?: pkg
        tcpAllowed = intent.getBooleanExtra(EXTRA_TCP,false)
        started = true
        try {
            val notification = notification(label)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTICE,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTICE,notification)
        } catch (e: Exception) {
            Log.e("LegionSession","Foreground service unavailable",e)
            started = false
            sessionActive = false
            stopSelf()
            return START_NOT_STICKY
        }
        sessionActive = true
        worker.execute {
            try {
                val repository = GameSessionRepository(applicationContext)
                storage = repository
                val id = repository.start(pkg,label,tcpAllowed)
                activeId = id
                recordingPackage=pkg
                smartStorage = SmartProfilesRepository(applicationContext)
                smartRules=smartStorage?.rules(pkg).orEmpty()
                repository.event(id,"START","Запись запущена пользователем")
                observeNetwork()
                observeThermal()
                ticker = worker.scheduleWithFixedDelay(
                    { capture() },0,SAMPLE_SECONDS,TimeUnit.SECONDS
                )
            } catch(e: Exception) {
                Log.e("LegionSession","Session start failed",e)
                stopRecording("START_FAILED")
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(label: String): Notification {
        val stop = PendingIntent.getService(this,1,
            Intent(this,GameSessionRecorderService::class.java).apply { action=ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this,2,
            Intent(this,GameSessionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Legion Control · идёт запись")
            .setContentText(label)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause,"Остановить",stop)
            .build()
    }

    private fun observeNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                observedNetwork.clearIf { it.first==network.networkHandle }
                enqueueEvent("NETWORK","Android: сеть потеряна")
            }
            override fun onAvailable(network: Network) { enqueueEvent("NETWORK","Android: сеть доступна") }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val validated=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if(!observedNetwork.accept(network.networkHandle to validated))return
                val status = "Android: сеть " + if(validated)
                    "проверена" else "не подтверждена"
                enqueueEvent("NETWORK",status)
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkManager = cm
            networkCallback = callback
        } catch(e: Exception) { enqueueEvent("API_ERROR","NetworkCallback: "+e.javaClass.simpleName) }
    }

    private fun observeThermal() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val callback = PowerManager.OnThermalStatusChangedListener { status ->
            if(observedThermal.accept(status)) enqueueEvent("THERMAL","Android Thermal Status: $status")
        }
        try {
            pm.addThermalStatusListener(worker,callback)
            thermalManager = pm
            thermalListener = callback
        } catch(e: Exception) { enqueueEvent("API_ERROR","Thermal listener: "+e.javaClass.simpleName) }
    }

    private fun enqueueEvent(
        type: String, detail: String, recordedAtMs: Long = System.currentTimeMillis()
    ) {
        // Preserve observation time even when SQLite writes wait in the serial queue.
        if (stopping) return
        runCatching {
            worker.execute {
                val id = activeId ?: return@execute
                runCatching { storage?.event(id,type,detail,recordedAtMs) }
                    .onFailure { Log.w("LegionSession","Event not saved",it) }
            }
        }
    }

    private fun capture() {
        if(stopping) return
        val id = activeId ?: return
        try {
            val point = GameSessionTelemetry.snapshot(applicationContext)
            storage?.record(id,point)
            for(alert in smartEngine.evaluate(point,smartRules)) {
                runCatching {
                    smartStorage?.logAlert(id,recordingPackage,alert)
                    storage?.event(id,"SMART_RULE",alert.description,point.timeMs)
                    notifySmartRule(alert)
                }.onFailure { Log.w("LegionSession","Rule event not saved",it) }
            }
            val net = point.networkType+":"+(point.networkValidated?.toString() ?: "?")
            if (net != lastNetwork) {
                lastNetwork = net
                storage?.event(id,"NETWORK_STATE",net,point.timeMs)
            }
            if(point.powerSaving == true) {
                // Only log transitions to avoid repeated identical alerts.
                if (!wasSaving) storage?.event(id,"POWER_SAVE","Энергосбережение включено",point.timeMs)
                wasSaving = true
            } else if(point.powerSaving == false) wasSaving = false
            if((point.thermalStatus ?: -1) >= 3 && !wasSevere) {
                storage?.event(id,"THERMAL_WARNING","Android Thermal Status ≥ SEVERE",point.timeMs)
                wasSevere = true
            } else if((point.thermalStatus ?: -1) < 3) wasSevere = false
            val nowElapsed=android.os.SystemClock.elapsedRealtime()
            if (tcpAllowed && MonotonicProbePolicy.due(lastTcpAt,nowElapsed,TCP_PERIOD_MS) &&
                (point.thermalStatus ?: -1) < 3 && (point.batteryTempDeciC ?: -1000) < 450
            ) {
                lastTcpAt = nowElapsed
                val probe = NetworkDiagnostics.probe()
                val stats = TcpProbeStatistics.analyze(probe.roundTripsMs)
                storage?.event(id,"TCP_PROBE",
                    "one.one.one.one:443 · "+probe.roundTripsMs.size+"/"+probe.attempts+
                    " · avg="+(stats?.averageMs?.let { String.format(java.util.Locale.US,"%.1f ms",it) } ?: "n/a")+
                    " · ошибки="+probe.failures)
            }
        } catch (e: Exception) {
            Log.w("LegionSession","Sample failed",e)
            runCatching { storage?.event(id,"API_ERROR","Sample: "+e.javaClass.simpleName) }
        }
    }
    private fun notifySmartRule(alert: SmartRuleAlert) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open=PendingIntent.getActivity(this,1800,
            Intent(this,SmartProfilesActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(1800+(alert.kind.ordinal),Notification.Builder(this,"smart-rule-alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Legion Control · "+alert.kind.title)
            .setContentText(alert.description)
            .setContentIntent(open).setAutoCancel(true).build())
    }

    private var wasSaving = false
    private var wasSevere = false

    private fun stopRecording(reason: String) {
        if(stopping) return
        stopping = true
        started = false
        ticker?.cancel(false)
        networkCallback?.let { runCatching { networkManager?.unregisterNetworkCallback(it) } }
        networkCallback = null
        thermalListener?.let { runCatching { thermalManager?.removeThermalStatusListener(it) } }
        thermalListener = null
        runCatching {
            worker.execute {
                activeId?.let { id ->
                    runCatching {
                        storage?.event(id,"STOP",reason)
                        storage?.finish(id,reason)
                    }.onFailure { Log.w("LegionSession","Finalization failed",it) }
                }
                runCatching { storage?.close() }
                runCatching { smartStorage?.close() }
                smartStorage=null
                smartRules=emptyList()
                storage = null
                activeId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                sessionActive = false
                stopSelf()
            }
        }.onFailure {
            sessionActive = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        ticker?.cancel(false)
        networkCallback?.let { runCatching { networkManager?.unregisterNetworkCallback(it) } }
        thermalListener?.let { runCatching { thermalManager?.removeThermalStatusListener(it) } }
        if (!stopping) {
            stopping=true
            runCatching {
                worker.execute {
                    activeId?.let { runCatching { storage?.finish(it,"SERVICE_DESTROYED") } }
                    runCatching { storage?.close() }
                    runCatching { smartStorage?.close() }
                }
            }
        }
        worker.shutdown()
        sessionActive = false
        super.onDestroy()
    }
}
