package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Writer
import java.text.DateFormat
import java.util.Date
import java.util.UUID

data class AudioTestRun(
    val id:String, val startedAtMs:Long, val endedAtMs:Long?,
    val kind:String, val channel:String, val frequencyHz:Int, val durationMs:Int,
    val sampleRateHz:Int, val expectedFrames:Int, val observedFrames:Int?,
    val routedDeviceType:Int?, val state:String, val errorCode:String
)

data class AudioRouteRecord(val atMs:Long,val kind:String,val type:Int?,val note:String)

/** Manual-only metadata. Never stores audio samples, microphone data or user voice. */
class AudioLabRepository(context:Context):AutoCloseable {
    private val helper=LocalHistoryStore(context.applicationContext)

    fun start(kind:String,channel:String,frequencyHz:Int,durationMs:Int,
              now:Long=System.currentTimeMillis()):String {
        require(AudioSignalGenerator.isValid(kind,channel,frequencyHz,durationMs))
        require(now>0)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("""
                UPDATE audio_test_runs
                SET ended_at_ms=MAX(started_at_ms,?),state='INTERRUPTED',error_code='PROCESS_STOP'
                WHERE state='RUNNING'
            """.trimIndent(),arrayOf(now))
            val id=UUID.randomUUID().toString()
            db.insertOrThrow("audio_test_runs",null,ContentValues().apply {
                put("run_id",id);put("started_at_ms",now);put("kind",kind)
                put("channel",channel);put("frequency_hz",frequencyHz)
                put("duration_ms",durationMs)
                put("sample_rate_hz",AudioSignalGenerator.SAMPLE_RATE)
                put("expected_frames",AudioSignalGenerator.frames(durationMs))
                put("state","RUNNING")
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun appendRoute(id:String,event:AudioRouteRecord) {
        require(event.kind in listOf("ROUTE_START","ROUTE_CHANGE"))
        require(event.atMs>0 && event.note.length<=180)
        require(event.type==null || event.type>=0)
        val db=helper.writableDatabase
        val active=db.rawQuery("SELECT state FROM audio_test_runs WHERE run_id=?",
            arrayOf(id)).use { c -> if(c.moveToFirst())c.getString(0) else null }
        require(active=="RUNNING") { "Audio test is no longer running" }
        db.insertOrThrow("audio_route_events",null,ContentValues().apply {
            put("run_id",id);put("recorded_at_ms",event.atMs)
            put("event_kind",event.kind)
            if(event.type!=null) put("output_device_type",event.type)
            put("details",event.note)
        })
    }

    fun finish(id:String,result:AudioPlaybackResult,now:Long=System.currentTimeMillis()) {
        require(result.state in listOf("COMPLETE","STOPPED","ERROR"))
        require(result.observedFrames>=0 && result.errorCode.length<=48 && now>0)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            val running=db.rawQuery("SELECT expected_frames FROM audio_test_runs WHERE run_id=? AND state='RUNNING'",
                arrayOf(id)).use { c -> if(c.moveToFirst()) c.getInt(0) else null }
            if(running!=null) {
                result.routes.take(40).forEach { record ->
                    db.insertOrThrow("audio_route_events",null,ContentValues().apply {
                        put("run_id",id);put("recorded_at_ms",record.atMs)
                        put("event_kind",record.kind)
                        record.type?.let { put("output_device_type",it) }
                        put("details",record.note.take(180))
                    })
                }
                db.update("audio_test_runs",ContentValues().apply {
                    put("ended_at_ms",maxOf(now,result.startedAtMs))
                    put("observed_frames",result.observedFrames.coerceAtMost(running))
                    result.routedType?.let { put("routed_device_type",it) }
                    put("state",result.state);put("error_code",result.errorCode)
                },"run_id=? AND state='RUNNING'",arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun runs(limit:Int=50):List<AudioTestRun> {
        val out=mutableListOf<AudioTestRun>()
        helper.readableDatabase.rawQuery("""
            SELECT run_id,started_at_ms,ended_at_ms,kind,channel,frequency_hz,
                   duration_ms,sample_rate_hz,expected_frames,observed_frames,
                   routed_device_type,state,error_code
            FROM audio_test_runs ORDER BY started_at_ms DESC,run_id DESC LIMIT ?
        """.trimIndent(),arrayOf(limit.coerceIn(1,100).toString())).use { c ->
            while(c.moveToNext()) out+=AudioTestRun(
                c.getString(0),c.getLong(1),
                if(c.isNull(2))null else c.getLong(2),c.getString(3),
                c.getString(4),c.getInt(5),c.getInt(6),c.getInt(7),
                c.getInt(8),if(c.isNull(9))null else c.getInt(9),
                if(c.isNull(10))null else c.getInt(10),c.getString(11),c.getString(12)
            )
        }
        return out
    }

    fun get(id:String):AudioTestRun?=
        helper.readableDatabase.rawQuery("""
            SELECT run_id,started_at_ms,ended_at_ms,kind,channel,frequency_hz,
                   duration_ms,sample_rate_hz,expected_frames,observed_frames,
                   routed_device_type,state,error_code
            FROM audio_test_runs WHERE run_id=?
        """.trimIndent(),arrayOf(id)).use { c ->
            if(!c.moveToFirst())null else AudioTestRun(
                c.getString(0),c.getLong(1),
                if(c.isNull(2))null else c.getLong(2),c.getString(3),
                c.getString(4),c.getInt(5),c.getInt(6),c.getInt(7),
                c.getInt(8),if(c.isNull(9))null else c.getInt(9),
                if(c.isNull(10))null else c.getInt(10),c.getString(11),c.getString(12)
            )
        }

    fun routes(id:String,limit:Int=40):List<AudioRouteRecord> {
        val out=mutableListOf<AudioRouteRecord>()
        helper.readableDatabase.rawQuery("""
            SELECT recorded_at_ms,event_kind,output_device_type,details FROM audio_route_events
            WHERE run_id=? ORDER BY recorded_at_ms,id LIMIT ?
        """.trimIndent(),arrayOf(id,limit.coerceIn(1,80).toString())).use { c ->
            while(c.moveToNext())out+=AudioRouteRecord(c.getLong(0),c.getString(1),
                if(c.isNull(2))null else c.getInt(2),c.getString(3))
        }
        return out
    }

    fun format(id:String):String {
        val run=get(id) ?: return "История теста не найдена."
        return buildString {
            append("AUDIO LAB · ").append(DateFormat.getDateTimeInstance().format(Date(run.startedAtMs))).append("\n")
            append("Тест: ").append(run.kind).append(" · ").append(run.channel)
                .append(" · ").append(run.frequencyHz).append(" Гц")
                .append(" · ").append(run.durationMs).append(" мс\n")
            append("Состояние: ").append(run.state).append(" · ")
                .append(run.observedFrames?.toString() ?: "н/д").append(" / ")
                .append(run.expectedFrames).append(" кадров по AudioTrack\n")
            append("Наблюдаемый route type: ").append(run.routedDeviceType?.toString() ?: "н/д").append("\n")
            if(run.errorCode.isNotEmpty()) append("Ошибка: ").append(run.errorCode).append("\n")
            val routeRecords=routes(id)
            if(routeRecords.isNotEmpty()) {
                append("События маршрута: ").append(routeRecords.size).append("\n")
                routeRecords.forEach { append("• ").append(it.kind)
                    .append(" · type=").append(it.type ?: "н/д").append("\n") }
            }
            append("Кадры AudioTrack — НЕ измеренная задержка динамика, громкость или слышимость.")
        }
    }

    fun compareRecent():String {
        val data=runs(2)
        if(data.size<2)return "Для сравнения нужны два законченных или прерванных теста."
        return buildString {
            append("ДВА ПОСЛЕДНИХ AUDIO TEST · только метаданные\n")
            for(run in data.asReversed()) {
                append(run.kind).append(" / ").append(run.channel).append(" / ")
                    .append(run.state).append(" · ")
                    .append(run.observedFrames ?: "н/д").append("/")
                    .append(run.expectedFrames).append(" кадров")
                    .append(" · маршрут type ").append(run.routedDeviceType ?: "н/д").append("\n")
            }
            append("Разные частоты, каналы и маршруты несопоставимы по качеству звучания. ")
            append("Прогресс AudioTrack — не акустическое измерение.")
        }
    }

    fun delete(id:String):Boolean=
        helper.writableDatabase.delete("audio_test_runs","run_id=?",arrayOf(id))>0

    fun exportCsv(writer:Writer) {
        writer.write("run_id,started_at_ms,ended_at_ms,kind,channel,frequency_hz,duration_ms,sample_rate_hz,expected_frames,observed_frames,routed_device_type,state,error_code\n")
        helper.readableDatabase.rawQuery("""
            SELECT run_id,started_at_ms,ended_at_ms,kind,channel,frequency_hz,
                   duration_ms,sample_rate_hz,expected_frames,observed_frames,
                   routed_device_type,state,error_code FROM audio_test_runs ORDER BY started_at_ms,run_id
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) writer.write((0 until c.columnCount).joinToString(",") {
                if(c.isNull(it)) "" else ApplicationAuditRules.csvCell(c.getString(it))
            }+"\n")
        }
        writer.write("\nrun_id,recorded_at_ms,event_kind,output_device_type,details\n")
        helper.readableDatabase.rawQuery("""
            SELECT run_id,recorded_at_ms,event_kind,output_device_type,details
            FROM audio_route_events ORDER BY recorded_at_ms,id
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) writer.write((0 until c.columnCount).joinToString(",") {
                if(c.isNull(it)) "" else ApplicationAuditRules.csvCell(c.getString(it))
            }+"\n")
        }
    }

    override fun close(){helper.close()}
}
