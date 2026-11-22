package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure input-boundary tests for user-configurable, foreground-only toolbox UI. */
class ToolboxTuningTest {
    @Test fun opacityAlwaysStaysInsideVisibleRange() {
        assertEquals(55, ToolboxTuning.opacity(-100))
        assertEquals(55, ToolboxTuning.opacity(0))
        assertEquals(80, ToolboxTuning.opacity(80))
        assertEquals(100, ToolboxTuning.opacity(200))
    }

    @Test fun bubbleDimensionsStayTouchable() {
        assertEquals(44, ToolboxTuning.sizeDp(0))
        assertEquals(56, ToolboxTuning.sizeDp(56))
        assertEquals(72, ToolboxTuning.sizeDp(500))
    }

    @Test fun dragLocationCannotEscapeDisplay() {
        assertEquals(0, ToolboxTuning.position(-15, 100))
        assertEquals(100, ToolboxTuning.position(200, 100))
        assertEquals(0, ToolboxTuning.position(20, -4))
    }

    @Test fun audioStepsRespectRealStreamLimits() {
        assertEquals(0, ToolboxTuning.volumeStep(0, 15, -1))
        assertEquals(15, ToolboxTuning.volumeStep(15, 15, 1))
        assertEquals(8, ToolboxTuning.volumeStep(7, 15, 1))
        assertEquals(0, ToolboxTuning.volumeStep(7, 0, 1))
    }
}
