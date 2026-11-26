package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Writer
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.BufferedWriter

/**
 * An opt-in recorder sharing LocalHistoryStore v6. No cloud, pruning or other-app files.
 * The caller owns a worker thread; no database call is safe on the main thread.
 */
class GameSessionRepository(context: Context) : AutoCloseable {
    private val dbHelper = LocalHistoryStore(context.applicationContext)

    fun start(packageName: String, label: String, tcpEnabled: Boolean, now: Long = System.currentTimeMillis()): String {
        require(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(packageName))
        require(now > 0)
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE game_sessions SET ended_at_ms=?, end_reason='INTERRUPTED' WHERE ended_at_ms IS NULL",
                arrayOf(now)
            )
            val id = UUID.randomUUID().toString()
            db.insertOrThrow("game_sessions",null,ContentValues().apply {
                put("session_id",id)
                put("package_name",packageName.take(200))
                put("app_label",label.take(100).ifBlank { packageName.take(100) })
                put("started_at_ms",now)
                put("tcp_opt_in",if(tcpEnabled) 1 else 0)
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun finish(id: String, reason: String, now: Long = System.currentTimeMillis()) {
        val db = dbHelper.writableDatabase
        val session = header(id) ?: return
        db.update("game_sessions",ContentValues().apply {
            put("ended_at_ms",maxOf(now,session.startedAtMs))
            put("end_reason",reason.take(60))
        },"session_id=? AND ended_at_ms IS NULL",arrayOf(id))
    }

    fun record(id: String, p: GameTelemetryPoint) {
        dbHelper.writableDatabase.insertOrThrow("game_samples",null,ContentValues().apply {
            put("session_id",id)
            put("recorded_at_ms",p.timeMs)
            p.batteryPercent?.let { put("battery_percent",it) }
            p.batteryTempDeciC?.let { put("battery_temp_deci_c",it) }
            p.currentMicroA?.let { put("battery_current_ua",it) }
            p.thermalStatus?.let { put("thermal_status",it) }
            p.powerSaving?.let { put("power_saving",if(it) 1 else 0) }
            p.refreshHz?.let { put("refresh_hz",it) }
            put("network_type",p.networkType.take(40))
            p.networkValidated?.let { put("network_validated",if(it) 1 else 0) }
        })
    }

    fun event(id: String, kind: String, detail: String, now: Long = System.currentTimeMillis()) {
        dbHelper.writableDatabase.insertOrThrow("game_events",null,ContentValues().apply {
            put("session_id",id)
            put("recorded_at_ms",now)
            put("event_type",kind.take(40))
            put("details",detail.take(300))
        })
    }

    fun header(id: String): GameSessionHeader? =
        dbHelper.readableDatabase.rawQuery(
            "SELECT session_id,package_name,app_label,started_at_ms,ended_at_ms,end_reason,tcp_opt_in FROM game_sessions WHERE session_id=?",
            arrayOf(id)
        ).use { c ->
            if (!c.moveToFirst()) null else GameSessionHeader(c.getString(0),c.getString(1),c.getString(2),
                c.getLong(3), if(c.isNull(4)) null else c.getLong(4),
                if(c.isNull(5)) null else c.getString(5),c.getInt(6)==1)
        }

    fun recent(limit: Int = 12): List<GameSessionHeader> {
        val out = mutableListOf<GameSessionHeader>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT session_id,package_name,app_label,started_at_ms,ended_at_ms,end_reason,tcp_opt_in FROM game_sessions ORDER BY started_at_ms DESC LIMIT ?",
            arrayOf(limit.coerceIn(1,50).toString())
        ).use { c ->
            while(c.moveToNext()) out += GameSessionHeader(c.getString(0),c.getString(1),c.getString(2),
                c.getLong(3),if(c.isNull(4))null else c.getLong(4),
                if(c.isNull(5))null else c.getString(5),c.getInt(6)==1)
        }
        return out
    }

    /** Stable, parameterized paging over local history, including unfinished records. */
    fun libraryPage(filter:LibraryFilter,now:Long,cursor:LibraryCursor?=null):LibraryPage {
        val query=SessionLibraryPolicy.query(filter,now,cursor)
        val out=mutableListOf<GameSessionHeader>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT session_id,package_name,app_label,started_at_ms,ended_at_ms,end_reason,tcp_opt_in FROM game_sessions"+
                query.where+" ORDER BY started_at_ms DESC,session_id DESC LIMIT ?",
            (query.args+(SessionLibraryPolicy.PAGE_SIZE+1).toString()).toTypedArray()
        ).use { c ->
            while(c.moveToNext())out+=GameSessionHeader(c.getString(0),c.getString(1),c.getString(2),
                c.getLong(3),if(c.isNull(4))null else c.getLong(4),if(c.isNull(5))null else c.getString(5),c.getInt(6)==1)
        }
        return SessionLibraryPolicy.page(out)
    }

