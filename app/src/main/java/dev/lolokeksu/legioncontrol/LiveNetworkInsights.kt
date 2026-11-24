package dev.lolokeksu.legioncontrol

/** Observed evidence, not an assertion of network fault causality. */
internal object LiveNetworkInsights {
    internal data class Evidence(val dnsErrors:Int,val tcpErrors:Int,val noAddress:Int,val noNetwork:Int,
        val gatewayOkExternalError:Int,val gatewayNoReplyExternalOk:Int,val unvalidated:Int,
        val ipv4:Int,val ipv6:Int)
    fun evidence(points:List<LiveNetworkPoint>)=Evidence(
        points.count{it.tcpState=="DNS_ERROR"},points.count{it.tcpState=="TCP_ERROR"},
        points.count{it.tcpState=="NO_ADDRESS"},points.count{it.tcpState=="NO_NETWORK"},
        points.count{it.gatewayState=="OK" && it.tcpState in setOf("DNS_ERROR","TCP_ERROR")},
        points.count{it.gatewayState=="NO_REPLY" && it.tcpState=="OK"},
        points.count{it.validated==false},points.count{it.family=="IPV4"},points.count{it.family=="IPV6"})
    fun describe(points:List<LiveNetworkPoint>):String {
        if(points.isEmpty()) return "Недостаточно измерений."
        val e=evidence(points)
        val dns=ConnectivityAnalytics.distribution(points.mapNotNull{it.dnsMs})
        return buildString {
            append("DNS median: ").append(LiveNetworkReports.number(dns.median)).append(" мс (n=").append(dns.count).append(")\n")
            append("IPv4 / IPv6: ").append(e.ipv4).append(" / ").append(e.ipv6)
                .append(" попыток · нет адреса: ").append(e.noAddress).append('\n')
            if(e.noNetwork>0) append("Без активной сети: ").append(e.noNetwork).append(" проб.\n")
            if(e.unvalidated>0) append("Android не подтвердил сеть: ").append(e.unvalidated).append(" проб.\n")
            if(e.dnsErrors>0) append("Ошибки DNS: ").append(e.dnsErrors).append(". Проверьте резолвер/другой домен.\n")
            if(e.tcpErrors>0) append("Ошибки TCP: ").append(e.tcpErrors).append(". Проверьте другой узел или маршрут.\n")
            if(e.gatewayOkExternalError>0) append("Шлюз ответил, внешний тест не прошёл: ").append(e.gatewayOkExternalError)
                .append(". Это не доказывает неисправность провайдера.\n")
            if(e.gatewayNoReplyExternalOk>0) append("Внешний TCP работает без ответа шлюза: ").append(e.gatewayNoReplyExternalOk)
                .append(". Шлюз может фильтровать проверку.\n")
            append("Наблюдения не устанавливают причину сбоя.")
        }
    }
    fun compare(a:List<LiveNetworkPoint>,b:List<LiveNetworkPoint>):String {
        fun tcp(p:List<LiveNetworkPoint>)=ConnectivityAnalytics.distribution(p.filter{it.tcpState=="OK"}.mapNotNull{it.tcpMs})
        fun dns(p:List<LiveNetworkPoint>)=ConnectivityAnalytics.distribution(p.mapNotNull{it.dnsMs})
        val ta=tcp(a);val tb=tcp(b);val da=dns(a);val db=dns(b)
        val sa=LiveNetworkPolicy.summarize(a);val sb=LiveNetworkPolicy.summarize(b)
        fun diff(x:Double?,y:Double?)=if(x==null || y==null) null else y-x
        fun supported(x:ConnectivityDistribution,y:ConnectivityDistribution,p95:Boolean=false):Double? =
            if(x.count<3 || y.count<3) null else if(p95) diff(x.p95,y.p95) else diff(x.median,y.median)
        return "Изменение B − A (не причинная оценка):\n"+
            "TCP median: "+LiveNetworkReports.number(supported(ta,tb))+" мс\n"+
            "TCP p95: "+LiveNetworkReports.number(supported(ta,tb,true))+" мс\n"+
            "DNS median: "+LiveNetworkReports.number(supported(da,db))+" мс\n"+
            "Джиттер: "+LiveNetworkReports.number(diff(sa.jitterMs,sb.jitterMs))+" мс\n"+
            "Ошибки: "+LiveNetworkReports.number(diff(sa.failurePercent,sb.failurePercent))+" п.п.\n"+
            "TCP успешных A/B: "+ta.count+"/"+tb.count+"; для дельт median/p95 нужно ≥3."
    }
}
