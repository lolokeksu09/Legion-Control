package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class LegionBackupPreview(val tables: Int, val rows: Long, val schema: Int,
                               val settings: Int)

/**
 * Local, portable recovery for all v12 SQLite tables plus non-secret user preferences.
 * The backup has no videos, screenshots, keystore or cloud storage.
 *
 * Restore NEVER overwrites a populated database. The user must choose a clean
 * installation and explicitly confirm after archive validation.
 */
object LegionDataBackup {
    private const val MAX_DB = 256L * 1024L * 1024L
    private const val MAX_PREFS = 512 * 1024
    private const val MAX_META = 16 * 1024
    private const val MAX_ROWS = 2_000_000L
    private val names = listOf(
        "legion-game-capture-profiles", "legion-capture-studio",
        "legion-launch-profiles-v1", ToolboxPreferences.NAME
    )
    private val nameRule = Regex("[A-Za-z][A-Za-z0-9_]{0,79}")
    private val allowedEntries = setOf("manifest.json", "preferences.json", "history.sqlite")

    private data class Staged(val sqlite: File, val manifest: JSONObject,
                              val preferences: Map<String, Map<String, Any>>)

    fun export(context: Context, uri: Uri): LegionBackupPreview {
        check(!GameSessionRecorderService.sessionActive) {
            "Остановите запись игровой сессии перед созданием архива."
        }
        check(CaptureCoordinator.reserveConsent(CaptureMode.MAINTENANCE)) {
            "Завершите MediaProjection перед резервным копированием"
        }
        val tmp = newStagingFile(context)
        try {
            val prefs = preferencesJson(context)
            LocalHistoryStore(context).use { helper ->
                helper.readableDatabase.execSQL("VACUUM INTO ?", arrayOf(tmp.absolutePath))
            }
            check(tmp.isFile && tmp.length() in 1..MAX_DB) { "Архив истории слишком велик" }
            val sqliteHash = sha256(tmp)
            val prefHash = sha256(prefs)
            val preview = inspectSQLite(context, tmp)
            val manifest = JSONObject()
                .put("format", "legion-control-local-backup")
                .put("version", 1)
                .put("package", context.packageName)
                .put("schema", HapticLabSchema.VERSION)
                .put("database_sha256", sqliteHash)
                .put("preferences_sha256", prefHash)
                .put("created_at_ms", System.currentTimeMillis())
            val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                "SAF отказал в записи резервной копии"
            }
            ZipOutputStream(output).use { zip ->
                putEntry(zip, "manifest.json", manifest.toString().toByteArray(Charsets.UTF_8))
                putEntry(zip, "preferences.json", prefs)
                zip.putNextEntry(ZipEntry("history.sqlite"))
                FileInputStream(tmp).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            return preview.copy(settings = countSettings(parsePreferences(prefs)))
        } finally {
            tmp.delete()
            CaptureCoordinator.release(CaptureMode.MAINTENANCE)
        }
    }

    fun preview(context: Context, uri: Uri): LegionBackupPreview =
        withStaged(context, uri) { stage ->
            inspectSQLite(context, stage.sqlite)
                .copy(settings = countSettings(stage.preferences))
        }

