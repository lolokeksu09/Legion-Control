package dev.lolokeksu.legioncontrol

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager

/** Reads public APIs only. Every missing OEM field stays null. */
object GameSessionTelemetry {
    fun snapshot(context: Context): GameTelemetryPoint {
        val battery = ResourceTelemetryBackend.readBatteryIntent(context)
        val percent = ResourceTelemetryBackend.batteryPercent(battery)
        val temp = ResourceTelemetryBackend.batteryTemperatureDeciC(battery)
        val current = ResourceTelemetryBackend.readCurrentNow(context)
            ?.takeIf { it in -200_000_000..200_000_000 }
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal = runCatching { power?.currentThermalStatus }.getOrNull()?.takeIf { it in 0..6 }
        val saving = runCatching { power?.isPowerSaveMode }.getOrNull()
        val hz = ResourceTelemetryBackend.refreshRate(ResourceTelemetryBackend.readDisplayMode(context))
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = runCatching {
            if (cm == null) "UNKNOWN" to null
            else {
                val active = cm.activeNetwork
                if (active == null) "NONE" to false
                else {
                    val caps = cm.getNetworkCapabilities(active)
                    if (caps == null) "UNKNOWN" to null else {
                        val type = when {
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                            else -> "OTHER"
                        }
                        type to caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    }
                }
            }
        }.getOrDefault("UNKNOWN" to null)
        return GameTelemetryPoint(System.currentTimeMillis(),percent,temp,current,thermal,saving,hz,net.first,net.second)
    }
}
