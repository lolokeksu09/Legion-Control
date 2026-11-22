package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ApplicationAuditRulesTest {
    private val present=AppFingerprint(true,123L,1700000000000L)

    @Test fun firstVisibleObservationRecordedOnce() {
        assertEquals(AppObservationEvent.FIRST_OBSERVED,
            ApplicationAuditRules.classify(null,present).event)
        assertNull(ApplicationAuditRules.classify(present,present).event)
    }

    @Test fun updatesDetectedByVersionOrTimestamp() {
        assertEquals(AppObservationEvent.VERSION_CHANGED,
            ApplicationAuditRules.classify(present,present.copy(versionCode=124)).event)
        assertEquals(AppObservationEvent.VERSION_CHANGED,
            ApplicationAuditRules.classify(present,present.copy(updatedAtMs=1700000001000L)).event)
    }

    @Test fun invisiblePackageDoesNotMeanUninstalled() {
        val unavailable=AppFingerprint(false,null,null)
        val event=ApplicationAuditRules.classify(present,unavailable)
        assertEquals(AppObservationEvent.BECAME_UNAVAILABLE,event.event)
        assertTrue(event.description.contains("НЕ доказательство удаления"))
        assertNull(ApplicationAuditRules.classify(unavailable,unavailable).event)
        assertEquals(AppObservationEvent.BECAME_VISIBLE,
            ApplicationAuditRules.classify(unavailable,present).event)
    }

    @Test fun neverInventUpdateForUnknownPackageState() {
        val unknown=AppFingerprint(false,null,null)
        assertNull(ApplicationAuditRules.classify(null,unknown).event)
        assertEquals(AppObservationEvent.BECAME_VISIBLE,
            ApplicationAuditRules.classify(unknown,AppFingerprint(true,null,null)).event)
    }

    @Test fun invalidOrUnknownVersionNamesExplicit() {
        assertEquals("неизвестна",ApplicationAuditRules.versionLabel(null,null))
        assertEquals("1.4 (код 24)",ApplicationAuditRules.versionLabel("1.4",24L))
        assertEquals("н/д",ApplicationAuditRules.humanDuration(null))
        assertEquals("н/д",ApplicationAuditRules.humanDuration(-10))
        assertEquals("1 ч 1 мин",ApplicationAuditRules.humanDuration(3_660_000L))
    }

    @Test fun csvEscapesFormulaAndQuoteAndNewline() {
        assertEquals("\"'=SUM(A1)\"",ApplicationAuditRules.csvCell("=SUM(A1)"))
        assertEquals("\"safe\"\"quote\"",ApplicationAuditRules.csvCell("safe\"quote"))
        assertEquals("\"' +test\"",ApplicationAuditRules.csvCell(" +test"))
        assertEquals("\"x\ny\"",ApplicationAuditRules.csvCell("x\ny"))
    }

    @Test fun unchangedNullVersionDoesNotFabricateUpdate() {
        val same=AppFingerprint(true,null,null)
        assertNull(ApplicationAuditRules.classify(same,same).event)
    }
}
