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

/** Read-only public API probes, evaluated on demand. No persistent cache or background polling. */
internal object CapabilityAccessBackend {
    private fun probe(read: () -> Boolean?): Boolean? = runCatching { read() }.getOrNull()

    private fun permission(context: Context, name: String): Boolean? =
        probe { context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED }

    fun usageState(context: Context): AccessState {
        val usage = probe {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return@probe null
            when (ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_DEFAULT, AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
                else -> null
            }
        }
        return CapabilityAccessPolicy.evaluate(true, usage)
    }

    fun overlayState(context: Context): AccessState =
        CapabilityAccessPolicy.evaluate(true, probe { Settings.canDrawOverlays(context) })

    fun notificationState(context: Context): AccessState {
        val notification = runCatching { context.getSystemService(NotificationManager::class.java) }.getOrNull()
        return CapabilityAccessPolicy.evaluate(true,
            permission(context, Manifest.permission.POST_NOTIFICATIONS),
            probe { notification?.areNotificationsEnabled() })
    }

    /** Absent channel is unknown, not disabled: the service can create it at start. */
    fun notificationChannelState(context: Context, channelId: String): AccessState {
        val app = notificationState(context)
        if (app != AccessState.READY) return app
        val enabled = probe {
            context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(channelId)?.let { it.importance > NotificationManager.IMPORTANCE_NONE }
        }
        return CapabilityAccessPolicy.evaluate(true, true, enabled)
    }

    fun playbackAudioState(context: Context): AccessState =
        CapabilityAccessPolicy.evaluate(true, permission(context, Manifest.permission.RECORD_AUDIO))

    fun dndState(context: Context): AccessState =
        CapabilityAccessPolicy.evaluate(true, probe {
            context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted
        })

    fun writeSettingsState(context: Context): AccessState =
        CapabilityAccessPolicy.evaluate(true, probe { Settings.System.canWrite(context) })

    fun mediaVolumeState(context: Context): AccessState =
        CapabilityAccessPolicy.evaluate(probe {
            context.getSystemService(AudioManager::class.java)?.let {
                !it.isVolumeFixed && it.getStreamMaxVolume(AudioManager.STREAM_MUSIC) > 0
            }
        }, true)

    fun read(context: Context): CapabilityAccessSnapshot = CapabilityAccessSnapshot(mapOf(
        CapabilityAccess.NOTIFICATIONS to notificationState(context),
        CapabilityAccess.OVERLAY to overlayState(context),
        CapabilityAccess.USAGE to usageState(context),
        CapabilityAccess.DND to dndState(context),
        CapabilityAccess.WRITE_SETTINGS to writeSettingsState(context),
        CapabilityAccess.MEDIA_VOLUME to mediaVolumeState(context),
        CapabilityAccess.PLAYBACK_AUDIO to playbackAudioState(context),
        CapabilityAccess.GAME_FPS to AccessState.UNSUPPORTED,
        CapabilityAccess.OEM_BOOST to AccessState.UNSUPPORTED
    ))

    /** Query only relevant access, avoiding unrelated probes in the automation watchdog. */
    fun automationAccess(context: Context, channelId: String): AccessState =
        CapabilityAccessPolicy.combine(listOf(usageState(context), overlayState(context),
            notificationChannelState(context, channelId)))

    fun profileAccess(context: Context, profile: GameLaunchProfile): AccessState =
        CapabilityAccessPolicy.combine(buildList {
            if (profile.mediaPercent != null) add(mediaVolumeState(context))
            if (profile.brightnessPercent != null) add(writeSettingsState(context))
            if (profile.focusEnabled != null) add(dndState(context))
        })

    fun notificationChannelIntent(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

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
            CapabilityAccess.PLAYBACK_AUDIO -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            else -> null
        }
    }
}