    fun restoreOnEmptyDatabase(context: Context, uri: Uri): LegionBackupPreview {
        check(!GameSessionRecorderService.sessionActive) {
            "Сначала остановите запись игровой сессии."
        }
        check(!context.getSharedPreferences("legion-launch-profiles-v1",
            Context.MODE_PRIVATE).contains("pending_global_undo")) {
            "Сначала верните системные настройки игрового профиля."
        }
        check(CaptureCoordinator.reserveConsent(CaptureMode.MAINTENANCE)) {
            "Завершите захват и повторите восстановление"
        }
        try {
            check(!GameSessionRecorderService.sessionActive)
            return withStaged(context, uri) { staged ->
                val preview = inspectSQLite(context, staged.sqlite)
                    .copy(settings = countSettings(staged.preferences))
                val previous = parsePreferences(preferencesJson(context))
                val source = SQLiteDatabase.openDatabase(staged.sqlite.absolutePath, null,
                    SQLiteDatabase.OPEN_READONLY)
                try {
                    LocalHistoryStore(context).use { helper ->
                        val target = helper.writableDatabase
                        val tables = tableOrder(target)
                        var settingsChanged = false
                        try {
                            target.beginTransaction()
                            try {
                                // Check every existing table BEFORE inserting a single row.
                                // SQLite rollback protects this entire transaction.
                                val existingCounts = tables.map { table ->
                                    target.rawQuery("SELECT COUNT(*) FROM " + sqlName(table),
                                        null).use {
                                        check(it.moveToFirst())
                                        it.getLong(0)
                                    }
                                }
                                check(BackupRestorePolicy.acceptsEmptyTarget(existingCounts)) {
                                    "База содержит пользовательские данные. Импорт разрешён только в пустую базу."
                                }
                                for (table in tables) {
                                    source.rawQuery("SELECT * FROM " + sqlName(table), null)
                                        .use { cursor ->
                                            var count = 0L
                                            while (cursor.moveToNext()) {
                                                check(++count <= MAX_ROWS) { "Слишком много строк" }
                                                val values = ContentValues()
                                                for (i in 0 until cursor.columnCount) {
                                                    val col = cursor.getColumnName(i)
                                                    when (cursor.getType(i)) {
                                                        Cursor.FIELD_TYPE_NULL -> values.putNull(col)
                                                        Cursor.FIELD_TYPE_INTEGER ->
                                                            values.put(col, cursor.getLong(i))
                                                        Cursor.FIELD_TYPE_FLOAT ->
                                                            values.put(col, cursor.getDouble(i))
                                                        Cursor.FIELD_TYPE_STRING ->
                                                            values.put(col, cursor.getString(i))
                                                        Cursor.FIELD_TYPE_BLOB -> {
                                                            val bytes = cursor.getBlob(i)
                                                            check(bytes.size <= 4 * 1024 * 1024)
                                                            values.put(col, bytes)
                                                        }
                                                        else -> error("Unsupported SQLite type")
                                                    }
                                                }
                                                target.insertOrThrow(table, null, values)
                                            }
                                        }
                                }
                                // Mark the attempt before modifying any preference file:
                                // an error on the second file must roll back the first.
                                settingsChanged = true
                                applyPreferences(context, staged.preferences)
                                target.setTransactionSuccessful()
                            } finally {
                                target.endTransaction()
                            }
                        } catch (failure: Exception) {
                            if (settingsChanged) runCatching {
                                applyPreferences(context, previous)
                            }
                            throw failure
                        }
                    }
                } finally {
                    source.close()
                }
                preview
            }
        } finally {
            CaptureCoordinator.release(CaptureMode.MAINTENANCE)
        }
    }

    private fun <T> withStaged(context: Context, uri: Uri, body: (Staged) -> T): T {
        val file = newStagingFile(context)
        try {
            val raw = mutableMapOf<String, ByteArray>()
            val seen = mutableSetOf<String>()
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "SAF отказал в чтении архива"
            }
            ZipInputStream(input).use { zip ->
                val block = ByteArray(8192)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in allowedEntries &&
                        seen.add(entry.name)) { "Некорректная структура backup ZIP" }
                    val limit = when (entry.name) {
                        "history.sqlite" -> MAX_DB
                        "preferences.json" -> MAX_PREFS.toLong()
                        else -> MAX_META.toLong()
                    }
                    var total = 0L
                    val output = if (entry.name == "history.sqlite")
                        FileOutputStream(file) else ByteArrayOutputStream()
                    output.use {
                        while (true) {
                            val n = zip.read(block)
                            if (n < 0) break
                            if (n == 0) continue
                            total += n
                            require(total <= limit) { "Backup entry exceeds size limit" }
                            it.write(block, 0, n)
                        }
                        if (it is ByteArrayOutputStream) raw[entry.name] = it.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
            require(seen == allowedEntries && file.length() in 1..MAX_DB)
            val manifestBytes = requireNotNull(raw["manifest.json"])
            val preferences = requireNotNull(raw["preferences.json"])
            val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
            require(manifest.getString("format") == "legion-control-local-backup" &&
                manifest.getInt("version") == 1 && manifest.getString("package") ==
                context.packageName && manifest.getInt("schema") == HapticLabSchema.VERSION) {
                "Несовместимая резервная копия"
            }
            require(manifest.getString("database_sha256") == sha256(file) &&
                manifest.getString("preferences_sha256") == sha256(preferences)) {
                "Контрольная сумма архива не совпадает"
            }
            return body(Staged(file, manifest, parsePreferences(preferences)))
        } finally {
            file.delete()
        }
    }

