package dev.lolokeksu.legioncontrol

/** A backup may be restored only if every existing application table is empty. */
object BackupRestorePolicy {
    fun acceptsEmptyTarget(rowCounts: Collection<Long>): Boolean =
        rowCounts.isNotEmpty() && rowCounts.all { it == 0L }
}
