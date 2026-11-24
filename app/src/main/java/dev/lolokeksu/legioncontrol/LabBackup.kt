package dev.lolokeksu.legioncontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, typed portable snapshots; never import external SQLite pages into lab stores. */
internal object LabBackup {
    const val MAX_BYTES=4*1024*1024
    data class Data(val network:List<LiveNetworkRecord>,val battery:List<BatteryLabRun>)
    fun snapshot(context:Context):ByteArray {
        val root=JSONObject().put("version",1)
        LiveNetworkArchive(context).use{db->
            val rows=db.list();check(db.count()==rows.size)
            root.put("network",JSONArray().apply{rows.forEach{put(JSONObject(db.encode(it.redacted())))}})
        }
        BatteryLabArchive(context).use{db->
            val rows=db.list();check(db.count()==rows.size)
            root.put("battery",JSONArray().apply{rows.forEach{put(JSONObject(db.encode(it)))}})
        }
        return root.toString().toByteArray(Charsets.UTF_8).also{
            require(it.size in 1..MAX_BYTES);parse(context,it)
        }
    }
    fun parse(context:Context,bytes:ByteArray):Data {
        require(bytes.size in 1..MAX_BYTES)
        val root=JSONObject(String(bytes,Charsets.UTF_8))
        require(root.keys().asSequence().toSet()==setOf("version","network","battery") && root.getInt("version")==1)
        val n=root.getJSONArray("network");val b=root.getJSONArray("battery")
        require(n.length()<=LiveNetworkArchive.LIMIT && b.length()<=BatteryLabArchive.LIMIT)
        val network=LiveNetworkArchive(context).use{db->
            (0 until n.length()).map{i->db.decode(n.getJSONObject(i).toString()).also{r->
                require(r==r.redacted())
                require(r.points.all{p->
                    p.tcpState in setOf("OK","TCP_ERROR","DNS_ERROR","NO_ADDRESS","NO_NETWORK","SWITCHED") &&
                    p.gatewayState in setOf("OK","NO_REPLY","UNAVAILABLE") &&
                    (p.family==null || p.family in setOf("IPV4","IPV6")) &&
                    (p.tcpMs!=null)==(p.tcpState=="OK") &&
                    (p.gatewayMs!=null)==(p.gatewayState=="OK")
                })
            }}
        }
        val battery=BatteryLabArchive(context).use{db->
            (0 until b.length()).map{i->db.decode(b.getJSONObject(i).toString()).also{r->
                require(r.points.all{p->
                    (p.percent==null || p.percent in 0..100) &&
                    (p.tempDeciC==null || p.tempDeciC in -400..1200) &&
                    (p.currentMicroA==null || p.currentMicroA in -50_000_000..50_000_000) &&
                    (p.voltageMv==null || p.voltageMv in 500..20_000) &&
                    (p.chargeMicroAh==null || p.chargeMicroAh in 0..100_000_000) &&
                    (p.remainingEnergyNwh==null || p.remainingEnergyNwh in 0L..1_000_000_000_000L) &&
                    (p.etaMs==null || p.etaMs in 0L..604_800_000L) &&
                    (p.thermal==null || p.thermal in 0..6)
                })
            }}
        }
        require(network.map{it.id}.toSet().size==network.size && battery.map{it.id}.toSet().size==battery.size)
        return Data(network,battery)
    }
    fun requireEmpty(context:Context) {
        check(LiveNetworkArchive(context).use{it.count()==0} && BatteryLabArchive(context).use{it.count()==0}) {
            "Архивы Network/Battery содержат записи. ZIP v3 можно восстановить только в пустые архивы."
        }
    }
    /** Idempotent replay after main DB commit. No existing different row is overwritten. */
    fun restore(context:Context,data:Data) {
        LiveNetworkArchive(context).use{it.restoreRecords(data.network)}
        BatteryLabArchive(context).use{it.restoreRecords(data.battery)}
    }
}
