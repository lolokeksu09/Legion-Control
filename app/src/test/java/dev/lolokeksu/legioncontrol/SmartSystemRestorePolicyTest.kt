package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSystemRestorePolicyTest {
    @Test fun restoresOnlyWhenCurrentValueStillMatchesLegionRequest() {
        assertEquals(SmartSystemRestoreDecision.RESTORE,
            SmartSystemRestorePolicy.decide(current = 200, original = 100, requested = 200))
    }

    @Test fun doesNotRewriteAnAlreadyRestoredValue() {
        assertEquals(SmartSystemRestoreDecision.ALREADY_ORIGINAL,
            SmartSystemRestorePolicy.decide(current = 100, original = 100, requested = 200))
    }

    @Test fun preservesValuesChangedByTheUserOrAnotherApp() {
        assertEquals(SmartSystemRestoreDecision.USER_CHANGED,
            SmartSystemRestorePolicy.decide(current = 150, original = 100, requested = 200))
    }

    @Test fun keepsBackwardCompatibilityForLegacyUndoRecords() {
        assertEquals(SmartSystemRestoreDecision.LEGACY_RESTORE,
            SmartSystemRestorePolicy.decide(current = 200, original = 100, requested = null))
    }
}
