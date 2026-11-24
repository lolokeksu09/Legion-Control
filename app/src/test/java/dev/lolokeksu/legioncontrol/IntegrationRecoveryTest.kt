package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class IntegrationRecoveryTest {
    @Test fun successfulSetterMustBeConfirmedByReadback() {
        val change=LaunchSettingChange(2,7)
        var actual=7
        GameLaunchProfilePolicy.restoreAndVerify({actual},{actual=it},change)
        assertEquals(2,actual)
        actual=7
        assertTrue(runCatching {
            GameLaunchProfilePolicy.restoreAndVerify({actual},{_ ->},change)
        }.isFailure)
        assertEquals(7,actual)
    }
    @Test fun unreadableOrRejectedRestorationFailsClosed() {
        val change=LaunchSettingChange(2,7)
        assertTrue(runCatching {
            GameLaunchProfilePolicy.restoreAndVerify({error("read blocked")},{_ ->},change)
        }.isFailure)
        assertTrue(runCatching {
            GameLaunchProfilePolicy.restoreAndVerify({7},{_ -> error("write blocked")},change)
        }.isFailure)
    }
    @org.junit.Test fun maintenanceGateIsNonBlockingReentrantAndReleasedAfterFailure() {
        val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            ProfileMaintenanceGate.tryRun({error("unexpected busy")}) {
                org.junit.Assert.assertEquals("nested",ProfileMaintenanceGate.tryRun({"blocked"}){"nested"})
                val other=worker.submit<String>{ProfileMaintenanceGate.tryRun({"blocked"}){"unsafe"}}
                org.junit.Assert.assertEquals("blocked",other.get(2,java.util.concurrent.TimeUnit.SECONDS))
            }
            org.junit.Assert.assertTrue(runCatching{
                ProfileMaintenanceGate.tryRun({error("unexpected busy")}){error("simulated failure")}
            }.isFailure)
            org.junit.Assert.assertEquals("released",worker.submit<String>{
                ProfileMaintenanceGate.tryRun({"blocked"}){"released"}
            }.get(2,java.util.concurrent.TimeUnit.SECONDS))
        } finally { worker.shutdownNow() }
    }
}
