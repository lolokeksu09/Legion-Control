package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class BackupRestorePolicyTest {
    @Test fun refusesAnyExistingUserRows() {
        assertFalse(BackupRestorePolicy.acceptsEmptyTarget(listOf(0L, 1L, 0L)))
        assertFalse(BackupRestorePolicy.acceptsEmptyTarget(listOf(12L)))
        assertFalse(BackupRestorePolicy.acceptsEmptyTarget(listOf(-1L)))
    }

    @Test fun refusesMissingTableChecksAndAcceptsEmptySchema() {
        assertFalse(BackupRestorePolicy.acceptsEmptyTarget(emptyList()))
        assertTrue(BackupRestorePolicy.acceptsEmptyTarget(listOf(0L, 0L, 0L)))
    }
}
