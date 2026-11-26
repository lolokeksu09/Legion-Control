package dev.lolokeksu.legioncontrol

import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GameSessionRecorderInstrumentedTest {
    @Test fun explicitCapturePersistsTheMarkerAndCompletion() {
        val fingerprint = Build.FINGERPRINT.lowercase()
        assertTrue("Disposable emulator required", fingerprint.contains("generic") ||
            fingerprint.contains("emulator") || Build.PRODUCT.lowercase().contains("sdk"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pkg = "dev.legion.recorder.ci" + UUID.randomUUID().toString().replace("-", "")
        val marker = "CI session marker"
        ActivityScenario.launch(LegionHubActivity::class.java).use { scenario ->
            GameSessionRepository(context).use { repo ->
                assertFalse(GameSessionRecorderService.sessionActive)
                scenario.onActivity { GameSessionRecorderService.start(it, pkg, "Recorder fixture", false) }
                try {
                    val row = await { repo.recentForPackage(pkg).singleOrNull() }
                    assertFalse(row.tcpOptIn)
                    scenario.onActivity { GameSessionRecorderService.mark(it, marker) }
                    val saved = await {
                        repo.eventsChronological(row.id, 20)
                            .firstOrNull { it.kind == "USER_MARK" && it.details == marker }
                    }
                    assertEquals(marker, saved.details)
                    scenario.onActivity { GameSessionRecorderService.stop(it) }
                    val ended = await { repo.header(row.id)?.takeIf { it.endedAtMs != null } }
                    assertEquals("USER_STOP", ended.endReason)
                    val snapshot = UnifiedGameSessionSnapshot.from(repo.analyticsDetail(row.id))
                    assertTrue(SessionSignals.render(snapshot.signals).contains(marker))
                } finally {
                    if (GameSessionRecorderService.sessionActive)
                        scenario.onActivity { GameSessionRecorderService.stop(it) }
                }
            }
        }
    }

    private fun <T : Any> await(read: () -> T?): T {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            read()?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for the recorder to persist the expected state")
    }
}
