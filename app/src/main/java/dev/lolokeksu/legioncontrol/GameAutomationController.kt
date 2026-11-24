package dev.lolokeksu.legioncontrol

import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import java.util.UUID
import java.util.concurrent.Executors

/** One opt-in in-memory lease; no restart or persistent foreground-app history. */
internal class GameAutomationController(
    private val context:Context,private val pkg:String,private val approved:GameLaunchProfile,
    private val confirmedAt:Long,private val update:(String)->Unit,private val finished:(String)->Unit
) {
    companion object {
        private const val PREFS="legion-automation-report-v1"
        fun lastReport(context:Context):String?=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("last",null)
    }
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val owner=UUID.randomUUID().toString()
    private val armedElapsed=SystemClock.elapsedRealtime()
    private var lastPollElapsed=armedElapsed
    private var watermark=confirmedAt-1
    private var lastWall=confirmedAt
    private var startedElapsed:Long?=null
    private var maxTemp:Int?=null
    private var maxThermal:Int?=null
    private var owned=false
    private var stopped=false
    private var tick=Runnable{poll()}
    private data class Observation(val events:List<AutomationEvent>,val temp:Int?,val thermal:Int?)
    private fun available():Boolean {
        val nm=context.getSystemService(NotificationManager::class.java)
        val power=context.getSystemService(PowerManager::class.java)
        val keyguard=context.getSystemService(KeyguardManager::class.java)
        return GameUsageDiagnostics.hasUsageAccess(context) && Settings.canDrawOverlays(context) &&
            nm?.areNotificationsEnabled()==true && nm.getNotificationChannel("game-toolbox")?.importance?.let{it>NotificationManager.IMPORTANCE_NONE}==true &&
            power?.isInteractive==true && keyguard?.isKeyguardLocked==false
    }
    fun start() {
        if(!GameLaunchProfilePolicy.validPackage(pkg)||pkg==context.packageName||!approved.actionable||
            GameLaunchProfiles.profile(context,pkg)!=approved||GameLaunchProfiles.pendingPackage(context)!=null||
            !runCatching{available()}.getOrDefault(false)){stop("Предусловия автоматизации не выполнены");return}
        update("Авто: ожидание входа в выбранную игру (до 90 с).")
        poll()
    }
    private fun poll() {
        if(stopped)return
        val elapsed=SystemClock.elapsedRealtime();val wall=System.currentTimeMillis()
        if(elapsed-armedElapsed>=GameAutomationPolicy.MAX_MS || (startedElapsed==null && elapsed-armedElapsed>=GameAutomationPolicy.ARM_MS)){
            stop("Лимит времени");return
        }
        if(elapsed-lastPollElapsed>GameAutomationPolicy.MAX_POLL_GAP_MS || wall<lastWall ||
            kotlin.math.abs((wall-lastWall)-(elapsed-lastPollElapsed))>15_000L){
            stop("Разрыв наблюдения или изменение часов");return
        }
        if(!runCatching{available()}.getOrDefault(false)){stop("Экран заблокирован или доступ отозван");return}
        if(owned&&!GameLaunchProfiles.ownsAutomatic(context,owner)){stop("Запись возврата изменена вручную");return}
        lastPollElapsed=elapsed;lastWall=wall
        val from=maxOf(confirmedAt,watermark+1,wall-30_000L)
        worker.execute {
            val result=runCatching {
                val manager=requireNotNull(context.getSystemService(UsageStatsManager::class.java))
                val events=requireNotNull(manager.queryEvents(from,wall+1))
                val e=UsageEvents.Event();val collected=mutableListOf<AutomationEvent>()
                var count=0
                while(events.hasNextEvent()) {
                    check(++count<=4096){"Too many events"}
                    events.getNextEvent(e)
                    val signal=when {
                        e.packageName==pkg && e.eventType==UsageEvents.Event.ACTIVITY_RESUMED -> AutomationSignal.TARGET_RESUMED
                        e.packageName==pkg && e.eventType in setOf(UsageEvents.Event.ACTIVITY_PAUSED,UsageEvents.Event.ACTIVITY_STOPPED) -> AutomationSignal.TARGET_EXIT
                        e.packageName!=pkg && e.eventType==UsageEvents.Event.ACTIVITY_RESUMED -> AutomationSignal.OTHER_RESUMED
                        else -> null
                    }
                    if(signal!=null)collected.add(AutomationEvent(e.timeStamp,signal))
                }
                val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temp=battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)?.takeIf{it in -400..1200}
                val thermal=context.getSystemService(PowerManager::class.java)?.currentThermalStatus?.takeIf{it in 0..6}
                Observation(collected,temp,thermal)
            }
            main.post {
                if(stopped)return@post
                if(!runCatching{available()}.getOrDefault(false) || SystemClock.elapsedRealtime()-elapsed>GameAutomationPolicy.MAX_POLL_GAP_MS) {
                    stop("Наблюдение прервано");return@post
                }
                val o=result.getOrElse{stop("События Android недоступны");return@post}
                val decision=GameAutomationPolicy.decide(o.events,confirmedAt,watermark,wall,startedElapsed!=null)
                watermark=maxOf(watermark,o.events.filter{it.wallMs<=wall}.maxOfOrNull{it.wallMs} ?: watermark)
                when(decision){
                    AutomationDecision.APPLY -> {
                        val applied=GameLaunchProfiles.applyAutomatic(context,pkg,owner,approved)
                        owned=GameLaunchProfiles.ownsAutomatic(context,owner)
                        if(!applied.success){stop("Применение отклонено: "+applied.message);return@post}
                        startedElapsed=SystemClock.elapsedRealtime()
                        update("Авто: игра обнаружена. "+applied.message)
                    }
                    AutomationDecision.FINISH -> {stop("Наблюдаемый выход из игры");return@post}
                    else -> Unit
                }
                if(startedElapsed!=null){
                    o.temp?.let{maxTemp=maxOf(maxTemp?:it,it)}
                    o.thermal?.let{maxThermal=maxOf(maxThermal?:it,it)}
                    if(o.temp?.let{it>=450}==true || o.thermal?.let{it>=3}==true) {
                        stop("Тепловое предупреждение: батарея ≥45 °C или Android SEVERE+");return@post
                    }
                }
                main.postDelayed(tick,2000L)
            }
        }
    }
    fun stop(reason:String) {
        if(stopped)return
        stopped=true;main.removeCallbacks(tick);worker.shutdownNow()
        val hadOwner=GameLaunchProfiles.ownsAutomatic(context,owner)
        val restore=if(hadOwner) runCatching{GameLaunchProfiles.restoreAutomatic(context,owner)}
            .getOrElse{GameLaunchResult(false,"Ошибка возврата: "+it.javaClass.simpleName)}
            else GameLaunchResult(true,"Нет принадлежащей авто-сессии записи возврата.")
        val observed=startedElapsed?.let{(SystemClock.elapsedRealtime()-it).coerceAtLeast(0L)/1000L}
        val text="Игра: "+pkg+"\nПричина: "+reason+"\nНаблюдаемое время: "+(observed?.let{"$it с"} ?: "вход не подтверждён")+
            "\nМаксимум температуры батареи: "+(maxTemp?.let{"${it/10.0} °C"} ?: "н/д")+
            "\nThermal status max: "+(maxThermal?.toString() ?: "н/д")+"\n"+restore.message+
            "\nЭто оценка наблюдения Android, не время матча. При убийстве процесса отчёт и автоматический возврат не гарантированы."
        runCatching{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("last",text).commit()}
        finished(if(restore.success)reason else reason+" · нужен ручной возврат.")
    }
}
