package dev.lolokeksu.legioncontrol

/** Pure boundaries for an explicitly requested, single-frame MediaProjection. */
object CaptureScreenshotPolicy {
    const val MAX_EDGE = 1920
    const val TIMEOUT_MS = 12_000L

    fun dimensions(width: Int, height: Int): Pair<Int, Int> =
        CaptureConfig.scaledSize(width, height, MAX_EDGE)

    fun fileName(epochMs: Long): String {
        require(epochMs in 1..9_999_999_999_999L)
        return "LegionShot_" + epochMs.toString().padStart(13, '0') + ".png"
    }

    /** Avoid excessive bitmap allocation from a malformed ImageReader row stride. */
    fun paddedWidth(width: Int, pixelStride: Int, rowStride: Int): Int {
        require(width in 1..MAX_EDGE)
        require(pixelStride == 4)
        require(rowStride >= width * pixelStride && rowStride % pixelStride == 0)
        val result = rowStride / pixelStride
        require(result <= width + 512)
        return result
    }
}
