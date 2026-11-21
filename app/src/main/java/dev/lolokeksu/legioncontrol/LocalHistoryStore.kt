package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Writer

/**
 * Private SQLite persistence, independent from Rust's ephemeral 64-sample buffer.
 * No storage permission, cloud sync, destructive migration or silent pruning.
 */
class LocalHistoryStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "legion-control-history.db", null, 1
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sessions (
                id TEXT PRIMARY KEY NOT NULL,
                started_at_ms INTEGER NOT NULL,
                ended_at_ms INTEGER
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL REFERENCES sessions(id),
                recorded_at_ms INTEGER NOT NULL,
                elapsed_ms INTEGER NOT NULL,
                percent INTEGER NOT NULL CHECK(percent BETWEEN 0 AND 100),
                temperature_deci_c INTEGER NOT NULL,
                charging_status TEXT NOT NULL,
                charging_source TEXT NOT NULL,
                voltage_mv INTEGER,
                current_ua INTEGER,
                power_saving INTEGER,
                refresh_hz REAL,
                width_px INTEGER,
                height_px INTEGER,
                UNIQUE(session_id, elapsed_ms)
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_samples_session ON samples(session_id, recorded_at_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // A missing migration MUST fail loudly, never erase history via DROP TABLE.
        throw IllegalStateException("No safe migration from $oldVersion to $newVersion")
    }

    fun record(reading: StoredReading): Boolean {
        require(reading.sessionId.isNotBlank() && reading.sessionId.length <= 100)
        require(reading.recordedAtMs > 0 && reading.elapsedMs > 0)
        require(reading.percent in 0..100)
        require(reading.temperatureDeciC in -400..1200)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val newSession = ContentValues().apply {
                put("id", reading.sessionId)
                put("started_at_ms", reading.recordedAtMs)
            }
            db.insertWithOnConflict("sessions", null, newSession, SQLiteDatabase.CONFLICT_IGNORE)
            val values = ContentValues().apply {
                put("session_id", reading.sessionId)
                put("recorded_at_ms", reading.recordedAtMs)
                put("elapsed_ms", reading.elapsedMs)
                put("percent", reading.percent)
                put("temperature_deci_c", reading.temperatureDeciC)
                put("charging_status", reading.chargingStatus)
                put("charging_source", reading.chargingSource)
                putNullable("voltage_mv", reading.voltageMv)
                putNullable("current_ua", reading.currentUa)
                putNullable("power_saving", reading.powerSaving?.let { if (it) 1 else 0 })
                if (reading.refreshRateHz == null) putNull("refresh_hz")
                else put("refresh_hz", reading.refreshRateHz)
                putNullable("width_px", reading.screenWidthPx)
                putNullable("height_px", reading.screenHeightPx)
            }
            val id = db.insertWithOnConflict(
                "samples", null, values, SQLiteDatabase.CONFLICT_IGNORE
            )
            db.setTransactionSuccessful()
            return id != -1L
        } finally {
            db.endTransaction()
        }
    }

    fun endSession(sessionId: String, endedAtMs: Long) {
        require(sessionId.isNotBlank() && endedAtMs > 0)
        val values = ContentValues().apply { put("ended_at_ms", endedAtMs) }
        writableDatabase.update("sessions", values, "id=?", arrayOf(sessionId))
    }

    fun count(): Long = DatabaseUtils.longForQuery(
        readableDatabase, "SELECT COUNT(*) FROM samples", null
    )

    fun recentSessions(limit: Int = 12): List<SavedSession> {
        require(limit in 1..100)
        val result = mutableListOf<SavedSession>()
        val sql = """
            SELECT s.id, s.started_at_ms,
                   COALESCE(MAX(m.recorded_at_ms), s.started_at_ms) AS last_at,
                   s.ended_at_ms, COUNT(m.id) AS sample_count
            FROM sessions s LEFT JOIN samples m ON m.session_id = s.id
            GROUP BY s.id
            ORDER BY s.started_at_ms DESC, s.rowid DESC
            LIMIT ?
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) {
                result += SavedSession(
                    id = c.getString(0),
                    startedAtMs = c.getLong(1),
                    lastAtMs = c.getLong(2),
                    endedAtMs = if (c.isNull(3)) null else c.getLong(3),
                    samples = c.getInt(4)
                )
            }
        }
        return result
    }

    /** Export incrementally; even large histories are not copied into app RAM. */
    fun writeCsv(writer: Writer): Int {
        CsvReport.begin(writer)
        var count = 0
        val sql = """
            SELECT session_id, recorded_at_ms, elapsed_ms, percent,
                   temperature_deci_c, charging_status, charging_source,
                   voltage_mv, current_ua, power_saving, refresh_hz,
                   width_px, height_px
            FROM samples ORDER BY recorded_at_ms ASC, id ASC
        """.trimIndent()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                CsvReport.append(writer, cursor.asStoredReading())
                count++
            }
        }
        writer.flush()
        return count
    }

    private fun Cursor.asStoredReading(): StoredReading = StoredReading(
        sessionId = getString(0),
        recordedAtMs = getLong(1),
        elapsedMs = getLong(2),
        percent = getInt(3),
        temperatureDeciC = getInt(4),
        chargingStatus = getString(5),
        chargingSource = getString(6),
        voltageMv = if (isNull(7)) null else getInt(7),
        currentUa = if (isNull(8)) null else getInt(8),
        powerSaving = if (isNull(9)) null else getInt(9) != 0,
        refreshRateHz = if (isNull(10)) null else getFloat(10),
        screenWidthPx = if (isNull(11)) null else getInt(11),
        screenHeightPx = if (isNull(12)) null else getInt(12)
    )

    private fun ContentValues.putNullable(key: String, value: Int?) {
        if (value == null) putNull(key) else put(key, value)
    }
}
