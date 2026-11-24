package dev.lolokeksu.legioncontrol

/** Pure limits shared by Android Activity, overlay service and JVM tests. */
internal data class ToolboxClockFrame(val seconds: Long, val nextDelayMs: Long)

object ToolboxTuning {
    /** No periodic UI work when collapsed; elapsed time is independent of visibility. */
    internal fun clockFrame(visible: Boolean, startedAtMs: Long, nowMs: Long): ToolboxClockFrame? {
        if (!visible || startedAtMs < 0 || nowMs < startedAtMs) return null
        val elapsed = nowMs - startedAtMs
        return ToolboxClockFrame(elapsed / 1000L, 1000L - elapsed % 1000L)
    }

    fun opacity(value: Int): Int = value.coerceIn(55, 100)
    fun sizeDp(value: Int): Int = value.coerceIn(44, 72)
    fun position(value: Int, available: Int): Int =
        value.coerceIn(0, available.coerceAtLeast(0))
    fun volumeStep(current: Int, max: Int, direction: Int): Int {
        val bound = max.coerceAtLeast(0)
        return (current.coerceIn(0, bound) + direction.coerceIn(-1, 1)).coerceIn(0, bound)
    }
}

/** Private preferences; never read which other application is foreground. */
object ToolboxPreferences {
    const val NAME = "legion-game-toolbox"
    const val GAME = "game_package"
    const val LABEL = "game_label"
    const val OPACITY = "overlay_opacity"
    const val SIZE = "overlay_size_dp"
    const val POS_X = "overlay_x"
    const val POS_Y = "overlay_y"
}
