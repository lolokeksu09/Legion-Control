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
}
