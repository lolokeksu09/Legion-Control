package dev.lolokeksu.legioncontrol

import android.content.Context

/**
 * Unified backend-only entry point for consumer layers added later.
 * Reuses the existing public capability probe; does not touch the UI.
 */
data class DeviceDiagnosticSnapshot(
    val capabilities: PlatformCapabilities,
    val resources: ResourceTelemetrySnapshot
)

object DeviceDiagnosticsBackend {
    fun read(context: Context): DeviceDiagnosticSnapshot =
        DeviceDiagnosticSnapshot(
            capabilities = DeviceCapabilitiesBackend.read(context),
            resources = ResourceTelemetryBackend.read(context)
        )
}
