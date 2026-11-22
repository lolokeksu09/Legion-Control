package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class HapticPatternTest {
    @Test fun fivePresetsHaveFiniteSafeNonRepeatingPatterns() {
        for (kind in HapticPattern.kinds) {
            for (level in HapticPattern.levels) {
                val p = HapticPattern.build(kind,level)
                assertTrue(p.timings.size == p.amplitudes.size)
                assertTrue(p.timings.size in 2..12)
                assertEquals(0L,p.timings.first())
                assertEquals(0,p.amplitudes.first())
                assertTrue(p.timings.all { it in 0..1_000 })
                assertTrue(p.amplitudes.all { it in 0..160 })
                assertTrue(p.amplitudes.any { it>0 })
                assertTrue(p.durationMs in 35L..1_800L)
                assertEquals(p.durationMs,p.timings.sum())
                assertEquals(HapticPattern.amplitude(level),p.requestedAmplitude)
            }
        }
        assertEquals(5,HapticPattern.kinds.size)
        assertEquals(3,HapticPattern.levels.size)
    }

    @Test fun predefinedPatternsAndIntensityHaveDistinctBehavior() {
        val one=HapticPattern.build("TAP","GENTLE")
        val two=HapticPattern.build("DOUBLE_TAP","GENTLE")
        val triple=HapticPattern.build("TRIPLE_PULSE","GENTLE")
        assertEquals(1,one.amplitudes.count { it>0 })
        assertEquals(2,two.amplitudes.count { it>0 })
        assertEquals(3,triple.amplitudes.count { it>0 })
        assertTrue(HapticPattern.build("LONG_PULSE","MEDIUM").durationMs >
            HapticPattern.build("TAP","MEDIUM").durationMs)
        assertEquals(48,HapticPattern.amplitude("GENTLE"))
        assertEquals(96,HapticPattern.amplitude("MEDIUM"))
        assertEquals(160,HapticPattern.amplitude("STRONG"))
    }

    @Test fun noUnsupportedInputCanCauseUnboundedVibration() {
        assertThrows(IllegalArgumentException::class.java) {
            HapticPattern.build("UNKNOWN","GENTLE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HapticPattern.build("TAP","MAX")
        }
        assertFalse(HapticPattern.isValid("CONTINUOUS","MEDIUM"))
        assertFalse(HapticPattern.isValid("TAP","255"))
    }

    @Test fun amplitudeFallbackPreservesSilenceAndDoesNotAmplify() {
        val source=HapticPattern.build("TRIPLE_PULSE","STRONG")
        val fallback=HapticPattern.withoutAmplitudeControl(source)
        assertTrue(fallback.amplitudes.indices.all { i ->
            if(source.amplitudes[i]==0) fallback.amplitudes[i]==0
            else fallback.amplitudes[i]==-1
        })
        assertEquals(source.durationMs,fallback.durationMs)
        assertEquals(source.timings.toList(),fallback.timings.toList())
    }

    @Test fun reportDistinguishesRequestFromVerifiedHardwareAction() {
        assertTrue(HapticPattern.caveat.contains("НЕ"))
        assertTrue(HapticPattern.caveat.contains("физическ"))
    }

    @Test fun csvEscapesSubjectiveUserNotesAndFormulaAttacks() {
        assertEquals("\"'=1+2\"",HapticPattern.csvCell("=1+2"))
        assertEquals("\"' +2\"",HapticPattern.csvCell(" +2"))
        assertEquals("\"a\"\"b\"",HapticPattern.csvCell("a\"b"))
    }
}
