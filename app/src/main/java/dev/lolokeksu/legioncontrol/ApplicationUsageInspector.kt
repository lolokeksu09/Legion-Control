package dev.lolokeksu.legioncontrol

import android.app.usage.UsageStatsManager
import android.content.Context
import java.text.DateFormat
import java.util.Date

/** Optional Android Usage Access; totals are coarse OS aggregates, not match duration. */
object ApplicationUsageInspector {
    fun report(context:Context,pkg:String,days:Int):String {
        require(days==1 || days==7)
        require(ApplicationInventory.isPackageName(pkg))
        if(!GameUsageDiagnostics.hasUsageAccess(context))
            return "Статистика использования недоступна. Android требует специальный "+
                "доступ Usage Access; включается пользователем в настройках. "+
                "Без него Legion Control не отслеживает чужие приложения."
        val stats=context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return "UsageStatsManager недоступен на устройстве."
        val end=System.currentTimeMillis()
        val start=end-days*86_400_000L
        return runCatching {
            val result=stats.queryAndAggregateUsageStats(start,end)[pkg]
            if(result==null) "Android не вернул данные для $pkg за $days дн. "+
                "Это НЕ доказательство отсутствия запусков."
            else buildString {
                append("СТАТИСТИКА ANDROID · ").append(pkg).append("\n")
                append("Окно: последние ").append(days).append(" дн.\n")
                append("Время в foreground по UsageStatsManager: ")
                    .append(ApplicationAuditRules.humanDuration(
                        result.totalTimeInForeground.takeIf { it>=0 })).append("\n")
                append("Последнее использование (метаданные Android): ")
                    .append(result.lastTimeUsed.takeIf { it>0 }?.let {
                        DateFormat.getDateTimeInstance().format(Date(it))
                    } ?: "н/д").append("\n")
                append("Это системная агрегированная статистика, не FPS и не часы "+
                    "конкретных игровых матчей; прошивка может предоставлять неполные данные.")
            }
        }.getOrElse { "Android не предоставил статистику: "+it.javaClass.simpleName }
    }
}
