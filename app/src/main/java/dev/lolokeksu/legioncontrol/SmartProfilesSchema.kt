package dev.lolokeksu.legioncontrol

import android.database.sqlite.SQLiteDatabase

/** Additive SQLite v7. Keep every v1-v6 historical table and row intact. */
object SmartProfilesSchema {
    const val VERSION = 7
    fun install(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE smart_profiles (
                package_name TEXT PRIMARY KEY NOT NULL CHECK(length(package_name) BETWEEN 3 AND 200),
                label TEXT NOT NULL CHECK(length(label) BETWEEN 1 AND 100),
                created_at_ms INTEGER NOT NULL CHECK(created_at_ms > 0),
                updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms),
                battery_hot_deci_c INTEGER NOT NULL DEFAULT 420 CHECK(battery_hot_deci_c BETWEEN 300 AND 550),
                battery_low_percent INTEGER NOT NULL DEFAULT 20 CHECK(battery_low_percent BETWEEN 5 AND 80),
                tcp_opt_in INTEGER NOT NULL DEFAULT 0 CHECK(tcp_opt_in IN (0,1))
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_smart_profiles_updated ON smart_profiles(updated_at_ms DESC)")
        db.execSQL("""
            CREATE TABLE smart_rules (
                rule_id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL REFERENCES smart_profiles(package_name) ON DELETE CASCADE,
                kind TEXT NOT NULL CHECK(kind IN (
                    'BATTERY_HOT','BATTERY_LOW','THERMAL_SEVERE',
                    'POWER_SAVE','NETWORK_UNVALIDATED','NETWORK_CHANGED'
                )),
                threshold INTEGER NOT NULL,
                cooldown_ms INTEGER NOT NULL DEFAULT 120000 CHECK(cooldown_ms BETWEEN 30000 AND 3600000),
                enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),
                UNIQUE(package_name,kind)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_smart_rules_package ON smart_rules(package_name,enabled)")
        db.execSQL("""
            CREATE TABLE smart_rule_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL REFERENCES game_sessions(session_id),
                package_name TEXT NOT NULL,
                kind TEXT NOT NULL,
                recorded_at_ms INTEGER NOT NULL,
                details TEXT NOT NULL CHECK(length(details) <= 300)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_smart_rule_events_session ON smart_rule_events(session_id,recorded_at_ms)")
    }
}
