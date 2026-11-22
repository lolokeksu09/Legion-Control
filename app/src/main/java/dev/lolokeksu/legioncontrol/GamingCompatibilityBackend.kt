package dev.lolokeksu.legioncontrol

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaDrm
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import java.util.UUID
import java.util.Locale

/** Read-only: no refresh-rate changes, audio routing changes or DRM license requests. */
object DisplayModeInspector {
    fun inspect(display: Display?): String {
        if (display == null) return "DISPLAY MODES · дисплей недоступен"
        return buildString {
            val mode = display.mode
            append("DISPLAY MODES · режимы, заявленные Android\n")
            append("Текущий: ").append(mode.physicalWidth).append("×")
                .append(mode.physicalHeight).append(" @ ")
                .append(String.format(Locale.US, "%.1f Гц",mode.refreshRate)).append("\n")
            val modes = display.supportedModes.sortedWith(
                compareBy({it.physicalWidth},{it.physicalHeight},{it.refreshRate})
            ).distinctBy { Triple(it.physicalWidth,it.physicalHeight,it.refreshRate) }
            append("Объявленных сочетаний: ").append(modes.size).append('\n')
            for (item in modes.take(20)) {
                append("· ").append(item.physicalWidth).append("×")
                    .append(item.physicalHeight).append(" @ ")
                    .append(String.format(Locale.US, "%.1f Гц",item.refreshRate)).append('\n')
            }
            val hdr = runCatching { display.hdrCapabilities.supportedHdrTypes.toList() }
                .getOrDefault(emptyList())
            append("HDR типы: ").append(if (hdr.isEmpty()) "не заявлены/недоступны"
            else hdr.distinct().joinToString { when(it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                else -> "тип " + it
            } }).append('\n')
            append("Широкая цветовая гамма: ")
                .append(runCatching { display.isWideColorGamut }.getOrNull()?.toString()
                    ?: "неизвестно").append('\n')
            append("Поддерживаемый режим — не FPS игры и не разрешение на изменение частоты.")
        }
    }
}

object AudioOutputInspector {
    fun inspect(context: Context): String {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "AUDIO OUTPUT · AudioManager недоступен"
        val outputs = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrDefault(emptyArray())
        return buildString {
            append("AUDIO OUTPUT · доступные выходы Android\n")
            append("Выходов: ").append(outputs.size).append('\n')
            for (out in outputs.take(16)) {
                val type = when(out.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Встроенный динамик"
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Разговорный динамик"
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Проводные наушники"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Гарнитура"
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-аудио"
                    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                    else -> "Android тип " + out.type
                }
                append("· ").append(type).append(" · ")
                    .append(out.productName.toString().take(70)).append('\n')
                if (out.sampleRates.isNotEmpty()) append("  PCM частоты: ")
                    .append(out.sampleRates.take(10).joinToString()).append(" Гц\n")
                if (out.channelCounts.isNotEmpty()) append("  Каналы: ")
                    .append(out.channelCounts.take(8).joinToString()).append('\n')
            }
            append("Оптимальная частота: ")
                .append(manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                    ?: "неизвестно").append(" Гц\n")
            append("Буфер: ")
                .append(manager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                    ?: "неизвестно").append(" кадров\n")
            append("Не измеряет задержку звука и не подтверждает текущий маршрут игры.")
        }
    }
}

object ControllerInventory {
    fun connected(): List<InputDevice> = InputDevice.getDeviceIds()
        .map { id -> InputDevice.getDevice(id) }
        .filterNotNull()
        .filter {
            (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (it.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }

    fun format(devices: List<InputDevice>): String = buildString {
        append("GAMEPAD INVENTORY · доступные Android контроллеры\n")
        append("Найдено: ").append(devices.size).append('\n')
        for (device in devices.take(12)) {
            append("· ").append(device.name.take(90)).append(" · id=").append(device.id)
                .append(" · vendor=").append(device.vendorId)
                .append(" product=").append(device.productId).append('\n')
            append("  Аналоговых осей: ")
                .append(device.motionRanges.count {
                    (it.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
                }).append('\n')
        }
        append("Кнопки и стики проверяются только на открытом экране этого приложения.")
    }

    fun axes(event: MotionEvent): String {
        val d = event.device ?: return "Контроллер не определён"
        val ranges = d.motionRanges.filter {
            (it.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
        }.take(12)
        return buildString {
            append("GAMEPAD LIVE · сигналы внутри Legion Control\n")
            append("Устройство: ").append(d.name.take(80)).append('\n')
            if (ranges.isEmpty()) append("Аналоговые оси не заявлены.\n")
            for (r in ranges) {
                append(MotionEvent.axisToString(r.axis)).append(": ")
                    .append(String.format(Locale.US,"%+.3f",event.getAxisValue(r.axis)))
                    .append(" · deadzone ")
                    .append(String.format(Locale.US,"%.3f",r.flat)).append('\n')
            }
        }
    }
}

object DrmCapabilityInspector {
    private val widevine = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    fun inspect(): String {
        if (!runCatching { MediaDrm.isCryptoSchemeSupported(widevine) }.getOrDefault(false)) {
            return "DRM · Widevine не заявлен Android или недоступен"
        }
        return runCatching {
            MediaDrm(widevine).use { drm ->
                val level = runCatching { drm.getPropertyString("securityLevel") }
                    .getOrNull()?.take(20) ?: "недоступен"
                val vendor = runCatching { drm.getPropertyString("vendor") }
                    .getOrNull()?.take(70) ?: "неизвестен"
                "DRM · Widevine API доступен\n" +
                    "Уровень защиты (сведения DRM API): " + level + "\n" +
                    "Поставщик: " + vendor + "\n" +
                    "Без открытия сессии и запроса лицензии. HD/4K воспроизведение не проверялось."
            }
        }.getOrElse { "DRM · API доступен, но свойства недоступны: " + it.javaClass.simpleName }
    }
}

object GamingReadinessSnapshot {
    fun read(context: Context, display: Display?): GamingReadinessInput {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal = runCatching { power?.currentThermalStatus }.getOrNull()
        val saving = runCatching { power?.isPowerSaveMode }.getOrNull()
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?.takeIf { it in 0..100 }
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)
            ?.takeIf { it in -400..1200 }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = runCatching { cm?.activeNetwork }.getOrNull()
        val caps = runCatching { active?.let { cm?.getNetworkCapabilities(it) } }.getOrNull()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        val lowMem = runCatching {
            if (am == null) null else { am.getMemoryInfo(info); info.lowMemory }
        }.getOrNull()
        return GamingReadinessInput(
            level, temp, thermal, saving,
            runCatching { display?.mode?.refreshRate }.getOrNull(),
            if (cm == null) null else active != null,
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            lowMem
        )
    }
}

object QuickComputeProbe {
    fun run(context: Context): String {
        val current = GamingReadinessSnapshot.read(context, null)
        if ((current.thermalStatus ?: 0) >= 3 ||
            (current.batteryTemperatureDeciC ?: 0) >= 450) {
            return "COMPUTE PROBE · пропущен из-за сильного нагрева"
        }
        val start = SystemClock.elapsedRealtimeNanos()
        val result = ComputeProbeMath.checksum(
            ComputeProbeMath.ITERATIONS,
            stopRequested = { Thread.currentThread().isInterrupted }
        )
        return ComputeProbeMath.format(ComputeProbeResult(
            ComputeProbeMath.ITERATIONS,SystemClock.elapsedRealtimeNanos()-start,result
        ))
    }
}
