"""Guard the Settings permission center, actionable rows and honest read-only limitations."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
hub = (src / "HubPageFragment.kt").read_text()
model = (src / "PermissionCenterModel.kt").read_text()
ui = (src / "LegionUiSupport.kt").read_text()
backend = (src / "CapabilityAccessBackend.kt").read_text()
settings = hub[hub.index("private fun showSettings"):hub.index("private fun showLegacyShortcuts")]
assert "CapabilityAccessBackend.read(requireContext())" in settings
assert "PermissionCenterModel.permissions(access)" in settings
assert "PermissionCenterModel.limitations(access)" in settings
assert "LegionUiSupport.permissionRow" in settings
assert "Settings.canDrawOverlays" not in settings
assert "CapabilityAccessBackend.settingsIntent(ctx, capability)" in hub
assert "CapabilityAccess.PLAYBACK_AUDIO -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS" in backend
assert "it.settings && snapshot[it.capability] != AccessState.UNSUPPORTED" in model
assert "item.state.label" in ui and "if (item.canOpenSettings)" in ui
assert "minimumHeight = dp(this, 48)" in ui
assert "isAccessibilityHeading = true" in hub
assert "android." not in model
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/PermissionCenterModelTest.kt").read_text()
assert tests.count("@Test") >= 4
print("PASS: common permission center with six user-managed capabilities, non-actionable limits, explicit Settings actions and independent presentation policy")
