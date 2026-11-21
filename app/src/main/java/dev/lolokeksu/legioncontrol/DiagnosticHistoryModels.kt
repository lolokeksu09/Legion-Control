package dev.lolokeksu.legioncontrol

/**
 * Persist only values actually returned by Android. Null always means missing.
 * Unlike the Rust ring buffer this survives process restarts in private SQLite.
 */
data class DiagnosticStoredSample(
    val sessionId: String,
    val elapsedMs: Long,
    val recordedAtMs: Long,
    val batteryPercent: Int?,
    val batteryTemperatureDeciC: Int?,
    val thermalStatus: Int?,
    val thermalHeadroom: Float?,
    val chargeCounterMicroAh: Int?,
    val currentNowMicroAmps: Int?,
    val currentAverageMicroAmps: Int?,
    val ramAvailableBytes: Long?
)

/**
 * Schema-only constants: migration is additive and may be exercised in plain
 * SQLite tests without an Android emulator or mocked database implementation.
 */
object DiagnosticHistorySchema {
    const val VERSION = 2

    const val CREATE_TABLE = """
        CREATE TABLE diagnostic_samples (
            session_id TEXT NOT NULL,
            elapsed_ms INTEGER NOT NULL,
            recorded_at_ms INTEGER NOT NULL,
            battery_percent INTEGER CHECK(battery_percent IS NULL OR battery_percent BETWEEN 0 AND 100),
            battery_temperature_deci_c INTEGER,
            thermal_status INTEGER CHECK(thermal_status IS NULL OR thermal_status BETWEEN 0 AND 6),
            thermal_headroom REAL,
            charge_counter_micro_ah INTEGER,
            current_now_micro_a INTEGER,
            current_average_micro_a INTEGER,
            ram_available_bytes INTEGER,
            PRIMARY KEY (session_id, elapsed_ms),
            FOREIGN KEY (session_id, elapsed_ms) REFERENCES samples(session_id, elapsed_ms)
        )
    """

    const val CREATE_INDEX =
        "CREATE INDEX idx_diagnostic_samples_session ON diagnostic_samples(session_id, elapsed_ms DESC)"

    fun fromSnapshot(
        sessionId: String,
        elapsedMs: Long,
        recordedAtMs: Long,
        batteryPercent: Int,
        batteryTemperatureDeciC: Int,
        snapshot: DeviceDiagnosticSnapshot
    ): DiagnosticStoredSample = DiagnosticStoredSample(
        sessionId = sessionId,
        elapsedMs = elapsedMs,
        recordedAtMs = recordedAtMs,
        batteryPercent = batteryPercent.takeIf { it in 0..100 },
        batteryTemperatureDeciC = batteryTemperatureDeciC.takeIf { it in -400..1200 },
        thermalStatus = snapshot.capabilities.thermalStatus,
        thermalHeadroom = snapshot.resources.thermalHeadroom.value,
        chargeCounterMicroAh = snapshot.resources.batteryChargeCounterMicroAh.value,
        currentNowMicroAmps = snapshot.resources.batteryCurrentNowMicroAmps.value,
        currentAverageMicroAmps = snapshot.resources.batteryCurrentAverageMicroAmps.value,
        ramAvailableBytes = snapshot.resources.systemMemory.value?.availableBytes
    )
}
