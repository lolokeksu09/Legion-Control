package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class FeatureFinderTest {
    @Test fun lookupSupportsRussianEnglishAndMultipleWords() {
        assertTrue(FeatureFinder.search("РЕЗЕРВНАЯ ZIP").any { it.id == "capture.backup" })
        assertTrue(FeatureFinder.search("stereo").any { it.id == "audio.stereo" })
        assertTrue(FeatureFinder.search("снимок png").any { it.id == "capture.screenshot" })
        assertTrue(FeatureFinder.search("подключенные аудиовыходы").any { it.id == "audio.routes" })
        assertTrue(FeatureFinder.search("dns tls").any { it.id == "network.single" })
        assertTrue(FeatureFinder.search("widevine").any { it.id == "compat.drm" })
        assertTrue(FeatureFinder.search("sha256").any { it.id == "hardware.storage" })
        assertTrue(FeatureFinder.search("тепловая история").any { it.id == "diag.thermal" })
        assertTrue(FeatureFinder.search("unlikelysearch999").isEmpty())
        assertTrue(FeatureFinder.search("   ").isEmpty())
    }
    @Test fun mediaLibrarySearchRetainsOldKeywordsAndFindsNewAnchor() {
        for (query in listOf("библиотека клипов mp4", "медиатека png", "галерея", "clip workshop")) {
            assertTrue(query, FeatureFinder.search(query).any { it.id == "capture.clips" })
        }
        assertEquals("ОБНОВИТЬ МЕДИАТЕКУ", FeatureFinder.resolve("capture.clips", FeatureModule.CAPTURE)?.anchor)
    }
    @Test fun onlyKnownIdsForTheOwningModuleResolve() {
        assertNotNull(FeatureFinder.resolve("capture.backup", FeatureModule.DATA))
        assertNull(FeatureFinder.resolve("capture.backup", FeatureModule.AUDIO))
        assertNull(FeatureFinder.resolve("unknown", FeatureModule.CAPTURE))
        assertNull(FeatureFinder.resolve(null, FeatureModule.CAPTURE))
    }
    @Test fun allEntriesHaveUniqueStableIdsAndAnchors() {
        val entries = FeatureFinder.entries
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertEquals(entries.size, entries.map { it.module to it.anchor }.toSet().size)
        assertTrue(entries.all { it.anchor.isNotBlank() && it.keywords.isNotBlank() })
    }
}
