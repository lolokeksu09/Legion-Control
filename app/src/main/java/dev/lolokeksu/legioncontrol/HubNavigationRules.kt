package dev.lolokeksu.legioncontrol

/** Pure navigation/favorites policy; used by Android views and JVM regression tests. */
enum class HubTab(val key: String, val title: String, val shortTitle: String) {
    HOME("home", "Главная", "Главная"),
    GAMES("games", "Игры", "Игры"),
    TOOLS("tools", "Инструменты", "Инстр."),
    HISTORY("history", "История", "История"),
    SETTINGS("settings", "Настройки", "Настройки");

    companion object {
        fun parse(key: String?): HubTab = entries.firstOrNull { it.key == key } ?: HOME
    }
}

data class HubFavoriteFields(
    val alias: String,
    val category: String,
    val note: String,
    val favorite: Boolean
)

object HubFavoriteRules {
    /** Never change an existing user's alias, category or note while favoriting. */
    fun toggle(current: HubFavoriteFields?, visibleLabel: String): HubFavoriteFields =
        if (current == null) HubFavoriteFields(visibleLabel.take(100), "Игра", "", true)
        else current.copy(favorite = !current.favorite)
}

object HubSearch {
    fun normalized(query: String?): String = query.orEmpty().trim().lowercase()
    fun matches(label: String, packageName: String, query: String?): Boolean {
        val normalized = normalized(query)
        return normalized.isEmpty() || label.lowercase().contains(normalized) ||
            packageName.lowercase().contains(normalized)
    }
}
