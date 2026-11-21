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
    context.applicationContext, "legion-control-history.db", null, DiagnosticHistorySchema.VERSION
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
        installDiagnostics(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == DiagnosticHistorySchema.VERSION) {
            // SQLiteOpenHelper wraps onUpgrade in a transaction. Never rewrite v1 rows.
            installDiagnostics(db)
            return
        }
        // A missing migration MUST fail loudly rather than delete existing rows.
        throw IllegalStateException("No safe migration from $oldVersion to $newVersion")
    }

    private fun installDiagnostics(db: SQLiteDatabase) {
        db.execSQL(DiagnosticHistorySchema.CREATE_TABLE.trimIndent())
        db.execSQL(DiagnosticHistorySchema.CREATE_INDEX)
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

    /** The legacy v1 battery row is inserted first; diagnostics are additive. */
    fun recordDiagnostic(sample: DiagnosticStoredSample): Boolean {
        require(sample.sessionId.isNotBlank() && sample.sessionId.length <= 100)
        require(sample.elapsedMs > 0L && sample.recordedAtMs > 0L)
        require(sample.batteryPercent == null || sample.batteryPercent in 0..100)
        require(sample.thermalStatus == null || sample.thermalStatus in 0..6)
        require(sample.thermalHeadroom == null ||
            (sample.thermalHeadroom.isFinite() && sample.thermalHeadroom >= 0f))
        val values = ContentValues().apply {
            put("session_id", sample.sessionId)
            put("elapsed_ms", sample.elapsedMs)
            put("recorded_at_ms", sample.recordedAtMs)
            putNullable("battery_percent", sample.batteryPercent)
            putNullable("battery_temperature_deci_c", sample.batteryTemperatureDeciC)
            putNullable("thermal_status", sample.thermalStatus)
            if (sample.thermalHeadroom == null) putNull("thermal_headroom")
            else put("thermal_headroom", sample.thermalHeadroom)
            putNullable("charge_counter_micro_ah", sample.chargeCounterMicroAh)
            putNullable("current_now_micro_a", sample.currentNowMicroAmps)
            putNullable("current_average_micro_a", sample.currentAverageMicroAmps)
            if (sample.ramAvailableBytes == null) putNull("ram_available_bytes")
            else put("ram_available_bytes", sample.ramAvailableBytes)
        }
        return writableDatabase.insertWithOnConflict(
            "diagnostic_samples", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    /** Reads only recent accepted samples of this specific Rust/SQLite session. */
    fun recentDiagnostics(sessionId: String, limit: Int = 5): List<DiagnosticStoredSample> {
        require(sessionId.isNotBlank() && limit in 1..20)
        val rows = mutableListOf<DiagnosticStoredSample>()
        val sql = """
            SELECT session_id, elapsed_ms, recorded_at_ms, battery_percent,
                   battery_temperature_deci_c, thermal_status, thermal_headroom,
                   charge_counter_micro_ah, current_now_micro_a,
                   current_average_micro_a, ram_available_bytes
            FROM diagnostic_samples
            WHERE session_id = ?
            ORDER BY elapsed_ms DESC
            LIMIT ?
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(sessionId, limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                rows += DiagnosticStoredSample(
                    sessionId = cursor.getString(0),
                    elapsedMs = cursor.getLong(1),
                    recordedAtMs = cursor.getLong(2),
                    batteryPercent = if (cursor.isNull(3)) null else cursor.getInt(3),
                    batteryTemperatureDeciC =
                        if (cursor.isNull(4)) null else cursor.getInt(4),
                    thermalStatus = if (cursor.isNull(5)) null else cursor.getInt(5),
                    thermalHeadroom = if (cursor.isNull(6)) null else cursor.getFloat(6),
                    chargeCounterMicroAh = if (cursor.isNull(7)) null else cursor.getInt(7),
                    currentNowMicroAmps = if (cursor.isNull(8)) null else cursor.getInt(8),
                    currentAverageMicroAmps =
                        if (cursor.isNull(9)) null else cursor.getInt(9),
                    ramAvailableBytes = if (cursor.isNull(10)) null else cursor.getLong(10)
                )
            }
        }
        return rows.asReversed()
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

    /**
     * Stream every legacy row, joining optional v2 telemetry on its immutable
     * session_id + elapsed_ms key. Older rows deliberately retain blank fields.
     * The join is one-to-zero-or-one, never duplicating existing measurements.
     */
    fun writeCsv(writer: Writer): Int {
        CsvReport.beginExtended(writer)
        var count = 0
        val sql = """
            SELECT s.session_id, s.recorded_at_ms, s.elapsed_ms, s.percent,
                   s.temperature_deci_c, s.charging_status, s.charging_source,
                   s.voltage_mv, s.current_ua, s.power_saving, s.refresh_hz,
                   s.width_px, s.height_px,
                   d.session_id, d.elapsed_ms, d.recorded_at_ms,
                   d.battery_percent, d.battery_temperature_deci_c,
                   d.thermal_status, d.thermal_headroom, d.charge_counter_micro_ah,
                   d.current_now_micro_a, d.current_average_micro_a,
                   d.ram_available_bytes
            FROM samples AS s
            LEFT JOIN diagnostic_samples AS d
                ON d.session_id = s.session_id AND d.elapsed_ms = s.elapsed_ms
            ORDER BY s.recorded_at_ms ASC, s.id ASC
        """.trimIndent()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                CsvReport.appendExtended(
                    writer, cursor.asStoredReading(), cursor.asDiagnosticStoredSampleOrNull()
                )
                count++
            }
        }
        writer.flush()
        return count
    }

    private fun Cursor.asDiagnosticStoredSampleOrNull(): DiagnosticStoredSample? {
        // A LEFT JOIN with no matching diagnostic yields SQL NULL for all d.* columns.
        if (isNull(13)) return null
        return DiagnosticStoredSample(
            sessionId = getString(13),
            elapsedMs = getLong(14),
            recordedAtMs = getLong(15),
            batteryPercent = if (isNull(16)) null else getInt(16),
            batteryTemperatureDeciC = if (isNull(17)) null else getInt(17),
            thermalStatus = if (isNull(18)) null else getInt(18),
            thermalHeadroom = if (isNull(19)) null else getFloat(19),
            chargeCounterMicroAh = if (isNull(20)) null else getInt(20),
            currentNowMicroAmps = if (isNull(21)) null else getInt(21),
            currentAverageMicroAmps = if (isNull(22)) null else getInt(22),
            ramAvailableBytes = if (isNull(23)) null else getLong(23)
        )
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
