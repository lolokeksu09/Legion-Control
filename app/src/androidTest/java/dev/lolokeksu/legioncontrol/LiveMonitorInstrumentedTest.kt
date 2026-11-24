package dev.lolokeksu.legioncontrol

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveMonitorInstrumentedTest {
    @Test fun realReadingsStopWhenScreenStopsAndRequireExplicitRestart() {
        ActivityScenario.launch(LiveMonitorActivity::class.java).use { scenario ->
            lateinit var model: LiveMonitorViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity)[LiveMonitorViewModel::class.java]
                assertFalse(model.state.value!!.running)
                assertTrue(model.state.value!!.points.isEmpty())
                model.interval(2000)
                model.start()
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 20000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                var ready = false
                scenario.onActivity { ready = model.state.value!!.points.size >= 2 }
                if (ready) break
                Thread.sleep(250)
            }
            scenario.onActivity {
                val points = model.state.value!!.points
                assertTrue("Actual periodic readings required", points.size >= 2)
                assertTrue("Expected real Android battery or RAM/display reading", points.any {
                    it.battery != null || it.availableRamBytes != null || it.displayHz != null
                })
                assertTrue(points.zipWithNext().all { (a,b) -> b.elapsedMs >= a.elapsedMs })
                model.pinBaseline()
                assertEquals(points.last(), model.state.value!!.baseline)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            var count = -1
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertFalse("onStop must halt collection", model.state.value!!.running)
                count = model.state.value!!.points.size
            }
            Thread.sleep(4500)
            scenario.onActivity {
                assertEquals("No implicit restart or late publication", count, model.state.value!!.points.size)
                assertFalse(model.state.value!!.running)
                model.start()
                assertTrue(model.state.value!!.running)
                assertNotNull("Pinned baseline survives a new run", model.state.value!!.baseline)
                model.stop()
                assertFalse(model.state.value!!.running)
            }
        }
    }
}
