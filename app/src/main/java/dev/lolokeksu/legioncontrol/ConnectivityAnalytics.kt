package dev.lolokeksu.legioncontrol

import java.util.Locale
import kotlin.math.ceil

/** Source measurements. They are NOT ICMP ping, packet loss or another app's game latency. */
data class ConnectivityRun(
    val id:String, val host:String, val startedAtMs:Long,
    val endedAtMs:Long?, val mode:String, val status:String
)

data class ConnectivitySample(
    val cycle:Int,
    val timeMs:Long,
    val family:String,
    val dnsMs:Double?,
    val tcpMs:Double?,
    val tlsMs:Double?,
    val result:String,
    val errorCode:String = "",
    val networkType:String = "UNKNOWN",
    val networkValidated:Boolean? = null
)

data class ConnectivityDistribution(
    val count:Int, val median:Double?, val p95:Double?, val min:Double?, val max:Double?
)

data class ConnectivitySummary(
    val run:ConnectivityRun,
    val cycles:Int,
    val dnsFailures:Int,
    val dns:ConnectivityDistribution,
    val tcp:ConnectivityDistribution,
    val tls:ConnectivityDistribution,
    val eligibleConnections:Int,
    val tcpFailures:Int,
    val tlsFailures:Int,
    val tlsSuccesses:Int,
    val ipv4AvailableCycles:Int,
    val ipv6AvailableCycles:Int,
    val networkTypes:List<String>,
    val unvalidatedCount:Int,
    val unknownNetworkCount:Int
)

/** Pure deterministic statistics, runnable in JVM tests, no Android dependency. */
object ConnectivityAnalytics {
    fun distribution(values:List<Double>):ConnectivityDistribution {
        val v=values.filter { it.isFinite() && it in 0.0..120_000.0 }.sorted()
        if(v.isEmpty()) return ConnectivityDistribution(0,null,null,null,null)
        fun quantile(q:Double):Double {
            val pos=(q*(v.size-1)).coerceIn(0.0,(v.size-1).toDouble())
            val low=pos.toInt()
            val high=ceil(pos).toInt()
            return v[low]+(v[high]-v[low])*(pos-low)
        }
        return ConnectivityDistribution(v.size,quantile(0.5),quantile(0.95),v.first(),v.last())
    }

    fun summarize(run:ConnectivityRun,samples:List<ConnectivitySample>):ConnectivitySummary {
        val data=samples.filter { it.cycle in 1..6 && it.family in listOf("IPV4","IPV6") }
        val uniqueCycles=data.map { it.cycle }.distinct().sorted()
        // DNS is one resolver invocation per cycle; IPv4/IPv6 rows share that measured DNS time.
        val dnsCycles=data.groupBy { it.cycle }
        val dns=dnsCycles.values.mapNotNull { rows -> rows.firstNotNullOfOrNull { it.dnsMs } }
        val unavailable=setOf("NO_ADDRESS","DNS_ERROR")
        val eligible=data.count { it.result !in unavailable }
        return ConnectivitySummary(
            run=run,cycles=uniqueCycles.size,
            dnsFailures=dnsCycles.values.count { rows -> rows.all { it.result=="DNS_ERROR" } },
            dns=distribution(dns),tcp=distribution(data.mapNotNull { it.tcpMs }),
            tls=distribution(data.mapNotNull { it.tlsMs }),
            eligibleConnections=eligible,
            tcpFailures=data.count { it.result=="TCP_ERROR" },
            tlsFailures=data.count { it.result=="TLS_ERROR" },
            tlsSuccesses=data.count { it.result=="OK" },
            ipv4AvailableCycles=data.count { it.family=="IPV4" && it.result !in unavailable },
            ipv6AvailableCycles=data.count { it.family=="IPV6" && it.result !in unavailable },
            networkTypes=data.map { it.networkType }.distinct(),
            unvalidatedCount=data.count { it.networkValidated==false },
            unknownNetworkCount=data.count { it.networkValidated==null }
        )
    }

