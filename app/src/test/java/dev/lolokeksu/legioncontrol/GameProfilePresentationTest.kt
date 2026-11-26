package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class GameProfilePresentationTest {
    @Test fun profileShowsOnlyConfiguredActions() {
        val text = GameProfilePresentation.report(
            GameLaunchProfile(mediaPercent = 40, brightnessPercent = 80),
            AccessState.READY, null, "com.example.game"
        )
        assertTrue(text.contains("Громкость мультимедиа: 40%"))
        assertTrue(text.contains("Яркость: 80%"))
        assertTrue(text.contains("«Не беспокоить»: не менять"))
        assertTrue(text.contains(AccessState.READY.label))
    }

    @Test fun noProfileDoesNotInventValues() {
        val text = GameProfilePresentation.report(null, null, null, "com.example.game")
        assertTrue(text.contains("не настроен"))
        assertFalse(text.contains("100%"))
    }

    @Test fun pendingRestoreIsBoundToCorrectPackage() {
        val own = GameProfilePresentation.report(GameLaunchProfile(mediaPercent = 50),
            AccessState.READY, "com.example.game", "com.example.game")
        val other = GameProfilePresentation.report(GameLaunchProfile(mediaPercent = 50),
            AccessState.READY, "com.other.game", "com.example.game")
        assertTrue(own.contains("Для этой игры"))
        assertTrue(other.contains("com.other.game"))
    }
}
