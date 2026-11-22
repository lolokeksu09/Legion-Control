package dev.lolokeksu.legioncontrol

import android.database.sqlite.SQLiteDatabase

/** Additive v8. Existing sessions/samples/profiles are never rewritten. */
object ExperimentSchema {
    const val VERSION = 8

    fun install(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE session_annotations (
                session_id TEXT PRIMARY KEY NOT NULL
                    REFERENCES game_sessions(session_id) ON DELETE CASCADE,
                tag TEXT NOT NULL DEFAULT '' CHECK(length(tag) <= 40),
                note TEXT NOT NULL DEFAULT '' CHECK(length(note) <= 1000),
                updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms > 0)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE session_experiments (
                experiment_id TEXT PRIMARY KEY NOT NULL,
                package_name TEXT NOT NULL CHECK(length(package_name) BETWEEN 3 AND 200),
                name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 100),
                hypothesis TEXT NOT NULL DEFAULT '' CHECK(length(hypothesis) <= 500),
                label_a TEXT NOT NULL DEFAULT 'A' CHECK(length(label_a) BETWEEN 1 AND 60),
                label_b TEXT NOT NULL DEFAULT 'B' CHECK(length(label_b) BETWEEN 1 AND 60),
                created_at_ms INTEGER NOT NULL CHECK(created_at_ms > 0)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_session_experiments_package ON session_experiments(package_name,created_at_ms DESC)")
        db.execSQL("""
            CREATE TABLE session_experiment_members (
                experiment_id TEXT NOT NULL
                    REFERENCES session_experiments(experiment_id) ON DELETE CASCADE,
                session_id TEXT NOT NULL
                    REFERENCES game_sessions(session_id) ON DELETE CASCADE,
                arm TEXT NOT NULL CHECK(arm IN ('A','B')),
                assigned_at_ms INTEGER NOT NULL CHECK(assigned_at_ms > 0),
                PRIMARY KEY(experiment_id,session_id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_experiment_members_session ON session_experiment_members(session_id)")
        db.execSQL("CREATE INDEX idx_experiment_members_arm ON session_experiment_members(experiment_id,arm)")
    }
}
