package dev.lolokeksu.legioncontrol

import android.app.NotificationManager
import android.os.Build
import android.service.notification.Condition
import android.content.Context
import android.media.AudioManager
import android.provider.Settings

data class GameLaunchResult(val success: Boolean, val message: String)

/**
 * Explicit per-package launch actions, including separately approved one-shot automation.
 * No privileged OEM APIs. All writes are global
 * Android settings, so an undo ticket is committed BEFORE the first write.
 */
object GameLaunchProfiles {
    private const val NAME = "legion-launch-profiles-v1"
    private const val ACTIVE = "pending_global_undo"
    private const val OWNER = "pending_auto_owner"
    private const val PREFIX = "profile_"
    private val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun profile(context: Context, pkg: String): GameLaunchProfile? {
        if (!GameLaunchProfilePolicy.validPackage(pkg)) return null
        return GameLaunchProfile.decode(prefs(context).getString(PREFIX + pkg, null))
    }

    fun save(context: Context, pkg: String, profile: GameLaunchProfile): Boolean {
        require(GameLaunchProfilePolicy.validPackage(pkg))
        return ProfileMaintenanceGate.tryRun({false}) {
            if(LegionDataBackup.hasPendingRestore(context)) return@tryRun false
            synchronized(lock) {
                val edit = prefs(context).edit()
                if (profile.actionable) edit.putString(PREFIX + pkg, profile.encode())
                else edit.remove(PREFIX + pkg)
                edit.commit()
            }
        }
    }

    fun pendingPackage(context: Context): String? {
        val raw = prefs(context).getString(ACTIVE, null) ?: return null
        return GameLaunchUndo.decode(raw)?.packageName ?: "неизвестен (запись повреждена)"
    }

    /**
     * Preflight ALL required access and snapshots before modifying ANY setting.
     * A pending ticket prevents one game from overwriting another game's restore data.
     */
    fun apply(context: Context, pkg: String): GameLaunchResult = applyInternal(context,pkg,null,null)

    internal fun applyAutomatic(context:Context,pkg:String,owner:String,approved:GameLaunchProfile):GameLaunchResult =
        applyInternal(context,pkg,owner,approved)

    internal fun ownsAutomatic(context:Context,owner:String):Boolean = synchronized(lock) {
        prefs(context).contains(ACTIVE) && prefs(context).getString(OWNER,null)==owner
    }

    internal fun restoreAutomatic(context:Context,owner:String):GameLaunchResult =
        ProfileMaintenanceGate.tryRun({maintenanceBusy()}) {
            synchronized(lock) {
                if(!ownsAutomatic(context,owner))
                    GameLaunchResult(true,"Нет записи возврата этой авто-сессии.")
                else restoreLocked(context)
            }
        }

