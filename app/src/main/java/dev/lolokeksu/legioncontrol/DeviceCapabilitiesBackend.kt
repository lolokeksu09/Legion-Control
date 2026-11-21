package dev.lolokeksu.legioncontrol

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display

/**
 * Read-only platform probe. No permissions, Shizuku, Root, OEM APIs, state changes
 * or background listeners. The caller decides when to collect the snapshot.
 */
object DeviceCapabilitiesBackend {
    fun read(context: Context): PlatformCapabilities {
        val power = runCatching {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }.getOrNull()

        val thermal = power?.let {
            runCatching { it.currentThermalStatus }.getOrNull()
        }
        val sustained = power?.let {
            runCatching { it.isSustainedPerformanceModeSupported }.getOrNull()
        }

        val display = runCatching {
            (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull()
        val currentMode = runCatching { display?.mode?.toReported() }.getOrNull()
        val supported = runCatching {
            display?.supportedModes?.mapNotNull { it.toReported() }.orEmpty()
        }.getOrDefault(emptyList())

        return PlatformCapabilities(
            thermalStatus = CapabilitySanitizer.thermalStatus(thermal),
            sustainedPerformanceSupported = sustained,
            activeDisplayMode = currentMode,
            supportedDisplayModes = CapabilitySanitizer.supportedModes(supported)
        )
    }

    private fun Display.Mode.toReported(): ReportedDisplayMode? =
        CapabilitySanitizer.displayMode(
            modeId = modeId,
            widthPx = physicalWidth,
            heightPx = physicalHeight,
            refreshRateHz = refreshRate
        )
}
