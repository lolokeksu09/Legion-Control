package dev.lolokeksu.legioncontrol

import android.app.ActivityManager
import android.content.Context
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
        val battery: ProbeResult<BatteryManager> = try {
            val service = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (service == null) ProbeResult.unavailable() else ProbeResult.available(service)
        } catch (_: Exception) {
            ProbeResult.failure()
        }

        return ResourceTelemetrySnapshot(
            elapsedRealtimeMs = elapsed,
            systemMemory = readSystemMemory(context),
            appFilesystem = readAppFilesystem(context),
            onlineProcessorCount = ProbeResult.read(
                { Runtime.getRuntime().availableProcessors() },
                TelemetryValidation::onlineProcessors
            ),
            batteryChargeCounterMicroAh = batteryProperty(
                battery, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,
                TelemetryValidation::chargeCounterMicroAh
            ),
            batteryCurrentNowMicroAmps = batteryProperty(
                battery, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,
                TelemetryValidation::currentMicroAmps
            ),
            batteryCurrentAverageMicroAmps = batteryProperty(
                battery, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE,
                TelemetryValidation::currentMicroAmps
            ),
            batteryEnergyCounterNanoWh = battery.flatMap { service ->
                ProbeResult.read(
                    { service.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) },
                    TelemetryValidation::energyCounterNanoWh
                )
            },
            thermalHeadroom = readThermalHeadroom(context, elapsed)
        )
    }

    /** Only the two resource fields required by the frequent live monitor. */
    fun readForMonitor(context: Context): MonitorResourceSnapshot =
        MonitorResourceSnapshot(readSystemMemory(context),
            readThermalHeadroom(context, SystemClock.elapsedRealtime()))

    private fun readSystemMemory(context: Context): ProbeResult<MemoryMetrics> {
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

    private fun readAppFilesystem(context: Context): ProbeResult<StorageMetrics> {
        return try {
            val stats = StatFs(context.filesDir.absolutePath)
            val validated = TelemetryValidation.storage(stats.totalBytes, stats.availableBytes)
            if (validated == null) ProbeResult.unavailable()
            else ProbeResult.available(validated)
        } catch (_: Exception) {
            ProbeResult.failure()
        }
    }

    private fun readThermalHeadroom(
        context: Context, nowElapsedMs: Long
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
