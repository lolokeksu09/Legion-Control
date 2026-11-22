package dev.lolokeksu.legioncontrol

import android.database.sqlite.SQLiteDatabase

/** SQLite v9 is additive. Every v1-v8 history row survives upgrade. */
object ConnectivitySchema {
    const val VERSION = 9
    fun install(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE connectivity_runs (
                run_id TEXT PRIMARY KEY NOT NULL,
                host TEXT NOT NULL CHECK(host IN ('one.one.one.one','dns.google','dns.quad9.net')),
                started_at_ms INTEGER NOT NULL CHECK(started_at_ms > 0),
                ended_at_ms INTEGER,
                mode TEXT NOT NULL CHECK(mode IN ('SINGLE','STABILITY')),
                status TEXT NOT NULL CHECK(status IN ('RUNNING','COMPLETE','CANCELLED','FAILED','INTERRUPTED')),
                CHECK(ended_at_ms IS NULL OR ended_at_ms >= started_at_ms),
                CHECK((status = 'RUNNING' AND ended_at_ms IS NULL) OR
                      (status <> 'RUNNING' AND ended_at_ms IS NOT NULL))
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_connectivity_runs_start ON connectivity_runs(started_at_ms DESC)")
        db.execSQL("""
            CREATE TABLE connectivity_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL REFERENCES connectivity_runs(run_id) ON DELETE CASCADE,
                cycle INTEGER NOT NULL CHECK(cycle BETWEEN 1 AND 6),
                recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms > 0),
                family TEXT NOT NULL CHECK(family IN ('IPV4','IPV6')),
                dns_ms REAL CHECK(dns_ms BETWEEN 0 AND 120000),
                tcp_ms REAL CHECK(tcp_ms BETWEEN 0 AND 120000),
                tls_ms REAL CHECK(tls_ms BETWEEN 0 AND 120000),
                result TEXT NOT NULL CHECK(result IN ('OK','NO_ADDRESS','DNS_ERROR','TCP_ERROR','TLS_ERROR')),
                error_code TEXT NOT NULL DEFAULT '' CHECK(length(error_code) <= 48),
                network_type TEXT NOT NULL CHECK(length(network_type) BETWEEN 1 AND 24),
                network_validated INTEGER CHECK(network_validated IN (0,1)),
                UNIQUE(run_id,cycle,family)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_connectivity_samples_run ON connectivity_samples(run_id,cycle,id)")
    }
}
