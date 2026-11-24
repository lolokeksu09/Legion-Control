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
    @Test fun finderUsesIdentityNotLabelAndNeverClicks() {
        ActivityScenario.launch(LiveMonitorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = android.widget.LinearLayout(activity)
                val target = android.widget.Button(activity).apply { text = "Original title" }
                val decoy = android.widget.Button(activity).apply { text = "Original title" }
                var clicks = 0
                target.setOnClickListener { clicks++ }
                decoy.setOnClickListener { clicks++ }
                root.addView(decoy)
                root.addView(target)
                val feature = requireNotNull(FeatureFinder.resolve("monitor.start", FeatureModule.MONITOR))
                assertNull(FeatureReveal.findTarget(root, feature))
                FeatureReveal.bind(target, feature.id)
                assertSame(target, FeatureReveal.findTarget(root, feature))
                target.text = "Changed title"
                assertSame(target, FeatureReveal.findTarget(root, feature))
                target.isEnabled = false
                assertSame(target, FeatureReveal.findTarget(root, feature))
                FeatureReveal.bind(decoy, feature.id)
                assertNull("Duplicate visible IDs must fail closed", FeatureReveal.findTarget(root, feature))
                decoy.visibility = android.view.View.GONE
                assertSame(target, FeatureReveal.findTarget(root, feature))
                assertEquals(0, clicks)
            }
        }
    }

    @Test fun sharedButtonUsesCompactMinimumWithoutFixedHeight() {
        ActivityScenario.launch(LiveMonitorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = android.widget.Button(activity).apply { text = "Long action label" }
                LegionUiSupport.styleAction(button)
                val expected = (48 * activity.resources.displayMetrics.density + 0.5f).toInt()
                assertEquals(expected, button.minHeight)
                assertEquals(expected, button.minimumHeight)
                assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, button.layoutParams.height)
                assertFalse(button.isAllCaps)
            }
        }
    }

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
                assertTrue(model.beginExport())
                assertFalse(model.beginExport())
                model.start()
                assertFalse("Pending picker must preserve captured run", model.state.value!!.running)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val retained = ViewModelProvider(activity)[LiveMonitorViewModel::class.java]
                assertSame(model, retained)
                assertTrue(retained.hasPendingDocument)
                assertEquals(count, retained.state.value!!.points.size)
                retained.finishExport(null)
                assertFalse(retained.hasPendingDocument)
                assertTrue("Cancel permits retry", retained.beginExport())
                retained.finishExport(null)
                model.start()
                assertTrue(model.state.value!!.running)
                assertNotNull("Pinned baseline survives a new run", model.state.value!!.baseline)
                model.stop()
                assertFalse(model.state.value!!.running)
            }
        }
    }
}
