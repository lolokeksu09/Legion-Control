package dev.lolokeksu.legioncontrol

/** Permission and effective feature state are separate; unknown never means denied or ready. */
internal enum class AccessState(val label: String) {
    READY("доступно"),
    DISABLED("разрешено, но отключено"),
    PERMISSION_REQUIRED("требуется разрешение"),
    UNSUPPORTED("не поддерживается публичным API"),
    UNKNOWN("не удалось определить")
}

internal object CapabilityAccessPolicy {
    fun evaluate(supported: Boolean?, granted: Boolean?, enabled: Boolean? = true): AccessState =
        when {
            supported == false -> AccessState.UNSUPPORTED
            supported == null -> AccessState.UNKNOWN
            granted == false -> AccessState.PERMISSION_REQUIRED
            granted == null -> AccessState.UNKNOWN
            enabled == false -> AccessState.DISABLED
            enabled == null -> AccessState.UNKNOWN
            else -> AccessState.READY
        }
}