    private fun number(v:Double?):String =
        v?.let { String.format(Locale.US,"%.1f",it) } ?: "н/д"
    private fun metric(m:ConnectivityDistribution):String =
        if(m.count==0) "н/д (0)" else
            "median ${number(m.median)} мс · p95 ${number(m.p95)} мс · n=${m.count}"

    fun render(s:ConnectivitySummary):String = buildString {
        append("CONNECTIVITY OBSERVATORY · ").append(s.run.host).append(":443\n")
        append("Режим: ").append(s.run.mode).append(" · статус: ").append(s.run.status)
            .append(" · циклов: ").append(s.cycles).append('\n')
        append("DNS-резолвер: ").append(metric(s.dns))
            .append(" · сбоев DNS: ").append(s.dnsFailures).append('\n')
        append("TCP CONNECT: ").append(metric(s.tcp)).append('\n')
        append("TLS handshake (проверка сертификата + имени): ").append(metric(s.tls)).append('\n')
        append("TCP кандидатов: ").append(s.eligibleConnections)
            .append(" · неуспешных TCP: ").append(s.tcpFailures)
            .append(" · неуспешных TLS: ").append(s.tlsFailures)
            .append(" · HTTPS/TLS успешных: ").append(s.tlsSuccesses).append('\n')
        append("IPv4 адрес доступен для попытки: ").append(s.ipv4AvailableCycles)
            .append(" циклов · IPv6: ").append(s.ipv6AvailableCycles).append(" циклов\n")
        append("Android сеть: ").append(s.networkTypes.ifEmpty { listOf("UNKNOWN") }.joinToString())
            .append(" · не подтверждена: ").append(s.unvalidatedCount)
            .append(" · неизвестно: ").append(s.unknownNetworkCount).append('\n')
        if(s.run.status=="CANCELLED" || s.run.status=="INTERRUPTED")
            append("Частичная запись: тест отменён или процесс прерван.\n")
        if(s.cycles<3) append("Малая выборка: медиана и p95 малоинформативны.\n")
        if(s.ipv6AvailableCycles==0)
            append("В результатах не найден IPv6-кандидат; отсутствие адреса — не сбой TCP.\n")
        if(s.dnsFailures>0 || s.tcpFailures>0 || s.tlsFailures>0)
            append("Есть ошибки соединения с выбранным публичным узлом; это не равно потерям пакетов в игре.\n")
        append("Это длительность DNS/TCP/TLS, НЕ FPS, ping игрового сервера, ICMP или скорость передачи.")
    }

    fun compare(a:ConnectivitySummary,b:ConnectivitySummary):String {
        require(a.run.host==b.run.host) { "Compare the same public diagnostic host only" }
        return buildString {
            append("СРАВНЕНИЕ · ").append(a.run.host).append(":443\n")
            append("Ранний / поздний запуск\n")
            append("Типы сети: ").append(a.networkTypes.joinToString()).append(" / ")
                .append(b.networkTypes.joinToString()).append('\n')
            append("DNS median: ").append(number(a.dns.median)).append(" / ")
                .append(number(b.dns.median)).append(" мс\n")
            append("TCP median: ").append(number(a.tcp.median)).append(" / ")
                .append(number(b.tcp.median)).append(" мс\n")
            append("TLS median: ").append(number(a.tls.median)).append(" / ")
                .append(number(b.tls.median)).append(" мс\n")
            append("Успешные TLS: ").append(a.tlsSuccesses).append("/")
                .append(a.eligibleConnections).append(" / ")
                .append(b.tlsSuccesses).append("/").append(b.eligibleConnections).append('\n')
            if(a.cycles<3 || b.cycles<3)
                append("Мало повторений: статистическое сравнение ограничено.\n")
            if(a.run.status!="COMPLETE" || b.run.status!="COMPLETE")
                append("Есть незавершённые тесты: не считайте их равными по охвату.\n")
            append("Разные сети/провайдеры/кэши и CDN влияют на измерения. ")
            append("Нельзя выводить игровой ping или гарантировать улучшение.")
        }
    }
}
