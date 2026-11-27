package dev.lolokeksu.legioncontrol

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager

internal object BatteryLabSource {
    fun read(context:Context,elapsed:Long):BatteryLabPoint? {
        val intent=ResourceTelemetryBackend.readBatteryIntent(context) ?: return null
        val bm=context.getSystemService(BatteryManager::class.java)
        val power=context.getSystemService(PowerManager::class.java)
        fun intProp(id:Int)=runCatching{bm?.getIntProperty(id)}.getOrNull()?.takeUnless{it==Int.MIN_VALUE}
        fun longProp(id:Int)=runCatching{bm?.getLongProperty(id)}.getOrNull()?.takeUnless{it==Long.MIN_VALUE}
        val status=when(intent.getIntExtra(BatteryManager.EXTRA_STATUS,-1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryLabStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryLabStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> BatteryLabStatus.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryLabStatus.NOT_CHARGING
            else -> BatteryLabStatus.UNKNOWN
        }
        val plug=when(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)) {
            0 -> BatteryLabPlug.NONE
            BatteryManager.BATTERY_PLUGGED_AC -> BatteryLabPlug.AC
            BatteryManager.BATTERY_PLUGGED_USB -> BatteryLabPlug.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> BatteryLabPlug.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> BatteryLabPlug.DOCK
            -1 -> BatteryLabPlug.UNKNOWN
            else -> BatteryLabPlug.OTHER
        }
        return BatteryLabPoint(elapsed,ResourceTelemetryBackend.batteryPercent(intent),
            ResourceTelemetryBackend.batteryTemperatureDeciC(intent),
            intProp(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeIf{it in -50_000_000..50_000_000},
            intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1).takeIf{it in 500..20_000},
            intProp(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf{it in 0..100_000_000},
            longProp(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)?.takeIf{it in 0L..1_000_000_000_000L},
            if(status==BatteryLabStatus.CHARGING) runCatching{bm?.computeChargeTimeRemaining()}.getOrNull()?.takeIf{it in 0L..604_800_000L} else null,
            status,plug,runCatching{power?.currentThermalStatus}.getOrNull()?.takeIf{it in 0..6})
    }
}
