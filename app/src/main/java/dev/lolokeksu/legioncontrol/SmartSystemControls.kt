package dev.lolokeksu.legioncontrol

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

/** Public-API actions only, never privileged settings or implicit per-game changes. */
object SmartSystemControls {
    private const val PREFS="legion-smart-system-undo"
    fun status(context: Context): String {
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val notification=context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val music=runCatching {
            if(audio==null) "н/д" else {
                val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val current=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                "$current/$max"
            }
        }.getOrDefault("н/д")
        val policy=runCatching { notification?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)
        val write=runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
        val notify=context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        return "Звук мультимедиа: $music\n" +
            "«Не беспокоить»: "+if(policy) "доступ разрешён" else "требуется доступ" +
            "\nИзменение системных параметров: "+if(write) "разрешено" else "нужен WRITE_SETTINGS" +
            "\nУведомления: "+if(notify) "разрешены" else "отключены/не разрешены"
    }

    fun setMediaVolume(context: Context, percent: Int): String {
        require(percent in 0..100)
        return runCatching {
            val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,(max*percent/100f).roundToInt(),0)
            "Громкость мультимедиа установлена: $percent% (с учётом шагов устройства)."
        }.getOrElse { "Не удалось изменить громкость: ${it.javaClass.simpleName}" }
    }

    fun setDoNotDisturb(context: Context, enabled: Boolean): String {
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return "Сначала разрешите доступ к режиму «Не беспокоить» в системных настройках."
        return runCatching {
            nm.setInterruptionFilter(if(enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL)
            "Запрошено: «Не беспокоить» "+if(enabled) "включить" else "выключить"
        }.getOrElse { "Прошивка отклонила изменение: ${it.javaClass.simpleName}" }
    }

    fun writeSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
        data=Uri.parse("package:"+context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun setScreenTimeout(context: Context, seconds: Int): String {
        require(seconds in listOf(30,60,120,300,600))
        if(!Settings.System.canWrite(context)) return "Нужно разрешить «Изменение системных настроек»."
        return runCatching {
            val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            if(!prefs.contains("timeout")) {
                prefs.edit().putInt("timeout",Settings.System.getInt(context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT)).apply()
            }
            check(Settings.System.putInt(context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,seconds*1000))
            "Тайм-аут экрана изменён на $seconds с. Исходное значение сохранено для ручного восстановления."
        }.getOrElse { "Не удалось изменить тайм-аут: ${it.javaClass.simpleName}" }
    }

    fun setBrightness(context: Context, percent: Int): String {
        require(percent in 10..100)
        if(!Settings.System.canWrite(context)) return "Нужно разрешить «Изменение системных настроек»."
        return runCatching {
            val resolver=context.contentResolver
            val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            if(!prefs.contains("brightness")) {
                prefs.edit().putInt("brightness",Settings.System.getInt(resolver,
                    Settings.System.SCREEN_BRIGHTNESS)).putInt("brightness_mode",
                    Settings.System.getInt(resolver,Settings.System.SCREEN_BRIGHTNESS_MODE)).apply()
            }
            check(Settings.System.putInt(resolver,Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
            check(Settings.System.putInt(resolver,Settings.System.SCREEN_BRIGHTNESS,
                (percent*255/100f).roundToInt().coerceIn(1,255)))
            "Яркость изменена на $percent%. Автояркость отключена; восстановить исходную можно кнопкой."
        }.getOrElse { "Не удалось изменить яркость: ${it.javaClass.simpleName}" }
    }

    fun restoreScreenSettings(context: Context): String {
        if(!Settings.System.canWrite(context)) return "Нужно разрешение WRITE_SETTINGS для восстановления."
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        if(!prefs.contains("brightness") && !prefs.contains("timeout")) return "Сохранённых параметров нет."
        return runCatching {
            val resolver=context.contentResolver
            var success=true
            if(prefs.contains("brightness")) {
                success=Settings.System.putInt(resolver,Settings.System.SCREEN_BRIGHTNESS,
                    prefs.getInt("brightness",128)) && success
                success=Settings.System.putInt(resolver,Settings.System.SCREEN_BRIGHTNESS_MODE,
                    prefs.getInt("brightness_mode",0)) && success
            }
            if(prefs.contains("timeout")) success=Settings.System.putInt(resolver,
                Settings.System.SCREEN_OFF_TIMEOUT,prefs.getInt("timeout",30_000)) && success
            if(success) prefs.edit().clear().apply()
            if(success) "Исходные экранные параметры восстановлены."
                else "Некоторые значения отклонены прошивкой; повторите восстановление."
        }.getOrElse { "Восстановление отклонено: ${it.javaClass.simpleName}" }
    }
}
