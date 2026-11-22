package dev.lolokeksu.legioncontrol

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Connected output device types: not the selected route, and not an acoustic test. */
object AudioRouteInspector {
    fun outputTypes(context:Context):List<Int> {
        val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .map { it.type }.distinct().sorted()
        }.getOrDefault(emptyList())
    }

    fun label(type:Int):String=when(type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Встроенный динамик"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Разговорный динамик"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Проводные наушники"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Проводная гарнитура"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB гарнитура"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE гарнитура"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE динамик"
        else -> "Android AudioDevice type $type"
    }

    fun snapshot(context:Context):String {
        val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val types=outputTypes(context)
        val current=manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max=manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return buildString {
            append("AUDIO ROUTING · подключённые устройства\n")
            if(types.isEmpty()) append("Android не вернул список выходов.\n")
            else types.forEach { append("• ").append(label(it)).append(" (type=").append(it).append(")\n") }
            append("Системная громкость MEDIA: ").append(current).append("/").append(max).append("\n")
            append("Выходы выше — ДОСТУПНЫЕ устройства, не доказательство текущего маршрута.\n")
            append("Для проверки левого/правого канала пользователь должен услышать тест сам.")
        }
    }
}

/** Only while the audio lab is visible; no service or background history capture. */
class AudioRouteMonitor(
    context:Context,
    private val onUpdate:(String)->Unit
) {
    private val app=context.applicationContext
    private val manager=app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var attached=false
    private val callback=object:AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices:Array<AudioDeviceInfo>) {
            onUpdate("Список выходов обновился:\n"+AudioRouteInspector.snapshot(app))
        }
        override fun onAudioDevicesRemoved(removedDevices:Array<AudioDeviceInfo>) {
            onUpdate("Выход отключён:\n"+AudioRouteInspector.snapshot(app))
        }
    }
    fun start() {
        if(attached)return
        attached=true
        manager.registerAudioDeviceCallback(callback,Handler(Looper.getMainLooper()))
    }
    fun stop() {
        if(!attached)return
        attached=false
        manager.unregisterAudioDeviceCallback(callback)
    }
}
