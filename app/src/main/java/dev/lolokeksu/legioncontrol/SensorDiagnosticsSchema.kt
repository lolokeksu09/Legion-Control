package dev.lolokeksu.legioncontrol

/** SQLite v4 is strictly additive: v1/v2 battery, v3 sensor events remain unchanged. */
object SensorDiagnosticsSchema {
    const val VERSION = 4
    const val CREATE_TABLE = """
        CREATE TABLE sensor_probe_attempts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            capture_id TEXT NOT NULL,
            kind INTEGER NOT NULL CHECK(kind IN (1,4,5,8)),
            recorded_at_ms INTEGER NOT NULL CHECK(recorded_at_ms > 0),
            state TEXT NOT NULL CHECK(state IN ('CAPTURED','MISSING','REGISTRATION_FAILED','TIMEOUT','INVALID_DATA')),
            event_count INTEGER NOT NULL CHECK(event_count >= 0),
            rejected_events INTEGER NOT NULL CHECK(rejected_events >= 0 AND rejected_events <= event_count),
            latency_ms INTEGER CHECK(latency_ms IS NULL OR latency_ms >= 0),
            timeout_ms INTEGER NOT NULL CHECK(timeout_ms BETWEEN 1000 AND 30000),
            reporting_mode INTEGER,
            sensor_name TEXT,
            rejection_reason TEXT,
            UNIQUE(capture_id, kind)
        )
    """
    const val CREATE_INDEX =
        "CREATE INDEX idx_sensor_probe_attempts_kind_id ON sensor_probe_attempts(kind,id DESC)"
}

data class SavedSensorAttempt(
    val kind: SensorKind,
    val state: SensorProbeState,
    val recordedAtMs: Long,
    val eventCount: Int,
    val rejectedEvents: Int,
    val latencyMs: Long?,
    val timeoutMs: Long,
    val reportingMode: Int?,
    val sensorName: String?,
    val rejectionReason: String?
)
