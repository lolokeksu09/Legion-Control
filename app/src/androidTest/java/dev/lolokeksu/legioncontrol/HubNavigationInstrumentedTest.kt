package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HubNavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun searchOpensRealControlsWithoutStartingCapture() {
        assertFeatureRoute("stereo", "audio.stereo", FeatureModule.AUDIO, MainActivity::class.java)
        assertFeatureRoute("replay", "capture.replay", FeatureModule.CAPTURE, CaptureStudioActivity::class.java)
        assertFalse(CaptureCoordinator.busy())
        assertFalse(ReplayCaptureService.active)
    }

    @Test fun deviceSectionsReplaceThePreviousForegroundLab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host ->
                val pages = listOf(
                    DiagnosticsPage.GAMING to GamingDiagnosticsFragment::class.java,
                    DiagnosticsPage.HARDWARE to HardwareLabFragment::class.java,
                    DiagnosticsPage.COMPAT to CompatibilityLabFragment::class.java,
                    DiagnosticsPage.NETWORK to ConnectivityLabFragment::class.java
                )
                for ((page, fragment) in pages) {
                    assertTrue(host.openPage(page))
                    val mounted = host.supportFragmentManager.findFragmentById(R.id.device_lab_container)
                    assertEquals(fragment, mounted?.javaClass)
                }
                assertTrue(host.openPage(DiagnosticsPage.MONITOR))
                assertNull(host.supportFragmentManager.findFragmentById(R.id.device_lab_container))
                assertFalse(host.openPage(DiagnosticsPage.AUDIO))
            }
        }
    }

    private fun assertFeatureRoute(query: String, id: String, module: FeatureModule, target: Class<out Activity>) {
        val feature = requireNotNull(FeatureFinder.resolve(id, module))
        ActivityScenario.launch(LegionHubActivity::class.java).use { scenario ->
            var onTools = false
            await {
                scenario.onActivity { hub ->
                    if (!onTools) onTools = hub.findViewById<View>(R.id.hub_tab_tools)?.performClick() == true
                }
                onTools
            }
            await {
                var entered = false
                scenario.onActivity { hub ->
                    hub.findViewById<EditText>(R.id.hub_feature_search)?.let {
                        it.setText(query)
                        entered = true
                    }
                }
                entered
            }
            val monitor = instrumentation.addMonitor(target.name, null, false)
            try {
                await {
                    var tapped = false
                    scenario.onActivity { hub ->
                        tapped = FeatureReveal.findTarget(hub.window.decorView, feature)?.performClick() == true
                    }
                    tapped
                }
                val opened = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000L))
                try {
                    await {
                        var revealed = false
                        instrumentation.runOnMainSync {
                            revealed = FeatureReveal.findTarget(opened.window.decorView, feature)?.let {
                                it.isShown && it.isEnabled
                            } == true
                        }
                        revealed
                    }
                } finally {
                    instrumentation.runOnMainSync { opened.finish() }
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (check()) return
            Thread.sleep(100)
        }
        throw AssertionError("Navigation did not reach the expected control")
    }
}
