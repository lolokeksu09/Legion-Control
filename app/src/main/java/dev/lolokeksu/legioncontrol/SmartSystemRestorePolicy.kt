package dev.lolokeksu.legioncontrol

internal enum class SmartSystemRestoreDecision {
    RESTORE, ALREADY_ORIGINAL, USER_CHANGED, LEGACY_RESTORE
}

/** Never overwrite a value that differs from both the saved original and Legion's last request. */
internal object SmartSystemRestorePolicy {
    fun decide(current: Int, original: Int, requested: Int?): SmartSystemRestoreDecision = when {
        current == original -> SmartSystemRestoreDecision.ALREADY_ORIGINAL
        requested == null -> SmartSystemRestoreDecision.LEGACY_RESTORE
        current == requested -> SmartSystemRestoreDecision.RESTORE
        else -> SmartSystemRestoreDecision.USER_CHANGED
    }
}
