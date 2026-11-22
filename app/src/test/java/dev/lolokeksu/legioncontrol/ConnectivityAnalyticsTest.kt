package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class ConnectivityAnalyticsTest {
    private val run=ConnectivityRun(
        id="run-1",host="dns.google",startedAtMs=1000L,endedAtMs=61000L,
        mode="STABILITY",status="COMPLETE"
    )
    private fun reading(cycle:Int,family:String,result:String,
                        dns:Double?=10.0,tcp:Double?=30.0,tls:Double?=40.0,
                        type:String="WIFI",validated:Boolean?=true)=
        ConnectivitySample(cycle,cycle*1000L,family,dns,tcp,tls,result,"",type,validated)

    @Test fun percentileMedianAndP95AreDeterministic() {
        val d=ConnectivityAnalytics.distribution(listOf(10.0,50.0,30.0,20.0,40.0))
        assertEquals(5,d.count)
        assertEquals(30.0,d.median!!,0.0001)
        assertEquals(48.0,d.p95!!,0.0001)
        assertEquals(10.0,d.min!!,0.0001)
        assertEquals(50.0,d.max!!,0.0001)
    }

    @Test fun nullOrInvalidReadingsStayUnavailableNotZero() {
        val d=ConnectivityAnalytics.distribution(listOf(Double.NaN,-3.0,Double.POSITIVE_INFINITY))
        assertEquals(0,d.count)
        assertNull(d.median)
        assertNull(d.p95)
        assertTrue(ConnectivityAnalytics.render(ConnectivityAnalytics.summarize(run,emptyList()))
            .contains("н/д"))
    }

    @Test fun dnsIsOneMeasurementPerCycleNotTwoFamilyCopies() {
        val data=listOf(
            reading(1,"IPV4","OK",12.0,20.0,30.0),
            reading(1,"IPV6","OK",12.0,40.0,50.0),
            reading(2,"IPV4","OK",17.0,21.0,31.0),
            reading(2,"IPV6","NO_ADDRESS",17.0,null,null)
        )
        val summary=ConnectivityAnalytics.summarize(run,data)
        assertEquals(2,summary.cycles)
        assertEquals(2,summary.dns.count)
        assertEquals(3,summary.tcp.count)
        assertEquals(3,summary.tlsSuccesses)
        assertEquals(2,summary.ipv4AvailableCycles)
        assertEquals(1,summary.ipv6AvailableCycles)
        assertEquals(0,summary.tcpFailures)
    }

    @Test fun unavailableIpv6IsNotTcpFailure() {
        val data=listOf(
            reading(1,"IPV4","OK",dns=15.0,tcp=25.0,tls=55.0),
            reading(1,"IPV6","NO_ADDRESS",dns=15.0,tcp=null,tls=null)
        )
        val s=ConnectivityAnalytics.summarize(run,data)
        assertEquals(1,s.eligibleConnections)
        assertEquals(0,s.tcpFailures)
        assertEquals(0,s.tlsFailures)
        assertEquals(1,s.tlsSuccesses)
        assertTrue(ConnectivityAnalytics.render(s).contains("не найден IPv6"))
    }

    @Test fun dnsFailureCannotBeCountedAsTwoFailures() {
        val data=listOf(
            reading(1,"IPV4","DNS_ERROR",dns=null,tcp=null,tls=null),
            reading(1,"IPV6","DNS_ERROR",dns=null,tcp=null,tls=null)
        )
        val s=ConnectivityAnalytics.summarize(run,data)
        assertEquals(1,s.dnsFailures)
        assertEquals(0,s.dns.count)
        assertEquals(0,s.eligibleConnections)
        assertEquals(0,s.tcpFailures)
        assertEquals(0,s.tlsFailures)
    }

    @Test fun failedTcpVsFailedTlsAreDistinctAndNoLossClaims() {
        val data=listOf(
            reading(1,"IPV4","TCP_ERROR",dns=9.0,tcp=null,tls=null),
            reading(1,"IPV6","TLS_ERROR",dns=9.0,tcp=30.0,tls=null),
            reading(2,"IPV4","OK",dns=11.0,tcp=20.0,tls=25.0),
            reading(2,"IPV6","NO_ADDRESS",dns=11.0,tcp=null,tls=null)
        )
        val s=ConnectivityAnalytics.summarize(run,data)
        assertEquals(1,s.tcpFailures)
        assertEquals(1,s.tlsFailures)
        assertEquals(1,s.tlsSuccesses)
        assertEquals(3,s.eligibleConnections)
        val report=ConnectivityAnalytics.render(s)
        assertTrue(report.contains("не равно потерям пакетов"))
        assertTrue(report.contains("НЕ ping игров"))
    }

    @Test fun incompleteRunIsLabelledAndComparisonDoesNotInventMetrics() {
        val cancelled=run.copy(id="r2",status="CANCELLED",endedAtMs=2000)
        val s=ConnectivityAnalytics.summarize(cancelled,emptyList())
        assertTrue(ConnectivityAnalytics.render(s).contains("Частичная запись"))
        val previous=ConnectivityAnalytics.summarize(run,emptyList())
        val compare=ConnectivityAnalytics.compare(previous,s)
        assertTrue(compare.contains("н/д"))
        assertTrue(compare.contains("незавершённые"))
    }

    @Test fun compareDifferentPublicHostsMustBeRejected() {
        val a=ConnectivityAnalytics.summarize(run,emptyList())
        val b=ConnectivityAnalytics.summarize(run.copy(host="one.one.one.one"),emptyList())
        assertThrows(IllegalArgumentException::class.java) { ConnectivityAnalytics.compare(a,b) }
    }

    @Test fun networkUnknownAndUnvalidatedAreDifferent() {
        val data=listOf(
            reading(1,"IPV4","OK",validated=null,type="UNKNOWN"),
            reading(1,"IPV6","OK",validated=false,type="VPN")
        )
        val s=ConnectivityAnalytics.summarize(run,data)
        assertEquals(1,s.unvalidatedCount)
        assertEquals(1,s.unknownNetworkCount)
        assertEquals(listOf("UNKNOWN","VPN"),s.networkTypes)
    }
}
