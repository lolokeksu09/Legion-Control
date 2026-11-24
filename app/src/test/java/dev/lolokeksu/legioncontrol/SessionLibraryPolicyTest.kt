package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream

class SessionLibraryPolicyTest {
    private fun header(id:String,at:Long=1000)=GameSessionHeader(id,"com.example.game","Game",at,at+60000,"USER_STOP",false)
    @Test fun queryBindsPackagePeriodAndStableCursor() {
        val q=SessionLibraryPolicy.query(LibraryFilter("com.example.game",LibraryPeriod.WEEK,LibraryStatus.FINISHED),
            1000000000L,LibraryCursor(999,"id"))
        assertTrue(q.where.contains("package_name=?"))
        assertTrue(q.where.contains("session_id<?"))
        assertEquals(listOf("com.example.game","395200000","1000000000","999","999","id"),q.args)
        assertTrue(runCatching { SessionLibraryPolicy.query(LibraryFilter("x' OR 1=1"),1000,null) }.isFailure)
    }
    @Test fun pagesOnlyExposeNextCursorWhenExtraRowExists() {
        val rows=(30 downTo 10).map { header(it.toString()) }
        val page=SessionLibraryPolicy.page(rows)
        assertEquals(20,page.rows.size)
        assertEquals(LibraryCursor(1000,"11"),page.next)
        assertNull(SessionLibraryPolicy.page(rows.take(20)).next)
        assertNull(SessionLibraryPolicy.page(emptyList()).next)
    }
    @Test fun exportContainsOnlySelectedSessionAndEscapesText() {
        val h=header("selected")
        val detail=LibraryDetail(h,listOf(GameTelemetryPoint(1000,80,null,-1000,0,false,120f,"WIFI",true)),
            listOf(GameSessionEvent(1010,"USER_MARK","=FORMULA()")),false,true)
        val out=ByteArrayOutputStream()
        SessionLibraryExport.write(detail,out)
        val entries=linkedMapOf<String,String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while(true) {
                val entry=zip.nextEntry ?: break
                entries[entry.name]=zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(setOf("session.csv","samples.csv","events.csv","README.txt"),entries.keys)
        assertTrue(entries.getValue("samples.csv").contains("selected"))
        assertTrue(entries.getValue("events.csv").contains("'=FORMULA()"))
        assertTrue(entries.getValue("README.txt").contains("event limit reached: true"))
        assertFalse(entries.getValue("samples.csv").contains("other-session"))
    }
}
