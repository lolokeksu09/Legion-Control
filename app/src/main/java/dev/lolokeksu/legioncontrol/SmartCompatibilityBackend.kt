package dev.lolokeksu.legioncontrol

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Capability report never confuses API existence with actual OEM functionality. */
object SmartCompatibilityBackend {
    fun inspect(context: Context): String {
        val notify=context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val dnd=runCatching { nm?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)
        val write=runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
        return buildString {
            append("SMART PROFILES · ВОЗМОЖНОСТИ\n")
            append("Android API: ").append(Build.VERSION.SDK_INT).append('\n')
            append("Профили, SQLite, аналитика, архивы: поддерживаются Android API\n")
            append("Громкость мультимедиа: публичный AudioManager (зависит от политики устройства)\n")
            append("Уведомления: ").append(if(notify)"разрешены" else "нужно разрешение").append('\n')
            append("Не беспокоить: ").append(if(dnd)"спецдоступ разрешён" else "спецдоступ не предоставлен").append('\n')
            append("Яркость/тайм-аут: ").append(if(write)"спецдоступ разрешён" else "спецдоступ не предоставлен").append('\n')
            append("Quick Settings: плитку добавляет пользователь, запуск записи из шторки не скрытый\n")
            append("Запись в другой игре: доступна только после явного запуска FGS; ")
            append("работа на конкретной прошивке НЕ ПРОВЕРЕНА\n")
            append("Touch Boost / Bypass / CPU-GPU частота / FPS игры: недоступны через публичные API\n")
            append("Root, Shizuku, геолокация: не требуются")
        }
    }
}
