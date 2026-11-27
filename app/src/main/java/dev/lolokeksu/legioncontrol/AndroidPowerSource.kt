package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager

/** Public Android battery signals; not a charging controller or thermal diagnosis. */
data class PowerReading(
    val chargingStatus: String,
    val chargingSource: String,
    val voltageMillivolts: Int?,
    val currentMicroAmps: Int?,
    val powerSaving: Boolean?
)

object AndroidPowerSource {
    fun read(context: Context, battery: Intent?): PowerReading {
        val status = battery?.getIntExtra(
            BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val chargingStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Заряжается"
            BatteryManager.BATTERY_STATUS_FULL -> "Заряжена"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Разряжается"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Не заряжается"
            else -> "Неизвестно"
        }

        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingSource = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "Сеть"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Беспроводная зарядка"
            plugged and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> "Док-станция"
            plugged == 0 -> "Не подключена"
            else -> "Неизвестно"
        }

        val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.takeIf { it in 500..20_000 }

        // Android BatteryManager reports CURRENT_NOW in microamperes.
        // Values and direction depend on the manufacturer, so do NOT infer
        // true charging efficiency, Bypass, health or instantaneous wattage.
        val current = TelemetryValidation.currentMicroAmps(ResourceTelemetryBackend.readCurrentNow(context))

        val powerSaving = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isPowerSaveMode
        }.getOrNull()

        return PowerReading(
            chargingStatus = chargingStatus,
            chargingSource = chargingSource,
            voltageMillivolts = voltage,
            currentMicroAmps = current,
            powerSaving = powerSaving
        )
    }
}
