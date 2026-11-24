package dev.lolokeksu.legioncontrol

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface

/** Explicit visible-only probes. TCP is bound to the captured Android Network. */
internal class LiveNetworkProbe(context: Context) {
    private val cm=requireNotNull(context.getSystemService(ConnectivityManager::class.java))
    fun capture(host: String,offsetMs: Long,mode:LiveIpMode=LiveIpMode.AUTO,isCancelled:()->Boolean={false}): LiveNetworkPoint {
        require(LiveNetworkPolicy.host(host)==host)
        fun checkCancel() { if(Thread.currentThread().isInterrupted || isCancelled()) throw InterruptedException() }
        fun elapsed(start:Long)=((SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0)
        checkCancel()
        val net=cm.activeNetwork ?: return LiveNetworkPoint(offsetMs,"NONE",null,null,null,"NO_NETWORK",null,"UNAVAILABLE")
        val caps=cm.getNetworkCapabilities(net)
        val transport=when {
            caps==null -> "UNKNOWN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
        var gatewayMs:Double?=null
        var gatewayState="UNAVAILABLE"
        var gatewayAddress:String?=null
        if(transport=="WIFI") {
            val link=cm.getLinkProperties(net)
            val gateway=link?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway
            gatewayAddress=gateway?.hostAddress
            val iface=link?.interfaceName?.let { runCatching { NetworkInterface.getByName(it) }.getOrNull() }
            if(gateway!=null && iface!=null) {
                checkCancel()
                val start=SystemClock.elapsedRealtimeNanos()
                // Platform reachability may use ICMP or TCP echo; no claim of ICMP RTT.
                val reachable=runCatching { gateway.isReachable(iface,0,900) }.getOrNull()
                gatewayState=when(reachable) { true -> "OK"; false -> "NO_REPLY"; null -> "UNAVAILABLE" }
                if(reachable==true) gatewayMs=elapsed(start)
            }
        }
        checkCancel()
        val resolveStart=SystemClock.elapsedRealtimeNanos()
        val addresses=runCatching { net.getAllByName(host) }.getOrNull()
        val dnsMs=if(host.matches(Regex("[0-9.]+"))) null else elapsed(resolveStart)
        checkCancel()
        val address=when(mode) {
            LiveIpMode.AUTO -> addresses?.firstOrNull { it is Inet4Address } ?: addresses?.firstOrNull()
            LiveIpMode.IPV4 -> addresses?.firstOrNull { it is Inet4Address }
            LiveIpMode.IPV6 -> addresses?.firstOrNull { it is Inet6Address }
        }
        val family=when(address) { is Inet4Address -> "IPV4"; is Inet6Address -> "IPV6"; else -> null }
        val validated=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        var tcpMs:Double?=null
        var tcpState=if(addresses.isNullOrEmpty()) "DNS_ERROR" else "NO_ADDRESS"
        if(address!=null) {
            val start=SystemClock.elapsedRealtimeNanos()
            tcpState="TCP_ERROR"
            try {
                net.socketFactory.createSocket().use { socket ->
                    checkCancel()
                    socket.connect(InetSocketAddress(address,443),1500)
                    tcpMs=elapsed(start)
                    tcpState="OK"
                }
            } catch (_:java.io.IOException) { /* bounded attempt; no retries */ }
        }
        checkCancel()
        if(cm.activeNetwork!=net) return LiveNetworkPoint(offsetMs,transport,net.networkHandle,null,null,"SWITCHED",null,"UNAVAILABLE")
        return LiveNetworkPoint(offsetMs,transport,net.networkHandle,address?.hostAddress,tcpMs,tcpState,gatewayMs,gatewayState,gatewayAddress,dnsMs=dnsMs,family=family,validated=validated)
    }
}
