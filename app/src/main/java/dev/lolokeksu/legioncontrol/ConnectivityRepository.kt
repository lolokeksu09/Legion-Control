package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Writer
import java.util.UUID

/**
 * Private SQLite. Main-thread database access is forbidden; use a worker executor.
 * All versions 1..8 are retained via LocalHistoryStore's additive upgrade.
 */
class ConnectivityRepository(context:Context):AutoCloseable {
    private val helper=LocalHistoryStore(context.applicationContext)

    fun start(host:String,mode:String,now:Long=System.currentTimeMillis()):String {
        require(ConnectivityProbe.validHost(host))
        require(mode=="SINGLE" || mode=="STABILITY")
        require(now>0)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            // Crash recovery: old unfinished runs are preserved, never erased.
            db.execSQL("""
                UPDATE connectivity_runs
                SET ended_at_ms=MAX(started_at_ms,?),status='INTERRUPTED'
                WHERE status='RUNNING'
            """.trimIndent(),arrayOf(now))
            val id=UUID.randomUUID().toString()
            db.insertOrThrow("connectivity_runs",null,ContentValues().apply {
                put("run_id",id)
                put("host",host)
                put("started_at_ms",now)
                put("mode",mode)
                put("status","RUNNING")
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun record(id:String,sample:ConnectivitySample) {
        require(sample.cycle in 1..6)
        require(sample.family in listOf("IPV4","IPV6"))
        require(sample.result in listOf("OK","NO_ADDRESS","DNS_ERROR","TCP_ERROR","TLS_ERROR"))
        require(sample.timeMs>0 && sample.errorCode.length<=48 &&
            sample.networkType.isNotBlank() && sample.networkType.length<=24)
        for(v in listOfNotNull(sample.dnsMs,sample.tcpMs,sample.tlsMs))
            require(v.isFinite() && v in 0.0..120_000.0)
        val db=helper.writableDatabase
        val active=db.rawQuery("SELECT status FROM connectivity_runs WHERE run_id=?",
            arrayOf(id)).use { c -> if(c.moveToFirst())c.getString(0) else null }
        require(active=="RUNNING") { "Recording is not active" }
        db.insertOrThrow("connectivity_samples",null,ContentValues().apply {
            put("run_id",id)
            put("cycle",sample.cycle)
            put("recorded_at_ms",sample.timeMs)
            put("family",sample.family)
            sample.dnsMs?.let { put("dns_ms",it) }
            sample.tcpMs?.let { put("tcp_ms",it) }
            sample.tlsMs?.let { put("tls_ms",it) }
            put("result",sample.result)
            put("error_code",sample.errorCode)
            put("network_type",sample.networkType)
            sample.networkValidated?.let { put("network_validated",if(it)1 else 0) }
        })
    }

    fun finish(id:String,status:String,now:Long=System.currentTimeMillis()) {
        require(status in listOf("COMPLETE","CANCELLED","FAILED","INTERRUPTED"))
        require(now>0)
        val db=helper.writableDatabase
        db.execSQL("""
            UPDATE connectivity_runs
            SET ended_at_ms=MAX(started_at_ms,?),status=?
            WHERE run_id=? AND status='RUNNING'
        """.trimIndent(),arrayOf(now,status,id))
    }

    fun runs(host:String?=null,limit:Int=50):List<ConnectivityRun> {
        require(host==null || ConnectivityProbe.validHost(host))
        val out=mutableListOf<ConnectivityRun>()
        val where=if(host==null)"" else " WHERE host=?"
        val arguments=if(host==null) arrayOf(limit.coerceIn(1,100).toString())
            else arrayOf(host,limit.coerceIn(1,100).toString())
        helper.readableDatabase.rawQuery("""
            SELECT run_id,host,started_at_ms,ended_at_ms,mode,status
            FROM connectivity_runs
        """.trimIndent()+where+" ORDER BY started_at_ms DESC,run_id DESC LIMIT ?",arguments).use { c ->
            while(c.moveToNext()) out+=ConnectivityRun(
                c.getString(0),c.getString(1),c.getLong(2),
                if(c.isNull(3))null else c.getLong(3),c.getString(4),c.getString(5))
        }
        return out
    }

    fun get(id:String):ConnectivityRun? =
        helper.readableDatabase.rawQuery("""
            SELECT run_id,host,started_at_ms,ended_at_ms,mode,status
            FROM connectivity_runs WHERE run_id=?
        """.trimIndent(),arrayOf(id)).use { c ->
            if(!c.moveToFirst()) null else ConnectivityRun(c.getString(0),c.getString(1),
                c.getLong(2),if(c.isNull(3))null else c.getLong(3),
                c.getString(4),c.getString(5))
        }

    fun samples(id:String):List<ConnectivitySample> {
        val out=mutableListOf<ConnectivitySample>()
        helper.readableDatabase.rawQuery("""
            SELECT cycle,recorded_at_ms,family,dns_ms,tcp_ms,tls_ms,result,
                   error_code,network_type,network_validated
            FROM connectivity_samples WHERE run_id=? ORDER BY cycle,id LIMIT 12
        """.trimIndent(),arrayOf(id)).use { c ->
            while(c.moveToNext()) out+=ConnectivitySample(
                cycle=c.getInt(0),timeMs=c.getLong(1),family=c.getString(2),
                dnsMs=if(c.isNull(3))null else c.getDouble(3),
                tcpMs=if(c.isNull(4))null else c.getDouble(4),
                tlsMs=if(c.isNull(5))null else c.getDouble(5),
                result=c.getString(6),errorCode=c.getString(7),
                networkType=c.getString(8),
                networkValidated=if(c.isNull(9))null else c.getInt(9)==1
            )
        }
        return out
    }

    fun summary(id:String):ConnectivitySummary {
        val run=get(id) ?: error("Run not found")
        return ConnectivityAnalytics.summarize(run,samples(id))
    }

    fun comparison(host:String):String {
        val list=runs(host,2)
        if(list.size<2) return "Для сравнения нужны минимум два теста одного публичного узла."
        val earlier=summary(list[1].id)
        val later=summary(list[0].id)
        return ConnectivityAnalytics.compare(earlier,later)
    }

    fun lastSevenDays(host:String,now:Long=System.currentTimeMillis()):String {
        require(ConnectivityProbe.validHost(host))
        val week=now-7L*86_400_000L
        val selected=runs(host,100).filter { it.startedAtMs>=week }
        val complete=selected.filter { it.status=="COMPLETE" }
        val summaries=complete.map { ConnectivityAnalytics.summarize(it,samples(it.id)) }
        val validMedian=summaries.mapNotNull { it.tcp.median }
        val tlsSuccess=summaries.sumOf { it.tlsSuccesses }
        val totalCandidates=summaries.sumOf { it.eligibleConnections }
        return buildString {
            append("7 ДНЕЙ · ").append(host).append("\n")
            append("Запусков (из последних 100): ").append(selected.size)
                .append(" · завершённых: ").append(complete.size).append("\n")
            append("Медиана TCP по завершённым тестам: ")
                .append(ConnectivityAnalytics.distribution(validMedian).median?.let {
                    String.format(java.util.Locale.US,"%.1f мс",it)
                } ?: "н/д").append("\n")
            append("Успешных TLS подключений: ").append(tlsSuccess)
                .append("/").append(totalCandidates).append("\n")
            append("Это публичные серверы диагностики; не ping игровых серверов.")
        }
    }

    fun delete(id:String):Boolean = helper.writableDatabase.delete(
        "connectivity_runs","run_id=?",arrayOf(id))>0

    fun writeCsv(writer:Writer) {
        writer.write("run_id,host,started_at_ms,ended_at_ms,mode,status,cycle,recorded_at_ms,ip_family,dns_ms,tcp_ms,tls_ms,result,error_code,android_network_type,android_network_validated\n")
        // Streaming cursor: no whole-database materialization or user-content upload.
        helper.readableDatabase.rawQuery("""
            SELECT r.run_id,r.host,r.started_at_ms,r.ended_at_ms,r.mode,r.status,
                   s.cycle,s.recorded_at_ms,s.family,s.dns_ms,s.tcp_ms,s.tls_ms,
                   s.result,s.error_code,s.network_type,s.network_validated
            FROM connectivity_runs r LEFT JOIN connectivity_samples s
              ON s.run_id=r.run_id
            ORDER BY r.started_at_ms,r.run_id,s.cycle,s.id
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) {
                writer.write((0 until c.columnCount).joinToString(",") { idx ->
                    if(c.isNull(idx)) "" else csv(c.getString(idx))
                }+"\n")
            }
        }
    }

    private fun csv(value:String):String {
        val left=value.trimStart()
        val protected=if(left.firstOrNull() in listOf('=','+','-','@')) "'"+value else value
        return "\"" + protected.replace("\"","\"\"") + "\""
    }

    override fun close(){helper.close()}
}
