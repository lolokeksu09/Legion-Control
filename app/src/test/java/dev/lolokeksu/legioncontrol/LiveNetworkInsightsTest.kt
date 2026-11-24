package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LiveNetworkInsightsTest {
    private fun p(state:String="OK",gw:String="OK",family:String?="IPV4",ms:Double?=20.0)=
        LiveNetworkPoint(0L,"WIFI",1L,"E1",ms,state,3.0,gw,dnsMs=5.0,family=family,validated=false)
    @Test fun unavailableFamilyIsNotAFailureAndObservationsArePaired() {
        val points=listOf(p("NO_ADDRESS",family=null,ms=null),p("TCP_ERROR",ms=null),p(gw="NO_REPLY"))
        val e=LiveNetworkInsights.evidence(points)
        assertEquals(1,e.noAddress);assertEquals(1,e.gatewayOkExternalError)
        assertEquals(1,e.gatewayNoReplyExternalOk);assertEquals(3,e.unvalidated)
        val s=LiveNetworkPolicy.summarize(points)
        assertEquals(1,s.failed);assertEquals(1,s.unavailable);assertEquals(50.0,s.failurePercent!!,0.001)
    }
    @Test fun comparisonDoesNotInventSmallSampleDeltas() {
        val a=listOf(p(ms=10.0));val b=listOf(p(ms=40.0))
        assertTrue(LiveNetworkInsights.compare(a,b).contains("TCP median: н/д"))
        assertTrue(LiveNetworkInsights.compare(List(3){a[0]},List(3){b[0]}).contains("TCP median: 30.0 мс"))
    }
    @Test fun explicitFamiliesCanBeComparedButAutoIsNotEquivalent() {
        fun r(mode:LiveIpMode)=LiveNetworkRecord(UUID.randomUUID().toString(),"dns.google",1L,
            LiveNetworkPlan.QUICK,LiveNetworkEnd.COMPLETE,List(10){p()},mode)
        assertTrue(LiveNetworkReports.comparable(r(LiveIpMode.IPV4),r(LiveIpMode.IPV6)))
        assertFalse(LiveNetworkReports.comparable(r(LiveIpMode.AUTO),r(LiveIpMode.IPV6)))
    }
}
