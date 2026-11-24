package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class LiveNetworkPolicyTest {
    @Test fun hostValidation() {
        assertEquals("dns.google",LiveNetworkPolicy.host(" DNS.GOOGLE "))
        assertEquals("1.1.1.1",LiveNetworkPolicy.host("1.1.1.1"))
        listOf("https://dns.google","dns.google:443","a..b","-a.b","a.b-","256.1.1.1",
            "01.1.1.1","1.2.3","a/b.c","user@dns.google","").forEach { assertNull(it,LiveNetworkPolicy.host(it)) }
    }
    private fun p(ms:Double?,route:Long=1,state:String="OK",endpoint:String="1.1.1.1") =
        LiveNetworkPoint(0,"WIFI",route,endpoint,ms,state,null,"UNAVAILABLE")
    @Test fun jitterDoesNotBridgeFailuresOrSwitches() {
        val s=LiveNetworkPolicy.summarize(listOf(p(10.0),p(20.0),p(null,state="TCP_ERROR"),
            p(100.0),p(105.0,2),p(115.0,2),p(50.0,2,endpoint="1.0.0.1")))
        assertEquals(6,s.successful); assertEquals(1,s.failed)
        assertEquals(10.0,s.jitterMs!!,0.001); assertEquals(1,s.switches)
        assertEquals(100.0/7,s.failurePercent!!,0.001)
    }
    @Test fun noMeasurementsAndUnavailableAreNotZeroJitter() {
        assertNull(LiveNetworkPolicy.summarize(listOf(p(10.0))).jitterMs)
        val s=LiveNetworkPolicy.summarize(listOf(p(null,state="SWITCHED"),p(null,state="NO_NETWORK")))
        assertNull(s.failurePercent); assertEquals(2,s.unavailable)
        assertFalse(LiveNetworkPolicy.valid(Double.NaN))
    }
}
