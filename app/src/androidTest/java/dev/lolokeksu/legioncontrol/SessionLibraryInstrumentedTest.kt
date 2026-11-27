package dev.lolokeksu.legioncontrol

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Mutates only disposable CI emulator SQLite fixtures. */
@RunWith(AndroidJUnit4::class)
class SessionLibraryInstrumentedTest {
    @Test fun reportOpensTheExactSessionInExperimentAndBackReturnsToReport() {
        requireEmulator()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val game = "dev.legion.experiment.ci" + SystemClock.uptimeMillis()
        val otherGame = "$game.other"
        val now = System.currentTimeMillis()
        try {
            val selected = GameSessionRepository(context).use { repo ->
                val id = repo.start(game, "Original game label", false, now - 120_000)
                repo.finish(id, "USER_STOP", now - 60_000)
                repeat(21) {
                    val start = now - 1_000 + it
                    val other = repo.start(otherGame, "Another game", false, start)
                    repo.finish(other, "USER_STOP", start)
                }
                val open = repo.start(game, "Current game label", false, now - 30_000)
                assertFalse(repo.libraryPage(LibraryFilter(), now).rows.any { it.id == id })
                assertEquals(listOf(open, id), repo.libraryPage(LibraryFilter(game), now).rows.map { it.id })
                assertEquals("Current game label", repo.knownSessionPackages().getValue(game))
                ExperimentRepository(context).use { experiments ->
                    assertEquals(id, experiments.sessionForPackage(game, id)?.id)
                    assertNull(experiments.sessionForPackage(otherGame, id))
                    assertNull(experiments.sessionForPackage(game, "absent"))
                    val unfinished = requireNotNull(experiments.sessionForPackage(game, open))
                    assertNull(unfinished.endedAtMs)
                }
                id
            }
            val launch = SessionLibraryActivity.intent(context, game, selected)
            ActivityScenario.launch<SessionLibraryActivity>(launch).use { scenario ->
                val monitor = instrumentation.addMonitor(ExperimentLabActivity::class.java.name, null, false)
                try {
                    await("Selected report did not open") {
                        var clicked = false
                        scenario.onActivity { report ->
                            val action = report.findViewById<View>(R.id.analytics_experiment_action)
                            if (action.isShown && action.isEnabled) clicked = action.performClick()
                        }
                        clicked
                    }
                    val experiment = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000L))
                        as ExperimentLabActivity
                    try {
                        assertEquals(game, experiment.intent.getStringExtra(ExperimentLabActivity.EXTRA_PACKAGE))
                        assertEquals(selected, experiment.intent.getStringExtra(ExperimentLabActivity.EXTRA_SESSION))
                        await("A/B did not bind the exact recorded session") {
                            var bound = false
                            instrumentation.runOnMainSync {
                                val contextView = experiment.findViewById<TextView>(R.id.experiment_selected_session)
                                bound = contextView?.text?.contains(selected.take(8)) == true
                            }
                            bound
                        }
                    } finally {
                        instrumentation.runOnMainSync { experiment.onBackPressedDispatcher.onBackPressed() }
                    }
                    scenario.onActivity { report ->
                        val action = report.findViewById<View>(R.id.analytics_experiment_action)
                        assertTrue(action.isShown && action.isEnabled)
                    }
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
            }
        } finally {
            LocalHistoryStore(context).use { store ->
                store.writableDatabase.delete("game_sessions", "package_name IN (?,?)", arrayOf(game, otherGame))
            }
        }
    }

    private fun await(message: String, check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (check()) return
            Thread.sleep(50)
        }
        throw AssertionError(message)
    }

    private fun requireEmulator() {
        val fingerprint = Build.FINGERPRINT.lowercase()
        assertTrue("CI emulator only", fingerprint.contains("generic") ||
            fingerprint.contains("emulator") || Build.PRODUCT.lowercase().contains("sdk"))
    }

    @Test fun stablePagingFiltersAndSingleRecordExport() {
        requireEmulator()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pkg="dev.legion.library.ci"
        val other="dev.legion.library.other"
        val now=System.currentTimeMillis()
        val at=now-60000L
        GameSessionRepository(context).use { repo ->
            val expected=mutableListOf<String>()
            for(i in 0..22) {
                val id=repo.start(pkg,"Library fixture",false,at)
                repo.record(id,GameTelemetryPoint(at,80,400,null,0,false,60f,"WIFI",true))
                repo.event(id,"USER_MARK","fixture-$i",at+1)
                repo.finish(id,"USER_STOP",at+1000)
                expected+=id
            }
            val foreign=repo.start(other,"Other fixture",false,at)
            repo.record(foreign,GameTelemetryPoint(at,80,400,null,0,false,60f,"WIFI",true))
            repo.event(foreign,"USER_MARK","DO_NOT_EXPORT_OTHER",at+1)
            repo.finish(foreign,"USER_STOP",at+1000)
            val interrupted=repo.start(pkg,"Interrupted fixture",false,at+2000)
            repo.finish(interrupted,"INTERRUPTED",at+3000)
            val open=repo.start(pkg,"Open fixture",false,at+4000)
            val filter=LibraryFilter(pkg,LibraryPeriod.WEEK,LibraryStatus.FINISHED)
            val page=repo.libraryPage(filter,now)
            val next=repo.libraryPage(filter,now,requireNotNull(page.next))
            assertEquals(20,page.rows.size)
            assertEquals(3,next.rows.size)
            assertNull(next.next)
            assertEquals(expected.sortedDescending(),(page.rows+next.rows).map { it.id })
            assertEquals(listOf(interrupted),repo.libraryPage(filter.copy(status=LibraryStatus.INTERRUPTED),now).rows.map { it.id })
            assertEquals(listOf(open),repo.libraryPage(filter.copy(status=LibraryStatus.OPEN),now).rows.map { it.id })
            repo.event(open, "USER_MARK", "in-progress", at + 4001)
            val preview = repo.analyticsDetail(open)
            assertNull(preview.header.endedAtMs)
            assertEquals("in-progress", preview.events.single().details)
            assertTrue(runCatching { repo.libraryDetail(open) }.isFailure)
            val selected=expected.first()
            val detail=repo.libraryDetail(selected)
            assertEquals(selected,detail.header.id)
            assertEquals(1,detail.points.size)
            assertEquals(1,detail.events.size)
            val bytes=ByteArrayOutputStream()
            SessionLibraryExport.write(detail,bytes)
            val entries=linkedMapOf<String,String>()
            ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
                while(true) {
                    val entry=zip.nextEntry ?: break
                    entries[entry.name]=zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            assertEquals(setOf("session.csv","samples.csv","events.csv","README.txt"),entries.keys)
            assertTrue(entries.getValue("samples.csv").contains(selected))
            assertTrue(entries.getValue("events.csv").contains("fixture-0"))
            assertFalse(entries.values.any { it.contains(foreign) || it.contains("DO_NOT_EXPORT_OTHER") })
            // Exact limit is complete; only the sentinel row establishes actual truncation.
            LocalHistoryStore(context).use { helper ->
                val db=helper.writableDatabase
                db.beginTransaction()
                try {
                    for(i in 1 until SessionLibraryPolicy.MAX_EVENTS)
                        db.execSQL("INSERT INTO game_events(session_id,recorded_at_ms,event_type,details) VALUES(?,?,?,?)",
                            arrayOf(selected,at+1+i,"USER_MARK","boundary-$i"))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            val exact=repo.libraryDetail(selected)
            assertEquals(SessionLibraryPolicy.MAX_EVENTS,exact.events.size)
            assertFalse(exact.eventLimited)
            repo.event(selected,"USER_MARK","overflow",at+9000)
            val truncated=repo.libraryDetail(selected)
            assertEquals(SessionLibraryPolicy.MAX_EVENTS,truncated.events.size)
            assertTrue(truncated.eventLimited)
            repo.finish(open,"INTERRUPTED",at+5000)
        }
    }
}
