package dev.lolokeksu.legioncontrol

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Locale

data class GameUseResult(
    val packageName: String,
    val observedStarts: Int,
    val matchedForegroundMs: Long,
    val incomplete: Boolean
)

/** Event window only; it cannot reconstruct sessions before the access grant. */
object GameUsageDiagnostics {
    const val DEFAULT_PACKAGE = "com.riotgames.league.wildrift"

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun read(context: Context, gamePackage: String): String {
        if (!hasUsageAccess(context)) return "GAME SESSION TRACKER · требуется специальный доступ к статистике использования. Включается вручную в настройках Android."
        val pkg = gamePackage.trim()
        if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(pkg))
            return "GAME SESSION TRACKER · неверное имя пакета"
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return "GAME SESSION TRACKER · сервис Android недоступен"
        val now = System.currentTimeMillis()
        val from = now - 24L*60*60*1000
        val events = manager.queryEvents(from, now)
            ?: return "GAME SESSION TRACKER · Android не вернул события"
        val event = UsageEvents.Event()
        var resumed: Long? = null
        var observedStarts = 0
        var total = 0L
        var incomplete = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != pkg) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (resumed != null) incomplete = true
                    resumed = event.timeStamp
                    observedStarts++
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    resumed?.let { started ->
                        total += (event.timeStamp - started).coerceAtLeast(0L)
                    }
                    resumed = null
                }
            }
        }
        if (resumed != null) {
            total += (now - resumed).coerceAtLeast(0L)
            incomplete = true
        }
        return format(GameUseResult(pkg, observedStarts, total, incomplete))
    }

    fun format(r: GameUseResult): String = buildString {
        append("GAME SESSION TRACKER · наблюдаемые события за 24 часа\n")
        append("Пакет: ").append(r.packageName).append("\n")
        append("Событий входа в foreground: ").append(r.observedStarts).append("\n")
        append("Сумма сопоставленных интервалов: ").append(String.format(
            Locale.US, "%.1f мин", r.matchedForegroundMs/60000.0
        )).append("\n")
        if (r.incomplete) append("Некоторые интервалы неполные или игра осталась активной.\n")
        append("Данные могут быть неполными из-за версии Android и разрешений; не игровая статистика матчей.")
    }
}
