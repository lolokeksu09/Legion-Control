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
        assertTrue(FeatureFinder.search("unlikelysearch999").isEmpty())
        assertTrue(FeatureFinder.search("   ").isEmpty())
    }
    @Test fun onlyKnownIdsForTheOwningModuleResolve() {
        assertNotNull(FeatureFinder.resolve("capture.backup", FeatureModule.CAPTURE))
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
