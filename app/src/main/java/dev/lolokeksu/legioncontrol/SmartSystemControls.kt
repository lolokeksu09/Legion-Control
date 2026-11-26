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
    private const val PREFS = "legion-smart-system-undo"

    fun status(context: Context): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val notification = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val music = runCatching {
            if (audio == null) "н/д" else {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                "$current/$max"
            }
        }.getOrDefault("н/д")
        val policy = runCatching { notification?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)
        val write = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
        val notify = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return buildString {
            append("Звук мультимедиа: ").append(music).append('\n')
            append("«Не беспокоить»: ").append(if (policy) "доступ разрешён" else "требуется доступ").append('\n')
            append("Изменение системных параметров: ").append(if (write) "разрешено" else "нужен WRITE_SETTINGS").append('\n')
            append("Уведомления: ").append(if (notify) "разрешены" else "отключены/не разрешены")
        }
    }

    fun setMediaVolume(context: Context, percent: Int): String {
        require(percent in 0..100)
        return runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            require(max > 0)
            val requested = (max * percent / 100f).roundToInt()
            val actual = VerifiedSettingWrite.apply(requested,
                { audio.getStreamVolume(AudioManager.STREAM_MUSIC) },
                { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0); true })
            "Громкость мультимедиа: $actual/$max (подтверждено Android)."
        }.getOrElse { "Не удалось изменить громкость: ${it.javaClass.simpleName}" }
    }

    fun setDoNotDisturb(context: Context, enabled: Boolean): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return "Сначала разрешите доступ к режиму «Не беспокоить» в системных настройках."
        return runCatching {
            nm.setInterruptionFilter(if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL)
            if (Build.VERSION.SDK_INT >= 35) {
                if (enabled) "Запрошено включение правила Legion Control; итоговый DND зависит от правил Android."
                else "Запрошено выключение правила Legion Control; другие правила Android могут сохранить DND."
            } else "Запрошено: «Не беспокоить» " + if (enabled) "включить" else "выключить"
        }.getOrElse { "Прошивка отклонила изменение: ${it.javaClass.simpleName}" }
    }

    fun writeSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
        data = Uri.parse("package:" + context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun setScreenTimeout(context: Context, seconds: Int): String {
        require(seconds in listOf(30, 60, 120, 300, 600))
        if (!Settings.System.canWrite(context)) return "Нужно разрешить «Изменение системных настроек»."
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = Settings.System.SCREEN_OFF_TIMEOUT
            val current = Settings.System.getInt(context.contentResolver, key)
            val original = if (prefs.contains("timeout")) {
                check(prefs.contains("timeout_requested")) {
                    "Есть запись возврата старого формата. Сначала восстановите прежнее значение."
                }
                val saved = prefs.getInt("timeout", current)
                val previous = prefs.getInt("timeout_requested", saved)
                check(current == saved || current == previous) {
                    "Тайм-аут изменён вне Legion Control. Сначала выполните возврат."
                }
                saved
            } else current
            check(prefs.edit().putInt("timeout", original)
                .putInt("timeout_requested", seconds * 1000).commit())
            writeVerified(context, key, seconds * 1000)
            "Тайм-аут экрана изменён на $seconds с. Исходное значение сохранено для ручного восстановления."
        }.getOrElse { "Не удалось изменить тайм-аут: ${it.message ?: it.javaClass.simpleName}" }
    }

    fun setBrightness(context: Context, percent: Int): String {
        require(percent in 10..100)
        if (!Settings.System.canWrite(context)) return "Нужно разрешить «Изменение системных настроек»."
        return runCatching {
            val resolver = context.contentResolver
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val brightnessKey = Settings.System.SCREEN_BRIGHTNESS
            val modeKey = Settings.System.SCREEN_BRIGHTNESS_MODE
            val currentBrightness = Settings.System.getInt(resolver, brightnessKey)
            val currentMode = Settings.System.getInt(resolver, modeKey)
            val requestedBrightness = (percent * 255 / 100f).roundToInt().coerceIn(1, 255)
            val requestedMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            val hasTicket = prefs.contains("brightness") || prefs.contains("brightness_mode")
            val originalBrightness: Int
            val originalMode: Int
            if (hasTicket) {
                check(prefs.contains("brightness") && prefs.contains("brightness_mode") &&
                    prefs.contains("brightness_requested") && prefs.contains("brightness_mode_requested")) {
                    "Есть запись возврата старого формата. Сначала восстановите прежние параметры."
                }
                originalBrightness = prefs.getInt("brightness", currentBrightness)
                originalMode = prefs.getInt("brightness_mode", currentMode)
                check(currentBrightness == originalBrightness ||
                    currentBrightness == prefs.getInt("brightness_requested", originalBrightness)) {
                    "Яркость изменена вне Legion Control. Сначала выполните возврат."
                }
                check(currentMode == originalMode ||
                    currentMode == prefs.getInt("brightness_mode_requested", originalMode)) {
                    "Режим яркости изменён вне Legion Control. Сначала выполните возврат."
                }
            } else {
                originalBrightness = currentBrightness
                originalMode = currentMode
            }
            check(prefs.edit().putInt("brightness", originalBrightness)
                .putInt("brightness_mode", originalMode)
                .putInt("brightness_requested", requestedBrightness)
                .putInt("brightness_mode_requested", requestedMode).commit())
            writeVerified(context, modeKey, requestedMode)
            writeVerified(context, brightnessKey, requestedBrightness)
            "Яркость изменена на $percent%. Автояркость отключена; исходные значения сохранены для ручного восстановления."
        }.getOrElse { "Яркость не подтверждена: ${it.message ?: it.javaClass.simpleName}. При частичном изменении используйте ручное восстановление." }
    }

    private fun writeVerified(context: Context, key: String, value: Int): Int =
        VerifiedSettingWrite.apply(value,
            { Settings.System.getInt(context.contentResolver, key) },
            { Settings.System.putInt(context.contentResolver, key, it) })

    private fun restoreTrackedSetting(
        context: Context,
        prefs: android.content.SharedPreferences,
        originalKey: String,
        requestedKey: String,
        systemKey: String
    ): SmartSystemRestoreDecision {
        val original = prefs.getInt(originalKey, 0)
        val requested = if (prefs.contains(requestedKey)) prefs.getInt(requestedKey, original) else null
        val current = Settings.System.getInt(context.contentResolver, systemKey)
        val decision = SmartSystemRestorePolicy.decide(current, original, requested)
        when (decision) {
            SmartSystemRestoreDecision.RESTORE, SmartSystemRestoreDecision.LEGACY_RESTORE ->
                writeVerified(context, systemKey, original)
            SmartSystemRestoreDecision.ALREADY_ORIGINAL, SmartSystemRestoreDecision.USER_CHANGED -> Unit
        }
        check(prefs.edit().remove(originalKey).remove(requestedKey).commit())
        return decision
    }

    fun restoreScreenSettings(context: Context): String {
        if (!Settings.System.canWrite(context)) return "Нужно разрешение WRITE_SETTINGS для восстановления."
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("brightness") && !prefs.contains("brightness_mode") && !prefs.contains("timeout")) {
            return "Сохранённых параметров нет."
        }
        var restored = 0
        var unchanged = 0
        var conflicts = 0
        var failures = 0
        fun restore(originalKey: String, requestedKey: String, systemKey: String) {
            if (!prefs.contains(originalKey)) return
            val decision = runCatching {
                restoreTrackedSetting(context, prefs, originalKey, requestedKey, systemKey)
            }.onFailure { failures++ }.getOrNull() ?: return
            when (decision) {
                SmartSystemRestoreDecision.RESTORE, SmartSystemRestoreDecision.LEGACY_RESTORE -> restored++
                SmartSystemRestoreDecision.ALREADY_ORIGINAL -> unchanged++
                SmartSystemRestoreDecision.USER_CHANGED -> conflicts++
            }
        }
        restore("brightness", "brightness_requested", Settings.System.SCREEN_BRIGHTNESS)
        restore("brightness_mode", "brightness_mode_requested", Settings.System.SCREEN_BRIGHTNESS_MODE)
        restore("timeout", "timeout_requested", Settings.System.SCREEN_OFF_TIMEOUT)
        return when {
            failures > 0 -> "Восстановлено: $restored; изменено пользователем и не перезаписано: $conflicts; ошибок Android: $failures. Повторите возврат для незавершённых параметров."
            conflicts > 0 -> "Восстановлено: $restored; уже исходные: $unchanged; изменённые вне Legion Control параметры оставлены без изменений: $conflicts."
            else -> "Исходные экранные параметры подтверждены. Восстановлено: $restored; уже исходные: $unchanged."
        }
    }
}
