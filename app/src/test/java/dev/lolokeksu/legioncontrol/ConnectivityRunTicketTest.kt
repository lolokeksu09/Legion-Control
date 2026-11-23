package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityRunTicketTest {
    @Test fun cancelBeforeStartPreventsOldTaskAndAllowsNewRequest() {
        val old=ConnectivityRunTicket()
        assertTrue(old.cancel())
        assertFalse(old.start())
        assertTrue(old.isCancelled())
        val next=ConnectivityRunTicket()
        assertTrue(next.start())
        assertFalse(next.isCancelled())
    }
    @Test fun cancelAfterStartRequiresWorkerCleanup() {
        val ticket=ConnectivityRunTicket()
        assertTrue(ticket.start())
        assertFalse(ticket.cancel())
        assertTrue(ticket.isCancelled())
        assertFalse(ticket.start())
    }
    @Test fun repeatedCancelIsIdempotent() {
        val ticket=ConnectivityRunTicket()
        assertTrue(ticket.cancel())
        assertTrue(ticket.cancel())
        assertFalse(ticket.start())
    }
}