    private fun inspectSQLite(context: Context, file: File): LegionBackupPreview {
        val source = SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY)
        try {
            check(source.version == HapticLabSchema.VERSION) { "Неподдерживаемая версия SQLite" }
            source.rawQuery("PRAGMA quick_check", null).use {
                check(it.moveToFirst() && it.getString(0) == "ok") {
                    "Архив SQLite повреждён"
                }
            }
            LocalHistoryStore(context).use { helper ->
                val expected = helper.readableDatabase
                val tables = allTables(expected)
                check(allTables(source) == tables) { "Неполный набор таблиц" }
                var rows = 0L
                for (name in tables) {
                    check(columns(source, name) == columns(expected, name)) {
                        "Несовместимая структура таблицы " + name
                    }
                    source.rawQuery("SELECT COUNT(*) FROM " + sqlName(name), null).use {
                        check(it.moveToFirst())
                        rows += it.getLong(0)
                        check(rows <= MAX_ROWS) { "Слишком много записей" }
                    }
                }
                return LegionBackupPreview(tables.size, rows, source.version, 0)
            }
        } finally {
            source.close()
        }
    }

    private fun allTables(db: SQLiteDatabase): List<String> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND " +
            "name NOT LIKE 'sqlite_%' ORDER BY name", null).use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(0).also {
                    check(nameRule.matches(it)) { "Недопустимое имя таблицы" }
                })
            }
        }

    private fun columns(db: SQLiteDatabase, table: String): List<String> =
        db.rawQuery("PRAGMA table_info(" + sqlName(table) + ")", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.getString(1) + ":" + c.getString(2) + ":" + c.getInt(3) +
                        ":" + c.getInt(5))
                }
            }
        }

    private fun tableOrder(db: SQLiteDatabase): List<String> {
        val all = allTables(db)
        val remaining = all.toMutableSet()
        val ordered = ArrayList<String>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { table ->
                db.rawQuery("PRAGMA foreign_key_list(" + sqlName(table) + ")", null)
                    .use { c ->
                        var blocked = false
                        while (c.moveToNext()) if (c.getString(2) in remaining)
                            blocked = true
                        !blocked
                    }
            }
            check(ready.isNotEmpty()) { "Цикл внешних ключей в схеме" }
            ordered.addAll(ready)
            remaining.removeAll(ready.toSet())
        }
        return ordered
    }

    private fun sqlName(value: String): String {
        require(nameRule.matches(value))
        return "\\"" + value + "\\""
    }

    private fun preferencesJson(context: Context): ByteArray {
        val root = JSONObject()
        for (name in names) {
            val values = JSONObject()
            for ((key, value) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                if (key == "pending_global_undo" || key == "last_result" ||
                    key == "replay_status") continue
                require(key.length in 1..150)
                val wrapped = when (value) {
                    is String -> JSONObject().put("type", "string").put("value", value)
                    is Int -> JSONObject().put("type", "int").put("value", value)
                    is Long -> JSONObject().put("type", "long").put("value", value)
                    is Boolean -> JSONObject().put("type", "bool").put("value", value)
                    is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
                    else -> null
                }
                if (wrapped != null) values.put(key, wrapped)
            }
            root.put(name, values)
        }
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_PREFS)
        return bytes
    }

    private fun parsePreferences(bytes: ByteArray): Map<String, Map<String, Any>> {
        require(bytes.size <= MAX_PREFS)
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        require(root.keys().asSequence().toSet() == names.toSet())
        return names.associateWith { name ->
            val obj = root.getJSONObject(name)
            require(obj.length() <= 500)
            obj.keys().asSequence().associateWith { key ->
                require(key.length in 1..150 && key != "pending_global_undo" &&
                    key != "last_result" && key != "replay_status")
                val wrapped = obj.getJSONObject(key)
                require(wrapped.keys().asSequence().toSet() == setOf("type", "value"))
                when (wrapped.getString("type")) {
                    "string" -> wrapped.getString("value").also { require(it.length <= 100_000) }
                    "int" -> wrapped.getInt("value")
                    "long" -> wrapped.getLong("value")
                    "bool" -> wrapped.getBoolean("value")
                    "float" -> wrapped.getDouble("value").toFloat().also {
                        require(it.isFinite())
                    }
                    else -> error("Unknown setting type")
                }
            }
        }
    }

    private fun countSettings(data: Map<String, Map<String, Any>>) =
        data.values.sumOf { it.size }

    private fun applyPreferences(context: Context, values: Map<String, Map<String, Any>>) {
        for (name in names) {
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear()
            for ((key, value) in values.getValue(name)) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            check(editor.commit()) { "Не удалось сохранить настройки " + name }
        }
    }

    private fun putEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun newStagingFile(context: Context): File =
        File.createTempFile("legion-db-", ".sqlite", context.cacheDir).also {
            check(it.delete()) { "Невозможно подготовить временный SQLite" }
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val block = ByteArray(8192)
            while (true) {
                val n = input.read(block)
                if (n < 0) break
                digest.update(block, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
