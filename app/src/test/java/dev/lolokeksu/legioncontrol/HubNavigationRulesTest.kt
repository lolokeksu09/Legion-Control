package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubNavigationRulesTest {
    @Test fun fiveTopLevelRoutesAreUniqueAndStable() {
        assertEquals(5, HubTab.entries.size)
        assertEquals(listOf("home","games","tools","history","settings"),
            HubTab.entries.map { it.key })
        assertEquals(5, HubTab.entries.map { it.key }.toSet().size)
        assertEquals("Главная", HubTab.HOME.shortTitle)
        assertEquals("Инструменты", HubTab.TOOLS.shortTitle)
    }

    @Test fun deepLinkSelectionIsBoundedToExplicitTabs() {
        assertEquals(HubTab.HOME, HubTab.parse("home"))
        assertEquals(HubTab.GAMES, HubTab.parse("games"))
        assertEquals(HubTab.HOME, HubTab.parse("../../admin"))
        assertEquals(HubTab.HOME, HubTab.parse(null))
    }

    @Test fun favoriteTogglePreservesExistingMetadata() {
        val original = HubFavoriteFields("Моя игра", "Соревновательные",
            "Контроллер Bluetooth", false)
        val updated = HubFavoriteRules.toggle(original, "Другой ярлык")
        assertEquals("Моя игра", updated.alias)
        assertEquals("Соревновательные", updated.category)
        assertEquals("Контроллер Bluetooth", updated.note)
        assertTrue(updated.favorite)
        assertFalse(HubFavoriteRules.toggle(updated, "Другой ярлык").favorite)
    }

    @Test fun freshFavoriteHasBoundedSafeName() {
        val row = HubFavoriteRules.toggle(null, "x".repeat(350))
        assertEquals(100, row.alias.length)
        assertEquals("Игра", row.category)
        assertEquals("", row.note)
        assertTrue(row.favorite)
    }

    @Test fun sanitizeSearchNeverTreatsPackageAsSpecialQuery() {
        assertEquals("wild", HubSearch.normalized(" Wild "))
        assertEquals("com.riotgames", HubSearch.normalized("COM.RIOTGAMES"))
        assertFalse(HubSearch.matches("Wild Rift", "com.game", "sudoku"))
        assertTrue(HubSearch.matches("Wild Rift", "com.game", "WILD"))
    }
}
