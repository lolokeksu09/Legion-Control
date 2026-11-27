package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.AtomicFile
import java.security.SecureRandom
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
                               val settings: Int, val archiveVersion: Int = 2,
                               val identity: BackupArchiveIdentity? = null,
                               val networkRuns:Int=0,val batteryRuns:Int=0)

data class BackupArchiveIdentity(val version: Int, val databaseSha256: String,
                                 val preferencesSha256: String)

/**
 * Local recovery for all v12 SQLite tables, diagnostic archives and non-secret user preferences.
 * The backup has no videos, screenshots, keystore or cloud storage.
 *
 * Restore NEVER overwrites a populated database. The user must choose a clean
 * installation and explicitly confirm after archive validation.
 */
object LegionDataBackup {
    private const val MAX_DB = 256L * 1024L * 1024L
    private const val MAX_LEGACY_PREFS = 512 * 1024
    private const val MAX_PREFS = 4 * 1024 * 1024
    private const val MAX_META = 16 * 1024
    private const val MAX_ROWS = 2_000_000L
    private const val PENDING_JOURNAL = "legion-restore-pending-v1.json"
    @Volatile private var lastRecoveryFailure: String? = null

    /** A failed recovery is visible in the hub rather than being silently ignored. */
    fun recoveryWarning(): String? = lastRecoveryFailure?.let {
        "Восстановление локальных данных из ZIP не завершено ($it). Откройте «Настройки → Данные → Завершить восстановление». Не удаляйте данные приложения."
    }
    private val legacyNames = listOf(
        "legion-game-capture-profiles", "legion-capture-studio",
        "legion-launch-profiles-v1", ToolboxPreferences.NAME
    )
    private val names = legacyNames + PreflightPresets.STORE
    private const val FORMAT_VERSION = 3
    private fun namesFor(version: Int) = when(version) {
        1 -> legacyNames
        2, FORMAT_VERSION -> names
        else -> error("Unsupported backup version")
    }
    private val nameRule = Regex("[A-Za-z][A-Za-z0-9_]{0,79}")
    private val allowedEntries = setOf("manifest.json", "preferences.json", "history.sqlite")

    private data class Staged(val sqlite: File, val manifest: JSONObject,
                              val preferences: Map<String, Map<String, Any>>,
                              val preferenceBytes: ByteArray, val archives: DiagnosticBackupBundle?)

    private fun pendingFile(context: Context): AtomicFile =
        AtomicFile(File(context.filesDir, PENDING_JOURNAL))

    /** All auxiliary mutations share this monitor with the restore transaction/replay.
     * A pending journal must be recovered before ordinary save/delete can proceed. */
    internal fun hasPendingRestore(context:Context):Boolean {
        val f=pendingFile(context).baseFile
        return f.exists() || File(f.path+".bak").exists() || File(f.path+".new").exists()
    }
    internal fun checkArchiveWritesAllowed(context:Context) {
        check(!hasPendingRestore(context)) {
            "Сначала завершите восстановление в разделе «Настройки → Данные»."
        }
    }

