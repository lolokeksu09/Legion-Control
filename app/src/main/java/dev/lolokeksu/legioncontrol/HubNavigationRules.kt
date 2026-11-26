package dev.lolokeksu.legioncontrol

/** Pure navigation/favorites policy; used by Android views and JVM regression tests. */
enum class HubTab(val key: String, val title: String, val shortTitle: String) {
    HOME("home", "Главная", "Главная"),
    GAMES("games", "Игры", "Игры"),
    TOOLS("tools", "Система", "Система"),
    HISTORY("history", "Аналитика", "Аналитика"),
    SETTINGS("settings", "Настройки", "Настройки");

    companion object {
        fun parse(key: String?): HubTab = when (key) {
            "system" -> TOOLS
            "analytics" -> HISTORY
            else -> entries.firstOrNull { it.key == key } ?: HOME
        }
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

/** The approved home layout is ordered by user workflow, not legacy Activity. */
internal enum class HubCenter(val title: String, val description: String) {
    GAMES("Игровой центр", "Игры и профили"),
    DEVICE("Устройство", "Мониторинг и тесты"),
    MEDIA("Медиацентр", "Запись и Replay"),
    PERIPHERALS("Периферия", "Экран и управление"),
    ANALYTICS("Аналитика", "Игровые сессии, история и сравнения")
}
