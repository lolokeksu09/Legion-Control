package dev.lolokeksu.legioncontrol

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import android.content.ContentValues

@RunWith(AndroidJUnit4::class)
class LiveNetworkArchiveInstrumentedTest {
    @Test fun isolatedArchiveRoundTripAndExplicitDeletion() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="live-network-test-"+UUID.randomUUID()+".db"
        fun open()=LiveNetworkArchive(context,name)
        val r=LiveNetworkRecord(UUID.randomUUID().toString(),"dns.google",100L,LiveNetworkPlan.QUICK,
            LiveNetworkEnd.CANCELLED,listOf(LiveNetworkPoint(0L,"WIFI",1234,"8.8.8.8",12.0,"OK",4.0,"OK","192.168.1.1",dnsMs=6.0,family="IPV4",validated=true)),LiveIpMode.IPV4)
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
    @Test fun legacyPayloadAndArchiveCapacityPreserveExistingRecords() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="live-network-legacy-"+UUID.randomUUID()+".db"
        val id=UUID.randomUUID().toString()
        val archive=LiveNetworkArchive(context,name)
        try {
            val legacy=org.json.JSONObject().apply {
                put("v",1);put("id",id);put("host","dns.google");put("started",10L)
                put("plan","QUICK");put("end","CANCELLED")
                put("points",org.json.JSONArray().put(org.json.JSONArray()
                    .put(0L).put("WIFI").put(1L).put("E1").put(10.0).put("OK")
                    .put(3.0).put("OK").put("G1")))
            }
            archive.writableDatabase.insertOrThrow("live_runs",null,ContentValues().apply{
                put("id",id);put("started",10L);put("payload",legacy.toString())
            })
            val old=archive.list().single()
            assertEquals(LiveIpMode.AUTO,old.ipMode);assertNull(old.points.single().dnsMs)
            repeat(LiveNetworkArchive.LIMIT-1){ i->archive.save(old.copy(id=UUID.randomUUID().toString(),startedAt=20L+i))}
            assertEquals(50,archive.list().size)
            val extra=old.copy(id=UUID.randomUUID().toString(),startedAt=1000L)
            assertTrue(runCatching{archive.save(extra)}.isFailure)
            assertEquals(50,archive.list().size)
            assertTrue(archive.list().any{it.id==id})
            assertTrue(archive.delete(id))
            archive.save(extra)
            assertEquals(50,archive.list().size)
        } finally { archive.close();context.deleteDatabase(name) }
    }
}
