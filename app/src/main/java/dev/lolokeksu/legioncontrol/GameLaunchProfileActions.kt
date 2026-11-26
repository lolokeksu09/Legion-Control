package dev.lolokeksu.legioncontrol

import android.content.Context

/** The game card and Toolbox share the confirmation and result handling. */
internal object GameLaunchProfileActions {
    fun confirmApply(context: Context, pkg: String?, withGame: Boolean, report: (String) -> Unit) {
        if (pkg == null || !GameLaunchProfilePolicy.validPackage(pkg)) {
            report("Сначала выберите игру.")
            return
        }
        val profile = GameLaunchProfiles.profile(context, pkg)
        if (profile == null || !profile.actionable) {
            report("Сначала сохраните профиль запуска.")
            return
        }
        val pending = GameLaunchProfiles.pendingPackage(context)
        if (pending != null) {
            report("Сначала восстановите предыдущие настройки: $pending")
            return
        }
        val access = CapabilityAccessBackend.profileAccess(context, profile)
        if (access != AccessState.READY) {
            report("Необходимые доступы: " + access.label)
            return
        }
        val description = "Приложение: $pkg\n" +
            "Громкость: " + (profile.mediaPercent?.let { "$it%" } ?: "не менять") + "\n" +
            "Яркость: " + (profile.brightnessPercent?.let { "$it%" } ?: "не менять") + "\n" +
            "DND: " + (profile.focusEnabled?.let { if (it) "включить" else "выключить" } ?: "не менять") +
            "\n\nНастройки общесистемные. Legion Control сохранит исходные значения для возврата. " +
            "Если вы поменяете параметр после применения профиля, при возврате он сохранится."
        LegionDialogs.builder(context)
            .setTitle(if (withGame) "Применить профиль и открыть игру?" else "Применить профиль?")
            .setMessage(description)
            .setPositiveButton(if (withGame) "Применить и открыть" else "Применить") { _, _ ->
                val result = if (withGame) {
                    val intent = runCatching { ApplicationInventory.launchIntent(context, pkg) }.getOrNull()
                    if (intent == null) {
                        report("Android не разрешает запустить выбранную игру.")
                        return@setPositiveButton
                    }
                    GameLaunchProfiles.launchConfirmed(context, pkg, profile) { context.startActivity(intent) }
                } else {
                    GameLaunchProfiles.applyConfirmed(context, pkg, profile)
                }
                report(result.message)
            }
            .setNegativeButton("Отмена", null).show()
    }

    fun confirmRestore(context: Context, report: (String) -> Unit) {
        val pending = GameLaunchProfiles.pendingConfirmation(context)
        if (pending == null) {
            report("Нет ожидающих возврата настроек.")
            return
        }
        LegionDialogs.builder(context).setTitle("Восстановить системные настройки?")
            .setMessage("Запись возврата: ${pending.packageName}\n" +
                "Legion Control сверит исходные, запрошенные и текущие значения. " +
                "Параметры, изменённые после применения, сохранятся. " +
                "При отказе Android можно повторить возврат.")
            .setPositiveButton("Восстановить") { _, _ ->
                report(GameLaunchProfiles.restoreConfirmed(context, pending).message)
            }
            .setNegativeButton("Отмена", null).show()
    }
}
