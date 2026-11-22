package dev.lolokeksu.legioncontrol

import android.os.SystemClock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import java.util.Locale

/** TCP connect measurements, NOT ICMP ping and NOT Wild Rift server latency. */
data class TcpProbeResult(
    val host: String,
    val port: Int,
    val dnsMs: Double?,
    val roundTripsMs: List<Double>,
    val failures: Int,
    val attempts: Int,
    val dnsFailed: Boolean
)

data class TcpStatistics(
    val minimumMs: Double,
    val averageMs: Double,
    val maximumMs: Double,
    val jitterMs: Double
)

/** Pure computation: jitter is mean absolute consecutive TCP-connect difference. */
object TcpProbeStatistics {
    fun analyze(values: List<Double>): TcpStatistics? {
        if (values.isEmpty() || values.any { !it.isFinite() || it < 0.0 }) return null
        val variation = values.zipWithNext().map { (a,b) -> abs(a-b) }
        return TcpStatistics(
            minimumMs = values.min(),
            averageMs = values.average(),
            maximumMs = values.max(),
            jitterMs = if (variation.isEmpty()) 0.0 else variation.average()
        )
    }

    fun format(r: TcpProbeResult): String = buildString {
        append("NETWORK ANALYZER · TCP connect, НЕ игровой ping\n")
        append("Узел: ").append(r.host).append(":").append(r.port).append("\n")
        append("DNS: ").append(r.dnsMs?.let {
            String.format(Locale.US, "%.1f мс", it)
        } ?: "не удалось разрешить").append("\n")
        append("TCP: ").append(r.roundTripsMs.size).append("/")
            .append(r.attempts).append(" успешных · ошибки=")
            .append(r.failures).append("\n")
        TcpProbeStatistics.analyze(r.roundTripsMs)?.let {
            append(String.format(
                Locale.US, "Мин/сред/макс: %.1f / %.1f / %.1f мс\n",
                it.minimumMs, it.averageMs, it.maximumMs
            ))
            append(String.format(Locale.US, "Jitter TCP: %.1f мс\n", it.jitterMs))
        }
        append("Это TCP-установление соединения с публичным узлом, не ICMP и не задержка Wild Rift.")
    }
}

/** Call from a user-triggered worker thread only. No continuous network monitoring. */
object NetworkDiagnostics {
    private const val TARGET = "one.one.one.one"
    private const val PORT = 443
    private const val TIMEOUT_MS = 1800
    private const val ATTEMPTS = 5

    fun probe(): TcpProbeResult {
        val start = SystemClock.elapsedRealtimeNanos()
        val addresses = runCatching { InetAddress.getAllByName(TARGET) }.getOrNull()
        val dnsMs = if (addresses.isNullOrEmpty()) null
            else (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        if (addresses.isNullOrEmpty()) {
            return TcpProbeResult(TARGET, PORT, null, emptyList(), ATTEMPTS, ATTEMPTS, true)
        }
        val measurements = mutableListOf<Double>()
        var failed = 0
        for (i in 0 until ATTEMPTS) {
            if (Thread.currentThread().isInterrupted) break
            val begin = SystemClock.elapsedRealtimeNanos()
            // Attempt all resolved addresses to avoid assuming IPv6 is reachable.
            val success = addresses.any { addr ->
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(addr, PORT), TIMEOUT_MS)
                    }
                    true
                }.getOrDefault(false)
            }
            if (success) {
                measurements += (SystemClock.elapsedRealtimeNanos() - begin) / 1_000_000.0
            } else failed++
            if (i < ATTEMPTS-1) {
                try { Thread.sleep(120) } catch (_: InterruptedException) { break }
            }
        }
        return TcpProbeResult(
            TARGET, PORT, dnsMs, measurements, failed,
            measurements.size + failed, false
        )
    }
}
