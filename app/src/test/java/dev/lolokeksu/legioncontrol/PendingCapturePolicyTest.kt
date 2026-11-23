package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class PendingCapturePolicyTest {
    private val now = 2_000_000L

    @Test fun completedAndYoungMediaNeverQualify() {
        assertFalse(PendingCapturePolicy.isStale("LegionClip_1763800000000.mp4",true,false,now-200_000,now))
        assertFalse(PendingCapturePolicy.isStale("LegionClip_1763800000000.mp4",true,true,now-20,now))
        assertFalse(PendingCapturePolicy.isStale("LegionClip_1763800000000.mp4",true,true,0,now))
    }

    @Test fun onlyOwnNamedOldPendingRowsQualify() {
        assertTrue(PendingCapturePolicy.isStale("LegionClip_1763800000000.mp4",true,true,now-100_000,now))
        assertTrue(PendingCapturePolicy.isStale("LegionShot_1763800000000.png",false,true,now-100_000,now))
        assertFalse(PendingCapturePolicy.isStale("IMG_1763800000000.png",false,true,now-100_000,now))
        assertFalse(PendingCapturePolicy.isStale("LegionClip_1763800000000.mp4",false,true,now-100_000,now))
    }
}
