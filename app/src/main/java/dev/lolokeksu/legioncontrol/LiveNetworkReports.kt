package dev.lolokeksu.legioncontrol

import java.util.Locale
import java.util.UUID

internal enum class LiveNetworkPlan(val count:Int,val intervalMs:Long,val label:String) {
    QUICK(10,1000,"Быстрый · 10 проб"),
    NORMAL(20,1000,"Стандарт · 20 проб"),
    LONG(40,3000,"Длительный · 40 проб")
}
internal enum class LiveNetworkEnd { COMPLETE, CANCELLED, FAILED }

internal data class LiveNetworkRecord(
    val id:String,
    val host:String,
    val startedAt:Long,
    val plan:LiveNetworkPlan,
    val end:LiveNetworkEnd,
    val points:List<LiveNetworkPoint>
) {
    init {
        require(UUID.fromString(id).toString()==id)
        require(LiveNetworkPolicy.host(host)==host && startedAt>0)
        require(points.isNotEmpty() && points.size<=plan.count)
        require(end!=LiveNetworkEnd.COMPLETE || points.size==plan.count)
        require(points.all { it.offsetMs>=0 && it.network in setOf("NONE","UNKNOWN","VPN","WIFI","CELLULAR","ETHERNET","OTHER") })
        require(points.zipWithNext().all { (a,b)->a.offsetMs<=b.offsetMs })
    }
    // Route handles and actual resolved/gateway IPs must not survive storage/export.
    // Only run-local equality is preserved, including changes within a run.
    fun redacted():LiveNetworkRecord {
        val routes=mutableMapOf<Long,Long>()
        val endpoints=mutableMapOf<String,String>()
        val gateways=mutableMapOf<String,String>()
        return copy(points=points.map { p->
            p.copy(route=p.route?.let { routes.getOrPut(it){routes.size.toLong()+1} },
                endpoint=p.endpoint?.let { endpoints.getOrPut(it){"E"+(endpoints.size+1)} },
                gatewayAddress=null,
                gatewayKey=p.gatewayKey?.let { gateways.getOrPut(it){"G"+(gateways.size+1)} })
        })
    }
}
internal object LiveNetworkReports {
    fun number(v:Double?):String=v?.let { String.format(Locale.US,"%.1f",it) } ?: "н/д"
    fun report(r:LiveNetworkRecord):String {
        val s=LiveNetworkPolicy.summarize(r.points)
        val tcp=ConnectivityAnalytics.distribution(r.points.filter{it.tcpState=="OK"}.mapNotNull{it.tcpMs})
        val gw=ConnectivityAnalytics.distribution(r.points.filter{it.gatewayState=="OK"}.mapNotNull{it.gatewayMs})
        return buildString {
            append(r.host).append(":443 · ").append(r.plan.label).append(" · ").append(r.end).append('\n')
            append("Пробы: ").append(r.points.size).append('/').append(r.plan.count).append('\n')
            append("Сети: ").append(r.points.map{it.network}.distinct().joinToString()).append('\n')
            append("TCP median / p95 / max: ").append(number(tcp.median)).append(" / ").append(number(tcp.p95)).append(" / ").append(number(tcp.max)).append(" мс\n")
            append("Джиттер TCP: ").append(number(s.jitterMs)).append(" мс · ошибки проб: ").append(number(s.failurePercent)).append("%\n")
            append("Успех / ошибка / недоступно: ").append(s.successful).append(" / ").append(s.failed).append(" / ").append(s.unavailable).append('\n')
            append("DNS ошибки: ").append(r.points.count{it.tcpState=="DNS_ERROR"}).append(" · TCP ошибки: ").append(r.points.count{it.tcpState=="TCP_ERROR"}).append('\n')
            append("Шлюз median: ").append(number(gw.median)).append(" мс · ответов: ").append(gw.count)
                .append(" · без ответа: ").append(r.points.count{it.gatewayState=="NO_REPLY"}).append('\n')
            append("Наблюдаемые смены сети: ").append(s.switches).append('\n')
            if(tcp.count<3) append("Мало успешных проб: статистика ограничена.\n")
            append("Не игровой ping, не ICMP и не потери пакетов.")
        }
    }
    fun comparable(a:LiveNetworkRecord,b:LiveNetworkRecord):Boolean =
        a.id!=b.id && a.host==b.host && a.plan==b.plan &&
            a.end==LiveNetworkEnd.COMPLETE && b.end==LiveNetworkEnd.COMPLETE
    fun compare(a:LiveNetworkRecord,b:LiveNetworkRecord):String {
        require(comparable(a,b))
        return "A / B · одинаковый узел и режим\n\nA: "+report(a)+"\n\nB: "+report(b)+
            "\n\nСравнение описательное: сети, DNS/CDN и число успешных проб могут отличаться. Причина изменения задержки не доказана."
    }
    fun csv(record:LiveNetworkRecord):String {
        val r=record.redacted()
        fun row(values:List<String>)=values.joinToString(","){CsvEscaper.cell(it)}
        return buildString {
            append("host,started_at_ms,plan,end,offset_ms,network,route_group,endpoint_group,tcp_ms,tcp_state,gateway_group,gateway_ms,gateway_state\r\n")
            for(p in r.points) append(row(listOf(r.host,r.startedAt.toString(),r.plan.name,r.end.name,p.offsetMs.toString(),
                p.network,p.route?.toString().orEmpty(),p.endpoint.orEmpty(),p.tcpMs?.toString().orEmpty(),
                p.tcpState,p.gatewayKey.orEmpty(),p.gatewayMs?.toString().orEmpty(),p.gatewayState))).append("\r\n")
        }
    }
}