    /** Bound memory before reading a damaged journal, not after allocating its contents. */
    internal fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        require(maxBytes > 0)
        val result = ByteArrayOutputStream()
        val block = ByteArray(minOf(8192, maxBytes))
        var remaining = maxBytes
        while (remaining > 0) {
            val n = input.read(block, 0, minOf(block.size, remaining))
            if (n < 0) return result.toByteArray()
            if (n == 0) {
                val one = input.read()
                if (one < 0) return result.toByteArray()
                result.write(one)
                remaining--
            } else {
                result.write(block, 0, n)
                remaining -= n
            }
        }
        require(input.read() == -1) { "Recovery journal exceeds size limit" }
        return result.toByteArray()
    }

    private fun identity(manifest: JSONObject) = BackupArchiveIdentity(
        manifest.getInt("version"), manifest.getString("database_sha256"),
        manifest.getString("preferences_sha256"))

    private fun sqliteMarker(db: SQLiteDatabase): Int =
        db.rawQuery("PRAGMA application_id", null).use { cursor ->
            check(cursor.moveToFirst()) { "SQLite application_id unavailable" }
            cursor.getInt(0)
        }

    /**
     * Durable preference intent precedes the SQLite transaction. No preferences
     * change until the same SQLite transaction commits its matching marker.
     */
    private fun savePending(context: Context, marker: Int, preferenceBytes: ByteArray, version: Int) {
        val journal = JSONObject()
            .put("version", version)
            .put("sqlite_marker", marker)
            .put("preferences_sha256", sha256(preferenceBytes))
            .put("preferences_json", String(preferenceBytes, Charsets.UTF_8))
            .toString().toByteArray(Charsets.UTF_8)
        require(journal.size <= MAX_PREFS * 2 + MAX_META) { "Recovery journal too large" }
        val atomic = pendingFile(context)
        val output = atomic.startWrite()
        try {
            output.write(journal)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        // AtomicFile can report a rename/sync failure through logging; require actual durable readback.
        check(atomic.openRead().use { readBounded(it, MAX_PREFS * 2 + MAX_META) }.contentEquals(journal)) {
            "Recovery journal readback failed"
        }
    }

    /**
     * Invoked before activities/services use preferences on every fresh process.
     * If SQLite rolled back, discard intent; if committed, replay all settings.
     * The atomic journal remains after a partial settings write, allowing retries.
     */
    @Synchronized fun recoverPendingRestore(context: Context): Boolean =
        ProfileMaintenanceGate.tryRun({error("Завершите изменение игрового профиля и повторите восстановление.")}) {
        val atomic = pendingFile(context)
        val rawFile = atomic.baseFile
        if (!rawFile.exists() && !File(rawFile.path + ".bak").exists()) {
            // An unpublished .new journal cannot authorize a main database commit.
            if(File(rawFile.path+".new").exists()) atomic.delete()
            lastRecoveryFailure = null
            return@tryRun false
        }
        try {
            val bytes = atomic.openRead().use { readBounded(it, MAX_PREFS * 2 + MAX_META) }
            require(bytes.size in 1..(MAX_PREFS * 2 + MAX_META))
            val entry = JSONObject(String(bytes, Charsets.UTF_8))
            val version = entry.getInt("version")
            namesFor(version)
            val marker = entry.getInt("sqlite_marker")
            require(marker > 0)
            val preferenceBytes = entry.getString("preferences_json").toByteArray(Charsets.UTF_8)
            require(sha256(preferenceBytes) == entry.getString("preferences_sha256"))
            val values = parsePreferences(preferenceBytes, version)
            val archives=DiagnosticBackupBundle.parse(context,preferenceBytes,version)
            val committed = LocalHistoryStore(context).use { helper ->
                sqliteMarker(helper.readableDatabase) == marker
            }
            if (committed) {
                check(GameLaunchProfiles.pendingPackage(context)==null) {
                    "Сначала выполните ручной возврат игрового профиля; журнал ZIP сохранён."
                }
                archives?.restore(context)
                applyPreferences(context, values)
            }
            atomic.delete()
            lastRecoveryFailure = null
            committed
        } catch (error: Exception) {
            lastRecoveryFailure = error.javaClass.simpleName
            throw error
        }
    }

    /** Explicit retry may replay settings, but it must not compete with running capture or a session. */
    @Synchronized internal fun retryPendingRestore(context: Context): Boolean {
        check(!GameToolboxService.isPanelActive()) { "Остановите Game Toolbox перед восстановлением." }
        check(!GameSessionRecorderService.sessionActive) { "Остановите запись игровой сессии." }
        check(CaptureCoordinator.reserveConsent(CaptureMode.MAINTENANCE)) {
            "Завершите захват и повторите восстановление."
        }
        return try {
            check(!GameSessionRecorderService.sessionActive) { "Остановите запись игровой сессии." }
            recoverPendingRestore(context)
        } finally {
            CaptureCoordinator.release(CaptureMode.MAINTENANCE)
        }
    }

    @Synchronized fun export(context: Context, uri: Uri): LegionBackupPreview =
        ProfileMaintenanceGate.tryRun({error("Завершите изменение игрового профиля и повторите экспорт ZIP.")}) {
        checkArchiveWritesAllowed(context)
        check(!context.getSharedPreferences("legion-launch-profiles-v1",Context.MODE_PRIVATE)
            .contains("pending_global_undo")) {
            "Сначала верните системные настройки игрового профиля перед созданием резервной копии."
        }
        check(!GameSessionRecorderService.sessionActive) {
            "Остановите запись игровой сессии перед созданием архива."
        }
        check(CaptureCoordinator.reserveConsent(CaptureMode.MAINTENANCE)) {
            "Завершите MediaProjection перед резервным копированием"
        }
        var temporary: File? = null
        try {
            val tmp = newStagingFile(context).also { temporary = it }
            val archives=DiagnosticBackupBundle.capture(context)
            val prefs = preferencesJson(context,archives)
            LocalHistoryStore(context).use { helper ->
                helper.readableDatabase.execSQL("VACUUM INTO ?", arrayOf(tmp.absolutePath))
            }
            check(tmp.isFile && tmp.length() in 1..MAX_DB) { "Архив истории слишком велик" }
            val sqliteHash = sha256(tmp)
            val prefHash = sha256(prefs)
            val preview = inspectSQLite(context, tmp)
            val manifest = JSONObject()
                .put("format", "legion-control-local-backup")
                .put("version", FORMAT_VERSION)
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
            preview.copy(settings = countSettings(parsePreferences(prefs, FORMAT_VERSION)),
                identity = identity(manifest), archiveVersion=FORMAT_VERSION,
                networkRuns=archives.network.size,batteryRuns=archives.battery.size)
        } finally {
            temporary?.delete()
            CaptureCoordinator.release(CaptureMode.MAINTENANCE)
        }
    }

    fun preview(context: Context, uri: Uri): LegionBackupPreview =
        withStaged(context, uri) { stage ->
            inspectSQLite(context, stage.sqlite)
                .copy(settings = countSettings(stage.preferences), archiveVersion = stage.manifest.getInt("version"),
                    identity = identity(stage.manifest),networkRuns=stage.archives?.network?.size ?: 0,
                    batteryRuns=stage.archives?.battery?.size ?: 0)
        }

    fun restoreOnEmptyDatabase(context: Context, uri: Uri,
                               confirmed: BackupArchiveIdentity): LegionBackupPreview =
        restoreCore(context, uri, confirmed, null)

    /** Emulator fixtures already operate on explicitly constructed local archives. */
    internal fun restoreOnEmptyDatabase(context: Context, uri: Uri): LegionBackupPreview =
        restoreCore(context, uri, requireNotNull(preview(context, uri).identity), null)

    /** Test seam: simulate a process interruption after DB COMMIT but before settings. */
    internal fun restoreInterruptedForTest(context: Context, uri: Uri,
                                          interrupt: () -> Unit): LegionBackupPreview =
        restoreCore(context, uri, requireNotNull(preview(context, uri).identity), interrupt)

    /** Test seam: interruption after durable intent but before the SQLite transaction commits. */
    internal fun restoreInterruptedBeforeDbCommitForTest(context: Context, uri: Uri,
                                                        interrupt: () -> Unit): LegionBackupPreview =
        restoreCore(context, uri, requireNotNull(preview(context, uri).identity), null, interrupt)

    @Synchronized private fun restoreCore(context: Context, uri: Uri,
                                          confirmed: BackupArchiveIdentity,
                                          afterDbCommit: (() -> Unit)?,
                                          afterJournalWrite: (() -> Unit)? = null): LegionBackupPreview =
        ProfileMaintenanceGate.tryRun({error("Завершите изменение игрового профиля и повторите восстановление.")}) {
        check(!GameToolboxService.isPanelActive()) { "Остановите Game Toolbox перед восстановлением." }
        checkArchiveWritesAllowed(context)
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
            withStaged(context, uri) { staged ->
                check(identity(staged.manifest) == confirmed) {
                    "Архив изменился после проверки. Откройте и подтвердите его заново."
                }
                val preview = inspectSQLite(context, staged.sqlite)
                    .copy(settings = countSettings(staged.preferences), archiveVersion = staged.manifest.getInt("version"),
                        identity = identity(staged.manifest),networkRuns=staged.archives?.network?.size ?: 0,
                        batteryRuns=staged.archives?.battery?.size ?: 0)
                staged.archives?.requireEmptyTargets(context)
                val source = SQLiteDatabase.openDatabase(staged.sqlite.absolutePath, null,
                    SQLiteDatabase.OPEN_READONLY)
                try {
                    LocalHistoryStore(context).use { helper ->
                        val target = helper.writableDatabase
                        val tables = tableOrder(target)
                        val oldMarker = sqliteMarker(target)
                        val random = SecureRandom()
                        var marker: Int
                        do { marker = random.nextInt(Int.MAX_VALUE - 1) + 1 }
                        while (marker == oldMarker)
                        target.beginTransaction()
                        try {
                            // Reject populated history BEFORE writing the intent or rows.
                            val existingCounts = tables.map { table ->
                                target.rawQuery("SELECT COUNT(*) FROM " + sqlName(table), null)
                                    .use { cursor ->
                                        check(cursor.moveToFirst())
                                        cursor.getLong(0)
                                    }
                            }
                            check(BackupRestorePolicy.acceptsEmptyTarget(existingCounts)) {
                                "База содержит пользовательские данные. Импорт разрешён только в пустую базу."
                            }
                            savePending(context, marker, staged.preferenceBytes, staged.manifest.getInt("version"))
                            afterJournalWrite?.invoke()
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
                            // SQLite application_id is an app-owned header field. The
                            // marker is atomic with all imported rows, no schema bump.
                            target.execSQL("PRAGMA application_id = $marker")
                            target.setTransactionSuccessful()
                        } finally {
                            target.endTransaction()
                        }
                    }
                } finally {
                    source.close()
                }
                afterDbCommit?.invoke()
                check(recoverPendingRestore(context)) {
                    "SQLite restore marker missing after commit"
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
                manifest.getInt("version") in 1..FORMAT_VERSION && manifest.getString("package") ==
                context.packageName && manifest.getInt("schema") == HapticLabSchema.VERSION) {
                "Несовместимая резервная копия"
            }
            require(manifest.getString("database_sha256") == sha256(file) &&
                manifest.getString("preferences_sha256") == sha256(preferences)) {
                "Контрольная сумма архива не совпадает"
            }
            return body(Staged(file, manifest, parsePreferences(preferences, manifest.getInt("version")), preferences,
                DiagnosticBackupBundle.parse(context,preferences,manifest.getInt("version"))))
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
        // Android manages android_metadata (locale) independently of our history.
        // That table is populated even on a brand-new empty install and MUST NOT
        // block user-data restore or be overwritten from a different device.
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND " +
            "name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' ORDER BY name",
            null).use { c ->
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
        return "\"" + value + "\""
    }

    private fun preferencesJson(context: Context,archives:DiagnosticBackupBundle): ByteArray {
        val root = JSONObject()
        for (name in names) {
            val values = JSONObject()
            val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            if(name == PreflightPresets.STORE) require(stored.size <= PreflightPresets.LIMIT)
            for ((key, value) in stored) {
                if(name == PreflightPresets.STORE)
                    require(PreflightPresets.validBackupEntry(key, value))
                if (key == "pending_global_undo" || key == "pending_auto_owner" ||
                    key == "pending_undo_generation" || key == "last_result" ||
                    key == "replay_status") continue
                require(key.length in 1..(if(name == PreflightPresets.STORE) 207 else 150))
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
        require(root.toString().toByteArray(Charsets.UTF_8).size<=MAX_LEGACY_PREFS)
        root.put(DiagnosticBackupBundle.KEY,archives.encode(context))
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_PREFS)
        return bytes
    }

    private fun parsePreferences(bytes: ByteArray, version: Int): Map<String, Map<String, Any>> {
        require(bytes.size <= if(version>=3) MAX_PREFS else MAX_LEGACY_PREFS)
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val expected = namesFor(version)
        require(root.keys().asSequence().toSet() == expected.toSet() +
            (if(version>=3) setOf(DiagnosticBackupBundle.KEY) else emptySet()))
        if(version>=3) {
            root.remove(DiagnosticBackupBundle.KEY)
            require(root.toString().toByteArray(Charsets.UTF_8).size<=MAX_LEGACY_PREFS)
        }
        return expected.associateWith { name ->
            val obj = root.getJSONObject(name)
            require(obj.length() <= (if(name == PreflightPresets.STORE) PreflightPresets.LIMIT else 500))
            obj.keys().asSequence().associateWith { key ->
                require(key.length in 1..(if(name == PreflightPresets.STORE) 207 else 150) && key != "pending_global_undo" &&
                    key != "last_result" && key != "replay_status" && key != "pending_auto_owner" &&
                    key != "pending_undo_generation")
                val wrapped = obj.getJSONObject(key)
                require(wrapped.keys().asSequence().toSet() == setOf("type", "value"))
                if(name == PreflightPresets.STORE) {
                    require(wrapped.getString("type") == "string")
                    require(PreflightPresets.validBackupEntry(key, wrapped.get("value")))
                }
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
        // A v1 archive or recovery journal never clears the Preflight store.
        for (name in names.filter { it in values }) {
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
