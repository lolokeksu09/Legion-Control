package dev.lolokeksu.legioncontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLaunchProfilePolicyTest {
    @Test fun optionalSettingsRemainOptIn() {
        val profile = GameLaunchProfile()
        assertFalse(profile.actionable)
        assertEquals("v1|-|-|-", profile.encode())
        assertEquals(profile, GameLaunchProfile.decode(profile.encode()))
    }

    @Test fun independentMediaBrightnessAndFocusRoundTrip() {
        for (profile in listOf(
            GameLaunchProfile(0, 10, true),
            GameLaunchProfile(100, 100, false),
            GameLaunchProfile(60, null, null),
            GameLaunchProfile(null, 75, null),
            GameLaunchProfile(null, null, false)
        )) {
            assertEquals(profile, GameLaunchProfile.decode(profile.encode()))
            assertTrue(profile.actionable)
        }
    }

    @Test fun invalidProfileDoesNotSilentlyDefaultToSystemWrites() {
        listOf("x", "v2|50|-|on", "v1|500|-|on", "v1|30|0|on",
            "v1|30|80|bad", "v1|abc|50|off", "v1|10|10").forEach {
            assertNull(GameLaunchProfile.decode(it))
        }
    }

    @Test fun packageValidationDoesNotAcceptInjectedSeparatorsOrBareNames() {
        assertTrue(GameLaunchProfilePolicy.validPackage("com.riotgames.league.wildrift"))
        for (bad in listOf("", "game", ".game.name", "game|name.other",
            "good.other;delete", "bad/other.name", "bad.other\n")) {
            assertFalse(GameLaunchProfilePolicy.validPackage(bad))
        }
        assertFalse(GameLaunchProfilePolicy.validPackage("a." + "x".repeat(200)))
    }

    @Test fun volumeConversionUsesIntegerArithmeticAndStreamBoundaries() {
        assertEquals(0, GameLaunchProfilePolicy.mediaIndex(0, 15))
        assertEquals(8, GameLaunchProfilePolicy.mediaIndex(50, 15))
        assertEquals(15, GameLaunchProfilePolicy.mediaIndex(100, 15))
        assertEquals(0, GameLaunchProfilePolicy.mediaIndex(100, 0))
        assertEquals(50_000, GameLaunchProfilePolicy.mediaIndex(100, 50_000))
    }

    @Test fun restoreProtectsManuallyChangedSettings() {
        val change = LaunchSettingChange(4, 9)
        assertEquals(RestoreChoice.RESTORE,
            GameLaunchProfilePolicy.restoreChoice(9, change))
        assertEquals(RestoreChoice.ALREADY_ORIGINAL,
            GameLaunchProfilePolicy.restoreChoice(4, change))
        assertEquals(RestoreChoice.CHANGED_BY_USER,
            GameLaunchProfilePolicy.restoreChoice(7, change))
    }

    @Test fun durableUndoRoundTripAcrossProcessRestart() {
        val ticket = GameLaunchUndo(
            "com.example.game", LaunchSettingChange(3, 8),
            LaunchSettingChange(104, 192), LaunchSettingChange(1, 0),
            LaunchSettingChange(2, 3)
        )
        assertEquals(ticket, GameLaunchUndo.decode(ticket.encode()))
        assertTrue(ticket.pending)
        assertFalse(ticket.copy(media=null, brightness=null, brightnessMode=null, focus=null).pending)
    }

    @Test fun legacyV1UndoDecodesAsGlobalFilterScope() {
        val legacy = GameLaunchUndo.decode("v1|com.example.game|-|-|-|2,3")
        assertEquals(FocusUndoScope.GLOBAL_FILTER, legacy?.focusScope)
        assertTrue(legacy?.focusRuleExisted == true)
    }

    @Test fun android15UndoRoundTripsImplicitRulePresence() {
        val ticket = GameLaunchUndo(
            "com.example.game", null, null, null, LaunchSettingChange(0, 113),
            FocusUndoScope.APP_RULE, false
        )
        val decoded = GameLaunchUndo.decode(ticket.encode())
        assertEquals(ticket, decoded)
        assertTrue(ticket.encode().startsWith("v2|"))
    }

    @Test fun implicitRuleSnapshotDistinguishesMissingFromInactive() {
        assertEquals(0, AppDndRuleSnapshot(false, 0, 1).encoded)
        assertEquals(AppDndRuleSnapshot(false, 0, 1), AppDndRuleSnapshot.decode(0))
        assertEquals(AppDndRuleSnapshot(true, 0, 2), AppDndRuleSnapshot.decode(102))
    }

    @Test fun implicitRuleSnapshotTracksFilterAsWellAsActivation() {
        val priority = AppDndRuleSnapshot(true, 1, 2)
        val alarms = AppDndRuleSnapshot(true, 1, 4)
        assertTrue(priority.encoded != alarms.encoded)
        assertEquals(priority, AppDndRuleSnapshot.decode(priority.encoded))
    }

    @Test fun invalidImplicitRuleSnapshotIsRejected() {
        assertTrue(runCatching { AppDndRuleSnapshot.decode(99) }.isFailure)
        assertTrue(runCatching { AppDndRuleSnapshot.decode(115) }.isFailure)
    }

    @Test fun malformedRestoreTicketIsRejectedRatherThanApplied() {
        for (bad in listOf("v1|com.example.game|0,8|a,2|-|-",
            "v1|com.example.game|0,-1|-|-|-",
            "v1|com.example.game|0,2|0,2|x|-",
            "v1|bad|1,2|-|-|-", "v2|com.example.game|-|-|-|-",
            "v2|com.example.game|-|-|-|-|bad|0", "v2|com.example.game|-|-|-|-|app_rule|yes")) {
            assertNull(GameLaunchUndo.decode(bad))
        }
    }
}
