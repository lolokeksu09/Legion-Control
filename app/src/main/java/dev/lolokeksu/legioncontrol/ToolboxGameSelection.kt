package dev.lolokeksu.legioncontrol

import android.content.Context

/** One persistence path for Game Hub and Toolbox's own picker. */
internal object ToolboxGameSelection {
    fun select(context: Context, pkg: String, label: String): Boolean =
        ProfileMaintenanceGate.tryRun({ false }) {
            if (LegionDataBackup.hasPendingRestore(context) ||
                !ApplicationInventory.isPackageName(pkg) || pkg == context.packageName ||
                context.packageManager.getLaunchIntentForPackage(pkg) == null)
                return@tryRun false
            context.getSharedPreferences(ToolboxPreferences.NAME, Context.MODE_PRIVATE).edit()
                .putString(ToolboxPreferences.GAME, pkg)
                .putString(ToolboxPreferences.LABEL, label.take(100))
                .commit()
        }
}
