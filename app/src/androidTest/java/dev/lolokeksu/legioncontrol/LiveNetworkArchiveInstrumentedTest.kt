package dev.lolokeksu.legioncontrol

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LiveNetworkArchiveInstrumentedTest {
    @Test fun isolatedArchiveRoundTripAndExplicitDeletion() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="live-network-test-"+UUID.randomUUID()+".db"
        fun open()=LiveNetworkArchive(context,name)
        val r=LiveNetworkRecord(UUID.randomUUID().toString(),"dns.google",100L,LiveNetworkPlan.QUICK,
            LiveNetworkEnd.CANCELLED,listOf(LiveNetworkPoint(0L,"WIFI",1234,"8.8.8.8",12.0,"OK",4.0,"OK","192.168.1.1")))
        try {
            open().let{db->try{ db.save(r);db.save(r);assertEquals(1,db.list().size) }finally{db.close()}}
            open().let{db->try{
                assertEquals(r.redacted(),db.list().single())
                val other=r.copy(id=UUID.randomUUID().toString(),startedAt=200L)
                db.save(other)
                assertEquals(other.id,db.list().first().id)
                assertTrue(db.delete(r.id));assertFalse(db.delete(r.id))
                assertEquals(other.id,db.list().single().id)
            }finally{db.close()}}
        } finally { context.deleteDatabase(name) }
    }
}
