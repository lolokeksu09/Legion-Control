package dev.lolokeksu.legioncontrol

import android.database.sqlite.SQLiteDatabase

/** Additive v10 tables. Existing diagnostics, profiles and recorded sessions remain untouched. */
object ApplicationCenterSchema {
    const val VERSION = 10

    fun install(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE app_library (
                package_name TEXT PRIMARY KEY NOT NULL CHECK(length(package_name) BETWEEN 3 AND 200),
                alias TEXT NOT NULL DEFAULT '' CHECK(length(alias) <= 100),
                category TEXT NOT NULL DEFAULT '' CHECK(length(category) <= 40),
                note TEXT NOT NULL DEFAULT '' CHECK(length(note) <= 1000),
                favorite INTEGER NOT NULL DEFAULT 0 CHECK(favorite IN (0,1)),
                created_at_ms INTEGER NOT NULL CHECK(created_at_ms > 0),
                updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms),
                last_seen_at_ms INTEGER,
                last_seen_version_code INTEGER,
                last_seen_update_ms INTEGER,
                last_seen_status TEXT NOT NULL DEFAULT 'UNKNOWN'
                    CHECK(last_seen_status IN ('UNKNOWN','VISIBLE','UNAVAILABLE'))
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_app_library_favorite ON app_library(favorite DESC,updated_at_ms DESC)")
        db.execSQL("""
            CREATE TABLE app_audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL REFERENCES app_library(package_name) ON DELETE CASCADE,
                recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms > 0),
                kind TEXT NOT NULL CHECK(kind IN (
                    'FIRST_OBSERVED','VERSION_CHANGED','BECAME_VISIBLE','BECAME_UNAVAILABLE'
                )),
                details TEXT NOT NULL DEFAULT '' CHECK(length(details) <= 300)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_app_audit_events_package ON app_audit_events(package_name,recorded_at_ms DESC,id DESC)")
    }
}
