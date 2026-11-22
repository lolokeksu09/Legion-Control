package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Writer

data class TrackedApplication(
    val packageName:String,val alias:String,val category:String,val note:String,
    val favorite:Boolean,val createdAtMs:Long,val updatedAtMs:Long,
    val lastSeenAtMs:Long?,val lastSeenVersionCode:Long?,val lastSeenUpdateMs:Long?,
    val lastSeenStatus:String
)

data class AppAuditEvent(
    val packageName:String,val recordedAtMs:Long,val kind:String,val details:String
)

/** Local opt-in library and explicit audit history; all operations run off UI. */
class ApplicationCenterRepository(context:Context):AutoCloseable {
    private val helper=LocalHistoryStore(context.applicationContext)
    private val MAX_ITEMS=150

    fun list():List<TrackedApplication> {
        val output=mutableListOf<TrackedApplication>()
        helper.readableDatabase.rawQuery("""
            SELECT package_name,alias,category,note,favorite,created_at_ms,
                   updated_at_ms,last_seen_at_ms,last_seen_version_code,
                   last_seen_update_ms,last_seen_status
            FROM app_library ORDER BY favorite DESC,updated_at_ms DESC,package_name ASC LIMIT 150
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) output+=TrackedApplication(c.getString(0),c.getString(1),
                c.getString(2),c.getString(3),c.getInt(4)==1,c.getLong(5),c.getLong(6),
                if(c.isNull(7))null else c.getLong(7),if(c.isNull(8))null else c.getLong(8),
                if(c.isNull(9))null else c.getLong(9),c.getString(10))
        }
        return output
    }

    fun get(pkg:String):TrackedApplication? {
        require(ApplicationInventory.isPackageName(pkg))
        return list().firstOrNull { it.packageName==pkg }
    }

    fun save(pkg:String,alias:String,category:String,note:String,favorite:Boolean,
             now:Long=System.currentTimeMillis()) {
        require(ApplicationInventory.isPackageName(pkg))
        require(alias.length<=100 && category.length<=40 && note.length<=1000 && now>0)
        require(!alias.contains('\u0000') && !category.contains('\u0000') && !note.contains('\u0000'))
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            val existing=db.rawQuery("SELECT created_at_ms FROM app_library WHERE package_name=?",
                arrayOf(pkg)).use { if(it.moveToFirst())it.getLong(0) else null }
            if(existing==null) {
                val count=db.rawQuery("SELECT COUNT(*) FROM app_library",null).use {
                    if(it.moveToFirst())it.getInt(0) else 0
                }
                require(count<MAX_ITEMS) { "Лимит библиотеки: 150 приложений" }
                db.insertOrThrow("app_library",null,ContentValues().apply {
                    put("package_name",pkg)
                    put("alias",alias)
                    put("category",category)
                    put("note",note)
                    put("favorite",if(favorite)1 else 0)
                    put("created_at_ms",now)
                    put("updated_at_ms",now)
                    put("last_seen_status","UNKNOWN")
                })
            } else {
                db.update("app_library",ContentValues().apply {
                    put("alias",alias)
                    put("category",category)
                    put("note",note)
                    put("favorite",if(favorite)1 else 0)
                    put("updated_at_ms",maxOf(now,existing))
                },"package_name=?",arrayOf(pkg))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun remove(pkg:String):Boolean {
        require(ApplicationInventory.isPackageName(pkg))
        return helper.writableDatabase.delete("app_library","package_name=?",arrayOf(pkg))>0
    }

    fun audit(pkg:String,observed:AppPackageObservation,now:Long=System.currentTimeMillis()):AppObservation {
        require(ApplicationInventory.isPackageName(pkg) && observed.packageName==pkg && now>0)
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            val previous=db.rawQuery("""
                SELECT last_seen_status,last_seen_version_code,last_seen_update_ms
                FROM app_library WHERE package_name=?
            """.trimIndent(),arrayOf(pkg)).use { c ->
                require(c.moveToFirst()) { "Сначала добавьте приложение в библиотеку" }
                when(c.getString(0)) {
                    "VISIBLE" -> AppFingerprint(true,
                        if(c.isNull(1))null else c.getLong(1),
                        if(c.isNull(2))null else c.getLong(2))
                    "UNAVAILABLE" -> AppFingerprint(false,null,null)
                    else -> null
                }
            }
            val decision=ApplicationAuditRules.classify(previous,observed.fingerprint)
            db.update("app_library",ContentValues().apply {
                put("last_seen_at_ms",now)
                put("last_seen_status",if(observed.visible)"VISIBLE" else "UNAVAILABLE")
                if(observed.visible) {
                    if(observed.versionCode!=null)put("last_seen_version_code",observed.versionCode)
                    else putNull("last_seen_version_code")
                    if(observed.updatedAtMs!=null)put("last_seen_update_ms",observed.updatedAtMs)
                    else putNull("last_seen_update_ms")
                } else {
                    putNull("last_seen_version_code")
                    putNull("last_seen_update_ms")
                }
            },"package_name=?",arrayOf(pkg))
            decision.event?.let { event ->
                db.insertOrThrow("app_audit_events",null,ContentValues().apply {
                    put("package_name",pkg)
                    put("recorded_at_ms",now)
                    put("kind",event.name)
                    put("details",decision.description.take(300))
                })
            }
            db.setTransactionSuccessful()
            return decision
        } finally { db.endTransaction() }
    }

    fun events(pkg:String?=null,limit:Int=80):List<AppAuditEvent> {
        require(pkg==null || ApplicationInventory.isPackageName(pkg))
        val sql="""
            SELECT package_name,recorded_at_ms,kind,details
            FROM app_audit_events
        """.trimIndent()+(if(pkg==null)"" else " WHERE package_name=?")+
            " ORDER BY recorded_at_ms DESC,id DESC LIMIT ?"
        val args=if(pkg==null) arrayOf(limit.coerceIn(1,100).toString())
            else arrayOf(pkg,limit.coerceIn(1,100).toString())
        val output=mutableListOf<AppAuditEvent>()
        helper.readableDatabase.rawQuery(sql,args).use { c ->
            while(c.moveToNext()) output+=AppAuditEvent(
                c.getString(0),c.getLong(1),c.getString(2),c.getString(3))
        }
        return output
    }

    fun writeCsv(writer:Writer) {
        writer.write("package_name,alias,category,note,favorite,tracked_at_ms,updated_at_ms,last_seen_at_ms,last_seen_version_code,last_seen_update_ms,last_seen_status\n")
        helper.readableDatabase.rawQuery("""
            SELECT package_name,alias,category,note,favorite,created_at_ms,
                updated_at_ms,last_seen_at_ms,last_seen_version_code,
                last_seen_update_ms,last_seen_status
            FROM app_library ORDER BY package_name
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) writer.write((0 until c.columnCount).joinToString(",") { index ->
                if(c.isNull(index))"" else ApplicationAuditRules.csvCell(c.getString(index))
            }+"\n")
        }
        writer.write("\npackage_name,recorded_at_ms,event_kind,details\n")
        helper.readableDatabase.rawQuery("""
            SELECT package_name,recorded_at_ms,kind,details FROM app_audit_events
            ORDER BY recorded_at_ms,id
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) writer.write((0 until c.columnCount).joinToString(",") { index ->
                if(c.isNull(index))"" else ApplicationAuditRules.csvCell(c.getString(index))
            }+"\n")
        }
    }

    override fun close(){helper.close()}
}
