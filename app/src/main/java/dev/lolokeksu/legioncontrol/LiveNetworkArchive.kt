package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Dedicated private archive. No changes to the legacy v12 schema or ZIP format. */
internal class LiveNetworkArchive(context:Context,name:String="legion_live_network.db"):
    SQLiteOpenHelper(context.applicationContext,name,null,1) {
    companion object { const val LIMIT=50 }
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE live_runs(id TEXT PRIMARY KEY NOT NULL, started INTEGER NOT NULL, payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        error("Unsupported live network archive migration")
    }
    fun save(record:LiveNetworkRecord) {
        val r=record.redacted()
        val payload=encode(r)
        val db=writableDatabase
        db.beginTransaction()
        try {
            val exists=db.rawQuery("SELECT 1 FROM live_runs WHERE id=?",arrayOf(r.id)).use{it.moveToFirst()}
            if(!exists) {
                val count=db.rawQuery("SELECT COUNT(*) FROM live_runs",null).use { it.moveToFirst();it.getInt(0) }
                check(count<LIMIT) { "Архив заполнен: удалите одну запись вручную." }
                db.insertOrThrow("live_runs",null,ContentValues().apply {
                    put("id",r.id); put("started",r.startedAt); put("payload",payload)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun list():List<LiveNetworkRecord> = readableDatabase.rawQuery(
        "SELECT payload FROM live_runs ORDER BY started DESC,id DESC LIMIT "+LIMIT,null
    ).use { cursor->
        buildList { while(cursor.moveToNext()) add(decode(cursor.getString(0))) }
    }
    fun delete(id:String):Boolean = writableDatabase.delete("live_runs","id=?",arrayOf(id))==1
    private fun encode(r:LiveNetworkRecord):String=JSONObject().apply {
        put("v",2);put("ipMode",r.ipMode.name);put("id",r.id);put("host",r.host);put("started",r.startedAt)
        put("plan",r.plan.name);put("end",r.end.name)
        put("points",JSONArray().apply {
            r.points.forEach { p->put(JSONArray().apply {
                put(p.offsetMs);put(p.network);put(p.route ?: JSONObject.NULL);put(p.endpoint ?: JSONObject.NULL)
                put(p.tcpMs ?: JSONObject.NULL);put(p.tcpState);put(p.gatewayMs ?: JSONObject.NULL)
                put(p.gatewayState);put(p.gatewayKey ?: JSONObject.NULL)
                put(p.dnsMs ?: JSONObject.NULL);put(p.family ?: JSONObject.NULL);put(p.validated ?: JSONObject.NULL)
            }) }
        })
    }.toString()
    private fun decode(raw:String):LiveNetworkRecord {
        require(raw.length<=100_000)
        val obj=JSONObject(raw)
        val version=obj.getInt("v")
        require(version in 1..2)
        val ipMode=if(version==1) LiveIpMode.AUTO else LiveIpMode.valueOf(obj.getString("ipMode"))
        val plan=LiveNetworkPlan.valueOf(obj.getString("plan"))
        val rows=obj.getJSONArray("points")
        require(rows.length() in 1..plan.count)
        val points=(0 until rows.length()).map { index->
            val a=rows.getJSONArray(index)
            require(a.length()==if(version==1) 9 else 12)
            fun text(i:Int)=if(a.isNull(i)) null else a.getString(i)
            fun number(i:Int)=if(a.isNull(i)) null else a.getDouble(i).also{require(LiveNetworkPolicy.valid(it))}
            LiveNetworkPoint(a.getLong(0),a.getString(1),if(a.isNull(2)) null else a.getLong(2),
                text(3),number(4),a.getString(5),number(6),a.getString(7),null,text(8),
                if(version==2) number(9) else null,if(version==2) text(10) else null,
                if(version==2 && !a.isNull(11)) a.getBoolean(11) else null)
        }
        return LiveNetworkRecord(obj.getString("id"),obj.getString("host"),obj.getLong("started"),
            plan,LiveNetworkEnd.valueOf(obj.getString("end")),points,ipMode)
    }
}
