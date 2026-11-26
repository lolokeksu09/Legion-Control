"""Guard shared status semantics and explicit permission navigation."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
backend = (src / "CapabilityAccessBackend.kt").read_text()
policy = (src / "CapabilityAccessPolicy.kt").read_text()
for name in ("SmartCompatibilityBackend.kt", "SmartSystemControls.kt", "GameToolboxFragment.kt"):
    assert "CapabilityAccessBackend.read(" in (src / name).read_text(), name
assert "CapabilityAccessBackend.settingsIntent" in (src / "SmartProfilesFragment.kt").read_text()
for state in ("READY", "DISABLED", "PERMISSION_REQUIRED", "UNSUPPORTED", "UNKNOWN"):
    assert state in policy
assert "notification?.areNotificationsEnabled()" in backend and "POST_NOTIFICATIONS" in backend
assert "MODE_DEFAULT" in backend and "else -> null" in backend
assert "AccessState.UNSUPPORTED" in backend
assert "getOrNull()" in backend
assert "startActivity(" not in backend and "requestPermissions(" not in backend
test = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/CapabilityAccessPolicyTest.kt").read_text()
assert test.count("@Test") >= 8
assert "CapabilityAccessBackend.automationAccess(" in (src / "GameAutomationController.kt").read_text()
assert "CapabilityAccessBackend.profileAccess(" in (src / "GameToolboxFragment.kt").read_text()
assert "CapabilityAccessBackend.playbackAudioState(" in (src / "CaptureStudioActivity.kt").read_text()
assert "ACTION_CHANNEL_NOTIFICATION_SETTINGS" in backend
assert "getNotificationChannel(channelId)" in backend
backup = (root / "app/src/androidTest/java/dev/lolokeksu/legioncontrol/FullBackupInstrumentedTest.kt").read_text()
after = backup[backup.index("// Simulate interruption AFTER"):backup.index("val interrupted = runCatching")]
assert re.search(r'^\s*assertTrue\(app\.deleteDatabase\("legion-control-history\.db"\)\)', after, re.M)
print("PASS: shared capability states, explicit settings intents and isolated ZIP crash fixtures")
