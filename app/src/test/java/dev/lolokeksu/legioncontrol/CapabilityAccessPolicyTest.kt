package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityAccessPolicyTest {
    @Test fun grantedAndEnabledIsReady() =
        assertEquals(AccessState.READY, CapabilityAccessPolicy.evaluate(true, true, true))

    @Test fun grantedButDisabledIsNotDenied() =
        assertEquals(AccessState.DISABLED, CapabilityAccessPolicy.evaluate(true, true, false))

    @Test fun deniedPermissionTakesPrecedenceOverEffectiveState() =
        assertEquals(AccessState.PERMISSION_REQUIRED, CapabilityAccessPolicy.evaluate(true, false, false))

    @Test fun unsupportedIsNotPermissionRequired() =
        assertEquals(AccessState.UNSUPPORTED, CapabilityAccessPolicy.evaluate(false, false))

    @Test fun compositeRequiresEveryRelevantCapability() {
        assertEquals(AccessState.READY, CapabilityAccessPolicy.combine(emptyList()))
        assertEquals(AccessState.READY, CapabilityAccessPolicy.combine(listOf(AccessState.READY, AccessState.READY)))
        assertEquals(AccessState.UNKNOWN, CapabilityAccessPolicy.combine(listOf(AccessState.READY, AccessState.UNKNOWN)))
    }

    @Test fun knownDenialOutranksUnknownInComposite() =
        assertEquals(AccessState.PERMISSION_REQUIRED,
            CapabilityAccessPolicy.combine(listOf(AccessState.UNKNOWN, AccessState.PERMISSION_REQUIRED)))

    @Test fun disabledChannelNeverCountsAsReady() =
        assertEquals(AccessState.DISABLED,
            CapabilityAccessPolicy.combine(listOf(AccessState.READY, AccessState.DISABLED)))

    @Test fun unknownProbeNeverInventsAccess() {
        assertEquals(AccessState.UNKNOWN, CapabilityAccessPolicy.evaluate(null, true))
        assertEquals(AccessState.UNKNOWN, CapabilityAccessPolicy.evaluate(true, null))
        assertEquals(AccessState.UNKNOWN, CapabilityAccessPolicy.evaluate(true, true, null))
    }
}
