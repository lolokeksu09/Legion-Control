package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class PendingDocumentTest {
    @Test fun rotationKeepsOriginalSnapshotAndRejectsSecondRequest() {
        val owner=PendingDocument<Any>()
        val original=Any()
        assertTrue(owner.begin(original))
        assertTrue(owner.hasPending)
        assertFalse(owner.begin(Any()))
        assertSame(original,owner.take())
        assertFalse(owner.hasPending)
        assertNull(owner.take())
    }
    @Test fun cancellationAndRecreatedProcessCannotReuseOldSnapshot() {
        val owner=PendingDocument<String>()
        assertTrue(owner.begin("first"))
        assertEquals("first",owner.take())
        assertTrue(owner.begin("second"))
        assertEquals("second",owner.take())
        assertNull(PendingDocument<String>().take())
    }
}
