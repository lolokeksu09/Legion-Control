package dev.lolokeksu.legioncontrol

import java.util.Locale

/**
 * Public Android battery broadcast fields (NOT a percentage battery-health API).
 * All values are nullable: absence and OEM-unsupported fields are not zeros.
 */
data class BatteryHealthSnapshot(
    val healthCode: Int?,
    val cycleCount: Int?,
    val chargingStatusCode: Int?,
    val technology: String?,
    val present: Boolean?,
    val systemBatteryLow: Boolean?
)

object BatteryHealthSanitizer {
    fun health(code: Int?): Int? = code?.takeIf { it in 2..7 }

    // 0 is legitimate for a new gauge, but OEMs may provide a placeholder.
    fun cycleCount(value: Int?): Int? = value?.takeIf { it in 0..100_000 }

    /** Android does not publish a portable meaning for all OEM charging codes. */
    fun chargingStatus(code: Int?): Int? = code?.takeIf { it in 1..100 }

    fun technology(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 60 && it.all { ch ->
            ch.code >= 32 && ch.code != 127 && ch != '\u2028' && ch != '\u2029'
        } }

    /**
     * An electrical estimate **at the battery terminals**, not negotiated
     * USB-PD power, adapter power, or wall power. The OEM current may be wrong.
     */
    fun batteryPowerWatts(voltageMilliVolts: Int?, currentMicroAmps: Int?): Double? {
        val mv = voltageMilliVolts?.takeIf { it in 500..20_000 } ?: return null
        val ua = currentMicroAmps?.takeIf {
            it != Int.MIN_VALUE && it in -50_000_000..50_000_000
        } ?: return null
        return mv.toDouble() * ua.toDouble() / 1_000_000_000.0
    }

    fun healthLabel(code: Int?): String = when (code) {
        2 -> "GOOD · Android не обнаружил проблему"
        3 -> "OVERHEAT · перегрев"
        4 -> "DEAD · критическое состояние"
        5 -> "OVER_VOLTAGE · повышенное напряжение"
        6 -> "UNSPECIFIED_FAILURE · аппаратная ошибка"
        7 -> "COLD · низкая температура"
        else -> "нет данных"
    }
}

object BatteryHealthReport {
    fun format(
        snapshot: BatteryHealthSnapshot,
        voltageMilliVolts: Int?,
        currentMicroAmps: Int?
    ): String = buildString {
        append("СОСТОЯНИЕ БАТАРЕИ · публичный Android API\n")
        append("Health: ").append(
            BatteryHealthSanitizer.healthLabel(snapshot.healthCode)
        ).append('\n')
        append("Циклы зарядки (сообщает Android): ")
            .append(snapshot.cycleCount?.toString() ?: "нет данных")
            .append('\n')
        append("Дополнительный статус зарядки (код Android): ")
            .append(snapshot.chargingStatusCode?.toString() ?: "нет данных")
            .append('\n')
        append("Технология батареи: ").append(snapshot.technology ?: "нет данных")
            .append('\n')
        append("Батарея обнаружена Android: ").append(when (snapshot.present) {
            true -> "да"
            false -> "нет"
            null -> "нет данных"
        }).append('\n')
        append("Системный признак низкого заряда: ").append(when (snapshot.systemBatteryLow) {
            true -> "да"
            false -> "нет"
            null -> "нет данных"
        }).append('\n')
        append("Расчётная мощность на клеммах батареи: ")
            .append(BatteryHealthSanitizer.batteryPowerWatts(
                voltageMilliVolts, currentMicroAmps
            )?.let { String.format(Locale.US, "%+.2f Вт", it) } ?: "нет данных")
            .append('\n')
        append("GOOD — не процент износа. Циклы и код зарядки зависят от прошивки. ")
        append("Мощность рассчитана по показаниям батареи, не измерена на зарядном устройстве; ")
        append("она не подтверждает мощность адаптера или наличие Bypass.")
    }
}
