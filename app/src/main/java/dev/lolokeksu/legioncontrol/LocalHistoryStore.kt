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
    context.applicationContext, "legion-control-history.db", null, ExperimentSchema.VERSION
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
        installSensors(db)
        installSensorAttempts(db)
        installTimedBatteryTests(db)
        GameSessionSchema.install(db)
        SmartProfilesSchema.install(db)
        ExperimentSchema.install(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (newVersion != ExperimentSchema.VERSION || oldVersion !in 1..7) {
            throw IllegalStateException("No safe migration from $oldVersion to $newVersion")
        }
        // SQLiteOpenHelper wraps upgrades in its own transaction. These are
        // additive DDL statements only: every historical row remains intact.
        if (oldVersion == 1) installDiagnostics(db)
        if (oldVersion <= 2) installSensors(db)
        if (oldVersion <= 3) installSensorAttempts(db)
        if (oldVersion <= 4) installTimedBatteryTests(db)
        if (oldVersion <= 5) GameSessionSchema.install(db)
        if (oldVersion <= 6) SmartProfilesSchema.install(db)
        ExperimentSchema.install(db)
    }

    private fun installDiagnostics(db: SQLiteDatabase) {
        db.execSQL(DiagnosticHistorySchema.CREATE_TABLE.trimIndent())
        db.execSQL(DiagnosticHistorySchema.CREATE_INDEX)
    }

    private fun installSensors(db: SQLiteDatabase) {
        db.execSQL(SensorHistorySchema.CREATE_TABLE.trimIndent())
        db.execSQL(SensorHistorySchema.CREATE_INDEX)
    }

    private fun installSensorAttempts(db: SQLiteDatabase) {
        db.execSQL(SensorDiagnosticsSchema.CREATE_TABLE.trimIndent())
        db.execSQL(SensorDiagnosticsSchema.CREATE_INDEX)
    }

    private fun installTimedBatteryTests(db: SQLiteDatabase) {
        db.execSQL(TimedBatterySchema.CREATE_TABLE.trimIndent())
        db.execSQL(TimedBatterySchema.CREATE_INDEX)
    }

    /** A completed foreground test is committed after its queued manual samples. */
    fun recordTimedBatteryTest(test: TimedBatteryTestResult): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Allows a 0-point test without adding a fictitious battery reading.
            db.insertWithOnConflict(
                "sessions", null,
                ContentValues().apply {
                    put("id", test.sessionId)
                    put("started_at_ms", test.startedAtMs)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
            val firstAndLast = mutableListOf<Pair<Int, Int>>()
            db.rawQuery(
                """SELECT percent,temperature_deci_c FROM samples
                   WHERE session_id=? ORDER BY elapsed_ms ASC,id ASC""",
                arrayOf(test.sessionId)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    firstAndLast += cursor.getInt(0) to cursor.getInt(1)
                }
            }
            val temperatures = firstAndLast.map { it.second }
            val thermalCode = db.rawQuery(
                """SELECT MAX(thermal_status) FROM diagnostic_samples WHERE session_id=?""",
                arrayOf(test.sessionId)
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0)
                else null
            }
            val values = ContentValues().apply {
                put("test_id", test.testId)
                put("session_id", test.sessionId)
                put("started_at_ms", test.startedAtMs)
                put("finished_at_ms", test.finishedAtMs)
                put("reason", test.reason.name)
                put("attempted_reads", test.attemptedReads)
                // Real committed SQLite rows, not merely a successful Rust return.
                put("successful_reads", firstAndLast.size.coerceAtMost(test.attemptedReads))
                if (firstAndLast.isNotEmpty()) {
                    put("first_percent", firstAndLast.first().first)
                    put("last_percent", firstAndLast.last().first)
                    put("min_temp_deci_c", temperatures.min())
                    put("max_temp_deci_c", temperatures.max())
                } else {
                    putNull("first_percent")
                    putNull("last_percent")
                    putNull("min_temp_deci_c")
                    putNull("max_temp_deci_c")
                }
                if (thermalCode == null) putNull("max_thermal_status")
                else put("max_thermal_status", thermalCode)
            }
            val rowId = db.insertWithOnConflict(
                "timed_battery_tests", null, values, SQLiteDatabase.CONFLICT_IGNORE
            )
            db.update(
                "sessions",
                ContentValues().apply { put("ended_at_ms", test.finishedAtMs) },
                "id=?", arrayOf(test.sessionId)
            )
            db.setTransactionSuccessful()
            return rowId != -1L
        } finally {
            db.endTransaction()
        }
    }

    fun recentTimedBatteryTests(limit: Int = 12): List<SavedTimedBatteryTest> {
        require(limit in 1..100)
        val result = mutableListOf<SavedTimedBatteryTest>()
        readableDatabase.rawQuery(
            """SELECT test_id,started_at_ms,finished_at_ms,reason,
                      attempted_reads,successful_reads,first_percent,last_percent,
                      min_temp_deci_c,max_temp_deci_c,max_thermal_status
               FROM timed_battery_tests ORDER BY finished_at_ms DESC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result += SavedTimedBatteryTest(
                testId = cursor.getString(0),
                startedAtMs = cursor.getLong(1),
                finishedAtMs = cursor.getLong(2),
                reason = TimedBatteryStopReason.valueOf(cursor.getString(3)),
                attemptedReads = cursor.getInt(4),
                successfulReads = cursor.getInt(5),
                firstBatteryPercent = if (cursor.isNull(6)) null else cursor.getInt(6),
                lastBatteryPercent = if (cursor.isNull(7)) null else cursor.getInt(7),
                minTemperatureDeciC = if (cursor.isNull(8)) null else cursor.getInt(8),
                maxTemperatureDeciC = if (cursor.isNull(9)) null else cursor.getInt(9),
                maxThermalStatus = if (cursor.isNull(10)) null else cursor.getInt(10)
            )
        }
        return result
    }

    fun writeTimedBatteryTestsCsv(writer: Writer): Int = streamDiagnosticCsv(
        writer,
        listOf(
            "test_id","session_id","started_at_ms","finished_at_ms",
            "reason","attempted_reads","successful_reads","first_percent",
            "last_percent","min_temp_deci_c","max_temp_deci_c","max_thermal_status"
        ),
        """SELECT test_id,session_id,started_at_ms,finished_at_ms,reason,
                  attempted_reads,successful_reads,first_percent,last_percent,
                  min_temp_deci_c,max_temp_deci_c,max_thermal_status
           FROM timed_battery_tests ORDER BY finished_at_ms ASC"""
    )

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

    /**
     * Atomic insert of accepted sensor values AND unsuccessful attempts.
     * Duplicate capture IDs remain idempotent; no fabricated sample rows.
     */
    fun recordSensorCapture(capture: SensorCaptureResult): Int {
        require(capture.captureId.isNotBlank() && capture.captureId.length <= 100)
        require(capture.recordedAtMs > 0L)
        require(capture.outcomes.map { it.kind }.distinct().size == capture.outcomes.size)
        capture.outcomes.forEach { outcome ->
            val sample = outcome.sample ?: return@forEach
            require(sample.eventTimestampNs > 0L && sample.recordedAtMs > 0L)
            require(sample.accuracy in -1..3)
            require(sample.values.size == sample.kind.dimensions)
            require(sample.values.all { it.isFinite() && kotlin.math.abs(it) <= 1_000_000f })
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            var count = 0
            for (outcome in capture.outcomes) {
                outcome.sample?.let { sample ->
                    val values = ContentValues().apply {
                        put("capture_id", capture.captureId)
                        put("kind", sample.kind.type)
                        put("recorded_at_ms", sample.recordedAtMs)
                        put("event_timestamp_ns", sample.eventTimestampNs)
                        put("accuracy", sample.accuracy)
                        put("x", sample.values[0])
                        if (sample.kind.dimensions == 3) {
                            put("y", sample.values[1])
                            put("z", sample.values[2])
                        } else {
                            putNull("y")
                            putNull("z")
                        }
                    }
                    if (db.insertWithOnConflict(
                            "sensor_samples", null, values, SQLiteDatabase.CONFLICT_IGNORE
                        ) != -1L) count++
                }
                val attempt = ContentValues().apply {
                    put("capture_id", capture.captureId)
                    put("kind", outcome.kind.type)
                    put("recorded_at_ms", capture.recordedAtMs)
                    put("state", outcome.state.name)
                    put("event_count", outcome.eventCount)
                    put("rejected_events", outcome.rejectedEvents)
                    if (outcome.latencyMs == null) putNull("latency_ms")
                    else put("latency_ms", outcome.latencyMs)
                    put("timeout_ms", outcome.timeoutMs)
                    if (outcome.reportingMode == null) putNull("reporting_mode")
                    else put("reporting_mode", outcome.reportingMode)
                    if (outcome.sensorName == null) putNull("sensor_name")
                    else put("sensor_name", outcome.sensorName.take(80))
                    if (outcome.rejectionReason == null) putNull("rejection_reason")
                    else put("rejection_reason", outcome.rejectionReason.name)
                }
                db.insertWithOnConflict(
                    "sensor_probe_attempts", null, attempt, SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            db.setTransactionSuccessful()
            return count
        } finally {
            db.endTransaction()
        }
    }

    /** Last attempted capture for each sensor, including failed and missing reads. */
    fun lastSensorAttempts(): Map<SensorKind, SavedSensorAttempt> {
        val last = mutableMapOf<SensorKind, SavedSensorAttempt>()
        readableDatabase.rawQuery(
            """SELECT kind,state,recorded_at_ms,event_count,rejected_events,
               latency_ms,timeout_ms,reporting_mode,sensor_name,rejection_reason
               FROM sensor_probe_attempts ORDER BY id DESC""", null
        ).use { cursor ->
            while (cursor.moveToNext() && last.size < SensorKind.entries.size) {
                val kind = SensorKind.fromType(cursor.getInt(0)) ?: continue
                if (kind in last) continue
                last[kind] = SavedSensorAttempt(
                    kind = kind,
                    state = SensorProbeState.valueOf(cursor.getString(1)),
                    recordedAtMs = cursor.getLong(2),
                    eventCount = cursor.getInt(3),
                    rejectedEvents = cursor.getInt(4),
                    latencyMs = if (cursor.isNull(5)) null else cursor.getLong(5),
                    timeoutMs = cursor.getLong(6),
                    reportingMode = if (cursor.isNull(7)) null else cursor.getInt(7),
                    sensorName = if (cursor.isNull(8)) null else cursor.getString(8),
                    rejectionReason = if (cursor.isNull(9)) null else cursor.getString(9)
                )
            }
        }
        return last
    }

    fun sensorSampleCount(): Long = DatabaseUtils.longForQuery(
        readableDatabase, "SELECT COUNT(*) FROM sensor_samples", null
    )

    /** Most recent stored observation per sensor, including previous app processes. */
    fun lastSensorSamples(): Map<SensorKind, SensorSample> {
        val last = mutableMapOf<SensorKind, SensorSample>()
        readableDatabase.rawQuery(
            """SELECT kind,recorded_at_ms,event_timestamp_ns,accuracy,x,y,z
               FROM sensor_samples ORDER BY id DESC""", null
        ).use { cursor ->
            while (cursor.moveToNext() && last.size < SensorKind.entries.size) {
                val kind = SensorKind.fromType(cursor.getInt(0)) ?: continue
                if (kind in last) continue
                val values = if (kind.dimensions == 3) {
                    if (cursor.isNull(5) || cursor.isNull(6)) continue
                    listOf(cursor.getFloat(4), cursor.getFloat(5), cursor.getFloat(6))
                } else listOf(cursor.getFloat(4))
                last[kind] = SensorSample(
                    kind, cursor.getLong(2), cursor.getLong(1), cursor.getInt(3), values
                )
            }
        }
        return last
    }

    /**
     * Reuses existing v1 battery + v2 diagnostic rows; no invented thermal history
     * and no new sampling, permissions or extra thermal API calls.
     */
    fun recentThermalReadings(limit: Int = 12): List<ThermalHistoryItem> {
        require(limit in 1..100)
        val results = mutableListOf<ThermalHistoryItem>()
        readableDatabase.rawQuery(
            """SELECT s.recorded_at_ms, s.temperature_deci_c,
                      d.thermal_status, d.thermal_headroom
               FROM samples AS s
               LEFT JOIN diagnostic_samples AS d
                 ON d.session_id = s.session_id AND d.elapsed_ms = s.elapsed_ms
               ORDER BY s.recorded_at_ms DESC, s.id DESC
               LIMIT ?""", arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += ThermalHistoryItem(
                    recordedAtMs = cursor.getLong(0),
                    batteryTemperatureDeciC = if (cursor.isNull(1)) null else cursor.getInt(1),
                    thermalStatus = if (cursor.isNull(2)) null else cursor.getInt(2),
                    thermalHeadroom = if (cursor.isNull(3)) null else cursor.getFloat(3)
                )
            }
        }
        return results
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

    /**
     * Most recent saved Rust session only; bounded reads prevent growing memory.
     * Every point came from a manual battery reading, not a sampling service.
     */
    /** Selected existing session; bounded manual data, not a background scan. */
    fun sessionReadings(sessionId: String, limit: Int = 250): List<StoredReading> {
        require(sessionId.isNotBlank() && sessionId.length <= 100)
        require(limit in 1..1000)
        val rows = mutableListOf<StoredReading>()
        readableDatabase.rawQuery(
            """SELECT session_id,recorded_at_ms,elapsed_ms,percent,
                      temperature_deci_c,charging_status,charging_source,
                      voltage_mv,current_ua,power_saving,refresh_hz,width_px,height_px
               FROM samples WHERE session_id=?
               ORDER BY elapsed_ms DESC,id DESC LIMIT ?""",
            arrayOf(sessionId,limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) rows += cursor.asStoredReading()
        }
        return rows.asReversed()
    }

    fun latestSessionReadings(limit: Int = 250): List<StoredReading> {
        require(limit in 1..1000)
        val readings = mutableListOf<StoredReading>()
        readableDatabase.rawQuery(
            """SELECT m.session_id,m.recorded_at_ms,m.elapsed_ms,m.percent,
                      m.temperature_deci_c,m.charging_status,m.charging_source,
                      m.voltage_mv,m.current_ua,m.power_saving,m.refresh_hz,
                      m.width_px,m.height_px
               FROM samples m
               WHERE m.session_id = (
                   SELECT id FROM sessions
                   ORDER BY started_at_ms DESC, rowid DESC LIMIT 1
               )
               ORDER BY m.elapsed_ms DESC, m.id DESC
               LIMIT ?""", arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) readings += cursor.asStoredReading()
        }
        return readings.asReversed()
    }

    fun latestSessionReadingCount(): Long = DatabaseUtils.longForQuery(
        readableDatabase,
        """SELECT COUNT(*) FROM samples WHERE session_id = (
               SELECT id FROM sessions ORDER BY started_at_ms DESC, rowid DESC LIMIT 1
           )""",
        null
    )

    /** Recent, completed attempts, including failures that produced no measurements. */
    fun recentSensorAttempts(limit: Int = 400): List<SavedSensorAttempt> {
        require(limit in 1..2000)
        val result = mutableListOf<SavedSensorAttempt>()
        readableDatabase.rawQuery(
            """SELECT kind,state,recorded_at_ms,event_count,rejected_events,
                      latency_ms,timeout_ms,reporting_mode,sensor_name,rejection_reason
               FROM sensor_probe_attempts
               ORDER BY id DESC LIMIT ?""", arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val kind = SensorKind.fromType(cursor.getInt(0)) ?: continue
                result += SavedSensorAttempt(
                    kind = kind,
                    state = SensorProbeState.valueOf(cursor.getString(1)),
                    recordedAtMs = cursor.getLong(2),
                    eventCount = cursor.getInt(3),
                    rejectedEvents = cursor.getInt(4),
                    latencyMs = if (cursor.isNull(5)) null else cursor.getLong(5),
                    timeoutMs = cursor.getLong(6),
                    reportingMode = if (cursor.isNull(7)) null else cursor.getInt(7),
                    sensorName = if (cursor.isNull(8)) null else cursor.getString(8),
                    rejectionReason = if (cursor.isNull(9)) null else cursor.getString(9)
                )
            }
        }
        return result
    }

    /** Five self-describing ZIP entries, preserving the original battery CSV. */
    fun writeDiagnosticArchive(output: java.io.OutputStream): DiagnosticArchiveCounts =
        DiagnosticArchive.write(
            output,
            batteryCsv = ::writeCsv,
            attemptsCsv = ::writeSensorAttemptsCsv,
            sensorsCsv = ::writeSensorValuesCsv,
            thermalCsv = ::writeThermalHistoryCsv,
            timedCsv = ::writeTimedBatteryTestsCsv
        )

    private fun writeSensorAttemptsCsv(writer: Writer): Int = streamDiagnosticCsv(
        writer,
        listOf(
            "capture_id", "sensor_type", "recorded_at_ms", "state",
            "event_count", "rejected_events", "latency_ms", "timeout_ms",
            "reporting_mode", "sensor_name", "rejection_reason"
        ),
        """SELECT capture_id,kind,recorded_at_ms,state,event_count,rejected_events,
                  latency_ms,timeout_ms,reporting_mode,sensor_name,rejection_reason
           FROM sensor_probe_attempts ORDER BY id ASC""",
        untrustedText = setOf(9)
    )

    private fun writeSensorValuesCsv(writer: Writer): Int = streamDiagnosticCsv(
        writer,
        listOf(
            "capture_id", "sensor_type", "recorded_at_ms", "event_timestamp_ns",
            "accuracy", "x", "y", "z"
        ),
        """SELECT capture_id,kind,recorded_at_ms,event_timestamp_ns,accuracy,x,y,z
           FROM sensor_samples ORDER BY id ASC"""
    )

    private fun writeThermalHistoryCsv(writer: Writer): Int = streamDiagnosticCsv(
        writer,
        listOf(
            "session_id", "recorded_at_ms", "battery_temperature_deci_c",
            "thermal_status", "thermal_headroom"
        ),
        """SELECT s.session_id,s.recorded_at_ms,s.temperature_deci_c,
                  d.thermal_status,d.thermal_headroom
           FROM samples s
           LEFT JOIN diagnostic_samples d
             ON d.session_id=s.session_id AND d.elapsed_ms=s.elapsed_ms
           ORDER BY s.recorded_at_ms ASC,s.id ASC"""
    )

    private fun streamDiagnosticCsv(
        writer: Writer,
        header: List<String>,
        sql: String,
        untrustedText: Set<Int> = emptySet()
    ): Int {
        DiagnosticArchiveCsv.begin(writer, header)
        var count = 0
        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                DiagnosticArchiveCsv.append(
                    writer,
                    (0 until cursor.columnCount).map { i ->
                        if (cursor.isNull(i)) "" else cursor.getString(i)
                    },
                    untrustedText
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
