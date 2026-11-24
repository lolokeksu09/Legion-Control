package dev.lolokeksu.legioncontrol

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ResourceOptimizationInstrumentedTest {
    @Test fun indexedLookupMatchesExistingLibraryAndCountMatchesProfiles() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val pkg="dev.legion.optimization.p"+UUID.randomUUID().toString().replace("-","")
        ApplicationCenterRepository(app).use { repo ->
            try {
                assertNull(repo.get(pkg))
                repo.save(pkg,"Test alias","MOBA","Test note",true)
                val expected=repo.list().single { it.packageName==pkg }
                assertEquals(expected,repo.get(pkg))
                assertEquals("Test note",repo.get(pkg)?.note)
            } finally { repo.remove(pkg) }
            assertNull(repo.get(pkg))
        }
        SmartProfilesRepository(app).use { repo -> assertEquals(repo.list().size,repo.count()) }
    }
    @Test fun hubSignalsAndLauncherResultsRemainBounded() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val (percent,hz)=GameSessionTelemetry.hubSignals(app)
        assertTrue(percent==null || percent in 0..100)
        assertTrue(hz==null || hz in 1f..500f)
        val launchable=ApplicationInventory.launchable(app)
        assertTrue(launchable.size<=600)
        assertEquals(launchable.size,launchable.map{it.packageName}.distinct().size)
        assertFalse(launchable.any{it.packageName==app.packageName})
    }
}
