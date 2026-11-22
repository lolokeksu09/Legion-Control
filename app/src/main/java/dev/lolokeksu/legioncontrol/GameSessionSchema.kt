package dev.lolokeksu.legioncontrol

import android.database.sqlite.SQLiteDatabase

/** Additive SQLite v6: every v1-v5 row is kept. Recording only on explicit consent. */
object GameSessionSchema {
    const val VERSION = 6
    fun install(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE game_sessions (
                session_id TEXT PRIMARY KEY NOT NULL,
                package_name TEXT NOT NULL,
                app_label TEXT NOT NULL,
                started_at_ms INTEGER NOT NULL CHECK(started_at_ms > 0),
                ended_at_ms INTEGER,
                end_reason TEXT,
                tcp_opt_in INTEGER NOT NULL CHECK(tcp_opt_in IN (0,1)),
                CHECK(ended_at_ms IS NULL OR ended_at_ms >= started_at_ms)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_game_sessions_started ON game_sessions(started_at_ms DESC)")
        db.execSQL("""
            CREATE TABLE game_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL REFERENCES game_sessions(session_id),
                recorded_at_ms INTEGER NOT NULL,
                battery_percent INTEGER CHECK(battery_percent BETWEEN 0 AND 100),
                battery_temp_deci_c INTEGER,
                battery_current_ua INTEGER,
                thermal_status INTEGER,
                power_saving INTEGER CHECK(power_saving IN (0,1)),
                refresh_hz REAL,
                network_type TEXT NOT NULL,
                network_validated INTEGER CHECK(network_validated IN (0,1))
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_game_samples_session ON game_samples(session_id,recorded_at_ms)")
        db.execSQL("""
            CREATE TABLE game_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL REFERENCES game_sessions(session_id),
                recorded_at_ms INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                details TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_game_events_session ON game_events(session_id,recorded_at_ms)")
    }
}
