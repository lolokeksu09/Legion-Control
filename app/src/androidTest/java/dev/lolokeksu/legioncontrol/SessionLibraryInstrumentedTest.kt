package dev.lolokeksu.legioncontrol

import android.os.Build
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
    @Test fun stablePagingFiltersAndSingleRecordExport() {
        val fingerprint=Build.FINGERPRINT.lowercase()
        assertTrue("CI emulator only",fingerprint.contains("generic") ||
            fingerprint.contains("emulator") || Build.PRODUCT.lowercase().contains("sdk"))
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
            repo.finish(open,"INTERRUPTED",at+5000)
        }
    }
}
