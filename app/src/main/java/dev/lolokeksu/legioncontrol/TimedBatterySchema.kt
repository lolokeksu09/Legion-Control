package dev.lolokeksu.legioncontrol

/** New table only. All old SQLite v1-v4 data must survive the upgrade. */
object TimedBatterySchema {
    const val VERSION = 5
    const val CREATE_TABLE = """
        CREATE TABLE timed_battery_tests (
            test_id TEXT PRIMARY KEY NOT NULL,
            session_id TEXT NOT NULL REFERENCES sessions(id),
            started_at_ms INTEGER NOT NULL CHECK(started_at_ms > 0),
            finished_at_ms INTEGER NOT NULL CHECK(finished_at_ms >= started_at_ms),
            reason TEXT NOT NULL CHECK(reason IN (
                'COMPLETED','USER_STOP','APP_BACKGROUND','THERMAL_SEVERE',
                'BATTERY_HOT','READING_FAILED'
            )),
            attempted_reads INTEGER NOT NULL CHECK(attempted_reads >= 0),
            successful_reads INTEGER NOT NULL CHECK(
                successful_reads >= 0 AND successful_reads <= attempted_reads
            ),
            first_percent INTEGER,
            last_percent INTEGER,
            min_temp_deci_c INTEGER,
            max_temp_deci_c INTEGER,
            max_thermal_status INTEGER,
            UNIQUE(session_id)
        )
    """
    const val CREATE_INDEX =
        "CREATE INDEX idx_timed_battery_tests_finished ON timed_battery_tests(finished_at_ms DESC)"
}
