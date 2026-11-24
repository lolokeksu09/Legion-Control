#!/usr/bin/env python3
"""alpha.40 audit remediation guard: concrete ownership, consent, and no-data-loss seams."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
tests = root / "app/src/test/java/dev/lolokeksu/legioncontrol"
read = lambda f: (base / f).read_text(encoding="utf-8")
test = lambda f: (tests / f).read_text(encoding="utf-8")

gradle = (root / "app/build.gradle.kts").read_text()
assert 'applicationId = "dev.lolokeksu.legioncontrol"' in gradle
assert 'versionCode = 49' in gradle and 'versionName = "0.1.0-alpha.49"' in gradle
assert '"arm64-v8a", "x86_64"' in gradle

coord = read("CaptureCoordinator.kt")
ui = read("CaptureStudioActivity.kt")
for mode in ("RECORDING", "REPLAY", "SCREENSHOT"):
    assert f"reserveConsent(CaptureMode.{mode})" in ui, mode
    assert f"CaptureCoordinator.begin(CaptureMode.{mode})" in read({
        "RECORDING": "CaptureRecordingService.kt",
        "REPLAY": "ReplayCaptureService.kt",
        "SCREENSHOT": "CaptureScreenshotService.kt",
    }[mode]), mode
    assert f"CaptureCoordinator.release(CaptureMode.{mode})" in (
        read("ReplayCaptureService.kt") + read("CaptureRecordingService.kt") +
        read("CaptureScreenshotService.kt")
    ), mode
assert "CapturePhase.CONSENT_PENDING" in coord and "CapturePhase.STOPPING" in coord
assert "releasePending" in coord and "invalidConsentCannotReleaseActiveProjection" in test("CaptureCoordinatorTest.kt")

for collection in ("CaptureClipLibrary.kt", "CaptureScreenshotLibrary.kt"):
    code = read(collection)
    assert "QUERY_ARG_LIMIT" in code and "QUERY_ARG_OFFSET" in code
    assert "fun listOwn(context: Context, page: Int = 0)" in code
    assert "fun isOwnPublished(context: Context, uri: Uri)" in code
    assert "OWNER_PACKAGE_NAME" in code and "IS_PENDING" in code
repo = read("CaptureStudioRepository.kt")
assert "CaptureClipLibrary.isOwnPublished(app, clip.uri)" in repo
assert "CaptureScreenshotLibrary.isOwnPublished(app, screenshot.uri)" in repo
vm = read("CaptureStudioViewModel.kt")
assert "nextClipPage()" in vm and "previousClipPage()" in vm
assert "nextScreenshotPage()" in vm and "previousScreenshotPage()" in vm
assert "viewModel.nextClipPage()" in ui and "viewModel.nextScreenshotPage()" in ui

cleanup = read("CapturePendingRecovery.kt")
assert "MediaStore.setIncludePending" in cleanup
assert "OWNER_PACKAGE_NAME" in cleanup and "IS_PENDING}=1" in cleanup
assert "CaptureMode.MAINTENANCE" in cleanup
assert "PendingCapturePolicy.isStale" in cleanup
assert "cleanStaleOwn" in vm and "setPositiveButton(\"Очистить\")" in ui
assert "onlyOwnNamedOldPendingRowsQualify" in test("PendingCapturePolicyTest.kt")

backup = read("LegionDataBackup.kt")
for needed in (
    "VACUUM INTO ?", "legion-control-local-backup", "SHA-256",
    "PRAGMA quick_check", "tableOrder(target)", "BackupRestorePolicy.acceptsEmptyTarget(",
    "target.beginTransaction()", "target.setTransactionSuccessful()",
    "savePending(context, marker, staged.preferenceBytes, staged.manifest.getInt(\"version\"),staged.labBytes)",
    "target.execSQL(\"PRAGMA application_id = $marker\")",
    "check(recoverPendingRestore(context))",
    "if (committed) applyPreferences(context, values)",
    "atomic.finishWrite(output)", "MAX_DB",
    "GameSessionRecorderService.sessionActive", "CaptureMode.MAINTENANCE"
):
    assert needed in backup, needed
assert "DROP TABLE" not in backup and "DELETE FROM" not in backup
assert backup.index("savePending(context, marker, staged.preferenceBytes, staged.manifest.getInt(\"version\"),staged.labBytes)") < backup.index("target.setTransactionSuccessful()")
assert "private fun restoreCore" in backup and "restoreInterruptedForTest" in backup
assert "readBounded(it, MAX_JOURNAL)" in backup
assert backup.index("check(identity(staged.manifest) == confirmed)") < backup.index("target.beginTransaction()")
assert "restoreOnEmptyDatabase(getApplication(), uri, confirmed)" in vm
assert "refusesAnyExistingUserRows" in test("BackupRestorePolicyTest.kt")
assert "viewModel.exportFullBackup(" in ui and "viewModel.inspectFullBackup(" in ui
assert "viewModel.restoreFullBackup(" in ui and "setPositiveButton(\"Восстановить\")" in ui
assert "MediaStore.MediaColumns.IS_PENDING" not in backup

csv = read("CsvEscaper.kt")
assert "it.isWhitespace() || it in invisible" in csv
assert "untrusted: Boolean = true" in csv
assert "protectsWhitespaceAndInvisibleFormulaPrefixes" in test("CsvEscaperTest.kt")
for f in ("GameSessionRepository.kt", "ApplicationAuditRules.kt",
          "ExperimentRepository.kt", "SessionIncidents.kt", "DiagnosticArchive.kt"):
    assert "CsvEscaper.cell(" in read(f), f

assert "versionLabel()" in read("NavigationHostActivity.kt")
assert "GAME HUB  ·  alpha.26" not in read("NavigationHostActivity.kt")
assert "contentDescription = \"Назад\"" in read("NavigationHostActivity.kt")
assert "WindowInsets.Type.ime()" in read("LegionUiSupport.kt")
assert "LegionUiSupport.setModuleActivityContent(this" in read("CaptureStudioActivity.kt")
assert "Не удалось" in read("ConnectivityLabViewModel.kt")
assert "CaptureStorageOverview.report" in vm
assert "android.permission.MANAGE_EXTERNAL_STORAGE" not in (
    root / "app/src/main/AndroidManifest.xml").read_text()
print("alpha.40 audit remediation consent, ownership, backup and accessibility contracts: PASS")
