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
import java.security.MessageDigest
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

    private fun rewritePreferences(source: ByteArray, version: Int,
                                   change: (JSONObject) -> Unit): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(source)).use { input ->
            while(true) {
                val entry = input.nextEntry ?: break
                entries[entry.name] = input.readBytes()
                input.closeEntry()
            }
        }
        val settings = JSONObject(String(entries.getValue("preferences.json"), Charsets.UTF_8))
        if(version<3)settings.remove(DiagnosticBackupBundle.KEY)
        change(settings)
        val bytes = settings.toString().toByteArray(Charsets.UTF_8)
        entries["preferences.json"] = bytes
        val manifest = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8))
        manifest.put("version", version)
        manifest.put("preferences_sha256", MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) })
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        val result = ByteArrayOutputStream()
        ZipOutputStream(result).use { out ->
            for((name, data) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(data)
                out.closeEntry()
            }
        }
        return result.toByteArray()
    }

    @Test fun recoveryReadRejectsOversizeBeforeAllocatingTheWholeInput() {
        val exact = ByteArray(8192) { 7 }
        assertArrayEquals(exact, LegionDataBackup.readBounded(ByteArrayInputStream(exact), exact.size))
        val oversized = ByteArrayInputStream(ByteArray(32_768))
        assertTrue(runCatching { LegionDataBackup.readBounded(oversized, 8192) }.isFailure)
        assertEquals(32_768 - 8193, oversized.available())
    }

    @Test fun backupCanRoundTripAndRejectsPopulatedOrTamperedTarget() {
        assertEmulatorOnly()
        val app = context()
        val prefs = app.getSharedPreferences("legion-capture-studio", Context.MODE_PRIVATE)
        val backup = createZip(app, "original")
        val tampered = createZip(app, "altered")
        val legacy = createZip(app, "legacy-v1")
        val legacy2=createZip(app,"legacy-v2")
        val invalidAux=createZip(app,"invalid-aux")
        val invalidPreset = createZip(app, "invalid-preset")
        val blockedExport = createZip(app, "blocked-export")
        val presetA = PreflightPreset(120,30,41,3,true)
        val presetB = PreflightPreset(60,10,44,1,false)
        val preflight = app.getSharedPreferences("legion-preflight-v1", Context.MODE_PRIVATE)
        val pkgA = "com.example.legionci.a"
        val pkgB = "com.example.legionci.b"
        val longPkg = "com." + "x".repeat(180)
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
            assertTrue(PreflightPresets.save(app, pkgA, presetA))
            assertTrue(PreflightPresets.save(app, pkgB, presetB))
            assertTrue(PreflightPresets.save(app, longPkg, presetA))
            val net=LiveNetworkRecord(java.util.UUID.randomUUID().toString(),"dns.google",100L,LiveNetworkPlan.QUICK,
                LiveNetworkEnd.CANCELLED,listOf(LiveNetworkPoint(0L,"WIFI",1234,"8.8.8.8",12.0,"OK",4.0,"OK","192.168.1.1")))
            val bat=BatteryLabRun(java.util.UUID.randomUUID().toString(),100L,BatteryLabPlan.QUICK,BatteryLabEnd.USER_STOP,
                listOf(BatteryLabPoint(0L,50,320,1000000,4000,2000000,null,null,BatteryLabStatus.CHARGING,BatteryLabPlug.AC,0)))
            LiveNetworkArchive(app).use{it.save(net)}
            BatteryLabArchive(app).use{it.save(bat)}
            fun clearAuxiliary() {
                assertTrue(app.deleteDatabase("legion_live_network.db"))
                assertTrue(app.deleteDatabase("legion_battery_lab.db"))
            }
            fun assertAuxiliary() {
                assertEquals(net.redacted(),LiveNetworkArchive(app).use{it.list().single()})
                assertEquals(bat,BatteryLabArchive(app).use{it.list().single()})
            }
            val exported = LegionDataBackup.export(app, backup)
            assertEquals(3, exported.archiveVersion)
            assertEquals(1,exported.networkRuns);assertEquals(1,exported.batteryRuns)
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
            val legacyBytes = rewritePreferences(originalBytes, 1) {
                it.remove("legion-preflight-v1")
            }
            requireNotNull(app.contentResolver.openOutputStream(legacy, "wt")).use { it.write(legacyBytes) }
            assertEquals(1, LegionDataBackup.preview(app, legacy).archiveVersion)
            val legacy2Bytes=rewritePreferences(originalBytes,2) { }
            requireNotNull(app.contentResolver.openOutputStream(legacy2,"wt")).use{it.write(legacy2Bytes)}
            assertEquals(2,LegionDataBackup.preview(app,legacy2).archiveVersion)
            val invalidAuxBytes=rewritePreferences(originalBytes,3){root->
                val arr=root.getJSONObject(DiagnosticBackupBundle.KEY).getJSONArray("network")
                arr.getJSONObject(0).getJSONArray("points").getJSONArray(0).put(3,"8.8.8.8")
            }
            requireNotNull(app.contentResolver.openOutputStream(invalidAux,"wt")).use{it.write(invalidAuxBytes)}
            assertTrue("Correctly hashed unredacted identifiers must be rejected",runCatching{LegionDataBackup.preview(app,invalidAux)}.isFailure)
            val invalidBytes = rewritePreferences(originalBytes, 2) {
                it.getJSONObject("legion-preflight-v1").put("preset_" + pkgA,
                    JSONObject().put("type","string").put("value","v1|120|999|41|3|1"))
            }
            requireNotNull(app.contentResolver.openOutputStream(invalidPreset, "wt")).use { it.write(invalidBytes) }
            assertTrue("A correctly hashed but invalid preset must be rejected",
                runCatching { LegionDataBackup.preview(app, invalidPreset) }.isFailure)
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
            // Even an empty main database cannot overwrite populated new archives.
            assertTrue(runCatching{LegionDataBackup.restoreOnEmptyDatabase(app,backup)}.isFailure)
            assertAuxiliary()
            clearAuxiliary()

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

            assertTrue(preflight.edit().clear().commit())
            // Same SAF URI, different valid contents: the previously approved hash must reject it.
            val confirmed = requireNotNull(preview.identity)
            val replacement = rewritePreferences(originalBytes, 3) {
                it.getJSONObject("legion-capture-studio").put("ci_restore_sentinel",
                    JSONObject().put("type","string").put("value","replacement"))
            }
            requireNotNull(app.contentResolver.openOutputStream(backup, "wt")).use { it.write(replacement) }
            assertNotEquals(confirmed, LegionDataBackup.preview(app, backup).identity)
            val rejected = runCatching { LegionDataBackup.restoreOnEmptyDatabase(app, backup, confirmed) }.exceptionOrNull()
            assertNotNull(rejected)
            assertTrue(rejected?.message.orEmpty().contains("Архив изменился"))
            assertNull(GameSessionRepository(app).use { it.header(sessionId) })
            assertNull(prefs.getString("ci_restore_sentinel", null))
            assertEquals(PreflightPreset(), PreflightPresets.read(app, pkgA))
            requireNotNull(app.contentResolver.openOutputStream(backup, "wt")).use { it.write(originalBytes) }
            val restored = LegionDataBackup.restoreOnEmptyDatabase(app, backup, confirmed)
            assertEquals(presetA, PreflightPresets.read(app, pkgA))
            assertEquals(presetB, PreflightPresets.read(app, pkgB))
            assertEquals(presetA, PreflightPresets.read(app, longPkg))
            assertEquals(exported.rows, restored.rows)
            assertAuxiliary()
            GameSessionRepository(app).use { repo ->
                assertEquals(id, requireNotNull(repo.header(sessionId)).packageName)
            }
            assertEquals("restored", prefs.getString("ci_restore_sentinel", null))
            // Simulate interruption AFTER the SQLite transaction is committed
            // but BEFORE any SharedPreferences files are modified.
            assertTrue(app.deleteDatabase("legion-control-history.db"))
            clearAuxiliary()
            assertTrue(prefs.edit().remove("ci_restore_sentinel").commit())
            assertTrue(preflight.edit().clear().commit())
            val interrupted = runCatching {
                LegionDataBackup.restoreInterruptedForTest(app, backup) {
                    throw IllegalStateException("SIMULATED_PROCESS_INTERRUPTION")
                }
            }.exceptionOrNull()
            assertNotNull(interrupted)
            assertEquals("SIMULATED_PROCESS_INTERRUPTION", interrupted?.message)
            GameSessionRepository(app).use { repo ->
                assertNotNull("SQLite committed before the fault", repo.header(sessionId))
            }
            assertNull("Preference replay has not yet occurred",
                prefs.getString("ci_restore_sentinel", null))
            assertEquals(PreflightPreset(), PreflightPresets.read(app, pkgA))
            // Pending journal blocks ordinary mutations. Simulate replay of one archive before a second crash.
            assertTrue(runCatching{LiveNetworkArchive(app).use{it.save(net)}}.isFailure)
            val profilePrefs=app.getSharedPreferences("legion-launch-profiles-v1",Context.MODE_PRIVATE)
            val profilePkg="com.example.legionci.profile"
            // Pending replay must not allow new global changes or erase an undo
            // ticket left by an earlier app version. Synthetic ticket, no system write.
            assertFalse(GameLaunchProfiles.save(app,profilePkg,GameLaunchProfile(mediaPercent=20)))
            assertFalse(GameLaunchProfiles.apply(app,profilePkg).success)
            val syntheticUndo=GameLaunchUndo(profilePkg,LaunchSettingChange(1,2),null,null,null).encode()
            assertTrue(profilePrefs.edit().putString("pending_global_undo",syntheticUndo).commit())
            try {
                assertTrue(runCatching{LegionDataBackup.recoverPendingRestore(app)}.isFailure)
                assertEquals(syntheticUndo,profilePrefs.getString("pending_global_undo",null))
                assertTrue(LegionDataBackup.hasPendingRestore(app))
                assertNull(prefs.getString("ci_restore_sentinel",null))
            } finally {
                // Remove the synthetic fixture only, never apply it to Android.
                assertTrue(profilePrefs.edit().remove("pending_global_undo").commit())
            }
            LiveNetworkArchive(app).use{it.restoreSnapshot(listOf(net.redacted()))}
            assertTrue("Recovery replays committed preferences",
                LegionDataBackup.recoverPendingRestore(app))
            assertEquals("restored", prefs.getString("ci_restore_sentinel", null))
            assertEquals(presetA, PreflightPresets.read(app, pkgA))
            assertEquals(presetB, PreflightPresets.read(app, pkgB))
            assertAuxiliary()
            assertFalse("Recovery clears durable intent when finished",
                LegionDataBackup.recoverPendingRestore(app))
            val exportUndo=GameLaunchUndo(profilePkg,LaunchSettingChange(1,2),null,null,null).encode()
            assertTrue(profilePrefs.edit().putString("pending_global_undo",exportUndo).commit())
            try {
                assertTrue("Export must not transfer a pending device-global undo ticket",
                    runCatching{LegionDataBackup.export(app,blockedExport)}.isFailure)
            } finally {
                assertTrue(profilePrefs.edit().remove("pending_global_undo").commit())
            }
            // A second import must reject the now populated database.
            assertTrue(runCatching {
                LegionDataBackup.restoreOnEmptyDatabase(app, backup)
            }.isFailure)
            // Old ZIP recovery must preserve presets already saved on the target.
            assertTrue(app.deleteDatabase("legion-control-history.db"))
            assertTrue(PreflightPresets.save(app, pkgA, presetB))
            val legacyFault = runCatching {
                LegionDataBackup.restoreInterruptedForTest(app, legacy) {
                    throw IllegalStateException("LEGACY_INTERRUPTED")
                }
            }.exceptionOrNull()
            assertEquals("LEGACY_INTERRUPTED", legacyFault?.message)
            assertTrue(LegionDataBackup.recoverPendingRestore(app))
            assertEquals(presetB, PreflightPresets.read(app, pkgA))
            assertEquals(presetB, PreflightPresets.read(app, pkgB))
            assertEquals(presetA, PreflightPresets.read(app, longPkg))
            assertAuxiliary() // old archive must preserve both new stores
            assertTrue(app.deleteDatabase("legion-control-history.db"))
            val old2=LegionDataBackup.restoreOnEmptyDatabase(app,legacy2)
            assertEquals(2,old2.archiveVersion);assertAuxiliary()
        } finally {
            app.contentResolver.delete(legacy, null, null)
            app.contentResolver.delete(legacy2,null,null)
            app.contentResolver.delete(invalidAux,null,null)
            app.contentResolver.delete(invalidPreset, null, null)
            app.contentResolver.delete(blockedExport,null,null)
            preflight.edit().clear().commit()
            app.contentResolver.delete(backup, null, null)
            app.contentResolver.delete(tampered, null, null)
            prefs.edit().remove("ci_restore_sentinel").commit()
            // The CI script clears the ephemeral app again before UI smoke.
        }
    }
}
