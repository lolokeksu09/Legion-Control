package dev.lolokeksu.legioncontrol

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import java.util.Locale

object DeviceInsights {
    fun battery(context: Context): String {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        fun int(id: Int): Int? = runCatching { manager?.getIntProperty(id) }
            .getOrNull()?.takeUnless { it == Int.MIN_VALUE || it < 0 }
        fun long(id: Int): Long? = runCatching { manager?.getLongProperty(id) }
            .getOrNull()?.takeUnless { it == Long.MIN_VALUE || it < 0 }
        val charge = int(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val energy = long(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        return buildString {
            append("BATTERY INTELLIGENCE · Android BatteryManager\n")
            append("Остаточный заряд: ")
                .append(charge?.let { String.format(Locale.US, "%.1f мА·ч", it/1000.0) }
                    ?: "нет данных").append("\n")
            append("Счётчик энергии: ")
                .append(energy?.let { String.format(Locale.US, "%.2f мВт·ч", it/1_000_000.0) }
                    ?: "нет данных").append("\n")
            append("Показания предоставляются прошивкой; не означают исходную ёмкость или износ.")
        }
    }

    fun thermal(context: Context): String {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val status = runCatching { power?.currentThermalStatus }.getOrNull()
        // Rate-limited by Android; invoked only on user's direct request.
        val headroom = runCatching { power?.getThermalHeadroom(0) }
            .getOrNull()?.takeIf { it.isFinite() && it >= 0f }
        return buildString {
            append("THERMAL GUARD · текущий Android status\n")
            append("Статус: ").append(ThermalSeverity.describe(status)).append("\n")
            append("Thermal Headroom: ").append(headroom?.let {
                String.format(Locale.US, "%.2f", it)
            } ?: "недоступен / ограничен Android").append("\n")
            if (status != null && status >= 3) {
                append("ВНИМАНИЕ: серьёзное тепловое ограничение, не запускайте нагрузочный тест.\n")
            }
            append("Это системная оценка теплового состояния, не температура CPU/GPU.")
        }
    }

    fun network(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return "Wi-Fi/LTE INSPECTOR · сервис недоступен"
        val active = runCatching { manager.activeNetwork }.getOrNull()
            ?: return "Wi-Fi/LTE INSPECTOR · активного подключения нет"
        val caps = runCatching { manager.getNetworkCapabilities(active) }.getOrNull()
            ?: return "Wi-Fi/LTE INSPECTOR · параметры недоступны"
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Сотовая сеть"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Другой транспорт"
        }
        // Wi-Fi information may be redacted by Android without location access.
        val wifi = caps.transportInfo as? WifiInfo
        return buildString {
            append("WI-FI / LTE INSPECTOR · публичные Android API\n")
            append("Активный транспорт: ").append(transport).append("\n")
            append("Доступ к интернету заявлен: ")
                .append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).append("\n")
            append("Подключение валидировано Android: ")
                .append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).append("\n")
            append("Оценка полосы канала системой: ↓")
                .append(caps.linkDownstreamBandwidthKbps).append(" / ↑")
                .append(caps.linkUpstreamBandwidthKbps).append(" кбит/с\n")
            wifi?.let {
                append("RSSI Wi-Fi: ")
                    .append(it.rssi.takeIf { rssi -> rssi in -125..-1 }?.let { rssi -> "$rssi dBm" }
                        ?: "скрыт или недоступен").append("\n")
                append("Link RX/TX: ")
                    .append(it.rxLinkSpeedMbps.takeIf { speed -> speed >= 0 }?.toString()
                        ?: "н/д").append(" / ")
                    .append(it.txLinkSpeedMbps.takeIf { speed -> speed >= 0 }?.toString()
                        ?: "н/д").append(" Мбит/с\n")
            }
            append("Пропускная способность — оценка Android, не измеренная скорость.\n")
            append("Точная LTE/5G технология без дополнительных прав не определяется.")
        }
    }

    fun system(context: Context, display: Display?): String {
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        runCatching { activity?.getMemoryInfo(info) }
        val stat = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        fun gib(bytes: Long) = String.format(Locale.US, "%.2f ГиБ", bytes/1073741824.0)
        return buildString {
            append("SYSTEM HEALTH · RAM и хранилище\n")
            append("RAM доступно/всего: ")
                .append(if (info.totalMem > 0L) gib(info.availMem)+" / "+gib(info.totalMem)
                        else "недоступно").append("\n")
            append("Android lowMemory: ").append(info.lowMemory).append("\n")
            append("Хранилище доступно/всего: ")
                .append(stat?.let { gib(it.availableBytes)+" / "+gib(it.totalBytes) }
                    ?: "недоступно").append("\n")
            append("PSS собственного процесса: ")
                .append(runCatching { Debug.getPss()/1024 }.getOrNull()?.let { "$it МиБ" }
                    ?: "нет данных").append("\n")
            append("Текущий режим дисплея: ")
                .append(display?.mode?.refreshRate?.let {
                    String.format(Locale.US, "%.1f Гц", it)
                } ?: "недоступен").append("\n")
            append("Не измеряет чужие процессы, реальные частоты CPU/GPU или FPS игр.")
        }
    }
}

