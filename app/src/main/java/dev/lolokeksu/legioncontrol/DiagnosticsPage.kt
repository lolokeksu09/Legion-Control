package dev.lolokeksu.legioncontrol

/** Stable page identity; restored state and feature deep links never trigger measurements. */
internal enum class DiagnosticsPage(val label: String) {
    OVERVIEW("Замеры и батарея"),
    MONITOR("Мониторинг ресурсов"),
    HISTORY("История и экспорт"),
    SENSORS("Датчики");

    companion object {
        private fun parse(value: String?) = entries.firstOrNull { it.name == value }

        fun initial(saved: String?, requested: String?, featureId: String?): DiagnosticsPage {
            parse(saved)?.let { return it }
            if (FeatureFinder.resolve(featureId, FeatureModule.MONITOR) != null) return MONITOR
            return when (FeatureFinder.resolve(featureId, FeatureModule.DIAGNOSTICS)?.id) {
                "diag.thermal", "diag.zip" -> HISTORY
                "diag.sensors" -> SENSORS
                "diag.measure", "diag.battery" -> OVERVIEW
                else -> parse(requested) ?: OVERVIEW
            }
        }
    }
}
