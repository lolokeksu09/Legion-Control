package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LiveNetworkReportsTest {
    private fun record(host:String="dns.google",end:LiveNetworkEnd=LiveNetworkEnd.COMPLETE,plan:LiveNetworkPlan=LiveNetworkPlan.QUICK):LiveNetworkRecord {
        val n=if(end==LiveNetworkEnd.COMPLETE) plan.count else 2
        return LiveNetworkRecord(UUID.randomUUID().toString(),host,123L,plan,end,
            (0 until n).map{LiveNetworkPoint(it*1000L,"WIFI",773,"8.8.8.8",10.0+it,"OK",3.0,"OK","192.168.1.1")})
    }
    @Test fun redactionPreservesAdjacencyAndOmitsRawAddresses() {
        val r=record()
        val safe=r.redacted()
        assertEquals(LiveNetworkPolicy.summarize(r.points),LiveNetworkPolicy.summarize(safe.points))
        assertNull(safe.points.first().gatewayAddress)
        assertEquals("G1",safe.points.first().gatewayKey)
        assertEquals("E1",safe.points.first().endpoint)
        assertEquals(safe,safe.redacted())
        val csv=LiveNetworkReports.csv(r)
        assertFalse(csv.contains("192.168.1.1")); assertFalse(csv.contains("8.8.8.8"))
        assertEquals(11,csv.trim().lines().size)
    }
    @Test fun comparisonsRequireDistinctCompletedComparableTests() {
        val a=record()
        assertTrue(LiveNetworkReports.comparable(a,record()))
        assertFalse(LiveNetworkReports.comparable(a,a))
        assertFalse(LiveNetworkReports.comparable(a,record("dns.quad9.net")))
        assertFalse(LiveNetworkReports.comparable(a,record(end=LiveNetworkEnd.CANCELLED)))
        assertFalse(LiveNetworkReports.comparable(a,record(plan=LiveNetworkPlan.NORMAL)))
    }
}
