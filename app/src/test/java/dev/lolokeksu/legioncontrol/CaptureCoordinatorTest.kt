package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class CaptureCoordinatorTest {
    @Test fun rejectsConcurrentConsentsAndReleasesOnCancellation() {
        CaptureCoordinator.release(CaptureMode.REPLAY)
        CaptureCoordinator.release(CaptureMode.RECORDING)
        CaptureCoordinator.release(CaptureMode.SCREENSHOT)
        assertTrue(CaptureCoordinator.reserveConsent(CaptureMode.RECORDING))
        assertFalse(CaptureCoordinator.reserveConsent(CaptureMode.REPLAY))
        assertFalse(CaptureCoordinator.begin(CaptureMode.REPLAY))
        CaptureCoordinator.release(CaptureMode.RECORDING)
        assertTrue(CaptureCoordinator.reserveConsent(CaptureMode.REPLAY))
        CaptureCoordinator.release(CaptureMode.REPLAY)
    }

    @Test fun doesNotReleaseUntilMediaResourcesAreClosed() {
        CaptureCoordinator.release(CaptureMode.REPLAY)
        assertTrue(CaptureCoordinator.reserveConsent(CaptureMode.REPLAY))
        assertTrue(CaptureCoordinator.begin(CaptureMode.REPLAY))
        assertFalse(CaptureCoordinator.begin(CaptureMode.REPLAY))
        CaptureCoordinator.markActive(CaptureMode.REPLAY)
        CaptureCoordinator.markStopping(CaptureMode.REPLAY)
        assertTrue(CaptureCoordinator.busy())
        assertFalse(CaptureCoordinator.reserveConsent(CaptureMode.SCREENSHOT))
        CaptureCoordinator.release(CaptureMode.REPLAY)
        assertEquals(CapturePhase.IDLE, CaptureCoordinator.state().second)
    }

    @Test fun invalidConsentCannotReleaseActiveProjection() {
        CaptureCoordinator.release(CaptureMode.REPLAY)
        assertTrue(CaptureCoordinator.reserveConsent(CaptureMode.REPLAY))
        CaptureCoordinator.releasePending(CaptureMode.REPLAY)
        assertFalse(CaptureCoordinator.busy())
        assertTrue(CaptureCoordinator.reserveConsent(CaptureMode.REPLAY))
        assertTrue(CaptureCoordinator.begin(CaptureMode.REPLAY))
        CaptureCoordinator.markActive(CaptureMode.REPLAY)
        CaptureCoordinator.releasePending(CaptureMode.REPLAY)
        assertTrue(CaptureCoordinator.busy())
        CaptureCoordinator.release(CaptureMode.REPLAY)
    }

    @Test fun otherOwnerCannotUnlockAnActiveCapture() {
        CaptureCoordinator.release(CaptureMode.SCREENSHOT)
        assertTrue(CaptureCoordinator.reserveConsent(CaptureMode.SCREENSHOT))
        CaptureCoordinator.release(CaptureMode.REPLAY)
        assertTrue(CaptureCoordinator.busy())
        CaptureCoordinator.release(CaptureMode.SCREENSHOT)
        assertFalse(CaptureCoordinator.busy())
    }
}
