package dev.lolokeksu.legioncontrol

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.view.Display
import android.os.BatteryManager
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock

/**
 * Public Android API only. Explicit, on-demand reads:
 * no Shizuku, Root, dangerous permissions, listeners, hardware writes or
 * background sampling. Each metric reports failure independently.
 */
object ResourceTelemetryBackend {
    private val thermalGate = ThermalHeadroomGate()

    fun read(context: Context): ResourceTelemetrySnapshot {
        val elapsed = SystemClock.elapsedRealtime()
        val counters = readBatteryCounters(context)
        return ResourceTelemetrySnapshot(
            elapsedRealtimeMs = elapsed,
            systemMemory = readSystemMemory(context),
            appFilesystem = readAppFilesystem(context),
            onlineProcessorCount = ProbeResult.read(
                { Runtime.getRuntime().availableProcessors() },
                TelemetryValidation::onlineProcessors
            ),
            batteryChargeCounterMicroAh = counters.charge,
            batteryCurrentNowMicroAmps = counters.currentNow,
            batteryCurrentAverageMicroAmps = counters.currentAverage,
            batteryEnergyCounterNanoWh = counters.energy,
            thermalHeadroom = readThermalHeadroom(context, elapsed)
        )
    }

    internal fun readBatteryIntent(context: Context): Intent? = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()

    internal fun batteryPercent(battery: Intent?): Int? = TelemetryValidation.batteryPercent(
        battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    )

    internal fun batteryTemperatureDeciC(battery: Intent?): Int? =
        battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it in -400..1200 }

    internal fun readDisplayMode(context: Context): Display.Mode? = runCatching {
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
    }.getOrNull()

    internal fun refreshRate(mode: Display.Mode?): Float? =
        mode?.refreshRate?.takeIf { it.isFinite() && it in 1f..500f }

    /** One-shot current value in µA. Each consumer keeps its existing validity range. */
    internal fun readCurrentNow(context: Context): Int? =
        batteryCurrentNow(batteryService(context)) { it?.takeUnless { raw -> raw == Int.MIN_VALUE } }.value

    /** Reused by manual battery screens without probing unrelated services. */
    internal fun readBatteryCounters(context: Context): BatteryCounterSnapshot {
        val battery = batteryService(context)
        return BatteryCounterSnapshot(
            batteryProperty(battery, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,
                TelemetryValidation::chargeCounterMicroAh),
            batteryCurrentNow(battery, TelemetryValidation::currentMicroAmps),
            batteryProperty(battery, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE,
                TelemetryValidation::currentMicroAmps),
            battery.flatMap { service ->
                ProbeResult.read({ service.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) },
                    TelemetryValidation::energyCounterNanoWh)
            }
        )
    }

    private fun batteryService(context: Context): ProbeResult<BatteryManager> = try {
        val service = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (service == null) ProbeResult.unavailable() else ProbeResult.available(service)
    } catch (_: Exception) {
        ProbeResult.failure()
    }

    private fun batteryCurrentNow(
        service: ProbeResult<BatteryManager>, normalize: (Int?) -> Int?
    ): ProbeResult<Int> = batteryProperty(service, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, normalize)

    /** Only the two resource fields required by the frequent live monitor. */
    fun readForMonitor(context: Context): MonitorResourceSnapshot =
        MonitorResourceSnapshot(readSystemMemory(context),
            readThermalHeadroom(context, SystemClock.elapsedRealtime()))

    internal fun readSystemMemory(context: Context): ProbeResult<MemoryMetrics> {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return ProbeResult.unavailable()
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            val validated = TelemetryValidation.memory(
                totalBytes = memory.totalMem,
                availableBytes = memory.availMem,
                thresholdBytes = memory.threshold,
                lowMemory = memory.lowMemory
            )
            if (validated == null) ProbeResult.unavailable()
            else ProbeResult.available(validated)
        } catch (_: Exception) {
            ProbeResult.failure()
        }
    }

    internal fun readAppFilesystem(context: Context): ProbeResult<StorageMetrics> {
        return try {
            val stats = StatFs(context.filesDir.absolutePath)
            val validated = TelemetryValidation.storage(stats.totalBytes, stats.availableBytes)
            if (validated == null) ProbeResult.unavailable()
            else ProbeResult.available(validated)
        } catch (_: Exception) {
            ProbeResult.failure()
        }
    }

    internal fun readThermalHeadroom(
        context: Context, nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ): ProbeResult<Float> {
        return try {
            val power = context.getSystemService(Context.POWER_SERVICE)
                as? PowerManager ?: return ProbeResult.unavailable()
            thermalGate.sample(nowElapsedMs) { power.getThermalHeadroom(0) }
        } catch (_: Exception) {
            ProbeResult.failure()
        }
    }

    private fun batteryProperty(
        service: ProbeResult<BatteryManager>, propertyId: Int,
        normalize: (Int?) -> Int?
    ): ProbeResult<Int> = service.flatMap { manager ->
        ProbeResult.read({ manager.getIntProperty(propertyId) }, normalize)
    }
}
