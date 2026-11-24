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
            assertTrue(GameLaunchProfiles.ownsAutomatic(context,"owner-a"))
            assertFalse(GameLaunchProfiles.ownsAutomatic(context,"owner-b"))
            assertTrue(GameLaunchProfiles.restoreAutomatic(context,"owner-b").success)
            assertEquals(ticket,prefs.getString(keys[0],null))
            assertFalse(GameLaunchProfiles.applyAutomatic(context,pkg,"owner-b",GameLaunchProfile(mediaPercent=80)).success)
            assertFalse(GameLaunchProfiles.applyAutomatic(context,pkg,"owner-b",GameLaunchProfile(mediaPercent=20)).success)
            assertEquals("owner-a",prefs.getString(keys[1],null))
            assertEquals(ticket,prefs.getString(keys[0],null))
            var entered=false
            assertTrue(runCatching{GameLaunchProfiles.withRestoreProtection(context){entered=true}}.isFailure)
            assertFalse("Pending undo must prevent entering preference replay",entered)
            assertEquals(ticket,prefs.getString(keys[0],null))
            assertTrue(prefs.edit().remove(keys[0]).remove(keys[1]).commit())
            GameLaunchProfiles.withRestoreProtection(context){
                assertFalse(GameLaunchProfiles.save(context,pkg,GameLaunchProfile(mediaPercent=30)))
                assertFalse(GameLaunchProfiles.applyAutomatic(context,pkg,"owner-b",GameLaunchProfile(mediaPercent=20)).success)
                GameLaunchProfiles.withRestoreProtection(context){
                    assertFalse(GameLaunchProfiles.save(context,pkg,GameLaunchProfile(mediaPercent=30)))
                }
                assertFalse(GameLaunchProfiles.save(context,pkg,GameLaunchProfile(mediaPercent=30)))
            }
            assertTrue(GameLaunchProfiles.save(context,pkg,GameLaunchProfile(mediaPercent=30)))
            assertEquals(GameLaunchProfile(mediaPercent=30),GameLaunchProfiles.profile(context,pkg))
        } finally {
            val edit=prefs.edit()
            previous.forEach{(key,value)->if(value==null)edit.remove(key)else edit.putString(key,value)}
            check(edit.commit())
        }
    }
}
