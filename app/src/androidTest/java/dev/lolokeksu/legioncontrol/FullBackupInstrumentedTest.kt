package dev.lolokeksu.legioncontrol

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Destructive test, intentionally restricted to fresh CI emulators. Neither the
 * test APK nor this instrumentation runner is included in a release APK.
 */
@RunWith(AndroidJUnit4::class)
class FullBackupInstrumentedTest {
    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assertEmulatorOnly() {
        val fingerprint = Build.FINGERPRINT.lowercase()
        assumeTrue("Never delete a physical device's Legion Control data",
            fingerprint.contains("generic") || fingerprint.contains("emulator") ||
                Build.PRODUCT.lowercase().contains("sdk"))
    }

    private fun createZip(context: Context, suffix: String): Uri =
        requireNotNull(context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,
                    "LegionControlCI-$suffix-${System.nanoTime()}.zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/LegionControlCI/")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
        ))

    /** Simulates a valid ZIP with a forged embedded checksum. */
    private fun forgeManifestHash(source: ByteArray): ByteArray {
        val result = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(source)).use { input ->
            ZipOutputStream(result).use { output ->
                while (true) {
                    val item = input.nextEntry ?: break
                    val bytes = input.readBytes()
                    output.putNextEntry(ZipEntry(item.name))
                    if (item.name == "manifest.json") {
                        val manifest = JSONObject(String(bytes, Charsets.UTF_8))
                        manifest.put("database_sha256", "0".repeat(64))
                        output.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    } else output.write(bytes)
                    output.closeEntry()
                    input.closeEntry()
                }
            }
        }
        return result.toByteArray()
    }

    /** Journal payloads use the exact four-file legacy preference format. */
    private fun settingsSnapshot(sentinel: String): String {
        val root = JSONObject()
        for (name in listOf("legion-game-capture-profiles", "legion-capture-studio",
                "legion-launch-profiles-v1", ToolboxPreferences.NAME)) {
            val fields = JSONObject()
            if (name == "legion-capture-studio") {
                fields.put("ci_restore_sentinel", JSONObject()
                    .put("type", "string").put("value", sentinel))
            }
            root.put(name, fields)
        }
        return root.toString()
    }

    @Test fun backupCanRoundTripAndRejectsPopulatedOrTamperedTarget() {
        assertEmulatorOnly()
        val app = context()
        val prefs = app.getSharedPreferences("legion-capture-studio", Context.MODE_PRIVATE)
        val backup = createZip(app, "original")
        val tampered = createZip(app, "altered")
        val id = "com.example.legionci"
        try {
            // The caller CI script clears this emulator-only app before this test.
            GameSessionRepository(app).use { repo ->
                assertTrue(repo.recent(1).isEmpty())
            }
            assertTrue(prefs.edit().putString("ci_restore_sentinel", "restored").commit())
            val sessionId = GameSessionRepository(app).use { repo ->
                repo.start(id, "CI game", false).also {
                    repo.event(it, "USER_MARK", "ci_backup_mark")
                    repo.finish(it, "STOP")
                }
            }
            val exported = LegionDataBackup.export(app, backup)
            assertTrue(exported.rows >= 2)
            assertTrue(exported.settings >= 1)
            val preview = LegionDataBackup.preview(app, backup)
            assertEquals(exported.tables, preview.tables)
            assertEquals(exported.rows, preview.rows)

            // No implicit erase or merge of existing history.
            val blocked = runCatching {
                LegionDataBackup.restoreOnEmptyDatabase(app, backup)
            }.exceptionOrNull()
            assertNotNull("Existing history must reject restore", blocked)
            assertTrue(GameSessionRepository(app).use { it.header(sessionId) != null })

            val originalBytes = requireNotNull(
                app.contentResolver.openInputStream(backup)).use { it.readBytes() }
            val forged = forgeManifestHash(originalBytes)
            requireNotNull(app.contentResolver.openOutputStream(tampered, "wt")).use {
                it.write(forged)
            }
            assertTrue("Modified checksum must reject the archive",
                runCatching { LegionDataBackup.preview(app, tampered) }.isFailure)

            // New install / empty database scenario: NO rows are overwritten.
            assertTrue("Only CI emulator history may be removed",
                app.deleteDatabase("legion-control-history.db"))
            assertTrue(prefs.edit().remove("ci_restore_sentinel").commit())
            assertNull(GameSessionRepository(app).use { it.header(sessionId) })

            // Diagnostic assertion: pin down which SQLite table is unexpectedly
            // populated after recreating the supposedly fresh database.
            LocalHistoryStore(app).use { helper ->
                val db = helper.readableDatabase
                val nonEmpty = mutableListOf<String>()
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND " +
                    "name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' " +
                    "ORDER BY name", null).use { tables ->
                    while (tables.moveToNext()) {
                        val table = tables.getString(0)
                        val safe = Regex("[A-Za-z][A-Za-z0-9_]{0,79}")
                        assertTrue(safe.matches(table))
                        db.rawQuery("SELECT COUNT(*) FROM \"" + table + "\"", null)
                            .use { count ->
                                assertTrue(count.moveToFirst())
                                if (count.getLong(0) != 0L)
                                    nonEmpty += table + "=" + count.getLong(0)
                            }
                    }
                }
                val appRows = nonEmpty.filterNot { it.startsWith("android_metadata=") }
                assertTrue("Fresh DB must have no application rows: " + appRows,
                    appRows.isEmpty())
            }

            val restored = LegionDataBackup.restoreOnEmptyDatabase(app, backup)
            assertEquals(exported.rows, restored.rows)
            GameSessionRepository(app).use { repo ->
                assertEquals(id, requireNotNull(repo.header(sessionId)).packageName)
            }
            assertEquals("restored", prefs.getString("ci_restore_sentinel", null))
            // A second import must reject the now populated database.
            assertTrue(runCatching {
                LegionDataBackup.restoreOnEmptyDatabase(app, backup)
            }.isFailure)

            // Simulate process death after SQLite commit but midway through the
            // separate SharedPreferences commits. Recovery must complete target.
            val journal = app.getSharedPreferences(
                "legion-backup-restore-journal", Context.MODE_PRIVATE)
            val committedMarker = LocalHistoryStore(app).use { helper ->
                helper.readableDatabase.rawQuery("PRAGMA application_id", null).use {
                    assertTrue(it.moveToFirst())
                    it.getInt(0)
                }
            }
            assertTrue("Restore commit marker must be durable", committedMarker > 0)
            assertFalse("Successful restore must clear journal",
                journal.contains("sqlite_commit_marker"))
            assertTrue(journal.edit()
                .putInt("sqlite_commit_marker", committedMarker)
                .putString("previous_settings", settingsSnapshot("old"))
                .putString("restored_settings", settingsSnapshot("restored"))
                .commit())
            assertTrue(prefs.edit().putString("ci_restore_sentinel", "partial").commit())
            assertTrue(LegionDataBackup.recoverInterruptedRestore(app))
            assertEquals("restored", prefs.getString("ci_restore_sentinel", null))
            assertFalse(journal.contains("sqlite_commit_marker"))
            assertFalse(LegionDataBackup.recoverInterruptedRestore(app))

            // Simulate a crash before SQLite COMMIT (different transactional
            // marker); rollback restores previous preferences without touching DB.
            val uncommittedMarker = if (committedMarker == Int.MAX_VALUE)
                committedMarker - 1 else committedMarker + 1
            assertTrue(journal.edit()
                .putInt("sqlite_commit_marker", uncommittedMarker)
                .putString("previous_settings", settingsSnapshot("old"))
                .putString("restored_settings", settingsSnapshot("new"))
                .commit())
            assertTrue(prefs.edit().putString("ci_restore_sentinel", "partial").commit())
            assertTrue(LegionDataBackup.recoverInterruptedRestore(app))
            assertEquals("old", prefs.getString("ci_restore_sentinel", null))
            assertFalse(journal.contains("sqlite_commit_marker"))
            GameSessionRepository(app).use { repo ->
                assertEquals(id, requireNotNull(repo.header(sessionId)).packageName)
            }
        } finally {
            app.contentResolver.delete(backup, null, null)
            app.contentResolver.delete(tampered, null, null)
            prefs.edit().remove("ci_restore_sentinel").commit()
            // The CI script clears the ephemeral app again before UI smoke.
        }
    }
}
