package dev.lolokeksu.legioncontrol

import android.content.Context

data class SmartInsightsResult(
    val packageName:String, val days:Int, val sessions:Int,
    val completedSessions:Int, val totalCompletedMinutes:Long,
    val sampleCount:Long, val peakBatteryTempDeciC:Int?,
    val severeReadings:Long, val unvalidatedReadings:Long,
    val thermalSampleCount:Long = sampleCount,
    val networkSampleCount:Long = sampleCount,
    val interruptedSessions:Int = 0
) {
    fun textReport(): String = SmartInsightsPresentation.report(this)
}

/** SQL aggregates avoid loading thousands of history samples into app memory. */
object SmartSessionInsights {
    fun summarize(context: Context, pkg: String, days: Int, now: Long = System.currentTimeMillis()): SmartInsightsResult {
        require(days in listOf(1,7,30))
        val since=(now-days.toLong()*86_400_000L).coerceAtLeast(0)
        LocalHistoryStore(context).use { db ->
            val games=db.readableDatabase
            var all=0
            var completed=0
            var interrupted=0
            var durationMs=0L
            games.rawQuery("""
                SELECT COUNT(*),
                  COALESCE(SUM(CASE WHEN ended_at_ms IS NOT NULL AND end_reason='USER_STOP' THEN 1 ELSE 0 END),0),
                  COALESCE(SUM(CASE WHEN ended_at_ms IS NOT NULL AND end_reason='USER_STOP' THEN
                    MAX(0,ended_at_ms-started_at_ms) ELSE 0 END),0),
                  COALESCE(SUM(CASE WHEN ended_at_ms IS NOT NULL AND
                    (end_reason IS NULL OR end_reason<>'USER_STOP') THEN 1 ELSE 0 END),0)
                FROM game_sessions WHERE package_name=? AND started_at_ms>=?
            """.trimIndent(),arrayOf(pkg,since.toString())).use { c ->
                if(c.moveToFirst()) {
                    all=c.getInt(0);completed=c.getInt(1);durationMs=c.getLong(2);interrupted=c.getInt(3)
                }
            }
            var count=0L
            var peak:Int?=null
            var severe=0L
            var unvalidated=0L
            var thermalCount=0L
            var networkCount=0L
            games.rawQuery("""
                SELECT COUNT(*),MAX(g.battery_temp_deci_c),
                  COALESCE(SUM(CASE WHEN g.thermal_status>=3 THEN 1 ELSE 0 END),0),
                  COALESCE(SUM(CASE WHEN g.network_validated=0 THEN 1 ELSE 0 END),0),
                  COUNT(g.thermal_status),COUNT(g.network_validated)
                FROM game_samples g JOIN game_sessions s ON s.session_id=g.session_id
                WHERE s.package_name=? AND s.started_at_ms>=?
            """.trimIndent(),arrayOf(pkg,since.toString())).use { c ->
                if(c.moveToFirst()) {
                    count=c.getLong(0)
                    peak=if(c.isNull(1))null else c.getInt(1)
                    severe=c.getLong(2)
                    unvalidated=c.getLong(3)
                    thermalCount=c.getLong(4)
                    networkCount=c.getLong(5)
                }
            }
            return SmartInsightsResult(pkg,days,all,completed,durationMs/60_000L,count,peak,severe,
                unvalidated,thermalCount,networkCount,interrupted)
        }
    }
}
