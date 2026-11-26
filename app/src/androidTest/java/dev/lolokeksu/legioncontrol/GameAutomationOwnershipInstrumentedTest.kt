package dev.lolokeksu.legioncontrol

import android.content.ActivityNotFoundException
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameAutomationOwnershipInstrumentedTest {
    @Test fun mismatchedOwnerAndChangedApprovalCannotConsumeTicket() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("legion-launch-profiles-v1",Context.MODE_PRIVATE)
        val pkg="dev.legion.automation.test"
        val keys=listOf("pending_global_undo","pending_auto_owner","profile_"+pkg,"pending_undo_generation")
        val previous=keys.associateWith{prefs.getString(it,null)}
        val ticket=GameLaunchUndo(pkg,LaunchSettingChange(1,2),null,null,null).encode()
        try {
            assertTrue(prefs.edit().putString(keys[0],ticket).putString(keys[1],"owner-a")
                .putString(keys[2],GameLaunchProfile(mediaPercent=20).encode())
                .putString(keys[3],"generation-a").commit())
            val approval = requireNotNull(GameLaunchProfiles.pendingConfirmation(context))
            // Identical values/package/owner, but a different ticket instance: no Android writes.
            assertTrue(prefs.edit().putString(keys[3],"generation-b").commit())
            val staleUndo = GameLaunchProfiles.restoreConfirmed(context, approval)
            assertFalse(staleUndo.success)
            assertTrue(staleUndo.message.contains("Запись возврата изменилась"))
            assertEquals(ticket,prefs.getString(keys[0],null))
            assertEquals("generation-b",prefs.getString(keys[3],null))
            var manualExecuted = false
            val blocked = SmartSystemControls.withManualMutation(context) {
                manualExecuted = true
                "unexpected"
            }
            assertFalse(manualExecuted)
            assertTrue(blocked.contains("возврат игрового профиля"))
            assertEquals(ticket,prefs.getString(keys[0],null))
            assertTrue(GameLaunchProfiles.ownsAutomatic(context,"owner-a"))
            assertFalse(GameLaunchProfiles.ownsAutomatic(context,"owner-b"))
            assertTrue(GameLaunchProfiles.restoreAutomatic(context,"owner-b").success)
            assertEquals(ticket,prefs.getString(keys[0],null))
            // A stale confirmation must be rejected before the existing-ticket gate.
            // Synthetic ticket makes this test non-mutating even if that check regresses.
            val staleManual = GameLaunchProfiles.applyConfirmed(context, pkg, GameLaunchProfile(mediaPercent=80))
            assertFalse(staleManual.success)
            assertTrue(staleManual.message.contains("Профиль изменился после подтверждения"))
            val currentManual = GameLaunchProfiles.applyConfirmed(context, pkg, GameLaunchProfile(mediaPercent=20))
            assertFalse(currentManual.success)
            assertTrue(currentManual.message.contains("Сначала верните предыдущие"))
            assertEquals(ticket, prefs.getString(keys[0], null))
            var launchCalled = false
            val blockedLaunch = GameLaunchProfiles.launchConfirmed(context, pkg, GameLaunchProfile(mediaPercent=20)) {
                launchCalled = true
            }
            assertFalse(blockedLaunch.success)
            assertFalse(launchCalled)
            assertEquals(ticket, prefs.getString(keys[0], null))
            assertFalse(GameLaunchProfiles.applyAutomatic(context,pkg,"owner-b",GameLaunchProfile(mediaPercent=80)).success)
            assertFalse(GameLaunchProfiles.applyAutomatic(context,pkg,"owner-b",GameLaunchProfile(mediaPercent=20)).success)
            assertEquals("owner-a",prefs.getString(keys[1],null))
            assertEquals(ticket,prefs.getString(keys[0],null))
        } finally {
            val edit=prefs.edit()
            previous.forEach{(key,value)->if(value==null)edit.remove(key)else edit.putString(key,value)}
            check(edit.commit())
        }
    }

    @Test fun failedLaunchRestoresTheVolumeAppliedForThatAttempt() {
        assumeTrue("Run volume mutation on the emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pkg = "dev.legion.launch.rollback.test"
        val audio = context.getSystemService(AudioManager::class.java)
        assumeTrue(audio != null && !audio.isVolumeFixed)
        requireNotNull(audio)
        val stream = AudioManager.STREAM_MUSIC
        val before = audio.getStreamVolume(stream)
        val maximum = audio.getStreamMaxVolume(stream)
        val minimum = audio.getStreamMinVolume(stream)
        assumeTrue(maximum > minimum && GameLaunchProfiles.pendingPackage(context) == null)
        val percent = (0..100).first {
            val index = GameLaunchProfilePolicy.mediaIndex(it, maximum)
            index >= minimum && index != before
        }
        val profile = GameLaunchProfile(mediaPercent = percent)
        val expectedDuringLaunch = GameLaunchProfilePolicy.mediaIndex(percent, maximum)
        val originalProfile = GameLaunchProfiles.profile(context, pkg)
        try {
            assertTrue(GameLaunchProfiles.save(context, pkg, profile))
            var volumeDuringLaunch: Int? = null
            val result = GameLaunchProfiles.launchConfirmed(context, pkg, profile) {
                volumeDuringLaunch = audio.getStreamVolume(stream)
                throw ActivityNotFoundException("Test launcher failure")
            }
            assertFalse(result.success)
            assertEquals(expectedDuringLaunch, volumeDuringLaunch)
            assertEquals(before, audio.getStreamVolume(stream))
            assertNull(GameLaunchProfiles.pendingPackage(context))
        } finally {
            if (GameLaunchProfiles.pendingPackage(context) == pkg) GameLaunchProfiles.restore(context)
            if (audio.getStreamVolume(stream) != before) audio.setStreamVolume(stream, before, 0)
            GameLaunchProfiles.save(context, pkg, originalProfile ?: GameLaunchProfile())
        }
    }
}
