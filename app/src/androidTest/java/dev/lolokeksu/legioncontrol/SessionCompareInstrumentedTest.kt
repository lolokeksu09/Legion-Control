package dev.lolokeksu.legioncontrol

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Test fixtures only on disposable CI emulators. */
@RunWith(AndroidJUnit4::class)
class SessionCompareInstrumentedTest {
    @Test fun selectsDistinctLocalSessionsAndInvalidatesStaleComparison() {
        val fingerprint=Build.FINGERPRINT.lowercase()
        assertTrue("CI emulator required",fingerprint.contains("generic") ||
            fingerprint.contains("emulator") || Build.PRODUCT.lowercase().contains("sdk"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pkg="dev.legion.compare.ci"
        val other="dev.legion.compare.other"
        val start=System.currentTimeMillis()-2_400_000L
        lateinit var older:String
        lateinit var newer:String
        GameSessionRepository(context).use { repo ->
            fun seed(packageName:String,at:Long,temp:Int):String {
                val id=repo.start(packageName,"CI fixture",false,at)
                for(i in 0..40)repo.record(id,GameTelemetryPoint(at+i*15000L,
                    80-i/10,temp+i,null,0,false,120f,"WIFI",true))
                repo.finish(id,"USER_STOP",at+600000L)
                return id
            }
            older=seed(pkg,start,400)
            newer=seed(pkg,start+700000L,420)
            seed(other,start+1400000L,500)
        }
        val intent=Intent(context,SessionCompareActivity::class.java)
            .putExtra(SessionCompareActivity.EXTRA_PACKAGE,pkg)
        ActivityScenario.launch<SessionCompareActivity>(intent).use { scenario ->
            lateinit var vm:SessionCompareViewModel
            scenario.onActivity { vm=ViewModelProvider(it)[SessionCompareViewModel::class.java] }
            fun await(predicate:(CompareState)->Boolean) {
                val end=SystemClock.elapsedRealtime()+15000L
                while(SystemClock.elapsedRealtime()<end) {
                    var ready=false
                    scenario.onActivity { ready=predicate(vm.state.value!!) }
                    if(ready)return
                    Thread.sleep(100)
                }
                fail("Expected comparison state not observed")
            }
            await { !it.busy && it.sessions.size>=2 }
            scenario.onActivity {
                val s=vm.state.value!!
                assertEquals(pkg,s.packageName)
                assertTrue(s.sessions.all { h->h.packageName==pkg })
                assertEquals(older,s.aId)
                assertEquals(newer,s.bId)
                assertNull(s.pair)
                vm.chooseWindow(5)
                vm.compare()
            }
            await { !it.busy && it.pair!=null }
            scenario.onActivity {
                val pair=vm.state.value!!.pair!!
                assertTrue(pair.comparable)
                assertEquals(21,pair.a.points)
                assertEquals(2.0,pair.delta(CompareMeasure.PEAK_TEMP)!!,0.001)
                vm.swap()
                assertNull(vm.state.value!!.pair)
                vm.compare()
            }
            await { !it.busy && it.pair!=null }
            scenario.onActivity {
                val pair=vm.state.value!!.pair!!
                assertEquals(-2.0,pair.delta(CompareMeasure.PEAK_TEMP)!!,0.001)
                vm.chooseWindow(15)
                assertNull(vm.state.value!!.pair)
                vm.compare()
            }
            await { !it.busy && it.pair!=null }
            scenario.onActivity {
                val pair=vm.state.value!!.pair!!
                assertFalse(pair.comparable)
                assertNull(pair.delta(CompareMeasure.PEAK_TEMP))
                assertTrue(SessionCompareAnalysis.csv(pair).contains("PEAK_TEMP"))
                assertFalse(GameSessionRecorderService.sessionActive)
            }
        }
    }
}
