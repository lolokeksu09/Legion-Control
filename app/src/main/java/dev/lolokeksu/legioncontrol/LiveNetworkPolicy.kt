package dev.lolokeksu.legioncontrol

import java.util.Locale
import kotlin.math.abs

internal data class LiveNetworkPoint(
    val offsetMs: Long,
    val network: String,
    val route: Long?,
    val endpoint: String?,
    val tcpMs: Double?,
    val tcpState: String,
    val gatewayMs: Double?,
    val gatewayState: String,
    val gatewayAddress: String? = null
)

internal data class LiveNetworkSummary(
    val successful: Int, val failed: Int, val unavailable: Int,
    val jitterMs: Double?, val switches: Int, val failurePercent: Double?
)

internal object LiveNetworkPolicy {
    // An explicit DNS name or IPv4 literal only; no URL, port, credentials or shell.
    fun host(raw: String): String? {
        val value=raw.trim().lowercase(Locale.ROOT)
        if(value.length !in 3..253) return null
        val labels=value.split('.')
        if(labels.size<2 || labels.any { it.length !in 1..63 ||
                !it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) }) return null
        if(labels.all { it.all(Char::isDigit) } &&
            (labels.size!=4 || labels.any { it.toIntOrNull()?.let { n -> n !in 0..255 } != false ||
                (it.length>1 && it.startsWith('0')) })) return null
        return value
    }

    fun valid(value: Double?): Boolean = value != null && value.isFinite() && value in 0.0..120_000.0
    fun connected(a: LiveNetworkPoint,b: LiveNetworkPoint): Boolean =
        a.route!=null && a.route==b.route && a.network==b.network && a.endpoint!=null && a.endpoint==b.endpoint

    fun summarize(points: List<LiveNetworkPoint>): LiveNetworkSummary {
        val good=points.count { it.tcpState=="OK" && valid(it.tcpMs) }
        val bad=points.count { it.tcpState in setOf("DNS_ERROR","TCP_ERROR") }
        val variation=points.zipWithNext().mapNotNull { (a,b) ->
            if(connected(a,b) && a.tcpState=="OK" && b.tcpState=="OK" &&
                valid(a.tcpMs) && valid(b.tcpMs)) abs(a.tcpMs!!-b.tcpMs!!) else null
        }
        val switches=points.zipWithNext().count { (a,b) -> a.route!=b.route || a.network!=b.network }
        return LiveNetworkSummary(good,bad,points.size-good-bad,variation.takeIf { it.isNotEmpty() }?.average(),
            switches,if(good+bad==0) null else 100.0*bad/(good+bad))
    }
}
