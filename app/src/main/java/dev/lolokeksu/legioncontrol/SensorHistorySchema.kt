package dev.lolokeksu.legioncontrol

/** Additive v3 migration. Separate from legacy Rust sessions and v2 diagnostics. */
object SensorHistorySchema {
    const val VERSION = 3
    const val CREATE_TABLE = """
        CREATE TABLE sensor_samples (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            capture_id TEXT NOT NULL,
            kind INTEGER NOT NULL CHECK(kind IN (1,4,5,8)),
            recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms > 0),
            event_timestamp_ns INTEGER NOT NULL CHECK(event_timestamp_ns > 0),
            accuracy INTEGER NOT NULL CHECK(accuracy BETWEEN -1 AND 3),
            x REAL NOT NULL,
            y REAL,
            z REAL,
            UNIQUE(capture_id,kind)
        )
    """
    const val CREATE_INDEX =
        "CREATE INDEX idx_sensor_samples_kind_id ON sensor_samples(kind,id DESC)"
}
