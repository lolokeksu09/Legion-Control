package dev.lolokeksu.legioncontrol

import android.content.Intent
import android.os.BatteryManager
import android.os.Build

/**
 * Reads only fields already exposed by the sticky ACTION_BATTERY_CHANGED intent.
 * No receiver registration, services, extra permissions or automatic monitoring.
 */
object BatteryHealthBackend {
    fun read(battery: Intent?): BatteryHealthSnapshot {
        val sdk = Build.VERSION.SDK_INT
        return BatteryHealthSnapshot(
            healthCode = BatteryHealthSanitizer.health(
                battery.intIfPresent(BatteryManager.EXTRA_HEALTH)
            ),
            cycleCount = if (sdk >= 34) {
                BatteryHealthSanitizer.cycleCount(
                    battery.intIfPresent(BatteryManager.EXTRA_CYCLE_COUNT)
                )
            } else null,
            chargingStatusCode = if (sdk >= 34) {
                BatteryHealthSanitizer.chargingStatus(
                    battery.intIfPresent(BatteryManager.EXTRA_CHARGING_STATUS)
                )
            } else null,
            technology = BatteryHealthSanitizer.technology(
                runCatching { battery?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) }.getOrNull()
            ),
            present = battery.booleanIfPresent(BatteryManager.EXTRA_PRESENT),
            systemBatteryLow = if (sdk >= 28) {
                battery.booleanIfPresent(BatteryManager.EXTRA_BATTERY_LOW)
            } else null
        )
    }

    private fun Intent?.intIfPresent(key: String): Int? = runCatching {
        this?.takeIf { it.hasExtra(key) }?.getIntExtra(key, Int.MIN_VALUE)
    }.getOrNull()?.takeUnless { it == Int.MIN_VALUE }

    private fun Intent?.booleanIfPresent(key: String): Boolean? = runCatching {
        this?.takeIf { it.hasExtra(key) }?.getBooleanExtra(key, false)
    }.getOrNull()
}
