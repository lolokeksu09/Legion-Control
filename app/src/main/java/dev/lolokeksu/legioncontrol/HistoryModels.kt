package dev.lolokeksu.legioncontrol

/** An immutable snapshot from public Android APIs, separate from Rust's RAM-only window. */
data class StoredReading(
    val sessionId: String,
    val recordedAtMs: Long,
    val elapsedMs: Long,
    val percent: Int,
    val temperatureDeciC: Int,
    val chargingStatus: String,
    val chargingSource: String,
    val voltageMv: Int?,
    val currentUa: Int?,
    val powerSaving: Boolean?,
    val refreshRateHz: Float?,
    val screenWidthPx: Int?,
    val screenHeightPx: Int?
)

data class SavedSession(
    val id: String,
    val startedAtMs: Long,
    val lastAtMs: Long,
    val endedAtMs: Long?,
    val samples: Int
)

/** Process lifetime matches Rust's OnceLock; a process restart always starts a new session. */
object ActiveSession {
    var id: String? = null
}
