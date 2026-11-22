package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

data class SmartProfile(
    val packageName: String,
    val label: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val hotDeciC: Int,
    val lowPercent: Int,
    val tcpOptIn: Boolean
)

data class SmartEvent(
    val timeMs: Long, val packageName: String, val kind: String, val details: String
)

/**
 * All calls go on an app-controlled executor. No cloud or hidden import.
 * Backup contains profiles/rules only; old game session telemetry stays untouched.
 */
class SmartProfilesRepository(context: Context) : AutoCloseable {
    private val helper = LocalHistoryStore(context.applicationContext)
    companion object {
        private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        private const val MAX_BACKUP = 512 * 1024
        private const val MAX_PROFILES = 100
        fun validatePackage(s: String): Boolean = s.length <= 200 && PACKAGE.matches(s)
    }

    fun list(): List<SmartProfile> {
        val out = mutableListOf<SmartProfile>()
        helper.readableDatabase.rawQuery("""
            SELECT package_name,label,created_at_ms,updated_at_ms,
              battery_hot_deci_c,battery_low_percent,tcp_opt_in
            FROM smart_profiles ORDER BY updated_at_ms DESC
        """.trimIndent(), null).use { c ->
            while(c.moveToNext()) out += SmartProfile(c.getString(0),c.getString(1),c.getLong(2),
                c.getLong(3),c.getInt(4),c.getInt(5),c.getInt(6)==1)
        }
        return out
    }

    fun get(pkg: String): SmartProfile? = helper.readableDatabase.rawQuery("""
        SELECT package_name,label,created_at_ms,updated_at_ms,
          battery_hot_deci_c,battery_low_percent,tcp_opt_in
        FROM smart_profiles WHERE package_name=?
    """.trimIndent(),arrayOf(pkg)).use { c ->
        if(!c.moveToFirst()) null else SmartProfile(c.getString(0),c.getString(1),
            c.getLong(2),c.getLong(3),c.getInt(4),c.getInt(5),c.getInt(6)==1)
    }

    fun save(pkg: String, label: String, hot: Int = 420, low: Int = 20,
             tcp: Boolean = false, now: Long = System.currentTimeMillis()) {
        require(validatePackage(pkg))
        require(label.isNotBlank() && label.length <= 100)
        require(hot in 300..550 && low in 5..80 && now>0)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            saveInTransaction(db,pkg,label,hot,low,tcp,now)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun saveInTransaction(db: SQLiteDatabase, pkg: String, label: String,
                                  hot: Int, low: Int, tcp: Boolean, now: Long) {
        val existing=db.rawQuery("SELECT created_at_ms FROM smart_profiles WHERE package_name=?",
            arrayOf(pkg)).use { c -> if(c.moveToFirst()) c.getLong(0) else null }
        if(existing==null) {
            db.insertOrThrow("smart_profiles",null,ContentValues().apply {
                put("package_name",pkg)
                put("label",label)
                put("created_at_ms",now)
                put("updated_at_ms",now)
                put("battery_hot_deci_c",hot)
                put("battery_low_percent",low)
                put("tcp_opt_in",if(tcp) 1 else 0)
            })
        } else db.update("smart_profiles",ContentValues().apply {
            put("label",label)
            put("updated_at_ms",maxOf(now,existing))
            put("battery_hot_deci_c",hot)
            put("battery_low_percent",low)
            put("tcp_opt_in",if(tcp) 1 else 0)
        },"package_name=?",arrayOf(pkg))
        SmartRuleKind.entries.forEach { kind ->
            db.insertWithOnConflict("smart_rules",null,ContentValues().apply {
                put("package_name",pkg)
                put("kind",kind.name)
                put("threshold",when(kind) {
                    SmartRuleKind.BATTERY_HOT -> hot
                    SmartRuleKind.BATTERY_LOW -> low
                    else -> kind.defaultThreshold
                })
                put("cooldown_ms",120_000)
                put("enabled",1)
            },SQLiteDatabase.CONFLICT_IGNORE)
        }
        for ((kind,threshold) in listOf(SmartRuleKind.BATTERY_HOT to hot,
            SmartRuleKind.BATTERY_LOW to low)) {
            db.update("smart_rules",ContentValues().apply { put("threshold",threshold) },
                "package_name=? AND kind=?",arrayOf(pkg,kind.name))
        }
    }

    fun delete(pkg: String): Boolean = helper.writableDatabase.delete(
        "smart_profiles","package_name=?",arrayOf(pkg))>0

    fun rules(pkg: String): List<SmartRule> {
        val out=mutableListOf<SmartRule>()
        helper.readableDatabase.rawQuery("""
            SELECT rule_id,package_name,kind,threshold,cooldown_ms,enabled
            FROM smart_rules WHERE package_name=? ORDER BY rule_id
        """.trimIndent(),arrayOf(pkg)).use { c ->
            while(c.moveToNext()) {
                val kind=runCatching { SmartRuleKind.valueOf(c.getString(2)) }.getOrNull()
                if(kind!=null) out += SmartRule(c.getLong(0),c.getString(1),
                    kind,c.getInt(3),c.getLong(4),c.getInt(5)==1)
            }
        }
        return out
    }

    fun setRule(pkg: String, kind: SmartRuleKind, enabled: Boolean,
                cooldownMs: Long = 120_000): Boolean {
        require(cooldownMs in 30_000..3_600_000)
        val changed=helper.writableDatabase.update("smart_rules",ContentValues().apply {
            put("enabled",if(enabled) 1 else 0)
            put("cooldown_ms",cooldownMs)
        },"package_name=? AND kind=?",arrayOf(pkg,kind.name))
        return changed>0
    }

    fun logAlert(sessionId: String, pkg: String, alert: SmartRuleAlert) {
        helper.writableDatabase.insertOrThrow("smart_rule_events",null,ContentValues().apply {
            put("session_id",sessionId)
            put("package_name",pkg.take(200))
            put("kind",alert.kind.name)
            put("recorded_at_ms",alert.timeMs)
            put("details",alert.description.take(300))
        })
    }

    fun events(limit: Int = 25): List<SmartEvent> {
        val out=mutableListOf<SmartEvent>()
        helper.readableDatabase.rawQuery("""
            SELECT recorded_at_ms,package_name,kind,details FROM smart_rule_events
            ORDER BY recorded_at_ms DESC,id DESC LIMIT ?
        """.trimIndent(),arrayOf(limit.coerceIn(1,100).toString())).use { c ->
            while(c.moveToNext()) out += SmartEvent(c.getLong(0),c.getString(1),
                c.getString(2),c.getString(3))
        }
        return out
    }

    fun writeBackup(output: OutputStream) {
        val arr=JSONArray()
        for(profile in list().take(MAX_PROFILES)) {
            val obj=JSONObject().put("package",profile.packageName).put("label",profile.label)
                .put("hot",profile.hotDeciC).put("low",profile.lowPercent)
                .put("tcp",profile.tcpOptIn)
            val rules=JSONArray()
            for(rule in rules(profile.packageName)) rules.put(JSONObject()
                .put("kind",rule.kind.name).put("enabled",rule.enabled)
                .put("cooldown",rule.cooldownMs))
            arr.put(obj.put("rules",rules))
        }
        val bytes=JSONObject().put("format","legion-smart-profiles")
            .put("version",1).put("profiles",arr).toString(2).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size<=MAX_BACKUP) { "Backup too large" }
        output.write(bytes)
    }

