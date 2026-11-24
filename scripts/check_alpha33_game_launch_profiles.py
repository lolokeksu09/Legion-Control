#!/usr/bin/env python3
"""Guard alpha.33 manual per-game actions; do not turn this into passive tracking."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/lolokeksu/legioncontrol"
policy = (src / "GameLaunchProfilePolicy.kt").read_text(encoding="utf-8")
engine = (src / "GameLaunchProfiles.kt").read_text(encoding="utf-8")
ui = (src / "GameToolboxFragment.kt").read_text(encoding="utf-8")
overlay = (src / "GameToolboxService.kt").read_text(encoding="utf-8")
tests = (root / "app/src/test/java/dev/lolokeksu/legioncontrol/GameLaunchProfilePolicyTest.kt").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
actual = {x.get(ns + "name") for x in manifest.findall("uses-permission")}
required = {"android.permission.WRITE_SETTINGS", "android.permission.ACCESS_NOTIFICATION_POLICY",
            "android.permission.MODIFY_AUDIO_SETTINGS"}
assert required.issubset(actual)
assert "android.permission.QUERY_ALL_PACKAGES" not in actual
assert "android.permission.WRITE_SECURE_SETTINGS" not in actual
assert "android.permission.BIND_ACCESSIBILITY_SERVICE" not in actual
assert "GameLaunchProfiles.apply(requireContext(), pkg)" in ui and "GameLaunchProfiles.restore(requireContext())" in ui
assert "GameLaunchProfiles.apply(this, pkg)" in overlay and "GameLaunchProfiles.restore(this)" in overlay
assert "GameLaunchProfiles.save(requireContext(), pkg, profile)" in ui
assert "getLaunchIntentForPackage(pkg)" in ui
assert 'edit().putString(ACTIVE, snapshot.encode())' in engine
assert engine.index('journal.commit()') < engine.index('audio.setStreamVolume(')
assert 'journal.putString(OWNER,owner)' in engine and 'restoreAutomatic' in engine
assert "Settings.System.canWrite(context)" in engine
assert "nm.isNotificationPolicyAccessGranted" in engine
assert "GameLaunchProfilePolicy.restoreChoice(read(), change)" in engine
assert "CHANGED_BY_USER" in engine
assert "GameLaunchUndo.decode(raw)" in engine
# Every launch-profile mutation and ZIP replay follows one lock order:
# maintenance gate first, then the profile ticket monitor. In particular,
# automatic rollback must not acquire the monitor before waiting for the gate.
auto_restore = engine[engine.index("internal fun restoreAutomatic"):engine.index("private fun applyInternal")]
manual_restore = engine[engine.index("fun restore(context"):engine.index("private fun restoreLocked")]
assert auto_restore.index("ProfileMaintenanceGate.tryRun") < auto_restore.index("synchronized(lock)")
assert manual_restore.index("ProfileMaintenanceGate.tryRun") < manual_restore.index("synchronized(lock)")
assert "private fun restoreLocked(context: Context)" in engine
restore_locked = engine[engine.index("private fun restoreLocked"):engine.rindex("\\n}")]
assert "return@synchronized" not in restore_locked
assert "return if (!saved)" in restore_locked


assert "ACTION_MANAGE_WRITE_SETTINGS" not in engine
assert all(forbidden not in engine for forbidden in (
    "queryUsageStats", "queryEvents(", "AccessibilityService", "startForegroundService",
    "startActivity(", "Shell", "Runtime.getRuntime", "java.net", "java.io.File"
))
assert tests.count("@Test") >= 8
assert "SmartProfilesRepository" not in engine
assert "LocalHistoryStore" not in engine
assert "android.permission" not in policy
print("PASS alpha33: user-initiated global actions, preflight, durable restore, no new privileges or SQLite migration")
