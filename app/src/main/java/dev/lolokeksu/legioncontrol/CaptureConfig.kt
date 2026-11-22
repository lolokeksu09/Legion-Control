package dev.lolokeksu.legioncontrol

import kotlin.math.roundToInt

/** No Android dependencies: validated recording presets and encoder frame dimensions. */
data class CaptureConfig(
    val maxEdge: Int,
    val fps: Int,
    val videoBitrate: Int,
    val maxDurationMs: Long
) {
    companion object {
        private val EDGES = setOf(1280, 1920)
        private val RATES = setOf(30, 60)
        private val BITRATES = setOf(4, 8, 12)
        private val MINUTES = setOf(1, 5, 15)

        fun fromOptions(edge: Int, fps: Int, bitRateMbps: Int, minutes: Int): CaptureConfig =
            CaptureConfig(
                if (edge in EDGES) edge else 1280,
                if (fps in RATES) fps else 30,
                (if (bitRateMbps in BITRATES) bitRateMbps else 4) * 1_000_000,
                (if (minutes in MINUTES) minutes else 1) * 60_000L
            )

        /** Preserve screen orientation/aspect, cap long edge, never exceed source. */
        fun scaledSize(screenWidth: Int, screenHeight: Int, maxEdge: Int): Pair<Int, Int> {
            require(screenWidth > 0 && screenHeight > 0)
            require(maxEdge > 0)
            val scale = minOf(1.0, maxEdge.toDouble() / maxOf(screenWidth, screenHeight))
            fun even(value: Int): Int {
                val rounded = (value * scale).roundToInt().coerceAtLeast(2)
                return if (rounded % 2 == 0) rounded else rounded - 1
            }
            return even(screenWidth) to even(screenHeight)
        }

        fun fileName(nowMs: Long): String =
            "LegionClip_" + nowMs.coerceAtLeast(0L).toString().padStart(13, '0') + ".mp4"
    }
}
