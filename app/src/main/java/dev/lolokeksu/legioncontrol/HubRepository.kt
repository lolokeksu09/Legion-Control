package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Read-only screen state; everything is derived from the already installed backend. */
data class HubApp(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
    val favorite: Boolean,
    val visible: Boolean,
    val tracked: Boolean
)

data class HubSnapshot(
    val apps: List<HubApp>,
    val trackedCount: Int,
    val favoriteCount: Int,
    val profileCount: Int,
    val clipPreviewCount: Int,
    val recentSessions: List<GameSessionHeader>,
    val batteryPercent: Int?,
    val refreshHz: Float?,
    val warnings: List<String>
) {
    companion object {
        val EMPTY = HubSnapshot(emptyList(), 0, 0, 0, 0, emptyList(), null, null, emptyList())
    }
}

/**
 * MVVM data layer. Reuses SQLite v12 repositories and Android launcher visibility:
 * no copy database, fake game statistics, global package enumeration or new permissions.
 * Invoked ONLY from HubViewModel's single background worker.
 */
class HubRepository(context: Context) {
    private val app = context.applicationContext

    fun read(): HubSnapshot {
        val warnings = ArrayList<String>()
        val tracked = runCatching {
            ApplicationCenterRepository(app).use { it.list() }
        }.getOrElse {
            warnings += "Библиотека приложений недоступна: " + it.javaClass.simpleName
            emptyList()
        }
        val favoriteCount = tracked.count { it.favorite }
        val trackedByPackage = tracked.associateBy { it.packageName }
        val available = ApplicationInventory.launchable(app)
        val pm = app.packageManager
        val apps = available.map { item ->
            val favorite = trackedByPackage[item.packageName]
            val isGame = runCatching {
                pm.getApplicationInfo(
                    item.packageName, PackageManager.ApplicationInfoFlags.of(0L)
                ).category == ApplicationInfo.CATEGORY_GAME
            }.getOrDefault(false)
            HubApp(item.packageName, favorite?.alias?.takeIf { it.isNotBlank() } ?: item.label,
                isGame, favorite?.favorite == true, true, favorite != null)
        }.toMutableList()

        // Favorite can be missing from public launcher query without being uninstalled.
        val visible = available.mapTo(HashSet()) { it.packageName }
        tracked.filter { it.favorite && it.packageName !in visible }.forEach {
            apps += HubApp(it.packageName,
                it.alias.ifBlank { it.packageName }, false, true, false, true)
        }
        apps.sortWith(compareByDescending<HubApp> { it.favorite }
            .thenByDescending { it.isGame }.thenBy { it.label.lowercase() })

        val profileCount = runCatching {
            SmartProfilesRepository(app).use { it.list().size }
        }.getOrElse {
            warnings += "Профили недоступны: " + it.javaClass.simpleName
            0
        }
        val sessions = runCatching {
            GameSessionRepository(app).use { it.recent(8) }
        }.getOrElse {
            warnings += "История игровых сессий недоступна: " + it.javaClass.simpleName
            emptyList()
        }
        val clips = runCatching {
            CaptureClipLibrary.listOwn(app).size
        }.getOrElse {
            warnings += "Библиотека клипов недоступна: " + it.javaClass.simpleName
            0
        }
        val telemetry = runCatching { GameSessionTelemetry.snapshot(app) }.getOrNull()
        return HubSnapshot(
            apps = apps,
            trackedCount = tracked.size,
            favoriteCount = favoriteCount,
            profileCount = profileCount,
            clipPreviewCount = clips,
            recentSessions = sessions,
            batteryPercent = telemetry?.batteryPercent,
            refreshHz = telemetry?.refreshHz,
            warnings = warnings
        )
    }

    /** Toggle in EXISTING SQLite row without touching its alias, category, note or audit events. */
    fun toggleFavorite(pkg: String, visibleLabel: String): Boolean {
        require(ApplicationInventory.isPackageName(pkg))
        ApplicationCenterRepository(app).use { repository ->
            val current = repository.get(pkg)
            val fields = HubFavoriteRules.toggle(
                current?.let {
                    HubFavoriteFields(it.alias, it.category, it.note, it.favorite)
                }, visibleLabel
            )
            repository.save(pkg, fields.alias, fields.category, fields.note, fields.favorite)
            return fields.favorite
        }
    }

    /** Existing Toolbox reads exactly these keys; only a user click may change them. */
    fun chooseToolboxGame(pkg: String, label: String): Boolean {
        if (!ApplicationInventory.isPackageName(pkg)) return false
        if (app.packageManager.getLaunchIntentForPackage(pkg) == null) return false
        app.getSharedPreferences(ToolboxPreferences.NAME, Context.MODE_PRIVATE).edit()
            .putString(ToolboxPreferences.GAME, pkg)
            .putString(ToolboxPreferences.LABEL, label.take(100))
            .apply()
        return true
    }
}
