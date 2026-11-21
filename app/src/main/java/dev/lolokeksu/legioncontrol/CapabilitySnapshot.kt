package dev.lolokeksu.legioncontrol

/** Values reported by DisplayManager, not measured game FPS or touch frequency. */
data class ReportedDisplayMode(
    val modeId: Int,
    val widthPx: Int,
    val heightPx: Int,
    val refreshRateHz: Float
)

/** Read-only public Android API capabilities; null means unavailable/unknown. */
data class PlatformCapabilities(
    val thermalStatus: Int?,
    val sustainedPerformanceSupported: Boolean?,
    val activeDisplayMode: ReportedDisplayMode?,
    val supportedDisplayModes: List<ReportedDisplayMode>
)

/** Pure validation layer for untrusted, potentially incomplete OEM responses. */
object CapabilitySanitizer {
    fun thermalStatus(raw: Int?): Int? = raw?.takeIf { it in 0..6 }

    fun displayMode(
        modeId: Int, widthPx: Int, heightPx: Int, refreshRateHz: Float
    ): ReportedDisplayMode? {
        if (modeId <= 0 || widthPx !in 1..16_384 || heightPx !in 1..16_384) return null
        if (!refreshRateHz.isFinite() || refreshRateHz <= 0f || refreshRateHz > 1000f) return null
        return ReportedDisplayMode(modeId, widthPx, heightPx, refreshRateHz)
    }

    /** Preserve platform mode IDs; suppress identical IDs in inconsistent responses. */
    fun supportedModes(raw: List<ReportedDisplayMode>): List<ReportedDisplayMode> =
        raw.distinctBy { it.modeId }.sortedWith(
            compareBy<ReportedDisplayMode> { it.widthPx.toLong() * it.heightPx }
                .thenBy { it.refreshRateHz }
                .thenBy { it.modeId }
        )
}
