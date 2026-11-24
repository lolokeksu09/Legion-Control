package dev.lolokeksu.legioncontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The v3 extension lives inside the hashed, journaled preferences.json payload.
 * It is not applied to SharedPreferences. No SQL from the archive is executed. */
internal data class DiagnosticBackupBundle(val network:List<LiveNetworkRecord>,val battery:List<BatteryLabRun>) {
    init {
        require(network.size<=LiveNetworkArchive.LIMIT && battery.size<=BatteryLabArchive.LIMIT)
        require(network.map{it.id}.distinct().size==network.size && battery.map{it.id}.distinct().size==battery.size)
        network.forEach { r ->
            require(r==r.redacted()) { "Unredacted network identifiers" }
            r.points.forEach { p ->
                require(p.tcpState in setOf("OK","DNS_ERROR","TCP_ERROR","NO_NETWORK","NO_ADDRESS","SWITCHED"))
                require(p.gatewayState in setOf("OK","NO_REPLY","UNAVAILABLE"))
                require(p.family==null || p.family in setOf("IPV4","IPV6"))
                require(listOf(p.tcpMs,p.gatewayMs,p.dnsMs).all{it==null || LiveNetworkPolicy.valid(it)})
                require((p.tcpState=="OK")== (p.tcpMs!=null))
                require((p.gatewayState=="OK")== (p.gatewayMs!=null))
            }
        }
        battery.forEach { r -> r.points.forEach { p ->
            require(p.percent==null || p.percent in 0..100)
            require(p.tempDeciC==null || p.tempDeciC in -400..1200)
            require(p.currentMicroA==null || p.currentMicroA in -50_000_000..50_000_000)
            require(p.voltageMv==null || p.voltageMv in 500..20_000)
            require(p.chargeMicroAh==null || p.chargeMicroAh in 0..100_000_000)
            require(p.remainingEnergyNwh==null || p.remainingEnergyNwh in 0L..1_000_000_000_000L)
            require(p.etaMs==null || p.etaMs in 0L..604_800_000L)
            require(p.thermal==null || p.thermal in 0..6)
        } }
    }
    fun encode(context:Context):JSONObject {
        val n=LiveNetworkArchive(context);val b=BatteryLabArchive(context)
        return try {
            JSONObject().put("network",JSONArray().apply{network.forEach{put(JSONObject(n.encode(it)))}})
                .put("battery",JSONArray().apply{battery.forEach{put(JSONObject(b.encode(it)))}})
        } finally { n.close();b.close() }
    }
    fun requireEmptyTargets(context:Context) {
        val n=LiveNetworkArchive(context);val b=BatteryLabArchive(context)
        try {
            check(n.list().isEmpty() && b.list().isEmpty()) {
                "Новый архив диагностики содержит записи. Восстановление разрешено только в пустые архивы."
            }
        } finally { n.close();b.close() }
    }
    /** Each archive commits atomically; exact replay after interruption is a no-op.
     * Preflight both targets before replay so divergent records are not overwritten. */
    fun restore(context:Context) {
        val n=LiveNetworkArchive(context);val b=BatteryLabArchive(context)
        try {
            val a=n.list();val c=b.list()
            check(a.isEmpty() || a.sortedBy{it.id}==network.sortedBy{it.id})
            check(c.isEmpty() || c.sortedBy{it.id}==battery.sortedBy{it.id})
            n.restoreSnapshot(network);b.restoreSnapshot(battery)
        } finally { n.close();b.close() }
    }
    companion object {
        const val KEY="diagnostic_archives"
        fun capture(context:Context):DiagnosticBackupBundle {
            val n=LiveNetworkArchive(context);val b=BatteryLabArchive(context)
            return try { DiagnosticBackupBundle(n.list(),b.list()) }
            finally { n.close();b.close() }
        }
        fun parse(context:Context,bytes:ByteArray,version:Int):DiagnosticBackupBundle? {
            if(version<3)return null
            require(version==3)
            val root=JSONObject(String(bytes,Charsets.UTF_8)).getJSONObject(KEY)
            require(root.keys().asSequence().toSet()==setOf("network","battery"))
            val nr=root.getJSONArray("network");val br=root.getJSONArray("battery")
            require(nr.length()<=LiveNetworkArchive.LIMIT && br.length()<=BatteryLabArchive.LIMIT)
            val n=LiveNetworkArchive(context);val b=BatteryLabArchive(context)
            return try {
                DiagnosticBackupBundle((0 until nr.length()).map{n.decode(nr.getJSONObject(it).toString())},
                    (0 until br.length()).map{b.decode(br.getJSONObject(it).toString())})
            } finally { n.close();b.close() }
        }
    }
}
