package dev.lolokeksu.legioncontrol

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameAutomationOwnershipInstrumentedTest {
    @Test fun mismatchedOwnerAndChangedApprovalCannotConsumeTicket() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("legion-launch-profiles-v1",Context.MODE_PRIVATE)
        val pkg="dev.legion.automation.test"
        val keys=listOf("pending_global_undo","pending_auto_owner","profile_"+pkg)
        val previous=keys.associateWith{prefs.getString(it,null)}
        val ticket=GameLaunchUndo(pkg,LaunchSettingChange(1,2),null,null,null).encode()
        try {
            assertTrue(prefs.edit().putString(keys[0],ticket).putString(keys[1],"owner-a")
                .putString(keys[2],GameLaunchProfile(mediaPercent=20).encode()).commit())
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
}