    /** Read and validate completely BEFORE beginning one atomic transaction. */
    fun importBackup(input: InputStream): Int {
        val buffer=ByteArray(MAX_BACKUP+1)
        var size=0
        while(size<buffer.size) {
            val n=input.read(buffer,size,buffer.size-size)
            if(n<0) break
            if(n==0) continue
            size+=n
        }
        require(size<=MAX_BACKUP) { "Backup exceeds 512 KiB" }
        val json=JSONObject(String(buffer,0,size,StandardCharsets.UTF_8))
        require(json.getString("format")=="legion-smart-profiles" && json.getInt("version")==1)
        val profiles=json.getJSONArray("profiles")
        require(profiles.length()<=MAX_PROFILES)
        data class ImportRow(val pkg:String,val label:String,val hot:Int,val low:Int,
                             val tcp:Boolean,val rules:List<Triple<SmartRuleKind,Boolean,Long>>)
        val parsed=mutableListOf<ImportRow>()
        val names=mutableSetOf<String>()
        for(i in 0 until profiles.length()) {
            val p=profiles.getJSONObject(i)
            val pkg=p.getString("package")
            val label=p.getString("label")
            val hot=p.getInt("hot")
            val low=p.getInt("low")
            require(validatePackage(pkg) && label.isNotBlank() && label.length<=100)
            require(hot in 300..550 && low in 5..80 && names.add(pkg))
            val rr=p.getJSONArray("rules")
            require(rr.length()<=SmartRuleKind.entries.size)
            val kinds=mutableSetOf<SmartRuleKind>()
            val parsedRules=mutableListOf<Triple<SmartRuleKind,Boolean,Long>>()
            for(j in 0 until rr.length()) {
                val item=rr.getJSONObject(j)
                val kind=SmartRuleKind.valueOf(item.getString("kind"))
                val cooldown=item.getLong("cooldown")
                require(kinds.add(kind) && cooldown in 30_000..3_600_000)
                parsedRules+=Triple(kind,item.getBoolean("enabled"),cooldown)
            }
            parsed+=ImportRow(pkg,label,hot,low,p.getBoolean("tcp"),parsedRules)
        }
        val now=System.currentTimeMillis().coerceAtLeast(1)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            for(p in parsed) {
                saveInTransaction(db,p.pkg,p.label,p.hot,p.low,p.tcp,now)
                for((kind,enabled,cooldown) in p.rules) {
                    db.update("smart_rules",ContentValues().apply {
                        put("enabled",if(enabled) 1 else 0)
                        put("cooldown_ms",cooldown)
                    },"package_name=? AND kind=?",arrayOf(p.pkg,kind.name))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return parsed.size
    }

    override fun close() { helper.close() }
}
