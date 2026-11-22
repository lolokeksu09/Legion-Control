package dev.lolokeksu.legioncontrol

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** Entire probe is user-triggered and must run off the UI thread. */
object SmartNetworkLab {
    fun inspect(context: Context): String {
        val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val overview=runCatching {
            val network=cm?.activeNetwork
            val caps=network?.let { cm.getNetworkCapabilities(it) }
            val props=network?.let { cm.getLinkProperties(it) }
            "Сеть Android: "+when {
                caps==null -> "не определена"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобильная сеть"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "другая"
            }+"\nПроверена Android: "+(caps?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED)?.toString() ?: "неизвестно")+
              "\nСерверов DNS в текущей конфигурации: "+(props?.dnsServers?.size?.toString() ?: "неизвестно")
        }.getOrDefault("Android не позволил прочесть параметры текущей сети.")
        val records=runCatching { InetAddress.getAllByName("one.one.one.one") }.getOrNull()
        val ipv4=records?.count { it is Inet4Address }
        val ipv6=records?.count { it is Inet6Address }
        val probe=NetworkDiagnostics.probe()
        return overview+"\nDNS: IPv4="+(ipv4?.toString() ?: "н/д")+
            " / IPv6="+(ipv6?.toString() ?: "н/д")+"\n\n"+
            TcpProbeStatistics.format(probe)+
            "\nТест не измеряет скорость Wi-Fi, FPS, потери пакетов ICMP или игровой ping."
    }
}
