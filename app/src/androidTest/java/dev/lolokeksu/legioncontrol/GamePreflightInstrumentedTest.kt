package dev.lolokeksu.legioncontrol

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GamePreflightInstrumentedTest {
    @Test fun realSnapshotAndPersistentPerGameIsolation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val packageA="dev.legion.preflight.test.a"
        val packageB="dev.legion.preflight.test.b"
        val presetA=PreflightPreset(120,30,41,3,true)
        val presetB=PreflightPreset(60,10,44,1,false)
        assertTrue(PreflightPresets.save(context,packageA,presetA))
        assertTrue(PreflightPresets.save(context,packageB,presetB))
        assertEquals(presetA,PreflightPresets.read(context,packageA))
        assertEquals(presetB,PreflightPresets.read(context,packageB))
        assertEquals(PreflightPreset(),PreflightPresets.read(context,"dev.legion.preflight.test.c"))

        ActivityScenario.launch(GamePreflightActivity::class.java).use { scenario ->
            lateinit var vm: GamePreflightViewModel
            scenario.onActivity { vm=ViewModelProvider(it)[GamePreflightViewModel::class.java] }
            fun await(description:String, predicate:(PreflightState)->Boolean) {
                val end=android.os.SystemClock.elapsedRealtime()+20000L
                while(android.os.SystemClock.elapsedRealtime()<end) {
                    var ready=false
                    scenario.onActivity { ready=predicate(requireNotNull(vm.state.value)) }
                    if(ready) return
                    Thread.sleep(150)
                }
                fail(description)
            }
            await("Launcher application list must load") { !it.busy && it.apps.isNotEmpty() }
            scenario.onActivity {
                val state=vm.state.value!!
                assertNull(state.report)
                val chosen=state.apps.firstOrNull { a -> a.packageName=="com.android.settings" }
                    ?: state.apps.first()
                vm.select(chosen.packageName)
            }
            await("Selection and preset must load") { !it.busy && it.selected!=null }
            scenario.onActivity {
                vm.savePreset(presetA)
            }
            await("Preset must be persisted") { !it.busy && it.preset==presetA }
            scenario.onActivity { vm.check() }
            await("Real report must finish") { !it.busy && it.report!=null }
            scenario.onActivity {
                val state=vm.state.value!!
                val report=state.report!!
                assertEquals(state.selected!!.packageName,report.packageName)
                assertEquals(presetA,report.preset)
                assertEquals(11,report.findings.size)
                assertTrue(report.findings.any { f ->
                    f.key in listOf("battery","display","ram") && f.level!=PreflightLevel.UNKNOWN
                })
                assertTrue(PreflightRules.fresh(report.checkedAtElapsed,android.os.SystemClock.elapsedRealtime()))
                assertFalse(GameSessionRecorderService.sessionActive)
                assertFalse(CaptureCoordinator.busy())
                assertTrue(state.launchFresh)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertNotNull("Stopped screen retains shareable report",vm.state.value!!.report)
                assertFalse("Returning to screen requires a new launch check",vm.state.value!!.launchFresh)
                vm.savePreset(presetB)
            }
            await("Changing requirements invalidates prior report") {
                !it.busy && it.preset==presetB && it.report==null
            }
        }
    }
}
