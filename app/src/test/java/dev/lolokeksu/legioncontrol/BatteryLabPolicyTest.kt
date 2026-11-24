package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BatteryLabPolicyTest {
    private fun p(t:Long,i:Int?=1_000_000,plug:BatteryLabPlug=BatteryLabPlug.AC,status:BatteryLabStatus=BatteryLabStatus.CHARGING)=
        BatteryLabPoint(t,50,320,i,4000,2_000_000,null,null,status,plug,0)
    @Test fun signedPowerAndEnergyRespectUnits() {
        assertEquals(4.0,p(0).watts!!,1e-9)
        val s=BatteryLabPolicy.statistics(listOf(p(0),p(5000),p(10000)))
        assertEquals(4.0*10/3600,s.receivedWh!!,1e-9);assertEquals(10000L,s.coveredMs)
        assertEquals(4.0,s.averageWatts!!,1e-9)
        val d=BatteryLabPolicy.statistics(listOf(p(0,-1_000_000,BatteryLabPlug.NONE,BatteryLabStatus.DISCHARGING),p(5000,-1_000_000,BatteryLabPlug.NONE,BatteryLabStatus.DISCHARGING)))
        assertEquals(4.0*5/3600,d.spentWh!!,1e-9);assertEquals(-4.0,d.averageWatts!!,1e-9)
    }
    @Test fun missingContradictoryAndGappedSamplesDoNotInventEnergy() {
        assertNull(p(0,-100).watts);assertNull(p(0,100,BatteryLabPlug.NONE).watts)
        val s=BatteryLabPolicy.statistics(listOf(p(0),p(5000,null),p(10000),p(30000),p(35000,plug=BatteryLabPlug.USB)))
        assertNull(s.receivedWh);assertEquals(0L,s.coveredMs);assertEquals(4,s.excludedIntervals)
        assertTrue(BatteryLabPolicy.hot(p(0).copy(tempDeciC=450)))
        assertTrue(BatteryLabPolicy.hot(p(0).copy(thermal=3)))
    }
    @Test fun comparisonRequiresCompleteMatchedConditionsAndCoverage() {
        fun run(points:List<BatteryLabPoint>,end:BatteryLabEnd=BatteryLabEnd.COMPLETE)=BatteryLabRun(UUID.randomUUID().toString(),1L,BatteryLabPlan.QUICK,end,points)
        val points=(0..24).map{p(it*5000L)}
        val a=run(points);assertTrue(BatteryLabPolicy.comparable(a,run(points)))
        assertFalse(BatteryLabPolicy.comparable(a,run(points.map{it.copy(percent=60)})))
        assertFalse(BatteryLabPolicy.comparable(a,run(points.take(2))))
        assertFalse(BatteryLabPolicy.comparable(a,run(points,BatteryLabEnd.USER_STOP)))
        assertFalse(BatteryLabPolicy.comparable(a,run(points.map{it.copy(currentMicroA=null)})))
    }
}
