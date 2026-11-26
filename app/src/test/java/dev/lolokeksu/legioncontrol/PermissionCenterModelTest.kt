package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class PermissionCenterModelTest {
    @Test fun allUserManagedCapabilitiesHaveOneEntry() {
        val rows = PermissionCenterModel.permissions(CapabilityAccessSnapshot(emptyMap()))
        assertEquals(6, rows.size)
        assertEquals(6, rows.map { it.capability }.distinct().size)
        assertTrue(rows.all { it.canOpenSettings && it.state == AccessState.UNKNOWN })
    }

    @Test fun missingAccessIsNeverReportedReady() {
        val rows = PermissionCenterModel.permissions(CapabilityAccessSnapshot(
            mapOf(CapabilityAccess.OVERLAY to AccessState.READY,
                CapabilityAccess.NOTIFICATIONS to AccessState.DISABLED)))
        assertEquals("Доступно: 1 из 6 · требуют внимания: 5", PermissionCenterModel.summary(rows))
        assertEquals(AccessState.DISABLED, rows.single { it.capability == CapabilityAccess.NOTIFICATIONS }.state)
    }

    @Test fun unsupportedPermissionCannotOfferSettings() {
        val rows = PermissionCenterModel.permissions(CapabilityAccessSnapshot(
            mapOf(CapabilityAccess.USAGE to AccessState.UNSUPPORTED)))
        assertFalse(rows.single { it.capability == CapabilityAccess.USAGE }.canOpenSettings)
    }

    @Test fun technicalLimitsNeverOfferMeaninglessPermissions() {
        val rows = PermissionCenterModel.limitations(CapabilityAccessSnapshot(
            mapOf(CapabilityAccess.MEDIA_VOLUME to AccessState.READY,
                CapabilityAccess.GAME_FPS to AccessState.UNSUPPORTED,
                CapabilityAccess.OEM_BOOST to AccessState.UNSUPPORTED)))
        assertEquals(3, rows.size)
        assertTrue(rows.none { it.canOpenSettings })
        assertEquals(AccessState.READY, rows.single { it.capability == CapabilityAccess.MEDIA_VOLUME }.state)
    }
}
