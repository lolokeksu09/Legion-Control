package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class HubLibraryPolicyTest {
    private fun app(i: Int, game: Boolean = true, favorite: Boolean = false, visible: Boolean = true) =
        HubApp("com.test.app$i", "Game %03d".format(i), game, favorite, visible, favorite)

    @Test fun allRowsAreReachableWithoutTruncation() {
        val apps = (1..73).map { app(it) }
        val pages = (0..3).flatMap {
            HubLibraryPolicy.select(apps, LibraryFilter.ALL, LibrarySort.NAME, "", it).items
        }
        assertEquals(73, pages.size)
        assertEquals(73, pages.map { it.packageName }.distinct().size)
    }

    @Test fun outOfRangePageClampsAfterFilter() {
        val result = HubLibraryPolicy.select(listOf(app(1)), LibraryFilter.ALL, LibrarySort.NAME, "", 99)
        assertEquals(0, result.page)
        assertEquals(1, result.pageCount)
        assertEquals(1, result.total)
    }

    @Test fun filtersKeepHiddenFavoritesButExcludeOtherApps() {
        val apps = listOf(app(1, false), app(2, false, true, false), app(3))
        val result = HubLibraryPolicy.select(apps, LibraryFilter.GAMES, LibrarySort.PRIORITY, "", 0)
        assertEquals(listOf("com.test.app2", "com.test.app3"), result.items.map { it.packageName })
        assertFalse(result.items.first().visible)
        assertEquals(listOf(apps[1]), HubLibraryPolicy.homeFavorites(apps))
    }

    @Test fun packageQueryAndSortAreIndependent() {
        val apps = listOf(app(3, favorite = true), app(1), app(2))
        assertEquals(apps[1], HubLibraryPolicy.select(apps, LibraryFilter.ALL, LibrarySort.NAME, "", 0).items.first())
        assertEquals(apps[0], HubLibraryPolicy.select(apps, LibraryFilter.ALL, LibrarySort.PRIORITY, "", 0).items.first())
        assertEquals(listOf(apps[2]), HubLibraryPolicy.select(apps, LibraryFilter.ALL, LibrarySort.NAME, "COM.TEST.APP2", 0).items)
    }

    @Test fun emptyAndUnknownSavedStateAreSafe() {
        val result = HubLibraryPolicy.select(emptyList(), LibraryFilter.ALL, LibrarySort.NAME, "", -5)
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.page)
        assertEquals(LibraryFilter.GAMES, HubLibraryPolicy.restore(LibraryFilter.entries, "invalid", LibraryFilter.GAMES))
    }
}
