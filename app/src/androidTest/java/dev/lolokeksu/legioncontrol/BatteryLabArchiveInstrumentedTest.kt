package dev.lolokeksu.legioncontrol

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BatteryLabArchiveInstrumentedTest {
    @Test fun archiveRoundTripCapacityAndDeletion() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="battery-lab-test-"+UUID.randomUUID()+".db"
        fun open()=BatteryLabArchive(context,name)
        val p=BatteryLabPoint(0L,50,320,1_000_000,4000,2_000_000,null,null,
            BatteryLabStatus.CHARGING,BatteryLabPlug.AC,0)
        val run=BatteryLabRun(UUID.randomUUID().toString(),100L,BatteryLabPlan.QUICK,
            BatteryLabEnd.USER_STOP,listOf(p,p.copy(elapsedMs=5000)))
        try {
            open().let{db->try{db.save(run);db.save(run);assertEquals(1,db.list().size)}finally{db.close()}}
            open().let{db->try{
                assertEquals(run,db.list().single())
                repeat(BatteryLabArchive.LIMIT-1){i->db.save(run.copy(id=UUID.randomUUID().toString(),startedAt=101L+i))}
                val extra=run.copy(id=UUID.randomUUID().toString(),startedAt=1000L)
                assertTrue(runCatching{db.save(extra)}.isFailure)
                assertEquals(30,db.list().size);assertTrue(db.list().any{it.id==run.id})
                assertTrue(db.delete(run.id));assertFalse(db.delete(run.id));db.save(extra)
                assertEquals(extra,db.list().first())
            }finally{db.close()}}
        }finally{context.deleteDatabase(name)}
    }
    @Test fun publicBatterySourceAndPanelCanReadWithoutChangingSettings() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val reading=BatteryLabSource.read(context,0L)
        assertNotNull(reading)
        assertTrue(requireNotNull(reading).percent?.let{it in 0..100} ?: true)
    }
}