    /** Freeze a selected, completed record before offering an export. */
    fun libraryDetail(id:String):LibraryDetail = readSessionDetail(id, allowOpen = false)

    internal fun analyticsDetail(id: String): LibraryDetail = readSessionDetail(id, allowOpen = true)

    private fun readSessionDetail(id: String, allowOpen: Boolean): LibraryDetail {
        val h = header(id) ?: error("Session unavailable")
        require(allowOpen || h.endedAtMs != null) { "Session has no completion record" }
        val samples=readPoints(id,SessionLibraryPolicy.MAX_SAMPLES+1)
        val events=readEventsChronological(id,SessionLibraryPolicy.MAX_EVENTS+1)
        return LibraryDetail(h,samples.take(SessionLibraryPolicy.MAX_SAMPLES),
            events.take(SessionLibraryPolicy.MAX_EVENTS),samples.size>SessionLibraryPolicy.MAX_SAMPLES,
            events.size>SessionLibraryPolicy.MAX_EVENTS)
    }

    /** Exact package history, not last-N-global. Safe for baseline comparisons. */
    fun recentForPackage(packageName:String, limit:Int=25):List<GameSessionHeader> {
        require(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(packageName))
        val out=mutableListOf<GameSessionHeader>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT session_id,package_name,app_label,started_at_ms,ended_at_ms,end_reason,tcp_opt_in " +
                "FROM game_sessions WHERE package_name=? " +
                "ORDER BY started_at_ms DESC,session_id DESC LIMIT ?",
            arrayOf(packageName,limit.coerceIn(1,25).toString())
        ).use { c ->
            while(c.moveToNext()) out += GameSessionHeader(
                c.getString(0),c.getString(1),c.getString(2),c.getLong(3),
                if(c.isNull(4))null else c.getLong(4),
                if(c.isNull(5))null else c.getString(5),c.getInt(6)==1)
        }
        return out
    }

    /** Unique historical package IDs; no PACKAGE_USAGE_STATS or broad package permission. */
    fun knownSessionPackages(limit:Int=40):List<String> {
        val out=mutableListOf<String>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT package_name FROM game_sessions GROUP BY package_name " +
                "ORDER BY MAX(started_at_ms) DESC LIMIT ?",
            arrayOf(limit.coerceIn(1,40).toString())
        ).use { c ->
            while(c.moveToNext()) out += c.getString(0)
        }
        return out
    }

    fun points(id: String, limit: Int = 10000): List<GameTelemetryPoint> =
        readPoints(id, limit.coerceIn(1,10000))

    private fun readPoints(id: String, limit: Int): List<GameTelemetryPoint> {
        val out = mutableListOf<GameTelemetryPoint>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT recorded_at_ms,battery_percent,battery_temp_deci_c,battery_current_ua,thermal_status,power_saving,refresh_hz,network_type,network_validated FROM game_samples WHERE session_id=? ORDER BY recorded_at_ms ASC,id ASC LIMIT ?",
            arrayOf(id,limit.coerceIn(1,SessionLibraryPolicy.MAX_SAMPLES+1).toString())
        ).use { c ->
            while(c.moveToNext()) out += GameTelemetryPoint(
                c.getLong(0),if(c.isNull(1))null else c.getInt(1),
                if(c.isNull(2))null else c.getInt(2),if(c.isNull(3))null else c.getInt(3),
                if(c.isNull(4))null else c.getInt(4),if(c.isNull(5))null else c.getInt(5)==1,
                if(c.isNull(6))null else c.getFloat(6),c.getString(7),
                if(c.isNull(8))null else c.getInt(8)==1)
        }
        return out
    }

    fun events(id: String, limit: Int = 15): List<GameSessionEvent> {
        val out = mutableListOf<GameSessionEvent>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT recorded_at_ms,event_type,details FROM game_events WHERE session_id=? ORDER BY recorded_at_ms DESC,id DESC LIMIT ?",
            arrayOf(id,limit.coerceIn(1,100).toString())
        ).use { c ->
            while(c.moveToNext()) out += GameSessionEvent(c.getLong(0),c.getString(1),c.getString(2))
        }
        return out
    }

    /** Chronological, bounded events for the offline signal timeline; no schema changes. */
    fun eventsChronological(id: String, limit: Int = 2000): List<GameSessionEvent> =
        readEventsChronological(id, limit.coerceIn(1,2000))

    private fun readEventsChronological(id: String, limit: Int): List<GameSessionEvent> {
        val out = mutableListOf<GameSessionEvent>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT recorded_at_ms,event_type,details FROM game_events WHERE session_id=? " +
                "ORDER BY recorded_at_ms ASC,id ASC LIMIT ?",
            arrayOf(id, limit.coerceIn(1,SessionLibraryPolicy.MAX_EVENTS+1).toString())
        ).use { c ->
            while(c.moveToNext()) out += GameSessionEvent(c.getLong(0),c.getString(1),c.getString(2))
        }
        return out
    }

    fun writeCsv(writer: Writer, kind: String) {
        val (header,query) = when(kind) {
            "sessions" -> "session_id,package_name,app_label,started_at_ms,ended_at_ms,end_reason,tcp_opt_in" to
                "SELECT session_id,package_name,app_label,started_at_ms,ended_at_ms,end_reason,tcp_opt_in FROM game_sessions ORDER BY started_at_ms"
            "samples" -> "session_id,recorded_at_ms,battery_percent,battery_temp_deci_c,battery_current_ua,thermal_status,power_saving,refresh_hz,network_type,network_validated" to
                "SELECT session_id,recorded_at_ms,battery_percent,battery_temp_deci_c,battery_current_ua,thermal_status,power_saving,refresh_hz,network_type,network_validated FROM game_samples ORDER BY id"
            "events" -> "session_id,recorded_at_ms,event_type,details" to
                "SELECT session_id,recorded_at_ms,event_type,details FROM game_events ORDER BY id"
            else -> error("Unknown CSV type")
        }
        writer.write(header + "\n")
        dbHelper.readableDatabase.rawQuery(query,null).use { c ->
            while(c.moveToNext()) {
                val line = (0 until c.columnCount).joinToString(",") { col ->
                    if(c.isNull(col)) "" else csv(c.getString(col))
                }
                writer.write(line + "\n")
            }
        }
    }

    fun exportZip(stream: OutputStream) {
        ZipOutputStream(stream).use { zip ->
            for (kind in listOf("sessions","samples","events")) {
                zip.putNextEntry(ZipEntry("game-$kind.csv"))
                val writer = BufferedWriter(OutputStreamWriter(zip,Charsets.UTF_8))
                writeCsv(writer,kind)
                writer.flush() // Do not close writer: it would close ZIP prematurely.
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(("Legion Control · Game Sessions\n"+
                "All data is local and exported only by explicit user action.\n"+
                "Temperature = battery, deci-C. Current = Android battery microampere.\n"+
                "Thermal is Android status, not CPU/GPU temperature.\n"+
                "Network validation is not in-game ping; TCP tests are opt-in.\n"+
                "Unknown fields are empty. No FPS or gaming performance measurements.\n").toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun csv(value: String): String = CsvEscaper.cell(value)

    override fun close() { dbHelper.close() }
}
