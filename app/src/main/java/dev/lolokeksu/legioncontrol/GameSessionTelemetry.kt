package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.view.Display

/** Reads public APIs only. Every missing OEM field stays null. */
object GameSessionTelemetry {
    /** Hub only displays battery level and refresh rate; skip unrelated Binder reads. */
    fun hubSignals(context:Context):Pair<Int?,Float?> {
        val intent=runCatching {
            context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level=intent?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
        val scale=intent?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
        val percent=if(level>=0 && scale>0 && level<=scale) (level.toLong()*100/scale).toInt() else null
        val hz=runCatching {
            val display=context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            display?.getDisplay(Display.DEFAULT_DISPLAY)?.mode?.refreshRate
        }.getOrNull()?.takeIf{it.isFinite() && it in 1f..500f}
        return percent to hz
    }

    fun snapshot(context: Context): GameTelemetryPoint {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
        val percent = if(level >= 0 && scale > 0 && level <= scale)
            (level.toLong()*100/scale).toInt() else null
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)
            ?.takeIf { it in -400..1200 }
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val current = runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
            .getOrNull()?.takeIf { it != Int.MIN_VALUE && it in -200_000_000..200_000_000 }
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal = runCatching { power?.currentThermalStatus }.getOrNull()?.takeIf { it in 0..6 }
        val saving = runCatching { power?.isPowerSaveMode }.getOrNull()
        val display = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val hz = runCatching {
            display?.getDisplay(Display.DEFAULT_DISPLAY)?.mode?.refreshRate
        }.getOrNull()?.takeIf { it.isFinite() && it in 1f..500f }
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
