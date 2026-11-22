package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Writer
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class HapticTestRun(
    val id:String,val startedAtMs:Long,val endedAtMs:Long?,val pattern:String,
    val level:String,val requestedAmplitude:Int,val durationMs:Long,val vibratorCount:Int?,
    val amplitudeControl:Boolean?,val usedDefaultAmplitude:Boolean?,
    val result:String,val errorCode:String
)
data class HapticHumanFeedback(val rating:String,val note:String,val updatedAtMs:Long)

/** User-triggered private SQLite only. Never stores movement or makes a claim of measured vibration. */
class HapticLabRepository(context:Context):AutoCloseable {
    private val helper=LocalHistoryStore(context.applicationContext)

    fun start(waveform:HapticWaveform,now:Long=System.currentTimeMillis()):String {
        require(HapticPattern.isValid(waveform.kind,waveform.level))
        require(waveform.requestedAmplitude==HapticPattern.amplitude(waveform.level))
        require(waveform.durationMs in 35..1_800 && now>0)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            // Crash recovery retains all prior rows; does not falsely claim a completed motor response.
            db.execSQL("""
                UPDATE haptic_test_runs
                SET ended_at_ms=MAX(started_at_ms,?),result='INTERRUPTED',error_code='PROCESS_STOP'
                WHERE result='RUNNING'
            """.trimIndent(),arrayOf(now))
            val id=UUID.randomUUID().toString()
            db.insertOrThrow("haptic_test_runs",null,ContentValues().apply {
                put("run_id",id)
                put("started_at_ms",now)
                put("pattern",waveform.kind)
                put("level",waveform.level)
                put("requested_amplitude",waveform.requestedAmplitude)
                put("duration_ms",waveform.durationMs)
                put("result","RUNNING")
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun finish(id:String,outcome:HapticPlaybackResult,now:Long=System.currentTimeMillis()) {
        require(outcome.state in listOf("COMPLETED_REQUEST","CANCELLED","ERROR"))
        require(now>0 && outcome.errorCode.length<=48)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            val started=db.rawQuery("""
                SELECT started_at_ms FROM haptic_test_runs
                WHERE run_id=? AND result='RUNNING'
            """.trimIndent(),arrayOf(id)).use { c ->
                if(c.moveToFirst())c.getLong(0) else null
            }
            if(started!=null) {
                db.update("haptic_test_runs",ContentValues().apply {
                    put("ended_at_ms",maxOf(now,started))
                    put("result",outcome.state)
                    put("error_code",outcome.errorCode)
                    outcome.vibratorCount?.let { put("vibrator_count",it.coerceIn(0,32)) }
                    outcome.amplitudeControl?.let { put("amplitude_control",if(it)1 else 0) }
                    outcome.usedDefaultAmplitude?.let { put("used_default_amplitude",if(it)1 else 0) }
                },"run_id=? AND result='RUNNING'",arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun get(id:String):HapticTestRun?=helper.readableDatabase.rawQuery("""
        SELECT run_id,started_at_ms,ended_at_ms,pattern,level,requested_amplitude,
            duration_ms,vibrator_count,amplitude_control,used_default_amplitude,
            result,error_code
        FROM haptic_test_runs WHERE run_id=?
    """.trimIndent(),arrayOf(id)).use { c -> if(!c.moveToFirst())null else decode(c) }

    private fun decode(c:android.database.Cursor):HapticTestRun=HapticTestRun(
        c.getString(0),c.getLong(1),if(c.isNull(2))null else c.getLong(2),
        c.getString(3),c.getString(4),c.getInt(5),c.getLong(6),
        if(c.isNull(7))null else c.getInt(7),
        if(c.isNull(8))null else c.getInt(8)==1,
        if(c.isNull(9))null else c.getInt(9)==1,c.getString(10),c.getString(11)
    )

    fun runs(limit:Int=70):List<HapticTestRun> {
        val out=mutableListOf<HapticTestRun>()
        helper.readableDatabase.rawQuery("""
            SELECT run_id,started_at_ms,ended_at_ms,pattern,level,requested_amplitude,
                duration_ms,vibrator_count,amplitude_control,used_default_amplitude,
                result,error_code
            FROM haptic_test_runs ORDER BY started_at_ms DESC,run_id DESC LIMIT ?
        """.trimIndent(),arrayOf(limit.coerceIn(1,100).toString())).use { c ->
            while(c.moveToNext())out+=decode(c)
        }
        return out
    }

    fun feedback(id:String):HapticHumanFeedback? =
        helper.readableDatabase.rawQuery("""
            SELECT rating,note,updated_at_ms FROM haptic_feedback WHERE run_id=?
        """.trimIndent(),arrayOf(id)).use { c ->
            if(c.moveToFirst())HapticHumanFeedback(c.getString(0),c.getString(1),c.getLong(2))
            else null
        }

    fun rate(id:String,rating:String,note:String="",now:Long=System.currentTimeMillis()) {
        require(rating in listOf("FELT","NOT_FELT","UNCERTAIN"))
        require(note.length<=200 && now>0 && !note.contains('\u0000'))
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            val state=db.rawQuery("SELECT result FROM haptic_test_runs WHERE run_id=?",
                arrayOf(id)).use { c -> if(c.moveToFirst())c.getString(0) else null }
            require(state!=null && state!="RUNNING") { "Test is not yet a completed record" }
            db.insertWithOnConflict("haptic_feedback",null,ContentValues().apply {
                put("run_id",id)
                put("rating",rating)
                put("note",note)
                put("updated_at_ms",now)
            },SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveSnapshot(snapshot:HapticCapabilitySnapshot) {
        require(snapshot.recordedAtMs>0 && snapshot.vibratorCount in 0..32)
        require(snapshot.effects.length<=240 && snapshot.primitives.length<=240)
        helper.writableDatabase.insertOrThrow("haptic_capability_snapshots",null,ContentValues().apply {
            put("recorded_at_ms",snapshot.recordedAtMs)
            put("vibrator_count",snapshot.vibratorCount)
            put("has_vibrator",if(snapshot.hasVibrator)1 else 0)
            snapshot.amplitudeControl?.let { put("amplitude_control",if(it)1 else 0) }
            snapshot.systemHapticsEnabled?.let { put("haptic_enabled",if(it)1 else 0) }
            put("effects",snapshot.effects)
            put("primitives",snapshot.primitives)
        })
    }

    fun latestSnapshot():HapticCapabilitySnapshot? =
        helper.readableDatabase.rawQuery("""
            SELECT recorded_at_ms,vibrator_count,has_vibrator,amplitude_control,
                haptic_enabled,effects,primitives
            FROM haptic_capability_snapshots ORDER BY recorded_at_ms DESC,id DESC LIMIT 1
        """.trimIndent(),null).use { c ->
            if(!c.moveToFirst())null else HapticCapabilitySnapshot(
                c.getLong(0),c.getInt(1),c.getInt(2)==1,
                if(c.isNull(3))null else c.getInt(3)==1,
                if(c.isNull(4))null else c.getInt(4)==1,
                c.getString(5),c.getString(6))
        }

    fun render(id:String):String {
        val r=get(id) ?: return "Тест не найден."
        val opinion=feedback(id)
        return buildString {
            append("HAPTIC LAB · ").append(DateFormat.getDateTimeInstance().format(Date(r.startedAtMs)))
                .append("\n")
            append("Сигнал: ").append(r.pattern).append(" · ").append(r.level)
                .append(" · ").append(r.durationMs).append(" мс")
                .append(" · цифровая амплитуда ").append(r.requestedAmplitude).append("/255\n")
            append("API-результат: ").append(r.result).append("\n")
            append("Android вибраторов: ").append(r.vibratorCount?.toString() ?: "н/д")
                .append(" · регулировка амплитуды: ")
                .append(r.amplitudeControl?.let { if(it)"да" else "нет" } ?: "н/д").append("\n")
            if(r.usedDefaultAmplitude==true)
                append("Платформа без регулировки: применён DEFAULT_AMPLITUDE, не заданная сила.\n")
            if(r.errorCode.isNotEmpty())append("Ошибка/остановка: ").append(r.errorCode).append("\n")
            append("Оценка пользователя: ").append(opinion?.rating ?: "ещё не дана").append("\n")
            if(opinion?.note?.isNotBlank()==true)append("Заметка: ").append(opinion.note).append("\n")
            append(HapticPattern.caveat).append("\n")
            append("COMPLETED_REQUEST — завершился интервал запроса API, ")
                .append("не измерение моторчика.")
        }
    }

    fun compareRecent():String {
        val tests=runs(2)
        if(tests.size<2) return "Для сравнения нужны минимум два выполненных запроса."
        return buildString {
            append("ДВА ПОСЛЕДНИХ HAPTIC TEST · запросы Android\n")
            for(r in tests.asReversed()) {
                append(r.pattern).append(" · ").append(r.level)
                    .append(" · ").append(r.durationMs).append(" мс")
                    .append(" · ").append(r.result)
                    .append(" · ощущение: ").append(feedback(r.id)?.rating ?: "нет ответа").append("\n")
            }
            append("Субъективные ощущения не доказывают физическую амплитуду, ")
                .append("задержку или качество вибромотора.")
        }
    }

    fun delete(id:String):Boolean =
        helper.writableDatabase.delete("haptic_test_runs","run_id=?",arrayOf(id))>0

    fun exportCsv(writer:Writer) {
        writer.write("run_id,started_at_ms,ended_at_ms,pattern,level,requested_amplitude,duration_ms,vibrator_count,amplitude_control,used_default_amplitude,result,error_code,subjective_rating,subjective_note\n")
        helper.readableDatabase.rawQuery("""
            SELECT r.run_id,r.started_at_ms,r.ended_at_ms,r.pattern,r.level,
                r.requested_amplitude,r.duration_ms,r.vibrator_count,r.amplitude_control,
                r.used_default_amplitude,r.result,r.error_code,f.rating,f.note
            FROM haptic_test_runs r LEFT JOIN haptic_feedback f ON f.run_id=r.run_id
            ORDER BY r.started_at_ms,r.run_id
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) writer.write((0 until c.columnCount).joinToString(",") {
                if(c.isNull(it)) "" else HapticPattern.csvCell(c.getString(it))
            }+"\n")
        }
        writer.write("\nrecorded_at_ms,vibrator_count,has_vibrator,amplitude_control,haptic_enabled,effects,primitives\n")
        helper.readableDatabase.rawQuery("""
            SELECT recorded_at_ms,vibrator_count,has_vibrator,amplitude_control,
                haptic_enabled,effects,primitives
            FROM haptic_capability_snapshots ORDER BY recorded_at_ms,id
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) writer.write((0 until c.columnCount).joinToString(",") {
                if(c.isNull(it)) "" else HapticPattern.csvCell(c.getString(it))
            }+"\n")
        }
    }

    override fun close() { helper.close() }
}
