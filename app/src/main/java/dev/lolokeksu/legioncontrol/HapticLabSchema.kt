package dev.lolokeksu.legioncontrol

import android.database.sqlite.SQLiteDatabase

/** SQLite v12: additive haptic requests, human feedback and optional capability snapshots. */
object HapticLabSchema {
    const val VERSION=12
    fun install(db:SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE haptic_test_runs (
                run_id TEXT PRIMARY KEY NOT NULL,
                started_at_ms INTEGER NOT NULL CHECK(started_at_ms > 0),
                ended_at_ms INTEGER,
                pattern TEXT NOT NULL CHECK(pattern IN ('TAP','DOUBLE_TAP','TRIPLE_PULSE','SLOW_PULSE','LONG_PULSE')),
                level TEXT NOT NULL CHECK(level IN ('GENTLE','MEDIUM','STRONG')),
                requested_amplitude INTEGER NOT NULL CHECK(requested_amplitude IN (48,96,160)),
                duration_ms INTEGER NOT NULL CHECK(duration_ms BETWEEN 35 AND 1800),
                vibrator_count INTEGER CHECK(vibrator_count BETWEEN 0 AND 32),
                amplitude_control INTEGER CHECK(amplitude_control IN (0,1)),
                used_default_amplitude INTEGER CHECK(used_default_amplitude IN (0,1)),
                result TEXT NOT NULL CHECK(result IN ('RUNNING','COMPLETED_REQUEST','CANCELLED','ERROR','INTERRUPTED')),
                error_code TEXT NOT NULL DEFAULT '' CHECK(length(error_code) <= 48),
                CHECK(ended_at_ms IS NULL OR ended_at_ms >= started_at_ms),
                CHECK((result='RUNNING' AND ended_at_ms IS NULL) OR
                      (result<>'RUNNING' AND ended_at_ms IS NOT NULL))
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_haptic_runs_started ON haptic_test_runs(started_at_ms DESC,run_id DESC)")
        db.execSQL("""
            CREATE TABLE haptic_feedback (
                run_id TEXT PRIMARY KEY NOT NULL REFERENCES haptic_test_runs(run_id) ON DELETE CASCADE,
                rating TEXT NOT NULL CHECK(rating IN ('FELT','NOT_FELT','UNCERTAIN')),
                note TEXT NOT NULL DEFAULT '' CHECK(length(note) <= 200),
                updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms > 0)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE haptic_capability_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms > 0),
                vibrator_count INTEGER NOT NULL CHECK(vibrator_count BETWEEN 0 AND 32),
                has_vibrator INTEGER NOT NULL CHECK(has_vibrator IN (0,1)),
                amplitude_control INTEGER CHECK(amplitude_control IN (0,1)),
                haptic_enabled INTEGER CHECK(haptic_enabled IN (0,1)),
                effects TEXT NOT NULL DEFAULT '' CHECK(length(effects) <= 240),
                primitives TEXT NOT NULL DEFAULT '' CHECK(length(primitives) <= 240)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_haptic_snapshots_time ON haptic_capability_snapshots(recorded_at_ms DESC,id DESC)")
    }
}
