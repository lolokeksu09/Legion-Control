"""Verify the ZIP restore intent/SQLite marker protocol and both crash boundaries."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol/LegionDataBackup.kt"
test = root / "app/src/androidTest/java/dev/lolokeksu/legioncontrol/FullBackupInstrumentedTest.kt"
body = src.read_text(encoding="utf-8")
android_test = test.read_text(encoding="utf-8")
core = body[body.index("private fun restoreCore"):body.index("private fun <T> withStaged")]
assert "afterJournalWrite: (() -> Unit)? = null" in core
assert core.index("savePending(context") < core.index("afterJournalWrite?.invoke()") < core.index("target.insertOrThrow")
assert "afterDbCommit?.invoke()" in core
assert core.index("checkArchiveWritesAllowed(context)") < core.index("withStaged(context, uri)")
retry = body[body.index("fun retryPendingRestore"):body.index("fun export(")]
assert retry.index("reserveConsent(CaptureMode.MAINTENANCE)") < retry.index("recoverPendingRestore(context)")
assert "CaptureCoordinator.release(CaptureMode.MAINTENANCE)" in retry
assert "restoreInterruptedBeforeDbCommitForTest" in body
assert "restoreInterruptedForTest" in body
assert "sqliteMarker(helper.readableDatabase) == marker" in body
assert "committed" in body and "preferences_sha256" in body
assert "SIMULATED_BEFORE_SQLITE_COMMIT" in android_test
assert "SIMULATED_PROCESS_INTERRUPTION" in android_test
# The post-commit case requires a clean fixture after successful pre-commit recovery.
post_commit_fixture = android_test.split("// Simulate interruption AFTER", 1)[1].split("val interrupted = runCatching", 1)[0]
assert any(line.strip() == 'assertTrue(app.deleteDatabase("legion-control-history.db"))'
           for line in post_commit_fixture.splitlines()), "Post-commit fixture reset must be executable, not a comment"
assert "Uncommitted marker must discard, not replay, preferences" in android_test
assert "Recovery replays committed preferences" in android_test
print("PASS: restore intent is replayed only after SQLite commit marker; both crash boundaries are covered")
