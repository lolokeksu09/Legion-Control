package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ShellAuditPolicyTest {
    @Test fun distinguishesConfiguredOffFromMissingAndDenied() {
        assertTrue(ShellAuditPolicy.value("0", true).startsWith("выключено"))
        assertTrue(ShellAuditPolicy.value("true", true).startsWith("включено"))
        assertTrue(ShellAuditPolicy.value("null", true).startsWith("не задано"))
        assertTrue(ShellAuditPolicy.value("1", false).startsWith("н/д"))
    }
    @Test fun unexpectedOutputIsNotDisplayed() {
        assertEquals("н/д · неизвестный формат", ShellAuditPolicy.value("unexpected private output", true))
    }
}
