package dev.lolokeksu.legioncontrol

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Explicit user-triggered public Internet diagnostics, not traffic interception.
 * One DNS lookup per cycle, at most one TCP/TLS connection per available IP family.
 * No insecure trust managers, local scanning, arbitrary host or port.
 */
object ConnectivityProbe {
    val targets: List<String> = listOf("one.one.one.one","dns.google","dns.quad9.net")
    private const val PORT = 443
    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val TLS_TIMEOUT_MS = 2_200

    fun validHost(host: String): Boolean = host in targets

    private fun elapsed(start:Long):Double =
        ((SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0).coerceIn(0.0,120_000.0)

    private fun metadata(context:Context):Pair<String,Boolean?> {
        return runCatching {
            val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net=cm?.activeNetwork
            if(net==null) return@runCatching ("NONE" to false)
            val caps=cm.getNetworkCapabilities(net)
            if(caps==null) "UNKNOWN" to null
            else {
                val type=when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                    else -> "OTHER"
                }
                type to caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }.getOrDefault("UNKNOWN" to null)
    }

    fun capture(context:Context,host:String,cycle:Int):List<ConnectivitySample> {
        require(validHost(host))
        require(cycle in 1..6)
        if(Thread.currentThread().isInterrupted) throw InterruptedException()
        val (networkType,validated)=metadata(context)
        val dnsStart=SystemClock.elapsedRealtimeNanos()
        val result=runCatching { InetAddress.getAllByName(host).toList() }
        val dnsTime=elapsed(dnsStart)
        val now=System.currentTimeMillis().coerceAtLeast(1)
        val addresses=result.getOrNull().orEmpty()
        if(result.isFailure || addresses.isEmpty()) {
            val error=result.exceptionOrNull()?.javaClass?.simpleName?.take(48) ?: "EmptyDnsResult"
            return listOf("IPV4","IPV6").map { family ->
                ConnectivitySample(cycle,now,family,dnsTime,null,null,"DNS_ERROR",error,
                    networkType,validated)
            }
        }
        return listOf("IPV4","IPV6").map { family ->
            if(Thread.currentThread().isInterrupted) throw InterruptedException()
            val address=addresses.firstOrNull { a ->
                if(family=="IPV4") a is Inet4Address else a is Inet6Address
            }
            if(address==null) {
                ConnectivitySample(cycle,System.currentTimeMillis().coerceAtLeast(1),
                    family,dnsTime,null,null,"NO_ADDRESS","",networkType,validated)
            } else attempt(host,address,cycle,family,dnsTime,networkType,validated)
        }
    }

    private fun attempt(host:String,address:InetAddress,cycle:Int,family:String,
                        dnsTime:Double,transport:String,validated:Boolean?):ConnectivitySample {
        val now=System.currentTimeMillis().coerceAtLeast(1)
        var tcpMs:Double?=null
        var tlsMs:Double?=null
        var status="TCP_ERROR"
        var errorCode=""
        Socket().use { plain ->
            val tcpStart=SystemClock.elapsedRealtimeNanos()
            try {
                plain.connect(InetSocketAddress(address,PORT),CONNECT_TIMEOUT_MS)
                tcpMs=elapsed(tcpStart)
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                val tlsStart=SystemClock.elapsedRealtimeNanos()
                status="TLS_ERROR"
                // SSL factory uses Android platform system trust and checks the real hostname.
                val ssl=SSLSocketFactory.getDefault() as SSLSocketFactory
                val secure=ssl.createSocket(plain,host,PORT,true) as SSLSocket
                secure.use { tls ->
                    tls.soTimeout=TLS_TIMEOUT_MS
                    val parameters=tls.sslParameters
                    parameters.endpointIdentificationAlgorithm="HTTPS"
                    parameters.serverNames=listOf(SNIHostName(host))
                    tls.sslParameters=parameters
                    tls.startHandshake()
                    tlsMs=elapsed(tlsStart)
                    status="OK"
                }
            } catch (interrupt: InterruptedException) {
                throw interrupt
            } catch (error: Exception) {
                errorCode=error.javaClass.simpleName.take(48)
            }
        }
        return ConnectivitySample(cycle,now,family,dnsTime,tcpMs,tlsMs,status,
            errorCode,transport,validated)
    }
}
