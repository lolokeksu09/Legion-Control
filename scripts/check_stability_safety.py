"""Regression source wiring for durable manual settings snapshots and readback."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
controls = (src / "SmartSystemControls.kt").read_text(encoding="utf-8")
policy = (src / "VerifiedSettingWrite.kt").read_text(encoding="utf-8")
timeout = controls[controls.index("fun setScreenTimeout"):controls.index("fun setBrightness")]
brightness = controls[controls.index("fun setBrightness"):controls.index("private fun writeVerified")]
restore = controls[controls.index("fun restoreScreenSettings"):]
assert ".commit()" in timeout and timeout.index(".commit()") < timeout.index("writeVerified(")
assert ".commit()" in brightness and brightness.index(".commit()") < brightness.index("writeVerified(")
assert ".apply()" not in timeout + brightness + restore
assert 'restore("brightness", "brightness_requested", Settings.System.SCREEN_BRIGHTNESS,' in restore
assert "modeAfterBrightness(hadBrightness, brightnessDecision)" in restore
assert "BrightnessModeRestoreAction.DEFER" in restore and "BrightnessModeRestoreAction.DISCARD" in restore
assert 'listOf("brightness_mode", "brightness_mode_requested")' in restore
assert "discardOnConflict.forEach { edit.remove(it) }" in controls
assert 'restore("brightness_mode", "brightness_mode_requested", Settings.System.SCREEN_BRIGHTNESS_MODE)' in restore
assert 'restore("timeout", "timeout_requested", Settings.System.SCREEN_OFF_TIMEOUT)' in restore
assert "SmartSystemRestorePolicy.decide" in controls
assert "SmartSystemRestoreDecision.USER_CHANGED" in controls
assert "brightness_mode_requested" in brightness and "timeout_requested" in timeout
assert "check(actual == requested)" in policy and "check(write(requested))" in policy
assert "Build.VERSION.SDK_INT >= 35" in controls and "другие правила Android" in controls
restore_policy = (src / "SmartSystemRestorePolicy.kt").read_text(encoding="utf-8")
restore_tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/SmartSystemRestorePolicyTest.kt").read_text(encoding="utf-8")
assert "current == requested" in restore_policy and "USER_CHANGED" in restore_policy
for token in ("restoresOnlyWhenCurrentValueStillMatchesLegionRequest",
              "doesNotRewriteAnAlreadyRestoredValue",
              "preservesValuesChangedByTheUserOrAnotherApp",
              "keepsBackwardCompatibilityForLegacyUndoRecords"):
    assert token in restore_tests, token
engine = (src / "GameLaunchProfiles.kt").read_text(encoding="utf-8")
overlay = (src / "GameToolboxService.kt").read_text(encoding="utf-8")
assert "ProfileMaintenanceGate.tryRun" in controls
assert "GameLaunchProfiles.pendingPackage(context) != null" in controls
assert "SmartSystemControls.hasPendingBrightness(context)" in engine
assert "SmartSystemControls.withManualMutation(this)" in overlay
for name in ("setMediaVolume", "setDoNotDisturb", "setScreenTimeout", "setBrightness", "restoreScreenSettings"):
    method = controls[controls.index("fun " + name):]
    assert "withManualMutation(context" in method.splitlines()[0], name
print("PASS: durable manual settings snapshots, readback, compare-before-restore, shared ownership gate and Android 15 DND feedback")