/** Real battery-terminal current/voltage; not the charger adapter output. */
data class ChargeReading(
    val timestampMs: Long,
    val percent: Int?,
    val temperatureDeciC: Int?,
    val voltageMillivolts: Int?,
    val currentMicroAmps: Int?,
    val state: String
)

object ChargingDiagnostics {
    fun read(context: Context): ChargeReading {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val current = runCatching {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()?.takeUnless { it == Int.MIN_VALUE }
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it >= 0 }
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it in 0..100 }
        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Заряжается"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Разряжается"
            BatteryManager.BATTERY_STATUS_FULL -> "Полная"
            else -> "Статус неизвестен"
        }
        return ChargeReading(System.currentTimeMillis(), level, temp, voltage, current, state)
    }

    fun format(current: ChargeReading, previous: ChargeReading?): String = buildString {
        append("CHARGING ANALYZER · показания на клеммах батареи\n")
        append("Статус: ").append(current.state).append("\n")
        append("Заряд: ").append(current.percent?.toString() ?: "н/д").append(" %\n")
        append("Температура: ").append(current.temperatureDeciC?.let {
            String.format(Locale.US, "%.1f °C", it/10.0)
        } ?: "н/д").append("\n")
        append("Напряжение: ").append(current.voltageMillivolts?.toString() ?: "н/д")
            .append(" мВ\n")
        append("Ток Android: ").append(current.currentMicroAmps?.let {
            String.format(Locale.US, "%+.0f мА", it/1000.0)
        } ?: "н/д").append("\n")
        if (current.voltageMillivolts != null && current.currentMicroAmps != null) {
            append("Произведение U×I батареи: ")
                .append(String.format(
                    Locale.US, "%+.2f Вт",
                    current.voltageMillivolts.toDouble() * current.currentMicroAmps /
                        1_000_000_000.0
                )).append("\n")
        }
        if (previous != null) {
            val deltaMs = current.timestampMs - previous.timestampMs
            append("От прошлого ручного замера: ").append(deltaMs/1000L).append(" с\n")
            if (previous.temperatureDeciC != null && current.temperatureDeciC != null) {
                append("Δ температура: ")
                    .append(String.format(Locale.US, "%+.1f °C",
                        (current.temperatureDeciC-previous.temperatureDeciC)/10.0))
                    .append("\n")
            }
            if (previous.currentMicroAmps != null && current.currentMicroAmps != null) {
                append("Δ ток: ")
                    .append(String.format(Locale.US, "%+.0f мА",
                        (current.currentMicroAmps-previous.currentMicroAmps)/1000.0))
                    .append("\n")
            }
        }
        append("Знак тока задаёт прошивка; это НЕ мощность адаптера и НЕ Bypass.")
    }
}
