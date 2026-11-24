package dev.lolokeksu.legioncontrol

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import org.json.JSONArray

/** Separate explicit-save archive. Included in backup v3 without legacy schema changes. */
internal class BatteryLabArchive(private val context:Context,name:String="legion_battery_lab.db"):
    SQLiteOpenHelper(context.applicationContext,name,null,1) {
    companion object { const val LIMIT=30 }
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE battery_runs(id TEXT PRIMARY KEY NOT NULL, started INTEGER NOT NULL, payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) { error("Unsupported battery archive migration") }
    fun save(run:BatteryLabRun) = synchronized(LegionDataBackup) {
        LegionDataBackup.checkArchiveWritesAllowed(context)
        val db=writableDatabase
        db.beginTransaction()
        try {
            val exists=db.rawQuery("SELECT 1 FROM battery_runs WHERE id=?",arrayOf(run.id)).use{it.moveToFirst()}
            if(!exists) {
                val count=db.rawQuery("SELECT COUNT(*) FROM battery_runs",null).use{it.moveToFirst();it.getInt(0)}
                check(count<LIMIT){"Архив заполнен. Удалите запись вручную."}
                db.insertOrThrow("battery_runs",null,ContentValues().apply{
                    put("id",run.id);put("started",run.startedAt);put("payload",encode(run))
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun list():List<BatteryLabRun> = readableDatabase.rawQuery(
        "SELECT payload FROM battery_runs ORDER BY started DESC,id DESC LIMIT "+LIMIT,null
    ).use{c->buildList{while(c.moveToNext()) add(decode(c.getString(0)))}}
    fun delete(id:String):Boolean = synchronized(LegionDataBackup) {
        LegionDataBackup.checkArchiveWritesAllowed(context)
        writableDatabase.delete("battery_runs","id=?",arrayOf(id))==1
    }
    internal fun restoreSnapshot(records:List<BatteryLabRun>) {
        val db=writableDatabase
        db.beginTransaction()
        try {
            val existing=list()
            check(existing.isEmpty() || existing.sortedBy{it.id}==records.sortedBy{it.id}) {
                "Архив батареи содержит другие данные; восстановление остановлено."
            }
            if(existing.isEmpty()) for(r in records) db.insertOrThrow("battery_runs",null,ContentValues().apply {
                put("id",r.id);put("started",r.startedAt);put("payload",encode(r))
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    internal fun encode(r:BatteryLabRun)=JSONObject().apply{
        put("v",1);put("id",r.id);put("started",r.startedAt);put("plan",r.plan.name);put("end",r.end.name)
        put("points",JSONArray().apply{r.points.forEach{p->put(JSONArray().apply{
            listOf(p.elapsedMs,p.percent,p.tempDeciC,p.currentMicroA,p.voltageMv,p.chargeMicroAh,p.remainingEnergyNwh,
                p.etaMs,p.status.name,p.plug.name,p.thermal).forEach{put(it ?: JSONObject.NULL)}
        })}})
    }.toString()
    internal fun decode(raw:String):BatteryLabRun {
        require(raw.length<=150_000)
        val o=JSONObject(raw);require(o.getInt("v")==1)
        val rows=o.getJSONArray("points");require(rows.length() in 1..121)
        val points=(0 until rows.length()).map{i->
            val a=rows.getJSONArray(i);require(a.length()==11)
            fun int(j:Int)=if(a.isNull(j)) null else a.getInt(j)
            fun long(j:Int)=if(a.isNull(j)) null else a.getLong(j)
            BatteryLabPoint(a.getLong(0),int(1),int(2),int(3),int(4),int(5),long(6),long(7),
                BatteryLabStatus.valueOf(a.getString(8)),BatteryLabPlug.valueOf(a.getString(9)),int(10))
        }
        return BatteryLabRun(o.getString("id"),o.getLong("started"),BatteryLabPlan.valueOf(o.getString("plan")),
            BatteryLabEnd.valueOf(o.getString("end")),points)
    }
}
