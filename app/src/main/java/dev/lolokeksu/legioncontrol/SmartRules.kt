package dev.lolokeksu.legioncontrol

/** Pure Kotlin: rule evaluation never changes system settings or sends network requests. */
enum class SmartRuleKind(val title: String, val defaultThreshold: Int) {
    BATTERY_HOT("Нагрев батареи",420),
    BATTERY_LOW("Низкий заряд",20),
    THERMAL_SEVERE("Термальный статус Android",3),
    POWER_SAVE("Режим энергосбережения",1),
    NETWORK_UNVALIDATED("Сеть не подтверждена",1),
    NETWORK_CHANGED("Смена сети",1);

    fun accepts(value: Int): Boolean = when(this) {
        BATTERY_HOT -> value in 300..550
        BATTERY_LOW -> value in 5..80
        THERMAL_SEVERE -> value in 3..6
        POWER_SAVE, NETWORK_UNVALIDATED, NETWORK_CHANGED -> value==1
    }

    fun describe(value: Int): String = when(this) {
        BATTERY_HOT -> "Температура батареи ≥ ${value/10.0} °C"
        BATTERY_LOW -> "Заряд ≤ $value%"
        THERMAL_SEVERE -> "Android Thermal Status ≥ $value"
        POWER_SAVE -> "Энергосбережение включено"
        NETWORK_UNVALIDATED -> "Android не подтверждает соединение с интернетом"
        NETWORK_CHANGED -> "Изменился сетевой транспорт"
    }
}

data class SmartRule(
    val id: Long,
    val packageName: String,
    val kind: SmartRuleKind,
    val threshold: Int,
    val cooldownMs: Long = 120_000,
    val enabled: Boolean = true
) {
    init {
        require(kind.accepts(threshold))
        require(cooldownMs in 30_000..3_600_000)
    }
}

data class SmartRuleAlert(
    val kind: SmartRuleKind,
    val description: String,
    val timeMs: Long,
    val ruleId: Long
)

/**
 * Each activation generates at most one event. Cooldown is additionally enforced
 * across recovery/re-entry. Unknown readings never trigger alerts.
 * One engine belongs to exactly one explicitly started session.
 */
class SmartRulesEngine {
    private val lastFired = mutableMapOf<Long,Long>()
    private val active = mutableMapOf<Long,Boolean>()
    private var lastNetwork: String? = null

    fun evaluate(point: GameTelemetryPoint, rules: List<SmartRule>): List<SmartRuleAlert> {
        val observed = point.networkType.takeIf { it.isNotEmpty() && it != "UNKNOWN" }
        val changed = lastNetwork != null && observed != null && lastNetwork != observed
        val alerts = mutableListOf<SmartRuleAlert>()
        for(rule in rules) {
            if(!rule.enabled) {
                active[rule.id] = false
                continue
            }
            val matches = when(rule.kind) {
                SmartRuleKind.BATTERY_HOT ->
                    point.batteryTempDeciC?.let { it >= rule.threshold } == true
                SmartRuleKind.BATTERY_LOW ->
                    point.batteryPercent?.let { it in 0..rule.threshold } == true
                SmartRuleKind.THERMAL_SEVERE ->
                    point.thermalStatus?.let { it >= rule.threshold } == true
                SmartRuleKind.POWER_SAVE -> point.powerSaving == true
                SmartRuleKind.NETWORK_UNVALIDATED -> point.networkValidated == false
                SmartRuleKind.NETWORK_CHANGED -> changed
            }
            val wasActive=active[rule.id] == true
            if(matches && !wasActive) {
                val prev=lastFired[rule.id]
                if(prev==null || point.timeMs>=prev && point.timeMs-prev >= rule.cooldownMs) {
                    alerts+=SmartRuleAlert(rule.kind,rule.kind.describe(rule.threshold),point.timeMs,rule.id)
                    lastFired[rule.id]=point.timeMs
                }
            }
            active[rule.id]=matches
        }
        if(observed!=null) lastNetwork=observed
        return alerts
    }
}
