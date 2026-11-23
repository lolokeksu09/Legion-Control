package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Writer
import java.util.UUID

data class SessionAnnotation(
    val sessionId: String, val tag: String, val note: String, val updatedAtMs: Long
)

data class SessionExperiment(
    val id: String, val packageName: String, val name: String,
    val hypothesis: String, val labelA: String, val labelB: String,
    val createdAtMs: Long
)

/** Local-only annotation and A/B repository. Call off the Android main thread. */
class ExperimentRepository(context: Context) : AutoCloseable {
    private val helper = LocalHistoryStore(context.applicationContext)
    private val app = context.applicationContext

    companion object {
        private const val MAX_TRIALS = 30
        private const val MAX_SAMPLES_PER_TRIAL = 10000
        private const val MAX_EXPERIMENTS = 100
        private fun checkString(value: String, limit: Int, nonEmpty: Boolean = false) {
            require(value.length <= limit && (!nonEmpty || value.isNotBlank()))
            require(!value.contains('\u0000'))
        }
    }

    fun recentSessions(packageFilter: String? = null, limit: Int = 60): List<GameSessionHeader> {
        require(packageFilter == null || SmartProfilesRepository.validatePackage(packageFilter))
        val sql = """SELECT session_id,package_name,app_label,started_at_ms,ended_at_ms,
                    end_reason,tcp_opt_in FROM game_sessions""" +
                (if(packageFilter == null) "" else " WHERE package_name=?") +
                " ORDER BY started_at_ms DESC LIMIT ?"
        val args = if(packageFilter == null) arrayOf(limit.coerceIn(1,100).toString())
            else arrayOf(packageFilter,limit.coerceIn(1,100).toString())
        val out=mutableListOf<GameSessionHeader>()
        helper.readableDatabase.rawQuery(sql,args).use { c ->
            while(c.moveToNext()) out+=GameSessionHeader(
                c.getString(0),c.getString(1),c.getString(2),c.getLong(3),
                if(c.isNull(4)) null else c.getLong(4),
                if(c.isNull(5)) null else c.getString(5),c.getInt(6)==1
            )
        }
        return out
    }

    fun getAnnotation(sessionId: String): SessionAnnotation? =
        helper.readableDatabase.rawQuery("""
            SELECT session_id,tag,note,updated_at_ms FROM session_annotations
            WHERE session_id=?
        """.trimIndent(),arrayOf(sessionId)).use { c ->
            if(c.moveToFirst()) SessionAnnotation(c.getString(0),c.getString(1),
                c.getString(2),c.getLong(3)) else null
        }

