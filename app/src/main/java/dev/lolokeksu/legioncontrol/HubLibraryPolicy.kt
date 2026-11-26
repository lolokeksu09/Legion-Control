package dev.lolokeksu.legioncontrol

import java.util.Locale

internal enum class HubGameFilter(val label: String) {
    GAMES("Игры и избранное"), FAVORITES("Избранное"), ALL("Все приложения")
}

internal enum class HubGameSort(val label: String) {
    PRIORITY("Избранное сначала"), NAME("По названию А–Я")
}

internal data class HubGamePage(val items: List<HubApp>, val total: Int,
                                val page: Int, val pageCount: Int)

internal object HubLibraryPolicy {
    const val PAGE_SIZE = 24

    fun select(apps: List<HubApp>, filter: HubGameFilter, sort: HubGameSort,
               query: String, requestedPage: Int): HubGamePage {
        val filtered = apps.filter {
            (when (filter) {
                HubGameFilter.GAMES -> it.isGame || it.favorite
                HubGameFilter.FAVORITES -> it.favorite
                HubGameFilter.ALL -> true
            }) && HubSearch.matches(it.label, it.packageName, query)
        }
        val name = compareBy<HubApp> { it.label.lowercase(Locale.ROOT) }
            .thenBy { it.packageName }
        val ordered = filtered.sortedWith(if (sort == HubGameSort.PRIORITY)
            compareByDescending<HubApp> { it.favorite }
                .thenByDescending { it.isGame }.then(name) else name)
        val count = ((ordered.size.toLong() + PAGE_SIZE - 1) / PAGE_SIZE).toInt().coerceAtLeast(1)
        val page = requestedPage.coerceIn(0, count - 1)
        return HubGamePage(ordered.drop(page * PAGE_SIZE).take(PAGE_SIZE), ordered.size, page, count)
    }

    /** Favorites are not silently dropped when Android's Launcher visibility changes. */
    fun homeFavorites(apps: List<HubApp>): List<HubApp> =
        select(apps, HubGameFilter.FAVORITES, HubGameSort.NAME, "", 0).items.take(3)

    fun <T : Enum<T>> restore(values: List<T>, name: String?, fallback: T): T =
        values.firstOrNull { it.name == name } ?: fallback
}
