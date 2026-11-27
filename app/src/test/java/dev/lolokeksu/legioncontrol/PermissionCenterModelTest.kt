package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class PermissionCenterModelTest {
    private fun permissions(states: Map<CapabilityAccess, AccessState>) =
        PermissionCenterModel.groups(CapabilityAccessSnapshot(states)).flatMap { it.items }

    @Test fun eachUserManagedCapabilityBelongsToItsPurpose() {
        val groups = PermissionCenterModel.groups(CapabilityAccessSnapshot(emptyMap()))
        val expected = setOf(
            setOf(CapabilityAccess.OVERLAY, CapabilityAccess.USAGE, CapabilityAccess.NOTIFICATIONS),
            setOf(CapabilityAccess.PLAYBACK_AUDIO),
            setOf(CapabilityAccess.DND, CapabilityAccess.WRITE_SETTINGS)
        )
        assertEquals(expected, groups.map { group -> group.items.map { it.capability }.toSet() }.toSet())
        val rows = groups.flatMap { it.items }
        assertEquals(rows.size, rows.map { it.capability }.distinct().size)
        assertTrue(rows.all { it.canOpenSettings && it.state == AccessState.UNKNOWN })
    }

    @Test fun missingAccessIsNeverReportedReady() {
        val rows = permissions(mapOf(
            CapabilityAccess.OVERLAY to AccessState.READY,
            CapabilityAccess.NOTIFICATIONS to AccessState.DISABLED))
        assertEquals(listOf(CapabilityAccess.OVERLAY), rows.filter { it.state == AccessState.READY }.map { it.capability })
        assertEquals(AccessState.DISABLED, rows.single { it.capability == CapabilityAccess.NOTIFICATIONS }.state)
    }

    @Test fun unsupportedPermissionCannotOfferSettings() {
        val rows = permissions(mapOf(CapabilityAccess.USAGE to AccessState.UNSUPPORTED))
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
