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

    @Test fun failedBrightnessDefersAdaptiveModeRestore() {
        assertEquals(BrightnessModeRestoreAction.DEFER,
            SmartSystemRestorePolicy.modeAfterBrightness(true, null))
    }

    @Test fun userChangedBrightnessDiscardsAssociatedModeRestore() {
        assertEquals(BrightnessModeRestoreAction.DISCARD,
            SmartSystemRestorePolicy.modeAfterBrightness(true, SmartSystemRestoreDecision.USER_CHANGED))
    }

    @Test fun completedBrightnessOrModeOnlyRetryCanRestoreMode() {
        assertEquals(BrightnessModeRestoreAction.RESTORE,
            SmartSystemRestorePolicy.modeAfterBrightness(false, null))
        for (decision in listOf(SmartSystemRestoreDecision.RESTORE,
            SmartSystemRestoreDecision.ALREADY_ORIGINAL, SmartSystemRestoreDecision.LEGACY_RESTORE)) {
            assertEquals(BrightnessModeRestoreAction.RESTORE,
                SmartSystemRestorePolicy.modeAfterBrightness(true, decision))
        }
    }

    @Test fun keepsBackwardCompatibilityForLegacyUndoRecords() {
        assertEquals(SmartSystemRestoreDecision.LEGACY_RESTORE,
            SmartSystemRestorePolicy.decide(current = 200, original = 100, requested = null))
    }
}
