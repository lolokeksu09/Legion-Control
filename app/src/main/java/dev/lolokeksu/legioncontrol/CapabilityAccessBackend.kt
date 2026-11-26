package dev.lolokeksu.legioncontrol

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Process
import android.provider.Settings

internal enum class CapabilityAccess(val label: String) {
    NOTIFICATIONS("Уведомления"),
    OVERLAY("Поверх окон"),
    USAGE("Статистика использования"),
    DND("Доступ к управлению DND"),
    WRITE_SETTINGS("Изменение системных настроек"),
    MEDIA_VOLUME("Управление громкостью"),
    GAME_FPS("Измерение FPS чужой игры"),
    OEM_BOOST("Управление Touch Boost / Bypass / CPU-GPU")
}

internal data class CapabilityAccessSnapshot(private val states: Map<CapabilityAccess, AccessState>) {
    operator fun get(capability: CapabilityAccess): AccessState = states[capability] ?: AccessState.UNKNOWN
    fun report(): String = CapabilityAccess.entries.joinToString("\n") { "${it.label}: ${get(it).label}" }
}

/** Read-only public API probes, evaluated on demand. No persistent cache or background polling. */
internal object CapabilityAccessBackend {
    private fun probe(read: () -> Boolean?): Boolean? = runCatching { read() }.getOrNull()

    fun read(context: Context): CapabilityAccessSnapshot {
        val notification = runCatching { context.getSystemService(NotificationManager::class.java) }.getOrNull()
        val audio = runCatching { context.getSystemService(AudioManager::class.java) }.getOrNull()
        val notificationGrant = probe {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        val usage = probe {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return@probe null
            when (ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_DEFAULT, AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
                else -> null
            }
        }
        return CapabilityAccessSnapshot(mapOf(
            CapabilityAccess.NOTIFICATIONS to CapabilityAccessPolicy.evaluate(true, notificationGrant,
                probe { notification?.areNotificationsEnabled() }),
            CapabilityAccess.OVERLAY to CapabilityAccessPolicy.evaluate(true, probe { Settings.canDrawOverlays(context) }),
            CapabilityAccess.USAGE to CapabilityAccessPolicy.evaluate(true, usage),
            CapabilityAccess.DND to CapabilityAccessPolicy.evaluate(true, probe { notification?.isNotificationPolicyAccessGranted }),
            CapabilityAccess.WRITE_SETTINGS to CapabilityAccessPolicy.evaluate(true, probe { Settings.System.canWrite(context) }),
            CapabilityAccess.MEDIA_VOLUME to CapabilityAccessPolicy.evaluate(probe {
                audio?.let { !it.isVolumeFixed && it.getStreamMaxVolume(AudioManager.STREAM_MUSIC) > 0 }
            }, true),
            CapabilityAccess.GAME_FPS to AccessState.UNSUPPORTED,
            CapabilityAccess.OEM_BOOST to AccessState.UNSUPPORTED
        ))
    }

    /** Settings only open on explicit user action; no access is granted by this app. */
    fun settingsIntent(context: Context, capability: CapabilityAccess): Intent? {
        val packageUri = Uri.parse("package:" + context.packageName)
        return when (capability) {
            CapabilityAccess.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            CapabilityAccess.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            CapabilityAccess.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            CapabilityAccess.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            CapabilityAccess.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri)
            else -> null
        }
    }
}
