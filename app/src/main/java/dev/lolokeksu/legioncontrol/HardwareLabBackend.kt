package dev.lolokeksu.legioncontrol

import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.os.SystemClock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/** Explicit, local-only, bounded, no additional Android permission. */
object SandboxIoBenchmark {
    private const val BYTES = 8 * 1024 * 1024
    private const val CHUNK = 64 * 1024

    fun run(context: Context): SandboxIoResult {
        val file = File.createTempFile("legion-io-", ".tmp", context.cacheDir)
        // Deterministic bytes make verification independent of memory caching.
        val block = ByteArray(CHUNK) { ((it * 31 + 17) and 0xff).toByte() }
        try {
            val expected = MessageDigest.getInstance("SHA-256")
            val startWrite = SystemClock.elapsedRealtimeNanos()
            FileOutputStream(file).use { stream ->
                repeat(BYTES / CHUNK) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Benchmark canceled")
                    stream.write(block)
                    expected.update(block)
                }
                // Timed write includes flush to the underlying filesystem.
                stream.fd.sync()
            }
            val writtenNs = SystemClock.elapsedRealtimeNanos() - startWrite
            val actual = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(CHUNK)
            var total = 0L
            val startRead = SystemClock.elapsedRealtimeNanos()
            FileInputStream(file).use { stream ->
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Benchmark canceled")
                    val count = stream.read(buffer)
                    if (count == -1) break
                    if (count > 0) {
                        total += count
                        actual.update(buffer, 0, count)
                    }
                }
            }
            val readNs = SystemClock.elapsedRealtimeNanos() - startRead
            return SandboxIoResult(
                bytes = total,
                writeElapsedNs = writtenNs,
                readElapsedNs = readNs,
                verified = total == BYTES.toLong() &&
                    MessageDigest.isEqual(expected.digest(), actual.digest())
            )
        } finally {
            file.delete()
        }
    }
}

object LocalRouteDiagnostics {
    /** Android only exposes properties of the currently active network here. */
    fun inspect(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "LOCAL NETWORK · ConnectivityManager недоступен"
        val network = cm.activeNetwork ?: return "LOCAL NETWORK · активной сети нет"
        val link = cm.getLinkProperties(network)
            ?: return "LOCAL NETWORK · LinkProperties скрыты прошивкой"
        return buildString {
            append("LOCAL NETWORK · параметры маршрута Android\n")
            append("Интерфейс: ").append(link.interfaceName ?: "неизвестен").append('\n')
            append("Адреса интерфейса: ")
                .append(link.linkAddresses.take(8).joinToString(", ") {
                    it.address.hostAddress ?: "неизвестен"
                }.ifEmpty { "нет данных" }).append('\n')
            append("DNS: ").append(link.dnsServers.take(8).joinToString(", ") {
                it.hostAddress ?: "неизвестен"
            }.ifEmpty { "нет данных" }).append('\n')
            append("Шлюзы: ").append(link.routes.filter { it.isDefaultRoute }
                .take(4).joinToString(", ") {
                    it.gateway?.hostAddress ?: "нет адреса шлюза"
                }.ifEmpty { "нет маршрута по умолчанию" }).append('\n')
            append("Частный DNS: ").append(if (link.isPrivateDnsActive) "активен" else "не активен")
            append('\n')
            append("Данные сети локальные и могут относиться к VPN, не обязательно к роутеру.")
        }
    }
}

object CodecCapabilityDiagnostics {
    fun inspect(): String {
        val all = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val targets = listOf("video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01")
        return buildString {
            append("CODEC INSPECTOR · декодеры Android MediaCodecList\n")
            for (mime in targets) {
                val supported = all.filter { !it.isEncoder &&
                    it.supportedTypes.any { type -> type.equals(mime, true) }
                }
                val hw = supported.filter { it.isHardwareAccelerated }
                append(mime).append(": ").append(supported.size)
                    .append(" декодеров · аппаратных: ").append(hw.size).append('\n')
                if (hw.isNotEmpty()) {
                    append("  ").append(hw.take(2).joinToString { it.name.take(70) })
                        .append('\n')
                }
            }
            append("Наличие декодера не подтверждает воспроизведение, fps, HDR или производительность игры.")
        }
    }
}
