package dev.lolokeksu.legioncontrol

/** Stable page identity; restored state and feature deep links never trigger measurements. */
internal enum class DiagnosticsPage(val label: String) {
    OVERVIEW("Замеры и батарея"),
    MONITOR("Мониторинг ресурсов"),
    HISTORY("История и экспорт"),
    SENSORS("Датчики"),
    GAMING("Игровые измерения"),
    HARDWARE("Аппаратные тесты"),
    NETWORK("Сеть"),
    COMPAT("Совместимость"),
    AUDIO("Звук"),
    HAPTIC("Вибрация"),
    DISPLAY("Экран и контроллеры");

    val isEmbeddedLab: Boolean get() = this in setOf(GAMING,HARDWARE,NETWORK,COMPAT,AUDIO,HAPTIC)
    val isPeripheral: Boolean get() = this in setOf(AUDIO,HAPTIC,DISPLAY)
    val module: FeatureModule? get() = when(this) {
        GAMING -> FeatureModule.GAMING
        HARDWARE -> FeatureModule.HARDWARE
        NETWORK -> FeatureModule.NETWORK
        COMPAT -> FeatureModule.COMPAT
        AUDIO -> FeatureModule.AUDIO
        HAPTIC -> FeatureModule.HAPTIC
        else -> null
    }

    companion object {
        private fun parse(value: String?) = entries.firstOrNull { it.name == value }

        fun initial(saved: String?, requested: String?, featureId: String?): DiagnosticsPage {
            parse(saved)?.let { return it }
            if (FeatureFinder.resolve(featureId, FeatureModule.MONITOR) != null) return MONITOR
            entries.firstOrNull { candidate -> candidate.module?.let { FeatureFinder.resolve(featureId,it)!=null }==true }
                ?.let { return it }
            return when (FeatureFinder.resolve(featureId, FeatureModule.DIAGNOSTICS)?.id) {
                "diag.thermal", "diag.zip" -> HISTORY
                "diag.sensors" -> SENSORS
                "diag.measure", "diag.battery" -> OVERVIEW
                else -> parse(requested) ?: OVERVIEW
            }
        }
    }
}