    private fun applyInternal(context:Context,pkg:String,owner:String?,approved:GameLaunchProfile?):GameLaunchResult =
        ProfileMaintenanceGate.tryRun({maintenanceBusy()}) {
        if(LegionDataBackup.hasPendingRestore(context)) return@tryRun GameLaunchResult(false,
            "Сначала завершите прерванное восстановление ZIP в Capture Studio.")
        synchronized(lock) {
        if (!GameLaunchProfilePolicy.validPackage(pkg)) {
            return@synchronized GameLaunchResult(false, "Некорректный пакет приложения.")
        }
        val p = profile(context, pkg)
            ?: return@synchronized GameLaunchResult(false, "Сначала сохраните профиль этой игры.")
        if (owner!=null && (approved==null || p!=approved)) {
            return@synchronized GameLaunchResult(false,"Профиль изменился после подтверждения. Запустите автоматизацию заново.")
        }
        if (!p.actionable) {
            return@synchronized GameLaunchResult(false, "Профиль не содержит действий.")
        }
        if (prefs(context).contains(ACTIVE)) {
            return@synchronized GameLaunchResult(false,
                "Сначала верните предыдущие глобальные настройки кнопкой «Восстановить».")
        }
        val snapshot = runCatching {
            val audio = if (p.mediaPercent != null)
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager else null
            val nm = if (p.focusEnabled != null)
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager else null
            if (p.brightnessPercent != null && !Settings.System.canWrite(context)) {
                error("Нет разрешения «Изменение системных настроек»")
            }
            if (nm != null && !nm.isNotificationPolicyAccessGranted) {
                error("Нет доступа к режиму «Не беспокоить»")
            }
            val media = if (audio != null) {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                require(max > 0) { "Громкость мультимедиа недоступна" }
                val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val after = GameLaunchProfilePolicy.mediaIndex(p.mediaPercent!!, max)
                if (before == after) null else LaunchSettingChange(before, after)
            } else null

            val resolver = context.contentResolver
            val screen = if (p.brightnessPercent != null) {
                val before = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
                val after = (p.brightnessPercent * 255 + 50) / 100
                if (before == after) null else LaunchSettingChange(before, after)
            } else null
            val mode = if (p.brightnessPercent != null) {
                val before = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
                val after = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                if (before == after) null else LaunchSettingChange(before, after)
            } else null
            val focusScope = if (Build.VERSION.SDK_INT >= 35)
                FocusUndoScope.APP_RULE else FocusUndoScope.GLOBAL_FILTER
            val appDnd = if (nm != null && Build.VERSION.SDK_INT >= 35)
                readAppDndRuleSnapshot(nm) else null
            val focus = if (nm != null) {
                val before: Int
                val after: Int
                if (Build.VERSION.SDK_INT >= 35) {
                    before = requireNotNull(appDnd).state
                    after = if (p.focusEnabled == true) APP_DND_ACTIVE else APP_DND_INACTIVE
                } else {
                    before = nm.currentInterruptionFilter
                    require(before in setOf(
                        NotificationManager.INTERRUPTION_FILTER_ALL,
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                        NotificationManager.INTERRUPTION_FILTER_NONE,
                        NotificationManager.INTERRUPTION_FILTER_ALARMS
                    )) { "Android не предоставил достоверный текущий режим DND" }
                    after = if (p.focusEnabled == true)
                        NotificationManager.INTERRUPTION_FILTER_NONE
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                }
                if (before == after) null else LaunchSettingChange(before, after)
            } else null
            GameLaunchUndo(pkg, media, screen, mode, focus, focusScope, appDnd?.exists ?: true)
        }.getOrElse {
            return@synchronized GameLaunchResult(false,
                "Настройки не изменены: " + (it.message ?: it.javaClass.simpleName))
        }
        if (!snapshot.pending) {
            return@synchronized GameLaunchResult(true, "Настройки уже соответствуют профилю.")
        }
        val journal=prefs(context).edit().putString(ACTIVE, snapshot.encode())
        if(owner!=null) journal.putString(OWNER,owner) else journal.remove(OWNER)
        if (!journal.commit()) {
            return@synchronized GameLaunchResult(false,
                "Не удалось сохранить запись для возврата. Ничего не изменено.")
        }
        try {
            snapshot.media?.let {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.requested, 0)
            }
            snapshot.brightnessMode?.let {
                check(Settings.System.putInt(context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE, it.requested))
            }
            snapshot.brightness?.let {
                check(Settings.System.putInt(context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS, it.requested))
            }
            snapshot.focus?.let {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.setInterruptionFilter(it.requested)
            }
            GameLaunchResult(true,
                if(owner==null) "Изменения запрошены у Android. Исходные значения сохранены; возврат вручную."
                else "Изменения запрошены у Android. Исходные значения сохранены для авто-возврата.")
        } catch (e: Exception) {
            val undo = restore(context)
            GameLaunchResult(false, "Применение прервано: ${e.javaClass.simpleName}. " +
                "Попытка возврата: ${undo.message}")
        }
    }

        }

    private fun maintenanceBusy()=GameLaunchResult(false,"Выполняется восстановление ZIP. Повторите действие после завершения.")

    /**
     * Per-field compare-before-restore. If the user changed a value afterwards,
     * that field is NOT overwritten. Failed writes remain queued for manual retry.
     */
    fun restore(context: Context): GameLaunchResult = ProfileMaintenanceGate.tryRun({maintenanceBusy()}) {
        synchronized(lock) { restoreLocked(context) }
    }

    private fun restoreLocked(context: Context): GameLaunchResult {
        val raw = prefs(context).getString(ACTIVE, null)
            ?: return GameLaunchResult(true, "Нет применённого профиля для возврата.")
        val ticket = GameLaunchUndo.decode(raw)
            ?: return GameLaunchResult(false,
                "Запись возврата повреждена. Автоматические системные изменения запрещены.")
        var restored = 0
        var conflicts = 0
        var failures = 0

        fun restoreField(
            change: LaunchSettingChange?,
            read: () -> Int,
            write: (Int) -> Unit
        ): LaunchSettingChange? {
            if (change == null) return null
            return try {
                when (GameLaunchProfilePolicy.restoreChoice(read(), change)) {
                    RestoreChoice.ALREADY_ORIGINAL -> null
                    RestoreChoice.CHANGED_BY_USER -> { conflicts++; null }
                    RestoreChoice.RESTORE -> {
                        GameLaunchProfilePolicy.restoreAndVerify(read,write,change)
                        restored++
                        null
                    }
                }
            } catch (_: Exception) {
                failures++
                change
            }
        }

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val resolver = context.contentResolver
        val media = restoreField(ticket.media,
            { requireNotNull(audio).getStreamVolume(AudioManager.STREAM_MUSIC) },
            { requireNotNull(audio).setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) })
        val brightness = restoreField(ticket.brightness,
            { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) },
            {
                check(Settings.System.canWrite(context))
                check(Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, it))
            })
        // Do not reactivate auto-brightness when someone changed brightness manually.
        val manualBrightness = ticket.brightness?.let { change ->
            runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }
                .getOrNull()?.let { it != change.original && it != change.requested } == true
        } == true
        val mode = if (brightness != null) {
            // Do not reactivate adaptive brightness while its value still needs retry.
            ticket.brightnessMode
        } else if (manualBrightness) {
            if (ticket.brightnessMode != null) conflicts++
            null
        } else restoreField(ticket.brightnessMode,
            { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE) },
            {
                check(Settings.System.canWrite(context))
                check(Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, it))
            })
        var legacyFocusSkipped = false
        val focus = when {
            ticket.focus == null -> null
            Build.VERSION.SDK_INT >= 35 && ticket.focusScope == FocusUndoScope.GLOBAL_FILTER -> {
                // A v1 ticket captured the aggregate filter, not Legion's implicit rule state.
                // Restoring it could override another app's or the user's active Zen rule.
                legacyFocusSkipped = true
                conflicts++
                null
            }
            Build.VERSION.SDK_INT >= 35 -> restoreAppDndRule(ticket.focus, ticket.focusRuleExisted, nm,
                onRestored = { restored++ }, onConflict = { conflicts++ }, onFailure = { failures++ })
            ticket.focusScope == FocusUndoScope.APP_RULE -> {
                legacyFocusSkipped = true
                conflicts++
                null
            }
            else -> restoreField(ticket.focus,
                { requireNotNull(nm).currentInterruptionFilter },
                {
                    check(requireNotNull(nm).isNotificationPolicyAccessGranted)
                    requireNotNull(nm).setInterruptionFilter(it)
                })
        }
        val remaining = ticket.copy(media = media, brightness = brightness,
            brightnessMode = mode, focus = focus)
        val saved = if (remaining.pending) {
            prefs(context).edit().putString(ACTIVE, remaining.encode()).commit()
        } else prefs(context).edit().remove(ACTIVE).remove(OWNER).commit()
        val summary = "Восстановлено: $restored; изменения пользователя не тронуты: $conflicts; " +
            "ошибок Android: $failures." +
            if (legacyFocusSkipped) " Старую запись DND нельзя безопасно сопоставить с правилом Android 15+; режим оставлен без изменений." else ""
        return if (!saved) GameLaunchResult(false, "$summary Не удалось сохранить результат возврата.")
        else GameLaunchResult(!remaining.pending,
            "$summary " + if (remaining.pending) "Есть незавершённые действия, повторите возврат."
            else "Возврат завершён.")
    }
    private data class AppDndRuleSnapshot(val exists: Boolean, val state: Int)

    private fun readAppDndRuleSnapshot(nm: NotificationManager): AppDndRuleSnapshot {
        val implicitRules = nm.automaticZenRules.filterValues { rule ->
            rule.owner == null && rule.configurationActivity == null
        }
        require(implicitRules.size <= 1) {
            "Невозможно однозначно определить неявное правило DND Legion Control"
        }
        val id = implicitRules.keys.singleOrNull()
            ?: return AppDndRuleSnapshot(false, APP_DND_INACTIVE)
        val state = when (nm.getAutomaticZenRuleState(id)) {
            Condition.STATE_TRUE -> APP_DND_ACTIVE
            Condition.STATE_FALSE -> APP_DND_INACTIVE
            else -> error("Android не предоставил достоверное состояние правила DND")
        }
        return AppDndRuleSnapshot(true, state)
    }

    private fun restoreAppDndRule(
        change: LaunchSettingChange,
        originalRuleExisted: Boolean,
        nm: NotificationManager?,
        onRestored: () -> Unit,
        onConflict: () -> Unit,
        onFailure: () -> Unit
    ): LaunchSettingChange? {
        return try {
            val manager = requireNotNull(nm)
            check(manager.isNotificationPolicyAccessGranted)
            val current = readAppDndRuleSnapshot(manager)
            if (!current.exists) {
                if (originalRuleExisted && change.original == APP_DND_ACTIVE) onConflict()
                return null
            }
            when (GameLaunchProfilePolicy.restoreChoice(current.state, change)) {
                RestoreChoice.ALREADY_ORIGINAL -> null
                RestoreChoice.CHANGED_BY_USER -> {
                    onConflict()
                    null
                }
                RestoreChoice.RESTORE -> {
                    manager.setInterruptionFilter(
                        if (change.original == APP_DND_ACTIVE)
                            NotificationManager.INTERRUPTION_FILTER_NONE
                        else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                    val restored = readAppDndRuleSnapshot(manager)
                    check(restored.exists && restored.state == change.original) {
                        "Android не подтвердил восстановление правила DND Legion Control"
                    }
                    onRestored()
                    null
                }
            }
        } catch (_: Exception) {
            onFailure()
            change
        }
    }

    private const val APP_DND_INACTIVE = 0
    private const val APP_DND_ACTIVE = 1

}
