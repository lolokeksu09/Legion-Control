package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VerifiedSettingWriteTest {
    @Test fun confirmedWriteReturnsReadback() {
        var current = 10
        assertEquals(20, VerifiedSettingWrite.apply(20, { current }, { current=it; true }))
    }
    @Test fun ignoredWriteIsNotSuccess() {
        assertThrows(IllegalStateException::class.java) {
            VerifiedSettingWrite.apply(20, { 10 }, { true })
        }
    }
    @Test fun rejectedWriteDoesNotReadBack() {
        var reads = 0
        assertThrows(IllegalStateException::class.java) {
            VerifiedSettingWrite.apply(20, { reads++; 20 }, { false })
        }
        assertEquals(0, reads)
    }
    @Test fun unreadableValueIsNotSuccess() {
        assertThrows(SecurityException::class.java) {
            VerifiedSettingWrite.apply(20, { throw SecurityException() }, { true })
        }
    }
}
