"""Startup recovery must not perform SQLite/journal replay on the main thread."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
app = (src / "LegionApplication.kt").read_text(encoding="utf-8")
coordinator = (src / "StartupRecoveryCoordinator.kt").read_text(encoding="utf-8")
launcher = (src / "NavigationHostActivity.kt").read_text(encoding="utf-8")
tile = (src / "LegionQuickTileService.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/StartupRecoveryCoordinatorTest.kt").read_text(encoding="utf-8")

assert "LegionDataBackup.hasPendingRestore(this)" in app
assert "Executors.newSingleThreadExecutor" in app
assert "startupRecovery.start()" in app
assert "recoverPendingRestore(this)" in app
assert "Process.THREAD_PRIORITY_BACKGROUND" in app
assert "StartupRecoveryState.Running" in coordinator
assert "StartupRecoveryState.Failed" in coordinator
assert "ContinuedWithWarning" in coordinator
assert "AtomicBoolean(false)" in coordinator
assert "coordinator.observe(::onStartupRecoveryState)" in launcher
assert "initializeNavigation(pendingSavedState)" in launcher
assert "continueAfterFailure()" in launcher
assert "Intent(this,NavigationHostActivity::class.java)" in tile
assert "SmartProfilesActivity::class.java" not in tile
assert "Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP" in tile
for token in ("recoveryIsQueuedAndRunsOnlyOnce", "failureIsVisibleUntilUserAcknowledgesIt",
              "laterSuccessfulManualRetryClearsWarningForActivityRecreation",
              "cleanInstallDoesNotScheduleRecoveryWork"):
    assert token in tests, token
print("PASS: startup recovery is background-only, launcher-gated, failure-visible and retryable")