    fun annotate(sessionId: String, tag: String, note: String,
                 now: Long = System.currentTimeMillis()) {
        checkString(tag,40)
        checkString(note,1000)
        require(now>0)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            require(sessionPackage(db,sessionId) != null) { "Session does not exist" }
            if(tag.isEmpty() && note.isEmpty()) {
                db.delete("session_annotations","session_id=?",arrayOf(sessionId))
            } else {
                db.insertWithOnConflict("session_annotations",null,ContentValues().apply {
                    put("session_id",sessionId)
                    put("tag",tag)
                    put("note",note)
                    put("updated_at_ms",now)
                },SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun create(pkg: String, name: String, hypothesis: String,
               labelA: String, labelB: String,
               now: Long = System.currentTimeMillis()): String {
        require(SmartProfilesRepository.validatePackage(pkg) && now>0)
        checkString(name,100,nonEmpty=true)
        checkString(hypothesis,500)
        checkString(labelA,60,nonEmpty=true)
        checkString(labelB,60,nonEmpty=true)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            val count=db.rawQuery("SELECT COUNT(*) FROM session_experiments",null).use {
                if(it.moveToFirst()) it.getInt(0) else 0
            }
            require(count<MAX_EXPERIMENTS) { "Experiment limit reached" }
            val id=UUID.randomUUID().toString()
            db.insertOrThrow("session_experiments",null,ContentValues().apply {
                put("experiment_id",id)
                put("package_name",pkg)
                put("name",name)
                put("hypothesis",hypothesis)
                put("label_a",labelA)
                put("label_b",labelB)
                put("created_at_ms",now)
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun update(id:String,name:String,hypothesis:String,labelA:String,labelB:String):Boolean {
        checkString(name,100,nonEmpty=true)
        checkString(hypothesis,500)
        checkString(labelA,60,nonEmpty=true)
        checkString(labelB,60,nonEmpty=true)
        return helper.writableDatabase.update("session_experiments",ContentValues().apply {
            put("name",name);put("hypothesis",hypothesis)
            put("label_a",labelA);put("label_b",labelB)
        },"experiment_id=?",arrayOf(id))>0
    }

    fun delete(id: String): Boolean =
        helper.writableDatabase.delete("session_experiments","experiment_id=?",arrayOf(id))>0

    fun experiments(packageFilter: String? = null): List<SessionExperiment> {
        require(packageFilter == null || SmartProfilesRepository.validatePackage(packageFilter))
        val where=if(packageFilter==null) "" else " WHERE package_name=?"
        val args=if(packageFilter==null) emptyArray() else arrayOf(packageFilter)
        val out=mutableListOf<SessionExperiment>()
        helper.readableDatabase.rawQuery("""
            SELECT experiment_id,package_name,name,hypothesis,label_a,label_b,created_at_ms
            FROM session_experiments
        """.trimIndent()+where+" ORDER BY created_at_ms DESC LIMIT 100",args).use { c ->
            while(c.moveToNext()) out+=SessionExperiment(
                c.getString(0),c.getString(1),c.getString(2),c.getString(3),
                c.getString(4),c.getString(5),c.getLong(6)
            )
        }
        return out
    }

    fun experiment(id:String):SessionExperiment? =
        helper.readableDatabase.rawQuery("""
            SELECT experiment_id,package_name,name,hypothesis,label_a,label_b,created_at_ms
            FROM session_experiments WHERE experiment_id=?
        """.trimIndent(),arrayOf(id)).use { c ->
            if(c.moveToFirst()) SessionExperiment(c.getString(0),c.getString(1),
                c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6))
            else null
        }

    fun assign(experimentId:String,sessionId:String,arm:String,
               now: Long = System.currentTimeMillis()) {
        require(arm=="A" || arm=="B")
        require(now>0)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            val experiment=experiment(experimentId) ?: error("Experiment not found")
            val owner=sessionPackage(db,sessionId) ?: error("Session not found")
            require(experiment.packageName==owner) { "Sessions must belong to the same app" }
            val already=db.rawQuery("""
                SELECT 1 FROM session_experiment_members
                WHERE experiment_id=? AND session_id=?
            """.trimIndent(),arrayOf(experimentId,sessionId)).use { it.moveToFirst() }
            val count=db.rawQuery("SELECT COUNT(*) FROM session_experiment_members WHERE experiment_id=?",
                arrayOf(experimentId)).use { if(it.moveToFirst()) it.getInt(0) else 0 }
            require(already || count<MAX_TRIALS) { "Maximum 30 trials per experiment" }
            db.insertWithOnConflict("session_experiment_members",null,ContentValues().apply {
                put("experiment_id",experimentId);put("session_id",sessionId)
                put("arm",arm);put("assigned_at_ms",now)
            },SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun unassign(experimentId:String,sessionId:String):Boolean =
        helper.writableDatabase.delete("session_experiment_members",
            "experiment_id=? AND session_id=?",arrayOf(experimentId,sessionId))>0

    private fun sessionPackage(db: SQLiteDatabase,id:String):String? =
        db.rawQuery("SELECT package_name FROM game_sessions WHERE session_id=?",
            arrayOf(id)).use { if(it.moveToFirst()) it.getString(0) else null }

    private fun sampleCount(id:String):Int =
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM game_samples WHERE session_id=?",
            arrayOf(id)).use { if(it.moveToFirst()) it.getInt(0) else 0 }

    private fun sampleEvidence(repository:GameSessionRepository,header:GameSessionHeader): SessionEvidence {
        val points=repository.points(header.id,MAX_SAMPLES_PER_TRIAL)
        val result=ExperimentAnalytics.evidence(header,points)
        val total=sampleCount(header.id)
        return if(total>points.size) result.copy(
            dischargePercentPerHour=null,
            qualityNotes=result.qualityNotes+(
                "История превышает лимит 10 000 точек; анализ частичный, разряд не сравнивается.")
        ) else result
    }

    fun evidence(id:String):SessionEvidence {
        GameSessionRepository(app).use { repository ->
            val header=repository.header(id) ?: error("Session not found")
            return sampleEvidence(repository,header)
        }
    }

    fun trials(experimentId:String):List<ExperimentTrial> {
        val experiment=experiment(experimentId) ?: error("Experiment not found")
        val assigned=mutableListOf<Pair<String,String>>()
        helper.readableDatabase.rawQuery("""
            SELECT session_id,arm FROM session_experiment_members
            WHERE experiment_id=? ORDER BY assigned_at_ms ASC,session_id ASC LIMIT 31
        """.trimIndent(),arrayOf(experimentId)).use { c ->
            while(c.moveToNext()) assigned+=c.getString(0) to c.getString(1)
        }
        check(assigned.size<=MAX_TRIALS)
        GameSessionRepository(app).use { records ->
            return assigned.mapNotNull { (sessionId,arm) ->
                val header=records.header(sessionId)
                if(header==null || header.packageName!=experiment.packageName) null
                else {
                    val note=getAnnotation(sessionId)
                    ExperimentTrial(arm,header,note?.note.orEmpty(),note?.tag.orEmpty(),
                        sampleEvidence(records,header))
                }
            }
        }
    }

    fun report(id:String):String {
        val exp=experiment(id) ?: error("Experiment not found")
        val trials=trials(id)
        return ExperimentAnalytics.formatComparison(
            exp.name,exp.hypothesis,
            ExperimentAnalytics.armSummary("A",exp.labelA,trials),
            ExperimentAnalytics.armSummary("B",exp.labelB,trials)
        )
    }

    fun exportCsv(id:String,writer:Writer) {
        val exp=experiment(id) ?: error("Experiment not found")
        val trials=trials(id)
        writer.write("experiment_id,experiment_name,package_name,arm,arm_label,session_id,started_at_ms,end_reason,tag,note,samples,duration_min,observed_min,battery_first_percent,battery_last_percent,discharge_pp_per_hour,battery_peak_c,thermal_severe_samples,power_save_samples,unvalidated_network_samples,network_transitions,gaps_over_45s,quality_notes\n")
        for(t in trials) {
            val e=t.evidence
            val values=listOf(exp.id,exp.name,exp.packageName,t.arm,
                if(t.arm=="A")exp.labelA else exp.labelB,
                t.session.id,t.session.startedAtMs.toString(),t.session.endReason.orEmpty(),
                t.tag,t.note,e.samples.toString(),
                e.sessionMinutes?.let { ExperimentAnalytics.formatNumber(it) }.orEmpty(),
                e.observedMinutes?.let { ExperimentAnalytics.formatNumber(it) }.orEmpty(),
                e.firstPercent?.toString().orEmpty(),e.lastPercent?.toString().orEmpty(),
                e.dischargePercentPerHour?.let { ExperimentAnalytics.formatNumber(it) }.orEmpty(),
                e.peakBatteryTempDeciC?.let { ExperimentAnalytics.formatNumber(it/10.0) }.orEmpty(),
                e.severeThermalReadings.toString(),e.powerSaveReadings.toString(),
                e.networkUnvalidatedReadings.toString(),e.networkTransitions.toString(),
                e.gapsOver45s.toString(),e.qualityNotes.joinToString(" | "))
            writer.write(values.joinToString(",") { csvSafe(it) }+"\n")
        }
    }

    private fun csvSafe(value: String): String = CsvEscaper.cell(value)

    override fun close() { helper.close() }
}
