package dev.lolokeksu.legioncontrol

import android.database.sqlite.SQLiteDatabase

/** Additive v11; previous game/session/app and network history is not rewritten. */
object AudioLabSchema {
    const val VERSION = 11

    fun install(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE audio_test_runs (
                run_id TEXT PRIMARY KEY NOT NULL,
                started_at_ms INTEGER NOT NULL CHECK(started_at_ms > 0),
                ended_at_ms INTEGER,
                kind TEXT NOT NULL CHECK(kind IN ('TONE','SWEEP','CHANNEL_ID')),
                channel TEXT NOT NULL CHECK(channel IN ('LEFT','RIGHT','BOTH','ALTERNATING')),
                frequency_hz INTEGER NOT NULL CHECK(frequency_hz BETWEEN 180 AND 2000),
                duration_ms INTEGER NOT NULL CHECK(duration_ms BETWEEN 250 AND 3000),
                sample_rate_hz INTEGER NOT NULL CHECK(sample_rate_hz BETWEEN 8000 AND 192000),
                expected_frames INTEGER NOT NULL CHECK(expected_frames > 0),
                observed_frames INTEGER CHECK(observed_frames >= 0),
                routed_device_type INTEGER CHECK(routed_device_type >= 0),
                state TEXT NOT NULL CHECK(state IN ('RUNNING','COMPLETE','STOPPED','ERROR','INTERRUPTED')),
                error_code TEXT NOT NULL DEFAULT '' CHECK(length(error_code) <= 48),
                CHECK(ended_at_ms IS NULL OR ended_at_ms >= started_at_ms),
                CHECK((state='RUNNING' AND ended_at_ms IS NULL) OR
                      (state<>'RUNNING' AND ended_at_ms IS NOT NULL))
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_audio_test_runs_start ON audio_test_runs(started_at_ms DESC,run_id DESC)")
        db.execSQL("""
            CREATE TABLE audio_route_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL REFERENCES audio_test_runs(run_id) ON DELETE CASCADE,
                recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms > 0),
                event_kind TEXT NOT NULL CHECK(event_kind IN ('ROUTE_START','ROUTE_CHANGE')),
                output_device_type INTEGER CHECK(output_device_type >= 0),
                details TEXT NOT NULL DEFAULT '' CHECK(length(details) <= 180)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_audio_route_events_run ON audio_route_events(run_id,recorded_at_ms,id)")
    }
}
