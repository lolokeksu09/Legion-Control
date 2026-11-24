package dev.lolokeksu.legioncontrol

import android.content.Context
import android.os.SystemClock

/** Separate private presets; no mutation of existing profiles or SQLite. */
object PreflightPresets {
    private const val STORE = "legion-preflight-v1"
    private const val PREFIX = "preset_"
    private const val LIMIT = 256
    private val lock = Any()

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun read(context: Context, pkg: String): PreflightPreset = synchronized(lock) {
        require(GameLaunchProfilePolicy.validPackage(pkg))
        PreflightPreset.decode(prefs(context).getString(PREFIX + pkg, null)) ?: PreflightPreset()
    }

    fun save(context: Context, pkg: String, preset: PreflightPreset): Boolean = synchronized(lock) {
        require(GameLaunchProfilePolicy.validPackage(pkg))
        val storage = prefs(context)
        val key = PREFIX + pkg
        if (!storage.contains(key) && storage.all.keys.count { it.startsWith(PREFIX) } >= LIMIT)
            return@synchronized false
        storage.edit().putString(key,preset.encode()).commit()
    }
}

object GamePreflightBackend {
    fun check(context: Context, app: LaunchableApp, preset: PreflightPreset): PreflightReport {
        require(GameLaunchProfilePolicy.validPackage(app.packageName))
        // Existing permission-free backends report unknown independently.
        val telemetry = runCatching { GameSessionTelemetry.snapshot(context) }.getOrNull()
        val resources = runCatching { ResourceTelemetryBackend.read(context) }.getOrNull()
        val capability = runCatching { DeviceCapabilitiesBackend.read(context) }.getOrNull()
        val pending = runCatching { GameLaunchProfiles.pendingPackage(context) }
        val launchable = runCatching {
            ApplicationInventory.launchIntent(context, app.packageName) != null
        }.getOrNull()
        val reading = PreflightReading(
            battery = telemetry?.batteryPercent,
            temperatureDeciC = telemetry?.batteryTempDeciC,
            thermal = telemetry?.thermalStatus ?: capability?.thermalStatus,
            powerSaving = telemetry?.powerSaving,
            displayHz = telemetry?.refreshHz ?: capability?.activeDisplayMode?.refreshRateHz,
            supportedMaxHz = capability?.supportedDisplayModes?.maxOfOrNull { it.refreshRateHz },
            availableRamBytes = resources?.systemMemory?.value?.availableBytes,
            androidLowRam = resources?.systemMemory?.value?.lowMemory,
            network = telemetry?.networkType,
            validatedNetwork = telemetry?.networkValidated,
            launchable = launchable,
            captureBusy = CaptureCoordinator.busy(),
            sessionActive = GameSessionRecorderService.sessionActive,
            pendingProfile = pending.getOrNull(),
            pendingProfileKnown = pending.isSuccess
        )
        return PreflightReport(app.packageName, app.label, preset,
            SystemClock.elapsedRealtime(),System.currentTimeMillis(),
            PreflightRules.evaluate(preset,reading))
    }
}
