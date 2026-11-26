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
        return buildString {
            append("Звук мультимедиа: ").append(music).append('\n')
            append("«Не беспокоить»: ").append(if(policy) "доступ разрешён" else "требуется доступ").append('\n')
            append("Изменение системных параметров: ").append(if(write) "разрешено" else "нужен WRITE_SETTINGS").append('\n')
            append("Уведомления: ").append(if(notify) "разрешены" else "отключены/не разрешены")
        }
    }

    fun setMediaVolume(context: Context, percent: Int): String {
        require(percent in 0..100)
        return runCatching {
            val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            require(max > 0)
            val requested=(max*percent/100f).roundToInt()
            val actual=VerifiedSettingWrite.apply(requested,
                { audio.getStreamVolume(AudioManager.STREAM_MUSIC) },
                { audio.setStreamVolume(AudioManager.STREAM_MUSIC,it,0); true })
            "Громкость мультимедиа: $actual/$max (подтверждено Android)."
        }.getOrElse { "Не удалось изменить громкость: ${it.javaClass.simpleName}" }
    }

    fun setDoNotDisturb(context: Context, enabled: Boolean): String {
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return "Сначала разрешите доступ к режиму «Не беспокоить» в системных настройках."
        return runCatching {
            nm.setInterruptionFilter(if(enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL)
            if (Build.VERSION.SDK_INT >= 35) {
                if (enabled) "Запрошено включение правила Legion Control; итоговый DND зависит от правил Android."
                else "Запрошено выключение правила Legion Control; другие правила Android могут сохранить DND."
            } else "Запрошено: «Не беспокоить» "+if(enabled) "включить" else "выключить"
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
                    Settings.System.SCREEN_OFF_TIMEOUT)).commit().also { check(it) }
            }
            writeVerified(context, Settings.System.SCREEN_OFF_TIMEOUT,seconds*1000)
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
                    Settings.System.getInt(resolver,Settings.System.SCREEN_BRIGHTNESS_MODE)).commit().also { check(it) }
            }
            writeVerified(context,Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            writeVerified(context,Settings.System.SCREEN_BRIGHTNESS,
                (percent*255/100f).roundToInt().coerceIn(1,255))
            "Яркость изменена на $percent%. Автояркость отключена; восстановить исходную можно кнопкой."
        }.getOrElse { "Яркость не подтверждена: ${it.javaClass.simpleName}. При частичном изменении используйте ручное восстановление." }
    }

    private fun writeVerified(context: Context, key: String, value: Int): Int =
        VerifiedSettingWrite.apply(value,
            { Settings.System.getInt(context.contentResolver,key) },
            { Settings.System.putInt(context.contentResolver,key,it) })

    fun restoreScreenSettings(context: Context): String {
        if(!Settings.System.canWrite(context)) return "Нужно разрешение WRITE_SETTINGS для восстановления."
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        if(!prefs.contains("brightness") && !prefs.contains("timeout")) return "Сохранённых параметров нет."
        return runCatching {
            var failures=0
            if(prefs.contains("brightness")) {
                val restored=runCatching {
                    check(prefs.contains("brightness_mode"))
                    writeVerified(context,Settings.System.SCREEN_BRIGHTNESS,prefs.getInt("brightness",0))
                    writeVerified(context,Settings.System.SCREEN_BRIGHTNESS_MODE,prefs.getInt("brightness_mode",0))
                    check(prefs.edit().remove("brightness").remove("brightness_mode").commit())
                }.isSuccess
                if(!restored) failures++
            }
            // An independent timeout restore must run even if brightness failed.
            if(prefs.contains("timeout")) {
                val restored=runCatching {
                    writeVerified(context,Settings.System.SCREEN_OFF_TIMEOUT,prefs.getInt("timeout",0))
                    check(prefs.edit().remove("timeout").commit())
                }.isSuccess
                if(!restored) failures++
            }
            if(failures==0) "Исходные экранные параметры восстановлены и подтверждены Android."
                else "Не все параметры подтверждены; повторите восстановление."
        }.getOrElse { "Восстановление отклонено: ${it.javaClass.simpleName}" }
    }
}
